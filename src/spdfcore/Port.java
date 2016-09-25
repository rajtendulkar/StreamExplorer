package spdfcore;

/**
 *  Port of an actor type ("filter" MIND object)
 *
 *  We reserve the term 'port' for class of links
 *  sharing the same name, rate and function within the given actor type. 
 *  
 *  Multiple actor instances of the same type
 *  use the same port definition, but connected 
 *  to different channels through Channel.Link.
 *  Thus a link is instantiation of a port.
 *  
 *  @author Peter Poplavko
 *   
 */
public class Port extends Id {

	/**
	 * One of the two possible port directions w.r.to the actor:
	 *  IN or OUT
	 */
	public enum DIR {
		OUT (0), IN (1);
		DIR (int value) { this.value = value; }
		private final int value;
		public int value () { return value; }
		public DIR getOpposite () { return (this==OUT) ? IN : OUT; }
	}

	//------- data ---------------
	// from Id:  inherits 'name', 'function' (actor type) and reference to a graph
	DIR dir;
	String rate;
	//-----------------------------

	/**
	 * Create a copy of a given port
	 * 
	 * @param copyPort another port
	 */
	public Port(Port copyPort)
	{
		this.dir = copyPort.dir;
		this.rate = new String(copyPort.rate);
		setName(new String(copyPort.getName()));
		setFunc(new String(copyPort.getFunc()));
	}

	/**
	 * Construct a port in the given direction w.r.t. the actor: IN or OUT.
	 * @param aDir - direction
	 */
	public Port (DIR aDir) { dir = aDir; }

	/**
	 * Initialize a port 
	 * 
	 * @param dir direction
	 * @param function port function
	 * @param name name of the port
	 * @param rate rate of the port
	 */
	public Port (Port.DIR dir, String function, String name, String rate)
	{
		this.dir = dir;
		setFunc(new String(function));
		setName (new String(name));
		setRate(new String(rate));
	}

	@Override
	public String toString () {
		return getName () + "@" + getFunc () + "@Port" + "(" + 
				(dir == DIR.OUT ? "OUT" : "IN") + ")";
		// + " r = (" + rate + ")";
	}

	/**
	 * Sets the rate value for the given port as textual expression
	 * @param txt rate in string
	 */
	public void setRate (String txt) {
		if (rate!=null)
			throw new RuntimeException ("Attempt to set rate for port " + this + "again!");
		rate = txt; 
	}

	/**
	 * Get rate of the port.
	 * 
	 * @return the rate value as String expression, or null if the rate was not set.
	 */
	public String getRate ()  {
		return rate;
	}

	/**
	 * Get port direction.
	 * 
	 * @return port direction: IN or OUT
	 */
	public DIR getDir () { return dir; }
}

