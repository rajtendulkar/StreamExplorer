package underDevelopment;

import java.util.ArrayList;

import spdfcore.Graph;
import spdfcore.stanalys.GraphExpressions;
import spdfcore.stanalys.LivenessAcyclic;
import spdfcore.stanalys.ParamComm;
import spdfcore.stanalys.Safety;
import spdfcore.stanalys.Solutions;
import tests.TestBase;


public class TryQuasiStatic extends TestBase {
    //-- data ---------
    private Graph graph;
    
    private ParamComm paramcomm = new ParamComm ();
    private GraphExpressions expressions = new GraphExpressions ();
    private Solutions solutions = new Solutions ();
    private Safety safety = new Safety ();
    //------------------

	/**
	 * @param args
	 */
	public static void main (String[] args) {
        // Graph structure
        //----------------------
        String[][] gs = {
        		//--->
        		{ "a1", "out1", "3*q", "a2", "in1", "1"},
        		//--->
            	{ "a2", "out1", "p",  "a3", "in1", "3"},        		
        };
        ArrayList<String[][]> allMDs = new ArrayList<String[][]>(); 
        
        String[][] md_live0 = {
                // modifier, parameter, type, period        		
        		{ "a1", "q", "num", "1" },
        		{ "a2", "p", "num", "3"} 
        };
        allMDs.add (md_live0);        

        
        for (int i=0; i<allMDs.size (); i++) {
        	TryQuasiStatic ts = new TryQuasiStatic ();
        	String[][] mds = allMDs.get (i);
        	//System.out.println ("Experiment "+ i + ":");
            ts.run (gs,mds);
    	}
	}
	
    private void run (String[][] gs, String[][] mds) {
    	int numA = 3; // nr actors
    	int numP = 2; // nr ports    	
    	
    	graph = constructGraph (numA, numP, gs, mds);    	
    	
    	paramcomm.setGraph (graph);

    	// Step: insert period actors (virtual actors that model the periods)
    	paramcomm.insertPeriodActors ();
    	
        // Step: parse the expressions for the periods and rates
    	// (periods are in fact modeled as rates of period actors)
        expressions.parse (graph);        

        // Step: make sure that for every parameter it is known
        // where its values come from.
        paramcomm.identifyParameterSources (expressions);
        
        // Step: solve the balance equations
        solutions.solve (graph, expressions);
        
        solutions.dump ();
        
        // Step: for each parameter, identify its users
    	paramcomm.identifyParameterUsers (expressions, solutions);
    	
        // Print graph 
        graph.dump ();
        
        try {
            // Step: check the parameter modification safety
            safety.check (paramcomm, solutions);
        	//System.out.println ("Safe!");
        	
        	// Extra step: check liveness
        	paramcomm.connectSourcesToUsers (solutions);
        	LivenessAcyclic live = new LivenessAcyclic ();
        	live.check (graph);
        	//System.out.println ("Live!");
        	
        	QuasiStaticScheduling quasiStSchedule = new  QuasiStaticScheduling (graph, paramcomm, solutions);        	
        	quasiStSchedule.generateSchedule ();        	
        	System.out.println ("Generated Quasi Static Schedule : " + quasiStSchedule.getStringSchedule ());
        	
        	//graph.dump ();
        } catch (Exception e) {
        	System.out.println (e);            	                            	
        }
    }
}
