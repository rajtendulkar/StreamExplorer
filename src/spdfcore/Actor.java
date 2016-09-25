package spdfcore;

import java.util.*;

import spdfcore.Channel.Link;

/**
 * 
 *  Models an instance of an actor (a 'filter') - a node of SPDF graph.
 *  An actor has a name, a function (i.e. type) and the list of links to the channels.
 *  Some actors are purely analytical (flag auto) 
 *  
 *  It inherits the name, the "function" and an assignment to a graph from Id.
 *  
 * @author Petro Poplavko
 *
 */
public class Actor extends Id 
{
	/**
	 * If an actor is dataflow actor or inserted communication actor.
	 *
	 */
	public enum ActorType {DATAFLOW, COMMUNICATION }

	/**
	 * Type of an actor
	 */
	private ActorType actorType;

	/**
	 * Execution time of an actor.
	 */
	private int executionTime = 0;
	
	/**
	 * Links connected to input ports of an actor. 
	 */
	private LinkedList<Channel.Link> links_IN  = new LinkedList<Channel.Link>();
	
	/**
	 * Links connected to output ports of an actor. 
	 */
	private LinkedList<Channel.Link> links_OUT = new LinkedList<Channel.Link>();
	
	/**
	 * true for purely analytical actors 
	 */
	private boolean auto = false;

	/**
	 * Create a copy of other actor.
	 * 
	 * @param otherActor all fields are copied from other actor.
	 */
	public Actor (Actor otherActor)
	{
		this.executionTime = otherActor.executionTime;
		this.auto = otherActor.auto;
		this.actorType = otherActor.actorType;
		setName(new String(otherActor.getName()));
		setFunc(new String(otherActor.getFunc()));
	}
	
	/**
	 * Initialize actor to data flow actor by default.
	 */
	public Actor () { setActorType(ActorType.DATAFLOW); }

	/**
	 * Initialize an actor 
	 * 
	 * @param function Function which an actor will execute, this is not a strict field
	 * @param name Name of the actor
	 * @param execTime execution time of the actor.
	 * @param actorType type of actor : data flow or communication
	 */
	public Actor (String function, String name, int execTime, ActorType actorType)
	{		
		setName(name);		
		setFunc(function);
		setActorType(actorType);		
		this.executionTime = execTime;
	}

	/**
	 * Gets number of incoming links. 
	 * 
	 * @return number of incoming links for this actor.
	 */
	public int numIncomingLinks ()
	{
		return links_IN.size ();
	}

	/**
	 * Gets number of outgoing links.
	 * 
	 * @return number of outgoing links for this actor.
	 */
	public int numOutgoingLinks ()
	{
		return links_OUT.size ();
	}

	/**
	 * Gets execution time of the actor.
	 * 
	 * @return execution time of the actor
	 */
	public int getExecTime ()
	{
		return executionTime;
	}

	/**
	 * Sets execution time of the actor
	 * @param time execution time of the actor
	 */
	public void setExecTime (int time)
	{
		executionTime = time;
	}

	/**
	 * @param lnk
	 */
	public void unbind (Link lnk)
	{
		if (links_IN.contains (lnk))
			links_IN.remove (lnk);
		else if (links_OUT.contains (lnk))
			links_OUT.remove (lnk);
		else
			throw new RuntimeException (this+ "cannot unbind from "+ lnk +"!");    		   	
	}

	/**
	 * connect actor to one side (link) of a channel
	 * (called by the channel)
	 *  
	 * @param link
	 */
	void bind (Channel.Link link) {
		if (link.getActor ()!=this) {
			throw new RuntimeException ("Cannot bind " + link + " to " + this + ": wrong actor!");
		}
		Port.DIR dir = link.getPort ().getDir ();
		if (linkList (dir).contains (link)) 
			throw new RuntimeException ("Cannot bind " + link + " to " + this + " two times!");
		linkList (dir).add (link);
	}


	/**
	 * access the links bound to the given actor IN or OUT
	 * 
	 * @param dir direction
	 * @return list of incoming or outgoing links
	 */
	private LinkedList<Channel.Link> linkList (Port.DIR dir) {
		return (dir==Port.DIR.IN) ?  links_IN : links_OUT;
	}


	/**
	 * set auto flag for purely analytical actors 
	 */
	public void setAutoFlag () {
		if (getGraph ()!=null) 
			throw new RuntimeException (
					this+ " cannot set flag *auto* when already assigned to a graph!");
		auto = true;
	}

	/**
	 * get the status of auto flag, indicating purely analytical actors
	 * 
	 * @return auto flag true indicates purely analytical actor. 
	 */
	public boolean getAutoFlag () {
		return auto;
	}     

	/**
	 * Get channels at the ports of this actor in a given direction.
	 * 
	 * @param direction port direction
	 * @return return the list of channels in given direction
	 */
	public HashSet<Channel> getChannels (Port.DIR direction)
	{
		HashSet<Channel> allChannels = new HashSet<Channel>();

		for(Link lnk : getLinks (direction))
			allChannels.add(lnk.getChannel());		
		
		return allChannels;
	}

	/**
	 * Gets a set of all incoming and outgoing channels connected to this actor.
	 * 
	 * @return list of all incoming and outgoing channels of an actor.
	 */
	public HashSet<Channel> getAllChannels ()
	{
		HashSet<Channel> allChannels = new HashSet<Channel>();

		HashSet<Channel.Link>lnkList =  getAllLinks ();
		for(Link lnk : lnkList) 	
			allChannels.add (lnk.getChannel ());

		return allChannels;
	}
 
	/**
	 * Gets a set of all incoming and outgoing links connected to this actor.
	 * 
	 * @return list of all incoming and outgoing links of an actor.
	 */
	public HashSet<Channel.Link> getAllLinks () 
	{
		HashSet<Channel.Link> allLinks = new HashSet<Channel.Link>();
		allLinks.addAll (linkList (Port.DIR.IN));
		allLinks.addAll (linkList (Port.DIR.OUT));
		return allLinks;
	}

	/**
	 * Get links at the ports of this actor in a given direction.
	 * 
	 * @param dir port direction
	 * @return return the list of links in given direction
	 */
	public HashSet<Channel.Link> getLinks (Port.DIR dir) 
	{
		return new HashSet<Channel.Link>(linkList (dir));
	}

	/**
	 * debug: print some details about the actor
	 */
	public void dump () {
		System.out.println ( "Ports of " + this + ": " );
		for (Port.DIR dir : Port.DIR.values ()) {
			Iterator<Channel.Link> links = linkList (dir).listIterator ();
			while (links.hasNext ()) {
				Channel.Link link= links.next ();
				System.out.println ( "\t"+ link + " is connected to " + link.getOpposite ());
			}
		}
	}

	/* (non-Javadoc)
	 * @see spdfcore.Id#toString()
	 */
	@Override
	public String toString () {
		return getName () + "@" + getFunc () + "@Actor";
	}

	/**
	 * Get Actor type.
	 * 
	 * @return actor type.
	 */
	public ActorType getActorType()
	{
		return actorType;
	}

	/**
	 * Set Actor type.
	 * 
	 * @param actorType Actor type : dataflow or communication
	 */
	public void setActorType(ActorType actorType)
	{
		this.actorType = actorType;
	}
}

