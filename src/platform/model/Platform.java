package platform.model;

import java.util.*;

/**
 * Represents a Hardware Platform model.
 * A platform consists of multiple components such as cluster, network links,
 * DMA, processors etc. 
 * 
 * @author Pranav Tendulkar
 *
 */
public class Platform 
{
	// I tried to make maximum variables as final, so that they will not be modified later on.
	
	/**
	 * Name of the platform
	 */
	private final String name;
	
	/**
	 * Total number of clusters in the platform 
	 */
	private final int numClusters;
	
	/**
	 * Total number of DMA Engines in the platform 
	 */
	private final int numDmaEngines;
	
	/**
	 * Total number of processors in the platform 
	 */
	private final int numProcessors;
	
	/**
	 * Total number of memories in the platform 
	 */
	private final int numMemories;
	
	/**
	 * Total number of links in the platform
	 */
	private final int numLinks;
	
	/**
	 * All the Clusters of the platform. 
	 */
	private Cluster clusters[]=null;
	
	/**
	 * All the processors of the platform. 
	 */
	private Processor processors[]=null;
	
	/**
	 * All the network links of the platform. 
	 */
	private NetworkLink links[]=null;
	
	/**
	 * All the memories of the platform. 
	 */
	private Memory memories[]=null;
	
	/**
	 * All the DMA Engines of the platform. 
	 */
	private DmaEngine dmaEngines[]=null;
	
	/**
	 * All the distances between processors or clusters of the platform.
	 * If there are no clusters in the platform, this array will store distances
	 * between the processors of the platform.
	 */
	private int minDistances[][];
	
	/**
	 * Initialization Time required for the DMA. 
	 */
	private int dmaSetupTime;
	
		
	/**
	 * Initialize the platform model.
	 * 
	 * @param name name of the platform
	 * @param numClusters total number of clusters in the platform
	 * @param numDmaEngines total number of DMAs in the platform
	 * @param numProcessors total number of processors in the platform
	 * @param numMemories total number of memories in the platform
	 * @param numLinks total number of network links in the platform
	 */
	public Platform(String name, int numClusters, int numDmaEngines,
			int numProcessors, int numMemories, int numLinks)
	{
		this.name = name;
		this.numClusters = numClusters;
		this.numDmaEngines = numDmaEngines;
		this.numProcessors = numProcessors;
		this.numMemories = numMemories;
		this.numLinks = numLinks;
		
		if (numClusters > 0)
			clusters = new Cluster[numClusters];
		if (numDmaEngines > 0)
			dmaEngines = new DmaEngine[numDmaEngines];
		if (numMemories > 0)
			memories = new Memory[numMemories];
		if (numLinks > 0)
			links = new NetworkLink[numLinks];
		if (numProcessors > 0)
			processors = new Processor[numProcessors];
	}

	/**
	 * Gets all the clusters of the platform
	 * 
	 * @return a set of all clusters in the platform
	 */
	public HashSet<Cluster> getAllClusters ()
	{
		HashSet<Cluster>result =  new HashSet<Cluster>();
		for(Cluster cluster : clusters)
			result.add(cluster);
		return result;
	}
	
	/**
	 * We sort the components of the platform according to their ID's
	 * This will help to address them using the index variable especially
	 * in the API like {@link platform.model.Processor#getMemory(int)}
	 */
	public void sortElements ()
	{
		// Since we input the elements in any order in the XML, 
		// we need to sort them out so that we can take advantage of 
		// using indexes.
		
		// We sort all the processor and DMA elements.
		if (clusters != null)
		{
			int currentIndexCount = 0;
			for(int i=0;i<clusters.length;i++)
			{
				Cluster cluster = clusters[i];
				cluster.sortProcessors();
				
				for( int j=0;j<cluster.getNumProcInCluster();j++)
				{
					Processor proc = cluster.getProcessor(j);
					if(proc != processors[currentIndexCount])
					{
						for(int k = 0;k<processors.length;k++)
						{
							if(processors[k] == proc)
							{
								// exchange k and current index count
								Processor temp = processors[currentIndexCount];
								processors[currentIndexCount] = processors[k];
								processors[k] = temp;
								break;
							}
						}
					}
					currentIndexCount++;
				}			
			}		
			
			currentIndexCount = 0;
			for(int i=0;i<clusters.length;i++)
			{
				Cluster cluster = clusters[i];		
				cluster.sortDma();
				
				for(int j=0;j<cluster.getNumDmaInCluster();j++)
				{
					DmaEngine dma = cluster.getDmaEngine(j);
					if(dma != dmaEngines[currentIndexCount])
					{
						for(int k = 0;k<dmaEngines.length;k++)
						{
							if(dmaEngines[k] == dma)
							{
								// exchange k and current index count
								DmaEngine temp = dmaEngines[currentIndexCount];
								dmaEngines[currentIndexCount] = dmaEngines[k];
								dmaEngines[k] = temp;
								break;
							}
						}
					}
					currentIndexCount++;
				}
			}	
		}
	}
	
