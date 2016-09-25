package exploration.interfaces.oneDim;

import java.util.Map;

import exploration.interfaces.SolverFunctions;

/**
 * Total number of clusters used for exploration
 * 
 * @author Pranav Tendulkar
 */
public interface ClusterConstraints extends SolverFunctions
{
	/**
	 * Get total number of clusters used from the model.
	 * 
	 * @param model model returned by the Solver on a SAT result.
	 * @return total number of clusters used from the result
	 */
	int getTotalClustersUsed (Map<String, String> model);
	
	/**
	 * Set total number of clusters used as a constraint.
	 * 
	 * @param numClusters number of clusters to be used
	 */
	void generateClusterConstraint (int numClusters);
}
