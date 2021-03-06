package exploration.parameters.twoDimension;
import java.util.*;

import exploration.ExplorationParameters;
import exploration.interfaces.twoDim.LatBuffConstraints;

import solver.Z3Solver.SatResult;
import spdfcore.*;
import spdfcore.stanalys.*;

import graphanalysis.CalculateBounds;

/**
 * Latency and Buffer-Size exploration Parameters.
 * 
 * @author Pranav Tendulkar
 *
 */
public class LatBuffParams extends ExplorationParameters 
{	
	/**
	 * Number of dimensions for this exploration.
	 */
	private final static int dimensionsForThisExploration = 2;
	
	/**
	 * Solver being used for the Exploration. 
	 */
	private LatBuffConstraints satSolver;
	
	/**
	 * Initialize exploration parameters object.
	 * 
	 * @param graph Application Graph
	 * @param solutions Solutions containing repetition count for all actors of application graph
	 */
	public LatBuffParams (Graph graph, Solutions solutions)
	{		
		super (dimensionsForThisExploration);		
		
		constraintNames[0] = "Latency";
		constraintNames[1] = "Buffer Size";
		
		CalculateBounds bounds = new CalculateBounds (graph, solutions);
		
		lowerBounds[0] = bounds.findGraphMinLatency (); // Minimum latency.
		lowerBounds[1] = bounds.findMinBufferSizeInBytes();   // Minimum buffer size
		
		upperBounds[0] = bounds.findGraphMaxLatency (); // Maximum latency.
		upperBounds[1] = bounds.findMaxBufferSizeInBytes ();   // Maximum buffer size
		
		// We set the exploration granularity.
		explorationGranularity[0] = bounds.findMinWorkLoad();
		explorationGranularity[1] = bounds.findMinCommunicationCost();
		
		// System.out.println ("Exploration Limits :: Latency (" + lowerBounds[0] + ", " + upperBounds[0] +
		//										") :: Buffer Size (" + lowerBounds[1] + ", " + upperBounds[1] + ")");
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
		result[1] = satSolver.getTotalBufferSize (model);		
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
			
			// Number of Processors.
			case 1:
				satSolver.generateBufferConstraint (constraintValue);
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
	public void setSolver (LatBuffConstraints solver)
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
