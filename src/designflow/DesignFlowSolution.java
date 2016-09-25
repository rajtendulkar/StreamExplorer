package designflow;

import graphanalysis.TransformSDFtoHSDF;
import graphanalysis.properties.GraphAnalysisSdfAndHsdf;
import java.util.*;
import java.util.Map.Entry;

import platform.model.*;
import solver.*;
import solver.distributedMemory.constraints.*;
import spdfcore.*;
import spdfcore.Actor.ActorType;
import spdfcore.stanalys.*;

/**
 * A solution which is gradually built during the Design flow.
 * 
 * @author Pranav Tendulkar
 *
 */
public class DesignFlowSolution 
{
	/**
	 * Application Graph SDF
	 */
	private Graph graph;
	
	/**
	 * Equivalent Application Graph HSDF
	 */
	private Graph hsdf;
	
	/**
	 * Solutions to Application Graph SDF
	 */
	private Solutions solutions;
	
	/**
	 *  Partition Aware Graph
	 *  Note : This graph is same sdf graph but with additional actors for communication.
	 */
	private Graph partitionAwareGraph; // 
	
	/**
	 * Partition Aware Graph HSDF
	 */
	private Graph partitionAwareHsdf; 
	
	/**
	 * Solutions to Partition Aware Graph
	 */
	private Solutions partitionAwareGraphSolutions;
	
	/**
	 * Target Platform model
	 */
	private Platform platform;
	
	/**
	 * Partitioning solution
	 */
	private Partition partition;
	
	/**
	 * Mapping solution
	 */
	private Mapping mapping;
	
	/**
	 * Scheduling solution
	 */
	private Schedule schedule;

	/**
	 * Initialize  a design flow solution
	 * @param graph application graph SDF
	 * @param hsdf equivalent HSDF graph
	 * @param solutions solutions to application graph
	 * @param platform target platform model
	 */
	public DesignFlowSolution (Graph graph, Graph hsdf, Solutions solutions, Platform platform)
	{
		this.graph = graph;
		this.hsdf = hsdf;
		this.partitionAwareGraph = null;
		this.partitionAwareGraphSolutions = null;
		this.solutions = solutions;
		this.platform = platform;
		mapping = null;
		partition = null;
		schedule = null;
	}
	
	/**
	 * Initialize a design flow solution copied from another solution.
	 * Copy partition aware graph and solutions to this solution
	 * 
	 * @param anotherSolution another design flow solution
	 */
	public DesignFlowSolution (DesignFlowSolution anotherSolution)
	{
		this(anotherSolution.graph, anotherSolution.hsdf, anotherSolution.solutions, anotherSolution.platform);
		
		this.partitionAwareGraph = anotherSolution.partitionAwareGraph;
		this.partitionAwareGraphSolutions = anotherSolution.partitionAwareGraphSolutions;
		
		if (anotherSolution.mapping != null)
			mapping = new Mapping(anotherSolution.mapping);
		
		if (anotherSolution.partition != null)
			partition = new Partition (anotherSolution.partition);
		
		if (anotherSolution.schedule != null)
			schedule = new Schedule (anotherSolution.schedule);		
	}
	
	/**
	 * Schedule class containing scheduling information.
	 * 
	 * @author Pranav Tendulkar
	 */
	public class Schedule
	{		
		
		/**
		 * Map containing buffer size value for each channel.
		 */
		private HashMap<String, Integer> bufferSizeMap;
		
		/**
		 * Map containing utilized processors inside a cluster
		 */
		private HashMap<Cluster, HashSet<Processor>> clusterUtilizationMap;
		// Note : These 3 maps are interlinked with each other.
		// the first map contains which actors, second map contains
		// instance Id of these actors and third map contains the start times of the same
		// actors. the list must have same corresponding location.
		/**
		 * List of actors mapped to a processor
		 */
		private HashMap<Processor, List<String>> processorActorMap;
		/**
		 * List of actor instances mapped to a processor
		 */
		private HashMap<Processor, List<Integer>> processorActorInstanceIdMap;
		/**
		 * List of start times of actor instances mapped to a processor
		 */
		private HashMap<Processor, List<Integer>> processorActorStartTimeMap;
		// It is the same for the actors allocated to the DMA Engines !
		/**
		 * List of actors mapped to DMA
		 */
		private HashMap<DmaEngine, List<String>> dmaActorMap;
		/**
		 * List of actor instancess mapped to DMA
		 */
		private HashMap<DmaEngine, List<Integer>> dmaActorInstanceIdMap;
		/**
		 * List of start times of actor instances mapped to DMA
		 */
		private HashMap<DmaEngine, List<Integer>> dmaActorStartTimeMap;
		
