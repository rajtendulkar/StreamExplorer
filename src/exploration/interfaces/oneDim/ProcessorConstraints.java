package exploration.interfaces.oneDim;

import java.util.Map;

/**
 * An upper bound on number of processors to be used in a schedule.
 * @author Pranav Tendulkar
 *
 */
public interface ProcessorConstraints 
{	
	/**
	 * Get the number of processors used in the schedule represented in the model.
	 * 
	 * @param model model returned by the Solver on a SAT result.
	 * @return number of processors used in the schedule.
	 */
	int getProcessors (Map<String, String> model);
	
	/**
	 * Set an upper bound on number of processors to be used in the schedule.
	 * 
	 * @param processors number of processors to be used as a constraint.
	 */
	void generateProcessorConstraint (int processors);
}
