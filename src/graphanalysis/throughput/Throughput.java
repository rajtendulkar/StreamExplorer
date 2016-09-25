package graphanalysis.throughput; 

import graphanalysis.Kosaraju;

import java.util.*;
import java.lang.Integer;


import spdfcore.*;
import spdfcore.Channel.Link;
import spdfcore.stanalys.*;

/**
 * 
 * Algorithm to calculate throughput from SDF graph.
 * 
 * This algorithm is taken from paper : 
 * Throughput Analysis of Synchronous Data Flow Graphs
 * 
 * Reference code can be found in SDF3 tool.
 * 
 * TODO : I have not yet tested this code. So not sure if it works correctly.
 * 
 * @author Pranav Tendulkar
 *
 */
public class Throughput 
{
	/**
	 * Solutions of graph.
	 */
	private Solutions globalSolutions = new Solutions ();		
	
	/**
	 * Expressions to get the solutions. 
	 */
	private GraphExpressions expressions = new GraphExpressions ();	
	
	/**
	 * Transition system.
	 * 
	 * @author Pranav Tendulkar
	 *
	 */
	class TransitionSystem 
	{
		/**
		 * State of the system.
		 */
		class State
		{
			private HashMap<Actor, List<Integer>> actClk = new HashMap<Actor, List<Integer>>();
			private HashMap<Channel, Integer> ch = new HashMap<Channel, Integer>();
			private int repeatedStateIndex = 0;
			
			private int glbClk=0;
			
			// Clone the argument State variable
			//public State (State argState)
			@SuppressWarnings ("unchecked")
			public void clone (State argState)
			{
				glbClk = argState.glbClk;				
					
				Iterator<Actor> actrIter = argState.actClk.keySet ().iterator ();
				while (actrIter.hasNext ())
				{
					Actor actr = actrIter.next ();					
					List<Integer> dstList = (List<Integer>) ((ArrayList<Integer>) argState.actClk.get (actr)).clone (); 					
					actClk.put (actr, dstList);
				}
				
				ch = (HashMap<Channel, Integer>)argState.ch.clone ();	
			}
			
			public State ()
			{
				// Initialize all the channels with initial tokens.
				Iterator<Channel> chnnlIter = graph.getChannels ();
				while (chnnlIter.hasNext ())
				{
					Channel chnnl = chnnlIter.next ();
					ch.put (chnnl, chnnl.getInitialTokens ());
				}
			}
			
			// In order that HashSet for storedStatesHash works, we have to override both equals
			// and hashCode () methods.
			@Override
			public int hashCode ()
			{  
				return glbClk + ch.hashCode () + actClk.hashCode ();
			}
			
			@Override
			public boolean equals (Object inputState)
			{
				State state = (State) inputState;
				// First check if Global Clocks are equal
				if (state.glbClk != glbClk)
					return false;
				
				// Now we check if channels contain same tokens
				if (ch.equals (state.ch) == false)
					return false;					
			
				// Lastly we check the Actor clocks
				if (actClk.size () == state.actClk.size ())
				{
					Iterator<Actor> actrIter = actClk.keySet ().iterator ();
					while (actrIter.hasNext ())
					{
						Actor actr = actrIter.next ();
						List<Integer> list1 = actClk.get (actr);
						List<Integer> list2 = state.actClk.get (actr);
						if (list1.size () == list2.size ())
						{
							for (int i=0;i<list1.size ();i++)
								if (list2.contains (list1.get (i)) == false)
									return false;
						}
						else
							return false;						
					}					
				}
				else
					return false;
				
				return true;
			}			
		}
		
		// I am using list here, because I need to know in
		// what order the states are stored. I was using hashset
		// earlier, but they store the data in random order.
		// I made a small test with lists, and found out that they
		// store the data in order.
		List<State> storedStates = new ArrayList<State>();
		private HashSet<State> storedStatesHash = new HashSet<State>();
		State currentState;
		State previousState;
		Graph graph = new Graph ();		
		Actor outputActor;
		Solutions transSol = new Solutions ();		
		
