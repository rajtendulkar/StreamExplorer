package tests;
import spdfcore.*;
import spdfcore.Channel.Link;
import input.*;

import java.util.*;

/**
 * Test to see if XML parser can correctly build an application graph
 * 
 * @author Pranav Tendulkar
 */
public class TryApplicationXML 
{

	/**
	 * This function builds the SDF graph that is being represented in the XML file.
	 * 
	 * @return application graph that is to be expected from the test XML file.
	 */
	private static Graph buildExpectedGraph ()
	{
		Graph expectedGraph = new Graph ();

		// VLD Actor
		Actor actor = new Actor ();
		actor.setName ("vld");
		actor.setFunc ("vld");
		actor.setExecTime (13009);
		expectedGraph.add (actor);

		// IQ Actor
		actor = new Actor ();
		actor.setName ("iq");
		actor.setFunc ("iq");
		actor.setExecTime (559);
		expectedGraph.add (actor);

		// IDCT Actor
		actor = new Actor ();
		actor.setName ("idct");
		actor.setFunc ("idct");
		actor.setExecTime (486);
		expectedGraph.add (actor);

		// MC Actor
		actor = new Actor ();
		actor.setName ("mc");
		actor.setFunc ("mc");
		actor.setExecTime (10958);
		expectedGraph.add (actor);

		// Finished all the actors, now all the channels.

		// vld2iq

		Port pout  = new Port (Port.DIR.OUT);
		pout.setFunc ("vld");
		pout.setName ("p0");        
		pout.setRate ("1");
		expectedGraph.add (pout);

		Port pin  = new Port (Port.DIR.IN);
		pin.setFunc ("iq");
		pin.setName ("p0");        
		pin.setRate ("4");
		expectedGraph.add (pin);        

		PortRef src = new PortRef ();
		src.setActorName ("vld");
		src.setPort (pout);

		PortRef snk = new PortRef ();
		snk.setActorName ("iq");
		snk.setPort (pin);

		Channel chnnl = new Channel ();
		expectedGraph.add (chnnl);
		chnnl.bind (src, snk);

		// iq2idct

		pout  = new Port (Port.DIR.OUT);
		pout.setFunc ("iq");
		pout.setName ("p1");        
		pout.setRate ("5");
		expectedGraph.add (pout);

		pin  = new Port (Port.DIR.IN);
		pin.setFunc ("idct");
		pin.setName ("p0");        
		pin.setRate ("8");
		expectedGraph.add (pin);

		src = new PortRef ();
		src.setActorName ("iq");
		src.setPort (pout);

		snk = new PortRef ();
		snk.setActorName ("idct");
		snk.setPort (pin);

		chnnl = new Channel ();
		expectedGraph.add (chnnl);
		chnnl.bind (src, snk);

		// idct2mc

		pout  = new Port (Port.DIR.OUT);
		pout.setFunc ("idct");
		pout.setName ("p1");        
		pout.setRate ("9");
		expectedGraph.add (pout);

		pin  = new Port (Port.DIR.IN);
		pin.setFunc ("mc");
		pin.setName ("p0");        
		pin.setRate ("10");
		expectedGraph.add (pin);

		src = new PortRef ();
		src.setActorName ("idct");
		src.setPort (pout);

		snk = new PortRef ();
		snk.setActorName ("mc");
		snk.setPort (pin);

		chnnl = new Channel ();
		expectedGraph.add (chnnl);
		chnnl.bind (src, snk);

		// vld2vld

		pin  = new Port (Port.DIR.IN);
		pin.setFunc ("vld");
		pin.setName ("p1");        
		pin.setRate ("2");
		expectedGraph.add (pin);

		pout  = new Port (Port.DIR.OUT);
		pout.setFunc ("vld");
		pout.setName ("p2");        
		pout.setRate ("3");
		expectedGraph.add (pout);

		src = new PortRef ();
		src.setActorName ("vld");
		src.setPort (pout);

		snk = new PortRef ();
		snk.setActorName ("vld");
		snk.setPort (pin);

		chnnl = new Channel ();
		chnnl.setInitialTokens (1);
		expectedGraph.add (chnnl);
		chnnl.bind (src, snk);   

		// iq2iq

		pin  = new Port (Port.DIR.IN);
		pin.setFunc ("iq");
		pin.setName ("p2");        
		pin.setRate ("6");
		expectedGraph.add (pin);

		pout  = new Port (Port.DIR.OUT);
		pout.setFunc ("iq");
		pout.setName ("p3");        
		pout.setRate ("7");
		expectedGraph.add (pout);

		src = new PortRef ();
		src.setActorName ("iq");
		src.setPort (pout);

		snk = new PortRef ();
		snk.setActorName ("iq");
		snk.setPort (pin);

		chnnl = new Channel ();
		chnnl.setInitialTokens (1);
		expectedGraph.add (chnnl);
		chnnl.bind (src, snk);        

		// mc2mc

		pin  = new Port (Port.DIR.IN);
		pin.setFunc ("mc");
		pin.setName ("p1");        
		pin.setRate ("11");
		expectedGraph.add (pin);

		pout  = new Port (Port.DIR.OUT);
		pout.setFunc ("mc");
		pout.setName ("p2");        
		pout.setRate ("12");
		expectedGraph.add (pout);

		src = new PortRef ();
		src.setActorName ("mc");
		src.setPort (pout);

		snk = new PortRef ();
		snk.setActorName ("mc");
		snk.setPort (pin);

		chnnl = new Channel ();
		chnnl.setInitialTokens (1);
		expectedGraph.add (chnnl);
		chnnl.bind (src, snk);

		return expectedGraph;
	}