		/**
		 * Initialize a schedule class object
		 */
		public Schedule ()
		{
			clusterUtilizationMap = new HashMap<Cluster, HashSet<Processor>>();
			bufferSizeMap = new HashMap<String, Integer>();
			processorActorMap = new HashMap<Processor, List<String>>();
			processorActorInstanceIdMap = new HashMap<Processor, List<Integer>>();
			processorActorStartTimeMap = new HashMap<Processor, List<Integer>>();
			
			dmaActorMap = new HashMap<DmaEngine, List<String>>();
			dmaActorInstanceIdMap = new HashMap<DmaEngine, List<Integer>>();
			dmaActorStartTimeMap = new HashMap<DmaEngine, List<Integer>>();			
		}
		
		/**
		 * Build a schedule object from another object
		 * 
		 * @param anotherSchedule another schedule object
		 */
		public Schedule (Schedule anotherSchedule)
		{
			this();
			bufferSizeMap.putAll(anotherSchedule.bufferSizeMap);
			
			// Copy all the processor mappings
			for(Processor proc : processorActorMap.keySet())
			{
				processorActorMap.put(proc, new ArrayList<String>(anotherSchedule.processorActorMap.get(proc)));
				processorActorInstanceIdMap.put(proc, new ArrayList<Integer>(anotherSchedule.processorActorInstanceIdMap.get(proc)));
				processorActorStartTimeMap.put(proc, new ArrayList<Integer>(anotherSchedule.processorActorStartTimeMap.get(proc)));
			}	
		}
		
		/**
		 * Get a list of actors which do not belong to application graph and mapped on processors.
		 * They are added automatically due to partitioning, placement etc.
		 * 
		 * @return list of automatically added actors  
		 */
		public HashSet<String> getNonAppGraphActors ()
		{
			HashSet<String> result = new HashSet<String>();
			for(List<String> actrList : processorActorMap.values())
			{
				for(String actr : actrList)
					if(graph.hasActor(actr) == false)
						result.add(actr);
			}
			return result;
		}
		
		/**
		 * Add a buffer size of a channel to the schedule.
		 * 
		 * @param chnnl name of the channel
		 * @param bufferSize size of the buffer
		 */
		public void addBufferSize (String chnnl , int bufferSize)
		{
			bufferSizeMap.put(chnnl, bufferSize);
		}
		
		/**
		 * Add an actor instance to DMA engine.
		 * 
		 * @param actr name of the actor
		 * @param instanceId instance id
		 * @param dma DMA engine to add to
		 * @param startTime start time of the actor instance
		 */
		public void addActor (String actr, int instanceId, DmaEngine dma, int startTime)
		{
			if (dmaActorMap.containsKey(dma) == false)
			{
				dmaActorMap.put(dma, new ArrayList<String>());
				dmaActorInstanceIdMap.put(dma, new ArrayList<Integer>());
				dmaActorStartTimeMap.put(dma, new ArrayList<Integer>());
			}
			
			// Let us add in a sorted manner, to save the later efforts to sort the list.
			if(dmaActorMap.get(dma).size() == 0)
			{
				dmaActorMap.get(dma).add(actr);
				dmaActorInstanceIdMap.get(dma).add(instanceId);
				dmaActorStartTimeMap.get(dma).add(startTime);
			}
			else
			{
				// By default we add to the end.
				int indexToAdd = dmaActorStartTimeMap.get(dma).size();
				
				for(int i=0;i<dmaActorStartTimeMap.get(dma).size();i++)
				{
					if (dmaActorStartTimeMap.get(dma).get(i) == startTime)
						throw new RuntimeException("Adding duplicate start times on the same processor.");
					else if (dmaActorStartTimeMap.get(dma).get(i) > startTime)
					{
						// found some location, where we should add.
						indexToAdd = i;
						break;
					}
				}
				
				// Add the elements to the index.
				dmaActorMap.get(dma).add(indexToAdd, actr);
				dmaActorInstanceIdMap.get(dma).add(indexToAdd, instanceId);
				dmaActorStartTimeMap.get(dma).add(indexToAdd,startTime);
			}
		}
		
