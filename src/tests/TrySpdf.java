package tests;
import java.util.Iterator;

import spdfcore.*;
import spdfcore.stanalys.*;

/**
 * 
 *  Purpose: Demonstrate the basic API and PEDF extension. 
 *           Simplest example: producer-consumer.
 *           
 */
public class TrySpdf 
{
    public static void main (String[] args) 
    {
   
    	// Start making the graph
        Graph g = new Graph (); // producer-consumer
        
        //   prodconsModule.adl 
        //      contains Prod as prod
        Actor prod = new Actor ();
        prod.setFunc ("Prod");
        prod.setName ("prod");
        g.add (prod);
        
        //    Prod.adl
        //      @Modify (parameter="p",type="bool",period="2")        
        Modifier modifier = new Modifier ();
        modifier.setFunc ("Prod");
        modifier.setParameter ("p");
        modifier.setParameterType ("bool");
        modifier.setPeriod ("2");
        g.add (modifier);

        //    Prod.adl
        //       @Rate (5*p)
        //       output uint16_t as out;        
        Port pout  = new Port (Port.DIR.OUT);
        pout.setFunc ("Prod");
        pout.setName ("out");        
        pout.setRate ("5*p*");
        g.add (pout);


        // prodconsModule.adl
        //   @ParamRef (parameter="p",modifier="prod") // optional
        //   contains Cons as cons
        Actor cons = new Actor ();
        cons.setFunc ("Cons");
        cons.setName ("cons");
        g.add (cons);

        //    Cons.adl
        //       @Rate (5)
        //       input uint16_t as in;        
        Port pin  = new Port (Port.DIR.IN);
        pin.setFunc ("Cons");
        pin.setName ("in");        
        pin.setRate ("5");
        g.add (pin);

        // prodconsModule.adl 
        //  binds prod.out to cons.in;
        PortRef src = new PortRef ();
        src.setActorName ("prod");
        src.setPortName ("out");
        PortRef snk = new PortRef ();
        snk.setActorName ("cons");
        snk.setPortName ("in");
        Channel prodcons = new Channel ();
        g.add (prodcons);
        prodcons.bind (src, snk);

        // Print graph contents
        //g.dump ();
        //prod.dump ();
        //cons.dump ();
        
        // Static analysis
        SpdfAnalyzer analyzer = new SpdfAnalyzer (g);
        analyzer.run ();
        //System.out.println ("Solutions: " + analyzer.getSolutions ());
        Solutions soln = analyzer.getSolutions ();
        validateTestOutput (g, soln);        
        System.out.println ("TryPedf1 Passed the Test !");
    }
    
    private static void validateTestOutput (Graph graph, Solutions solution)
    {
    	String[][] expectedResults = {{"prod", "2"}, {"<p>", "1"}, {"cons", "2*p"}};
    	
    	Iterator<Actor> actrIter = graph.getActors ();
    	while (actrIter.hasNext ())
    	{
    		Actor actor = actrIter.next ();
    		if (solution.contains (actor))
    		{    			
    			for (String[] i : expectedResults)
    			{    				
    				if (i[0].equals (actor.getName ()))
    				{
    					Expression result = solution.getSolution (actor);
    					Expression expectedResult = new Expression (i[1]);
    					if (expectedResult.equals (result) == false)
    					{
    						throw new RuntimeException (
    								"The expected solution of Actor " + actor.getName () + " was " + expectedResult 
    								+ ". But the generated result is " + result + ".");
    					}
    					break;
    				}
    			}
    			
    		}
    		else
    		{
    			throw new RuntimeException (
    					"Solution Missing for Actor : " + actor.getName ());
    		}
    	}    	
    }
}


