package graphanalysis.properties;

import java.util.*;

import spdfcore.*;

/**
 * Different methods to check certain properties of the Graph.
 * This class contains the methods which are common to all split-join, sdf and hsdf graphs.
 * 
 * @author Pranav Tendulkar
 *
 */
public class GraphAnalysis 
{	
	/**
	 * Check if two actors are connected with a channel.
	 * 
	 * @param actr1 an actor
	 * @param actr2 another actor
	 * @return true if they are connected via a channel, false otherwise
	 */
	public boolean areActorsImmediatelyConnected (Actor actr1, Actor actr2) 
	{
		HashSet<Channel> chnnlList = actr1.getAllChannels ();
		for (Channel chnnl : chnnlList)
			if (chnnl.getOpposite (actr1) == actr2)
				return true;
		
		return false;
	}
	
	/**
	 * Get channel connecting two actors in a given direction 
	 * 
	 * @param actr1 an actor
	 * @param actr2 another actor
	 * @param direction  input or output channels
	 * @return channel between two connected actors, null if doesn't exist
	 */
	public Channel getChannelConnectingActors (Actor actr1, Actor actr2, Port.DIR direction) 
	{
		for(Channel chnnl : actr1.getChannels (direction))
		{
			if (chnnl.getOpposite (actr1).equals (actr2))
				return chnnl;
		}	
		return null;
	}
	
	/**
	 * Get actors which are immediately connected.
	 * 
	 * @param actor an actor
	 * @param direction input or ouput direction
	 * @return Set of connected actors either input or output
	 */
	public HashSet<Actor> getImmediatelyConnectedActors (Actor actor, Port.DIR direction)
	{
		HashSet<Actor> connectedActors = new HashSet<Actor>();
		
		for(Channel chnnl : actor.getChannels (direction))
			connectedActors.add (chnnl.getOpposite (actor));
				
		return connectedActors;
	}
	
	/**
	 * Get actors which are immediately connected, both at input and output ports.
	 * 
	 * @param actor an actor
	 * @return Entire set of connected actors
	 */
	public HashSet<Actor> getImmediatelyConnectedActors (Actor actor)
	{
		HashSet<Actor> connectedActors = new HashSet<Actor>();
		connectedActors.addAll (getImmediatelyConnectedActors (actor, Port.DIR.OUT));
		connectedActors.addAll (getImmediatelyConnectedActors (actor, Port.DIR.IN));				
		return connectedActors;
	}	
	
	/**
	 * Get Instance Id of an HSDF actor
	 * 
	 * @param hsdfActr HSDF actor
	 * @return instance id
	 */
	public int getInstanceId (Actor hsdfActr) 
	{
		int instanceId = -1;
		String actrName = hsdfActr.getName ();
		if (actrName.contains ("_"))
			instanceId = Integer.parseInt (actrName.substring (actrName.indexOf ("_")+1, actrName.length ()));
		return instanceId;
	}
	
	/**
	 * Get list of all the channels connected at all input ports of an actor 
	 * @param actr an actor
	 * @return list of channels connected at all input ports of an actor
	 */
	public List<Channel> getIncomingChannels (Actor actr) 
	{
		List<Channel> incomingChannels = new ArrayList<Channel>();
		
		for(Channel chnnl : actr.getChannels (Port.DIR.IN))
			incomingChannels.add (chnnl);
		
		return incomingChannels;
	}
}
