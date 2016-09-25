package spdfcore.stanalys;

import spdfcore.*;
import java.util.*;

/**
 * Stores the parsed expressions for the rates and periods
 * of the given SPDF graph.
 */
public class GraphExpressions {
	
	//--------- data -------------
	private HashMap<Port, Expression> portRates // lookup from port to the (parsed) rate  
		= new HashMap<Port, Expression>(); 
	//----------------------------
	
	/**
     * parse the expressions for the periods and rates from textual 
     * form to internal form.
     *  
     * Periods are in fact modeled as rates of "period nodes", so they
     * are not explicitly parsed.
     * 
	 * @param graph - the graph whose textual expressions are to be parsed
	 */
	public void parse (Graph graph) {
		Iterator<Port> ports = graph.getPorts ();
		
		while (ports.hasNext ()) {
			Port port = ports.next ();
			String rate = port.getRate ();
			if (rate!=null) {
				Expression expr = new Expression (rate);
				expr.parse ();
				portRates.put (port, expr);
			}
		}
		
	}
	
	/**
	 * Get the rate of a port as a parsed expression. If this is a periodActor 
	 * input port, then this call returns the period. 
	 *  
	 * @param port - the port whose rate is queried
	 * @return - the parsed expression for the rate
	 */
	public Expression getRate (Port port) {
		if (portRates.containsKey (port))
			return portRates.get (port);

		String rate = port.getRate ();
		if (rate!=null) {
			Expression expr = new Expression (rate);
			expr.parse ();
			portRates.put (port, expr);
			return expr;
		}
		
		return null;
	}

	/**
	 * clear all the port rates
	 */
	public void clear () {
		portRates.clear ();
		
	}
}
