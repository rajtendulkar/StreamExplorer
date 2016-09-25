package designflow;

import input.CommandLineArgs;

import java.io.*;
import java.util.*;

import output.DotGraph;

import designflow.DesignFlowSolution.Partition;
import exploration.oneDimensionExploration.BinarySearchOneDim;
import exploration.parameters.oneDimension.*;
import exploration.parameters.threeDimension.MaxwrkloadCommClusterParams;
import exploration.parameters.twoDimension.*;
import exploration.paretoExploration.gridexploration.GridBasedExploration;
import graphanalysis.TransformSDFtoHSDF;
import platform.kalray.scheduleXML.NonPipelinedScheduleXml;
import platform.model.*;
import solver.distributedMemory.constraints.SchedulingConstraints;
import solver.distributedMemory.partitioning.PartitionSolverSDF;
import solver.distributedMemory.placement.GenericPlacementSolver;
import solver.distributedMemory.scheduling.ClusterMutExclNonPipelined;
import spdfcore.*;
import spdfcore.stanalys.*;

/**
 * Non-pipelined scheduling using Design Flow.
 * 
 * @author Pranav Tendulkar
 */
public class NonPipelinedScheduling 
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
	 * Build a non-pipelined scheduling object
	 * 
	 * @param g application graph
	 * @param p platform model
	 * @param args commandline arguments
	 */
	public NonPipelinedScheduling (Graph g, Platform p, CommandLineArgs args)
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
	 * I need to update the buffer exploration parameters
	 * because, the fifo status size will be calculated into buffer.
	 * however it is not taken into account in constraints. 
	 * so I will have to change the buffer bounds of exploration.
	 * 
	 * @param partitionAwareGraph partition aware graph
	 * @param explorationParams design space exploration parameters
	 * @param schedConstraints scheduling constraints
	 */
	private void updateLatBuffExplParams(Graph partitionAwareGraph, LatBuffParams explorationParams, SchedulingConstraints schedConstraints)
	{
		HashMap<Cluster, int[]> clusterMap = new HashMap<Cluster, int[]>();
		int minBufferSize = Integer.MIN_VALUE;
		int maxBufferSize = Integer.MIN_VALUE;

		int commCostGranularity = Integer.MAX_VALUE;

		Iterator<Channel> iterChnnl = graph.getChannels ();
		while (iterChnnl.hasNext ())
		{
			Channel chnnl = iterChnnl.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();

			int srcRepCount = graphSolutions.getSolution (srcActor).returnNumber ();
			int srcRate = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
			int dstRate = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());
			int initialTokens = chnnl.getInitialTokens();
			int tokenSize = chnnl.getTokenSize();

			if (commCostGranularity > (srcRate * tokenSize))
				commCostGranularity = (srcRate * tokenSize);

			if (commCostGranularity > (dstRate * tokenSize))
				commCostGranularity = (dstRate * tokenSize);

			// We check if this channel is split between the clusters.
			// First we calculate GCD.
			int ratesGcd = Integer.parseInt (Expression.gcd (new Expression (Integer.toString (srcRate)), 
					new Expression (Integer.toString (dstRate))).toString());

			int bufferMinBound =  ((srcRate + dstRate - ratesGcd + (initialTokens % ratesGcd))*tokenSize);
			int bufferMaxBound = tokenSize * (chnnl.getInitialTokens () + (srcRate * srcRepCount));

			Cluster srcCluster = schedConstraints.getActorAllocatedCluster(srcActor.getName());
			Cluster dstCluster = schedConstraints.getActorAllocatedCluster(dstActor.getName());

			int temp1[] = new int[2];
			temp1[0] = 0;
			temp1[1] = 0;
			if(clusterMap.containsKey(srcCluster) == false)
				clusterMap.put(srcCluster, temp1);

			clusterMap.get(srcCluster)[0] += bufferMinBound;
			clusterMap.get(srcCluster)[1] += bufferMaxBound;

			if(srcCluster !=  dstCluster)
			{				
				int temp2[] = new int[2];
				temp2[0] = 0;
				temp2[1] = 0;
				if(clusterMap.containsKey(dstCluster) == false)
					clusterMap.put(dstCluster, temp2);				

				clusterMap.get(dstCluster)[0] += bufferMinBound;
				clusterMap.get(dstCluster)[1] += bufferMaxBound;				
			}
		}

		for(Cluster cluster : clusterMap.keySet())
		{
			if(clusterMap.get(cluster)[0] >  minBufferSize)
				minBufferSize = clusterMap.get(cluster)[0];

			if(clusterMap.get(cluster)[1] >  maxBufferSize)
				maxBufferSize = clusterMap.get(cluster)[1];			
		}

		explorationParams.setLowerBound(1, minBufferSize);
		explorationParams.setUpperBound(1, maxBufferSize);

		// Set the exploration granularity.		
		// explorationParams.setExplorationGranularity(0, 1);
		explorationParams.setExplorationGranularity(0, explorationParams.getExplorationGranularity(0)/2);
		explorationParams.setExplorationGranularity(1, commCostGranularity/2);
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
			String schedResultsDirectory = processedArgs.outputDirectory.concat("scheduling/schedule_");
			schedResultsDirectory += (Integer.toString(i) + "/");
			
			// Create the output Directory first if it doesn't exist
			File dir = new File (schedResultsDirectory);
			dir.mkdirs ();

			DesignFlowSolution designFlowSolution = deploymentSolutions.get(i);			
			SchedulingConstraints schedConstraints = designFlowSolution.getMapping().getSchedulingConstraints();

			Graph partitionAwareGraph = designFlowSolution.getpartitionAwareGraph();
			Solutions partitionGraphSolutions = designFlowSolution.getPartitionAwareGraphSolutions();

			// Now we have constraints on where actors can be allocated. Let us do the scheduling !
			ClusterMutExclNonPipelined schedulingSolver = new ClusterMutExclNonPipelined(graph, hsdfGraph, graphSolutions, 
					partitionAwareGraph, designFlowSolution.getPartitionAwareHsdf(), partitionGraphSolutions, 
					platform, schedResultsDirectory, schedConstraints);
			schedulingSolver.graphSymmetry = true;
			schedulingSolver.processorSymmetry = true;
			boolean oneDimExploration = false;

			// We do a latency minimization.
			if(oneDimExploration == true)
			{
				schedulingSolver.bufferAnalysis = false;
				schedulingSolver.assertNonPipelineConstraints();
				schedulingSolver.pushContext();
				schedulingSolver.generateSatCode(schedResultsDirectory + "scheduling.z3");

				LatencyParams latencyParams = new LatencyParams (designFlowSolution.getpartitionAwareGraph(), partitionGraphSolutions);
				latencyParams.setSolver(schedulingSolver);

				BinarySearchOneDim oneDimExplorer = new BinarySearchOneDim (
						schedResultsDirectory,
						processedArgs.timeOutPerQueryInSeconds, 
						processedArgs.totalTimeOutInSeconds, latencyParams);
				oneDimExplorer.explore();
				// singleExploration.readExploredPoints(outputDirectory);

				Map<String,String> model = oneDimExplorer.getLeastSatPointModel();
				designFlowSolution.setSchedule(schedulingSolver.modelToSchedule(model, designFlowSolution));

				// Generate the XML.
				generateXml.generateSolutionXml(schedResultsDirectory+"solution.xml", graph, graphSolutions, platform, designFlowSolution);

				// Generate the Gantt Chart
				schedulingSolver.modelToGantt(model, schedResultsDirectory+"solution.pdf");

				newSolutionList.add(designFlowSolution);				
			}
			else
			{
				schedulingSolver.bufferAnalysis = true;
				schedulingSolver.assertNonPipelineConstraints();
				schedulingSolver.pushContext();
				schedulingSolver.generateSatCode(schedResultsDirectory + "scheduling.z3");

				// We do a latency-buffer size exploration.
				LatBuffParams explorationParams = new LatBuffParams (designFlowSolution.getpartitionAwareGraph(), partitionGraphSolutions);
				updateLatBuffExplParams(partitionAwareGraph, explorationParams, schedConstraints);				
				explorationParams.setSolver(schedulingSolver);

				GridBasedExploration paretoExplore = new GridBasedExploration (schedResultsDirectory, processedArgs.timeOutPerQueryInSeconds, 
						processedArgs.totalTimeOutInSeconds, explorationParams);

				// TODO: a temporary hack. instead of performing exploration everytime, read old results.
				// paretoExplore.readExploredPoints(outputDirectory);
				paretoExplore.explore ();

				int solutionCount = 0;
				for(Map<String,String> model : paretoExplore.getParetoModels())
				{
					DesignFlowSolution newSolution = new DesignFlowSolution(designFlowSolution);
					newSolution.setSchedule(schedulingSolver.modelToSchedule(model, newSolution));
					newSolutionList.add(newSolution);

					String xmlOutputDir = schedResultsDirectory + "solution_" + Integer.toString(solutionCount++) + "/";

					// Create the output Directory first if it doesn't exist
					File directory = new File (xmlOutputDir);
					directory.mkdirs ();

					// Generate the XML.
					generateXml.generateSolutionXml(xmlOutputDir+"solution.xml", graph, graphSolutions, platform, newSolution);

					// Generate the Gantt Chart
					schedulingSolver.modelToGantt(model, xmlOutputDir+"solution.pdf");
				}				
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
			String placementResultDirectory = processedArgs.outputDirectory.concat("placement/partition_");
			placementResultDirectory += (Integer.toString(i) + "/");
			
			// Create the output Directory first if it doesn't exist
	        File directory = new File (placementResultDirectory);
	        directory.mkdirs ();
			
			GenericPlacementSolver placementSolver = new GenericPlacementSolver(partition, platform);
			placementSolver.generatePlacementConstraints();			
			placementSolver.generateSatCode(placementResultDirectory + "placement.z3");
			placementSolver.pushContext();

			CommCostParams params = new CommCostParams (graph, graphSolutions, platform);
			params.setSolver(placementSolver);

			BinarySearchOneDim oneDimExplorer = new BinarySearchOneDim (placementResultDirectory, 
					processedArgs.timeOutPerQueryInSeconds, 
					processedArgs.totalTimeOutInSeconds, params);            
			// Note: a temporary hack. instead of performing exploration everytime, read old results.
			// oneDimExplorer.readExploredPoints(placementResultDirectory);
			oneDimExplorer.explore ();

			Map<String, String> model = oneDimExplorer.getLeastSatPointModel ();
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
		// Create the output Directory first if it doesn't exist
        File directory = new File (partitionResultDirectory);
        directory.mkdirs ();
		
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

		paretoExplore.explore ();
		// paretoExplore.readExploredPoints(partitionResultDirectory);

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
