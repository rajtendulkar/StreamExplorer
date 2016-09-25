package exploration.interfaces.oneDim;

import java.util.Map;

import exploration.interfaces.SolverFunctions;

/**
 * Total Communication cost used for exploration.
 * 
 * @author Pranav Tendulkar
 *
 */
public interface CommunicationCostConstraints extends SolverFunctions
{	
	/**
	 * Sets the Communication cost constraint for the exploration query
	 * 
	 * @param communicationCost
	 */
	void generateCommunicationCostConstraint (int communicationCost);
	
	/**
	 * Get the communication cost calculated in the model.
	 * 
	 * @param model model returned by the Solver on a SAT result.
	 * @return the total communication cost calculated in the model
	 */
	int getCommunicationCost (Map<String, String> model);
}
