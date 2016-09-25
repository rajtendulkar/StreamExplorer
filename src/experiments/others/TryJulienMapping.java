package experiments.others;
import graphanalysis.TransformSDFtoHSDF;
import input.ParseApplicationGraph;
import input.ParseHardwarePlatform;
import input.CommandLineArgs;

import java.io.*;

import exploration.parameters.twoDimension.WrkLoadCommCostParams;
import exploration.paretoExploration.gridexploration.GridBasedExploration;
import platform.model.*;
import solver.distributedMemory.mapping.*;
import spdfcore.*;
import spdfcore.stanalys.*;


/**
 * Test mapping constraints developed by Julien Legriel
 * in "Meeting Deadlines Cheaply" paper.
 * 
 * @author Pranav Tendulkar
 *
 */
public class TryJulienMapping 
{	
	/**
	 * Entry point for testing mapping constraints.
	 * 
	 * @param args command line arguments
	 */
	public static void main (String[] args)
	{		
		CommandLineArgs processedArgs = new CommandLineArgs (args);
		processedArgs.printConfig ();
		
		// Create the output Directory first if it doesn't exist
        File directory = new File (processedArgs.outputDirectory);
        directory.mkdirs ();
		
		ParseApplicationGraph xmlParse = new ParseApplicationGraph ();
		Graph g = xmlParse.parseSingleGraphXml (processedArgs.applicationGraphFileName);
		TransformSDFtoHSDF toHSDF = new TransformSDFtoHSDF ();
		Graph hsdf = toHSDF.convertSDFtoHSDFWithUniqueChannels (g);
		
		Solutions solutions;
		GraphExpressions expressions = new GraphExpressions ();
		expressions.parse (g);
		solutions = new Solutions ();
		solutions.setThrowExceptionFlag (false);
		solutions.solve (g, expressions);		
		
		// int i=4;
		// for (int i=2;i<=16;i++)
		{
			ParseHardwarePlatform platformXmlParse = new ParseHardwarePlatform ();
			Platform p = platformXmlParse.parsePlatformXml ("inputFiles/hardware_platform/mesh_16x16.xml");
			p.calculateMinDistances ();
			
			MappingCommSolver satSolver = new MappingCommSolver (g, hsdf, solutions, p);			
			satSolver.generateMappingConstraints ();
			satSolver.totalClustersUsed (processedArgs.processorConstraint);
			satSolver.generateSatCode (processedArgs.outputDirectory + "mapping.z3");						
			satSolver.pushContext ();
			
			WrkLoadCommCostParams explorationParams = new WrkLoadCommCostParams (g, solutions);
			explorationParams.setSolver (satSolver);			
			
			System.out.println ("Num Clusters : " + processedArgs.processorConstraint);			
			
			String outputDirectory = processedArgs.outputDirectory.concat ("cluster-"+Integer.toString (processedArgs.processorConstraint)+"/");
				
			GridBasedExploration paretoExplore = new GridBasedExploration (outputDirectory, 
						processedArgs.timeOutPerQueryInSeconds, 
						processedArgs.totalTimeOutInSeconds, explorationParams);
				
			paretoExplore.explore ();
		}
	}	
}