	/**
	 * Add a DMA Engine to the platform.
	 * 
	 * @param dma
	 */
	public void addDmaEngine (DmaEngine dma) 
	{
		for (int i=0;i<numDmaEngines;i++)
		{
			if (dmaEngines[i] == null)
			{
				dmaEngines[i] = dma;
				return;
			}
		}
		
		throw new RuntimeException ("Total number of DMA Engines exceeded for : " + dma.getName());
	}
	
	/**
	 * Add a network link to the platform.
	 * 
	 * @param lnk network link component to be added to the platform.
	 */
	public void addLink (NetworkLink lnk) 
	{
		for (int i=0;i<numLinks;i++)
		{
			if (links[i] == null)
			{
				links[i] = lnk;
				return;
			}
		}
		
		throw new RuntimeException ("Total number of Links exceeded for : " + lnk.getName());
	}
	
	/**
	 * Add memory component to the platform.
	 * 
	 * @param mem memory component to be added to the platform.
	 */
	public void addMemory (Memory mem) 
	{
		for (int i=0;i<numMemories;i++)
		{
			if (memories[i] == null)
			{
				memories[i] = mem;
				return;
			}
		}
		
		throw new RuntimeException ("Total number of Memories exceeded for : " + mem.getName());
	}
	
	/**
	 * Set the DMA initialization time in the platform.
	 * @param dmaSetupTime dma initialization time of the platform.
	 */
	public void setDmaSetupTime(int dmaSetupTime) { this.dmaSetupTime = dmaSetupTime; }
	
	/**
	 * Add a processor component to the platform.
	 * 
	 * @param proc processor component to be added to the platform.
	 */
	public void addProcessor(Processor proc)
	{
		for (int i=0;i<numProcessors;i++)
		{
			if (processors[i] == null)
			{
				processors[i] = proc;
				return;
			}
		}
		
		throw new RuntimeException ("Total number of Processors exceeded for : " + proc.getName());
	}
	
	/**
	 * Add a cluster component to the platform.
	 * 
	 * @param cluster cluster component to be added to the platform.
	 */
	public void addCluster (Cluster cluster)
	{
		for (int i=0;i<numClusters;i++)
		{
			if (clusters[i] == null)
			{
				clusters[i] = cluster;
				return;
			}
		}
		
		throw new RuntimeException ("Total number of Clusters exceeded for : " + cluster.getName());
	}
	
	/**
	 * Gets a cluster at particular index.
	 * 
	 * @param index index location in the array of clusters.
	 * @return cluster component at the index
	 */
	public Cluster getCluster (int index) { return clusters[index]; }
	
	/**
	 * Gets a processor at particular index.
	 * 
	 * @param index index location in the array of processors.
	 * @return processor component at the index
	 */
	public Processor getProcessor (int index) { return processors[index]; }
	
	/**
	 * Gets a DMA Engine at particular index.
	 * 
	 * @param index index location in the array of DMA Engines.
	 * @return DMA component at the index
	 */
	public DmaEngine getDmaEngine (int index) { return dmaEngines[index]; }
	
	/**
	 * Gets a Network Link at particular index.
	 * 
	 * @param index index location in the array of network links.
	 * @return Network Link component at the index
	 */
	public NetworkLink getLink (int index) { return links[index]; }
	
	/**
	 * Gets a cluster by its name.
	 * 
	 * @param name name of the cluster.
	 * @return cluster specified by name
	 */
	public Cluster getCluster (String name) 
	{
		for (int i=0;i<numClusters;i++)
			if (clusters[i].getName ().equals (name))
				return clusters[i];
		
		return null; 
	}
	
