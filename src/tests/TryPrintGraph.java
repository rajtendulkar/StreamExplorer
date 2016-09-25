package tests;
import spdfcore.*;

import java.io.InputStream;
import java.io.FileInputStream;

import output.*;

	
/**
 *  Validate the Dot File generation from Graph
 *
 *  @author Pranav Tendulkar          
 */
public class TryPrintGraph  
{
	private static void validateTestOutput (String fileName)
	{	
		InputStream input1 = null;
		InputStream input2 = null;
		try 
		{
			input1 = new FileInputStream (fileName);
			input2 = new FileInputStream ("outputFiles/TryPrintGraphOutput.dot");
			
			while (true)
			{
				int read1 = input1.read ();
				int read2 = input2.read ();
				if (read1 != read2)
				{
					input1.close ();
					input2.close ();
					throw new RuntimeException (
							"The generated output file does not match expected outputfile. " +
							"Is there any change in output format?" );
				}
				else if ((read2 == -1) && (read1 == -1))
				{
					System.out.println ("TryPrintGraph Passed the Test !");
					input1.close ();
					input2.close ();
					return;
				}
			}			
		} catch (Exception e)
		{}			
	}
	
	
    /**
     * Procedure to generate a dotGraph for simple application graph.
     * Later we match it with an expected result.
     * 
     * @param args None Required
     */
    public static void main (String[] args) 
    {
    	String fileName = new String ("outputFiles/out.dot");
   
    	// Start making the graph
        Graph g = new Graph (); // producer-consumer
        DotGraph dotG = new DotGraph ();
        
        //   prodconsModule.adl 
        //   contains Prod as prod
        Actor prod = new Actor ();
        prod.setFunc ("Prod");
        prod.setName ("prod");
        g.add (prod);
        
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
        src.setActor (prod);
        src.setPort (pout);        
        
        PortRef snk = new PortRef ();
        snk.setActor (cons);
        snk.setPort (pin);
        
        Channel prodcons = new Channel ();
        g.add (prodcons);
        prodcons.bind (src, snk);
        
        dotG.generateDotFromGraph (g, fileName);        
        validateTestOutput (fileName);
    }
}


