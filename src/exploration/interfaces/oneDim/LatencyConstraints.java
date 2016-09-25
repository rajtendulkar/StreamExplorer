package exploration.interfaces.oneDim;

import java.util.Map;

import exploration.interfaces.SolverFunctions;

/**
 * Latency of the graph used for exploration.
 * 
 * @author Pranav Tendulkar
 *
 */
public interface LatencyConstraints extends SolverFunctions
{
	/**
	 * Get the latency of the application graph calculated by the solver and returned in the model.
	 * 
	 * @param model model returned by the Solver on a SAT result.
	 * @return latency of the application graph in the schedule.
	 */
	public int getLatency (Map<String, String> model);
	
	/**
	 * Set the latency constraint for exploration query.
	 * 
	 * @param latency upper bound on the latency value for the exploration. 
	 */
	void generateLatencyConstraint (int latency);
}
