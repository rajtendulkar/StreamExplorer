package exploration.parameters.twoDimension;

import java.util.Map;

import exploration.ExplorationParameters;
import exploration.interfaces.twoDim.PeriodProcConstraints;

import solver.Z3Solver.SatResult;
import spdfcore.*;
import spdfcore.stanalys.*;

import graphanalysis.CalculateBounds;

/**
 * Period and Number of processors used exploration Parameters.
 * 
 * @author Pranav Tendulkar
 *
 */
public class PeriodProcParams extends ExplorationParameters 
{	
	/**
	 * Number of dimensions for this exploration.
	 */
	private final static int dimensionsForThisExploration = 2;
	
	/**
	 * Solver being used for the Exploration.
	 */
	private PeriodProcConstraints satSolver;
	
	/**
	 * @param graph graph Application Graph
	 * @param solutions Solutions containing repetition count for all actors of application graph
	 */
	public PeriodProcParams (Graph graph, Solutions solutions)
	{		
		super (dimensionsForThisExploration);		
		
		constraintNames[0] = "Period";
		constraintNames[1] = "Processors";
		
		CalculateBounds bounds = new CalculateBounds (graph, solutions);
		
		lowerBounds[0] = bounds.findGraphMinPeriod ();  // Minimum period.
		lowerBounds[1] = 1; 						   // Minimum One processor
		
		upperBounds[0] = bounds.findGraphMaxPeriod ();  // Maximum period.
		upperBounds[1] = bounds.findMaxProcessors ();   // Naximum Number of Processors
		
		//System.out.println ("Exploration Limits :: Period (" + lowerBounds[0] + ", " + upperBounds[0] +
		//										") :: Processors (" + lowerBounds[1] + ", " + upperBounds[1] +
		//										")");
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
		result[1] = satSolver.getProcessors (model);		
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
	public void setSolver (PeriodProcConstraints solver)
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
