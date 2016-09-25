package platform.model;

/**
 * Represents a Memory component in the platform.
 * 
 * A Memory is attached to the cluster(s) or the processor(s).
 *  
 * @author Pranav Tendulkar
 *
 */
public class Memory extends PlatformComponentProp
{
	/**
	 * Number of processors which can access this memory.
	 */
	private final int numProcessors;
	
	/**
	 * Number of clusters which can access this memory.
	 */
	private final int numClusters;
	
	/**
	 * Size of the Memory in bytes.
	 */
	private final long sizeInBytes;
	
	/**
	 * Latency of memory access.
	 */
	private final int latency;
	
	/**
	 * Processors which can access this memory.
	 */
	private Processor proc[];
	
	/**
	 * Clusters which can access this memory.
	 */
	private Cluster cluster[];	
	
	/**
	 * Initializes the Memory component of the platform.
	 * 
	 * @param name name of the memory
	 * @param id id of the memory
	 * @param sizeInBytes size of memory in bytes.
	 * @param latency latency of access for this memory.
	 * @param numClusters number of clusters which can access this memory.
	 * @param numProcessors number of processors which can access this memory.
	 */
	public Memory (String name, int id, long sizeInBytes, int latency, int numClusters, int numProcessors)
	{
		super(name, id);
		this.sizeInBytes = sizeInBytes;
		this.latency = latency;
		this.numClusters = numClusters;
		this.numProcessors = numProcessors;
		
		if (numProcessors > 0)
			this.proc = new Processor[numProcessors];
		if (numClusters > 0)
			this.cluster = new Cluster[numClusters];
	}
	
	/**
	 * Gets the size of the memory in bytes.
	 * @return size of memory in bytes.
	 */
	public long getSizeInBytes () { return sizeInBytes; }
	
	/**
	 * Gets the latency of the memory access.
	 * @return latency of memory access.
	 */
	public int getLatency () { return latency; }
	
	/**
	 * Add the processor which can access this memory.
	 * @param processor processor which can access this memory
	 */
	public void addProcessor (Processor processor) 
	{
		for (int i=0;i<numProcessors;i++)
		{
			if (proc[i] == null)
			{
				proc[i] = processor;
				return;
			}
		}
		
		throw new RuntimeException ("Processors more than specified for memory " + getName());
	}
	
	/**
	 * Add the cluster which can access this memory.
	 * @param cluster cluster which can access this memory
	 */
	public void addCluster (Cluster cluster) 
	{
		for (int i=0;i<numClusters;i++)
		{
			if (this.cluster[i] == null)
			{
				this.cluster[i] = cluster;
				return;
			}
		}
		
		throw new RuntimeException ("Clusters more than specified for memory " + getName());
	}
}
