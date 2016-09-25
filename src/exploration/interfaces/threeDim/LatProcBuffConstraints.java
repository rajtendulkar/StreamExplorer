package exploration.interfaces.threeDim;

import exploration.interfaces.oneDim.BufferConstraints;
import exploration.interfaces.oneDim.LatencyConstraints;
import exploration.interfaces.oneDim.ProcessorConstraints;

/**
 * Interface to be implemented for 3-dim exploration of 
 * Latency, Processors used and Total Communication Buffer Size.
 * 
 * @author Pranav Tendulkar
 *
 */
public interface LatProcBuffConstraints extends LatencyConstraints, ProcessorConstraints, BufferConstraints
{
}