		public TransitionSystem (Graph inputGraph)
		{			
			// Initialize the graph
			graph.clone (inputGraph);
			
			currentState = new State ();
			previousState = new State ();
			
			// initialize output actor which has lowest repetition count.
					
			GraphExpressions tempexpressions = new GraphExpressions ();			
			tempexpressions.parse (graph);
			
			// Calculate the Repetition Vector			
			transSol.solve (graph, tempexpressions);
			
			int count = 0x0FFFFFFF;
			// pick up the actor with lowest count.
			Iterator<Actor> actrIter = graph.getActors ();
			while (actrIter.hasNext ())
			{
				Actor actr = actrIter.next ();
				if (transSol.getSolution (actr).returnNumber () < count)
				{
					count = transSol.getSolution (actr).returnNumber ();
					outputActor = actr;					
				}
			}			
		}

		public double execSDFgraph () 
		{
			int repCnt=0;
			double clkStep=0;			
			while (true)
			{
				// Store Partial State to check progress
				// clear lists.
				// create initial state.	
				
				// Finish Actor Firings
				Iterator<Actor> actorIter = graph.getActors ();
				while (actorIter.hasNext ())
				{
					Actor actr = actorIter.next ();
					while (actorReadyToEnd (actr))
					{
						if (actr == outputActor)
						{
							repCnt++;
							if (transSol.getSolution (actr).returnNumber () == repCnt)
							{
								if (storeState (currentState) == false)
								{
									// System.out.println ("End of StateSpace Exploration");
									return computeThroughput (currentState.repeatedStateIndex);
								}
								currentState.glbClk = 0;
								repCnt = 0; 
							}							
						}
						endActorFiring (actr);
					}
				}
				
				// Start Actor Firings
				Iterator<Actor> actrIter = graph.getActors ();
				while (actrIter.hasNext ())
				{
					Actor actr = actrIter.next ();
					while (actorReadyToFire (actr))
					{
						startActorFiring (actr);
					}
				}
				
				// Clock Step
				clkStep = clockStep ();				
				
				// Deadlocked?
				if (clkStep == Integer.MAX_VALUE)
				{
					System.out.println ("System Deadlocked !!");
					return 0;
				}				
			}
		}

		private double computeThroughput (int index) 
		{
			int nr_fire = 0;
			int time = 0;
			
			// Check all state from stack till cycle complete			
			for (int i=index;i<storedStates.size ();i++)
			{
				State state = storedStates.get (i);
				
				// Number of states in cycle is equal to number of iterations 
				// in the period
				nr_fire++;
				
				// Time between previous state
				time += state.glbClk;				
				
			}			
			// System.out.println ("nr:"+nr_fire + " time:" + time);
			return (double)(nr_fire)/(time);
			
		}

		private int clockStep () 
		{
			int step = Integer.MAX_VALUE;
			
			// Find Maximal Time Progress
			Iterator<Actor> iterActr = currentState.actClk.keySet ().iterator ();
			while (iterActr.hasNext ())
			{
				Actor actr = iterActr.next ();
				int max = Collections.max (currentState.actClk.get (actr));
				if (step > max)
					step = max;
			}
			
			// Still actors ready to end their firing?
			if (step == 0)
				return 0;
			
			// Check for progress (i.e. no deadlock)
			if (step == Integer.MAX_VALUE)
				return Integer.MAX_VALUE;
			
			// Lower remaining execution time actors
			iterActr = currentState.actClk.keySet ().iterator ();
			while (iterActr.hasNext ())
			{
				Actor actr = iterActr.next ();
				List<Integer> actrExecTimeList = currentState.actClk.get (actr);
				for (int i=0;i<actrExecTimeList.size ();i++)									
					actrExecTimeList.set (i, (actrExecTimeList.get (i) - step));				
			}
			
			// Advance the global clock
			currentState.glbClk += step;
			
			return 0;
		}

		private void startActorFiring (Actor actr) 
		{
			// Consume tokens from inputs and space for output tokens
			for(Link lnk : actr.getLinks (Port.DIR.IN))   // get All Incoming Links
			{			
				int portRate = Integer.parseInt (lnk.getPort ().getRate ());
				int tokens = currentState.ch.get (lnk.getChannel ());
				tokens -= portRate;
				//currentState.ch.remove (lnk.getChannel ());				
				currentState.ch.put (lnk.getChannel (), tokens);
			}
			
			// Add actor firing to the list of active firings of this actor
			// First Create the list of firing of the actors if it does not exist.
			if (currentState.actClk.containsKey (actr) == false)
			{
				List<Integer> newList = new ArrayList<Integer>();
				currentState.actClk.put (actr, newList);
			}
			
			// Add execution time to list of current actor firings.
			currentState.actClk.get (actr).add (actr.getExecTime ());
			
		}

