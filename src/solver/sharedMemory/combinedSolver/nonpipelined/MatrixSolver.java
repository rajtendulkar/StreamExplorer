package solver.sharedMemory.combinedSolver.nonpipelined;
import exploration.interfaces.oneDim.LatencyConstraints;
import exploration.interfaces.oneDim.ProcessorConstraints;
import exploration.interfaces.twoDim.LatProcConstraints;
import graphanalysis.TransformSDFtoHSDF;
import graphanalysis.properties.GraphAnalysisSdfAndHsdf;
import solver.SmtVariablePrefixes;

import java.util.*;

import com.microsoft.z3.*;

import solver.*;
import spdfcore.*;
import spdfcore.Channel.Link;
import spdfcore.stanalys.*;

/**
 * Matrix Solver is a SMT based solver which uses functions to solve
 * the scheduling problem. Imagine that you have a 2D matrix and you
 * place the tasks in this matrix, it determines the start time, 
 * order in which tasks execute etc.
 * 
 * The schedule matrix is filled up with end times of the tasks.
 * The initial values are filled with zero. An index of each task refers
 * to slot in the schedule matrix. The indices are unique also define
 * order of execution. However with two indices, tasks can have same 
 * start times.
 * 
 * Note : this solve we experimented with was slower than mutual exclusion.
 * However it is kept for reference purpose.
 * 
 * @author Pranav Tendulkar
 *
 */
