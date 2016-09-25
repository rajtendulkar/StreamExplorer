package graphanalysis;

import graphanalysis.properties.GraphAnalysisSdfAndHsdf;

import java.util.*;

import spdfcore.*;
import spdfcore.stanalys.Solutions;

/**
 * Traverse a graph with breadth first search
 * 
 * @author Pranav Tendulkar
 */
public class BreadthFirstSearch 
{
	/**
	 * Graph to be traversed.
	 */
	private Graph graph;
	
	/**
	 * Solutions of the graph containing repetition count. 
	 */
	private Solutions solutions;
	
	/**
	 * Initialize a BFS object.
	 * 
	 * @param inputGraph input graph to be analysed
	 * @param solutions solutions of the graph
	 */
	public BreadthFirstSearch (Graph inputGraph, Solutions solutions)
	{
		this.solutions = solutions;
		graph = inputGraph;
	}
	
	/**
	 * Gets a list of actors which if we sequentially access then we are 
	 * essentially accessing them in breadth first fashion.
	 * 
	 * @return List of actors in breadth-first-search order
	 */
	public List<Actor> getBfsActorList ()
	{
		List<Actor> result = new LinkedList<Actor>();
		
		GraphAnalysisSdfAndHsdf graphAnalysis = new GraphAnalysisSdfAndHsdf (graph, solutions);
		
		List<Actor> startActors = graphAnalysis.findSdfStartActors ();
		for (Actor actr : startActors)		
			result.add (actr);
		
		List<Actor> tempList = new ArrayList<Actor>();
		while (startActors.size () != 0)
		{
			tempList.clear ();
			for (Actor actr : startActors)
			{
				HashSet<Actor> outgoingActors = graphAnalysis.getImmediatelyConnectedActors (actr, Port.DIR.OUT);
				for (Actor outgoingActr : outgoingActors)
				{
					// only if all incoming actors are scheduled this should be added to the list.
					HashSet<Actor>incoming = graphAnalysis.getImmediatelyConnectedActors (outgoingActr, Port.DIR.IN);
					incoming.removeAll (result);
					if (incoming.size () == 0 && (startActors.contains (outgoingActr) == false) && (tempList.contains (outgoingActr) == false))
						tempList.add (outgoingActr);
					
				}					
			}
			
			result.addAll (tempList);
			startActors.clear ();
			startActors.addAll (tempList);
		}
		
		System.out.println ("Result : " + result.toString ());
		return result;
	}
}
