package spdfcore;

/**
 * Modifier - actor type that sets the parameter. 
 *   Currently for each parameter only one actor instance of modifier type is allowed. 
 *   to be instantiated, otherwise the users of the parameters would need
 *   reference to the particular instance of a modifier of the given parameter. 
 *   
 *   @author Peter Poplavko
 */
public class Modifier extends Id {
	//-- parameter type ---
	// inherited: 
	//      name = parameter name
	//      func = actor type of the modifier
	//      graph
	private Id type   = new Id (); // misuse Id to store the string of parameter type	
	private Id period = new Id (); // misuse Id to store the string of period	
	//------------------
	
	// inherited:  
	//     setFunc (), 
	//     getFunc (), 
	//     setGraph (), 
	//     getGrapgh ()
	
	/**
	 * Set Parameter Name
	 * @param name name of the parameter
	 */
	public void setParameter (String name) {
		setName (name);
	}
	
	/**
	 * Get Parameter
	 * @return name of the parameter
	 */
	public String getParameter () {
		return getName ();
	}
	
	/**
	 * Set type of the parameter
	 * 
	 * @param typeName type of the parameter
	 */
	public void setParameterType (String typeName) {
		type.setName (typeName);
	}
	
	/**
	 * Get type of the parameter
	 * @return type of the parameter
	 */
	public String getParameterType () {
		return type.getName ();
	}

	/**
	 * Set period
	 * @param txt period
	 */
	public void setPeriod (String txt) {
		period.setName (txt);
	}
	/**
	 * Get period
	 * @return period in string
	 */
	public String getPeriod () {
		return period.getName ();
	}
		
	@Override
	public String toString () {
		return "(" + 
			"set " + getParameter () + ":" + getParameterType () + "[" + getPeriod () + "]"
			    + ")" 
			    + "@" + getFunc ();
	}
}
