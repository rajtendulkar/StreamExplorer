package exploration.interfaces.oneDim;

import java.util.Map;

import exploration.interfaces.SolverFunctions;

/**
 * Buffer size exploration
 * 
 * @author Pranav Tendulkar
 *
 */
public interface BufferConstraints extends SolverFunctions
{
	/**
	 * Get Total Buffer Size from the model.
	 * 
	 * @param model model returned by the Solver on a SAT result.
	 * 
	 * @return total buffer size
	 */
	int getTotalBufferSize (Map<String, String> model);
	
	/**
	 * Set the buffer size for exploration query.
	 * 
	 * @param bufferSize total buffer size for the query.
	 */
	void generateBufferConstraint (int bufferSize);
}
