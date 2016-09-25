package tests;
import output.DotGraph;
import input.ParseApplicationGraph;
import graphanalysis.throughput.Throughput;

import spdfcore.Graph;

/**
 * Test to verify throughput calculation of an example graph.
 * 
 * @author Pranav Tendulkar
 *
 */
public class TryThroughput 
{
	
	/**
	 * Verify if the throughput matched the expected value.
	 * 
	 * @param throughput calculated throughput
	 */
	private static void validateTestOutput (double throughput)
	{
		final double expectedThroughput = ((double)1/12);
		
		if (throughput != expectedThroughput)
		{
			throw new RuntimeException (
					"The expected Throughput was : " + expectedThroughput +
					". Is there any change in input file?" );
		}
		else
		{
			System.out.println ("TryThroughput Passed the Test !");			
		}
	}
	
	/**
	 * Procedure to calculate the throughput of an application graph.
	 * 
	 * @param args None Required
	 */
	public static void main (String[] args) 
	{	
		Throughput thruPut = new Throughput ();
		DotGraph dotG = new DotGraph ();

		ParseApplicationGraph xmlParse = new ParseApplicationGraph ();
		Graph g = xmlParse.parseSingleGraphXml ("inputFiles/test_graphs/ThroughputTest.xml");
		
		
		//System.out.println ("Printing Graph:");
		dotG.generateDotFromGraph (g, "outputFiles/out.dot");

		// Calculate the Theoretical Maximum Throughput.
		double thr = thruPut.calculateThroughput (g);
		
		validateTestOutput (thr);
	}
}
