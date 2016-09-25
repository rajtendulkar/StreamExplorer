package platform.model;

/**
 * Represents a processor in the platform.
 * 
 * @author Pranav Tendulkar
 *
 */
public class Processor extends PlatformComponentProp
{
	/**
	 * Speed of the processor.
	 */
	private final int speed;
	
	/**
	 * Number of network links connected to the processor 
	 */
	private final int numLinks;
	
	/**
	 * Number of memories in direct access to this processor. 
	 */
	private final int numMemory;
	
	/**
	 * Cluster to which this processor belongs to. 
	 */
	private final Cluster cluster;
	
	/**
	 * Memories of the processor 
	 */
	private Memory memory[];
	
	/**
	 * Links connected to the processor. 
	 */
	private NetworkLink links[];
	
	/**
	 * Initialize the processor component of the platform model.
	 * 
	 * @param name name of the processor
	 * @param id id of the processor
	 * @param speed speed of the processor
	 * @param numLinks number of links connected to the processor
	 * @param numMemory number of memories accessed by the processor
	 * @param cluster cluster to which the processor belongs to
	 */
	public Processor (String name, int id, int speed, int numLinks, int numMemory, Cluster cluster)
	{
		super(name, id);
		this.speed = speed;
		this.numLinks = numLinks;
		this.numMemory = numMemory;
		this.links = new NetworkLink[numLinks];
		this.memory = new Memory[numMemory];
		this.cluster = cluster;
	}	
	
	/**
	 * Gets the speed of the processor
	 * @return the speed of the processor
	 */
	public int getSpeed () { return speed; }
	
	/**
	 * Gets number of links connected to the processor
	 * @return number of links connected to the processor
	 */
	public int getNumLinks () { return numLinks; }
	
	/**
	 * Gets number of memories accessed by the processor
	 * @return number of memories accessed by the processor
	 */
	public int getnumMemory () { return numMemory; }
	
	/**
	 * Gets the network link at a particular index.
	 * @param index index of the network link
	 * @return the network link at particular index.
	 */
	public NetworkLink getLink (int index) { return links[index]; }
	
	/**
	 * Gets the memory at a particular index.
	 * @param index index of the memory
	 * @return the memory at particular index.
	 */
	public Memory getMemory (int index) { return memory[index]; }
	
	/**
	 * Adds a memory component to the processor at next available index.
	 * @param mem memory component to add
	 */
	public void addMemory (Memory mem) 
	{
		for (int i=0;i<numMemory;i++)
		{
			if (memory[i] == null)
			{
				memory[i] = mem;
				return;
			}
		}
		
		throw new RuntimeException ("Memories more than specified for processor " + getName());
	}
	
	/**
	 * Adds network link at the port of the processor
	 * @param lnk network link to be added 
	 * @param port port at which network link is added (port < numLinks)
	 */
	public void addLink (NetworkLink lnk, int port) 
	{
		if (links[port] == null)
			links[port] = lnk;
		else
			throw new RuntimeException ("Port "+ port +" already occupied for processor " + getName());
	}

	/**
	 * Gets the cluster to which this processor belongs to.
	 * @return the cluster to which this processor belongs to.
	 */
	public Cluster getCluster() { return cluster; }
}