	/**
	 * This procedure validates the graph that is built by the XML parser
	 * against the model.
	 *  
	 * @param generatedGraph built by the XML parser
	 */
	private static void validateTestOutput (Graph generatedGraph)
	{
		Graph expectedGraph = buildExpectedGraph ();

		// Now we should compare expected and generated graph structures.
		// I wish there was a method to do this ! :)

		// First check the number of actors.
		if (generatedGraph.countActors () != expectedGraph.countActors ())
		{
			throw new RuntimeException (
					"The number of actors in generated and Expected Graph is different !");
		}

		// Now check each actor in detail.
		Iterator<Actor> actorIter = generatedGraph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor generatedGraphActr = actorIter.next ();			
			Actor expectedGraphActr = expectedGraph.getActor (generatedGraphActr.getName ());

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

				//System.out.println ("Searching : " + lnkExpected.getActor ().getName () + " --> " 
				//		+ lnkExpected.getOpposite ().getActor ().getName ());

				for (Link lnkGenerated : generatedGraphActr.getLinks (Port.DIR.OUT)) // Get All Outgoing Links	
				{					
					//System.out.println ("This link : " + lnkGenerated.getActor ().getName () + " --> " 
					//		+ lnkGenerated.getOpposite ().getActor ().getName ());

					if ((lnkExpected.getActor ().getName ().equals (lnkGenerated.getActor ().getName ())) && 
							(lnkExpected.getOpposite ().getActor ().getName ().equals (lnkGenerated.getOpposite ().getActor ().getName ())))
					{

						//System.out.println ("This link matched " + lnkGenerated.getActor ().getName () + " --> " 
						//		+ lnkGenerated.getOpposite ().getActor ().getName ());
						// Found a Match !
						found = true;

						if (lnkExpected.getChannel ().getInitialTokens () != lnkGenerated.getChannel ().getInitialTokens ())
						{
							//System.out.println ("Expected : " + lnkExpected.getChannel ().getInitialTokens () +
							//		"Generated : " + lnkGenerated.getChannel ().getInitialTokens ());

							throw new RuntimeException ("MisMatch in Initial Tokens !!");
						}						
						break;
					}					
				}

				// System.out.println ("Here");

				// Check here if we found a link or not  ?
				if (found == false)
				{
					throw new RuntimeException ("Link is Absent !!" +
							"From : " + lnkExpected.getActor ().getName () + " To : " 
							+ lnkExpected.getOpposite ().getActor ().getName ());
				}

			}			
		}

		System.out.println ("TryXML Passed the Test !");	
	}


	/**
	 * Procedure to parse XML file and build a graph.
	 * This graph is then tested with a model. 
	 * 
	 * @param args None Required
	 */
	public static void main (String[] args) 
	{
		ParseApplicationGraph xmlParse = new ParseApplicationGraph ();
		Graph g = xmlParse.parseSingleGraphXml ("inputFiles/test_graphs/XMLTest.xml");

		validateTestOutput (g);

	}
}
