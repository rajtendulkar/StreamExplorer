package solver.distributedMemory.constraints;

import java.util.*;

import platform.model.*;

/**
 * Scheduling constraints for design flow. We allocate certain actors to
 * certain clusters or processors. We can apply them as constraints to the
 * scheduling solver.
 * 
 * TODO : These constraints have to be made generic such a way that they could
 * be applied to different solvers. This way we plan to make a feedback step from
 * the scheduling phase back to partitioning and placement step.
 * 
 * @author Pranav Tendulkar
 *
 */
public class SchedulingConstraints
{
	// Note : Earlier this was Actor. But I have actors mixing from partition aware graph and application graph.
	// Then I would have problems with getting solutions, because actors are different in both these graphs
	// because they are connected to different channels.
	
	/**
	 * Actor name as key with cluster as value where it should be allocated.
	 */
	private HashMap<String, Cluster> actorsRestrictedToCluster;
	/**
	 * Actor name as key with DMA of a cluster as value where it should be allocated.
	 */
	private HashMap<String, Cluster> actorsRestrictedToDmaOfCluster;
	
	/**
	 * Initialize the constraints lists.
	 */
	public SchedulingConstraints()
	{
		actorsRestrictedToCluster = new HashMap<String, Cluster>();
		actorsRestrictedToDmaOfCluster = new HashMap<String, Cluster>();
	}
	
	/**
	 * Get all the actors which are allocated to the cluster.
	 * 
	 * @param cluster cluster instance
	 * @return Hashset of names of actors allocated to the cluster
	 */
	public HashSet<String> getActorsAllocatedToCluster (Cluster cluster)
	{
		HashSet<String> result = new HashSet<String>();
		for(String actr : actorsRestrictedToCluster.keySet())
		{
			if(actorsRestrictedToCluster.get(actr) == cluster)
				result.add(actr);
		}		
		return result;
	}
	
	/**
	 *  Get all the actors which are allocated to the DMAs of the cluster.
	 *  
	 * @param cluster cluster instance
	 * @return Hashset of names of actors allocated to the DMA of the cluster
	 */
	public HashSet<String> getActorsMappedToDmaOfCluster (Cluster cluster)
	{
		HashSet<String> result = new HashSet<String>();
		for(String actr : actorsRestrictedToDmaOfCluster.keySet())
			if(actorsRestrictedToDmaOfCluster.get(actr) == cluster)
				result.add(actr);
		return result;
	}
	
	/**
	 * Get a map of actors mapped to DMA of the cluster.
	 * 
	 * @return map containing cluster and its mapped actors
	 */
	public HashMap<Cluster, HashSet<String>> getActorsMappedToDmaOfCluster ()
	{
		HashMap<Cluster, HashSet<String>> result = new HashMap<Cluster, HashSet<String>>();
		
		for(String actr : actorsRestrictedToDmaOfCluster.keySet())
		{
			Cluster cluster = actorsRestrictedToDmaOfCluster.get(actr);
			if(result.containsKey(cluster) == false)
				result.put(cluster, new HashSet<String>());
			result.get(cluster).add(actr);
		}
		
		return result;
	}
	
	/**
	 * Get a map of actors mapped to all the cluster.
	 * 
	 * @return map containing cluster and its mapped actors
	 */
	public HashMap<Cluster, HashSet<String>> getActorsMappedToCluster ()
	{
		HashMap<Cluster, HashSet<String>> result = new HashMap<Cluster, HashSet<String>>();
		
		for(String actr : actorsRestrictedToCluster.keySet())
		{
			Cluster cluster = actorsRestrictedToCluster.get(actr);
			if(result.containsKey(cluster) == false)
				result.put(cluster, new HashSet<String>());
			result.get(cluster).add(actr);
		}
		
		return result;
	}

	/**
	 * Get a cluster to which an actor is allocated.
	 * 
	 * @param actr name of the actor
	 * @return cluster to which it is allocated
	 */
	public Cluster getActorAllocatedCluster (String actr)
	{
		// Check first for data flow actors
		for (String mapActr : actorsRestrictedToCluster.keySet())
			if(actr.equals(mapActr))
				return actorsRestrictedToCluster.get(mapActr);
		
		// If not, then check for communication actors
		for (String mapActr : actorsRestrictedToDmaOfCluster.keySet())
			if(actr.equals(mapActr))
				return actorsRestrictedToDmaOfCluster.get(mapActr);
		
		// else return null.
		return null;
	}
	
	/**
	 * Add a constraint of actor mapped to a specific cluster.
	 * 
	 * @param actr name of the actor
	 * @param cluster cluster to which it is allocated
	 */
	public void addActorClusterConstraint (String actr, Cluster cluster)
	{
		actorsRestrictedToCluster.put(actr, cluster);
	}
	
	
	/**
	 * Add a constraint of actor mapped to a DMA of specific cluster.
	 * @param actr name of the actor
	 * @param cluster cluster to which it is allocated
	 */
	public void addActorDmaEngineConstraint (String actr, Cluster cluster)
	{
		actorsRestrictedToDmaOfCluster.put(actr, cluster);
	}
	
	/**
	 * Get all the actors allocated to the DMA of a cluster.
	 * 
	 * @param cluster cluster instance
	 * @return list of actors allocated to DMA of this cluster
	 */
	public List<String> getActorsAllocatedToDmaOfCluster (Cluster cluster)
	{
		List<String> result = new ArrayList<String>();
		
		for(String actr : actorsRestrictedToDmaOfCluster.keySet())
			if(actorsRestrictedToDmaOfCluster.get(actr) == cluster)
				result.add(actr);
		
		return result;
	}
}