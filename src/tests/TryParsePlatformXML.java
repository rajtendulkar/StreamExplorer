package tests;
import input.*;
import platform.model.*;


/**
 * Test the parsing of the hardware platform XML.
 * 
 * @author Pranav Tendulkar
 *
 */
public class TryParsePlatformXML 
{
	/**
	 * Procedure to parse Platform XML file and build a model. 
	 * 
	 * @param args None Required
	 */
	public static void main (String[] args)
	{
		ParseHardwarePlatform xmlParse = new ParseHardwarePlatform ();
		Platform p = xmlParse.parsePlatformXml ("inputFiles/hardware_platform/tilera.xml");
		p.calculateMinDistances ();		
		
		// I am believing that the validate function in the 
		// hardware parsing will verify if model for the platform and its components
		// was built correctly. Otherwise we can add tests as usual.
		System.out.println ("TryParseHardware Passed the Test !");
	}
}