	/**
	 * Get a processor by its name.
	 * 
	 * @param name name of the processor
	 * @return processor specified by name
	 */
	public Processor getProcessor (String name) 
	{
		if (processors != null)
		{
			for (int i=0;i<numProcessors;i++)
				if ((processors[i] != null) && (processors[i].getName ().equals (name)))
					return processors[i];
		}
		
		return null; 
	}
	
	/**
	 * Gets all the DMA Engines that belong to a cluster.
	 * 
	 * @param cluster cluster component
	 * @return Set of DMA Engines belongs to this cluster
	 */
	public HashSet<DmaEngine> getDmaEngine (Cluster cluster)
	{
		HashSet<DmaEngine> result = new HashSet<DmaEngine>();
		for(int i=0;i<cluster.getNumDmaInCluster();i++)
			result.add(cluster.getDmaEngine(i));
		
		return result;
	}
	
	/**
	 * Gets index of Cluster in the platform.
	 * 
	 * @param cluster Cluster Component
	 * @return index in the array of Clusters where this component is present.
	 */
	public int getClusterIndex (Cluster cluster)
	{
		for (int i=0;i<numClusters;i++)
			if (cluster.equals (clusters[i]))
				return i;
		return -1;
	}
	
	/**
	 * Gets index of Processor in the platform.
	 * 
	 * @param processor Processor Component
	 * @return index in the array of Processors where this component is present.
	 */
	public int getProcIndex (Processor processor)
	{
		for (int i=0;i<numProcessors;i++)
			if (processor.equals (processors[i]))
				return i;
		return -1;
	}
	
	/**
	 * Gets index of DMA Engine in the platform.
	 * 
	 * @param dmaEngine DMA Engine component.
	 * @return index in the array of DMA Engines where this component is present.
	 */
	public int getDmaEngineIndex(DmaEngine dmaEngine)
	{
		for (int i=0;i<numDmaEngines;i++)
			if (dmaEngine == dmaEngines[i])
				return i;
		return -1;
	}	
	
	/**
	 * Gets the minimum distances between the processors or clusters.
	 * 
	 * @return Distance is the key of the hashmap and the value contains
	 * a list of integer array with two value source processor/cluster and destination processor/cluster index.
	 * This index is the same index as the arrays of processors/clusters. 
	 */
	public HashMap<Integer, List<int[]>> getDistanceMap()
	{
		HashMap<Integer, List<int[]>> map = new HashMap<Integer, List<int[]>>();
		
		if (numClusters <= 0)
		{
			for (int i=0;i<processors.length;i++)
			{
				for (int j=i+1;j<processors.length;j++)
				{
					if(i==j) continue;
					if(map.containsKey(minDistances[i][j]) == false)
						map.put(minDistances[i][j], new ArrayList<int[]>());
					
					int srcDst[] = new int[2];
					srcDst[0] = i;
					srcDst[1] = j;
					
					map.get(minDistances[i][j]).add(srcDst);
				}
			}			
		}
		else
		{
			for (int i=0;i<clusters.length;i++)
			{
				for (int j=i+1;j<clusters.length;j++)
				{
					if(i==j) continue;
					
					if(map.containsKey(minDistances[i][j]) == false)
						map.put(minDistances[i][j], new ArrayList<int[]>());
					
					int srcDst[] = new int[2];
					srcDst[0] = i;
					srcDst[1] = j;
					
					map.get(minDistances[i][j]).add(srcDst);
				}
			}
		}		
		return map;
	}
	
	/**
	 * Minimum distance in the platform between two processors / clusters.
	 * 
	 * @return Minimum distance between any two processors / clusters
	 */
	public int getMinDistanceInPlatform ()
	{
		if (numClusters <= 0)
		{
			int minDistance=Integer.MAX_VALUE;
			for (int i=0;i<processors.length;i++)
				for (int j=0;j<processors.length;j++)
					if (minDistances[i][j] < minDistance)
						minDistance = minDistances[i][j];
			return minDistance;
		}
		else
		{
			int minDistance=Integer.MAX_VALUE;
			for (int i=0;i<clusters.length;i++)
				for (int j=0;j<clusters.length;j++)
					if (minDistances[i][j] < minDistance)
						minDistance = minDistances[i][j];
			return minDistance;
		}		
	}
	
