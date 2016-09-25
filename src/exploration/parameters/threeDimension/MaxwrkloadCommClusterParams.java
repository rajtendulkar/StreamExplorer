package exploration.parameters.threeDimension;

import java.util.Map;

import solver.Z3Solver.SatResult;
import spdfcore.*;
import spdfcore.stanalys.Solutions;
import exploration.ExplorationParameters;
import exploration.interfaces.threeDim.MaxWrkLdCommCostClusterConstraints;
import graphanalysis.CalculateBounds;

/**
 * Maximum workload per cluster, communication cost, and number of clusters used  exploration parameters.
 *
 * @author Pranav Tendulkar
 */
public class MaxwrkloadCommClusterParams extends ExplorationParameters 
{
	/**
	 * Number of dimensions for this exploration.
	 */
	private final static int dimensionsForThisExploration = 3;
	
	/**
	 * Solver being used to solve this problem.
	 */
	private MaxWrkLdCommCostClusterConstraints satSolver;
	
	/**
	 * Initialize exploration parameters object.
	 * 
	 * @param graph Application Graph
	 * @param solutions Solutions containing repetition count for all actors of application graph
	 * @param totalNumClusters total number of clusters in the hardware platform
	 */
	public MaxwrkloadCommClusterParams (Graph graph, Solutions solutions, int totalNumClusters)
	{
		super (dimensionsForThisExploration);		
		
		constraintNames[0] = "Max Workload Per Cluster";
		constraintNames[1] = "Communication Cost";
		constraintNames[2] = "Number of Clusters";
		
		CalculateBounds bounds = new CalculateBounds (graph, solutions);
		
		lowerBounds[0] = 0;  // Minimum Workload per cluster.
		lowerBounds[1] = 0;  // Minimum Communication Cost
		lowerBounds[2] = 1;  // Minimum No. Of Clusters to start
		
		upperBounds[0] = bounds.findTotalWorkLoad(); // Maximum Workload Imbalance
		upperBounds[1] = bounds.findMaxCommunicationCost ();   // Maximum Communication Cost
		upperBounds[2] = totalNumClusters;   // Maximum No. Of Clusters
		
		int maxWorkload = Integer.MAX_VALUE;
		for(Actor actr : graph.getActorList())
		{
			int workload = actr.getExecTime() * solutions.getSolution(actr).returnNumber();
			if(maxWorkload > workload)
				maxWorkload = workload;
		}
		
		// Set exploration granularity, below which the algorithm will not explore useless points.
		explorationGranularity[0] = maxWorkload / 2;
		explorationGranularity[1] = bounds.findMinCommunicationCost() / 2;
		explorationGranularity[2] = 1;
		
		// System.out.println ("Exploration Limits :: Max Workload Per Cluster (" + lowerBounds[0] + ", " + upperBounds[0] +
		// 										") :: Communication Cost (" + lowerBounds[1] + ", " + upperBounds[1] + ")"
		//										+ " :: Number of Clusters ("+ lowerBounds[2] + ", " + upperBounds[2] + ")");
		// System.out.println ("Exploration Granularity (" + explorationGranularity[0] + ", " + explorationGranularity[1] + ", " +  
		//		explorationGranularity[2]+  ")");
	}
	
	/* (non-Javadoc)
	 * @see exploration.ExplorationParameters#getCostsFromModel()
	 */
	@Override
	public int[] getCostsFromModel ()
	{
		int [] result = new int[dimensions];
		Map<String, String> model = satSolver.getModel ();		
		result[0] = satSolver.getMaxWorkLoadPerCluster (model);
		result[1] = satSolver.getCommunicationCost (model);
		result[2] = satSolver.getTotalClustersUsed (model);
		return result;
	}

	/* (non-Javadoc)
	 * @see exploration.ExplorationParameters#setConstraint(int, int)
	 */
	@Override
	public void setConstraint (int constraintDimension, int constraintValue) 
	{
		switch (constraintDimension)
		{
			// Workload Imbalance
			case 0:
				satSolver.generateMaxWorkloadPerClusterConstraint (constraintValue);
				break;
			
			// Communication Cost
			case 1:
				satSolver.generateCommunicationCostConstraint (constraintValue);
				break;
				
			// Number of Clusters.
			case 2:
				satSolver.generateClusterConstraint (constraintValue);
				break;
				
			default:
				throw new RuntimeException ("Undefined Constraint Dimension");
		}		
	}
	
	/**
	 * Set the SMT solver for exploration purposes.
	 * 
	 * @param solver solver to be used for exploration
	 */
	public void setSolver (MaxWrkLdCommCostClusterConstraints solver)
	{
		satSolver = solver;
	}
	
	/* (non-Javadoc)
	 * @see exploration.ExplorationParameters#getModelFromSolver()
	 */
	@Override
	public Map<String, String> getModelFromSolver ()
	{
		return satSolver.getModel ();
	}
	
	/* (non-Javadoc)
	 * @see exploration.ExplorationParameters#solverQuery(int)
	 */
	@Override
	public SatResult solverQuery (int timeOutInSeconds)
	{
		return satSolver.checkSat (timeOutInSeconds);
	}
	
	/* (non-Javadoc)
	 * @see exploration.ExplorationParameters#pushSolverContext()
	 */
	@Override
	public void pushSolverContext ()
	{
		satSolver.pushContext ();
	}
	
	/* (non-Javadoc)
	 * @see exploration.ExplorationParameters#popSolverContext(int)
	 */
	@Override
	public void popSolverContext (int numContext)
	{
		satSolver.popContext (numContext);
	}
}
