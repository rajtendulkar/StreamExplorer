package graphanalysis;
import graphanalysis.Kosaraju;
import graphanalysis.properties.GraphAnalysis;
import java.util.*;

import spdfcore.*;



/**
 * Find cycles in the graph.
 * 
 * this code is adapted from SDF3 - 
 * sdf3/sdf/base/algo/cycle.cc
 * 
 * @author Pranav Tendulkar
 *
 */
public class GraphFindCycles 
{
	/**
	 * Node color
	 */
	private enum Color {WHITE,GRAY};
	
	/**
	 * Graph analysis to quickly calculate some properties.
	 */
	private GraphAnalysis graphAnalysis;
	
	/**
	 * Color assigned to the actor. 
	 */
	private HashMap<Actor, Color> color;
	
	/**
	 * 
	 */
	private HashMap<Actor, Actor> pi;
	
	/**
	 * List of cycles in the graph. 
	 */
	private List<List<Actor>> cycles;
	
	/**
	 * 
	 */
	public GraphFindCycles ()
	{
		graphAnalysis = new GraphAnalysis ();
		color = new HashMap<Actor, Color>();
		pi = new HashMap<Actor, Actor>();
		cycles = new ArrayList<List<Actor>>();
	}
	
	/**
	 * Get adjacent actors to an actor
	 * @param a an actor
	 * @param fromNodes
	 * @return list of adjacent actors
	 */
	private  List<Actor> getAdjacentList (Actor a, List<Actor> fromNodes)
	{
		List<Actor> result = new ArrayList<Actor>();
		
		HashSet<Actor> outActors = graphAnalysis.getImmediatelyConnectedActors (a, Port.DIR.OUT);
		for (Actor outActor : outActors)
			if (fromNodes.contains (outActor))
				result.add (outActor);
		
		return result;
	}
	
	/**
	 * Find cycles in the graph.
	 * 
	 * @param inputGraph graph in which cycles are to be found.
	 * @return list of cycles in the graph. Each element is a list of actors present in a cycle.
	 */
	public List<List<Actor>> findCycles (Graph inputGraph)
	{		
		Kosaraju connectedComponents = new Kosaraju (inputGraph);
		// Strongly Connected components.
		List<List<Actor>> scc = connectedComponents.getStronglyConnectedComponents ();
		
		cycles.clear ();
		
		for (List<Actor> component : scc)
		{			
			for (Actor actr : component)
				color.put (actr, Color.WHITE);
			
			Actor a = component.get (0);			
			color.put (a, Color.GRAY);
			
			List<Actor> adjList = getAdjacentList (a, component);			
			for (Actor b : adjList)
			{
				if (color.get (b) == Color.WHITE)
				{
					pi.put (b, a);
					simpleCycleVisit (b, component);
				}
				else if (color.get (b) == Color.GRAY)
				{
					// Self-Loop
					List<Actor> cycle = new ArrayList<Actor>();
					cycle.add (a);
					cycles.add (cycle);
				}					
			}		
		}
		
		// Remove Possible duplicates.
		List<List<Actor>> result = new ArrayList<List<Actor>>();
		for (int i=0;i<cycles.size ();i++)
		{
			boolean add = true;
			for (int j=0;j<result.size ();j++)
			{
				if (result.get (j).size () == cycles.get (i).size ())
				{
					int k;
					for (k=0;k<result.get (j).size ();k++)
						if (cycles.get (i).contains (result.get (j).get (k)) == false)
							break;
					
					if (k == result.get (j).size ())
					{
						add = false;
						break;
					}
				}
			}
			
			if (add == true)
				result.add (cycles.get (i));
		}		
		return result;
	}

	/**
	 * @param a
	 * @param component
	 */
	private void simpleCycleVisit (Actor a, List<Actor> component) 
	{
		color.put (a, Color.GRAY);
		
		List<Actor> adjList = getAdjacentList (a, component);
			
		for (Actor b : adjList)
		{
			if (color.get (b) == Color.WHITE)
			{
				pi.put (b, a);
				simpleCycleVisit (b, component); 
			}
			else if (color.get (b) == Color.GRAY)
			{
				List<Actor> cycle = new ArrayList<Actor>();
				
				cycle.add (a);
				
				Actor tempActor = a;					
				while (tempActor.equals (b) == false)
				{
					tempActor = pi.get (tempActor);
					cycle.add (tempActor);
				}
				
				cycles.add (cycle);
			}				
		}		
		color.put (a, Color.WHITE);
	}
}

	
