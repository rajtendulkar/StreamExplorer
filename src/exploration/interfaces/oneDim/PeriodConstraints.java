package exploration.interfaces.oneDim;

import java.util.Map;

import exploration.interfaces.SolverFunctions;

/**
 * Period of the graph used for exploration.
 * 
 * @author Pranav Tendulkar
 *
 */
public interface PeriodConstraints extends SolverFunctions 
{
	/**
	 * Get the period value calculated by the solver on a SAT result.
	 * 
	 * @param model model returned by the Solver on a SAT result.
	 * @return Period value calculated in the model.
	 */
	int getPeriod (Map<String, String> model);
	
	/**
	 * Set the period constraint for exploration query.
	 * 
	 * @param constraintValue period constraint for an exploration query.
	 */
	void generatePeriodConstraint (int constraintValue);
}
