package spdfcore;

import java.util.*;

import spdfcore.Channel.Link;

/**
 * A Graph is a collection of actors which are connected by channels.
 * 
 * Right now we don't make any differentiation between SDF and HSDF graph.
 * They both fit in this class. If there is an actor "A" in SDF graph, there
 * will be corresponding actors "A_0", "A_1"... "A_{alpha-1}", where alpha is
 * the repetition count of actor A.
 * 
 * Note : We call the corresponding actors "A_0", "A_1"... "A_{alpha-1}" by
 * different names such as "tasks", "actor instances" or even "actors" of HSDF graph.
 * They all mean the same thing.
 * 
 * For actor instance we can provide name as "A" with instance id "0" and so on.
 * For referring as task or actor we can can get an actor directly with name "A_0".
 * It depends on convienience which one to use. 
 * 
 * @author Peter Poplavko
 *
 */
public class Graph 
{
	
	//-------------- data ---------------
	/**
	 * Actors of the graph mapped by names
	 */
    private HashMap<String, Actor> actors // actor lookup by name
        = new HashMap<String, Actor>(); 

    /**
     * Ports of the graph mapped by their ID
     */
    private HashMap<Id, Port> ports       // port lookup by func and name
        = new HashMap<Id, Port>();  

    /**
     * Channels of the graph
     */
    private HashSet<Channel> channels     // the set of all channels
        = new HashSet<Channel>();
    
    /**
     * Modifiers in the graph, required for SPDF graph
     */
    private HashMap<String, Modifier> modifiers // modifier lookup by parameter
    	= new HashMap<String,Modifier>();
    
    private HashMap<String, HashSet<Actor>> actorInstances // instances of a given actor function
    	= new HashMap<String, HashSet<Actor>>();
    String applicationName=null;
    //-----------------------------------------
    
    public String getGraphAppName () { return applicationName; }
    public void setGraphAppName (String name) { applicationName=name; }
    
    // Graph Constructors
    public Graph () {}
    
    /**
     * Build a graph as exactly a copy of another graph.
     * 
     * @param referenceGraph another graph
     */
    public Graph (Graph referenceGraph)
    {
    	this.applicationName = new String (referenceGraph.applicationName);
    	
    	// First we duplicate the ports.
    	for (Id refId : referenceGraph.ports.keySet())
    	{
    		Port refPort = referenceGraph.ports.get(refId);
    		Port newPort = new Port(refPort);
    		add (newPort);
    	}
    	
    	// Here just the actor is created. the links are not yet added to the actor.
    	for(String actrName : referenceGraph.actors.keySet())
    	{
    		Actor refActor = referenceGraph.actors.get(actrName);
    		Actor newActor = new Actor (refActor);
    		add(newActor);
    	}    	
    	
    	// Channel is created and link is not created.
    	for (Channel refChnnl : referenceGraph.channels)
    	{
    		Channel newChnnl = new Channel ();
    		
    		newChnnl.setName(new String(refChnnl.getName()));    		
    		
    		PortRef src = new PortRef ();
    		PortRef snk = new PortRef ();
    		
    		src.setActorName (new String (refChnnl.getLink(Port.DIR.OUT).getActor().getName()));
    		src.setPortName (new String (refChnnl.getLink(Port.DIR.OUT).getPort().getName()));
    		
    		snk.setActorName (new String (refChnnl.getLink(Port.DIR.IN).getActor().getName()));
    		snk.setPortName (new String (refChnnl.getLink(Port.DIR.IN).getPort().getName()));
    		
    		add (newChnnl);
    		
    		newChnnl.bind (src, snk);
      		newChnnl.setInitialTokens(refChnnl.getInitialTokens());
    		newChnnl.setTokenSize(refChnnl.getTokenSize());    		
    		
    		if(refChnnl.getAutoFlag() == true)
    			newChnnl.setAutoFlag();
    	}
    	
    	for (String refName : referenceGraph.modifiers.keySet())
    	{
    		Modifier refModifier = referenceGraph.modifiers.get(refName);
    		Modifier newModifier = new Modifier();
    		
    		newModifier.setFunc(new String(refModifier.getFunc()));
    		newModifier.setName(new String(refModifier.getName()));
    		newModifier.setParameter(new String(refModifier.getParameter()));
    		newModifier.setPeriod(new String(refModifier.getPeriod()));
    		newModifier.setParameterType(new String(refModifier.getParameterType()));
    		add (newModifier);
    	}   	
    }
    
