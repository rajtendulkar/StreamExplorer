package platform.model;

/**
 * Represents a Network Link in the platform.
 * @author Pranav Tendulkar
 *
 */
public class NetworkLink extends PlatformComponentProp
{
	/**
	 * Source port number of the network link. 
	 */
	private final int sourcePort;
	
	/**
	 * Destination port number of the network link.
	 */
	private final int destinationPort;
	
	/**
	 * Latency of the network link.
	 */
	private final int latency;
	
	/**
	 * Source cluster of the network link.
	 * (can be null, but then sourceProcessor should not be null).
	 */
	private final Cluster sourceCluster;

	/**
	 * Destination cluster of the network link.
	 * (can be null, but then destinationProcessor should not be null).
	 */
	private final Cluster destinationCluster;
	
	/**
	 * Source processor of the network link.
	 * (can be null, but then sourceCluster should not be null).
	 */
	private final Processor sourceProcessor;
	
	/**
	 * Destination processor of the network link.
	 * (can be null, but then destinationCluster should not be null).
	 */
	private final Processor destinationProcessor;

	
	/**
	 * Initializes the Network Link component of the platform.
	 * 
	 * @param name name of the processor
	 * @param id id of the processor
	 * @param sourcePort source port where the link starts
	 * @param destinationPort destination port where the link ends
	 * @param latency latency of the network link
	 * @param srcProcessor source processor
	 * @param dstProcessor destination processor
	 * @param srcCluster source cluster
	 * @param dstCluster destination cluster
	 */
	public NetworkLink (String name, int id, int sourcePort, int destinationPort, int latency, 
			Processor srcProcessor, Processor dstProcessor, Cluster srcCluster, Cluster dstCluster)
	{
		super(name, id);
		sourceProcessor = srcProcessor;
		destinationProcessor = dstProcessor;
		this.sourcePort = sourcePort;
		this.destinationPort = destinationPort;
		this.latency = latency;
		sourceCluster = srcCluster;
		destinationCluster = dstCluster;
	}
	
	// Getting the values
	/**
	 * Gets the source cluster of the network link.
	 * @return source cluster of the network link.
	 */
	public Cluster getSourceCluster () { return sourceCluster; }
	
	/**
	 * Gets the destination cluster of the network link.
	 * @return destination cluster of the network link.
	 */
	public Cluster getDestinationCluster () { return destinationCluster; }
	
	/**
	 * Gets the source processor of the network link.
	 * @return source processor of the network link.
	 */
	public Processor getSourceProcessor () { return sourceProcessor; }
	
	/**
	 * Gets the destination processor of the network link.
	 * @return destination processor of the network link.
	 */
	public Processor getDestinationProcessor () { return destinationProcessor; }
	
	/**
	 * Gets the source port of the network link
	 * @return the source port of the network link
	 */
	public int getSourcePort () { return sourcePort; }
	
	/**
	 * Gets the destination port of the network link
	 * @return the destination port of the network link
	 */
	public int getDestinationPort () { return destinationPort; }
	
	/**
	 * Gets the latency of the network link.
	 * @return the latency of the network link.
	 */
	public int getLatency () { return latency; }
}
