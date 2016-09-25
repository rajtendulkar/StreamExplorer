package tests;
import input.ParseApplicationGraph;
import input.CommandLineArgs;
import spdfcore.*;
import spdfcore.Actor.ActorType;
import spdfcore.stanalys.*;


/**
 * Validate the cloning of graph object and insertion of new actor in the graph.
 * 
 * @author Pranav Tendulkar
 *
 */
public class TryGraphClone
{	
	/**
	 * This test takes an example SDF graph and inserts a new actor between two actors.
	 * And then it checks for the repetition count if it is as expected.
	 * 
	 * <p>
	 * <b>Note :</b> If we modify the example, we need to modify the checking as well.
	 * <p>
	 * 
	 * @param args None Required
	 */
	public static void main (String[] args)
	{
		CommandLineArgs processedArgs = new CommandLineArgs (args);
		processedArgs.applicationGraphFileName = "inputFiles/test_graphs/hsdfTest.xml";
		
		ParseApplicationGraph applicationXmlParse = new ParseApplicationGraph ();
		Graph g = applicationXmlParse.parseSingleGraphXml(processedArgs.applicationGraphFileName);
		
		Graph duplicateGraph = new Graph(g);
		
		// Channel to remove.
		Actor srcActor = duplicateGraph.getActor("A");
		Actor dstActor = duplicateGraph.getActor("B");
		Channel chnnl = duplicateGraph.getChannel("A2B");		
		
		// Let us add a new actor to the graph
		Actor newActor = new Actor("dmaActor", "dmaActor", 100, ActorType.COMMUNICATION);		
		duplicateGraph.add(newActor);
		
		// Add new Ports of the new actor.
		Port newPort = new Port (Port.DIR.OUT, "dmaActor", "p1", "1");
		duplicateGraph.add(newPort);
		
		newPort = new Port (Port.DIR.IN, "dmaActor", "p0", "1");		
		duplicateGraph.add(newPort);		
		
		int tokenSize = chnnl.getTokenSize();
		int initialTokens = chnnl.getInitialTokens();
		
		// Insert new actor between A and B.
		Channel[] chnnls = duplicateGraph.insertNewActorBetweenTwoActors (srcActor, dstActor, chnnl, newActor, "p0", "p1");		
		
		// Don't forget to set token sizes and initial tokens.
		chnnls[0].setTokenSize(tokenSize);
		chnnls[0].setInitialTokens(initialTokens);
		chnnls[1].setTokenSize(tokenSize);
		chnnls[1].setInitialTokens(initialTokens);
		
		// This should not throw any error if we are inserting the actor correctly.
		GraphExpressions expressions = new GraphExpressions ();
		expressions.parse (duplicateGraph);
		Solutions solutions = new Solutions ();
		solutions.solve (duplicateGraph, expressions);
		
		if(duplicateGraph.hasActor("dmaActor") == false)
			throw new RuntimeException ("Adding new actor failed");
		
		if(solutions.getSolution(duplicateGraph.getActor("dmaActor")).returnNumber() != 6)
			throw new RuntimeException ("Wrong solution calculated. We modified the rates? Or error in actor insertion?");
		
		System.out.println("Graph clone Passed the Test !");
	}
}
