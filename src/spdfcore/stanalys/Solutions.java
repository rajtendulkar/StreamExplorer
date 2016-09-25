package spdfcore.stanalys;

import spdfcore.*;

import java.util.*;

/** 
 * Solver of balance equations of SPDF
 * @author Peter Poplavko
 */
public class Solutions {
	
	//=====================================
	//========== data  ====================
	
	/**
	 * Actor and its mapped solution expression
	 */
	private HashMap<Actor,Expression> solutions = new HashMap<Actor,Expression>();
	
	/**
	 * Should we throw an exception when unable to get solutions?
	 */
	boolean throwException = true;
	//=====================================
	
	/**
	 * Clear all the solutions
	 */
	public void clear ()
	{
		solutions.clear ();
	}
	
	@SuppressWarnings ("unchecked")
	public void copy (Solutions second)
	{
		solutions = (HashMap<Actor, Expression>) second.solutions.clone ();
	}	
	
	/**
	 * Check if the solutions contain an actor
	 * @param actr actor to check for
	 * @return true if solutions contains actor, false otherwise
	 */
	public boolean contains (Actor actr)
	{
		if (solutions.containsKey (actr))
			return true;
		else
			return false;
	}
	
	/**
	* local data needed when solving the balance equations
	*/
	class Data 
	{
		// static input data
		Graph graph;
		GraphExpressions expressions;
		
		// dynamic helper data:
		HashMap<Actor, Fraction> fracSolutions = new HashMap<Actor,Fraction>();
		HashSet<Channel>  visitedChannels = new HashSet<Channel>();	
		// every actor except the start actor has a channel through which
		// we entered into the given actor:
		HashMap<Actor, Channel> predecessors = new HashMap<Actor,Channel>();
	}

	/**
	 * Before solving, tell the solver whether we want an exception
	 * if inconsistency is found
	 */
	public void setThrowExceptionFlag (boolean flag) {
		throwException = flag;
	}
	
	/**
	 * Gets an expression to solution of an actor
	 * 
	 * @param actor an actor to get a solution for
	 * @return solution of balance equations
	 */
	public Expression getSolution (Actor actor) {
		return solutions.get (actor);
	}
	
	/**
	 * print all solutions to a string
	 */
	@Override
	public String toString () {
		StringBuffer buf = new StringBuffer ();
		Iterator<Map.Entry<Actor, Expression>> solIter = solutions.entrySet ().iterator ();
		while (solIter.hasNext ()) {
			Map.Entry<Actor, Expression> entry = solIter.next ();			
			buf.append (""+entry.getKey ().getName ()+"("+entry.getValue ()+") ");
		}
		return buf.toString ();
	}
	
	/**
	 * println all solutions to standard input
	 */
	public void dump () {
		System.out.println (this.toString ());
	}
	
	
	/** 
	 * Use method of "Software Synthesis from Dataflow Graphs" book
	 *  of Bhattacharrya, generalized to SPDF graphs
	 *  
	 *    If inconsistency is found and throwExpetion==true (default)
	 *    then throws am exception.
	 *    
	 * @param graph       - SPDF graph
	 * @param expressions - GraphExpressions for parsed rate expressions
	 * @return - InconsistencyProof if not successful (in case exception is switched off)   
	 */
	public InconsistencyProof solve (Graph graph, GraphExpressions expressions) {
		Actor startActor = getArbitraryActor (graph);
		return solve (graph, expressions, startActor);
	}
	
	/**
	 *   Solve balance equations. This version allows to select the start actor 
	 *   for debugging/testing purposes. Any start actor should lead to the same result
	 *   modulo which inconsistency cycle is found if there are many.
	 *   
	 *    If inconsistency is found and throwExpetion==true (default)
	 *    then throws am exception.
	 *   
	 * @param graph input graph
	 * @param expressions graph expressions
	 * @param startActor starting actor to solve the balance equations
	 * @return - InconsistencyProof if not successful (in case exception is switched off)   
	 */
	public InconsistencyProof solve (Graph graph, GraphExpressions expressions, Actor startActor) {	
		Data data = new Data ();
		data.graph = graph;
		data.expressions = expressions;

		data.fracSolutions.put (startActor, new Fraction ("1"));
		
		//-- do the main job ---
		solveRecursively (startActor, data);
		//-------------
		
		// finalize
		InconsistencyProof inconsistency = checkResults (data);
		if (inconsistency!=null) return inconsistency;
		
		HashMap<Actor,Expression> scaledSolutions = scaleFractions (data);
		solutions.putAll (scaledSolutions);
		return null; // no inconsistency
	}
	
