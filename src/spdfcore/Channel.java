package spdfcore;

/**
 *  FIFO channel: an edge in the SPDF or SDF graph.
 *  @author Peter Poplavko 
 */
public class Channel 
{
	/**
	 * Number of initial tokens on the channel
	 */
	private int initialTokens= 0;
	/**
	 * Size of each token
	 */
	private int tokenSize = 0;
	/**
	 * Name of the channel. 
	 * Note: we couldn't use Id class here.
	 */
	private String channelName;
	
	/**
	 *  A link between a channel and an actor what is usually called a port.  
	 *  In our case, we reserve the term 'port' for class of link sharing the same
	 *  name, rate and function within the given actor type. 
	 *  Thus a link is instantiation of a port.
	 */
	public class Link 
	{
		private Actor actor;
		private Port  port;

		@Override
		public String toString () 
		{
			return actor.getName () + "." + port.getName ();
		}        

		public Actor getActor () { return actor; }
		public Port  getPort () { return port; }
		public Channel getChannel () { return Channel.this; }
		public Link  getOpposite () 
		{ 
			return Channel.this.links[port.getDir ().getOpposite ().value ()]; 
		}

		@Override
		public boolean equals (Object obj) 
		{
			Link other = (Link) obj;

			return (this.actor.equals (other.actor) &&
					this.port.equals (other.port));
		}        
	}

	//------- data ---------
	private Link[] links = new Link[2];
	Graph graph;
	boolean auto = false;
	//----------------------
	//

	/**
	 * Set Initial tokens
	 * 
	 * @param tokens number of initial tokens
	 */
	public void setInitialTokens (int tokens) { initialTokens = tokens; }
	
	/**
	 * Get Initial tokens
	 * 
	 * @return number of initial tokens
	 */
	public int getInitialTokens () { return initialTokens; }
	
	
	/**
	 * Set the size of tokens
	 * 
	 * @param size of tokens
	 */
	public void setTokenSize (int size) { tokenSize = size; }
	
	/**
	 * Get size of the tokens
	 * 
	 * @return size of the tokens
	 */
	public int getTokenSize () { return tokenSize; }
	
	/**
	 * Get the name of the channel
	 * 
	 * @return name of the channel
	 */
	public String getName () { return channelName; }
	
	/**
	 * Set the name of the channel
	 *  
	 * @param name name of the channel
	 */
	public void setName (String name) { channelName = name; }

	/**
	 * Called by the graph when the link is getting assigned to the graph
	 * 
	 * @param g graph where the channel belongs to
	 */
	void setGraph (Graph g) 
	{
		if (graph!=null)
			throw new RuntimeException ("setGraph () should be called only once!");
		graph=g;
	}


	public Graph getGraph () { return graph; }

	/**
	 * Get one of the two links of the channel.
	 * @param dir IN or OUT
	 * @return link as per the direction of the channel
	 */
	public Link getLink (Port.DIR dir) { return links[dir.value ()]; }

	/**
	 * Let the channel bind the two specified ports.
	 * @param src source port reference for the channel
	 * @param snk sink port reference for the channel
	 */
	public void bind (PortRef src, PortRef snk) 
	{
		bind (src, Port.DIR.OUT);
		bind (snk, Port.DIR.IN);
	}

	/**
	 *  Unlink the channel before removal
	 */
	public void unbind () 
	{
		unbind (links[Port.DIR.OUT.value ()]);
		unbind (links[Port.DIR.IN.value ()]);
		links[Port.DIR.OUT.value ()] = null; // allow garbage collector to pick it up later 
		links[Port.DIR.IN.value ()] = null;
	}

	/**
	 *  Check binding  
	 */
	public boolean isBound () 
	{
		return (links[Port.DIR.IN.value ()]!=null);    	
	}

	/**
	 *  Get the link at the opposite side of the channel
	 * @param actor actor on one end of the channel.
	 * @return actor on the other end of the channel.
	 */
	public Actor getOpposite (Actor actor) 
	{
		for (Port.DIR d : Port.DIR.values ()) 
		{
			if (getLink (d).getActor ()==actor)
				return getLink (d).getOpposite ().getActor ();
		}
		throw new RuntimeException ("" + actor + " is not bound to " + this);
	}

	/**
	 * set auto flag for purely analytical channels 
	 */
	public void setAutoFlag () 
	{
		if (getGraph ()!=null) 
			throw new RuntimeException (this+ " cannot set flag *auto* when already assigned to a graph!");
		auto = true;
	}

	/**
	 * get the status of auto flag, indicating purely analytical channels
	 */
	public boolean getAutoFlag () { return auto; }

	/**
	 * Bind a port of an actor.
	 * 
	 * @param ref Port reference for the port
	 * @param dir direction in which to bind
	 */
	private void bind (PortRef ref, Port.DIR dir) 
	{
		if (links[dir.value ()]!=null) 
		{
			throw new RuntimeException (dir + " already bound!");
		}

		Actor actor = graph.getActor (ref.getActorName ());
		String func = actor.getFunc ();
		Id portId = new Id ();
		portId.setFunc (func);
		portId.setName (ref.getPortName ());

		Port port = graph.getPort (portId);
		if (port.getDir ()!=dir) 
		{
			throw new RuntimeException (dir + " Actor " + ref.getActorName () + " port=" + ref.getPortName ()+ " wrong direction!");
		}

		Link link = new Link ();
		links[dir.value ()] = link;
		link.actor= actor;
		link.port = port;
		actor.bind (link);
	}

	/**
	 * Unbind a link on the port of actor.
	 * 
	 * @param link link to unbind
	 */
	private void unbind (Link link) 
	{
		link.actor.unbind (link);
		link.actor = null; // important for Java garbage collector
		link.port = null;
	}

	@Override
	public String toString () 
	{
		return links[Port.DIR.OUT.value ()] + " --> " + links[Port.DIR.IN.value ()];
	}
}