		private boolean actorReadyToFire (Actor actr) 
		{
			// Check for the input tokens on all the input links.
			for (Link lnk : actr.getLinks (Port.DIR.IN))  // get All Incoming Links
			{
				Channel chnnl = lnk.getChannel ();
				if (currentState.ch.get (chnnl) < Integer.parseInt (lnk.getPort ().getRate ()))
					return false;
			}
			return true;
		}

		private void endActorFiring (Actor actr) 
		{
			// Produce tokens on all the output channels of this actor.
			for (Link lnk : actr.getLinks (Port.DIR.OUT))  // get All Outgoing Links
			{			
				Channel chnnl = lnk.getChannel ();
				int portRate = Integer.parseInt (lnk.getPort ().getRate ()) + currentState.ch.get (chnnl);
				//currentState.ch.remove (chnnl);
				currentState.ch.put (chnnl, portRate);
			}
			
			// Remove the Actor from the active clocks.
			currentState.actClk.remove (actr);
			
		}

		private boolean storeState (State currState) 
		{
			State tempState = new State ();
			tempState.clone (currState);
			
			// We add it to hashed states as well as to the list.
			// the list preserves the order in which states are
			// added and hashed states prevents adding of duplicate states.
			if (storedStatesHash.add (currState) == false)
			{			
				// Check if the state is already stored in the list.			
				for (int i=0;i<storedStates.size ();i++)
				{
					State state = storedStates.get (i);
					if (state.equals (currState))
					{						
						currentState.repeatedStateIndex = i;
						return false;
					}
				}
			}
			else			
				storedStates.add (tempState);			
			
			return true;
		}

		private boolean actorReadyToEnd (Actor actr) 
		{
			if (currentState.actClk.containsKey (actr) == false)
				return false;
			
			if (currentState.actClk.get (actr).contains (0) == false)			
				return false;			
			
			return true;
		}		
	}
	
	
	/**
	 * Calculates throughput of the graph.
	 * 
	 * @param inputGraph input SDF graph
	 * @return throughput of the graph
	 */
	public double calculateThroughput (Graph inputGraph)
	{
		double thr = Double.POSITIVE_INFINITY; // a big number
		Kosaraju ipGraphConnctd = new Kosaraju (inputGraph);
		// Clear and Build the input Graph Expressions
		expressions.clear ();
		expressions.parse (inputGraph);			
		
		// Clear previous and Calculate the Repetition Vector
		globalSolutions.clear ();
		globalSolutions.solve (inputGraph, expressions);
		
		// Print the solutions
		// globalSolutions.dump ();
		
		// Check if Graph is strongly connected
		if (!(ipGraphConnctd.isStronglyConnected ()))
		{
			// TODO: This part is not tested right now.
			List<List<Actor>> stronglyConnectedComponentList = ipGraphConnctd.getStronglyConnectedComponents ();
			
			Iterator<List<Actor>> listIter = stronglyConnectedComponentList.iterator ();
			while (listIter.hasNext ())
			{
				List<Actor> actrList = listIter.next ();
				
				Graph graph = new Graph ();
				graph.clone (inputGraph);
				
				for (Actor removeActor : actrList)
				if (graph.getActor (removeActor.getName ()) != null)
					graph.remove (removeActor);								
				
				Throughput thruput = new Throughput ();
				double thrGc = thruput.calculateThroughput (graph);
				
				Solutions solutions = new Solutions ();		
				GraphExpressions tempexpressions = new GraphExpressions ();
				tempexpressions.clear ();
				tempexpressions.parse (graph);
				
				// Clear previous and Calculate the Repetition Vector
				solutions.clear ();
				solutions.solve (graph, tempexpressions);
				
				// Scale throughput wrt repetition vector component vs graph
				Actor tempActor = graph.getActors ().next ();
				thrGc = (thrGc * solutions.getSolution (tempActor).returnNumber ()) / (globalSolutions.getSolution (tempActor).returnNumber ());
				
				if (thrGc < thr)
					thr = thrGc;				
			}
			
			return thr;
		}
		// Create a Transition System
		TransitionSystem tranSys = new TransitionSystem (inputGraph);
		
		// Find the Maximal Throughput.
		thr = tranSys.execSDFgraph ();		
		
		return thr;		
	}
}