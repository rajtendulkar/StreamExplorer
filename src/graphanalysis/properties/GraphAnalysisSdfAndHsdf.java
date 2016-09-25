package graphanalysis.properties;

import graphanalysis.DepthFirstSearch;
import graphanalysis.TransformSDFtoHSDF;

import java.util.*;
import spdfcore.*;
import spdfcore.Channel.Link;
import spdfcore.stanalys.Solutions;

/**
 * Different methods to check certain properties of an SDF and its equivalent HSDF graph.
 * 
 * @author Pranav Tendulkar
 */
public class GraphAnalysisSdfAndHsdf extends GraphAnalysis 
{
	// We have to gurantee that we don't modify these objects.
	// In case we want to, then we should clone them.
	// But I am doing this, because the HSDF Graph is very heavy, 
	// and I don't want to run out of memory by cloning it.
	/**
	 * SDF Graph
	 */
	protected Graph graph;
	
	/**
	 * Equivalent HSDF graph 
	 */
	protected Graph hsdf;
	
	/**
	 * Solutions of SDF graph 
	 */
	protected Solutions solutions;

	/**
	 * Initialize graph analysis object 
	 * 
	 * @param graph SDF graph
	 * @param solutions solutions of SDF graph
	 */
	public GraphAnalysisSdfAndHsdf (Graph graph, Solutions solutions)
	{
		this (graph, solutions, null);
		TransformSDFtoHSDF toHSDF = new TransformSDFtoHSDF ();
		hsdf = toHSDF.convertSDFtoHSDFWithUniqueChannels (graph);
	}
	
	/**
	 * Initialize graph analysis object
	 *  
	 * @param graph SDF graph
	 * @param solutions solutions of SDF graph
	 * @param hsdf equivalent HSDF graph
	 */
	public GraphAnalysisSdfAndHsdf (Graph graph, Solutions solutions, Graph hsdf)
	{
		this.graph = graph;
		this.solutions = solutions;
		this.hsdf = hsdf;		
	}
	
	/**
	 * Get an HSDF actor from SDF actor and instance id
	 * 
	 * @param actr SDF actor
	 * @param instanceId instance ID
	 * @return HSDF actor
	 */
	public Actor getSdfToHsdfActor (Actor actr, int instanceId)
	{
		return hsdf.getActor (actr.getName () + "_" + Integer.toString (instanceId));
	}
	
	/**
	 * Check if two actors are connected
	 * 
	 * @param srcActor an actor
	 * @param dstActor other actor
	 * @param direction direction to check input or output
	 * @return true if they are connected in the given direction, false otherwise
	 */
	public boolean isConnected (Actor srcActor, Actor dstActor, Port.DIR direction)
	{
		HashSet<Channel>connectedChannelsToSource = srcActor.getChannels(direction);
		for(Channel chnnl : connectedChannelsToSource)
			if(chnnl.getOpposite(srcActor) == dstActor)
				return true;
		return false;
	}
	
	/**
	 * Get HSDF actors corresponding to an SDF actor
	 * 
	 * @param actr SDF actor
	 * @return list of corresponding HSDF actors
	 */
	public List<Actor> getSdfToAllHsdfActors (Actor actr)
	{
		List<Actor> result = new ArrayList<Actor>();
		int repCount = solutions.getSolution (actr).returnNumber ();
		for (int i=0;i<repCount;i++)
			result.add (hsdf.getActor (actr.getName () + "_" + Integer.toString (i)));
		return result;
	}
	
	/**
	 * Get all HSDF channels corresponding to SDF channel.
	 * Returns a list sorted by its channel names.
	 * 
	 * @param chnnl HSDF channel
	 * @return list of (sorted) HSDF channels
	 */
	public List<Channel> getSdfToHsdfChannels (Channel chnnl)
	{
		LinkedList<Channel> result = new LinkedList<Channel>();
		
		Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
		Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
		int srcRepCount = solutions.getSolution (srcActor).returnNumber ();
		
		for (int i=0;i<srcRepCount;i++)
		{
			Actor hsdfSrc = hsdf.getActor (srcActor.getName () + "_" + Integer.toString (i));
			
			for(Channel hsdfChnnl : hsdfSrc.getChannels (Port.DIR.OUT))
			{			
				if (hsdfChnnl.getOpposite (hsdfSrc).getName ().startsWith (dstActor.getName ()+"_"))
					result.add (hsdfChnnl);
			}
		}
		
		Collections.sort (result, new Comparator<Channel>() {
		    @Override
			public int compare (Channel e1, Channel e2) {
		        return e1.getName ().compareTo (e2.getName ());
		    }
		});
		
		return result;
	}
	
