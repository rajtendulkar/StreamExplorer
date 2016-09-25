package experiments.sharedMemory.oneDimension;
import exploration.oneDimensionExploration.BinarySearchOneDim;
import exploration.parameters.oneDimension.*;
import graphanalysis.*;

import input.ParseApplicationGraph;
import input.CommandLineArgs;
import input.CommandLineArgs.SolverType;

import java.io.*;

import output.DotGraph;

import solver.sharedMemory.combinedSolver.pipelined.MutExPipelinedScheduling;
import solver.sharedMemory.combinedSolver.pipelined.UnfoldingScheduling;
import spdfcore.*;
import spdfcore.stanalys.*;


/**
 * One dimension exploration of period for shared memory architecture
 * 
 * @author Pranav Tendulkar
 *
 */
public class SinglePeriodQuery 
{
	
	/**
	 * Entry point method for period exploration for shared memory architecture
	 * 
	 * @param args command line arguments
	 */
	public static void main (String[] args) 
	{
		//String solverTactics[] = { "simplify", "purify-arith", "elim-term-ite",
		//		  "reduce-args", "propagate-values", 
		//		 "solve-eqs", "symmetry-reduce", "smt", "sat","sat-preprocess"};
		
		CommandLineArgs processedArgs = new CommandLineArgs (args);
		
		String graphName = processedArgs.extractNameFromPath(processedArgs.applicationGraphFileName);
		
		processedArgs.outputDirectory = processedArgs.outputDirectory.concat (graphName+"/");
		processedArgs.printConfig ();
		
		SolverType solverType = processedArgs.solver;
				
		File directory = new File (processedArgs.outputDirectory);
		directory.mkdirs ();

		ParseApplicationGraph xmlParse = new ParseApplicationGraph ();
		Graph g = xmlParse.parseSingleGraphXml (processedArgs.applicationGraphFileName);
		
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
		
		CalculateBounds bounds = new CalculateBounds (g,solutions);
		if ((processedArgs.maxLatencyScalingFactor <= 0) || (processedArgs.maxLatencyScalingFactor > 1))
			throw new RuntimeException ("The Latency Scaling Factor should > 0 and <= 1 ");
		
		Double latency = Math.ceil (bounds.findGraphMaxLatency () * processedArgs.maxLatencyScalingFactor);
		
		if (solverType == SolverType.PERIOD_LOCALITY)
		{			
			// int maxLatency = bounds.findGraphMaxLatency ();		
			MutExPipelinedScheduling satSolver = new MutExPipelinedScheduling (g);
			// satSolver.setTacTicSolver (solverTactics);
			satSolver.disablePrimes = processedArgs.disablePrime;
			satSolver.typeDifferentiateAlgo = processedArgs.typeDifferentiateAlgo;
			satSolver.omegaAnalysis = processedArgs.omegaAnalysis;
			satSolver.graphSymmetry = processedArgs.graphSymmetry;
			satSolver.processorSymmetry = processedArgs.processorSymmetry;
			satSolver.bufferAnalysis = processedArgs.bufferAnalysis; 
			satSolver.bufferAnalysisWithFunctions = processedArgs.bufferAnalysisWithFunctions; 
			satSolver.leftEdgeAlgorithm = processedArgs.leftEdge;
			satSolver.mutualExclusionGraphAnalysis = processedArgs.mutualExclusionGraphAnalysis;
			satSolver.periodSymmetry = processedArgs.periodSymmetry;		
			
			satSolver.assertPipelineConstraints ();
			satSolver.generateLatencyConstraint (latency.intValue ());
			satSolver.generateProcessorConstraint (processedArgs.processorConstraint);
			
			PeriodParams params = new PeriodParams (g, solutions);
			params.setSolver (satSolver);
			
			BinarySearchOneDim oneDimExplorer = new BinarySearchOneDim (processedArgs.outputDirectory, 
					processedArgs.timeOutPerQueryInSeconds, 
					processedArgs.totalTimeOutInSeconds, params);
			oneDimExplorer.explore ();
		}
		else if (solverType == SolverType.UNFOLDING_SOLVER)
		{
			// I have to make exception to this julien's constraints.
			// this is because of the way it is implemented, i have to reset the solver and set some things
			// for every query. that is the reason why also, it has another exploration.params.
			UnfoldingScheduling satSolver = new UnfoldingScheduling (g);
			satSolver.graphSymmetry = processedArgs.graphSymmetry;
			satSolver.processorSymmetry = processedArgs.processorSymmetry;
			// satSolver.setTacTicSolver (solverTactics);
			// satSolver.pushContext ();	
			satSolver.resetSolver ();
			satSolver.assertPipelineConstraints ();
			satSolver.generateLatencyConstraint (latency.intValue ());
			satSolver.generateProcessorConstraint (processedArgs.processorConstraint);
					
			PeriodUnfoldingParams params = new PeriodUnfoldingParams (g, solutions);
			params.setSolver (satSolver);
			params.numProcessors = processedArgs.processorConstraint;
			params.maxLatencyScalingFactor = processedArgs.maxLatencyScalingFactor;
			
			BinarySearchOneDim oneDimExplorer = new BinarySearchOneDim (processedArgs.outputDirectory, 
					processedArgs.timeOutPerQueryInSeconds, 
					processedArgs.totalTimeOutInSeconds, params);
			oneDimExplorer.explore ();
		}
		else
			throw new RuntimeException ("Unknown Solver Type. " + solverType.toString ());
	}
}
