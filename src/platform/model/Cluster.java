package platform.model;

import java.util.*;

/**
 * Represents a cluster in the platform.
 * The cluster is composed of memories, processors, links, DMAs.
 * 
 * @author Pranav Tendulkar
 *
 */
public class Cluster extends PlatformComponentProp 
{	
	/**
	 * Number of memories inside the cluster. 
	 */
	private final int numMemoriesInCluster;
	
	/**
	 * Number of processors inside the cluster. 
	 */
	private final int numProcessorsInCluster;
	
	/**
	 * Number of links connected to the cluster. 
	 */
	private final int numLinksToCluster;
	
	/**
	 * Number of DMA engines of the cluster. 
	 */
	private final int numDmaEngines;
	
	/**
	 *  Speed of the cluster.
	 */
	private final int speed;

	/**
	 * Processors of the cluster.
	 */
	private Processor processors[];
	
	/**
	 * Links connected to the cluster.
	 */
	private NetworkLink links[];
	
	/**
	 * Memories of the cluster.
	 */
	private Memory memory[];
	
	/**
	 * DMA Engines of the cluster. 
	 */
	private DmaEngine dmaEngines[];
	
	/**
	 * Initialize the cluster component of the platform model.
	 * 
	 * @param name name of the cluster
	 * @param id id of the cluster
	 * @param speed speed of the cluster
	 * @param numProcessorsInThisCluster number of processors present in the cluster
	 * @param numberOfLinksToThisCluster number of links connected to the cluster
	 * @param numClusterMemory number of memories present in the cluster
	 * @param numDmaEngines number of DMA present in the cluster
	 */
	public Cluster (String name, int id, int speed, int numProcessorsInThisCluster, 
			int numberOfLinksToThisCluster, int numClusterMemory, int numDmaEngines)
	{
		super(name,id);
		
		if (numProcessorsInThisCluster < 0)
			throw new RuntimeException ("Number of Processors Should be a positive value");
		numProcessorsInCluster = numProcessorsInThisCluster;
		numMemoriesInCluster = numClusterMemory;
		numLinksToCluster = numberOfLinksToThisCluster;
		this.numDmaEngines = numDmaEngines;
		this.speed = speed;
		
		processors = new Processor[numProcessorsInThisCluster];		
		links = new NetworkLink [numLinksToCluster];
		memory = new Memory[numClusterMemory];
		dmaEngines = new DmaEngine[numDmaEngines];
	}	
	
	// Sort the processors according to the name.
	
	/**
	 * Sort the processors inside the cluster according to their ID. 
	 */
	public void sortProcessors ()
	{		
        Arrays.sort (processors, new Comparator<Processor>() {
            @Override
            public int compare (final Processor proc1, final Processor proc2) 
            {                	   
               return (proc1.getId()-proc2.getId());            	                 
            }
        });				
	}
	
	/**
	 * Sort the DMA inside the cluster according to their ID. 
	 */
	public void sortDma ()
	{		
        Arrays.sort (dmaEngines, new Comparator<DmaEngine>() {
            @Override
            public int compare (final DmaEngine dma1, final DmaEngine dma2) 
            {                	   
               return (dma1.getId()-dma2.getId());            	                 
            }
        });				
	}
	
	/**
	 * Gets number of processors in the cluster
	 * @return number of processors in the cluster
	 */
	public int getNumProcInCluster () { return numProcessorsInCluster; }
	
	/**
	 * Gets number of DMA in the cluster
	 * @return number of DMA in the cluster
	 */
	public int getNumDmaInCluster () { return numDmaEngines; }
	
	/**
	 * Gets number of links connected to the cluster
	 * @return number of links connected to the cluster
	 */
	public int getNumLinksToCluster () { return numLinksToCluster; }
	
	/**
	 * Gets number of Memories in the cluster
	 * @return number of memories in the cluster
	 */
	public int getNumMemoriesInCluster () { return numMemoriesInCluster; }
	
	/**
	 * Gets processor inside cluster at index
	 * @param index processor at this particular index
	 * @return processor of the cluster at index
	 */
	public Processor getProcessor (int index) { return processors[index]; }
	
	/**
	 * Gets DMA inside cluster at index
	 * @param index DMA at this particular index
	 * @return DMA of the cluster at index
	 */
	public DmaEngine getDmaEngine (int index) { return dmaEngines[index]; }
	
	/**
	 * Gets Link of cluster at index
	 * @param index Link at this particular index
	 * @return Link of the cluster at index
	 */
	public NetworkLink getLink (int index) { return links[index]; }
	
	/**
	 * Gets Memory inside cluster at index
	 * @param index Memory at this particular index
	 * @return Memory of the cluster at index
	 */
	public Memory getMemory (int index) { return memory[index]; }
	
	/**
	 * Get Speed of the cluster
	 * @return speed of the cluster
	 */
	public int getSpeed() { return speed; }
	
	
	/**
	 * Adds DMA Engine to the cluster at available index.
	 * @param dma DMA Engine instance to be added
	 */
	public void addDma (DmaEngine dma) 
	{
		for (int i=0;i<numDmaEngines;i++)
		{
			if (dmaEngines[i] == null)
			{
				dmaEngines[i] = dma;
				return;
			}
		}
		
		throw new RuntimeException ("DmaEngines more than specified for Cluster " + getName());
	}
	
	/**
	 * Adds Processor to the cluster at available index.
	 * @param proc Processor instance to be added
	 */
	public void addProcessor (Processor proc) 
	{
		for (int i=0;i<numProcessorsInCluster;i++)
		{
			if (processors[i] == null)
			{
				processors[i] = proc;
				return;
			}
		}
		
		throw new RuntimeException ("Processors more than specified for Cluster " + getName());
	}
	
	/**
	 * Adds Memory to the cluster at available index.
	 * @param mem Memory instance to be added
	 */
	public void addMemory (Memory mem) 
	{
		for (int i=0;i<numMemoriesInCluster;i++)
		{
			if (memory[i] == null)
			{
				memory[i] = mem;
				return;
			}
		}
		
		throw new RuntimeException ("Memories more than specified for Cluster " + getName());
	}
	
	/**
	 * Adds network link to a given port.
	 * @param lnk Network Link instance to be added.
	 * @param port Port of the cluster where network link is added.
	 */
	public void addLink (NetworkLink lnk, int port) 
	{
		if (links[port] == null)
			links[port] = lnk;
		else		
			throw new RuntimeException ( "Failed adding link " + lnk.getName() + " to cluster " + getName() +
					" Port "+ port +" already occupied with link " + links[port].getName());
	}
}
