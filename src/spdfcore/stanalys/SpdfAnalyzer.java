package spdfcore.stanalys;

import spdfcore.*;

/**
 * Static analysis for conversion to PEDF
 * 
 * @author Peter Poplavko
 *
 */
public class SpdfAnalyzer {
    //-- data ---------
    private Graph graph;
    
    private ParamComm paramcomm = new ParamComm ();
    private GraphExpressions expressions = new GraphExpressions ();
    private Solutions solutions = new Solutions ();
    private Safety safety = new Safety ();
    //------------------
    //
    public SpdfAnalyzer (Graph g) { 
    	graph = g; 
    	paramcomm.setGraph (graph);
    }

    public void run () {
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
        
        // Step: check the parameter modification safety
        safety.check (paramcomm, solutions);
        
    	// Step: check liveness
    	paramcomm.connectSourcesToUsers (solutions);
    	LivenessAcyclic live = new LivenessAcyclic ();
    	live.check (graph);
    }
    
    public Solutions getSolutions () {
    	return solutions;
    }
}