public class MatrixSolver extends Z3Solver
					implements LatencyConstraints, ProcessorConstraints, LatProcConstraints 
{
	/**
	 * Application graph.
	 */
	protected Graph graph;
	/**
	 * Equivalent HSDF graph
	 */
	protected Graph hsdf;
	/**
	 * Solutions for Application graph.
	 */
	protected Solutions solutions;
	/**
	 * 
	 */
	private Map<Actor, HashSet<Actor>> predecessors;	
	/**
	 * Enable Tetris Symmetry for allocating processors to the tasks.
	 */
	public boolean tetrisSymmetry = false;
	/**
	 * Enable processor symmetry
	 */
	public boolean processorSymmetry = false;
	/**
	 * Enable task symmetry
	 */
	public boolean graphSymmetry = false;
	/**
	 * Use quantifier or not. Quantifier slows down the solver
	 */
	public boolean useQuantifier = true;
	
	/**
	 * Should we use a function to calculate maximum values among the 
	 * two values (true) or use if then else statement (false)  
	 */
	public boolean useMaxFunction = true;
	
	// This list contains the actors which are from HSDF graph.
	/**
	 * List of actors without successors.
	 */
	protected List<Actor> lastActorList = null;
	/**
	 * List of actors without predecessors.
	 */
	protected List<Actor> startActorList = null;
	

	/**
	 * SMT variable to calculate latency of the schedule
	 */
	protected IntExpr latencyDecl;
	/**
	 * SMT variable to calculate processors used in the schedule
	 */
	protected IntExpr totalProcDecl;
	
	/**
	 * Schedule matrix where the problem is solved 
	 */
	protected FuncDecl schedMatrixDecl;
	
	/**
	 * SMT function to calculate the maximum value among the two values
	 */
	protected FuncDecl maxFunctionDecl;
	
	/**
	 * SMT variables for index of the task 
	 */
	private Map<String, IntExpr> taskIndexDecl;
	/**
	 *  SMT variables for actor duration 
	 */
	private Map<String, IntExpr> durationDecl;
	/**
	 * SMT variables for allocate processor of the task
	 */
	private Map<String, IntExpr> cpuDecl;
	/**
	 * SMT variables for enable time of the task
	 */
	private Map<String, IntExpr> enableTimeDecl;
	/**
	 * SMT variables for symmetry
	 */
	private Map<String, IntExpr> symmetryDecl;
	
	/**
	 * Get SMT variable for latency calculation of the schedule.
	 * 
	 * @return variable for latency calculation of the schedule.
	 */
	public IntExpr getLatencyDeclId () { return latencyDecl; }
	
	/**
	 * Get SMT variable for number of processors used in the schedule.
	 * 
	 * @return variable for number of processors used in the schedule
	 */
	public IntExpr getProcDeclId () { return totalProcDecl; }
	
	/**
	 * Get SMT variable for the schedule matrix. 
	 * @return variable for the schedule matrix
	 */
	public FuncDecl getSchedMatrixDeclId () { return schedMatrixDecl; }
	
	/**
	 * Get the max function.
 	 * @return the max function
	 */
	public FuncDecl getMaxFunctionDeclId () { return maxFunctionDecl; }
	
	/**
	 * Get the SMT variable for processor allocated to an actor instance
	 * 
	 * @param name name of the actor
	 * @param instance instance id of the actor
	 * @return variable for processor allocated to an actor instance
	 */
	IntExpr cpuId (String name, int instance) 	 { return cpuDecl.get (SmtVariablePrefixes.cpuPrefix + name + "_" + Integer.toString (instance)); }
	
	/**
	 * Get the SMT variable for processor allocated to a task
	 * 
	 * @param name name of the task
	 * @return variable for processor allocated to a task
	 */
	IntExpr cpuId (String name) 	 { return cpuDecl.get (SmtVariablePrefixes.cpuPrefix + name); }
	
	/**
	 * Get SMT variable for duration of an actor.
	 * 
	 * @param name name of the actor
	 * @return variable for duration of an actor
	 */
	IntExpr durationId (String name)			 { return durationDecl.get (SmtVariablePrefixes.durationPrefix+ name); }
	
	/**
	 * Get SMT variable for index of an actor instance in the schedule matrix.
	 * 
	 * @param name name of the actor
	 * @param instance instance id
	 * @return variable for index of an actor instance
	 */
	IntExpr taskIndexId (String name, int instance) { return taskIndexDecl.get (SmtVariablePrefixes.taskIndexPrefix + name +"_"+ Integer.toString (instance)); }
	
	/**
	 * Get SMT variable for task index in the schedule matrix
	 * 
	 * @param name name of the task
	 * @return variable for task index
	 */
	IntExpr taskIndexId (String name) 	{ return taskIndexDecl.get (SmtVariablePrefixes.taskIndexPrefix + name); }
	
	/**
	 * Get SMT variable for maximum processor index where task can be allocated
	 *  
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable for maximum processor index where task can be allocated
	 */
	IntExpr maxCpuId (String name, int index) 	 { return symmetryDecl.get (SmtVariablePrefixes.maxCpuPrefix+ name+Integer.toString (index)); }
	
	/**
	 * Get SMT variable for enabled time for execution of actor instance.
	 * 
	 * @param name actor name
	 * @param instance instance id
	 * @return variable for enabled time for execution of actor instance
	 */
	IntExpr enableTimeId (String name, int instance) 
	{
		if (enableTimeDecl.containsKey (SmtVariablePrefixes.enableTimePrefix + name +"_"+ Integer.toString (instance)))
			return enableTimeDecl.get (SmtVariablePrefixes.enableTimePrefix + name +"_"+ Integer.toString (instance));
		else
			return null;
	}
	
	/**
	 * Get SMT variable for enabled time for execution of task.
	 * 
	 * @param name name of the task
	 * @return variable for enabled time for execution of task
	 */
	IntExpr enableTimeId (String name) 
	{
		if (enableTimeDecl.containsKey (SmtVariablePrefixes.enableTimePrefix + name))
			return enableTimeDecl.get (SmtVariablePrefixes.enableTimePrefix + name);
		else
			return null;
	}
	
	
	/**
	 * Build a matrix solver object.
	 * 
	 * @param inputGraph application graph SDF
	 */
	public MatrixSolver (Graph inputGraph)
	{
		graph = inputGraph;
		
		TransformSDFtoHSDF toHSDF = new TransformSDFtoHSDF ();
		hsdf = toHSDF.convertSDFtoHSDFWithUniqueChannels (graph);		
		
		GraphExpressions expressions = new GraphExpressions ();
		expressions.parse (graph);
		solutions = new Solutions ();
		solutions.setThrowExceptionFlag (false);	    		
		solutions.solve (graph, expressions);
				
		GraphAnalysisSdfAndHsdf analysis = new GraphAnalysisSdfAndHsdf (graph, solutions, hsdf);
		startActorList = analysis.findHsdfStartActors ();
		lastActorList = analysis.findHsdfEndActors ();
		
		taskIndexDecl = new TreeMap<String, IntExpr>();
		durationDecl = new TreeMap<String, IntExpr>();
		cpuDecl = new TreeMap<String, IntExpr>();
		predecessors = new HashMap<Actor, HashSet<Actor> >();
		enableTimeDecl = new TreeMap<String, IntExpr>();
		symmetryDecl = new TreeMap<String, IntExpr>();
		
		// calculate the predecessor list.
		generatePredecessorsList ();
	}
	
	/**
	 * Make a function application with two indices.
	 * For example func(0,1)
	 * 
	 * @param functionId function id
	 * @param index1 index 1
	 * @param index2 index 2
	 * @return function app with two indices
	 */
	private Expr functionApplication (FuncDecl functionId, Expr index1, Expr index2)
	{
		Expr result=null;
		Expr functionIndex[] = new Expr[2];
		functionIndex[0] = index1;
		functionIndex[1] = index2;
		
		try { result = ctx.mkApp (functionId, functionIndex); } catch (Z3Exception e) { e.printStackTrace (); }
		return result;
	}
	
    /**
     * Generate a list of predecessors for all HSDF actors
     */
    private void generatePredecessorsList ()
    {    
        Iterator<Actor> actrIter = hsdf.getActors ();
        while (actrIter.hasNext ())
        {   
            Actor actr = actrIter.next ();    

            HashSet<Actor> pred = new HashSet<Actor>();

            for (Link lnk : actr.getLinks (Port.DIR.IN))
                pred.add (lnk.getOpposite ().getActor ());
            predecessors.put (actr, pred);    
        }   
    }
	
	/**
	 * Define index variables for all the tasks.
	 * The indices for all the tasks are uniquely assigned such that
	 * now two indices are same. They define the slot in the schedule
	 * matrix.
	 */
	private void defineTaskIndex ()
	{
		contextStatements.add (";; Index Variables");
				
		// Start Times
		Iterator<Actor> actorIter = graph.getActors ();

		String arguments[] = new String[1];
		arguments[0] = "Int";
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();	
			int repCount = solutions.getSolution (actr).returnNumber ();

			for (int i=0;i<repCount;i++)
			{
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.taskIndexPrefix + actr.getName () +"_"+ Integer.toString (i), "Int");
				taskIndexDecl.put (SmtVariablePrefixes.taskIndexPrefix + actr.getName () +"_"+ Integer.toString (i), id);
			}
		}		
	}
	
	/**
	 * Define SMT variables for actor durations
	 */
	private void defineActorDuration ()
	{		
		// Define Actor Durations		
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();			
			try 
			{
				IntExpr id = ctx.mkInt (actr.getExecTime ());
				durationDecl.put (SmtVariablePrefixes.durationPrefix + actr.getName (), id);
			} catch (Z3Exception e) { e.printStackTrace (); }
		}	
	}
	
	/**
	 * Define SMT variables for processor allocated for the task
	 */
	private void defineCpuAllocation ()
	{
		contextStatements.add (";; Cpu Allocation Variables");
		
		String arguments[] = new String[1];
		arguments[0] = "Int";
		// Processor Allocated to each Instance.		
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();			
						
			int repCount = solutions.getSolution (actr).returnNumber ();			
			for (int i=0;i<repCount;i++)
			{
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.cpuPrefix + actr.getName () + "_" + Integer.toString (i), "Int");
				cpuDecl.put (SmtVariablePrefixes.cpuPrefix + actr.getName () + "_" + Integer.toString (i), id);
			}			
		}		
	}
	
	/**
	 * Define SMT variables tocalculate the enable time of actors
	 * such that all its precedence constraints are satisfied
	 */
	private void defineEnableTimes ()
	{
		contextStatements.add (";; Task Enable Variables");
		for (Actor actr: predecessors.keySet ())
		{
			if (enableTimeDecl.containsKey (SmtVariablePrefixes.enableTimePrefix+actr.getName ()))
				continue;
			
			HashSet<Actor> actrPredList = predecessors.get (actr);
			
			// Skip the start actors.
			if (actrPredList.size () == 0)
				continue;
			
			List<String> equalEnableActors = new ArrayList<String>();
			equalEnableActors.add (actr.getName ());
			for (Actor equalActr: predecessors.keySet ())
			{
				if (equalActr.equals (actr))
					continue;
				HashSet<Actor> equalActrPredList = predecessors.get (equalActr);
				
				if (actrPredList.size () == equalActrPredList.size ())
				{					
					if (actrPredList.containsAll (equalActrPredList) && equalActrPredList.containsAll (actrPredList) == true)
						equalEnableActors.add (equalActr.getName ());
				}
			}
			
			// enableTimePrefix
			if (equalEnableActors.size () > 1)
			{
				String actrNames = "";
				for (int i=0;i<equalEnableActors.size ();i++)
					actrNames = actrNames.concat (equalEnableActors.get (i));
				
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.enableTimePrefix + actrNames, "Int");
				
				for (int i=0;i<equalEnableActors.size ();i++)
					enableTimeDecl.put (SmtVariablePrefixes.enableTimePrefix + equalEnableActors.get (i), id);
			}
			else
			{
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.enableTimePrefix + actr.getName (), "Int");
				enableTimeDecl.put (SmtVariablePrefixes.enableTimePrefix + actr.getName (), id);
			}	
		}
	}
	
	/**
	 * Define upper and lower bound on the processor variables
	 */
	private void assertCpuBounds ()
	{
		contextStatements.add (";; Lower and Upper bounds on Cpu variables");
		String arguments[] = new String[1];
		arguments[0] = "Int";
		// Processor Allocated to each Instance.		
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();			
						
			int repCount = solutions.getSolution (actr).returnNumber ();			
			for (int i=0;i<repCount;i++)
			{
				IntExpr id = cpuId (actr.getName (), i);
				// (assert (and (>= cpu_A  0) (< cpu_A  totalProc)))
				try 
				{
					generateAssertion (ctx.mkAnd (ctx.mkGe (id, ctx.mkInt (0)), ctx.mkLt (id, getProcDeclId ())));
				} catch (Z3Exception e) { e.printStackTrace (); }
			}			
		}
	}
	
	/**
	 * Define bounds on the index variables for all the tasks
	 */
	private void assertTaskIndexBounds ()
	{
		contextStatements.add (";; Lower and Upper bounds on Index variables");
		int totalRepCount = hsdf.countActors ();
		IntExpr distinctArgs[] = new IntExpr [totalRepCount];
		int count = 0;
		
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();			
						
			int repCount = solutions.getSolution (actr).returnNumber ();			
			for (int i=0;i<repCount;i++)
			{
				IntExpr id = taskIndexId (actr.getName (), i);
				// (assert (and (> index_A  0) (<= index_A  5)))
				try 
				{
					generateAssertion (ctx.mkAnd (ctx.mkGt (id, ctx.mkInt (0)),
						ctx.mkLe (id, ctx.mkInt (totalRepCount))));
				} catch (Z3Exception e) { e.printStackTrace (); }
				distinctArgs[count++] = id;
			}
		
		}
		
		// (assert (distinct index_A index_B0 index_B1 index_B2 index_C))
		contextStatements.add (";; Index variables are distinct");
		try { generateAssertion (ctx.mkDistinct (distinctArgs)); } catch (Z3Exception e) { e.printStackTrace (); }			
	}
	
	/**
	 * Generate Task Symmetry constraints
	 */
	protected void graphSymmetryLexicographic ()
	{
		contextStatements.add (";; Graph Symmetry Constraints.");
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			if (solutions.getSolution (actr).returnNumber () > 1)
			{
				for (int i=1;i<solutions.getSolution (actr).returnNumber ();i++)
				{
					try 
					{
						// (assert (<= (schedMatrix cpuB_0 indexB_0) (schedMatrix cpuB_1 indexB_1)))
						generateAssertion (ctx.mkLe (
								(IntExpr)functionApplication (getSchedMatrixDeclId (), cpuId (actr.getName (), i-1), taskIndexId (actr.getName (), i-1)), 
								(IntExpr)functionApplication (getSchedMatrixDeclId (), cpuId (actr.getName (), i), taskIndexId (actr.getName (), i))));
						
						// (assert (< index_B0 index_B1))
						generateAssertion (ctx.mkLt (taskIndexId (actr.getName (), i-1), taskIndexId (actr.getName (), i)));
					} catch (Z3Exception e) { e.printStackTrace (); }	
				}
			}
		}
	}
	
	
	
	/**
	 * Set the initial values of the schedule matrix.
	 * All the initial values are set to zero.
	 * 
	 * @param numProcessors number of processors to be used.
	 */
	private void initialSchedMatrix (int numProcessors)
	{
		contextStatements.add (";; Initial values of the scheduling matrix");
		FuncDecl schedMatrixId = getSchedMatrixDeclId ();
		Expr funcArgs[] = new Expr[2];
		try
		{
			funcArgs[1] = ctx.mkInt (0);
			
			for (int i=0;i<numProcessors;i++)
			{
				funcArgs[0] = ctx.mkInt (i);
				generateAssertion (ctx.mkEq (ctx.mkApp (schedMatrixId, funcArgs), ctx.mkInt (0)));
			}
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
//	private void schedMatrixBoundConstraints (int numProcessors)
//	{
//		contextStatements.add (";; Lower and Upper Bounds on sched matrix variables");
//		FuncDecl schedMatrixId = getSchedMatrixDeclId ();
//		Expr funcArgs[] = new Expr[2];
//		
//		
//		for (int i=1;i<=hsdf.countActors ();i++)
//		{
//			for (int j=0;j<numProcessors;j++)
//			{
//				try
//				{
//					funcArgs[0] = ctx.mkInt (j);
//					funcArgs[1] = ctx.mkInt (i);
//					Expr funcId = ctx.mkApp (schedMatrixId, funcArgs);
//					generateAssertion (ctx.mkAnd (ctx.mkGe ((ArithExpr) funcId, ctx.mkInt (0)),
//								ctx.mkLe ((ArithExpr) funcId, getLatencyDeclId ())));
//				} catch (Z3Exception e) { e.printStackTrace (); }
//			}
//		}
//	}
	
	/**
	 * @param numProcessors
	 */
	private void calculateSchedMatrixNonIndexVar (int numProcessors)
	{
		contextStatements.add (";; Calculation of sched matrix values");
		Iterator<Actor> actorIter = hsdf.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			IntExpr cpuId = cpuId (actr.getName ());
			IntExpr indexId = taskIndexId (actr.getName ());
			
			Expr funcArgs1[] = new Expr[2];
			Expr funcArgs2[] = new Expr[2];
			
			for (int i=0;i<numProcessors;i++)
			{
				try 
				{
					funcArgs1[0] = ctx.mkInt (i);
					funcArgs2[0] = ctx.mkInt (i);
					
					funcArgs1[1] = indexId;
					funcArgs2[1] = ctx.mkSub (indexId, ctx.mkInt (1));
					
					generateAssertion (ctx.mkImplies (ctx.mkNot (ctx.mkEq (cpuId, ctx.mkInt (i))), 
						ctx.mkEq (ctx.mkApp (getSchedMatrixDeclId (), funcArgs1), 
								ctx.mkApp (getSchedMatrixDeclId (), funcArgs2))));
				} catch (Z3Exception e) { e.printStackTrace (); }	
			}			
		}		
	}
	
	/**
	 * Tetris symmetry is another kind of symmetry that is seen in 
	 * allocating the processor to a tasks, just like in tetris game.
	 * 
	 * @param numProcessors number of processors to use
	 */
	private void tetrisSymmetryConstraints (int numProcessors)
	{
		contextStatements.add (";; Tetris Symmetry Constraints");
		Iterator<Actor> hsdfActorList = hsdf.getActors ();
		while (hsdfActorList.hasNext ())
		{
			Actor actr = hsdfActorList.next ();
			IntExpr durId = durationId (actr.getName ().substring (0, actr.getName ().indexOf ("_")));
			IntExpr cpuId = cpuId (actr.getName ());
			IntExpr indexId = taskIndexId (actr.getName ());			
			
			for (int i=1;i<numProcessors;i++)
			{
				try
				{
					Expr funcArgs1[] = new Expr[2];
					Expr funcArgs2[] = new Expr[2];
					
					funcArgs1[0] = ctx.mkInt (i-1);
					funcArgs1[1] = indexId;
					
					funcArgs2[0] = cpuId;
					funcArgs2[1] = indexId;
					
					ArithExpr leftSide = ctx.mkAdd ((ArithExpr)ctx.mkApp (getSchedMatrixDeclId (), funcArgs1), durId);
					Expr rightSide = ctx.mkApp (getSchedMatrixDeclId (), funcArgs2);							
					
					BoolExpr ifStatement = ctx.mkGe (cpuId, ctx.mkInt (i));				
					BoolExpr thenStatement = ctx.mkGt (leftSide, (ArithExpr) rightSide);				
					generateAssertion (ctx.mkImplies (ifStatement, thenStatement));
				} catch (Z3Exception e) { e.printStackTrace (); }
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.ProcessorConstraints#generateProcessorConstraint(int)
	 */
	@Override
	public void generateProcessorConstraint (int numProcessors)
	{
		if (useQuantifier == false)
		{
			initialSchedMatrix (numProcessors);
			// schedMatrixBoundConstraints (numProcessors);
			calculateSchedMatrixNonIndexVar (numProcessors);
			// Tetris Symmetry
			if (tetrisSymmetry == true)
				tetrisSymmetryConstraints (numProcessors);
		}
		
		try
		{
			generateAssertion (ctx.mkEq (totalProcDecl, ctx.mkInt (numProcessors)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}	

	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.LatencyConstraints#generateLatencyConstraint(int)
	 */
	@Override
	public void generateLatencyConstraint (int latencyConstraint)
	{
		try
		{
			generateAssertion (ctx.mkLe (latencyDecl, ctx.mkInt (latencyConstraint)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * Generate latency calculation for the schedule
	 */
	private void generateLatencyCalculation ()
	{
		if (lastActorList.size () == 0)
		{
			throw new RuntimeException ("Unable to detect ending actors. We have to use older function generateLatencyCalculation (numProcessors). ");
		}
		else if (lastActorList.size () == 1)
		{
			try
			{
				generateAssertion (ctx.mkEq (getLatencyDeclId (), 
					functionApplication (getSchedMatrixDeclId (), cpuId (lastActorList.get (0).getName ()), 
							taskIndexId (lastActorList.get (0).getName ()))));
			} catch (Z3Exception e) { e.printStackTrace (); }
		}
		else
		{
			BoolExpr orArgs[] = new BoolExpr[lastActorList.size ()];
			BoolExpr andArgs[] = new BoolExpr[lastActorList.size ()];		
			
			for (int i=0;i<lastActorList.size ();i++)
			{
				try
				{
					orArgs[i] = ctx.mkEq (getLatencyDeclId (),
						functionApplication (getSchedMatrixDeclId (), cpuId (lastActorList.get (i).getName ()), 
								taskIndexId (lastActorList.get (i).getName ())));
					andArgs[i] = ctx.mkGe (getLatencyDeclId (), 
						(IntExpr)functionApplication (getSchedMatrixDeclId (), cpuId (lastActorList.get (i).getName ()), 
								taskIndexId (lastActorList.get (i).getName ())));
				} catch (Z3Exception e) { e.printStackTrace (); }
			}
			
			// (assert (or (= latency (sched_matrix 0 5)) (= latency (sched_matrix 1 5))))
			// (assert (and (>= latency (sched_matrix 0 5)) (>= latency (sched_matrix 1 5))))
			try 
			{
				generateAssertion (ctx.mkOr (orArgs));
				generateAssertion (ctx.mkAnd (andArgs));
			} catch (Z3Exception e) { e.printStackTrace (); }
		}
	}
	
//	private void generateLatencyCalculation (int processors)
//	{
//		contextStatements.add (";; Calculation of Latency of the schedule");
//		int totalRepCount = hsdf.countActors ();
//		long orArgs[] = new long[processors];
//		long andArgs[] = new long[processors];		
//		
//		for (int i=0;i<processors;i++)
//		{
//			orArgs[i] = ctx.mkEq (getLatencyDeclId (),
//					functionApplication (getSchedMatrixDeclId (), ctx.mkInt (i), ctx.mkInt (totalRepCount)));
//			andArgs[i] = ctx.mkGe (getLatencyDeclId (), 
//					functionApplication (getSchedMatrixDeclId (), ctx.mkInt (i), ctx.mkInt (totalRepCount)));			
//		}
//		
//		// For 2 processors.
//		// (assert (or (= latency (sched_matrix 0 5)) (= latency (sched_matrix 1 5))))
//		// (assert (and (>= latency (sched_matrix 0 5)) (>= latency (sched_matrix 1 5))))
//		generateAssertion (ctx.mkOr (orArgs));
//		generateAssertion (ctx.mkAnd (andArgs));
//	}	
	
	/**
	 * Generate precedence constraints for the tasks
	 */
	private void actorPrecedenceConstraints ()
	{
		// Actor Precedences
		Iterator<Channel> iterChnnl = graph.getChannels ();
		while (iterChnnl.hasNext ())
		{
			Channel chnnl = iterChnnl.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();			

			HashMap<Integer, List<Integer>> precedenceList = new HashMap<Integer, List<Integer>>();
			// Initialize the List.
			for (int i=0;i<solutions.getSolution (dstActor).returnNumber ();i++)
			{
				List<Integer> tempIntList = new ArrayList<Integer>();
				precedenceList.put (i, tempIntList);
			}

			if (chnnl.getInitialTokens () == 0)
			{
				// Since we have equal repetition count, the precedence is one is to one.
				if (solutions.getSolution (srcActor).returnNumber () == solutions.getSolution (dstActor).returnNumber ())
				{					
					for (int i=0;i<solutions.getSolution (srcActor).returnNumber ();i++)					
						precedenceList.get (i).add (i);
				}
				else
				{
					// Since we have unequal rates and repetition count, we have to schedule accordingly.
					// The Tokens are produced on FIFO order, thus tokens generated by first producer instance
					// should be consumed by first consumer. if extra left, it can be consumed by second consumer
					// if they are less than consumption rate, the first consumer will then consume from
					// second producer instance.
					int repDst = solutions.getSolution (dstActor).returnNumber ();
					int rateSrc = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
					int rateDst = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());

					int srcIndex = 0;
					int srcTokens = rateSrc;

					for (int i=0;i<repDst;i++)
					{
						int dstTokens = rateDst;

						while (dstTokens > 0)
						{
							precedenceList.get (i).add (srcIndex);						

							if (dstTokens <= srcTokens )
							{
								srcTokens -= dstTokens;
								dstTokens = 0;								
							}
							else
							{
								dstTokens -= srcTokens;
								srcTokens = 0;
							}

							if (srcTokens == 0)
							{
								srcIndex ++;
								srcTokens += rateSrc;
							}							
						}
					}					
				}
			}
			else
			{
				// Check if this is Stateful actor?
				if (srcActor == dstActor)
				{
					// we can have more than one initial tokens on self-edge.
					int initialTokens = chnnl.getInitialTokens ();
					// the production rate has to be equal to consumption rate. otherwise graph is inconsistent.
					int portRate = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());					
					for (int i=(initialTokens/portRate);i<solutions.getSolution (srcActor).returnNumber ();i++)					
						precedenceList.get (i).add (i-(initialTokens/portRate));
				}
				else
				{
					// With initial tokens, we need to consider multiple iterations.
					int repDst = solutions.getSolution (dstActor).returnNumber ();					
					int rateSrc = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
					int rateDst = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());
					int initialTokens = chnnl.getInitialTokens ();

					int srcIndex = 0;
					int srcTokens = rateSrc;

					for (int i=0;i<repDst;i++)
					{
						if (initialTokens >= rateDst)
						{
							initialTokens -= rateDst;							
							continue;
						}

						int dstTokens = rateDst;

						while (dstTokens > 0)
						{
							precedenceList.get (i).add (srcIndex);						

							if (dstTokens <= (srcTokens + initialTokens ))
							{								
								srcTokens -= (dstTokens - initialTokens);
								dstTokens = 0;
								initialTokens = 0;
							}
							else
							{
								dstTokens -= (srcTokens + initialTokens);
								srcTokens = 0;
								initialTokens = 0;								
							}

							if (srcTokens == 0)
							{
								srcIndex ++;
								srcTokens += rateSrc;
							}							
						}						
					}
				}				
			}

			// Actor Precedences 
			// (assert (and (>= (xB 0) (+ (xA 0) dA)) (>= (xB 0) (+ (xA 1) dA))))
			// (assert (and (>= (xB 1) (+ (xA 1) dA)) (>= (xB 1) (+ (xA 2) dA))))

			//Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			//Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
			
			contextStatements.add (";; Precedence constraints on Index variables.");

			for (int i=0;i<solutions.getSolution (dstActor).returnNumber ();i++)
			{
				if (precedenceList.get (i).size () == 0)
					continue;

				for (int j=0;j<precedenceList.get (i).size ();j++)
				{
					
//					long srcId = functionApplication (getSchedMatrixDeclId (), cpuId (srcActor.getName (), precedenceList.get (i).get (j)), 
//																			 taskIndexId (srcActor.getName (), precedenceList.get (i).get (j)));
//					
//					long dstId = ctx.mkSub (functionApplication (getSchedMatrixDeclId (),cpuId (dstActor.getName (), i), 
//													taskIndexId (dstActor.getName (), i)), durationId (dstActor.getName ()));
//					
//					generateAssertion (ctx.mkGe (dstId, srcId));
					
					// (assert (< indexA_0 indexB_0))
					try
					{
						generateAssertion (ctx.mkLt (taskIndexId (srcActor.getName (), precedenceList.get (i).get (j)), 
																taskIndexId (dstActor.getName (), i)));
					} catch (Z3Exception e) { e.printStackTrace (); }
				}
			}			
		}		
	}
	
	/**
	 * 
	 */
	private void schedMatrixConstraints ()
	{
		// (assert (forall ((x Int)(y Int)) (=> (and (>= x 0) (< x totalProc)) (= (sched_matrix x y) 0))))
		
		try
		{
			IntExpr xVar =  (IntExpr) addVariableDeclaration ("x", "Int");
			IntExpr yVar =  (IntExpr) addVariableDeclaration ("y", "Int");
		
			Expr args[] = new IntExpr[2];
			args[0] = xVar;
			args[1] = yVar;
			
			BoolExpr implication = ctx.mkImplies (
						ctx.mkAnd (ctx.mkGe (xVar, ctx.mkInt (0)), 
							ctx.mkLt (xVar, getProcDeclId ())),						 
							ctx.mkEq (functionApplication (getSchedMatrixDeclId (), xVar, ctx.mkInt (0)), ctx.mkInt (0)));
			
			generateAssertion (ctx.mkForall (args, implication, 0, null, null, null, null));
			
			// (assert (forall ((x Int)(y Int)) (=> (and (< x 0) (>= x totalProc) (< y 0) (> y 14)) (= (schedMatrix x y) 0))))		
			implication = ctx.mkImplies (
					ctx.mkAnd (ctx.mkLt (xVar, ctx.mkInt (0)), 
							ctx.mkGe (xVar, getProcDeclId ()), 
							ctx.mkLt (yVar, ctx.mkInt (0)),
							ctx.mkGt (yVar, ctx.mkInt (hsdf.countActors ()))), 
							ctx.mkEq (functionApplication (getSchedMatrixDeclId (), xVar, yVar), ctx.mkInt (0)));
			
			generateAssertion (ctx.mkForall (args, implication, 0, null, null, null, null));
			
			// (assert (forall ((x Int)(y Int)) (>= (sched_matrix x y) 0)))		
			Expr statement = ctx.mkGe ((ArithExpr)functionApplication (getSchedMatrixDeclId (), xVar, yVar), ctx.mkInt (0));
			generateAssertion (ctx.mkForall (args, statement, 0, null, null, null, null));
		} catch (Z3Exception e) { e.printStackTrace (); }	
	}
	
	/**
	 * Generate constraints for lower and upper bounds on enable times
	 */
	private void enableTimeBounds ()
	{
		contextStatements.add (";; Lower and Upper Bounds on the Enable Times.");
		
		HashSet<Expr> uniqueEnableTimeId = new HashSet<Expr>();
		
		for (String key : enableTimeDecl.keySet ())
		{
			Expr id = enableTimeDecl.get (key);
			uniqueEnableTimeId.add (id);
		}
		
		Iterator<Expr> longIter = uniqueEnableTimeId.iterator ();
		while (longIter.hasNext ())
		{
			Expr id = longIter.next ();
			try
			{
				generateAssertion (ctx.mkAnd (ctx.mkGe ((ArithExpr) id, ctx.mkInt (0)), ctx.mkLt ((ArithExpr) id, getLatencyDeclId ())));
			} catch (Z3Exception e) { e.printStackTrace (); }
		}		
	}
	
	/**
	 *  Generate tetris symmetry constraints
	 */
	private void tetrisSymmetryConstraints ()
	{
		// (assert (forall ((x Int)) (=> (and (>= x 0) (< x cpu_B0)) 
		//		(> (+ (sched_matrix x index_B0) dB) (max_integ (sched_matrix cpu_B0 index_B0) (+ enable_B0_B1_B2 dB))))))
		Iterator<Actor> hsdfActorList = hsdf.getActors ();
		while (hsdfActorList.hasNext ())
		{
			Actor actr = hsdfActorList.next ();
			
			try
			{
			
				IntExpr durId = durationId (actr.getName ().substring (0, actr.getName ().indexOf ("_")));
				IntExpr enableId = enableTimeId (actr.getName ());			
			
				IntExpr xVar = (IntExpr) addVariableDeclaration ("x", "Int");
				Expr[] args = new Expr[1];
				args[0] = xVar;
				
				IntExpr funcArgs[] = new IntExpr[2];
				funcArgs[0] = xVar;
				funcArgs[1] = taskIndexId (actr.getName ());
				
				// (sched_matrix x index_A0)
				Expr func1 = functionApplication (getSchedMatrixDeclId (), xVar, taskIndexId (actr.getName ()));
				// (sched_matrix cpu_A0 index_A0)
				Expr func2 = functionApplication (getSchedMatrixDeclId (), cpuId (actr.getName ()), taskIndexId (actr.getName ()));			
				BoolExpr thenStatement = null;
			
				BoolExpr andArgs[] = new BoolExpr[2];
				andArgs[0] = ctx.mkGe (xVar, ctx.mkInt (0));
				andArgs[1] = ctx.mkLt (xVar, cpuId (actr.getName ()));
				
				if (enableId == null)
				{
					thenStatement = ctx.mkGt ((ArithExpr)func1, (ArithExpr)func2);
				}
				else
				{
					if (useMaxFunction == true)
					{
						Expr func = functionApplication (getMaxFunctionDeclId (), func2, ctx.mkAdd (enableId, durId));
						thenStatement = ctx.mkGt (ctx.mkAdd ((ArithExpr)func1, durId), (ArithExpr) func);
					}
					else
					{
						thenStatement = (BoolExpr) ctx.mkITE (
							ctx.mkGt ((ArithExpr) func2, ctx.mkAdd (enableId, durId)), 
							func2, 
							ctx.mkAdd (enableId, durId));
					}
				}
				
				generateAssertion (ctx.mkForall (args, ctx.mkImplies (ctx.mkAnd (andArgs), thenStatement), 0, null, null, null, null));
			} catch (Z3Exception e) { e.printStackTrace (); }				
		}
	}
	
	/**
	 * 
	 */
	private void calculateSchedMatrixIndexVar ()
	{
		contextStatements.add (";; Scheduling matrix calculations, which contains end times of each actor.");
		Iterator<Actor> actorIter = hsdf.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			IntExpr enableId = enableTimeId (actr.getName ());
			IntExpr cpuId = cpuId (actr.getName ());
			IntExpr indexId = taskIndexId (actr.getName ());
			IntExpr durId = durationId (actr.getName ().substring (0, actr.getName ().indexOf ("_")));
			
			// (assert (= (sched_matrix cpu_B0 index_B0) (max_integ (+ (sched_matrix cpu_B0 (- index_B0 1)) dB) (+ enable_B0_B1_B2 dB))))
			try
			{
				Expr leftSide = functionApplication (getSchedMatrixDeclId (), cpuId, indexId);
				IntExpr rightSide = null;
				if (enableId == null)
				{
					rightSide = (IntExpr)ctx.mkAdd ((ArithExpr)functionApplication (getSchedMatrixDeclId (), 
										cpuId, ctx.mkSub (indexId, ctx.mkInt (1))), durId);
				}
				else
				{
					if (useMaxFunction == true)
					{
						rightSide = (IntExpr)functionApplication (getMaxFunctionDeclId (),
							ctx.mkAdd ((ArithExpr)functionApplication (getSchedMatrixDeclId (), cpuId, ctx.mkSub (indexId, ctx.mkInt (1))), durId), 
							ctx.mkAdd (enableId, durId));
					}
					else
					{
						rightSide = (IntExpr) ctx.mkITE (
								ctx.mkGt ((ArithExpr)functionApplication (getSchedMatrixDeclId (), cpuId, ctx.mkSub (indexId, ctx.mkInt (1))), enableId), 
								ctx.mkAdd ((ArithExpr)functionApplication (getSchedMatrixDeclId (), cpuId, ctx.mkSub (indexId, ctx.mkInt (1))), durId), 
								ctx.mkAdd (enableId, durId));
					}
				}
				
				generateAssertion (ctx.mkEq (leftSide, rightSide));
				
			} catch (Z3Exception e) { e.printStackTrace (); }
		}
	}
	
	/**
	 * 
	 */
	private void calculateSchedMatrixNonIndexVar ()
	{
		Iterator<Actor> actorIter = hsdf.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			IntExpr cpuId = cpuId (actr.getName ());
			IntExpr indexId = taskIndexId (actr.getName ());
			
			try
			{
			
				// Max Function Definition		
				IntExpr xVar = (IntExpr) addVariableDeclaration ("x", "Int");
				Expr args[] = new Expr[1];
				args[0] = xVar;
				
				BoolExpr ifStatement = ctx.mkAnd (ctx.mkGe (xVar, ctx.mkInt (0)), 
						ctx.mkLt (xVar, getProcDeclId ()), ctx.mkNot (ctx.mkEq (xVar, cpuId)));
				BoolExpr thenStatement = ctx.mkEq (functionApplication (getSchedMatrixDeclId (), xVar, indexId), 
						functionApplication (getSchedMatrixDeclId (), xVar, ctx.mkSub (indexId, ctx.mkInt (1))));
				
				// (assert (forall ((x Int)) (=> (and (>= x 0) (< x totalProc) (not (= x cpu_A))) 
				//						(= (sched_matrix x index_A) (sched_matrix x (- index_A 1))))))
				generateAssertion (ctx.mkForall (args, ctx.mkImplies (ifStatement, thenStatement), 0, null, null, null, null));
			} catch (Z3Exception e) { e.printStackTrace (); }	
		}		
	}
	
	/**
	 * Calculate enable times of the tasks
	 */
	private void calculateEnableTimes ()
	{
		contextStatements.add (";; Task Enable Time Calculation.");
		HashSet<IntExpr> enableFinishedIds = new HashSet<IntExpr>();
		for (Actor actr : predecessors.keySet ())
		{
			IntExpr enableId = enableTimeId (actr.getName ());
			if (enableFinishedIds.contains (enableId) == false)
			{
				try 
				{
					HashSet<Actor> predList = predecessors.get (actr);
					
					// Skip the start actors.
					if (predList.size () == 0)
						continue;
					
					if (predList.size () == 1)
					{
						Iterator<Actor>actrIter = predList.iterator ();
						while (actrIter.hasNext ())
						{
							Actor predActr = actrIter.next ();
							generateAssertion (ctx.mkEq (enableId, functionApplication (getSchedMatrixDeclId (), cpuId (predActr.getName ()), 
																			taskIndexId (predActr.getName ()))));
						}
					}
					else
					{
						// calculate the max
						BoolExpr orArgs[] = new BoolExpr [predList.size ()];
						BoolExpr andArgs[] = new BoolExpr [predList.size ()];
						int count = 0;
						
						for (Actor predActor : predList)
						{
							andArgs[count] = ctx.mkGe (enableId, 
													(ArithExpr) functionApplication (getSchedMatrixDeclId (), 
															cpuId (predActor.getName ()), 
															taskIndexId (predActor.getName ())));
							
							orArgs[count++] = ctx.mkEq (enableId, 
									functionApplication (getSchedMatrixDeclId (), 
											cpuId (predActor.getName ()), 
											taskIndexId (predActor.getName ())));
						}
						
						generateAssertion (ctx.mkAnd (andArgs));
						generateAssertion (ctx.mkOr (orArgs));					
					}				
					enableFinishedIds.add (enableId);
				} catch (Z3Exception e) { e.printStackTrace (); }
			}			
		}
	}
	
	/**
	 * Define max function which calculates the maximum value
	 * between two integers
	 */
	private void defineMaxFunction ()
	{
		try
		{
			Sort argType[] = new Sort[2];
			argType[0] = ctx.getIntSort ();
			argType[1] = ctx.getIntSort ();
			
			maxFunctionDecl = ctx.mkFuncDecl (SmtVariablePrefixes.maxFunctionPrefix, argType, ctx.getIntSort ());
			
			// Max Function Definition		
			IntExpr xVar = (IntExpr) addVariableDeclaration ("x", "Int");
			IntExpr yVar = (IntExpr) addVariableDeclaration ("y", "Int");
			Expr args[] = new Expr[2];
			args[0] = xVar;
			args[1] = yVar;
			
			Expr fapp = ctx.mkApp (maxFunctionDecl, args);
			
			Expr ite = ctx.mkITE (ctx.mkLt (yVar, xVar), xVar, yVar);
			Expr eq = ctx.mkEq (fapp, ite);
			generateAssertion (ctx.mkForall (args, eq, 0, null, null, null, null));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * Generate SMT variables for processor symmetry
	 */
	private void generateProcessorSymmetryDefinitions ()
	{
		contextStatements.add (";; Processor Symmetry Variables");
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{				
			Actor actr = actorIter.next ();
			int repCount = solutions.getSolution (actr).returnNumber ();
			for (int i=0;i<repCount;i++)
			{
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.maxCpuPrefix + actr.getName () + Integer.toString (i), "Int");
				symmetryDecl.put (SmtVariablePrefixes.maxCpuPrefix+ actr.getName () +Integer.toString (i), id);
			}
		}
	}
	
	/**
	 * Generate constraints for processor symmetry
	 */
	private void processorSymmetryConstraints ()
	{
		contextStatements.add (";; Processor Symmetry Constraints.");
		IntExpr prevMaxCpuId=null, prevCpuId=null, currCpuId=null, currMaxCpuId=null;
		int actorCount = 0;		
		Iterator<Actor> actorIter = graph.getActors ();
		
		try
		{

			while (actorIter.hasNext ())
			{				
				Actor actr = actorIter.next ();
				int repCount = solutions.getSolution (actr).returnNumber ();
				
				for (int i=0;i<repCount;i++)
				{
					currMaxCpuId = maxCpuId (actr.getName (), i);
					currCpuId 	 = cpuId (actr.getName (), i);
	
					if (actorCount == 0)
					{
						// This is a first actor instance.									
						generateAssertion (ctx.mkEq (currCpuId, ctx.mkInt (0)));				
					}
					else if (actorCount == 1)
					{					
						// This is second actor instance.
						generateAssertion (ctx.mkLe (currCpuId, ctx.mkAdd (currMaxCpuId, ctx.mkInt (1))));					
						generateAssertion (ctx.mkEq (currMaxCpuId, prevCpuId));					
					}
					else
					{
						BoolExpr orArgs[] = new BoolExpr[2];
						BoolExpr andArgs[] = new BoolExpr[2];					
	
						orArgs[0]  = ctx.mkEq (currMaxCpuId, prevCpuId);
						orArgs[1]  = ctx.mkEq (currMaxCpuId, prevMaxCpuId);
						andArgs[0] = ctx.mkGe (currMaxCpuId, prevCpuId);
						andArgs[1] = ctx.mkGe (currMaxCpuId, prevMaxCpuId);
	
						generateAssertion (ctx.mkLe (currCpuId, ctx.mkAdd (currMaxCpuId, ctx.mkInt (1))));					
						generateAssertion (ctx.mkOr (orArgs));
						generateAssertion (ctx.mkAnd (andArgs));					
					}
	
					prevMaxCpuId = currMaxCpuId;
					prevCpuId = currCpuId;
	
					actorCount++;
				}
			}
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * New type of symmetry : index symmetry
	 */
	private void indexSymmetry ()
	{
		contextStatements.add (";; Index Symmetry.");
		// Create a list of actors.
		Iterator<Actor> actorIter = graph.getActors ();	
		List<Actor> actrList = new ArrayList<Actor>();
		while (actorIter.hasNext ())
			actrList.add (actorIter.next ());
		
		for (int i=0;i<actrList.size ();i++)
		{
			Actor actr = actrList.get (i);
			int repCount = solutions.getSolution (actr).returnNumber ();
			
			for (int j=1;j<repCount;j++)
			{
				try
				{
					// we can compare end times instead of start for the tasks of same actor.
					BoolExpr leftSide = ctx.mkLe (
							(ArithExpr)functionApplication (getSchedMatrixDeclId (), cpuId (actr.getName (), j), taskIndexId (actr.getName (), j-1)), 
							(ArithExpr)functionApplication (getSchedMatrixDeclId (), cpuId (actr.getName (), j), taskIndexId (actr.getName (), j)));
					
					BoolExpr rightSide = ctx.mkLt (taskIndexId (actr.getName (), j-1), taskIndexId (actr.getName (), j));
					
					generateAssertion (ctx.mkIff (leftSide, rightSide));
				} catch (Z3Exception e) { e.printStackTrace (); }
				// generateAssertion (ctx.makeImplication (leftSide, rightSide));
				// generateAssertion (ctx.makeImplication (rightSide, leftSide));
				
			}
			
			for (int j=i+1;j<actrList.size ();j++)
			{
				Actor actr2 = actrList.get (j);
				int repCount2 = solutions.getSolution (actr2).returnNumber ();
				
				for (int k=0;k<repCount;k++)
				{
					for (int l=0;l<repCount2;l++)
					{
						try
						{
							BoolExpr leftSide = ctx.mkLe (
								ctx.mkSub ((ArithExpr)functionApplication (getSchedMatrixDeclId (), cpuId (actr.getName (), k), taskIndexId (actr.getName (), k)), durationId (actr.getName ())), 
								ctx.mkSub ((ArithExpr)functionApplication (getSchedMatrixDeclId (), cpuId (actr2.getName (), l), taskIndexId (actr2.getName (), l)), durationId (actr2.getName ())));
						
							BoolExpr rightSide = ctx.mkLt (taskIndexId (actr.getName (), k), taskIndexId (actr2.getName (), l));
						
							generateAssertion (ctx.mkIff (leftSide, rightSide));
							//generateAssertion (ctx.mkImplies (leftSide, rightSide));
							//generateAssertion (ctx.mkImplies (rightSide, leftSide));
						} catch (Z3Exception e) { e.printStackTrace (); }
					}
				}				
			}			
		}
	}
	
	/**
	 * Generate all the constraints for non-pipelined scheduling
	 */
	public void assertNonPipelineConstraints ()
	{
		// Define Task Index
		defineTaskIndex ();
		
		// Define Actor Durations.
		defineActorDuration ();
		
		// Define Enable Times
		defineEnableTimes ();
		
		// CPU Allocation Definitions 
		defineCpuAllocation ();
		
		if (processorSymmetry == true)
			generateProcessorSymmetryDefinitions ();
		
		try
		{		
			// Generate Other Definitions.
			contextStatements.add (";; Latency of the Schedule");
			latencyDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.latencyPrefix, "Int");	
			contextStatements.add (";; Processors Used in the Schedule");
			totalProcDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.totalProcPrefix, "Int");
			
			
			contextStatements.add (";; Scheduling Matrix");
			Sort argType[] = new Sort[2];
			argType[0] = ctx.getIntSort ();
			argType[1] = ctx.getIntSort ();
			schedMatrixDecl = ctx.mkFuncDecl (SmtVariablePrefixes.schedMatrixPrefix, argType, ctx.getIntSort ());
		} catch (Z3Exception e) { e.printStackTrace (); }	
		
		if (useMaxFunction == true)
			defineMaxFunction ();		
		
		// Index Upper and Lower bound
		assertTaskIndexBounds ();
		
		// Cpu Upper and Lower Bound
		assertCpuBounds ();		
		
		// Index Precedence Constraints
		actorPrecedenceConstraints ();
		
		if (useQuantifier == true)
		{
			// Initial scheduling matrix is zero.
			schedMatrixConstraints ();
			
			// Calculate scheduling matrix variables
			calculateSchedMatrixNonIndexVar ();
			
			// Tetris Symmetry
			if (tetrisSymmetry == true)
				tetrisSymmetryConstraints ();
		}
		
		// Calculate scheduling matrix variables
		calculateSchedMatrixIndexVar ();			
			
		// Lower and Upper Bounds on enable times
		enableTimeBounds ();		
		
		// Calculate Enable Times
		calculateEnableTimes ();
		
		// Graph Symmetry
		if (graphSymmetry == true)
			graphSymmetryLexicographic ();
		
		// Processor Symmetry
		if (processorSymmetry == true)
			processorSymmetryConstraints ();
		
		indexSymmetry ();

		// Assert that Latency >= (Max Latency / No of Processors)
		int maxLatency = 0;
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			maxLatency += (solutions.getSolution (actr).returnNumber () * actr.getExecTime ());
		}

		contextStatements.add (";; Lower Bound on Latency.");
		try
		{
			generateAssertion (ctx.mkGe (getLatencyDeclId (), 
				ctx.mkDiv (ctx.mkInt (maxLatency),getProcDeclId ())));
		} catch (Z3Exception e) { e.printStackTrace (); }
		
		generateLatencyCalculation ();
	}
	
	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.LatencyConstraints#getLatency(java.util.Map)
	 */
	@Override
	public int getLatency (Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.latencyPrefix));
	}
	
	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.ProcessorConstraints#getProcessors(java.util.Map)
	 */
	@Override
	public int getProcessors (Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.totalProcPrefix));
	}
}
	
