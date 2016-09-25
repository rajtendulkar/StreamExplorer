package spdfcore.stanalys;

import spdfcore.*;
import java.util.*;


/**
 *  "Parameter Communication" class manages parameter sources and users. 
 *  You can find all sources and all users and bind them to each other
 *  by edges that model parameter communication.
 *   
 *  The sources of parameters are modifiers, which delegate their job 
 *  to "period actors". 
 */
final public class ParamComm {
	
	//-------------- data -----------------
	private Graph graph = null;

	/** lookup from modified parameter to period actors 
	 * (eventually, there should be only one period actor per modified parameter)
	 */
	private HashMap<String,HashSet<Actor>> periodActors 
		= new HashMap<String,HashSet<Actor>>();
	
	/** lookup from period actor to the corresponding modifier*/
	private HashMap<Actor,Actor> modifierOfPeriodActor = new HashMap<Actor,Actor>(); 
	
	/** the set of all environment parameters. See getEnvironmentParams ()  */
	private HashSet<String> environmentParams = new HashSet<String>();	
	
	/** lookup from modifier actor to parameters */
	private HashMap<Actor,HashSet<String>> modifiedParameters 
	     = new HashMap<Actor,HashSet<String>>();
	
	/** lookup from user to parameters */
	private HashMap<Actor,HashSet<String>> usedParameters = new HashMap<Actor,HashSet<String>>();
	
	/** lookup from parameter to its users */
	private HashMap<String, HashSet<Actor>> users = new HashMap<String, HashSet<Actor>>(); 
	//-------------------------------------
	
	/**
	 * set the graph to be managed by this ParamComm object
	 */
	public void setGraph (Graph graph) {
		if (this.graph!=null)
			throw new RuntimeException ("Cannot set the graph again, already set!");
		this.graph = graph;
	}
	
	/**
	 * get period actor for the parameter. 
	 * If it does not exist (e.g. environment parameter) then return null
	 */
	public Actor getPeriodActor (String parameter) {
		if (!periodActors.containsKey (parameter))
			return null;
		// the set must contain only one element (this is checked elsewhere)
		return periodActors.get (parameter).iterator ().next ();
	}
	
	/**
	 * get the user actors for the parameter.
	 *   by convention, the modifier of a parameter can still be its user,
	 *   we do not exclude the modifiers from the set of users if they 
	 *   use the parameter. 
	 */
	public HashSet<Actor> getUserSet (String parameter) {
		if (!users.containsKey (parameter))
			return new HashSet<Actor>(); // empty set
		return users.get (parameter);
	}

	/**
	 * get the set of parameters used by the actor
	 *   by convention, a parameter modified by an actor can be also a parameter used,
	 *   we do not exclude the parameters modified from the set of parameter used. 
	 */
	public HashSet<String> getUsedParameterSet (Actor actor) {
		if (!usedParameters.containsKey (actor))
			return new HashSet<String>(); // empty set
		return usedParameters.get (actor); 	
	}

	/**
	 * get the parameters modified by the given actor
	 */
	public HashSet<String> getModifiedParameterSet (Actor actor) {
		if (!modifiedParameters.containsKey (actor))
			return new HashSet<String>(); // empty set
		return modifiedParameters.get (actor); 	
	}
	
	/**
	 * get all the modified parameters
	 */
	public Iterator<String> getModifiedParameters () {
		return periodActors.keySet ().iterator ();
	}
	
	/**
	 *  Environment parameters are those that are not modified by an actor.
	 *  Those are assumed to be (unknown) constants, provided at run-time
	 *  at the start of execution for the whole life cycle of the graph. 
	 */
	Iterator<String> getEnvironmentParameters () {
		return environmentParams.iterator ();
	}
	
	/**
	 *  Checks wether given actor is a period actor
	 * 
	 */
	public boolean isPeriodActor (Actor actor) {
		return modifierOfPeriodActor.containsKey (actor);
	}
	
