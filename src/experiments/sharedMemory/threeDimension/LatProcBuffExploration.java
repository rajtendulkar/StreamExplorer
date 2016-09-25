package experiments.sharedMemory.threeDimension;

import input.ParseApplicationGraph;
import input.CommandLineArgs;
import input.CommandLineArgs.SolverType;

import java.io.*;
import java.util.List;
import java.util.Map;

import output.GanttChart;

import platform.tilera.scheduleXML.NonPipelinedScheduleXml;

import exploration.parameters.threeDimension.LatProcBuffParams;
import exploration.paretoExploration.gridexploration.GridBasedExploration;

import solver.sharedMemory.combinedSolver.nonpipelined.MutExNonPipelinedScheduling;
import spdfcore.*;
import spdfcore.stanalys.*;

/**
 * Perform a three dimensional exploration of 
 * latency vs processors vs buffer-size for shared memory.
 * 
 * @author Pranav Tendulkar
 *
 */
public class LatProcBuffExploration 
{
	/**
	 * If true, the after exploration it will generate schedule XML files
	 * for every Pareto Point.
	 */
	private static boolean generateScheduleXML = true;
	/**
	 * If true, the after exploration it will generate gantt charts
	 * for every Pareto Point.
	 */
	private static boolean generateGanttCharts = true;
	
	/**
	 * Entry point method to perform a 3D exploration of 
	 * latency vs processors vs buffer-size for shared memory
	 * 
	 * @param args command line arguments
	 */
	public static void main (String[] args)
	{
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
		
		if (processedArgs.bufferAnalysis != true)
			throw new RuntimeException ("Buffer Analysis must be enabled for this exploration.");
		
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
			satSolver.generateSatCode (processedArgs.outputDirectory  + "scheduling.z3");
			
			LatProcBuffParams explorationParams = new LatProcBuffParams (g, solutions);
			explorationParams.setSolver (satSolver);
			
			// Initialize the upper bound on number of processors to be used
			if(processedArgs.processorConstraint != 0 && processedArgs.processorConstraint < explorationParams.getUpperBounds()[1])
				explorationParams.setUpperBound(1, processedArgs.processorConstraint);
			
			GridBasedExploration paretoExplore = new GridBasedExploration (processedArgs.outputDirectory, 
					processedArgs.timeOutPerQueryInSeconds, 
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
		else
			throw new RuntimeException ("Unknown Solver Type.");
		
	}	
}