    /**
     * Insert a new actor between a source and destination actor.
     * 
     * @param srcActor source actor
     * @param dstActor destination actor
     * @param portAtSrc port of the source actor
     * @param portAtDst port of the destination actor
     * @param newActor new actor to insert
     * @param newActorInputPortName name of input port for new actor
     * @param newActorOutputPortName name of output port for new actor
     * @return array of channels at the input and output of the new actor
     */
    public Channel[] insertNewActorBetweenTwoActors (Actor srcActor, Actor dstActor, Port portAtSrc, Port portAtDst,
												Actor newActor, String newActorInputPortName, String newActorOutputPortName)
    {
    	// Connect Src Actor -- > New Actor
		PortRef src = new PortRef ();
		PortRef snk = new PortRef ();
		
		src.setActorName (srcActor.getName());
		src.setPortName (portAtSrc.getName());
		
		snk.setActorName (newActor.getName());
		snk.setPortName (newActorInputPortName);
		
		Channel chnnl1 = new Channel();
		
		chnnl1.setName(srcActor.getName() + "to" + newActor.getName());		
		add (chnnl1);
		
		chnnl1.bind (src, snk);
		
		// Connect New Actor -- > Dst Actor	
		src = new PortRef ();
		snk = new PortRef ();
		
		src.setActorName (newActor.getName());
		src.setPortName (newActorOutputPortName);
		
		snk.setActorName (dstActor.getName());
		snk.setPortName (portAtDst.getName());
		
		Channel chnnl2 = new Channel();
		
		chnnl2.setName(newActor.getName() + "to" + dstActor.getName());		
		add (chnnl2);
		
		chnnl2.bind (src, snk);
		
		// Return the new channels
		Channel chnnls[] = new Channel[2];
		chnnls[0] = chnnl1;
		chnnls[1] = chnnl2;
		
		return chnnls;
    }
    
    /**
     * Insert a new channel between two actors.
     * 
     * @param srcActor source actor
     * @param dstActor destination actor
     * @param tokenSize size of the token
     * @param initialTokens number of initial tokens
     * @param srcRate production rate on the channel
     * @param dstRate consumption rate on the channel
     */
    public void insertNewChannelBetweenActors (Actor srcActor, Actor dstActor, 
    											int tokenSize, int initialTokens, 
    											int srcRate, int dstRate)
    {
    	PortRef src = new PortRef ();
		PortRef snk = new PortRef ();
		
		String srcPortName = "p_" + srcActor.getAllChannels().size();
		String dstPortName = "p_" + dstActor.getAllChannels().size();
		
		Id srcPortId = new Id();
		srcPortId.setFunc(srcActor.getFunc ());
		srcPortId.setName(srcPortName); 
		
        if(hasPort(srcPortId) == false)
        {
			Port srcPort  = new Port (Port.DIR.OUT);		
			srcPort.setFunc (srcActor.getFunc ());
			srcPort.setName (srcPortName);        		
			srcPort.setRate (Integer.toString(srcRate));
			add (srcPort);
        }
		
        Id dstPortId = new Id();
        dstPortId.setFunc(dstActor.getFunc ());
        dstPortId.setName(dstPortName);
        if(hasPort(dstPortId) == false)
        {
			Port dstPort  = new Port (Port.DIR.IN);		
			dstPort.setFunc (dstActor.getFunc ());
			dstPort.setName (dstPortName);        		
			dstPort.setRate (Integer.toString(dstRate));
			add (dstPort);
        }
		
		src.setActorName (srcActor.getName());
		src.setPortName (srcPortName);
		
		snk.setActorName (dstActor.getName());
		snk.setPortName (dstPortName);		
		
    	Channel chnnl = new Channel();		
		chnnl.setName(srcActor.getName() + "to" + dstActor.getName());
		chnnl.setTokenSize(tokenSize);
		chnnl.setInitialTokens(initialTokens);
		add (chnnl);
		
		chnnl.bind (src, snk);
    }
    
    /**
     * Insert a new actor between two actors
     * 
     * @param srcActor source actor
     * @param dstActor sink actor
     * @param chnnlToRemove channel to be removed
     * @param newActor new actor to be added
     * @param newActorInputPortName new actor input port name
     * @param newActorOutputPortName new actor output port name
     * @return array of channels at the input and output of the new actor
     */
    public Channel[] insertNewActorBetweenTwoActors (Actor srcActor, Actor dstActor, Channel chnnlToRemove,
    											Actor newActor, String newActorInputPortName, String newActorOutputPortName)
    {
    	String srcPortName = chnnlToRemove.getLink(Port.DIR.OUT).getPort().getName();
    	String dstPortName = chnnlToRemove.getLink(Port.DIR.IN).getPort().getName();
    	remove(chnnlToRemove);
    	
    	// Connect Src Actor -- > New Actor
		PortRef src = new PortRef ();
		PortRef snk = new PortRef ();
		
		src.setActorName (srcActor.getName());
		src.setPortName (srcPortName);
		
		snk.setActorName (newActor.getName());
		snk.setPortName (newActorInputPortName);
		
		Channel chnnl1 = new Channel();
		
		chnnl1.setName(srcActor.getName() + "to" + newActor.getName());		
		add (chnnl1);
		
		chnnl1.bind (src, snk);
		
    	// Connect New Actor -- > Dst Actor		
		src = new PortRef ();
		snk = new PortRef ();
		
		src.setActorName (newActor.getName());
		src.setPortName (newActorOutputPortName);
		
		snk.setActorName (dstActor.getName());
		snk.setPortName (dstPortName);
		
		Channel chnnl2 = new Channel();
		
		chnnl2.setName(newActor.getName() + "to" + dstActor.getName());		
		add (chnnl2);
		
		chnnl2.bind (src, snk);
		
		// Return the new channels
		Channel chnnls[] = new Channel[2];
		chnnls[0] = chnnl1;
		chnnls[1] = chnnl2;
		
		return chnnls;
    }
    
