package solver;

import java.util.*;

import solver.Z3Solver.SatResult;
import com.microsoft.z3.*;

/**
 * This class contains a Z3 Solver context.
 * A Z3 context contains a lot of assertions
 * 
 * @author Pranav Tendulkar
 *
 */
public class Z3Context
{
	/**
	 * Z3 Context.
	 */
	protected Context ctx; 

	/**
	 * Build Z3 Context
	 */
	public Z3Context ()
	{
		// Enable Z3 Solver to produce Models on a SAT answer.
		HashMap<String, String> cfg = new HashMap<String, String>();
		cfg.put ("MODEL", "true");
		try 
		{
			ctx = new Context (cfg);
		} catch (Z3Exception e) { e.printStackTrace (); }		
	}
	
	/**
	 * Set a Random seed for the Solver.
	 * 
	 * @param solver Z3 Solver
	 * @param seed seed value
	 */
	public void setRandomSeed (Solver solver, int seed)
	{
		Params arg=null;
		try 
		{
			arg = ctx.mkParams ();
			arg.add (":random-seed", seed);
			solver.setParameters (arg);
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/**
	 * Get Version of the Z3 Solver.
	 * 
	 * @return version string
	 */
	public String getVersion () { return Version.getString (); }	

	/**
	 * Parse a Model from the model class to a hash map.
	 * 
	 * @param model model returned by the Z3 Solver.
	 * @return HashMap representation of the model.
	 */
	private Map<String, String> parseModel (Model model)
	{
		Map<String, String> result = new TreeMap<String, String>();
		
		FuncDecl[] decl;
		try 
		{
			decl = model.getDecls ();
		
			for (int i=0;i<decl.length;i++)
			{
				if (decl[i].getArity () > 0)
				{
					// This is a function.
					String value = model.getFuncInterp (decl[i]).toString ();				
					value = value.replaceAll ("\\[", "");
					value = value.replaceAll ("\\]", "");
					value = value.replaceAll (" ", "");               // remove spaces, too,
					                                                 // no need to trim later
					int start = 0;
					while (start < value.length ()) 
					{
					    int arrow = value.indexOf ("->", start);      // next -> after start
					    int comma = value.indexOf (",", arrow);       // next comma after ->
					    comma = comma > -1 ? comma : value.length (); // final segment?
				
					    String segment = value.substring (start, comma);
					    String key = segment.split ("->")[0];         // before ->
					    String val = segment.split ("->")[1];         // after ->
				
					    if (key.contains ("else") == false)
					    {
					        String[] keys = key.split (",");
					        // System.out.println (Arrays.toString (keys) + ": " + val);
					        result.put (("("+decl[i].getName ().toString ()+" " + Arrays.toString (keys)+")"), val);
					    }
					    else
					    {
					    	// the last part which contains else, which we ignore.
					    }
					    start = comma + 1;                           // continue after segment
					}							
				}
				else
					result.put (decl[i].getName ().toString (), model.getConstInterp (decl[i]).toString ());							
			}			
		} catch (Z3Exception e) { e.printStackTrace (); }		
		
		return result;
	}	

	/**
	 * Assert a SMT Query in the Solver.
	 * 
	 * @param solver Z3 Solver instance
	 * @param timeOutInSeconds time out in seconds
	 * @return Result from the Z3 Solver for the query.
	 */
	public SatResult check (Solver solver, int timeOutInSeconds)
	{	
		SatResult result = SatResult.UNKNOWN;	
		try 
		{	
			
			if (timeOutInSeconds != 0)
			{
				// We have to set the Solver Time Out
				// Params p = ctx.mkParams ();
				// p.add ("soft_timeout", (timeOutInSeconds));				
				// p.add (":soft_timeout", (timeOutInSeconds));
				//p.add (":solver2_timeout", (timeOutInSeconds));
				// solver.setParameters (p);
				
				// This is new way to set timeout?
				ctx.updateParamValue("timeout", Integer.toString(timeOutInSeconds * 1000));
			}
			
			Status satResult = solver.check ();
			
			// if True
			if (satResult == Status.SATISFIABLE)
				result = SatResult.SAT;			
			else if (satResult == Status.UNSATISFIABLE)
				result = SatResult.UNSAT;
			else
			{			
				String reason=null;
				
				reason = solver.getReasonUnknown ();				
				
				if (reason.compareToIgnoreCase ("canceled") == 0)
					result = SatResult.TIMEOUT;
				else 
					result = SatResult.UNKNOWN;
			}		
		} catch (Z3Exception e) { e.printStackTrace (); }
		
		return result;
	}

	/**
	 * Get a Model after we have a SAT answer.
	 * 
	 * @param solver Z3 Solver
	 * @return Map containing a variable and its assignment
	 */
	public Map<String, String> getModel (Solver solver) 
	{
		Map<String, String>modelMap=null;
		try 
		{
			Model model = solver.getModel ();			
			modelMap = parseModel (model);
		} catch (Z3Exception e) { e.printStackTrace (); }		
		return modelMap;		
	}	
}
