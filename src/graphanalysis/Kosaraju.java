package graphanalysis;

import graphanalysis.properties.GraphAnalysis;

import java.util.*;
import spdfcore.*;



/**
 * An implementation of Kosaraju's algorithm for finding strongly-connected
 * components (SCCs) of a graph in linear time.
 *
 * This algorithm is adapted from - 
 * http://www.keithschwarz.com/interesting/code/?dir=kosaraju
 * 
 * @author Pranav Tendulkar
 *
 */
public class Kosaraju 
{
	/**
	 * Input Graph under analysis
	 */
	private Graph inputGraph;
	
	/**
	 * Graph properties for quick reference.
	 */
	private GraphAnalysis graphAnalysis;
	
	/**
	 * List of strongly connected components
	 */
	private List<List<Actor>> stronglyConnectedComponents = null;
	
	/**
	 * Initialize Kosaraju algorithm instance to find strongly connected
	 * components.
	 * 
	 * @param inputGraph input graph
	 */
	public Kosaraju (Graph inputGraph)
	{
		this.inputGraph = inputGraph;
		graphAnalysis = new GraphAnalysis ();
	}
	
	/**
	 * Get list of strongly connected components
	 * 
	 * @return list of strongly connected components.
	 */
	public List<List<Actor>> getStronglyConnectedComponents ()
	{
		if (stronglyConnectedComponents == null)
		{
			calculateStronglyConnectedComponents ();
		}
		
		return stronglyConnectedComponents;
	}
	
	/**
	 * Check if there are any strongly connected components in the graph.
	 * 
	 * @return true if strongly connected components are presents, false otherwise
	 */
	public boolean isStronglyConnected ()
	{
		if (stronglyConnectedComponents == null)
			calculateStronglyConnectedComponents ();
		
		boolean result = false;
		for (int i=0;i<stronglyConnectedComponents.size ();i++)
			if (stronglyConnectedComponents.get (i).size () > 1)
			{
				result = true;
				break;
			}
		
		return result;
	}
	
	/**
	 * Find strongly connected components.
	 */
	private void calculateStronglyConnectedComponents ()
	{
		DepthFirstSearch dfsExploration = new DepthFirstSearch ();
		stronglyConnectedComponents = new ArrayList<List<Actor>>();
		/* Run a depth-first search in the reverse graph to get the order in
         * which the nodes should be processed.
         */
        Stack<Actor> visitOrder = dfsExploration.dfsVisitOrder (inputGraph, true);
        
        /* Now we can start listing connected components.  To do this, we'll
         * create the result map, as well as a counter keeping track of which
         * DFS iteration this is.
         */
        Map<Actor, Integer> result = new HashMap<Actor, Integer>();
        int iteration = 0;
        
        /* Continuously process the the nodes from the queue by running a DFS
         * from each unmarked node we encounter.
         */
        while (!visitOrder.isEmpty ()) 
        {
        	/* Grab the last node.  If we've already labeled it, skip it and
             * move on.
             */
        	Actor startPoint = visitOrder.pop ();
        	
        	if (result.containsKey (startPoint))
                continue;
        	
        	/* Run a DFS from this node, recording everything we visit as being
             * at the current level.
             */
            markReachableNodes (startPoint, inputGraph, result, iteration);
            
            /* Bump up the number of the next SCC to label. */
            ++iteration;
        }
        
        iteration--;
        while ((iteration >= 0) && (result.isEmpty ()==false))
        {
        	List<Actor> actrList = new ArrayList<Actor>();
        	for (Map.Entry<Actor, Integer> entry : result.entrySet ()) 
        	{
        		if (entry.getValue ().equals (iteration)) 
        		{
        			actrList.add (entry.getKey ());
        			result.remove (entry);
        		}
        	}
        	stronglyConnectedComponents.add (actrList);
        	iteration--;
        }
	}
	
    /**
     * Recursively marks all nodes reachable from the given node by a DFS with
     * the current label.
     *
     * @param node The starting point of the search.
     * @param g The graph in which to run the search.
     * @param result A map in which to associate nodes with labels.
     * @param label The label that we should assign each node in this SCC.
     */
    private void markReachableNodes (Actor node, Graph g, Map<Actor, Integer> result, int label) 
    {
        /* If we've visited this node before, stop the search. */
        if (result.containsKey (node)) 
        	return;

        /* Otherwise label the node with the current label, since it's
         * trivially reachable from itself.
         */
        result.put (node, label);

        /* Explore all nodes reachable from here. */
        for (Actor endpoint: graphAnalysis.getImmediatelyConnectedActors (node, Port.DIR.OUT))
            markReachableNodes (endpoint, g, result, label);
    }	
}