	/**
	 *  One basic step of the analysis flow:
	 *    insert the graph nodes called "period actors" (also known as `emitters').
	 *  We connect every period actor to the corresponding modifier. 
	 *  A "period actor" is a purely analytical node that fires at  
	 *  each modification period.
	 *  
	 */
	public void insertPeriodActors () {		
		Iterator<Modifier> modifiers = graph.getModifiers ();
		
		while (modifiers.hasNext ()) {
			Modifier modifier = modifiers.next ();
			String func   = modifier.getFunc ();
			String param  = modifier.getParameter ();
			String period = modifier.getPeriod ();
			
			// Create new output port to connect the period actor
			Port portOut = new Port (Port.DIR.OUT);
			portOut.setFunc (func);
			//portOut.setName ("_auto_" + param + "_out"); // a name clear for debugging 
			portOut.setName ("_" + param + "_out"); // a name clear for debugging 
			portOut.setRate ("1");			
			graph.add (portOut);

			// Create new function for the period actors 
			//String periodFunc = "_Auto_" + param + "_Period"; // for debug
			String periodFunc = "<" + param + ">"; // for debug
			
			// Create the input port for that function
			Port portIn = new Port (Port.DIR.IN);
			portIn.setFunc (periodFunc);
			//portIn.setName ("_auto_in");
			portIn.setName ("in");
			portIn.setRate (period);
			graph.add (portIn);
			
			// prepare the lookup
			HashSet<Actor> periodActorSet = new HashSet<Actor>(); 
			periodActors.put (param, periodActorSet);
			
			//  In future we may support multiple instances of the same modifier
			//  (by parameter renaming -- later in the design flow)   
			Iterator<Actor> modifierInstances = graph.getActors (func);
			
			while (modifierInstances.hasNext ()) {
				Actor modifierActor  = modifierInstances.next ();
				
				// Create the period actor
				Actor periodActor = new Actor ();
				periodActor.setAutoFlag ();  // this is a purely analytical actor
				periodActor.setFunc (periodFunc);
				//periodActor.setName ("_auto_" + param + "_period" + idx++);
				periodActor.setName ("<" + param + ">" ); //+ idx++);
				graph.add (periodActor);
				periodActorSet.add (periodActor);
				
				// Bind the period actor to the modifier
		        PortRef src = new PortRef ();
		        src.setActor (modifierActor);
		        src.setPort (portOut);
		        PortRef snk = new PortRef ();
		        snk.setActor (periodActor);
		        snk.setPort (portIn);
		        Channel channel = new Channel ();
		        channel.setAutoFlag ();    // this is a purely analytical channel
		        graph.add (channel);
		        channel.bind (src, snk);
		        
		        // Create a lookup entry
		        modifierOfPeriodActor.put (periodActor, modifierActor);
			}
		}
	}
	
	/**
	 *  Look which parameters are being used by each actor and
	 *  identify the source of each parameter.
	 *  If parameter is modified by an actor, then the "source"
	 *  is the associated period actor.
	 *  Otherwise the parameter is supposed to be provided by graph environment,
	 *  and is not modified during the execution.
	 */
    public void identifyParameterSources (GraphExpressions expressions) {
    	// identify sources of modified parameters
    	establishUniquePeriodActors ();
    	    	
    	// identify which parameters are not modified
    	// (those that have no modifier actor types) 
    	Iterator<Port> ports = graph.getPorts ();
    	while (ports.hasNext ()) {
    		Port port = ports.next ();
    		Expression rate = expressions.getRate (port);
    		if (rate==null) continue; // can happen for unconnected port
    		HashSet<String> rateParameterSet = rate.getParameterSet ();
    		Iterator<String> rateParameters = rateParameterSet.iterator (); 
    		while (rateParameters.hasNext ()) {
    			String param = rateParameters.next ();
    			if (periodActors.get (param)==null) {
    				// no period actor => this is environment parameter
    				environmentParams.add (param);
    			}
    		}
    	}    	

    	// TODO: allow actor types to declare which parameters they would like
    	// to use internally. It is not only the rate where parameters may be
    	// required, but for some internal use.
    	//Iterator<InternalUsers> internalUsers = graph.getInternalUsers;
    	//...
    }
    
    /**
     *    Check whether all the modified parameters have
     *  have a unique instance of modifier. Every modifier instance
     *  corresponds to one period actor.
     */
    private void establishUniquePeriodActors () {
    	Iterator<Map.Entry<String, HashSet<Actor>>> periodActorIter 
    		= periodActors.entrySet ().iterator ();
    	while (periodActorIter.hasNext ()) {
    		Map.Entry<String, HashSet<Actor>> entry = periodActorIter.next (); 
    		String param                 = entry.getKey ();
    		HashSet<Actor> periodActorSet = entry.getValue ();
    		String func = graph.getModifier (param).getFunc ();
    		if (periodActorSet.isEmpty ()) {
    			throw new RuntimeException (
    					"No actor of type \"" + func
    					+ "\", which should provide parameter " + param  
    					+ ", is found in the graph!");
    		}
    		if (periodActorSet.size ()>1) {
    			// Currently we do not support a modifier instantiated multiple times. 
    		    // TODO:  we could rename of the parameters but then need hints 
    			// from the designer which user uses which modifier
    			throw new RuntimeException (
    					"Multiple actors of type \"" + func + 
    					"\" modify parameter" + param + ". Ambiguous!");    			    			
    		}
    		
    		// create lookup from modifier actor to parameter
    		Actor periodActor = periodActorSet.iterator ().next (); 
    		Actor modifierActor = modifierOfPeriodActor.get (periodActor);
    		if (modifiedParameters.get (modifierActor)==null) {
    			modifiedParameters.put (modifierActor,new HashSet<String>());
    		}
    		modifiedParameters.get (modifierActor).add (param);    		
    	}
    }

