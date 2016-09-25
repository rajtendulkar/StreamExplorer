package exploration.interfaces.threeDim;

import exploration.interfaces.oneDim.ClusterConstraints;
import exploration.interfaces.oneDim.CommunicationCostConstraints;
import exploration.interfaces.oneDim.MaxWorkLoadPerCluster;

/**
 * Interface to be implemented for 3-dim exploration of
 * max workload per cluster, communication cost and number of clusters used.
 * 
 * @author Pranav Tendulkar
 *
 */
public interface MaxWrkLdCommCostClusterConstraints extends MaxWorkLoadPerCluster, 
															CommunicationCostConstraints, 
															ClusterConstraints
{

}
