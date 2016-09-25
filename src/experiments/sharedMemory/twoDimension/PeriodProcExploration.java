package experiments.sharedMemory.twoDimension;

import input.ParseApplicationGraph;
import input.CommandLineArgs;
import input.CommandLineArgs.SolverType;

import java.io.*;
import java.util.*;

import output.GanttChart;
import platform.tilera.scheduleXML.PipelinedScheduleXml;

import exploration.parameters.twoDimension.*;
import exploration.paretoExploration.gridexploration.GridBasedExploration;

import solver.sharedMemory.combinedSolver.pipelined.MutExPipelinedScheduling;
import solver.sharedMemory.combinedSolver.pipelined.UnfoldingScheduling;
import spdfcore.*;
import spdfcore.stanalys.*;
import graphanalysis.CalculateBounds;

/**
 * Perform Period vs Processors exploration for a shared memory architecture.
 * 
 * @author Pranav Tendulkar
 *
 */
public class PeriodProcExploration
{
	/**
	 * Generate a schedule XML for Tilera platform
	 * 
	 * @param g application graph
	 * @param solutions solutions to application graph
	 * @param paretoPoints list of models of pareto points
	 * @param processedArgs command line arguments
	 */
	private static void generateScheduleXml (Graph g, Solutions solutions, List<Map<String, String>>paretoPoints, CommandLineArgs processedArgs)
	{
		for(int i=0;i<paretoPoints.size();i++)
		{
			Map<String, String> model = paretoPoints.get(i);
			String outputDirectory = processedArgs.outputDirectory + "solution_" + Integer.toString(i) + "/";
			
	        File directory = new File (outputDirectory);
	        directory.mkdirs ();
			
			GanttChart chart = new GanttChart ();
			chart.textRotationAngle = 90;
			chart.plotChart (model, g, outputDirectory+"chart.pdf");
	        PipelinedScheduleXml tileraSchedXml = new PipelinedScheduleXml();
	        tileraSchedXml.generateSolutionXml (outputDirectory+"schedule.xml", g, solutions, model);
		}
	}
	
	/**
	 * Entry point method to perform Period vs Processors exploration for a shared memory architecture.
	 * 
	 * @param args command line arguments
	 */
	public static void main (String[] args)
	{
		// String solverTactics[] = { "simplify", "purify-arith", "elim-term-ite",
		//		  "reduce-args", "propagate-values", 
		//		 "solve-eqs", "symmetry-reduce", "smt", "sat","sat-preprocess"};		
		
		CommandLineArgs processedArgs = new CommandLineArgs (args);
		
		SolverType solverType = processedArgs.solver;
		
		// Create the output Directory first if it doesn't exist
        File directory = new File (processedArgs.outputDirectory);
        directory.mkdirs ();
		
		ParseApplicationGraph xmlParse = new ParseApplicationGraph ();
		Graph g = xmlParse.parseSingleGraphXml (processedArgs.applicationGraphFileName);
		
		Solutions solutions;
		GraphExpressions expressions = new GraphExpressions ();
		expressions.parse (g);
		solutions = new Solutions ();
		solutions.setThrowExceptionFlag (false);
		solutions.solve (g, expressions);
		
		CalculateBounds bounds = new CalculateBounds (g,solutions);
		int maxLatency = bounds.findGraphMaxLatency ();
		
		if (solverType == SolverType.PERIOD_LOCALITY)
		{
			PeriodProcParams explorationParams = new PeriodProcParams (g, solutions);

			MutExPipelinedScheduling satSolver = new MutExPipelinedScheduling (g);
			// satSolver.setTacTicSolver (solverTactics);
			satSolver.disablePrimes = processedArgs.disablePrime;
			satSolver.graphSymmetry = processedArgs.graphSymmetry;
			satSolver.processorSymmetry = processedArgs.processorSymmetry;
			satSolver.bufferAnalysis = processedArgs.bufferAnalysis; 
			satSolver.bufferAnalysisWithFunctions = processedArgs.bufferAnalysisWithFunctions; 
			satSolver.leftEdgeAlgorithm = processedArgs.leftEdge;
			satSolver.mutualExclusionGraphAnalysis = processedArgs.mutualExclusionGraphAnalysis;
			satSolver.typeDifferentiateAlgo = processedArgs.typeDifferentiateAlgo;
			satSolver.omegaAnalysis = processedArgs.omegaAnalysis;
			satSolver.periodSymmetry = processedArgs.periodSymmetry;
			
			if ((processedArgs.maxLatencyScalingFactor <= 0) || (processedArgs.maxLatencyScalingFactor > 1))
				throw new RuntimeException ("The Latency Scaling Factor should > 0 and <= 1 ");
			
			satSolver.assertPipelineConstraints ();
			Double latency = Math.ceil (maxLatency * processedArgs.maxLatencyScalingFactor);
			satSolver.generateLatencyConstraint (latency.intValue ());
			satSolver.generateSatCode (processedArgs.outputDirectory  + "scheduling.z3");
			explorationParams.setSolver (satSolver);
			
			GridBasedExploration paretoExplorer = new GridBasedExploration (processedArgs.outputDirectory, processedArgs.timeOutPerQueryInSeconds, 
												processedArgs.totalTimeOutInSeconds, explorationParams);
			paretoExplorer.explore ();
			generateScheduleXml (g, solutions, paretoExplorer.getParetoModels(), processedArgs);
		}
		else if (solverType == SolverType.UNFOLDING_SOLVER)
		{
			PeriodProcUnfolding explorationParams = new PeriodProcUnfolding (g, solutions);
			explorationParams.maxLatencyScalingFactor = processedArgs.maxLatencyScalingFactor;
			
			UnfoldingScheduling satSolver = new UnfoldingScheduling (g);
			// satSolver.setTacTicSolver (solverTactics);
			
			satSolver.graphSymmetry = processedArgs.graphSymmetry;
			satSolver.processorSymmetry = processedArgs.processorSymmetry;
			
			explorationParams.setSolver (satSolver);
			
			GridBasedExploration paretoExplorer = new GridBasedExploration (processedArgs.outputDirectory, processedArgs.timeOutPerQueryInSeconds, 
												processedArgs.totalTimeOutInSeconds, explorationParams);
			paretoExplorer.explore ();
			generateScheduleXml (g, solutions, paretoExplorer.getParetoModels(), processedArgs);
		}		
		else
			throw new RuntimeException ("Unknown Solver Type : " + solverType.toString ());		
	}	
}
