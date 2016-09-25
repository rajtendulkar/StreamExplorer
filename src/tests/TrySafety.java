package tests;

import spdfcore.*;
import spdfcore.stanalys.*;

import java.util.*;

/**
 * Test verification of safety property of an SPDF graph.
 * 
 * @author Peter Poplavko
 *
 */
public class TrySafety extends TestBase {
    //-- data ---------
    private Graph graph;
    
    private ParamComm paramcomm = new ParamComm ();
    private GraphExpressions expressions = new GraphExpressions ();
    private Solutions solutions = new Solutions ();
    private Safety safety = new Safety ();
    //------------------

	public static void main (String[] args) {
        // Graph structure
        //----------------------
        String[][] gs = {
        		//--->
        		{ "a1", "out1", "2*p", "a2", "in1", "1"},
        		//--->
            	{ "a2", "out1", "q",  "a3", "in1", "p"},
        		//--->
            	{ "a4", "out1", "3*q", "a3", "in2",  "1" }
        };
        ArrayList<String[][]> allMDs = new ArrayList<String[][]>(); 
        ArrayList<Boolean>    allExpectations = new ArrayList<Boolean>();
        
        String[][] md_unsafe0 = {
                // modifier, parameter, type, period        		
        		{ "a1", "p", "num", "1" },
        		{ "a2", "q", "num", "3*p"} // to be safe could be "3 \circ p"
        };
        allMDs.add (md_unsafe0);
        allExpectations.add (new Boolean (false)); // expect "NON-SAFE"
        
        String[][] md_safe1 = {
                // modifier, parameter, type, period        		
        		{ "a1", "p", "num", "1" },
        		{ "a4", "q", "num", "1"} // at "a2" the period is in effect "3 \circ p"!
        };         
        allMDs.add (md_safe1);
        allExpectations.add (new Boolean (true)); // expect "SAFE"
        
        String[][] md_unsafe2 = {
                // modifier, parameter, type, period        		
        		{ "a1", "p", "num", "1" },
        		{ "a2", "q", "num", "6*p"} 
        };
        allMDs.add (md_unsafe2);
        allExpectations.add (new Boolean (false)); // expect "NON-SAFE"
        
        String[][] md_safe3 = {
                // modifier, parameter, type, period        		
        		{ "a1", "p", "num", "3" },
        		{ "a2", "q", "num", "3*p"} 
        };   
        allMDs.add (md_safe3);
        allExpectations.add (new Boolean (true)); // expect "SAFE"
        
        for (int i=0; i<allMDs.size (); i++) {
            TrySafety ts = new TrySafety ();
        	String[][] mds = allMDs.get (i);
        	boolean expectSafe = allExpectations.get (i).booleanValue (); 
        	//System.out.println ("Experiment "+ i + ":");
            ts.run (gs,mds,expectSafe);
    	}
        System.out.println ("TrySafety Passed the Test !");
	}
	
    private void run (String[][] gs, String[][] mds, boolean expectSafe) {
        	int numA = 4; // nr actors
        	int numP = 2; // nr ports
        	
        	graph = constructGraph (numA, numP, gs, mds);        	
        	paramcomm.setGraph (graph);

        	// Step: insert period actors (virtual actors that model the periods)
        	paramcomm.insertPeriodActors ();
        	
            // Step: parse the expressions for the periods and rates
        	//  (periods are in fact modeled as rates of period actors)
            expressions.parse (graph);

            // Step: make sure that for every parameter it is known
            // where its values come from.
            paramcomm.identifyParameterSources (expressions);
            
            // Step: solve the balance equations
            solutions.solve (graph, expressions);
            
            // Step: for each parameter, identify its users
        	paramcomm.identifyParameterUsers (expressions, solutions);
        	
            // Print graph 
            //graph.dump ();
    
            int result = 3; // 1 - safe, 2 - non safe
            try {
                // Step: check the parameter modification safety
                safety.check (paramcomm, solutions);
            	//System.out.println ("Safe!");            	                
            	result = 1;
            	
                // Extra step: check liveness
                //   (no harm to do extra testing)
            	paramcomm.connectSourcesToUsers (solutions);
            	LivenessAcyclic live = new LivenessAcyclic ();
            	live.check (graph);      

            	//graph.dump ();
            } catch (Exception e) 
            {
            	//System.out.println (e);            	                            	
            	result = 2;
            }
        	
            if (result == 1) {//safe 
            	if (!expectSafe) 
            		raiseError ("Wrong result, expected result is NON-SAFE!");
            } else if (result == 2) {// non-safe
            	if (expectSafe) 
            		raiseError ("Wrong result, expected result is SAFE!");
            } else {
            	raiseError ("Bad result!");
            }
            
        }
    
    static private void raiseError (String text) {
		throw new RuntimeException (text);
	}

}
