package graphanalysis;
import java.util.*;
import spdfcore.*;
import spdfcore.stanalys.Expression;


/**
 * Bellman-Ford algorithm that computes shortest or longest paths from a 
 * single source vertex to other vertices in a weighted graph.
 * 
 * Algorithm taken from wikipedia.
 * @see <a href="http://en.wikipedia.org/wiki/Bellman%E2%80%93Ford_algorithm">Wikipedia</a>
 * 
 * @author Pranav Tendulkar
 */
public class BellmanFord
{
	/**
	 * Graph under analysis
	 */
	private Graph graph;
	
	/**
	 * Distances of different actors from the source
	 */
	private HashMap<Actor, String> distances = new HashMap<Actor, String>() ;
	
	/**
	 * Predecessor of an actor.
	 */
	private HashMap<Actor, Actor> predecessors = new HashMap<Actor, Actor>() ;
	
	/**
	 * Weight for each channel
	 */	
	private HashMap<Channel, String> edgeQuantities = new HashMap<Channel, String>();
	
	/**
	 * Maximum distance in the graph. 
	 */
	private final int MAX_DISTANCE = Integer.MAX_VALUE;

	/**
	 * Initialize Bellman-Ford algorithm object.
	 * 
	 * @param inputGraph Input Graph on which distances are to be calculated
	 * @param edgQty Weight for each channel in the graph
	 */
	@SuppressWarnings ("unchecked")
	public BellmanFord (Graph inputGraph, HashMap<Channel, String> edgQty)
	{
		distances.clear ();
		predecessors.clear ();
		graph = inputGraph;
		edgeQuantities = (HashMap<Channel, String>) edgQty.clone ();
	}
	
	/**
	 * Initialize the edge quantities and distances between actors.
	 * 
	 * @param srcActor Starting actor
	 * @param longestPath longest(=true) / shortest(=false) path to calculate
	 */
	private void initializeGraph (Actor srcActor, boolean longestPath)
	{
		int value;
		
		// Negate the Channel Quantities to find the longest
		// path instead of shortest.
		if (longestPath == true)
		{
			HashMap<Channel, String> tempEdgeQuantities = new HashMap<Channel, String>();
			
			Iterator<Channel> chnnlIter = edgeQuantities.keySet ().iterator ();
			while (chnnlIter.hasNext ())
			{
				Channel chnnl = chnnlIter.next ();				
				String edgeQty = edgeQuantities.get (chnnl);
				//System.out.print ("Edge: "+chnnl.getLink (Port.DIR.OUT).getActor ().getName ()
				//		+ " to " + chnnl.getLink (Port.DIR.IN).getActor ().getName () +" Edge: " + edgeQty);
				
				edgeQty = Integer.toString (Integer.parseInt (edgeQty) * -1);
				//System.out.println (" Changed to : "+edgeQty);				
				tempEdgeQuantities.put (chnnl, edgeQty);				
			}
			edgeQuantities = tempEdgeQuantities;
		}
			
		
		// Clear the initial distances.
		distances.clear ();
		Iterator<Actor> iterActor = graph.getActors ();
		while (iterActor.hasNext ())
		{
			Actor actr = iterActor.next ();
			if (actr == srcActor)
				value = 0;
			else
				value = MAX_DISTANCE;
			
			distances.put (actr, Integer.toString (value));			
		}
		
		// Clear all the predecessors.
		predecessors.clear ();
		predecessors.put (srcActor, null);
	}
	
	/**
	 * Relax all the edges
	 */
	private void relaxEdges ()
	{
		int nrActors = graph.countActors ();
		
		for (int i=0;i<(nrActors-1);i++)
		{
			Iterator<Channel> chnnlIter = graph.getChannels ();
			while (chnnlIter.hasNext ())
			{
				Channel chnnl = chnnlIter.next ();
				Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
				Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
				String weight = edgeQuantities.get (chnnl);
				
				if (Integer.parseInt (distances.get (srcActor)) == MAX_DISTANCE)
					continue;
				
				Expression addition = Expression.add (new Expression (weight), 
											new Expression (distances.get (srcActor)));				
				
				// if (addition.returnNumber () < distances.get (dstActor))
				if (addition.returnNumber () < Integer.parseInt (distances.get (dstActor)))
				{
					distances.remove (dstActor);
					distances.put (dstActor, Integer.toString (
							Integer.parseInt (distances.get (srcActor)) + Integer.parseInt (weight)));
					
					if (predecessors.containsKey (dstActor))
						predecessors.remove (dstActor);
					
					predecessors.put (dstActor, srcActor);
				}					
			}			
		}		
	}
	
