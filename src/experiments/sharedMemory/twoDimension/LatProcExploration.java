package experiments.sharedMemory.twoDimension;

import input.ParseApplicationGraph;
import input.CommandLineArgs;
import input.CommandLineArgs.SolverType;

import java.io.*;
import java.util.List;
import java.util.Map;

import output.GanttChart;

import platform.tilera.scheduleXML.NonPipelinedScheduleXml;

import exploration.parameters.twoDimension.LatProcParams;
import exploration.paretoExploration.gridexploration.GridBasedExploration;

import solver.sharedMemory.combinedSolver.nonpipelined.*;
import spdfcore.*;
import spdfcore.stanalys.*;

/**
 * Perform Latency vs Processor used exploration for shared memory architecture.
 * 
 * @author Pranav Tendulkar
 *
 */
public class LatProcExploration 
{	
	/**
	 * If true, the after exploration it will generate schedule XML files
	 * for every Pareto Point.
	 */
	private static boolean generateScheduleXML = true;
	/**
	 * If true, the after exploration it will generate Gantt charts
	 * for every Pareto Point.
	 */
	private static boolean generateGanttCharts = true;
	
	/**
	 * Entry point method to perform Latency vs Processor used 
	 * exploration for shared memory architecture.
	 * 
	 * @param args command line arguments
	 */
	public static void main (String[] args)
	{
		//String solverTactics[] = { "simplify", "purify-arith", "elim-term-ite",
		//		  "reduce-args", "propagate-values", 
		//		 "solve-eqs", "symmetry-reduce", "smt", "sat","sat-preprocess"};		
		
		CommandLineArgs processedArgs = new CommandLineArgs (args);
		
		processedArgs.printConfig ();
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
		
		LatProcParams explorationParams = new LatProcParams (g, solutions);
		
		// Initialize the upper bound on number of processors to be used
		if(processedArgs.processorConstraint != 0 && processedArgs.processorConstraint < explorationParams.getUpperBounds()[1])
			explorationParams.setUpperBound(1, processedArgs.processorConstraint);
		
		if (solverType == SolverType.MUTUAL_EXCLUSION)
		{		
			MutExNonPipelinedScheduling satSolver = new MutExNonPipelinedScheduling (g);
			// satSolver.setTacTicSolver (solverTactics);
			satSolver.graphSymmetry = processedArgs.graphSymmetry;
			satSolver.processorSymmetry = processedArgs.processorSymmetry;
			satSolver.bufferAnalysis = processedArgs.bufferAnalysis; 
			satSolver.bufferAnalysisWithFunctions = processedArgs.bufferAnalysisWithFunctions; 
			satSolver.leftEdgeAlgorithm = processedArgs.leftEdge;
			satSolver.mutualExclusionGraphAnalysis = processedArgs.mutualExclusionGraphAnalysis;
			satSolver.assertNonPipelineConstraints ();
			satSolver.generateSatCode (processedArgs.outputDirectory  + "scheduling.z3");
			
			explorationParams.setSolver (satSolver);			
		}
		else if (solverType == SolverType.MATRIX_SOLVER)
		{
			MatrixSolver satSolver = new MatrixSolver (g);
			// satSolver.setTacTicSolver (solverTactics);
			satSolver.processorSymmetry = processedArgs.processorSymmetry;
			satSolver.graphSymmetry = processedArgs.graphSymmetry;
			satSolver.useMaxFunction = processedArgs.useMaxFunction;
			satSolver.useQuantifier = processedArgs.useQuantifier; 
			
			satSolver.assertNonPipelineConstraints ();
			satSolver.generateSatCode (processedArgs.outputDirectory  + "scheduling.z3");
			explorationParams.setSolver (satSolver);
		}
		else
			throw new RuntimeException ("Unknown Solver Type.");
		
		// Perform Grid-based design space exploration.
		GridBasedExploration paretoExplore = new GridBasedExploration (processedArgs.outputDirectory, processedArgs.timeOutPerQueryInSeconds, 
												processedArgs.totalTimeOutInSeconds, explorationParams);
		
		paretoExplore.explore ();
		// paretoExplore.readExploredPoints(processedArgs.outputDirectory);
		
		if (generateScheduleXML == true)
		{
			NonPipelinedScheduleXml generateSchedXML = new NonPipelinedScheduleXml();
			GanttChart ganttChart = new GanttChart ();
							
			List<Map<String,String>>models = paretoExplore.getParetoModels();
			for(int i=0;i<models.size();i++)
			{
				String xmlDirStr = processedArgs.outputDirectory + "solution_" + Integer.toString(i) + "/";
				// Create directory
				File xmlDir = new File (xmlDirStr);
				xmlDir.mkdirs ();
				
				generateSchedXML.generateSolutionXml(xmlDirStr + "schedule.xml", g, solutions, models.get(i));
				if(generateGanttCharts == true)
					ganttChart.plotChart(models.get(i), g, xmlDirStr + "schedule.pdf");
			}
		}
	}	
}