	/**
	 * Get all HSDF channels corresponding to SDF channel.
	 * 
	 * @param srcSdfActor source actor
	 * @param dstSdfActor sink actor
	 * @return Set of corresponding HSDF channels
	 */
	public TreeSet<Channel> getSdfToHsdfChannels (Actor srcSdfActor, Actor dstSdfActor)
	{
		TreeSet<Channel> result = new TreeSet<Channel>(new Comparator<Channel>(){
			@Override
			public int compare (Channel one, Channel two)
			{ 
				return one.getLink (Port.DIR.OUT).getActor ().getName ().compareTo (two.getLink (Port.DIR.OUT).getActor ().getName ());
			}});
		Iterator<Channel>chnnlIter = hsdf.getChannels ();
		while (chnnlIter.hasNext ())
		{
			Channel chnnl = chnnlIter.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
			if ((srcActor.getName ().startsWith (srcSdfActor.getName () + "_")) && (dstActor.getName ().startsWith (dstSdfActor.getName () + "_")))
				result.add (chnnl);
		}
		return result;
	}
	
	/**
	 * Find SDF actors with no predecessors
	 * 
	 * @return list of SDF actors with no predecessors
	 */
	public List<Actor> findSdfStartActors ()
	{
		List<Actor> hsdfActors = findHsdfStartActors ();
        HashSet<Actor> sdfStartActors = new HashSet<Actor>();
        for (int i=0;i<hsdfActors.size ();i++)
        {
        	String hsdfActorName = hsdfActors.get (i).getName (); 
        	String sdfActorName = hsdfActorName.substring (0, hsdfActorName.indexOf ("_"));
            Actor sdfActor = graph.getActor (sdfActorName);
            sdfStartActors.add (sdfActor);
        }   
    
        List<Actor> sdfActors = new ArrayList<Actor>(sdfStartActors);    
        return sdfActors;
		
	}
	
	/**
	 * Find SDF actors with no successors
	 * 
	 * @return list of SDF actors with no successors
	 */
	public List<Actor> findSdfEndActors ()
	{
		List<Actor> hsdfActors = findHsdfEndActors ();
        HashSet<Actor> sdfEndActors = new HashSet<Actor>();
        for (int i=0;i<hsdfActors.size ();i++)
        {
        	String hsdfActorName = hsdfActors.get (i).getName (); 
        	String sdfActorName = hsdfActorName.substring (0, hsdfActorName.indexOf ("_"));
            Actor sdfActor = graph.getActor (sdfActorName);
            sdfEndActors.add (sdfActor);
        }   
    
        List<Actor> sdfActors = new ArrayList<Actor>(sdfEndActors);    
        return sdfActors;		
	}

	/**
	 * Find HSDF actors with no predecessors
	 * 
	 * @return list of HSDF actors with no predecessors
	 */
	public List<Actor> findHsdfStartActors ()
	{
		List<Actor> result = new ArrayList<Actor>();

		Iterator<Actor> actrList = hsdf.getActors ();
		while (actrList.hasNext ())
		{
			Actor actr = actrList.next ();
			boolean startActor = true;

			for(Link lnk : actr.getLinks (Port.DIR.IN))
			{			
				int initialTokens = lnk.getChannel ().getInitialTokens ();
				if (initialTokens < Integer.parseInt (lnk.getPort ().getRate ()))
				{
					startActor = false;
					break;
				}    
			}   

			if (startActor == true)
				result.add (actr);    
		}   

		return result;
	}

