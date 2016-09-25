package exploration.interfaces.threeDim;

import exploration.interfaces.oneDim.ClusterConstraints;
import exploration.interfaces.oneDim.CommunicationCostConstraints;
import exploration.interfaces.oneDim.WorkloadImbalanceConstraints;

/**
 * Interface to be implemented for 3-dim exploration of
 * Max workload imbalance between the clusters, communication cost and number of clusters used.
 * 
 * @author Pranav Tendulkar
 *
 */
public interface WorkloadCommClusterConstraints extends WorkloadImbalanceConstraints, CommunicationCostConstraints, ClusterConstraints 
{

}
