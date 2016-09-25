package exploration.parameters.oneDimension;
import java.util.*;

import exploration.ExplorationParameters;

import solver.Z3Solver.SatResult;
import solver.sharedMemory.combinedSolver.pipelined.UnfoldingScheduling;
import spdfcore.*;
import spdfcore.stanalys.*;
import graphanalysis.*;
import graphanalysis.properties.GraphAnalysisSdfAndHsdf;

/**
 *  Exploration Parameters for one-dimensional Period exploration using Unfolding solver.
 *  We had to create a new parameters, because push and pop doesn't work for this solver.
 *  For every query, we have to create a new solver instance. This happens because, depending
 *  on the unfolding parameter, the number of variables in the problem context change.
 *  
 * @author Pranav Tendulkar
 *
 */
public class PeriodUnfoldingParams extends ExplorationParameters 
{	
	/**
	 * Number of dimensions for this exploration. 
	 */
	private final static int dimensionsForThisExploration = 1;
	
	/**
	 * Solver being used to determine the period cost.
	 */
	private UnfoldingScheduling satSolver;
	
	/**
	 * An upper bound on latency for exploration. 
	 * This will be sum of execution times of all the tasks.
	 */
	private double maxLatency=0;
	
	/**
	 * Latency scaling factor. Latency constraint = (maxLatencyScalingFactor * maxLatency)
	 */
	public double maxLatencyScalingFactor = 1.0;
	
	/**
	 * Number of processors used in the exploration 
	 */
	public int numProcessors=1;
	
	/**
	 * An unfolding factor omega.
	 */
	private int omegaMax=0;
	
	/**
	 * Initialize exploration parameters object.
	 * 
	 * @param graph Application Graph
	 * @param solutions Solutions containing repetition count for all actors of application graph
	 */
	public PeriodUnfoldingParams (Graph graph, Solutions solutions)
	{
		super (dimensionsForThisExploration);		
		
		constraintNames[0] = "Period";
		
		CalculateBounds bounds = new CalculateBounds (graph, solutions);
		
		lowerBounds[0] = bounds.findGraphMinPeriod ();  // Minimum period.		
		upperBounds[0] = bounds.findGraphMaxPeriod (); // Maximum Period.
		
		maxLatency = bounds.findGraphMaxLatency ();
		
		GraphAnalysisSdfAndHsdf graphAnalysis = new GraphAnalysisSdfAndHsdf (graph, solutions);
		
		omegaMax = 0;
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			int distance = graphAnalysis.getMaxDistanceFromSrc (actr);
			if (distance > omegaMax)
				omegaMax = distance;
		}
		
		// System.out.println ("Exploration Limits :: Period (" + lowerBounds[0] + ", " + upperBounds[0] + ")" );
	}
	
	/* (non-Javadoc)
	 * @see exploration.ExplorationParameters#getCostsFromModel()
	 */
	@Override
	public int[] getCostsFromModel ()
	{
		int [] result = new int[dimensions];
		Map<String, String> model = satSolver.getModel ();		
		result[0] = satSolver.getPeriod (model);
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
			// Period
			case 0:
				if (maxLatencyScalingFactor <= 0 || maxLatencyScalingFactor > 1)
					throw new RuntimeException ("Max Latency Scaling factor should be in the range (0,1] ");
				
				Double latency = (maxLatency * maxLatencyScalingFactor);
				
				satSolver.resetSolver ();
				satSolver.numGraphUnfold = (int) Math.ceil (latency / constraintValue);
				
				if (satSolver.numGraphUnfold > (2*omegaMax + 2))
					satSolver.numGraphUnfold = (2*omegaMax + 2);

				satSolver.assertPipelineConstraints ();
				satSolver.generateLatencyConstraint (latency.intValue ());				
				satSolver.generatePeriodConstraint (constraintValue);
				satSolver.generateProcessorConstraint (numProcessors);
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
	public void setSolver (UnfoldingScheduling solver)
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
		// satSolver.pushContext ();
	}
	
	/* (non-Javadoc)
	 * @see exploration.ExplorationParameters#popSolverContext(int)
	 */
	@Override
	public void popSolverContext (int numContext)
	{
		// satSolver.popContext (numContext);
	}
}
