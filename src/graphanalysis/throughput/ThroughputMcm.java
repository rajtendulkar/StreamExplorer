package graphanalysis.throughput;

import java.util.*;

import graphanalysis.*;
import graphanalysis.properties.*;
import spdfcore.*;
import spdfcore.stanalys.*;

/**
 * Calculate maximum throughput of the graph.
 * 
 * @author Pranav Tendulkar
 *
 */
public class ThroughputMcm 
{
	/**
	 * HSDF graph
	 */
	private Graph hsdfGraph;
	
	/**
	 * Calculate strongly connected components in the graph
	 */
	private Kosaraju connectedComponents;
	
	/**
	 * Get some properties of the graph
	 */
	private GraphAnalysis graphAnalysis;
	
	/**
	 * Initialize throughput calculator object.
	 * 
	 * @param hsdfGraph input HSDF graph
	 */
	public ThroughputMcm (Graph hsdfGraph)
	{
		this.hsdfGraph = hsdfGraph;
		connectedComponents = new Kosaraju (hsdfGraph);
		graphAnalysis = new GraphAnalysis ();
	}
	
	/**
	 * Calculates the throughput using MCM.
	 * Refer to Bhattacharya book for the formula.
	 * 
	 * @return maximum throughput of the graph
	 */
	public double calculateThroughput ()
	{
		double throughPut = 0.0;	
		
		if (connectedComponents.isStronglyConnected () == true)
		{
			GraphExpressions expressions = new GraphExpressions ();
			expressions.parse (hsdfGraph);
			Solutions solutions = new Solutions ();
			solutions.setThrowExceptionFlag (false);	    		
			solutions.solve (hsdfGraph, expressions);
			GraphFindCycles findCycles = new GraphFindCycles ();
			
			List<List<Actor>>cycles = findCycles.findCycles (hsdfGraph); 
			
			double mcm = Double.NEGATIVE_INFINITY;
			
			for (List<Actor> cycle : cycles)
			{
				double cycleMcm = 0;
				double totalExecTime = 0;
				int totalDelay = 0;
				for (Actor actr : cycle)
					totalExecTime += actr.getExecTime ();
				
				for (int i=1;i<cycle.size ();i++)
				{
					Channel chnnl = graphAnalysis.getChannelConnectingActors (cycle.get (i), cycle.get (i-1), Port.DIR.OUT);
					totalDelay += chnnl.getInitialTokens ();
				}
				
				Channel chnnl = graphAnalysis.getChannelConnectingActors (cycle.get (0), cycle.get (cycle.size ()-1), Port.DIR.OUT);
				totalDelay += chnnl.getInitialTokens ();
				
				cycleMcm = totalExecTime / totalDelay;
				
				// Find the max.
				mcm = ((cycleMcm > mcm) ? cycleMcm : mcm);
				// System.out.println ("Cycle " + cycle.toString () + " MCM : " + cycleMcm + " time : " + totalExecTime + " delay " + totalDelay);
			}
			throughPut = 1 / mcm;
		}
		else
		{	
			throughPut = Double.POSITIVE_INFINITY;
			System.out.println ("The Graph is not strongly connected. The throughput is infinity\n");			
		}		
		
		return throughPut;
	}
}
