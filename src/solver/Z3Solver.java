package solver;

import java.io.*;
import java.util.*;

import com.microsoft.z3.*;

import exploration.interfaces.SolverFunctions;

/**
 * Expose the Z3 Solver to our framework.
 * 
 * @author Pranav Tendulkar
 *
 */
public abstract class Z3Solver extends Z3Context implements SolverFunctions
{
	/**
	 * Z3 Solver Instance.
	 */
	protected Solver z3Solver;
	
	/**
	 * For every statement that we assert, we can save it on our own stack to 
	 * generate the output file.
	 */
	private boolean enableStatementStack=false;
	
	/**
	 * We save all the assertions on this stack.
	 * 
	 * Use this to generate a Z3 solver file.
	 * 
	 * TODO: we use push and pop alternatively so this works. 
	 * otherwise we must have list<list<integer>> and more complex logic.
	 */
	protected List<String> contextStatements; 

	/**
	 * Context pushed on the stack? 
	 * Next thing to do will be a pop.
	 */
	protected boolean pushedContext;
	
	/**
	 * Number of statements asserted after context pushed. 
	 */
	protected int statementCountAfterPush = 0;
	
	/**
	 * Result of a SMT query
	 */
	public enum SatResult { SAT, UNSAT, UNKNOWN, INCONSISTENT, TIMEOUT, OUTOFMEMORY, OTHERS }

