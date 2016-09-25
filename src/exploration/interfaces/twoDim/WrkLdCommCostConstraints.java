package exploration.interfaces.twoDim;

import exploration.interfaces.oneDim.CommunicationCostConstraints;
import exploration.interfaces.oneDim.WorkloadImbalanceConstraints;

/**
 * Interface to be implemented for 2-dim exploration of
 * Workload imbalance and total communication cost
 * 
 * @author Pranav Tendulkar
 *
 */
public interface WrkLdCommCostConstraints extends WorkloadImbalanceConstraints, CommunicationCostConstraints
{
}
