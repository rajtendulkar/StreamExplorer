package experiments.sharedMemory.oneDimension;
import exploration.oneDimensionExploration.BinarySearchOneDim;
import exploration.parameters.oneDimension.LatencyParams;
import graphanalysis.*;

import input.ParseApplicationGraph;
import input.CommandLineArgs;
import input.CommandLineArgs.SolverType;

import java.io.*;

import output.DotGraph;

import solver.sharedMemory.combinedSolver.nonpipelined.MatrixSolver;
import solver.sharedMemory.combinedSolver.nonpipelined.MutExNonPipelinedScheduling;
import spdfcore.*;
import spdfcore.stanalys.*;


/**
 * One dimension exploration of latency for shared memory architecture 
 * @author Pranav Tendulkar
 */
public class SingleLatencyQuery 
{	
	/**
	 * Entry point method for latency exploration for shared memory architecture
	 * 
	 * @param args command line arguments
	 */
	public static void main (String[] args) 
	{
		CommandLineArgs processedArgs = new CommandLineArgs (args);
		
		String graphName = processedArgs.extractNameFromPath(processedArgs.applicationGraphFileName);
		
		processedArgs.outputDirectory = processedArgs.outputDirectory.concat (graphName+"/");
		processedArgs.printConfig ();
		
		SolverType solverType = processedArgs.solver;

		File directory = new File (processedArgs.outputDirectory);
		directory.mkdirs ();

		ParseApplicationGraph xmlParse = new ParseApplicationGraph ();
		Graph g = xmlParse.parseSingleGraphXml (processedArgs.applicationGraphFileName );
		
		// Print HSDF Graphs
		if (processedArgs.printHsdf == true)
		{
			TransformSDFtoHSDF toHSDF = new TransformSDFtoHSDF ();
			Graph hsdf = toHSDF.convertSDFtoHSDFWithUniqueChannels (g);
			DotGraph dotG = new DotGraph ();
			dotG.generateDotFromGraph (hsdf, processedArgs.outputDirectory+"/out.dot");
		}
		
		Solutions solutions;
		GraphExpressions expressions = new GraphExpressions ();
		expressions.parse (g);
		solutions = new Solutions ();
		solutions.setThrowExceptionFlag (false);
		solutions.solve (g, expressions);
		
		LatencyParams params=null;
		
		// We add any new type of solver over here to do the single dimension latency exploration.
		if (solverType == SolverType.MUTUAL_EXCLUSION)
		{		
			MutExNonPipelinedScheduling satSolver = new MutExNonPipelinedScheduling (g);

			satSolver.graphSymmetry = processedArgs.graphSymmetry;
			satSolver.processorSymmetry = processedArgs.processorSymmetry;
			satSolver.bufferAnalysis = processedArgs.bufferAnalysis; 
			satSolver.bufferAnalysisWithFunctions = processedArgs.bufferAnalysisWithFunctions; 
			satSolver.leftEdgeAlgorithm = processedArgs.leftEdge;
			satSolver.mutualExclusionGraphAnalysis = processedArgs.mutualExclusionGraphAnalysis;
	
			satSolver.assertNonPipelineConstraints ();
			satSolver.generateProcessorConstraint (processedArgs.processorConstraint);
			satSolver.pushContext ();
			satSolver.generateSatCode (processedArgs.outputDirectory  + "scheduling.z3");
			
			params = new LatencyParams (g, solutions);
			params.setSolver (satSolver);			
		}
		else if (solverType == SolverType.MATRIX_SOLVER)
		{
			MatrixSolver satSolver = new MatrixSolver (g);
			
			satSolver.processorSymmetry = processedArgs.processorSymmetry;
			satSolver.graphSymmetry = processedArgs.graphSymmetry;
			satSolver.useMaxFunction = processedArgs.useMaxFunction;
			satSolver.useQuantifier = processedArgs.useQuantifier;

			satSolver.assertNonPipelineConstraints ();
			satSolver.generateProcessorConstraint (processedArgs.processorConstraint);
			satSolver.pushContext ();
			satSolver.generateSatCode (processedArgs.outputDirectory  + "scheduling.z3");
			
			params = new LatencyParams (g, solutions);
			params.setSolver (satSolver);			
		}
		else
			throw new RuntimeException ("Unknown Solve Type !!");		
		
		BinarySearchOneDim oneDimExplorer = new BinarySearchOneDim (processedArgs.outputDirectory, 
				processedArgs.timeOutPerQueryInSeconds, 
				processedArgs.totalTimeOutInSeconds, params);
		oneDimExplorer.explore ();
	}
}
