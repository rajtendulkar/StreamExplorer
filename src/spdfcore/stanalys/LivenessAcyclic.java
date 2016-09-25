package spdfcore.stanalys;

import spdfcore.*;
import java.util.*;

/**
 * The simplest sufficient condition for liveness:
 *   the graph must be acyclic.
 */

public class LivenessAcyclic {
	
	//========== data  ====================
	boolean throwException = true;
	//=====================================

	/**
	 * Before checking the liveness, tell the checker whether 
	 * we want an exception if graph is not live
	 */
	void setThrowExceptionFlag (boolean flag) {
		throwException = flag;
	}

	/**
	 * check the liveness (in this version - whether the graph is acyclic)
	 * @param graph
	 * @return - null if live; if not live throws exception unless told
	 *           not to do so, then it returns a cyclic path
	 */
	public LinkedList<Channel> check (Graph graph) {
		Data data = new Data (graph);
		
		Actor actor;
		LinkedList<Channel> cycle = null;
		
		while ((actor=data.nextNonvisitedActor ())!=null) {
			cycle = visitSuccessors (actor, data);
			if (cycle!=null) break;
		}
		
		if (cycle!=null && throwException) {
			throw new RuntimeException ("Cycle found: " + cycle.toString ());
		}
		
		return cycle;
	}

	/**
	 * 
	 * local data for liveness checker
	 *
	 */
	private class Data {
		Iterator<Actor> examinedActors;
		HashSet<Actor>  visited  = new HashSet<Actor>();
		HashSet<Actor>  finished = new HashSet<Actor>();
		boolean cycleComplete = false;
		Actor cycleActor;
		
		Data (Graph g) {
			examinedActors = g.getActors ();
		}
	
	
		Actor nextNonvisitedActor () {
			while (examinedActors.hasNext ()) {
				Actor actor = examinedActors.next ();
				if (!visited.contains (actor)) {
					return actor;
				}
			}
			return null;
		}
	}
	
	/**
	 *  visit non-visited successors of the given actor 
	 *  @return - directed cycle if found any
	 */
	LinkedList<Channel> visitSuccessors (Actor actor, Data data) {
		data.visited.add (actor);
		for (Channel.Link link  : actor.getLinks (Port.DIR.OUT))
		{		
			Actor successor = link.getOpposite ().getActor ();
			if (data.visited.contains (successor)) {
				if (!data.finished.contains (successor)) {
					// cycle found! 
					//   initialize the cycle data
					data.cycleActor = successor;
					LinkedList<Channel> cycle = new LinkedList<Channel>();
					// update the cycle 
					cycle.addFirst (link.getChannel ());
					if (actor.equals (data.cycleActor))
						data.cycleComplete = true;
					return cycle;
				}
			}
			else { // not visited
				LinkedList<Channel> cycle = visitSuccessors (successor, data);
				if (cycle!=null && !data.cycleComplete) {
					// we are in the process of discovering a cycle
					// update the cycle 
					cycle.addFirst (link.getChannel ());
					if (data.cycleActor.equals (actor)) 
						data.cycleComplete = true;
				}
				if (cycle!=null) return cycle;
			}
		}
		data.finished.add (actor); // we have visited all the successors
		return null; // no cycle is reachable from this actor
	}
}