		/**
		 * Add an actor instance to Processor.
		 * 
		 * @param actr name of the actor
		 * @param instanceId instance id
		 * @param proc Processor to add to
		 * @param startTime start time of the actor instance
		 */
		public void addActor (String actr, int instanceId, Processor proc, int startTime)
		{
			Cluster cluster = proc.getCluster();
			if(clusterUtilizationMap.containsKey(cluster) == false)
				clusterUtilizationMap.put(cluster, new HashSet<Processor>());
			clusterUtilizationMap.get(cluster).add(proc);
			
			if (processorActorMap.containsKey(proc) == false)
			{
				processorActorMap.put(proc, new ArrayList<String>());
				processorActorInstanceIdMap.put(proc, new ArrayList<Integer>());
				processorActorStartTimeMap.put(proc, new ArrayList<Integer>());
			}
			
			// Let us add in a sorted manner, to save the later efforts to sort the list.
			if(processorActorMap.get(proc).size() == 0)
			{
				processorActorMap.get(proc).add(actr);
				processorActorInstanceIdMap.get(proc).add(instanceId);
				processorActorStartTimeMap.get(proc).add(startTime);
			}
			else
			{
				// By default we add to the end.
				int indexToAdd = processorActorStartTimeMap.get(proc).size();
				
				for(int i=0;i<processorActorStartTimeMap.get(proc).size();i++)
				{
					if (processorActorStartTimeMap.get(proc).get(i) == startTime)
						throw new RuntimeException("Adding duplicate start times on the same processor " + i + "." +
								" Already Present Actor " + processorActorMap.get(proc).get(i) + " instance : " 
								+ processorActorInstanceIdMap.get(proc).get(i) + ". " +
										"Trying to add actor " + actr + " instance : " + instanceId);
					else if (processorActorStartTimeMap.get(proc).get(i) > startTime)
					{
						// found some location, where we should add.
						indexToAdd = i;
						break;
					}
				}
				
				// Add the elements to the index.
				processorActorMap.get(proc).add(indexToAdd, actr);
				processorActorInstanceIdMap.get(proc).add(indexToAdd, instanceId);
				processorActorStartTimeMap.get(proc).add(indexToAdd,startTime);
			}				
		}
		
		/**
		 * Get cluster to which an actor is allocated to.
		 * 
		 * @param actr name of the actor
		 * @return cluster to which actor is allocated
		 */
		public Cluster getAllocatedCluster (String actr)
		{
			for(Processor proc : processorActorMap.keySet())
				for(String procActr : processorActorMap.get(proc))
					if(procActr.equals(actr))
						return proc.getCluster();
					
			return null;
		}
		
		/**
		 * Get processor to which an actor instance is allocated to.
		 * 
		 * @param actr name of the actor
		 * @param instanceId instance id
		 * @return processor to which an actor instance is allocated
		 */
		public Processor getAllocatedProcessor (String actr, int instanceId)
		{
			for (Processor proc : processorActorMap.keySet())
			{
				for(int i=0;i<processorActorMap.get(proc).size();i++)
				{
					String procActor = processorActorMap.get(proc).get(i);
					
					if((actr.equals(procActor)) && (processorActorInstanceIdMap.get(proc).get(i) == instanceId))
						return proc;
				}
			}
			
			return null;
		}
		
		/**
		 * Get buffer size calculated for a channel.
		 * 
		 * @param chnnl channel instance
		 * @return buffer size of the channel
		 */
		public int getBufferSize (Channel chnnl)
		{
			if(bufferSizeMap.containsKey(chnnl) == false)
				return -1;
			else
				return bufferSizeMap.get(chnnl);
		}
		
		/**
		 * Get a list of actors allocated to DMA engine sorted by their start times.
		 * 
		 * @param dma DMA engine
		 * @return list of actors allocated to DMA engine sorted by their start times
		 */
		public List<Entry<String,Integer>> getSortedActorInstances(DmaEngine dma)
		{
			List<Entry<String,Integer>> result = new ArrayList<Entry<String,Integer>>();
			
			if(dmaActorMap.containsKey(dma))
			{			
				for(int i=0;i<dmaActorMap.get(dma).size();i++)
					result.add(new AbstractMap.SimpleEntry<String,Integer> (dmaActorMap.get(dma).get(i), 
																	   dmaActorInstanceIdMap.get(dma).get(i)));
			}
			
			return result;
		}

