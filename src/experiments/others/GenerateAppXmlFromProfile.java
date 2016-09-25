package experiments.others;

import output.GenerateSdfXml;
import spdfcore.Graph;
import input.CommandLineArgs;
import input.ParseProfileInfo;

/**
 * Generate an application XML file from the profiling 
 * information xml generated from the hardware platform.
 * 
 * @author Pranav Tendulkar
 *
 */
public class GenerateAppXmlFromProfile
{
	/**
	 * Entry point method to generate application XMl from profile XML
	 * 
	 * @param args command line arguments
	 */
	public static void main (String[] args) 
	{
		CommandLineArgs cmdArgs = new CommandLineArgs (args);
		
		ParseProfileInfo profileParser = new ParseProfileInfo();
		Graph graph = profileParser.parseProfileXml(cmdArgs.profileXmlFileName);
		
		GenerateSdfXml generateAppXml = new GenerateSdfXml();
		
		generateAppXml.generateOutput(graph, cmdArgs.outputXmlFileName);
	}	
}
