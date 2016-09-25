package tests;
import input.*;

import java.io.File;
import java.util.Iterator;

import output.GenerateSdfXml;

import spdfcore.*;
import spdfcore.Channel.Link;

/**
 * Validate the generated XML file from a graph.
 * 
 * @author Pranav Tendulkar
 *
 */
public class TryGenerateSdfXml
{
	
	/**
	 * This test parses an XML file and converts it into graph structure.
	 * It will call then a procedure to generate XML file from the graph structure.
	 * Will again parse the new generate XML file to see if same graph structure is generated.
	 * 
	 * @param args None Required
	 */
	public static void main (String[] args) 
	{		
		ParseApplicationGraph parse = new ParseApplicationGraph();	
		Graph graph = parse.parseSingleGraphXml ("inputFiles/test_graphs/hsdfTest.xml");
		
		GenerateSdfXml outXml = new GenerateSdfXml();
		outXml.generateOutput (graph, "outputFiles/testSdf.xml");
		
		Graph generatedGraph = parse.parseSingleGraphXml ("outputFiles/testSdf.xml");
		
		// First check the number of actors.
		if (generatedGraph.countActors () != graph.countActors ())
		{
			throw new RuntimeException (
					"The number of actors in generated and Expected Graph is different !");
		}

		// Now check each actor in detail.
		Iterator<Actor> actorIter = generatedGraph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor generatedGraphActr = actorIter.next ();			
			Actor expectedGraphActr = graph.getActor (generatedGraphActr.getName ());

			if (generatedGraphActr.getFunc ().equals (expectedGraphActr.getFunc ()) == false)
			{
				throw new RuntimeException (
						"Actor Functions don't match ! : " + generatedGraphActr.getFunc () + " : " + expectedGraphActr.getFunc ());
			}

			if (expectedGraphActr.getExecTime () != expectedGraphActr.getExecTime ())
			{
				throw new RuntimeException (
						"Actor Execution Time don't match ! : " + generatedGraphActr.getExecTime () + 
						" : " + expectedGraphActr.getExecTime ());
			}

			// Check only outgoing to avoid redundant checking of links.
			for (Link lnkExpected : expectedGraphActr.getLinks (Port.DIR.OUT)) // get All Outgoing Links
			{			
				boolean found = false;
				
				for (Link lnkGenerated : generatedGraphActr.getLinks (Port.DIR.OUT)) // Get All Outgoing Links	
				{
					if ((lnkExpected.getActor ().getName ().equals (lnkGenerated.getActor ().getName ())) && 
							(lnkExpected.getOpposite ().getActor ().getName ().equals (lnkGenerated.getOpposite ().getActor ().getName ())))
					{

						// Found a Match !
						found = true;

						if (lnkExpected.getChannel ().getInitialTokens () != lnkGenerated.getChannel ().getInitialTokens ())
						{
							throw new RuntimeException ("MisMatch in Initial Tokens !!");
						}						
						break;
					}					
				}

				// Check here if we found a link or not  ?
				if (found == false)
				{
					throw new RuntimeException ("Link is Absent !!" +
							"From : " + lnkExpected.getActor ().getName () + " To : " 
							+ lnkExpected.getOpposite ().getActor ().getName ());
				}

			}			
		}
		
		File file = new File("outputFiles/testSdf.xml");
		file.delete(); 
		
		System.out.println ("SDF XML Generation Passed the Test !");
		
	}
}
