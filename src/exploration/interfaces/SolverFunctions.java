package exploration.interfaces;

import java.util.Map;
import solver.Z3Solver.SatResult;

// I used this trick that any solver should implement
// the respective interface. So that in the exploration.parameters.pareto or oneDimension
// will use one class to perform the exploration using only one "exploration.parameters.XX" class.
// This will avoid us using multiple exploration.parameters class for same type of exploration but 
// different type of solvers. The advantage is that we use one exploration.parameters class with any
// solver (which implements different algorithms to solve the same problem). 

/**
 * Interface that every solver must implement so that it could
 * be used by the design space exploration.
 * 
 * @author Pranav Tendulkar
 *
 */
public interface SolverFunctions 
{
	/**
	 * Get model from the Solver
	 * @return model from the Solver if the result is SAT.
	 */
	Map<String, String> getModel ();
	
	/**
	 * Perform a SMT query if the problem is
	 * satisfiable or not.
	 * 
	 * @param timeOutInSeconds time out in seconds for the query
	 * @return Query result SAT or UNSAT or others
	 */
	SatResult checkSat (int timeOutInSeconds);
	
	/**
	 * Save the context. So that we now add new cost constraints
	 * and ask solver for its evaluation.  
	 */
	void pushContext ();
	
	/**
	 * Get back the context for next query.
	 * 
	 * @param numContext
	 */
	void popContext (int numContext);
}