		/**
		 * Get a list of actors allocated to processor sorted by their start times.
		 * 
		 * @param processor processor instance
		 * @return list of actors allocated to processor sorted by their start times
		 */
		public List<Entry<String,Integer>> getSortedActorInstances(Processor processor)
		{
			List<Entry<String,Integer>> result = new ArrayList<Entry<String,Integer>>();			
			
			if(processorActorMap.containsKey(processor))
			{
				for(int i=0;i<processorActorMap.get(processor).size();i++)
					result.add(new AbstractMap.SimpleEntry<String,Integer> (processorActorMap.get(processor).get(i), 
																	   processorActorInstanceIdMap.get(processor).get(i)));
			}
			return result;
		}

		/**
		 * Get number of processors used in a cluster.
		 * 
		 * @param cluster cluster instance
		 * @return number of processors used in this cluster
		 */
		public int numProcessorsUsed(Cluster cluster)
		{
			if(clusterUtilizationMap.containsKey(cluster))
				return clusterUtilizationMap.get(cluster).size();
			return 0;
		}

		/**
		 * Get total number of DMA engines used by producers or consumers of channel.
		 * 
		 * @param cluster name of the cluster
		 * @param channelName name of the channel
		 * @return total number of DMA engines used by producers or consumers of channel
		 */
		public int getMaxDmaEnginesUsed(Cluster cluster, String channelName)
		{
			int numDmaEnginesUsed = 0;
			
			for(int i=0;i<cluster.getNumDmaInCluster();i++)
			{
				DmaEngine dma = cluster.getDmaEngine(i);
				if(dmaActorMap.containsKey(dma))
				{
					List<String>actrList = dmaActorMap.get(dma);
					for(String actrName : actrList)
					{
						if((actrName.equals(SmtVariablePrefixes.dmaTokenTaskPrefix + channelName)) ||
								(actrName.equals(SmtVariablePrefixes.dmaStatusTaskPrefix + channelName)))
						{
							numDmaEnginesUsed++;
							break;
						}
					}
				}
			}
			
			return numDmaEnginesUsed;
		}		
	}
	
	/**
	 * Class containing Mapping information for design flow solution. 
	 * @author Pranav Tendulkar
	 *
	 */
	public class Mapping
	{
		/**
		 * Are the SDF actors allocated to groups in mapping?
		 * TODO: HSDF is not yet supported.
		 */
		private final boolean sdfAllocation;
		/**
		 * Map of group assigned to a cluster.
		 */
		private HashMap<Cluster, Integer> clusterToGroupMap;
		
		/**
		 * Build a mapping solution from another mapping solution.
		 * 
		 * @param anotherMappingSolution another mapping solution
		 */
		public Mapping (Mapping anotherMappingSolution)
		{
			sdfAllocation = anotherMappingSolution.sdfAllocation;
			clusterToGroupMap = new HashMap<Cluster, Integer>();
			clusterToGroupMap.putAll(anotherMappingSolution.clusterToGroupMap);			
		}
		
		/**
		 * Build a mapping solution.
		 * 
		 * @param sdfAllocation is it SDF allocation?
		 */
		public Mapping (boolean sdfAllocation)
		{
			this.sdfAllocation = sdfAllocation;
			clusterToGroupMap = new HashMap<Cluster, Integer>();
		}
		
		/**
		 * Get a list of clusters on which there are some actor allocated.
		 * 
		 * @return list of used clusters
		 */
		public List<Cluster> usedClusters ()
		{
			return new ArrayList<Cluster>(clusterToGroupMap.keySet());
		}
		
		
		/**
		 * Assign a groupe to a cluster.
		 * @param cluster cluster instance
		 * @param group group number
		 */
		public void addGroupToCluster (Cluster cluster, int group)
		{
			clusterToGroupMap.put(cluster, group);
		}
		
		/**
		 * Get the cluster allocated to a group.
		 * 
		 * @param group group index
		 * @return cluster allocated to this group
		 */
		public Cluster getClusterAllocatedToGroup (int group)
		{
			for(Cluster cluster : clusterToGroupMap.keySet())
				if (clusterToGroupMap.get(cluster) == group)
					return cluster;
				
			return null;
		}
		