	/**
	 * Maximum distance in the platform between two processors / clusters.
	 * 
	 * @return Maximum distance between any two processors / clusters
	 */
	public int getMaxDistanceInPlatform ()
	{
		if (numClusters <= 0)
		{
			int maxDistance=Integer.MIN_VALUE;
			for (int i=0;i<processors.length;i++)
				for (int j=0;j<processors.length;j++)
					if (minDistances[i][j] > maxDistance)
						maxDistance = minDistances[i][j];
			return maxDistance;
		}
		else
		{
			int maxDistance=Integer.MIN_VALUE;
			for (int i=0;i<clusters.length;i++)
				for (int j=0;j<clusters.length;j++)
					if (minDistances[i][j] > maxDistance)
						maxDistance = minDistances[i][j];
			return maxDistance;
		}
	}
	
	
	/**
	 * Gets minimum distance between any two given clusters.
	 * 
	 * @param srcCluster source cluster
	 * @param dstCluster destination cluster
	 * @return distance between source and destination cluster
	 */
	public int getMinDistance (Cluster srcCluster, Cluster dstCluster)
	{
		int srcIndex = getClusterIndex (srcCluster);
		int dstIndex = getClusterIndex (dstCluster);
		return minDistances[srcIndex][dstIndex];
	}
	
	/**
	 * Get the minimum distance in the array.
	 * 
	 * @param aIndex first index
	 * @param bIndex second index
	 * @return value minDistances[aIndex][bIndex]
	 */
	public int getMinDistance (int aIndex, int bIndex)
	{
		return minDistances[aIndex][bIndex];
	}
	
	/**
	 * Gets minimum distance between any two given processors.
	 * 
	 * Note : In this case, I assumed that there are zero clusters in the platform.
	 * 
	 * 
	 * @param srcProc source processor
	 * @param dstProc destination processor
	 * @return distance between source and destination processor
	 */
	public int getMinDistance (Processor srcProc, Processor dstProc)
	{
		if(numClusters != 0)
			throw new RuntimeException("Get the distances between the clusters, since minDistances[][] array " +
					"contains distances between two clusters and not processors");
		
		int aIndex = getProcIndex (dstProc);
		int bIndex = getProcIndex (dstProc);
		return minDistances[aIndex][bIndex];
	}
	
	/**
	 * Calculate the minimum distances between the clusters.
	 * If there are no clusters in the platform it calculates the minimum 
	 * distances between the processors. 
	 */
	public void calculateMinDistances ()
	{
		if (numClusters <= 0)
		{
			// Calculate the distances for the processors, since we don't have any clusters.
			minDistances = new int[processors.length][processors.length];
			for (int i=0;i<processors.length;i++)
				for (int j=0;j<processors.length;j++)
					minDistances[i][j] = 0;
			
			// formulate available lengths.
			for (int i=0;i<processors.length;i++)
			{
				Processor proc = processors[i];
				for (int j=0;j<proc.getNumLinks ();j++)
				{
					NetworkLink lnk = proc.getLink (j);
					int srcIndex = getProcIndex (lnk.getSourceProcessor ());
					int dstIndex = getProcIndex (lnk.getDestinationProcessor ());
					minDistances[srcIndex][dstIndex] = lnk.getLatency ();
					minDistances[dstIndex][srcIndex] = lnk.getLatency ();
				}
			}		
			
			// calculate non-available lengths.		
			for (int i=0;i<processors.length;i++)
			{
				for (int j=0;j<processors.length;j++)
				{
					if (i != j && (minDistances[i][j] != 0))
					{
						for (int k=0;k<processors.length;k++)
						{
							if (i != k && (minDistances[j][k] != 0))
							{
								int tempDistance  = minDistances[j][k] + minDistances[i][j];
								if ((minDistances[i][k] == 0) || (tempDistance < minDistances[i][k]))
									minDistances[i][k] = tempDistance;
							}
						}
					}				
				}
			}
		}
		else
		{
			// Calculate the distances for the clusters.
			minDistances = new int[clusters.length][clusters.length];
			for (int i=0;i<clusters.length;i++)
				for (int j=0;j<clusters.length;j++)
					minDistances[i][j] = 0;
			
			// formulate available lengths.
			for (int i=0;i<clusters.length;i++)
			{
				Cluster cluster = clusters[i];
				for (int j=0;j<cluster.getNumLinksToCluster ();j++)
				{
					NetworkLink lnk = cluster.getLink (j);
					int srcIndex = getClusterIndex (lnk.getSourceCluster ());
					int dstIndex = getClusterIndex (lnk.getDestinationCluster ());
					minDistances[srcIndex][dstIndex] = lnk.getLatency ();
					minDistances[dstIndex][srcIndex] = lnk.getLatency ();
				}
			}		
			
			// calculate non-available lengths.		
			for (int i=0;i<clusters.length;i++)
			{
				for (int j=0;j<clusters.length;j++)
				{
					if (i != j && (minDistances[i][j] != 0))
					{
						for (int k=0;k<clusters.length;k++)
						{
							if (i != k && (minDistances[j][k] != 0))
							{
								int tempDistance  = minDistances[j][k] + minDistances[i][j];
								if ((minDistances[i][k] == 0) || (tempDistance < minDistances[i][k]))
									minDistances[i][k] = tempDistance;
							}
						}
					}				
				}
			}
		}
	}
	
