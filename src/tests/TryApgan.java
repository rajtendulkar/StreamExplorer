package tests;
import input.ParseApplicationGraph;
import graphanalysis.scheduling.*;
import spdfcore.Graph;

/**
 * This class tests the APGAN algorithm.
 * 
 * @author Pranav Tendulkar
 *
 */
public class TryApgan 
{
	/**
	 * This method just verifies if the generated schedule by APGAN for a fixed
	 * test graph is as we expect.
	 * 
	 * @param generatedSchedule The schedule that was generated by running the algorithm.
	 */
	private static void validateTestOutput (String generatedSchedule)
	{
		String expectedSchedule = new String ("((2*((((3*A)*(B)))*(2*C)))*(((E)*(5*D))))");		
		
		if (generatedSchedule.compareTo (expectedSchedule) != 0)
		{
			throw new RuntimeException ("The generated and Expected Schedules do not match !" +
					"Generated : " + generatedSchedule + " Expected : " + expectedSchedule);
		}
		else
			System.out.println ("TryApgan Passed the Test !");
		
	}
	
	
	/**
	 * Main function to test APGAN Algorithm
	 * @param args None Required
	 */
	public static void main (String[] args) 
	{		
		Apgan apganAlgo = new Apgan ();

		ParseApplicationGraph xmlParse = new ParseApplicationGraph ();
		Graph g = xmlParse.parseSingleGraphXml ("inputFiles/test_graphs/ApganTest.xml");
				
		// Generate the APGAN schedule.
		apganAlgo.generateScheduleApgan (g);
		
		String schedule = apganAlgo.getStringSchedule ();
		
		validateTestOutput (schedule);
			
	}
}