		/**
		 * Resolve the execution times for all the DMA actors 
		 */
		public void resolveDmaTaskExecutionTime()
		{
			Iterator<Actor> actrIter = partitionAwareGraph.getActors();
			while(actrIter.hasNext())
			{
				Actor actor = actrIter.next();
				if(actor.getActorType() == ActorType.COMMUNICATION)
				{
					// If this is a communication actor, we need to set its execution time.
					int actorExecTime = 0;

					for(Channel chnnl : actor.getChannels(Port.DIR.IN))
					{
						int tokenSize = chnnl.getTokenSize();
						int rate = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());
						// TODO : Right now I am following this equation.
						// The exec time = 3000 + 0.2818 * tokenSize.
						// Note : I need additional 3000 clock cycles for the dma schedule list.
						// Thus in the platform.xml, we should have DMA Setup time as 6000 clock cycles.
						actorExecTime = (int) (platform.getDmaSetupTime() + (0.2818 * tokenSize * rate));						
						break;
					}
					
					actor.setExecTime(actorExecTime);
				}
			}
		}
		
		/**
		 * Get all the scheduling constraints based on mapping.
		 * This contain information to which cluster actors to be mapped.
		 * 
		 * @return scheduling constraints.
		 */
		public SchedulingConstraints getSchedulingConstraints ()
		{
			SchedulingConstraints schedConstraints = new SchedulingConstraints();
			GraphAnalysisSdfAndHsdf graphAnalysis = new GraphAnalysisSdfAndHsdf (graph, solutions, hsdf);
			
			// First add all the actors to respective clusters.
			for(Cluster cluster : clusterToGroupMap.keySet())
			{
				int group = clusterToGroupMap.get(cluster);
				HashSet<String> groupedActors = partition.actorsInGroup (group);
				for(String actr: groupedActors)
					schedConstraints.addActorClusterConstraint(actr, cluster);
			}
			
			// We add communicating tasks to the DMA engines.
			for(int i=0;i<partition.getNumGroups();i++)
			{
				for(int j=i+1;j<partition.getNumGroups();j++)
				{					
					HashMap<String, HashSet<String>> communicatingActorList = partition.communicatingActorsBetweenGroups (i, j);
					
					for (String actrNameInOneGroup : communicatingActorList.keySet())
					{
						HashSet<String> actorsInOtherGroup = communicatingActorList.get(actrNameInOneGroup);												
						for (String actrNameInOtherGroup : actorsInOtherGroup)
						{
							Actor actrInOneGroup = graph.getActor(actrNameInOneGroup);
							Actor actrInOtherGroup = graph.getActor(actrNameInOtherGroup);
							
							Actor srcActor=null, dstActor=null;
							if (graphAnalysis.isConnected (actrInOneGroup, actrInOtherGroup, Port.DIR.OUT))
							{
								srcActor = actrInOneGroup;
								dstActor = actrInOtherGroup;
							}
							else if (graphAnalysis.isConnected (actrInOneGroup, actrInOtherGroup, Port.DIR.IN))
							{
								srcActor = actrInOtherGroup;
								dstActor = actrInOneGroup;
							}
							else
								throw new RuntimeException("How did we reach here? The actors are not connected?");
							
							Channel chnnl = graphAnalysis.getChannelConnectingActors(srcActor, dstActor, Port.DIR.OUT);
							
							Actor dmaTokenTask = partitionAwareGraph.getActor(SmtVariablePrefixes.dmaTokenTaskPrefix + chnnl.getName());							
							Actor dmaStatusTask = partitionAwareGraph.getActor(SmtVariablePrefixes.dmaStatusTaskPrefix + chnnl.getName());
							Actor fifoUpdateTask = partitionAwareGraph.getActor(chnnl.getName());
							Cluster srcCluster = getClusterAllocatedToGroup(partition.groupAllocated(srcActor));
							Cluster dstCluster = getClusterAllocatedToGroup(partition.groupAllocated(dstActor));
							schedConstraints.addActorDmaEngineConstraint (dmaTokenTask.getName(), srcCluster);
							schedConstraints.addActorClusterConstraint (fifoUpdateTask.getName(), srcCluster);
							schedConstraints.addActorDmaEngineConstraint (dmaStatusTask.getName(), dstCluster);							
						}
					}					
				}
			}
			
			return schedConstraints;
		}
	}
	
	/**
	 * Class and methods for Partitioning information.
	 * 
	 * @author Pranav Tendulkar
	 */
	public class Partition 
	{
		/**
		 * Total number of groups.
		 */
		private final int numGroups;
		/**
		 * Total communication cost.
		 */
		private final int totalCommunicationCost;
		/**
		 * Is it a SDF partitioning?
		 * TODO: HSDF is not yet supported
		 */
		private final boolean sdfAllocation; 
		/**
		 * Map of list of actors allocated to a group.
		 */
		private HashMap<Integer, HashSet<String>> groupList;
		
		// .
		/**
		 * Build a partition object from another partition object.
		 * Copy Constructor
		 * 
		 * @param clonePartition another partition object
		 */
		public Partition (Partition clonePartition)
		{
			numGroups = clonePartition.numGroups;
			totalCommunicationCost = clonePartition.totalCommunicationCost;
			sdfAllocation = clonePartition.sdfAllocation; 
			groupList = new HashMap<Integer, HashSet<String>>();
			for(Integer key : clonePartition.groupList.keySet())
			{
				groupList.put(key, new HashSet<String>());
				groupList.get(key).addAll(clonePartition.groupList.get(key));
			}
		}

		/**
		 * Build a partition object.
		 * 
		 * @param numberOfPartitions total number of partitions
		 * @param commCost total communication cost
		 * @param sdfAllocation is it SDF allocation or not?
		 */
		public Partition (int numberOfPartitions, int commCost, boolean sdfAllocation)
		{		
			this.sdfAllocation = sdfAllocation;
			this.numGroups = numberOfPartitions;
			this.totalCommunicationCost = commCost;
			groupList = new HashMap<Integer, HashSet<String>>();
			
			for (int i=0;i<numGroups;i++)
				groupList.put(i, new HashSet<String>());		
		}
		
		/**
		 * Get the group to which an actor is allocated.
		 * 
		 * @param actor actor instance
		 * @return group to which it is allocated
		 */
		public int groupAllocated (Actor actor)
		{
			for(int partition : groupList.keySet())
			{
				for(String partActr : groupList.get(partition))
					if(actor.getName().equals(partActr))
						return partition;
			}
			
			return -1;
		}		
		
		/**
		 * Get a list of communicating actors between two groups.
		 * 
		 * @param group1 first group
		 * @param group2 second group
		 * @return list of communicating actors between group1 and group2
		 */
		public HashMap<String, HashSet<String>> communicatingActorsBetweenGroups (int group1, int group2)
		{
			HashMap<String, HashSet<String>> result = new HashMap<String, HashSet<String>>();
			
			if(group1 >= numGroups || group2 >= numGroups)
				throw new RuntimeException("Illegal partition input :" + group1 + " or " + 
						group2 + " is greater than num partitions " + numGroups);
			
			HashSet<String> group1Actors = groupList.get(group1);
			HashSet<String> group2Actors = groupList.get(group2);
			GraphAnalysisSdfAndHsdf graphAnalysis = new GraphAnalysisSdfAndHsdf (graph, solutions, hsdf);
			
			for (String actr : group1Actors)
			{
				HashSet<Actor> connectedActorList = graphAnalysis.getImmediatelyConnectedActors(graph.getActor(actr));				
				
				for(Actor connectedActor : connectedActorList)
				{
					for(String p2Actr : group2Actors)
						if(p2Actr.equals(connectedActor.getName()))					
						{	
							if(result.containsKey(actr) == false)
								result.put(actr, new HashSet<String>());
							result.get(actr).add(connectedActor.getName());
						}
				}
			}
			
			return result;
		}
		
		/**
		 * Get a list of actors allocated to a group.
		 * 
		 * @param group group index
		 * @return list of actors allocated to the group
		 */
		public HashSet<String> actorsInGroup (int group)
		{
			HashSet<String> result=null;
			HashSet<String> actrList = groupList.get(group);
			if(actrList != null)
			{
				result= new HashSet<String>();
				for(String actr : actrList)
					result.add(actr);
			}
			return result;
		}

		/**
		 * Get the total number of groups.
		 * 
		 * @return the total number of groups
		 */
		public int getNumGroups () { return numGroups; }
		
		/**
		 * Get the total communication cost.
		 * 
		 * @return the total communication cost
		 */
		public int getTotalCommunicationCost () { return totalCommunicationCost; }
		
		/**
		 * Is it SDF allocation?
		 * @return true if SDF allocation, false otherwise
		 */
		public boolean isSdfAllocation() { return sdfAllocation; }
		
		/**
		 * Get communication cost between two groups equal to amount of
		 * data transfer between them.
		 * 
		 * @param group1 first group
		 * @param group2 second group
		 * @return communication cost between first and second group
		 */
		public int commCostBetweenGroups (int group1, int group2)
		{
			int commCost = 0;
			
			HashSet<String> actorsPartition1 = groupList.get(group1);
			HashSet<String> actorsPartition2 = groupList.get(group2);
			
			GraphAnalysisSdfAndHsdf graphAnalysis = new GraphAnalysisSdfAndHsdf (graph, solutions, hsdf);
			
			for(String actrName : actorsPartition1)
			{
				Actor actr = graph.getActor(actrName);
				HashSet<Actor> connectedActors = graphAnalysis.getImmediatelyConnectedActors(actr);
				for(Actor connectedActr : connectedActors)
				{
					if (actorsPartition2.contains(connectedActr.getName()))
					{
						Channel chnnl = graphAnalysis.getChannelConnectingActors (actr, connectedActr, Port.DIR.IN);
						if(chnnl == null)
							chnnl = graphAnalysis.getChannelConnectingActors (actr, connectedActr, Port.DIR.OUT);
						
						Actor srcActr = chnnl.getLink(Port.DIR.OUT).getActor();
						int srcRate = Integer.parseInt(chnnl.getLink(Port.DIR.OUT).getPort().getRate());
						commCost += (chnnl.getTokenSize() * solutions.getSolution(srcActr).returnNumber() * srcRate);
						// add the token status size for the return communication.
						// commCost += DesignFlow.fifoStatusSizeInBytes;
					}
				}
			}
			
			return commCost;
		}		
		
		/**
		 * Add SDF actor to a group.
		 * 
		 * @param group group index
		 * @param sdfActor name of SDF actor
		 */
		public void addSdfActorToGroup (int group, String sdfActor)
		{
			groupList.get(group).add(sdfActor);
		}	
	}	
	
	/**
	 * Get partitioning solution.
	 * 
	 * @return partitioning solution
	 */
	public Partition getPartition() { return partition; }
	
	/**
	 * Set partitioning solution.
	 *  
	 * @param partition new partitioning solution
	 */
	public void setPartition(Partition partition) { this.partition = partition; }
	
	/**
	 * Get mapping solution.
	 * 
	 * @return mapping solution
	 */
	public Mapping getMapping() { return mapping; }
	
	/**
	 * Set mapping solutions in design flow.
	 * 
	 * @param mapping new mapping solution
	 */
	public void setMapping(Mapping mapping) { this.mapping = mapping; }
	
	/**
	 * Get partition aware graph in SDF
	 * @return partition aware graph 
	 */
	public Graph getpartitionAwareGraph() { return partitionAwareGraph; }	
	
	/**
	 * Set partition aware graph in SDF
	 * 
	 * @param partitionAwareGraph partition aware graph
	 */
	public void setpartitionAwareGraph(Graph partitionAwareGraph) 
	{ 
		this.partitionAwareGraph = partitionAwareGraph;
		
		TransformSDFtoHSDF toHSDF = new TransformSDFtoHSDF ();
		partitionAwareHsdf = toHSDF.convertSDFtoHSDFWithUniqueChannels (partitionAwareGraph);
	}
	
	/**
	 * Get schedule from the design flow.
	 * 
	 * @return scheduling information
	 */
	public Schedule getSchedule() { return schedule; }
	
	/**
	 * Set schedule in a design flow solution.
	 * 
	 * @param schedule new scheduling solution
	 */
	public void setSchedule(Schedule schedule) { this.schedule = schedule; }

	/**
	 * Get solutions to partition aware graph
	 * 
	 * @return solutions to the partition aware graph
	 */
	public Solutions getPartitionAwareGraphSolutions() { return partitionAwareGraphSolutions; }
	
	/**
	 * Set solutions to partition aware graph
	 * 
	 * @param partitionAwareGraphSolutions solutions to partition aware graph
	 */
	public void setPartitionAwareGraphSolutions(Solutions partitionAwareGraphSolutions)
	{
		this.partitionAwareGraphSolutions = partitionAwareGraphSolutions;
	}

	/**
	 * Get HSDF equivalent of partition aware graph
	 * @return HSDF equivalent of partition aware graph
	 */
	public Graph getPartitionAwareHsdf()
	{
		return partitionAwareHsdf;
	}
}