	/**
	 * Check if platform model built confirms to specifications in the XML file.
	 */
	public void validatePlatform () 
	{
		if (name == null)
			throw new RuntimeException ("Name of the Platform is absent");
		
		if (numClusters != 0 && clusters.length != numClusters)
			throw new RuntimeException ("Number of Clusters not equal to specified clusters");
		
		if (numProcessors != 0 && processors.length != numProcessors)
			throw new RuntimeException ("Number of Processors not equal to specified processors");
		
		if (numLinks != 0 && links.length != numLinks)
			throw new RuntimeException ("Number of Links not equal to specified links");
		
		for (int i=0;i<numClusters;i++)
			if (clusters[i] == null)
				throw new RuntimeException ("Cluster " + i + " is missing.");
		
		for (int i=0;i<numProcessors;i++)
			if (processors[i] == null)
				throw new RuntimeException ("Processor " + i + " is missing.");
		
		for (int i=0;i<numLinks;i++)
			if (links[i] == null)
				throw new RuntimeException ("NetworkLink " + i + " is missing.");
		
		for (int i=0;i<numMemories;i++)
			if (memories[i] == null)
				throw new RuntimeException ("Memory " + i + " is missing.");
		
		for (int i=0;i<numProcessors;i++)
		{
			for (int j=0;j<processors[i].getNumLinks ();j++)
				if (processors[i].getLink (j) == null)
					throw new RuntimeException ("Processor " + i + " NetworkLink " + j + " is missing.");
			
			for (int j=0;j<processors[i].getnumMemory ();j++)
				if (processors[i].getMemory (j) == null)
					throw new RuntimeException ("Processor " + i + " Memory " + j + " is missing.");
		}
		
		for (int i=0;i<numClusters;i++)
		{
			for (int j=0;j<clusters[i].getNumProcInCluster ();j++)			
				if (clusters[i].getProcessor (j) == null)
					throw new RuntimeException ("Cluster " + i + " Processor " + j + " is missing.");					
			
			for (int j=0;j<clusters[i].getNumLinksToCluster ();j++)	
				if (clusters[i].getLink (j) == null)
					throw new RuntimeException ("Cluster " + i + " NetworkLink " + j + " is missing.");
			
			for (int j=0;j<clusters[i].getNumMemoriesInCluster ();j++)	
				if (clusters[i].getMemory (j) == null)
					throw new RuntimeException ("Cluster " + i + " Memory " + j + " is missing.");			
		}				 
	}	
		
	/**
	 * Gets the number of clusters in the platform.
	 *  
	 * @return number of clusters in the platform.
	 */
	public int getNumClusters () { return numClusters; }
	
	/**
	 * Gets the number of processors in the platform.
	 * 
	 * @return number of processors in the platform.
	 */
	public int getNumProcessors () { return numProcessors; }
	
	/**
	 * Gets the number of Network Links in the platform.
	 * 
	 * @return number of network links in the platform.
	 */
	public int getNumLinks () { return numLinks; }
	
	/**
	 * Gets the number of memories in the platform.
	 * 
	 * @return number of memories in the platform.
	 */
	public int getNumMemories () { return numMemories; }
	
	/**
	 * Gets the number of DMA Engines in the platform.
	 * 
	 * @return number of DMA Engines in the platform.
	 */
	public int getNumDmaEngines () { return numDmaEngines; }
	
	/**
	 * Gets the dma initialization time in the platform.
	 * 
	 * @return the dma initialization time.
	 */
	public int getDmaSetupTime() { return dmaSetupTime; }
}