	/**
	 *  Look for rate inconsistency
	 *  
	 * @param data - solver state after solving
	 * @return - inconsistency proof if did not throw an exception
	 */
	private InconsistencyProof  checkResults (Data data) {
		// have solutions for all actors?
		if (data.graph.countActors ()!=data.fracSolutions.size ()) {
			// some actors are not connected to the start actor
			throw new RuntimeException ("The graph is not completely connected!");
		}
		
		// have an undirected cycle with inconsistent rates?
		InconsistencyProof inconsistency = new InconsistencyProof ();
		if (inconsistency.findBadCycle (data) ) {
			if (throwException)
			   throw new RuntimeException (inconsistency.diagnostics ());
			return inconsistency;
		}
		return null; // no inconsistency
	}
	
	/**
	* pick up the first actor we can get
	* 
	* @param graph input graph
	*/
	private Actor getArbitraryActor (Graph graph) {
		return graph.getActors ().next ();
	}
	
	/**
	 *  one step of recursion, from solution for current actor
	 *  find solutions of its neighbors, and call this function
	 *  for each neighbor
	 * @param actor - current actor
	 * @param data  - context of the solver with (intermediate) results
	 */
	private void solveRecursively (Actor actor, Data data) {
		Fraction solution = data.fracSolutions.get (actor);
		
		for(Channel.Link link : actor.getAllLinks ())
		{		
			Port port = link.getPort ();
			Expression rate = data.expressions.getRate (port);
			
			Actor otherActor  = link.getOpposite ().getActor ();

			// if the neighbor does not have solution yet
			if (!data.fracSolutions.containsKey (otherActor)) {	
				Channel channel = link.getChannel ();
				Port otherPort    = link.getOpposite ().getPort ();
				Expression  otherRate = data.expressions.getRate (otherPort);

				Fraction otherSolution = 
						Fraction.multiply (solution, new Fraction (rate, otherRate));
				data.fracSolutions.put (otherActor, otherSolution);
				data.visitedChannels.add (channel);				
				data.predecessors.put (otherActor, channel);
				
				solveRecursively (otherActor, data);
			}
		}
	}	
	
	/**
	 * Scale the fractional solutions from the results of the solver
	 * to be non-fractional, using the least common multiple of 
	 * the denominators.
	 * 
	 * @param data - contains the results of the solver
	 * @return the scaled solutions
	 */
	HashMap<Actor,Expression> scaleFractions (Data data) {
		
		// Find the LCM of denominators
		Expression lcm = new Expression ("1");
		Iterator<Map.Entry<Actor, Fraction>> fIter = 
				data.fracSolutions.entrySet ().iterator ();
		while (fIter.hasNext ()) {
			Map.Entry<Actor, Fraction> entry = fIter.next (); 
			Expression denom = entry.getValue ().getDenominator ();
			Expression gcd = Expression.gcd (lcm, denom);
			lcm = Expression.divide (lcm, gcd);
			lcm = Expression.multiply (lcm, denom);			
		}
		
		// Scale all solutions by the LCM
		fIter = data.fracSolutions.entrySet ().iterator (); // rewind back
		HashMap<Actor,Expression> scaledSolutions = new HashMap<Actor,Expression>();
		while (fIter.hasNext ()) {
			Map.Entry<Actor, Fraction> entry = fIter.next (); 
			Actor    actor      = entry.getKey ();
			Fraction fractional = entry.getValue ();
			Fraction scaled     = Fraction.multiply (fractional, new Fraction (lcm));
			scaledSolutions.put (actor, scaled.toExpression ());
		}
		return scaledSolutions;
	}
}