    /**
     * 
     * Find the user actors for every parameter.
     * 
     * @param expressions - all rates 
     * @param solutions   - solutions of balance equations
     */
    public void identifyParameterUsers (GraphExpressions expressions, Solutions solutions) {
    	
    	Iterator<Actor> actors = graph.getActors ();
    	while (actors.hasNext ()) {
    		Actor actor = actors.next ();
    		HashSet<String> solParameterSet 
    			= solutions.getSolution (actor).getParameterSet ();
    		addUser (solParameterSet, actor);

    		// we look at the ports of *all* actors, including
    		// period actors; this rules out the possibility
    		// to interpret periods like 3*p as 3 \circ p,
    		// but:
    		//   1. To be consistent with the rates, it is better
    		//      to interpret polynomial  periods as polynomials. 
    		//   2. Supporting \circ in rates and periods is "syntactic sugar"
    		//      that can be optionally added later.
    		//   3. Not looking at the ports of periodic actors would open
    		//      a "can of worms", e.g. the unschedulable { A set p[1]; set q[p]; } 
    		
    		for(Channel.Link link : actor.getAllLinks ())
    		{    		
    			HashSet<String> rateParameterSet 
    					= expressions.getRate (link.getPort ()).getParameterSet ();
    			addUser (rateParameterSet, actor);
    		}        				
    	}
    }
    
    /**
     *  register an actor as user of the given set of parameters
     * @param parameterSet
     * @param actor
     */
    private void addUser (HashSet<String> parameterSet, Actor actor) {
    	if (!usedParameters.containsKey (actor))
    		usedParameters.put (actor, new HashSet<String>());
    	usedParameters.get (actor).addAll (parameterSet);
    	
    	Iterator<String> parameters = parameterSet.iterator ();
    	while (parameters.hasNext ()) {
    		String parameter = parameters.next ();
    		if (!users.containsKey (parameter))
    			users.put (parameter, new HashSet<Actor>());
    		users.get (parameter).add (actor);
    	}
    }
    
    /**
     *   Connect the period nodes to the users to model the propagation
     *   of parameters to the users at every period.
     *   
     *   Note that this should be done after a successful safety check,
     *   otherwise this step will not succeed or a later safety check 
     *   may say "unsafe".
     *   @param solutions - solutions of balance equations
     */
    public void connectSourcesToUsers (Solutions solutions) {
    	// pair: ( parameter, set of users )
    	Iterator<Map.Entry<String, HashSet<Actor>>> paramUserPairs =
    			users.entrySet ().iterator ();
    	while (paramUserPairs.hasNext ()) {
    		Map.Entry<String, HashSet<Actor>> pair = paramUserPairs.next ();
    		String parameter       = pair.getKey ();
    		HashSet<Actor> userSet = pair.getValue ();
    		Actor periodActor = getPeriodActor (parameter);
			Expression solPeriodActor = solutions.getSolution (periodActor);
    		
    		Iterator<Actor> userIter = userSet.iterator ();
    		int idx = 0;
    		while (userIter.hasNext ()) {
    			Actor user = userIter.next ();
    			if (user.equals (periodActor))
    				// do not connect to the period actor
    				continue;
    			if (getModifiedParameterSet (user).contains (parameter))
    				// do not connect to the modifier of this parameter  
    				continue;
    			Expression solUser = solutions.getSolution (user);
    			// This division must be successful in a safe graph:
    			Expression ratioUserToSol = Expression.divide (solUser, solPeriodActor);
    			// Create port at the source
    			Port portOut = new Port (Port.DIR.OUT);
    			portOut.setFunc (periodActor.getFunc ());
    			portOut.setName ("out"+idx++);
    			portOut.setRate (ratioUserToSol.toString ());
    			graph.add (portOut);
    			// Create port at the user
    			Port portIn = new Port (Port.DIR.IN);
    			portIn.setFunc (user.getFunc ());
    			portIn.setName ("_" + parameter + "_in");
				portIn.setRate ("1");
				// if we have already created the port for the given
				// function -- take the existing one
    			if (!graph.hasPort (portIn)) {
    				graph.add (portIn);
    			}
    			// connect the two ports
    			PortRef src = new PortRef ();
    			src.setActor (periodActor);
    			src.setPort (portOut);
    			PortRef dst = new PortRef ();
    			dst.setActor (user);
    			dst.setPort (portIn);
    			Channel channel = new Channel ();
		        channel.setAutoFlag ();    // this is a purely analytical channel
		        graph.add (channel);
		        channel.bind (src, dst);
    		}
    	}
    }
}