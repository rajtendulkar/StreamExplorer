package tests;
import java.util.ArrayList;

import spdfcore.Graph;
import spdfcore.stanalys.GraphExpressions;
import spdfcore.stanalys.LivenessAcyclic;
import spdfcore.stanalys.ParamComm;
import spdfcore.stanalys.Safety;
import spdfcore.stanalys.Solutions;


/**
 * Demonstrates the checking of liveness property of
 * the SPDF graph.
 * 
 * @author Peter Poplavko
 *
 */
public class TryLiveness extends TestBase {
    //-- data ---------
    private Graph graph;
    
    private ParamComm paramcomm = new ParamComm ();
    private GraphExpressions expressions = new GraphExpressions ();
    private Solutions solutions = new Solutions ();
    private Safety safety = new Safety ();
    //------------------

	/**
	 * It constructs a sample SPDF graph and checks the liveness property 
	 * of it. It checks for both false and true cases.
	 * 
	 * @param args None required
	 */
	public static void main (String[] args) {
        // Graph structure
        //----------------------
        String[][] gs = {
        		//--->
        		{ "a1", "out1", "p", "a2", "in1", "1"},
        		//--->
            	{ "a2", "out1", "q",  "a3", "in1", "1"},
        		//--->
            	{ "a3", "out1", "1", "a4", "in2",  "1" }
        };
        ArrayList<String[][]> allMDs = new ArrayList<String[][]>();
        ArrayList<Boolean>    allExpectations = new ArrayList<Boolean>();
        
        String[][] md_live0 = {
                // modifier, parameter, type, period        		
        		{ "a1", "p", "num", "1" },
        		{ "a2", "q", "num", "1"} 
        };
        allMDs.add (md_live0);
        allExpectations.add (new Boolean (true)); // expect "LIVE"
        
        String[][] md_nonlive1 = {
                // modifier, parameter, type, period        		
        		{ "a1", "p", "num", "1" },
        		{ "a3", "q", "num", "q"} 
        };
        allMDs.add (md_nonlive1);
        allExpectations.add (new Boolean (false)); // expect "NON-LIVE"
        
        for (int i=0; i<allMDs.size (); i++) {
            TryLiveness ts = new TryLiveness ();
        	String[][] mds = allMDs.get (i);
        	boolean expectation = allExpectations.get (i).booleanValue (); 
        	//System.out.println ("Experiment "+ i + ":");
            ts.run (gs,mds,expectation);
    	}
        System.out.println ("TryLiveness Passed the Test !");
	}
	
    /**
     * Procedure to check the liveness of an SPDF graph
     * 
     * @param gs
     * @param mds
     * @param expectLive Should the result be live or not
     */
    private void run (String[][] gs, String[][] mds, boolean expectLive) {
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
        
        int result = 3; // 1 = LIVE, 2 = NON-LIVE
        
        try {
            // Step: check the parameter modification safety
            safety.check (paramcomm, solutions);
        	//System.out.println ("Safe!");            	                
        	
        	// Extra step: check liveness
        	paramcomm.connectSourcesToUsers (solutions);
        	LivenessAcyclic live = new LivenessAcyclic ();
        	live.check (graph);
        	//System.out.println ("Live!");            	                
        	
        	//graph.dump ();
        	result = 1; //LIVE
        } catch (Exception e) 
        {
        	//System.out.println (e);
        	result = 2; //NON-LIVE
        }
        
        if (result == 1) 
        {//live 
        	if (!expectLive) 
        		throw new RuntimeException ("Wrong result, expected result is NON-LIVE!");
        } 
        else if (result == 2) 
        {// non-live
        	if (expectLive) 
        		throw new RuntimeException ("Wrong result, expected result is LIVE!");
        } 
        else 
        {
        	throw new RuntimeException ("Bad result!");
        }        
    }
}