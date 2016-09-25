package spdfcore.stanalys;

import java.util.*;

import spdfcore.*;

/**
 * Diagnostics of inconsistent undirected cycles when solving the
 * balance equations. 
 */
public class InconsistencyProof {

	//---- data ------------------
	private LinkedList<Channel> cycle;	
	private Fraction out2inRatio; 
	//--------------------------------
	
	/**
	 * a undirected cycle found by findBadCycle in "IN-to-OUT" direction
	 * of a (rather arbitrary) 'reference channel'
	 */
	@SuppressWarnings ("unchecked")
	public LinkedList<Channel> getCycle () {		
		return (LinkedList<Channel>) cycle.clone ();
	}
	
	/**
	 * mismathed factors in the cycle: "OUT" to "IN" ratio
	 * of a (rather arbitrary) 'reference channel'
	 */	
	public Fraction getOutToInRatio () {
		return out2inRatio; 		
	}
	
	/** Check the channels we did not visit.
	 *  For them the balance equations should hold too.
	 *  Otherwise the rates are inconsistent, there is at least one 
	 *  undirected cycle with inconsistent rates.
	 *  If such cycles exists, fill it into the cycle field
	 *  so that we can report this error to the designer.
	 *  @return true if bad cycle was found
	 */
	public boolean findBadCycle (Solutions.Data data) {
		Iterator<Channel> channels = data.graph.getChannels ();
		while (channels.hasNext ()) {
			Channel channel = channels.next ();
			if (data.visitedChannels.contains (channel)) continue;
		
			// check whether the balance equation holds
			Channel.Link linkIN = channel.getLink (Port.DIR.IN);
			Port portIN = linkIN.getPort ();
			Expression rateIN = data.expressions.getRate (portIN);
			Actor actorIN = linkIN.getActor ();
			Fraction solutionIN = data.fracSolutions.get (actorIN);
		
			Channel.Link linkOUT = channel.getLink (Port.DIR.OUT);
			Port portOUT = linkOUT.getPort ();
			Expression rateOUT = data.expressions.getRate (portOUT);
			Actor actorOUT = linkOUT.getActor ();
			Fraction solutionOUT = data.fracSolutions.get (actorOUT);

			Fraction vOUT = Fraction.multiply (solutionOUT, new Fraction (rateOUT));
			Fraction vIN  = Fraction.multiply (solutionIN,  new Fraction (rateIN));
		
			// vOUT / vIN == 1 ?
			out2inRatio = Fraction.divide (vOUT, vIN);
			if (!out2inRatio.equals (new Fraction ("1"))) {
				findCycle (channel, data);
				return true;
			}
		}
		cycle = null;
		return false;
	}
	
	/**
	* given the channel where inconsistency was detected, expose 
	* the complete cyclic path with inconsistency by combining 
	* this channel with the paths which were followed by the solver 
	* when trying to derive  the solution.
	 */
	private void findCycle (Channel refChannel, Solutions.Data data) {
		Actor actorOUT = refChannel.getLink (Port.DIR.OUT).getActor ();
		LinkedList<Channel> outToStart = pathToStart (actorOUT, data);
		Actor actorIN = refChannel.getLink (Port.DIR.IN).getActor ();
		LinkedList<Channel> inToStart = pathToStart (actorIN, data);
		
		// cut off the common part from start to OUT and IN
		ListIterator<Channel> pToOut = outToStart.listIterator (outToStart.size ());
		ListIterator<Channel> pToIn  =  inToStart.listIterator ( inToStart.size ());
		// pToOut -- 'previous ()' leads to Out
		// pToIn  -- 'previous ()' leads to In

		// "fork"= divergence at a point where we did not reach
		// neither IN nor OUT of the problematic point
		Channel forkToOut = null;
		Channel forkToIn  = null;
		boolean forked = false;
		
		// go from start to IN and OUT until they diverge
		while (pToOut.hasPrevious () && pToIn.hasPrevious () && !forked) {
			forkToOut = pToOut.previous ();
			forkToIn  = pToIn.previous ();
			
			forked = (!forkToOut.equals (forkToIn));
		}
		
		// start forming the cycle conforming by its direction 
		// to the refChannel (a convention) 
		cycle = new LinkedList<Channel>();
				
		// go from divergence point to IN
		if (forked)
			cycle.add (forkToIn);		
		while (pToIn.hasPrevious ())
			cycle.add (pToIn.previous ());
		// go from IN to OUT
		cycle.add (refChannel);		
		// go from OUT to divergence point
		// do that by reversing the path from divergence point to OUT
		LinkedList<Channel> divToOut = new LinkedList<Channel>();
		if (forked)
			divToOut.add (forkToOut);
		while (pToOut.hasPrevious ())
			divToOut.add (pToOut.previous ());
		//   now reverse:
		ListIterator<Channel> pToDiv =  divToOut.listIterator (divToOut.size ());  
		//pToDiv --- means  previous () leads to divergence
		while (pToDiv.hasPrevious ()) {
			cycle.add (pToDiv.previous ());
		}
	}
	
	/**
	 * @param actor - where to start from
	 * @param data - solver data
	 * @return the undirected path from actor to start actor using the 'history' of 
	 * deriving the fractional solutions, recorded by the solver in 'data'.
	 */
	static private LinkedList<Channel> pathToStart (Actor actor, Solutions.Data data) {
		LinkedList<Channel> path = new LinkedList<Channel>();
		while (true)
		{
			Channel predecessor = data.predecessors.get (actor);
			if (predecessor!=null)
				path.add (predecessor);
			else
				return path;
			actor = predecessor.getOpposite (actor);
		}
	}
	
	/**
	 * Provide the (hopefully) human-readable diagnostics of the inconsistency
	 * 
	 */
	public String diagnostics () {
		if (cycle==null)
			return "No inconsistency";
		StringBuffer buf = new StringBuffer ();
		buf.append ("This undirected cycle has a problem:\n");
		ListIterator<Channel> cycIter = cycle.listIterator ();
		while (cycIter.hasNext ()) {
			buf.append (cycIter.next ());
			buf.append ("\n");
		}
		buf.append ("Mismatched factors:\n");
		if (!getOutToInRatio ().getDenominator ().equals (new Expression ("1"))) {
				buf.append ("IN: ");
				buf.append (getOutToInRatio ().getDenominator ());
				buf.append ("\n");
		}		
		if (!getOutToInRatio ().getNumerator ().equals (new Expression ("1"))) {
			buf.append ("OUT: ");
			buf.append (getOutToInRatio ().getNumerator ());
			buf.append ("\n");
		}		
		return buf.toString ();
	}
	
	/**
	 * Diagnostics
	 */
	@Override
	public String toString () {
		if (cycle==null)
			return "No inconsistency";
		else 
			return "Inconsistency "+ super.toString () + ", see diagnostics ().";				
	}
}	
