package designflow;

import input.CommandLineArgs;

import java.io.*;
import java.util.*;

import output.DotGraph;
import designflow.DesignFlowSolution.Partition;
import exploration.oneDimensionExploration.BinarySearchOneDim;
import exploration.parameters.oneDimension.*;
import exploration.parameters.threeDimension.MaxwrkloadCommClusterParams;
import exploration.paretoExploration.gridexploration.GridBasedExploration;
import graphanalysis.TransformSDFtoHSDF;
import platform.kalray.scheduleXML.NonPipelinedScheduleXml;
import platform.model.*;
import solver.distributedMemory.constraints.SchedulingConstraints;
import solver.distributedMemory.partitioning.PartitionSolverSDF;
import solver.distributedMemory.placement.GenericPlacementSolver;
import solver.distributedMemory.scheduling.ClusterMutExclPipelined;
import spdfcore.*;
import spdfcore.stanalys.*;

/**
 * Pipelined scheduling using Design Flow.
 * 
 * TODO: We have kept the partitioning and placement
 * step same as non-pipelined scheduling. The scheduling
 * class is not yet completed with modification to support
 * pipelined scheduling. The aim is to implement period 
 * locality in the scheduling class.
 * 
 * @author Pranav Tendulkar
 *
 */
public class PipelinedScheduling 
{
	/**
	 * Application Graph SDF
	 */
	private Graph graph;
	
	/**
	 * Equivalent HSDF graph of application graph
	 */
	private Graph hsdfGraph;
	
	/**
	 * Solutions of application graph 
	 */
	private Solutions graphSolutions;
	
	/**
	 * Target Platform model 
	 */
	private Platform platform;
	
	/**
	 * Command line arguments 
	 */
	private CommandLineArgs processedArgs;
	
	/**
	 * List of design flow solutions 
	 */
	private List<DesignFlowSolution> deploymentSolutions;
	
	/**
	 * Size of FIFO status word transferred from destination to source
	 * via DMA. This is implementation specific status word. 
	 */
	public static final int fifoStatusSizeInBytes = 8;
	
	/**
	 * Build a pipelined scheduling object
	 * 
	 * @param g application graph
	 * @param p platform model
	 * @param args commandline arguments
	 */
	public PipelinedScheduling (Graph g, Platform p, CommandLineArgs args)
	{
		graph = g;
		platform = p;
		processedArgs = args;
		
		deploymentSolutions = new ArrayList<DesignFlowSolution>();
		
		GraphExpressions expressions = new GraphExpressions ();
		expressions.parse (graph);
		graphSolutions = new Solutions ();
		graphSolutions.setThrowExceptionFlag (false);	    		
		graphSolutions.solve (graph, expressions);
		
		TransformSDFtoHSDF toHSDF = new TransformSDFtoHSDF ();
		hsdfGraph = toHSDF.convertSDFtoHSDFWithUniqueChannels (g);
		
		// Create the output Directory first if it doesn't exist
        File directory = new File (processedArgs.outputDirectory);
        directory.mkdirs ();		
	}	
	
	/**
	 * Perform the scheduling step of the design flow. 
	 */
	public void performApplicationScheduling()
	{
		NonPipelinedScheduleXml generateXml = new NonPipelinedScheduleXml();
		List<DesignFlowSolution> newSolutionList = new ArrayList<DesignFlowSolution>();
		// We have the placement now. 
		// We should perform scheduling and buffer-sizing.
		// int i = 2;
		for(int i=0;i<deploymentSolutions.size();i++)
		{
			System.out.println("Exploring Schedule : " + i);
			String outputDirectory = processedArgs.outputDirectory.concat("scheduling/schedule_");
			outputDirectory += (Integer.toString(i) + "/");
			
			DesignFlowSolution designFlowSolution = deploymentSolutions.get(i);			
			SchedulingConstraints schedConstraints = designFlowSolution.getMapping().getSchedulingConstraints();
			
			Graph partitionAwareGraph = designFlowSolution.getpartitionAwareGraph();
			Solutions partitionGraphSolutions = designFlowSolution.getPartitionAwareGraphSolutions();
			
			// Now we have constraints on where actors can be allocated. Let us do the scheduling !
			ClusterMutExclPipelined schedulingSolver = new ClusterMutExclPipelined(graph, hsdfGraph, graphSolutions, 
					partitionAwareGraph, designFlowSolution.getPartitionAwareHsdf(), partitionGraphSolutions, 
					platform, schedConstraints);
			schedulingSolver.graphSymmetry = true;
			schedulingSolver.processorSymmetry = true;
			
			boolean oneDimExploration = true;
			
			// We do a latency minimization.
			if(oneDimExploration == true)
			{
				schedulingSolver.bufferAnalysis = false;
				schedulingSolver.assertPipelineConstraints();
				schedulingSolver.pushContext();
				schedulingSolver.generateSatCode(outputDirectory + "scheduling.z3");
				
				PeriodParams periodParams = new PeriodParams (designFlowSolution.getpartitionAwareGraph(), partitionGraphSolutions);
				periodParams.setSolver(schedulingSolver);
				
				BinarySearchOneDim oneDimExplorer = new BinarySearchOneDim (
									outputDirectory,
									processedArgs.timeOutPerQueryInSeconds, 
									processedArgs.totalTimeOutInSeconds, periodParams);
				oneDimExplorer.explore();
				// singleExploration.readExploredPoints(outputDirectory);
				
				Map<String,String> model = oneDimExplorer.getLeastSatPointModel();
				designFlowSolution.setSchedule(schedulingSolver.modelToSchedule(model, designFlowSolution));
				
				// Generate the XML.
				generateXml.generateSolutionXml(outputDirectory+"solution.xml", graph, graphSolutions, platform, designFlowSolution);
				
				// Generate the Gantt Chart
				schedulingSolver.modelToGantt(model, outputDirectory+"solution.pdf");
				
				newSolutionList.add(designFlowSolution);				
			}
			else
			{
			}			
		}
		
		deploymentSolutions.clear();
		deploymentSolutions.addAll(newSolutionList);
	}
	