    @SuppressWarnings ("unchecked")
	public void clone (Graph anotherGraph)
    {
    	this.actorInstances = (HashMap<String, HashSet<Actor>>) anotherGraph.actorInstances.clone ();
    	this.modifiers = (HashMap<String, Modifier>) anotherGraph.modifiers.clone ();
    	this.channels = (HashSet<Channel>) anotherGraph.channels.clone ();
    	this.ports = (HashMap<Id, Port>) anotherGraph.ports.clone ();
    	this.actors = (HashMap<String, Actor>) anotherGraph.actors.clone ();
    }

    /**
     * Add an actor to the graph
     * @param actor new actor to add in the graph
     */
    public void add (Actor actor) {
        actor.setGraph (this);
        if (actors.containsKey (actor.getName ())) {
                throw new RuntimeException ("Actor with name " + actor.getName () + " added twice!");
        }

        actors.put (actor.getName (), actor);
        
        // add actor to the instances of its actor function
        if (actorInstances.get (actor.getFunc ())==null)
        	actorInstances.put (actor.getFunc (), new HashSet<Actor>());
        actorInstances.get (actor.getFunc ()).add (actor);
    }
    
    /**
     * Get all the channels connecting two actors
     * 
     * @param srcActor source actor
     * @param dstActor sink actor
     * @return HashSet containing channels connecting specified actors
     */
    public HashSet<Channel> getChannel (Actor srcActor, Actor dstActor)
    {
    	HashSet<Channel> result = new HashSet<Channel>();
    	for (Channel chnnl : channels)
    	{
    		if ((chnnl.getLink (Port.DIR.OUT).getActor () == srcActor) && 
    				(chnnl.getLink (Port.DIR.IN).getActor () == dstActor))
    			result.add (chnnl);
    	}
    	return result;
    }
    
    /**
     * Get a channel with a given name.
     * 
     * @param channelName name of the channel
     * @return Channel corresponding to the name
     */
    public Channel getChannel (String channelName)
    {
    	for (Channel chnnl : channels)
    	{
    		if (chnnl.getName ().equals (channelName))
    			return chnnl;
    	}
    	return null;
    }
    
    
    /**
     * Check if the graph has a channel with specified name
     * 
     * @param name name of the channel to be found in the graph
     * @return true if it is present in the graph, false otherwise
     */
    public boolean hasChannel(String name)
	{
		if(getChannel (name) == null)
			return false;
		else
			return true;
	}
    
    /**
     * Check if the graph has an actor with specified name
     * 
     * @param name name of the actor to be found in the graph
     * @return true if it is present in the graph, false otherwise
     */
    public boolean hasActor (String name)
    {
    	if (actors.get(name)==null)
    		return false;
    	else
    		return true;
    }

    /**
     * get actor with given name. 
     * Actor must exist!
     * We assume that all the actors have unique names in the graph.
     * 
     * @param actorName name of the actor in the graph.
     * @return actor with name equal to the argument. 
     */
    public Actor getActor (String actorName) 
    {
      Actor actor = actors.get (actorName);     
      if (actor==null)
        throw new RuntimeException ("Actor with name " + actorName + " not found!");
      return actor;
    }
    
    /**
     * Get a list of all the actors in the graph.
     * 
     * @return list of all the actors in the graph
     */
    public List<Actor> getActorList()
    {
    	return new ArrayList<Actor>(actors.values());
    }

    /**
     * Get an iterator to all the actors in the graph
     * 
     * @return an iterator to all the actors in the graph
     */
    public Iterator<Actor> getActors () {
    	return actors.values ().iterator ();
    }

    /**
     * Get actors corresponding to a specific function
     * @param func function
     * @return actors corresponding to a function
     */
    public Iterator<Actor> getActors (String func) {
    	HashSet<Actor> actors = actorInstances.get (func);
    	if (actors==null)
    		throw new RuntimeException (
    				"Function (actor type) " + "\"" + func + "\"" + "not found!");
    	return actors.iterator ();
    }
    
