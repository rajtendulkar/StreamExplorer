package exploration.parameters.oneDimension;

import java.util.Map;

import exploration.ExplorationParameters;
import exploration.interfaces.oneDim.LatencyConstraints;


import solver.Z3Solver.SatResult;
import spdfcore.*;
import spdfcore.stanalys.*;

import graphanalysis.CalculateBounds;

/**
 * Exploration Parameters for one-dimensional latency exploration.
 * 
 * @author Pranav Tendulkar
 *
 */
public class LatencyParams extends ExplorationParameters 
{	
	/**
	 * Number of dimensions for this exploration.
	 */
	private final static int dimensionsForThisExploration = 1;
	
	/**
	 * Solver being used to determine the latency cost. 
	 */
	private LatencyConstraints satSolver;
	
	/**
	 * Initialize exploration parameters object.
	 * 
	 * @param graph Application Graph
	 * @param solutions Solutions containing repetition count for all actors of application graph
	 */
	public LatencyParams (Graph graph, Solutions solutions)
	{		
		super (dimensionsForThisExploration);
		
		constraintNames[0] = "Latency";
		
		CalculateBounds bounds = new CalculateBounds (graph, solutions);
		
		lowerBounds[0] = bounds.findGraphMinLatency (); // Minimum latency		
		upperBounds[0] = bounds.findGraphMaxLatency (); // Maximum latency.
		
		// System.out.println ("Exploration Limits :: Latency (" + lowerBounds[0] + ", " + upperBounds[0] +")");
	}
	
	/* (non-Javadoc)
	 * @see exploration.ExplorationParameters#getCostsFromModel()
	 */
	@Override
	public int[] getCostsFromModel ()
	{
		int [] result = new int[dimensions];
		Map<String, String> model = satSolver.getModel ();
		
		result[0] = satSolver.getLatency (model);
		
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
				satSolver.generateLatencyConstraint (constraintValue);
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
	public void setSolver (LatencyConstraints solver)
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
