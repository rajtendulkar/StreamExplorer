package platform.model;

/**
 * Represents a DMA Engine in the platform.
 * A DMA Engine is attached to the cluster.
 * We can add it to processor as well, but its not yet supported/required.
 * 
 * @author Pranav Tendulkar
 *
 */
public class DmaEngine extends PlatformComponentProp
{	
	/**
	 * Cluster to which the DMA engine belongs 
	 */
	private final Cluster cluster;
	
	/**
	 * Initialize the DMA Engine component of the platform model.
	 * 
	 * @param name name of the DMA Engine
	 * @param id id of the DMA Engine
	 * @param cluster cluster to which DMA belongs
	 */
	public DmaEngine (String name, int id, Cluster cluster)
	{
		super(name,id);
		this.cluster = cluster;
	}
	
	/**
	 * Gets Cluster to which the DMA Engine belongs to.
	 * @return cluster where the DMA engine is located.
	 */
	public Cluster getCluster() { return cluster; }
}
