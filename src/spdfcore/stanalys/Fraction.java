package spdfcore.stanalys;

/**
 * implements a fraction
 * by construction the fraction is 'reduced': the numerator and denominator
 * are coprimes. 
 */
public class Fraction implements Cloneable 
{
	/**
	 * Numerator of the fraction
	 */
	private Expression numerator;
	/**
	 * Denominator of the fraction
	 */
	private Expression denominator;

	public Fraction (String expr) {
		numerator   = new Expression (expr);
		denominator = new Expression ("1");
	}

	/**
	 * Build a new fraction with denominator = 1.
	 * @param expr numerator of the fraction
	 */
	public Fraction (Expression expr) {
		numerator   = expr;
		denominator = new Expression ("1");		
	}
	
	/**
	 * Build a new fraction
	 * @param num numerator
	 * @param denom denominator
	 */
	public Fraction (Expression num, Expression denom) {
		// reduce the num and denom
		Expression gcd = Expression.gcd (num, denom);
		numerator    = Expression.divide (num,   gcd);
		denominator  = Expression.divide (denom, gcd);		
	}
	
	/**
	 * Get the numerator
	 * @return numerator of the fraction
	 */
	public Expression getNumerator () {
		return numerator;
	}
	
	/**
	 * Get the denominator
	 * @return denominator of the fraction
	 */
	public Expression getDenominator () {
		return denominator;
	}
	
	/**
	 * 	Convert to non-fractional expression, denominator must be = 1
	 *  otherwise an exception will be thrown.
	 *   
	 * @return the expression that is equal to the fraction
	 */
	public Expression toExpression () {
		if (!denominator.equals (new Expression ("1")))
			throw new RuntimeException (
					"Invalid conversion of " + this + " to integer polynomial!" );
		return numerator;
	}
	
	/**
	 * Get the product of two fractions
	 * 
	 * @param frac1 fraction1
	 * @param frac2 fraction2
	 * @return multiplication of fraction1 and fraction2
	 */
	static public Fraction multiply (Fraction frac1, Fraction frac2) 
	{
		Expression numerator   
			= Expression.multiply (frac1.numerator, frac2.numerator);
		Expression denominator  
		    = Expression.multiply (frac1.denominator, frac2.denominator);
				
		return new Fraction (numerator, denominator);
	}

	/**
	 * Division of two fractions
	 * 
	 * @param frac1 fraction1
	 * @param frac2 fraction2
	 * @return fraction1 divided by fraction2
	 */
	static public Fraction divide (Fraction frac1, Fraction frac2) {
		Expression numerator   
			= Expression.multiply (frac1.numerator, frac2.denominator);
		Expression denominator  
	    	= Expression.multiply (frac1.denominator, frac2.numerator);
			
		return new Fraction (numerator, denominator);
	}
	
	// equals ()
	@Override
	public boolean equals (Object obj) {
		Fraction other = (Fraction) obj;
		if (!this.numerator.equals (other.numerator)) return false;
		if (!this.denominator.equals (other.denominator)) return false;
		return true;
	}
	
	@Override
	public String toString () {
		return "(" + numerator + "/" + denominator + ")";
	}
	
	@Override
	public int hashCode () {
		return numerator.hashCode () + denominator.hashCode ();
	}
}
