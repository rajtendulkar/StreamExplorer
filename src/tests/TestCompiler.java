package tests;

/**
 * Perform a series of tests on the code to check if there is any problem.
 * 
 * @author Pranav Tendulkar
 *
 */
public class TestCompiler 
{
	
	/**
	 * It will call some tests in this package to check if parts of compiler
	 * are working fine. This is not an exhaustive testing but a basic one.
	 * 
	 * @param args None Required
	 */
	public static void main (String[] args)
	{
		// Test Fraction Class
		TryFraction.main (null);
		
		// Test Expression Class
		TryExpression.main (null);
		
		// Try Safety Analysis
		TrySafety.main (null);
		
		// Try Liveness Algorithm
		TryLiveness.main (null);
		
		// Try Solution solver
		TrySolutions.main (null);
		
		// Test PEDF Analyzer
		TrySpdf.main (null);
		
		// Test XML Input
		TryApplicationXML.main (null);		
		
		// Test Dot Graph Generation
		TryPrintGraph.main (null);
		
		// APGAN Test.
		TryApgan.main (null);
		
		// Test Throughput Calculation
		TryThroughput.main (null);
		
		// Test Bellman Ford Algorithm
		TryBellmanFord.main (null);
		
		// Test SDF to HSDF Tranformation
		TryTransformSDFToHSDF.main(null);
		
		// Test SDF XML generation
		TryGenerateSdfXml.main(null);
		
		// Test Hardware Platform Parsing 
		TryParsePlatformXML.main(null);
		
		// Test Quasi Static Scheduling
		// TryQuasiStatic.main (null);
		
	}
}
