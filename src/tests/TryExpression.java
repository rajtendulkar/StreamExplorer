package tests;
import spdfcore.stanalys.*; 

/**
 * Tests the {@link spdfcore.stanalys.Expression} class with multiple test cases.
 * 
 * @author Peter Poplavko
 *
 */
public class TryExpression 
{
    /**
     * This function creates some expressions and it checks 
     * the results for addition, subtraction, division, multiplication
     * of these expressions. It checks if expected results match 
     * the ones given by solving them using {@link spdfcore.stanalys.Expression} class.
     * 
     * @param args None Required
     */
    public static void main (String[] args) 
    {
    	Expression e_33  = new Expression ("100*x*x*y");
    	Expression e_11  = new Expression ("50*x*x");    
    	
    	Expression result = Expression.divide (e_33, e_11);
    	Expression expectedResult = new Expression ("2*y");
    	
    	if (result.equals (expectedResult) == false)
    	{
    		throw new RuntimeException (
					"The expected result of " +  e_33 + " divided by " + e_11 +
					" was expected : " + expectedResult + " but was generated : "  + result);
    	}
    	
    	// System.out.println (e_33 + "/" + e_11 + "=" + result);
    	
    	result = Expression.multiply (e_33, e_11);
    	expectedResult = new Expression ("5000*y*x*x*x*x");
    	
    	if (result.equals (expectedResult) == false)
    	{
    		throw new RuntimeException (
					"The expected result of " +  e_33 + " multiplied by " + e_11 +
					" was expected : " + expectedResult + " but was generated : "  + result);
    	}
    	
    	// System.out.println (e_33 + "*" + e_11 + "=" + Expression.multiply (e_33, e_11));    	
    	// System.out.println (e_33 + " gcd " + e_11 + "=" + Expression.gcd (e_33, e_11));
    	
    	result = Expression.gcd (e_33, e_11);
    	expectedResult = new Expression ("50*x*x");
    	
    	if (result.equals (expectedResult) == false)
    	{
    		throw new RuntimeException (
					"The expected result of GCD of " +  e_33 + " and " + e_11 +
					" was expected : " + expectedResult + " but was generated : "  + result);
    	}
    	
    	Expression e_C = Expression.multiply (e_11, new Expression ("7*x"));
    	
    	result = Expression.gcd (e_C, e_33);
    	expectedResult = new Expression ("50*x*x");
    	
    	if (result.equals (expectedResult) == false)
    	{
    		throw new RuntimeException (
					"The expected result of GCD of " +  e_C + " and " + e_33 +
					" was expected : " + expectedResult + " but was generated : "  + result);
    	}
    	
    	//System.out.println (e_C + " gcd " + e_33 + "=" + Expression.gcd (e_C, e_33));   	
    	//System.out.println (e_33 + " gcd " + e_C + "=" + Expression.gcd (e_33, e_C));
    	
    	result = Expression.gcd (e_33, e_C);
    	expectedResult = new Expression ("50*x*x");
    	
    	if (result.equals (expectedResult) == false)
    	{
    		throw new RuntimeException (
					"The expected result of GCD of " +  e_33 + " and " + e_C +
					" was expected : " + expectedResult + " but was generated : "  + result);
    	}
    	
    	// result = Expression.add (e_11, e_33);
    	
    	System.out.println ("TryExpression Passed the Test !");
    	
    }
}
