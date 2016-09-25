package graphanalysis;

import graphanalysis.properties.GraphAnalysis;

import java.util.*;

import spdfcore.*;

/**
 * Traverse a graph with depth first search
 * 
 * @author Pranav Tendulkar
 *
 */
public class DepthFirstSearch 
{
	/**
	 * Properties of graph.
	 */
	private GraphAnalysis graphAnalysis;
	
	/**
	 * Initialize depth first search object.
	 */
	public DepthFirstSearch ()
	{	
		graphAnalysis = new GraphAnalysis ();
	}
	
	/**
	 * Clone a path
	 * 
	 * @param path input path
	 * @return clone path same as input path
	 */
	private List<Actor> clonePath (List<Actor> path)
	{
		List<Actor> newPath = new ArrayList<Actor>();
		newPath.addAll (path);
		return newPath;
	}
	
	/**
	 * DFS visit a node.
	 * 
	 * @param actr current actor
	 * @param dstActor last actor to be visited
	 * @param currentPath current path which is being traversed
	 * @param paths list of paths
	 */
	public void dfsVisit (Actor actr, Actor dstActor, List<Actor> currentPath, List<List<Actor>> paths)
	{
		HashSet<Actor> adjActorList = getAdjacentActors (actr, false);
		if (actr == dstActor)
		{
			paths.add (clonePath (currentPath));
			return;
		}

		for (Actor ac : adjActorList)
		{
			// Check if we getting in a loop.
			if (currentPath.contains (ac) == false)
			{
				currentPath.add (ac);
				dfsVisit (ac, dstActor, currentPath, paths);
				currentPath.remove (currentPath.size ()-1);
			}
		}
	}
	
	/**
	 * Get depth first search path.
	 * 
	 * @param srcActr starting actor of the path
	 * @param dstActr ending actor of the path
	 * @return Multiple paths from start actor to end actor.
	 */
	public List<List<Actor>> getDfsPaths (Actor srcActr, Actor dstActr)
	{		
		List<List<Actor>> paths = new ArrayList<List<Actor>>();
		List<Actor> tempList = new ArrayList<Actor>();
		tempList.add (srcActr);
		dfsVisit (srcActr, dstActr, tempList, paths);
		
		return paths;
	}

	/**
	 * The function returns a list with actors directly reachable from
	 * actor a (in case transpose if false). If transpose is 'true', the
	 * graph is transposed and the function returns a list with actors
	 * which are directly reachable from a in the transposed graph.
	 * 
	 * @param actr actor of which adjacent actors are to be calculated
	 * @param transpose direction to search
	 * @return Set of actors directly reachable from an actor
	 */
	private HashSet<Actor> getAdjacentActors (Actor actr, boolean transpose) 
	{
		GraphAnalysis graphAnalysis = new GraphAnalysis ();
		HashSet<Actor> actrList;
		
		if (!transpose)
		{
			// In this case we have to get all the outgoing links
			actrList = graphAnalysis.getImmediatelyConnectedActors (actr, Port.DIR.OUT);
		}
		else
		{
			actrList = graphAnalysis.getImmediatelyConnectedActors (actr, Port.DIR.IN); // get All Incoming Links
		}
		
		// Remove the actor if it contains self-edges.
		while (actrList.contains (actr) == true)
			actrList.remove (actr);
						
		return actrList;
	}
	
	/**
     * Given a graph, returns a queue containing the nodes of that graph in 
     * the order in which a DFS of that graph finishes expanding the nodes.
     *
     * @param g The graph to explore.
     * @param transpose direction to search
     * @return A stack of nodes in the order in which the DFS finished
     *         exploring them.
     */
    public Stack<Actor> dfsVisitOrder (Graph g, boolean transpose) 
    {
        /* The resulting ordering of the nodes. */
        Stack<Actor> result = new Stack<Actor>();

        /* The set of nodes that we've visited so far. */
        Set<Actor> visited = new HashSet<Actor>();

        /* Fire off a DFS from each node. */
        Iterator<Actor> actrIter = g.getActors ();
        while (actrIter.hasNext ())
        {
        	Actor node = actrIter.next ();
        	recExplore (node, g, result, visited, transpose);
        }            

        return result;
    }
    
    /**
     * Recursively explores the given node with a DFS, adding it to the output
     * list once the exploration is complete.
     *
     * @param node The node to start from.
     * @param g The graph to explore.
     * @param result The final listing of the node ordering.
     * @param visited The set of nodes that have been visited so far.
     * @param transpose direction is reversed if transpose is set
     */
    private void recExplore (Actor node, Graph g, Stack<Actor> result, Set<Actor> visited, boolean transpose) 
    {
        /* If we've already been at this node, don't explore it again. */
        if (visited.contains (node)) 
        	return;

        /* Otherwise, mark that we've been here. */
        visited.add (node);

        HashSet<Actor> connectedActors;
        /* Recursively explore all the node's children. */
        if (transpose == true)
        	connectedActors = graphAnalysis.getImmediatelyConnectedActors (node, Port.DIR.IN);
        else
        	connectedActors = graphAnalysis.getImmediatelyConnectedActors (node, Port.DIR.OUT);
        
        for (Actor connectd : connectedActors)
        	recExplore (connectd, g, result, visited, transpose);

        /* We're done exploring this node, so add it to the list of visited
         * nodes.
         */
        result.push (node);
    }
}
