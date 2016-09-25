package exploration.parameters.threeDimension;

import java.util.*;

import exploration.ExplorationParameters;
import exploration.interfaces.threeDim.LatProcBuffConstraints;

import solver.Z3Solver.SatResult;
import spdfcore.*;
import spdfcore.stanalys.*;

import graphanalysis.CalculateBounds;

/**
 * Latency, Number of Processors used and Communication buffer-size exploration parameters.
 * @author Pranav Tendulkar
 *
 */
public class LatProcBuffParams extends ExplorationParameters 
{	
	/**
	 * Number of dimensions for this exploration.
	 */
	private final static int dimensionsForThisExploration = 3;
	
	/**
	 * Solver being used to solve this problem. 
	 */
	private LatProcBuffConstraints satSolver;
	
	/**
	 * Initialize exploration parameters object.
	 * 
	 * @param graph Application Graph
	 * @param solutions Solutions containing repetition count for all actors of application graph
	 */
	public LatProcBuffParams (Graph graph, Solutions solutions)
	{		
		super (dimensionsForThisExploration);		
		
		constraintNames[0] = "Latency";
		constraintNames[1] = "Processors";
		constraintNames[2] = "Buffer Size";
		
		CalculateBounds bounds = new CalculateBounds (graph, solutions);
		
		lowerBounds[0] = bounds.findGraphMinLatency (); // Minimum latency.
		lowerBounds[1] = 1; 						   // Minimum One processor
		lowerBounds[2] = bounds.findMinBufferSizeInBytes ();   // Minimum Buffer Size
		
		upperBounds[0] = bounds.findGraphMaxLatency (); // Maximum latency.
		upperBounds[1] = bounds.findMaxProcessors ();   // Maximum Number of Processors
		upperBounds[2] = bounds.findMaxBufferSizeInBytes ();   // Maximum Buffer Size
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
		result[1] = satSolver.getProcessors (model);
		result[2] = satSolver.getTotalBufferSize (model);		
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
				satSolver.generateProcessorConstraint (constraintValue);
				break;
				
			// Buffer Size
			case 2:
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
	public void setSolver (LatProcBuffConstraints solver)
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
