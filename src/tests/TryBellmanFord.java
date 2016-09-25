package tests;
import graphanalysis.BellmanFord;

import input.ParseApplicationGraph;

import java.util.*;

import output.DotGraph;
import spdfcore.*;


/**
 * This class tests the Bellman Ford Algorithm to find
 * the shortest or longest path.
 * The example we test in this class is 
 * <a href="http://compprog.wordpress.com/2007/11/29/one-source-shortest-path-the-bellman-ford-algorithm/">given here</a>.
 * 
 * @author Pranav Tendulkar
 *
 */
public class TryBellmanFord
{
	// Example taken from here.
	// http://compprog.wordpress.com/2007/11/29/one-source-shortest-path-the-bellman-ford-algorithm/
	// and removed some edges in order to remove cycles for shortest and longest path.	
	
	public static void main (String[] args) 
	{		
		HashMap<Channel, String> edgQty = new HashMap<Channel, String>();
		
		// Source actor, destination actor, edge weight
		String[][] edgeWeights = {	        		
	        		{ "1", "2", "6"},
	        		{ "1", "4", "7"},
	        		//{ "2", "3", "5"},
	        		{ "2", "4", "8"},
	        		{ "2", "5", "-4"},
	        		{ "3", "2", "-2"},
	        		//{ "4", "3", "-3"},
	        		{ "4", "5", "9"},
	        		//{ "5", "1", "2"},
	        		//{ "5", "3", "7"},
	        		{ "6", "3", "-9"},
	        };

		ParseApplicationGraph xmlParse = new ParseApplicationGraph ();
		Graph g = xmlParse.parseSingleGraphXml ("inputFiles/test_graphs/BellmanFordTest.xml");
				
		DotGraph dotG = new DotGraph ();
		
		//System.out.println ("Printing Graph:");
		dotG.generateDotFromGraph (g, "outputFiles/bellman.dot");
		
		Iterator<Channel> chnnlIter = g.getChannels ();
		while (chnnlIter.hasNext ())
		{					
			Channel chnnl = chnnlIter.next ();					
			
			// search in edge Weights 
			for (int i=0;i<edgeWeights.length; i++)
			{
				if ((edgeWeights[i][0].equals (chnnl.getLink (Port.DIR.OUT).getActor ().getName ()) && 
						(edgeWeights[i][1].equals (chnnl.getLink (Port.DIR.IN).getActor ().getName ()))))
				{
					// Found a Match
					//System.out.println ("Edge from :"+edgeWeights[i][0]+" to : "+ edgeWeights[i][1] + " Weight : "+ edgeWeights[i][2]);
					edgQty.put (chnnl, edgeWeights[i][2]);
					break;
				}
			}					
		}				
		// g.dump ();
		
		// ***********************************************
		// Test Bellman Ford Algorithm for Longest Path.
		// ***********************************************
		BellmanFord bmFrd = new BellmanFord (g, edgQty);
		
		Actor srcActor = g.getActor ("1");
		Actor dstActor = g.getActor ("5");
		
		Stack<String> expectedLongestPath = new Stack<String>();
		// Pushing in reverse order.
		expectedLongestPath.push ("5");
		expectedLongestPath.push ("4");
		expectedLongestPath.push ("2");
		expectedLongestPath.push ("1");				
		
		Stack<Actor> longestPath = bmFrd.searchPath (srcActor, dstActor, true);
		if (longestPath == null)
		{
			throw new RuntimeException (
					"Longest Path from Actor "+ srcActor.getName () + " to Actor " 
					+ dstActor.getName () + " not found !" );
		}				
		else
		{				
			//System.out.println ("Path from " + srcActor.getName () + " to " + dstActor.getName ());
			while (longestPath.size () != 0)
			{
				Actor actor = longestPath.pop ();
				
				String nextPathElem = new String ("");
				if (expectedLongestPath.size () != 0)
					nextPathElem = expectedLongestPath.pop ();
				
				if (nextPathElem.equals (actor.getName ()) == false)
				{
					throw new RuntimeException (
							"Longest Path : Next Path Element : " + nextPathElem + " does not match the expected : "  
							+ actor.getName ());
				}						
				//System.out.print (actor.getName () + " --> ");
			}
			//System.out.println ("done.");
		}
		
		
		// ***********************************************
		// Test Bellman Ford Algorithm for Shortest Path.
		// ***********************************************				
		Stack<String> expectedShortestPath = new Stack<String>();
		// Pushing in reverse order.
		expectedShortestPath.push ("5");				
		expectedShortestPath.push ("2");
		expectedShortestPath.push ("1");
		
		
		bmFrd = new BellmanFord (g, edgQty);
		Stack<Actor> shortestPath = bmFrd.searchPath (srcActor, dstActor, false);
		if (shortestPath == null)
		{
			throw new RuntimeException (
					"Shortest Path from Actor "+ srcActor.getName () + " to Actor " 
					+ dstActor.getName () + " not found !" );
		}				
		else
		{				
			//System.out.println ("Path from " + srcActor.getName () + " to " + dstActor.getName ());
			while (shortestPath.size () != 0)
			{
				Actor actor = shortestPath.pop ();
				
				String nextPathElem = new String ("");
				if (expectedShortestPath.size () != 0)
					nextPathElem = expectedShortestPath.pop ();
				
				if (nextPathElem.equals (actor.getName ()) == false)
				{
					throw new RuntimeException (
							"Shortest Path : Next Path Element : " + nextPathElem + 
							" does not match the expected : " + actor.getName ());
				}						
				//System.out.print (actor.getName () + " --> ");
			}
			//System.out.println ("done.");
		}
		
		System.out.println ("TryBellmanFord Passed the Test !");
	}
}