	/**
	 * Build Z3 Solver Object
	 */
	protected Z3Solver ()
	{
		contextStatements = new ArrayList<String>();
		
		System.setProperty("java.library.path", "/home/rajtendulkar/eclipse-workspace/Java-WorkSpace/spdf_with_Z3_integrated/dep/Z3Lib/64-bit");

		// Make a default purpose solver.
		try { z3Solver = ctx.mkSolver (); } catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * Get a Sort from the Z3. Int, Real or Bool used for variables.
	 * 
	 * @param sortType type of sort in string
	 * @return Sort from Z3 Solver
	 */
	private Sort getSort (String sortType)
	{
		Sort sort=null;
		try 
		{
			if (sortType.equalsIgnoreCase ("Int"))
				sort = ctx.getIntSort ();			
			else if (sortType.equalsIgnoreCase ("Real"))
				sort = ctx.getRealSort ();
			else if (sortType.equalsIgnoreCase ("Bool"))
				sort = ctx.getBoolSort ();
			else
				throw new RuntimeException (" Unknown Return Sort " + sortType);
		} catch (Z3Exception e) { e.printStackTrace (); }
		return sort;
	}

	/**
	 * Declare a variable in Z3 context.
	 * We save the variable declaration for generation of the
	 * output z3 file. By default, I think it is not saved in
	 * the context.
	 * 
	 * @param variableName name of the variable
	 * @param variableType type of the variable
	 * 
	 * @return Expression representing the variable.
	 */
	public Expr addVariableDeclaration (String variableName, String variableType) 
	{
		contextStatements.add ("(declare-const " + variableName + " " + variableType + ")");
		try
		{
			if (variableType.equalsIgnoreCase ("Int"))
				return ctx.mkIntConst (variableName);
			else if (variableType.equalsIgnoreCase ("Real"))
				return ctx.mkRealConst (variableName);
			else if (variableType.equalsIgnoreCase ("Bool"))
				return ctx.mkBoolConst (variableName); 
			else
				throw new RuntimeException ("Unknown Sort " + variableType);

		} catch (Z3Exception e) { e.printStackTrace (); }
		return null;					
	}


	/**
	 * Declare a function in Z3 context.
	 * 
	 * @param functionName name of the function
	 * @param argumentType arguments of the function
	 * @param returnType return type of the function
	 * 
	 * @return Function Declaration
	 */
	public FuncDecl declareFunction (String functionName, String[] argumentType, String returnType) 
	{
		String functionString = "(declare-fun " + functionName + " (";
		for (int i=0;i<argumentType.length;i++)
			functionString = functionString.concat ("("+argumentType[i]+")");
		functionString = functionString.concat (") " + returnType + ")");
		contextStatements.add (functionString);

		Sort[] argumentSortTypes = new Sort[argumentType.length];
		Sort returnTypeSort;

		for (int i=0;i<argumentType.length;i++)	
			argumentSortTypes[i] = getSort (argumentType[i]);

		returnTypeSort = getSort (returnType);

		// FuncDecl funcDecl = ctx.mkFuncDecl (functionName, argumentSortTypes, returnType);	

		FuncDecl funcDecl=null;
		try 
		{
			funcDecl = ctx.mkFuncDecl (functionName, argumentSortTypes, returnTypeSort);
		} catch (Z3Exception e) { e.printStackTrace (); }

		return funcDecl;
	}


	/**
	 * Instead of default solver, we would want to use tactic solver.
	 * We apply one tactic after another. However, we can have 
	 * some advanced tactic application. Its not done yet.
	 * 
	 * @param tacticNames name of the tactics for the solver.
	 */
	public void setTacTicSolver (String tacticNames[])
	{
		Tactic finalTactic;
		try 
		{
			finalTactic = ctx.mkTactic (tacticNames[0]);
			for (int i=1;i<tacticNames.length;i++)
				finalTactic = ctx.then (finalTactic, ctx.mkTactic (tacticNames[i]));

			z3Solver = ctx.mkSolver (finalTactic);
		} catch (Z3Exception e) { e.printStackTrace (); }		
	}

	/**
	 * Generate a Z3 file with all the constraints in it.
	 * We can use this file to check the same problem by using 
	 * Z3 on command line.
	 * @param fileName name of the output file including the path
	 */
	public void generateSatCode (String fileName)
	{
		FileWriter fstreamOutput ;
		PrintWriter out;
		// String fileName = outputDirectory + "output.z3";

		try
		{
			fstreamOutput = new FileWriter (fileName);
			out = new PrintWriter (fstreamOutput);

			out.println ("(set-option :produce-models true) ; enable model generation");
			out.println ("(set-option :print-success false)");

			if (enableStatementStack == true)
			{
				for (int i=0;i<contextStatements.size ();i++)
					out.println (contextStatements.get (i));
			}
			else
			{
				for (int i=0;i<contextStatements.size ();i++)
					out.println (contextStatements.get (i));

				BoolExpr expr[] = z3Solver.getAssertions ();
				for (int i=0;i<expr.length;i++)
					out.println ("(assert"+expr[i].toString ()+")");
			}

			out.println ("(check-sat)");
			out.println ("(get-model)");

			out.close ();
			fstreamOutput.close ();			
		}
		catch (Exception e)
		{ //Catch exception if any
			System.err.println ("Error File Generation: " + e.getMessage ());
		}
	}

	/**
	 * Print the current context of variables and assertions.
	 */
	public void printContext ()
	{
		if (enableStatementStack == true)
		{
			for (int i=0;i<contextStatements.size ();i++)
				System.out.println (contextStatements.get (i));
		}
		else
		{
			try 
			{
				BoolExpr expr[] = z3Solver.getAssertions ();
				for (int i=0;i<expr.length;i++)
					System.out.println (expr[i].toString ());					
			} catch (Z3Exception e) { e.printStackTrace (); }
		}
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.SolverFunctions#popContext(int)
	 */
	@Override
	public void popContext (int scopes)
	{
		// Remove these many statements from the end.
		if (enableStatementStack == true)
		{
			for (int i=0;i<statementCountAfterPush;i++)
				contextStatements.remove (contextStatements.size ()-1);
			statementCountAfterPush = 0;
			pushedContext = false;
		}
		try { z3Solver.pop (scopes); } catch (Z3Exception e) { e.printStackTrace (); }
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.SolverFunctions#pushContext()
	 */
	@Override
	public void pushContext ()
	{
		if (enableStatementStack == true)
			pushedContext = true;
		try { z3Solver.push (); } catch (Z3Exception e) { e.printStackTrace (); }
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.SolverFunctions#getModel()
	 */
	@Override
	public Map<String, String> getModel () { return getModel (z3Solver); }

	/**
	 * Generate an assertion in Z3 context.
	 * 
	 * @param expression expresssion to be asserted
	 */
	public void generateAssertion (BoolExpr expression)
	{
		try
		{
			z3Solver.add(expression);
			// z3Solver.assert_(id);
		}
		catch (Z3Exception e)
		{
			e.printStackTrace();
		}
		// try { z3Solver.assert_(id); } catch (Z3Exception e) { e.printStackTrace (); }				
		String str = expression.toString ();

		if (enableStatementStack == true)
		{
			contextStatements.add ("(assert " + str + ")");
			if (pushedContext)
				statementCountAfterPush ++;
		}
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.SolverFunctions#checkSat(int)
	 */
	@Override
	public SatResult checkSat (int timeOutInSeconds) 
	{		
		return check (z3Solver, timeOutInSeconds);
	}

	/**
	 * Get Z3 Solver statistics
	 * 
	 * @return Different statistics in String.
	 */
	public String getStatistics ()
	{
		Statistics stats=null;
		try 
		{
			stats = z3Solver.getStatistics ();
		} catch (Z3Exception e) { e.printStackTrace (); }
		return stats.toString ();
	}

	/**
	 * Reset the Z3 Solver.
	 */
	public void resetSolver()
	{
		try
		{
			z3Solver.reset();
			contextStatements.clear();
			// com.microsoft.z3.Native.resetMemory();			
		} catch (Z3Exception e) { e.printStackTrace(); }		
	}
}