	/**
	 * Perform the placement step of the design flow.
	 */
	public void performApplicationPlacement()
	{
		for(int i=0;i<deploymentSolutions.size();i++)
		{
			DesignFlowSolution designFlowSolution = deploymentSolutions.get(i);
			Partition partition = designFlowSolution.getPartition();
			String outputDirectory = processedArgs.outputDirectory.concat("placement/partition_");
			outputDirectory += (Integer.toString(i) + "/");
			GenericPlacementSolver placementSolver = new GenericPlacementSolver(partition, platform);
			placementSolver.generatePlacementConstraints();
			placementSolver.pushContext();
			placementSolver.generateSatCode(outputDirectory + "placement.z3");
			
			CommCostParams params = new CommCostParams (graph, graphSolutions, platform);
			params.setSolver(placementSolver);
			
            BinarySearchOneDim explorer = new BinarySearchOneDim (outputDirectory, 
                    processedArgs.timeOutPerQueryInSeconds, 
                    processedArgs.totalTimeOutInSeconds, params);            
            // TODO: a temporary hack. instead of performing exploration everytime, read old results.
            // explorer.readExploredPoints(outputDirectory);
            explorer.explore ();
            
            Map<String, String> model = explorer.getLeastSatPointModel ();
            designFlowSolution.setMapping(placementSolver.modelToMapping(model, designFlowSolution));
            designFlowSolution.getMapping().resolveDmaTaskExecutionTime();
		}		
	}
	
	/**
	 * Perform the partitioning step of the design flow.
	 */
	public void performApplicationPartitioningThreeDim ()
	{
		String partitionResultDirectory = processedArgs.outputDirectory.concat("partition/");
		PartitionSolverSDF partitionSolver = new PartitionSolverSDF (graph, hsdfGraph, 
															graphSolutions, platform, 
															partitionResultDirectory);
		
		partitionSolver.generatePartitioningConstraints();
		partitionSolver.generateSatCode(partitionResultDirectory + "partitioning.z3");
		partitionSolver.pushContext();
		
		// Suppose we have only 4 actors, and number of clusters is 16, 
		// then max partitions we can have will be 4.
		int numPartitions = platform.getNumClusters();
		if(graph.countActors() < numPartitions)
			numPartitions = graph.countActors();

		// we can do a 3D exploration of max workload per cluster, comm cost and no. of clusters used.
		MaxwrkloadCommClusterParams explorationParams = new MaxwrkloadCommClusterParams(graph, graphSolutions, numPartitions);
		
		// We can do a 3D exploration of workload imbalance, comm cost and no. of clusters used.
		// WorkloadCommClusterParams explorationParams = new WorkloadCommClusterParams (graph, graphSolutions, platform.getNumClusters());
		explorationParams.setSolver (partitionSolver);
		
		GridBasedExploration paretoExplore = new GridBasedExploration (partitionResultDirectory, 
									processedArgs.timeOutPerQueryInSeconds, 
									processedArgs.totalTimeOutInSeconds, explorationParams);

		// TODO: a temporary hack. instead of performing exploration everytime, read old results.
		// paretoExplore.readExploredPoints(partitionResultDirectory);
		paretoExplore.explore ();
		
		int count=0;
		List<Map<String, String>>paretoModels = paretoExplore.getParetoModels();
		for(Map<String, String> model : paretoModels)
		{
			DesignFlowSolution designFlowSolution = new DesignFlowSolution(graph, hsdfGraph, graphSolutions, platform);
			partitionSolver.setDesignFlowSolution(designFlowSolution, model);
			
			DotGraph dotG = new DotGraph ();
			dotG.generateDotFromGraph (designFlowSolution.getpartitionAwareGraph(), 
					partitionResultDirectory + "partitionAwareGraph_"+Integer.toString(count++)+".dot");
			
			deploymentSolutions.add(designFlowSolution);
		}
	}
}