    /**
     * Get number of actors in the graph
     * 
     * @return number of actors in the graph
     */
    public int countActors () 
    {
    	return actors.size ();
    }
    
    /**
     * Get number of channels in the graph
     * 
     * @return number of channels in the graph
     */
    public int countChannels () 
    {
    	return channels.size ();
    }

    /**
     * Add a new port to the graph.
     * 
     * @param port new port to be added
     */
    public void add (Port port) 
    {
        port.setGraph (this);
        if (ports.containsKey (port)) {
                throw new RuntimeException ("Port " + port + " added twice!");
        }

        ports.put (port, port);
    }
    
    /**
     * check whether port with given name and function 
     * is contained in the graph
     * @param portId - Id object with port name and function
     * @return - true if port is contained
     */
    public boolean hasPort (Id portId) 
    {
    	Id cpPortId = portId.cloneId (); // we won't modify the caller's id    	
    	cpPortId.setGraph (this); // graph participates in the key
    	return ports.containsKey (cpPortId);
    }

    /**
     * get port with given name and function 
     * Port must exist!
     * @param portId - Id object with port name and function
     * @return Port instance by the given id.
     */
    public Port getPort (Id portId) 
    {
    	Id cpPortId = portId.cloneId (); // we won't modify the caller's id    	
    	cpPortId.setGraph (this); // graph participates in the key
    	Port port = ports.get (cpPortId);
    	if (port==null)
    		throw new RuntimeException ("Port " + portId + " not found!");
    	return port;
    }
    
    /**
     * Get an iterator to all the ports in the graph.
     * 
     * @return iterator to all the ports
     */
    public Iterator<Port> getPorts () 
    {
    	return ports.values ().iterator ();
    }

    /* Petro: currently don't need this method!
	public void remove (Port port)
	{
		ports.remove (port);		
	} */
    
    /**
     * Removes actor and all channels that are bound to it.
     * 
     * @param actor actor to be removed from the graph
     */
    public void remove (Actor actor)
    {
    	HashSet<Channel> channelsToRemove = new HashSet<Channel>(); 
    	for(Link lnk : actor.getAllLinks ())
    		channelsToRemove.add (lnk.getChannel ());
    	
    	Iterator<Channel> channelIter = channelsToRemove.iterator ();
    	while (channelIter.hasNext ())
    	{
    		Channel channel = channelIter.next ();
    		channel.unbind ();
    		this.remove (channel);
    	}    	
    	
    	actorInstances.get (actor.getFunc ()).remove (actor);
		actors.remove (actor.getName ());     	
    }
    
    /**
     * Removes a channel from the graph.
     * 
     * @param channel channel to be removed from the graph
     */
    public void remove (Channel channel)
    {
    	if (channel.isBound ()) channel.unbind ();
    	channels.remove (channel);    	
    }

    /**
     * Add a new channel to the graph.
     * @param channel channel to be added
     */
    public void add (Channel channel) 
    {
        if (channels.contains (channel)) {
                throw new RuntimeException ("Channel " + channel + " added twice!");
        }

        channel.setGraph (this);
        channels.add (channel);
    }
   
    /**
     * Add a modifier to this graph
     * @param modifier new modifier to add to the graph
     */
    public void add (Modifier modifier) 
    {
        modifier.setGraph (this);
        if (modifiers.containsKey (modifier.getParameter ())) {
                throw new RuntimeException ("Modifier for parameter " + modifier.getParameter () + " added twice!");
        }
        
        modifiers.put (modifier.getParameter (), modifier);
    }

    /**
     * Get a modifier by parameter name
     * @param parameter parameter name 
     * @return corresponding modifier
     */
    public Modifier getModifier (String parameter)
    {
        Modifier modifier = modifiers.get (parameter);     
        if (modifier==null)
          throw new RuntimeException ("No modifier for " + parameter + " was found!");
        return modifier;
      }
    
    /**
     * Get an iterator to all the modifiers in the graph.
     * @return iterator to all the modifiers
     */
    public Iterator<Modifier> getModifiers () {
    	return modifiers.values ().iterator ();
    }
    
    /**
     * Print information about the graph
     */
    public void dump () 
    {
        System.out.println (actors);
        System.out.println (ports);
        System.out.println (channels);
    }
    
    /**
     * Get an iterator to all the channels in the graph.
     * 
     * @return get an iterator to all the channels
     */
    public Iterator<Channel> getChannels () 
    {
    	return channels.iterator ();
    }
    
    /**
     * Get list of all the channels in the graph.
     * 
     * @return list of all the channels
     */
	public List<Channel> getChannelList()
	{
		return new ArrayList<Channel>(channels);
	}
}