	/**
	 * Check for Negative weight cycles. If the graph has it, then
	 * this algorithm will not work.
	 */
	private void checkForNegativeWeightCycles ()
	{
		Iterator<Channel> chnnlIter = graph.getChannels ();
		while (chnnlIter.hasNext ())
		{
			Channel chnnl = chnnlIter.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
			String weight = edgeQuantities.get (chnnl);
			
			Expression addition = Expression.add (new Expression (weight), 
					new Expression (distances.get (srcActor)));				

			// if (addition.returnNumber () < distances.get (dstActor))
			if ((Integer.parseInt (distances.get (srcActor)) != MAX_DISTANCE) && 
					(addition.returnNumber () < Integer.parseInt (distances.get (dstActor))))
			{
				System.out.print ("Src: "+ chnnl.getLink (Port.DIR.OUT).getActor ().getName ());
				System.out.print (" Dst: "+ chnnl.getLink (Port.DIR.IN).getActor ().getName ());
				System.out.print (" addition: "+ addition.returnNumber ());
				System.out.println (" distance: "+distances.get (dstActor));				
				throw new RuntimeException ("Graph contains a negative-weight cycle !");
			}
		}		
	}
	
	/**
	 * Print final solutions.
	 * 
	 * @param srcActr starting actor.
	 */
	public void printSolutions (Actor srcActr)
	{		
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			System.out.println ("From Source : "+ srcActr.getName () + " to Dest : " + actr.getName () + " Path : "
					+ distances.get (actr));
			
		}
		
		actrIter = predecessors.keySet ().iterator ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			Actor pred = predecessors.get (actr);
			if (pred != null)
				System.out.println ("Actor : "+actr.getName () + " Predecessor : " + pred.getName ());
			else
				System.out.println ("Actor : "+actr.getName () + " Predecessor : null");
		}		
	}
	
	/**
	 * Check if path exists between two actors
	 * 
	 * @param srcActr starting actor of the path
	 * @param dstActr ending actor of the path
	 * @return true if path exists, false otherwise
	 */
	private boolean hasPath (Actor srcActr, Actor dstActr)
	{
		String dstDistance = distances.get (dstActr);
		if (Integer.parseInt (dstDistance) == MAX_DISTANCE)
			return false;		
		return true;		
	}
	
	/**
	 * Get the path between two actors.
	 * 
	 * @param srcActr starting actor of the path
	 * @param dstActr ending actor of the path
	 * @return stack of actors, where each actor represents a hop
	 */
	private Stack<Actor> getPath (Actor srcActr, Actor dstActr)
	{
		Stack<Actor> path = new Stack<Actor>();
		path.add (dstActr);
		Actor predecessor = predecessors.get (dstActr);		
		while (predecessor != null)
		{
			path.add (predecessor);
			predecessor = predecessors.get (predecessor);
		}
		
		return path;
	}	

	/**
	 * Search for a shortest or longest path between two actors.
	 * 
	 * @param srcActr Starting actor of the path
	 * @param dstActr Ending actor of the path
	 * @param longestPath true to calculate longest path, false to calculate shortest
	 * @return A stack of actors which represents hops for the path
	 */
	public Stack<Actor> searchPath (Actor srcActr, Actor dstActr, boolean longestPath)
	{		
		//System.out.println ("Search for Path");
		// Step 1: initialize graph
		initializeGraph (srcActr, longestPath);
		
		// Step 2: relax edges repeatedly
		relaxEdges ();		
		
		// Step 3: check for negative-weight cycles.
		checkForNegativeWeightCycles ();
		
		//System.out.println ("Printing Solutions");
		//printSolutions (srcActr);
		
		// Step 4: Get the Shortest / Longest Path.
		if (hasPath (srcActr, dstActr))
		{
			return getPath (srcActr, dstActr);			
		}
		else
			return null;
	}
}
