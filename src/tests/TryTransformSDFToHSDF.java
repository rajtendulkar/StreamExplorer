package tests;
import input.*;
import spdfcore.*;
import spdfcore.stanalys.GraphExpressions;
import spdfcore.stanalys.Solutions;
import graphanalysis.*;


/**
 * Test conversion of SDF graph to HSDF graph with simple tests.
 * 
 * @author Pranav Tendulkar
 *
 */
public class TryTransformSDFToHSDF
{
	
	/**
	 * Procedure to convert SDF to HSDF graph and then we perform some simple
	 * tests on the HSDF graph to check their rates, number of actors and repetition count.
	 * 
	 * @param args None Required
	 */
	public static void main (String[] args) 
	{		 
		 ParseApplicationGraph xmlParse = new ParseApplicationGraph ();
		 Graph g = xmlParse.parseSingleGraphXml ("inputFiles/test_graphs/hsdfTest.xml");	
				 
		 TransformSDFtoHSDF toHSDF = new TransformSDFtoHSDF ();
		 Graph hsdf = toHSDF.convertSDFtoHSDF (g);
		  
		 String expectedHsdfActors[] = {"B_0", "B_1", "B_2", "B_3", "B_4", "C_0", "B_5", "A_1", "C_1", "A_0", "C_2"};
		 
		 // Check how many actors
		 if(hsdf.countActors() != expectedHsdfActors.length)
			 throw new RuntimeException("Wrong Number of actors in HSDF Graph\n");

		 // Check which actors are present
		 for(String expectedActr : expectedHsdfActors)
			 if(hsdf.hasActor(expectedActr) == false)
				 throw new RuntimeException("Actors " + expectedActr + " missing in HSDF Graph\n");
		 
		 // Check rates of all the ports
		 for(Actor actr : hsdf.getActorList())
		 {
			 for(Channel chnnl : actr.getAllChannels())
			 {
				 Port inputPort = chnnl.getLink(Port.DIR.IN).getPort();
				 int inputRate = Integer.parseInt(inputPort.getRate());
				 Port outputPort = chnnl.getLink(Port.DIR.OUT).getPort();
				 int outputRate = Integer.parseInt(outputPort.getRate());
				 if ((inputRate != 1) || (outputRate != 1))
					 throw new RuntimeException("Rates are not 1\n");
			 }
		 }
		
		 // Check if all actors have repetion count as 1. 
         Solutions solutions;
         GraphExpressions expressions = new GraphExpressions (); 
         expressions.parse (hsdf);
         solutions = new Solutions (); 
         solutions.setThrowExceptionFlag (false);
         solutions.solve (hsdf, expressions);
         
         for(Actor actr : hsdf.getActorList())
        	if(solutions.getSolution(actr).returnNumber() != 1)
        		throw new RuntimeException("Solution of "+ actr.getName() +" not equal to 1\n");
		 
		 System.out.println ("SDF to HSDF Passed the Test !");
	}
}
