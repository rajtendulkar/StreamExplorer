package exploration.parameters.oneDimension;

import java.util.*;

import platform.model.Platform;
import exploration.ExplorationParameters;
import exploration.interfaces.oneDim.CommunicationCostConstraints;
import solver.Z3Solver.SatResult;
import spdfcore.*;
import spdfcore.stanalys.*;
import graphanalysis.CalculateBounds;

/**
 * Exploration Parameters for minimizing the communication cost.
 * 
 * @author Pranav Tendulkar
 *
 */
public class CommCostParams extends ExplorationParameters 
{	
	/**
	 * Number of dimensions for this exploration.
	 */
	private final static int dimensionsForThisExploration = 1;
	
	/**
	 * Solver being used to determine the communication cost. 
	 */
	private CommunicationCostConstraints satSolver;
	
	/**
	 * Initialize exploration parameters object.
	 * 
	 * @param graph Application Graph
	 * @param solutions Solutions containing repetition count for all actors of application graph
	 * @param platform hardware platform model being used
	 */
	public CommCostParams (Graph graph, Solutions solutions, Platform platform)
	{		
		super (dimensionsForThisExploration);
		
		constraintNames[0] = "Communication Cost";
		
		CalculateBounds bounds = new CalculateBounds (graph, solutions);
		
		lowerBounds[0] = 0; // Minimum Communication Cost.		
		upperBounds[0] = bounds.findMaxCommunicationCost () * platform.getMaxDistanceInPlatform(); // Maximum Comm Cost.
		
		// System.out.println ("Exploration Limits :: Communication Cost (" + lowerBounds[0] + ", " + upperBounds[0] +")");
	}
	
	/* (non-Javadoc)
	 * @see exploration.ExplorationParameters#getCostsFromModel()
	 */
	@Override
	public int[] getCostsFromModel ()
	{
		int [] result = new int[dimensions];
		Map<String, String> model = satSolver.getModel ();
		
		result[0] = satSolver.getCommunicationCost (model);
		
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
			// Latency
			case 0:
				satSolver.generateCommunicationCostConstraint (constraintValue);
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
	public void setSolver (CommunicationCostConstraints solver)
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
