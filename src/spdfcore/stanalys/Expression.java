package spdfcore.stanalys;
import java.util.*;

/**
 *  This is a class for parsing the String expressions and
 * applying arithmetic operations.
 *     
 * Later on this class should be renamed to Term, because it
 * implements only one term of multi-variable polynomial, where
 * every variable is mentioned "power" number of times
 *    4*x*x*y
 *
 * Parsing prepares internal representation of expression that
 * is convenient for operators like gcd, divide, multiply.
 * Similar to String, this object is not modifiable, once
 * it is constructed it remains equal to itself. That's why
 * it is safe to clone it using the default Object cloning method.
 *
 */
public final class Expression implements Cloneable {
	static final boolean DEBUG = true; // set to false when finished debugging 

	//************ data *******************
	/**   lookup table from factor to its power: */
	private DivisorSet divisors; // lazy cache

	/**  string provided by the user */
	private String string;
	//*********************************	
	

	public Expression (String txt) 
	{
		string = new String (txt);
	}	

	// Note! this is for internal use.
	// It assumes that the provided divisors will 
	// never be modified.
	private Expression (DivisorSet divisors) {
		this.divisors = divisors; 
		if (DEBUG)
			unparse ();
	}

	//------------------------------------------------
	// Content accessors
	//------------------------------------------------

	// Note! This is for internal use in the class.
	// Nobody is allowed to modify the contents of returned divisors! 
	private DivisorSet getDivisors () {
		parse (); // ensure presence of divisors
		return divisors;
	}

	//--------------------------------------------------------------
	/**   string ---> divisors;
	 * parse the string provided by the user, if not already parsed */
	//--------------------------------------------------------------
	public void parse () {
		if (divisors!=null) return; // already parsed

		// currently very simple stupid parsing: assuming product of primitive divisors,
		// as in our DATE2012 submission
		//  e.g. p*q  or  4*s*s*t,  but not yet 4*(s^2+2*s*t+t^2) 

		divisors = new DivisorSet (); 		
		StringTokenizer tokenizer = new StringTokenizer (string,"*");
		while (tokenizer.hasMoreTokens ()) {
			String divisor = tokenizer.nextToken ().trim ();
			DivisorSet new_divisors = new DivisorSet ();
			try {
				//--- numeric factor -------
				int num = Integer.parseInt (divisor);
				if (num!=1)
					new_divisors = PrimeDivisorSet.getDivisorSet (num);
			} catch (NumberFormatException e) {
				//--- symbolic factor -------
				new_divisors.setPower (divisor, 1);
			}
			divisors.incrPowerWith (new_divisors);
		}
	}

	//-------------------------------------------------------------
	/** string ---> divisors;
	 * parse the string provided by the user, if not already parsed */
	//--------------------------------------------------------------
	public void unparse () {
		if (string!=null) return; // no unparse needed

		Iterator<Map.Entry<String, Integer>> iter = divisors.getIterator ();
		int numericAccu = 1;
		StringBuffer symbolicAccu = new StringBuffer ();
		while (iter.hasNext ()) {
			Map.Entry<String, Integer> entry = iter.next (); 
			String divisor = entry.getKey ();
			int power = entry.getValue ().intValue ();       
			try {
				//--- numeric factor -------
				int intDivisor = Integer.parseInt (divisor);
				for (int i=1; i<=power; i++) {				
					numericAccu *= intDivisor;
				}
			} catch (NumberFormatException e) {
				//--- symbolic factor -------
				for (int i=1; i<=power; i++) {
					if (symbolicAccu.length ()==0) 
						symbolicAccu.append (divisor);
					else
						symbolicAccu.append ("*" + divisor);
				}
			}	
		}

		boolean haveNum = (numericAccu!=1);
		boolean haveSym = (symbolicAccu.length ()>0);
		if      (haveNum && haveSym)
			string = String.valueOf (numericAccu) + "*" + String.valueOf (symbolicAccu);
		else if (!haveNum && haveSym )
			string = String.valueOf (symbolicAccu);
		else //!haveSym
			string = String.valueOf (numericAccu);
	}

	/**
	 * Get a number from the expression
	 * 
	 * @return number if possible, -1 otherwise
	 */
	public int returnNumber () 
	{
		try {
			return ( Integer.parseInt (string));
		} catch (NumberFormatException e)
		{
			System.out.println ("Invalid Expression String to convert to Number");
			return -1;
		}		
	}