	/**
	 * Find HSDF actors with no successors
	 * 
	 * @return list of HSDF actors with no successors
	 */
	public List<Actor> findHsdfEndActors ()
	{
		List<Actor> result = new ArrayList<Actor>();		
		Iterator<Actor> actrIter = hsdf.getActors ();
		while (actrIter.hasNext ())
		{
			boolean endActor = true;
			Actor actr = actrIter.next ();
			for (Link lnk : actr.getLinks (Port.DIR.OUT))
			{			
				if (Integer.parseInt (lnk.getOpposite ().getPort ().getRate ()) <= lnk.getChannel ().getInitialTokens ())
					continue;
				else
				{
					endActor = false;
					break;
				}
			}

			if (endActor == true)
				result.add (actr);    
		}    
		return result;  
	}
	
	/**
	 * Get HSDF to corresponding SDF actor
	 * 
	 * @param hsdfActor an HSDF actor
	 * 
	 * @return Corresponding HSDF actor
	 */
	public Actor getHsdfToSdfActor (Actor hsdfActor)
	{
		return graph.getActor (hsdfActor.getName ().substring (0, hsdfActor.getName ().indexOf ("_")));
	}
	
	/**
	 * Get maximum distance of an actor from source (or start actor) of the graph
	 * 
	 * @param actr an actor
	 * @return maximum distance from source in number of hops
	 */
	public int getMaxDistanceFromSrc (Actor actr) 
	{
		int maxDistance = 0;
		List<Actor> sdfStartActrList = findSdfStartActors ();
		if (sdfStartActrList.size () != 1)
			throw new RuntimeException ("Expecting only one sdf start actor. but found " + sdfStartActrList.size ());
		
		if (actr == sdfStartActrList.get (0))
			return 0;
		else
		{		
			DepthFirstSearch dfsSearch = new DepthFirstSearch ();
			List<List<Actor>> paths = dfsSearch.getDfsPaths (sdfStartActrList.get (0), actr);
			for (List<Actor> path : paths)
				if ((path.size ()-1) > maxDistance)
					maxDistance = (path.size ()-1);
		}
		return maxDistance;		
	}
	
	/**
	 * Get multiple paths between two actors
	 * 
	 * @param srcActr source actor
	 * @param dstActr sink actor
	 * @return paths between source and sink actor
	 */
	public List<List<Actor>> getPath (Actor srcActr, Actor dstActr)
	{		
		if (srcActr == dstActr)
			throw new RuntimeException ("Source and Destination actors are the same.");
		
		DepthFirstSearch depthFirstSearch = new DepthFirstSearch ();
		return depthFirstSearch.getDfsPaths (srcActr, dstActr);
	}
	

	/**
	 * This function calculates the longest path in the graph
	 * from source to destination actor. And then it add the 
	 * sum of execution time of all the actors and returns the
	 * value of the path has maximum delay.
	 * 
	 * @return Longest path delay in the SDF graph
	 */
	public int getLongestDelay ()
	{
		int longestDelay = 0;
		
		List<Actor> startActors = findHsdfStartActors ();
		List<Actor> endActors = findHsdfEndActors ();
		
		for (Actor startActr : startActors)
		{
			for (Actor endActr : endActors)
			{
				List<List<Actor>> pathList = getPath (startActr, endActr);
				
				for (List<Actor> path : pathList)
				{
					int pathCost = 0;
					for (Actor pathActr : path)
						pathCost += pathActr.getExecTime ();
					if (pathCost > longestDelay)
						longestDelay = pathCost;
				}
			}
		}
		
		return longestDelay;
	}
	
//	/**
//	 * @param hsdfActor
//	 * @param sdfActor
//	 * @return
//	 */
//	public List<Actor> getImmediatelySdfConnectedActorInstances (Actor hsdfActor, Actor sdfActor)
//	{
//		List<Actor> connectedActors = new ArrayList<Actor>();
//		connectedActors.addAll (getImmediatelyConnectedActors (hsdfActor));
//		for (int i=0;i<connectedActors.size ();i++)
//		{
//			if (connectedActors.get (i).getName ().startsWith (sdfActor.getName ()+"_") == false)
//			{
//				connectedActors.remove (i);
//				i--;
//			}
//		}						
//		return connectedActors;
//	}
}
