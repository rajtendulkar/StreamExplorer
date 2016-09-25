package tests;
import spdfcore.*;
import spdfcore.stanalys.*;

/**
 * Test the balance equation solver using a cyclic graph
 * two sets of rates: consistent and inconsistent.
 * and different starting actors.
 * 
 * @author Peter Poplavko
 */
public class TrySolutions {
	public static void main (String[] args) {
        
        class Rates {
        	public String inRates[][];
        	public String outRates[][];
        }
        
        // Graph structure
        //----------------------
        String[][] gs = {
            	{ "a1", "out1", "a2", "in2"},
            	// the cycle
            	{ "a2", "out1", "a3", "in1"},        		
            	{ "a3", "out1", "a4", "in1"},        		
            	{ "a4", "out1", "a5", "in1"},        		            	
            	{ "a5", "out1", "a2", "in1"}        		            	
        };
        		
        // Consistent rates
        //----------------------------
        String[][] consis_InRates = { // [a_i][in_j] 
        		{"1", "1"},
        		// input side of the cycle
        		{"2", "1"}, {"3*x", "1"}, {"7*y", "1"}, {"5", "1"}        						
        };        
        String[][] consis_outRates = { // [a_i][out_j]
        		{"1", "1"},        
        		// output side of the cycle
				{"3", "1"}, {"2", "1"}, {"5*y*x", "1"}, {"7", "1"}        						
		};

        // Inconsistent rates
        //----------------------------
        //   Add z multiplier to OUT
        //   Add 7 multiplier to IN
        String[][] inConsis_InRates = { // [a_i][in_j] 
        		{"1", "1"},
        		// input side of the cycle
        		{"2", "1"}, {"3*x*7", "1"}, {"7*y", "1"}, {"5", "1"}        						
        };        
        String[][] inConsis_outRates = { // [a_i][out_j]
        		{"1", "1"},        
        		// output side of the cycle
				{"3", "1"}, {"2", "1"}, {"5*y*x", "1"}, {"7*z", "1"}        						
		};

        Rates [] allRates = new Rates[2];
        allRates[0] = new Rates ();
        allRates[0].inRates  = consis_InRates;
        allRates[0].outRates = consis_outRates; 
        allRates[1] = new Rates ();
        allRates[1].inRates  = inConsis_InRates;
        allRates[1].outRates = inConsis_outRates; 
        
        for (int test=0; test<2; test++) {
        	Graph g = new Graph (); 
        	Rates rates = allRates[test];
        	boolean ratesAreConsistent = (test==0);

        	// Ports
        	//----------
        	for (int i=1; i<=5; i++) {
        		for (int j=1; j<=2; j++) {
        			Port p = new Port (Port.DIR.IN);
        			p.setName ("in"+j);
        			p.setFunc ("Func"+i);
        			p.setRate (rates.inRates[i-1][j-1]);
        			g.add (p);
        		}
        		for (int j=1; j<=2; j++) {
        			Port p = new Port (Port.DIR.OUT);
        			p.setName ("out"+j);
        			p.setFunc ("Func"+i);
        			p.setRate (rates.outRates[i-1][j-1]);
        			g.add (p);
        		}
        	}
        
        	// Create graph structure
        	for (int i=1; i<=5; i++) {
        		Actor a = new Actor ();
        		a.setFunc ("Func"+i);
        		a.setName ("a"+i);
        		g.add (a);
        	}
        	
        	for (int k=0; k<gs.length; k++) {
                PortRef src = new PortRef ();
                src.setActorName (gs[k][0]);
                src.setPortName (gs[k][1]);
                PortRef snk = new PortRef ();
                snk.setActorName (gs[k][2]);
                snk.setPortName (gs[k][3]);
                Channel prodcons = new Channel ();
                g.add (prodcons);
                prodcons.bind (src, snk);
        	}
        	
        	//g.dump ();
        	
        	// Solutions
        	GraphExpressions expressions = new GraphExpressions ();
        	expressions.parse (g);
        	
        	for (int i=1; i<=5; i++) {
        		Actor startActor = g.getActor ("a"+i);
        		Solutions solutions = new Solutions ();
        		solutions.setThrowExceptionFlag (false);
        		//System.out.println ("From " + startActor.getName ());
        		InconsistencyProof inconsistency = 
        		solutions.solve (g, expressions, startActor);
        		if (inconsistency==null) {
            		//System.out.println ("Solutions:");
            		//System.out.println (solutions);
        			if (ratesAreConsistent)
        				checkSolutions (g, solutions);
        			else
        				throw new RuntimeException ("Inconsistent rates were not detected!");
        		}
        		else 
        		{
        			//System.out.println (inconsistency.diagnostics ());
        			if (ratesAreConsistent)
        				throw new RuntimeException ("False alarm about inconsistency!");
        			else
        				checkInconsistency (inconsistency);
        		}
    			//System.out.println ();
        	}
        }
        System.out.println ("TrySolutions Passed the Test !");
		//System.out.println ("FINISH");
	}
	
	static private void checkSolutions (Graph graph, Solutions solutions) {
		String[] goodSolutions = { "y*7*x", "7*x*y", "y*7", "2", "x*y*2" };
		String[] actorNames = {"a1", "a2", "a3", "a4", "a5"};
		int i;
		for (i=0;i<actorNames.length; i++) {
			Actor actor = graph.getActor (actorNames[i]);
			Expression correctResult = new Expression (goodSolutions[i]);
			if (!solutions.getSolution (actor).equals (correctResult)) {
				throw new RuntimeException ("Bad solution for actor " + actor.getName () + " Expected: " + correctResult);
			}
		}
	}

	static private void checkInconsistency (InconsistencyProof inconsistency) {
		Fraction correctResult = 
				new Fraction (new Expression ("z"), 
						     new Expression ("7"));
		if (!inconsistency.getOutToInRatio ().equals (correctResult)) 
			throw new RuntimeException ("Bad incosistency ratio, expected " + correctResult);		
	}
}