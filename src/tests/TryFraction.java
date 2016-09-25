package tests;
import spdfcore.stanalys.*;

/**
 * 
 * Tests the {@link spdfcore.stanalys.Fraction} class using small example.
 * 
 * @author Peter Poplavko
 *
 */
public class TryFraction {

	/**
	 * This function creates some fractions and it checks 
     * the results for division, multiplication
     * of these fractions. It checks if expected results match 
     * the ones given by solving them using {@link spdfcore.stanalys.Fraction} class.
	 * 
	 * @param args None Required
	 */
	public static void main (String[] args) {
    	
    	Fraction f_33 = new Fraction (new Expression ("100*x*x*y"), 
    								 new Expression ("66*x*z"));
    	Fraction f_11 = new Fraction (new Expression ("10*z*z*y"), 
    								 new Expression ("11*z*y"));
    	
    	Fraction result = Fraction.divide (f_33, f_11);
    	Fraction expectedResult = new Fraction (new Expression ("5*x*y"), 
				 new Expression ("3*z*z"));;
				 
	    if (result.equals (expectedResult) == false)
	    {
	    	throw new RuntimeException (
					"The expected result of Division of " +  f_33 + " by " + f_11 +
					" was expected : " + expectedResult + " but was generated : "  + result);
	    }
	    
	    result = Fraction.multiply (f_33, f_11);
    	expectedResult = new Fraction (new Expression ("500*x*y"), 
				 new Expression ("363"));;
				 
	    if (result.equals (expectedResult) == false)
	    {
	    	throw new RuntimeException (
					"The expected result of Multiplication of " +  f_33 + " by " + f_11 +
					" was expected : " + expectedResult + " but was generated : "  + result);
	    }    	
    	
    	// System.out.println (f_33 + "/" + f_11 + "=" + Fraction.divide (f_33, f_11));
    	// System.out.println (f_33 + "*" + f_11 + "=" + Fraction.multiply (f_33, f_11));
    	
    	System.out.println ("TryFraction Passed the Test !");
		
	}

}
