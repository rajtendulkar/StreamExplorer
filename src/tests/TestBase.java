package tests;
import spdfcore.*;

/***
 *  Provide some useful functions as basis for tests. 
 */
public class TestBase {
	
	/**
	 * Construct an SPDF graph.
	 * @param numA - number of actors, will be named "a1", "a2",... 
	 * @param numP - maximum number of input and output ports "in1", "in2",..., "out1", "out2",...
	 * @param gs - graph structure - array of edges { "actor1", "port1", "rate1", "actor2", "port2", "rate2" }
	 * @param md - array of modifiers { "actor", "parameter", "num/bool", "period" } 
	 * @return - the constructed graph
	 * 
	 * 
	 */
	protected Graph constructGraph (int numA, int numP, String[][] gs, String[][] md) {

		final int	ACTOR1 = 0, PORT1=1, RATE1=2, ACTOR2=3, PORT2=4, RATE2=5,
					M_ACTOR = 0, PARAMETER=1, TYPE=2, PERIOD=3;
		
		Graph g = new Graph ();
		
		// create actors and ports
		for (int i=1; i<=numA; i++) {
    		Actor a = new Actor ();
    		a.setFunc ("Func"+i);
    		a.setName ("a"+i);
    		g.add (a);
    		for (int j=1; j<=numP; j++) {
    			Port p = new Port (Port.DIR.IN);
    			p.setName ("in"+j);
    			p.setFunc ("Func"+i);
    			g.add (p);
    		}
    		for (int j=1; j<=numP; j++) {
    			Port p = new Port (Port.DIR.OUT);
    			p.setName ("out"+j);
    			p.setFunc ("Func"+i);
    			g.add (p);
    		}
    	}
    	
		// set port rates		
    	for (int k=0; k<gs.length; k++) {    		
    		setPortRate (g, gs[k][ACTOR1], gs[k][PORT1], gs[k][RATE1]);
    		setPortRate (g, gs[k][ACTOR2], gs[k][PORT2], gs[k][RATE2]);    		    	
    	}
    	
    	// connect the ports
    	for (int k=0; k<gs.length; k++) {
            PortRef src = new PortRef ();
            src.setActorName (gs[k][ACTOR1]);
            src.setPortName (gs[k][PORT1]);
            PortRef snk = new PortRef ();
            snk.setActorName (gs[k][ACTOR2]);
            snk.setPortName (gs[k][PORT2]);
            Channel prodcons = new Channel ();
            g.add (prodcons);
            prodcons.bind (src, snk);
    	}
    	
    	// create the modifiers
    	for (int k=0; k<md.length; k++) {
    		Actor modifierActor = g.getActor (md[k][M_ACTOR]);
    		String func = modifierActor.getFunc (); 
    		Modifier modifier = new Modifier ();
            modifier.setFunc (func);
            modifier.setParameter (md[k][PARAMETER]);
            modifier.setParameterType (md[k][TYPE]);
            modifier.setPeriod (md[k][PERIOD]); 
            g.add (modifier);
    	}    	

    	return g;
	}
	
	private void setPortRate (Graph g, String actorName, String portName, String rate ) {
		Actor actor = g.getActor (actorName);
		String func = actor.getFunc ();
		Id portId = new Id ();
		portId.setFunc (func);
		portId.setName (portName);
		Port port = g.getPort (portId);
		port.setRate (rate);
	}
}
