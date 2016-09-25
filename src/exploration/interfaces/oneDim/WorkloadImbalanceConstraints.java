package exploration.interfaces.oneDim;

import java.util.Map;

import exploration.interfaces.SolverFunctions;

/**
 * Maximum workload imbalance between the clusters.
 * 
 * The imbalance is calculated as (max workload on a cluster - min workload on a cluster).
 * 
 * @author Pranav Tendulkar
 *
 */
public interface WorkloadImbalanceConstraints extends SolverFunctions
{
	/**
	 * Set an upper bound on max workload imbalance between the clusters.
	 * 
	 * @param constraintValue Set upper bound on max workload imbalance.
	 */
	void generateWorkImbalanceConstraint (int constraintValue);
	
	/**
	 * @param model model returned by the Solver on a SAT result.
	 * @return max. workload imbalance between the clusters.
	 */
	int getWorkLoadImbalance (Map<String, String> model);
}
