package exploration.interfaces.oneDim;

import java.util.Map;

import exploration.interfaces.SolverFunctions;

/**
 * Max workload allocated to a cluster constraint for exploration.
 * 
 * @author Pranav Tendulkar
 *
 */
public interface MaxWorkLoadPerCluster extends SolverFunctions  
{
	/**
	 * Get maximum workload per cluster from the solver model.
	 * 
	 * @param model model returned by the Solver on a SAT result.
	 * @return maximum workload per cluster in the solver model
	 */
	int getMaxWorkLoadPerCluster (Map<String, String> model);
	
	/**
	 * Set the maximum workload per cluster constraint for exploration query.
	 * 
	 * @param constraintValue upper bound on max workload allocated to the cluster.
	 */
	void generateMaxWorkloadPerClusterConstraint (int constraintValue);
}