	/**
	 *  Determine whether given divisor is numeric (otherwise it is symbolic)
	 */
	static public boolean isNumeric (String divisor) {
		try {
			//--- numeric factor -------
			Integer.parseInt (divisor);
			// no exception, then:
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/**
	 *  Get the set of all parameters used in the expression.
	 *  Those are all divisors that are not numeric.
	 */
	public HashSet<String> getParameterSet () {
		HashSet<String> parameterSet = new HashSet<String>();
		Iterator<Map.Entry<String, Integer>> iter = divisors.getIterator ();

		while (iter.hasNext ()) {
			Map.Entry<String, Integer> entry = iter.next (); 
			String divisor = entry.getKey ();
			if (!isNumeric (divisor)) {
				parameterSet.add (divisor);
			}
		}

		return parameterSet; 
	}

	//---------------------------------------------------
	//   Object operations
	//---------------------------------------------------
	// public Object clone () -- default is OK, this object remains 
	// equal to itself as long as it exists

	// Call the HashMap's "equals" method, which calls "equals" for 
	// all keys and values
	@Override
	public boolean equals (Object obj) {
		Expression other = (Expression) obj;
		DivisorSet    myDivisors = this.getDivisors ();
		DivisorSet otherDivisors = other.getDivisors ();

		return myDivisors.equals (otherDivisors);
	}

	@Override
	public int hashCode () {
		return this.getDivisors ().hashCode ();
	}	

	@Override
	public String toString () {
		unparse ();
		return string; 
	}

	//---------------------------------------------------
	//   Mathematical operations
	//---------------------------------------------------
	
	/**
	 * Add two expressions
	 * 
	 * @param arg1 expression1
	 * @param arg2 expression2
	 * @return addition of two expressions
	 */
	static public Expression add (Expression arg1, Expression arg2)
	{
		Expression result = null;		
		
		try
		{
			int num1 = Integer.parseInt (arg1.string);
			int num2 = Integer.parseInt (arg2.string);
			result = new Expression (Integer.toString (num1 + num2));
		}
		catch (Exception e)
		{
			// TODO: Fix this
			// The strings need to be handled separately.
			// Make sure that arguments are parsed.
			throw new RuntimeException (
					"We are not yet ready to add Non-Integer Strings ! ");
			//arg1.parse ();
			//arg2.parse ();
		}		
		return result;
	}


	/**  Multiplication */
	//--------------------
	/**
	 * Multiply two expressions
	 * 
	 * @param arg1 expression1
	 * @param arg2 expression2
	 * @return multiplication of two expressions
	 */
	static public Expression multiply (Expression arg1, Expression arg2) {
		// we need to clone, as we are not allowed to modify the original!
		DivisorSet resDivisors = (DivisorSet) arg1.getDivisors ().clone ();

		// do the job: multiply
		resDivisors.incrPowerWith (arg2.getDivisors ());

		// finished modifying the divisors => ready to construct the expression 
		Expression res = new Expression (resDivisors);
		return res;
	}

	/**  
	 * Division : divide one expression by other. 
	 * Will throw a Runtime expression if not divisible
	 * @param arg1 dividend 
	 * @param arg2 divisor
	 */
	static public Expression divide (Expression arg1, Expression arg2) {
		// we need to clone, as we are not allowed to modify the original!
		DivisorSet resDivisors = (DivisorSet) arg1.getDivisors ().clone ();

		// do the job: divide
		boolean negative = true; // decrement powers		
		resDivisors.incrPowerWith (arg2.getDivisors (), negative);

		// finished modifying the divisors => ready to construct the expression 
		Expression res = new Expression (resDivisors);
		return res;
	}

	/**
	 * Greatest common divisor of two expressions.
	 * will throw a Runtime exception if not divisible
	 * 
	 * @param arg1 expression 1
	 * @param arg2 expression 2
	 * @return GCD of both the expressions.
	 */
	static public Expression gcd (Expression arg1, Expression arg2) {
		// we need to clone, as we are not allowed to modify the original!
		DivisorSet resDivisors = (DivisorSet) arg1.getDivisors ().clone ();

		// do the job: gcd
		resDivisors.intersectionWith (arg2.getDivisors ());

		// finished modifying the divisors => ready to construct the expression 
		Expression res = new Expression (resDivisors);
		return res;
	}
}
