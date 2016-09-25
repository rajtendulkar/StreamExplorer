package exploration.parameters.twoDimension;
import java.util.*;

import exploration.ExplorationParameters;

import solver.Z3Solver.SatResult;
import solver.sharedMemory.combinedSolver.pipelined.UnfoldingScheduling;
import spdfcore.*;
import spdfcore.stanalys.*;
import graphanalysis.*;
import graphanalysis.properties.GraphAnalysisSdfAndHsdf;

/**
 *  Period and Number of processors used exploration Parameters.
 * @author Pranav Tendulkar
 *
 */
public class PeriodProcUnfolding extends ExplorationParameters 
{	
	/**
	 * Number of dimensions for this exploration.
	 */
	private final static int dimensionsForThisExploration = 2;
	
	/**
	 * Solver being used for the Exploration. 
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
	 * An unfolding factor omega.
	 */
	private int omegaMax=0;
	
	/**
	 * Initialize exploration parameters object.
	 * 
	 * @param graph Application Graph
	 * @param solutions Solutions containing repetition count for all actors of application graph
	 */
	public PeriodProcUnfolding (Graph graph, Solutions solutions)
	{
		super (dimensionsForThisExploration);		
		
		constraintNames[0] = "Period";
		constraintNames[1] = "Processors";
		
		CalculateBounds bounds = new CalculateBounds (graph, solutions);
		
		lowerBounds[0] = bounds.findGraphMinPeriod ();  // Minimum period.
		lowerBounds[1] = 1; 						   // Minimum One processor
		
		upperBounds[0] = bounds.findGraphMaxPeriod (); // Maximum Period.
		upperBounds[1] = bounds.findMaxProcessors ();   // Naximum Number of Processors
		
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
		
		// System.out.println ("Exploration Limits :: Period (" + lowerBounds[0] + ", " + upperBounds[0] +
		//		") :: Processors (" + lowerBounds[1] + ", " + upperBounds[1] + ")");
	}
	
	/* (non-Javadoc)
	 * @see exploration.ExplorationParameters#getCostsFromModel()
	 */
	@Override
	public int[] getCostsFromModel ()
	{
		int [] result = new int[dimensions];
		Map<String, String> model = satSolver.getModel ();

		result[0] = Integer.parseInt (model.get ("period"));
		result[1] = Integer.parseInt (model.get ("totalProc"));
		
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
				break;
			
			// Number of Processors.
			case 1:
				satSolver.generateProcessorConstraint (constraintValue);
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
