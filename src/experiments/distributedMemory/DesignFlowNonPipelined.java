package experiments.distributedMemory;
import java.io.File;

import output.DotGraph;
import input.*;
//import logger.ConsoleLogger;
import designflow.NonPipelinedScheduling;
import platform.model.*;
import graphanalysis.TransformSDFtoHSDF;
import spdfcore.*;

/**
 * Test design flow to solve non-pipelined scheduling problem
 * 
 * @author Pranav Tendulkar
 *
 */
public class DesignFlowNonPipelined 
{	
	/**
	 * Entry point method for design flow for non-pipelined scheduling.
	 *  
	 * @param args command line arguments
	 */
	public static void main (String[] args) 
	{
		CommandLineArgs processedArgs = new CommandLineArgs (args);
		
		// Create the output Directory first if it doesn't exist
        File directory = new File (processedArgs.outputDirectory);
        directory.mkdirs ();

		// @SuppressWarnings({"unused", "resource"})
		// ConsoleLogger logger = new ConsoleLogger(processedArgs.outputDirectory + "solver_log.txt");

		ParseApplicationGraph applicationXmlParse = new ParseApplicationGraph ();
		Graph g = applicationXmlParse.parseSingleGraphXml(processedArgs.applicationGraphFileName);

		DotGraph dotG = new DotGraph ();
		dotG.generateDotFromGraph (g, processedArgs.outputDirectory + "out.dot");

		TransformSDFtoHSDF toHSDF = new TransformSDFtoHSDF ();
		Graph hsdf = toHSDF.convertSDFtoHSDFWithUniqueChannels (g);
		dotG.generateDotFromGraph (hsdf, processedArgs.outputDirectory + "hsdf.dot");		

		ParseHardwarePlatform platformXmlParse = new ParseHardwarePlatform ();
		Platform p = platformXmlParse.parsePlatformXml (processedArgs.platformGraphFileName);
		p.calculateMinDistances ();

		NonPipelinedScheduling designFlowNonPipelined = new NonPipelinedScheduling (g, p, processedArgs);

		// Perform Application Partitioning
		designFlowNonPipelined.performApplicationPartitioningThreeDim();

		// Invoke the Garbage Collector to possibly recover the memory.
		System.gc();

		// Perform Platform Placement of the Application.
		designFlowNonPipelined.performApplicationPlacement();

		// Invoke the Garbage Collector to possibly recover the memory.
		System.gc();

		// Perform Scheduling and Buffer Sizing.
		designFlowNonPipelined.performApplicationScheduling();

		System.out.println("Finished Design Flow.");
	}
}
