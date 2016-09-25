package exploration.parameters.twoDimension;
import java.util.Map;

import exploration.ExplorationParameters;
import exploration.interfaces.twoDim.WrkLdCommCostConstraints;

import solver.Z3Solver.SatResult;
import spdfcore.*;
import spdfcore.stanalys.*;
import graphanalysis.CalculateBounds;

/**
 *  Workload Imbalance and Communication cost exploration Parameters.
 *  
 * @author Pranav Tendulkar
 *
 */
public class WrkLoadCommCostParams extends ExplorationParameters 
{	
	/**
	 * Number of dimensions for this exploration.
	 */
	private final static int dimensionsForThisExploration = 2;
	
	/**
	 * Solver being used for the Exploration. 
	 */
	private WrkLdCommCostConstraints satSolver;
	
	/**
	 * Initialize exploration parameters object.
	 * 
	 * @param graph Application Graph
	 * @param solutions Solutions containing repetition count for all actors of application graph
	 */
	public WrkLoadCommCostParams (Graph graph, Solutions solutions)
	{
		super (dimensionsForThisExploration);		
		
		constraintNames[0] = "Workload Imbalance";
		constraintNames[1] = "Communication Cost";
		
		CalculateBounds bounds = new CalculateBounds (graph, solutions);
		
		lowerBounds[0] = 0;  // Minimum Workload Imbalance.
		lowerBounds[1] = 0;  // Minimum Communication Cost
		
		upperBounds[0] = bounds.findTotalWorkLoad (); // Maximum Workload Imbalance
		upperBounds[1] = bounds.findMaxCommunicationCost () * 2;   // Maximum Communication Cost
		// This was multiplied by 2, because on two clusters the cost will add up.
		
		// Set exploration granularity, below which the algorithm will not explore useless points.
		explorationGranularity[0] = bounds.findMinWorkLoad ();
		explorationGranularity[1] = bounds.findMinCommunicationCost ();
		
		//System.out.println ("Exploration Limits :: Workload Imbalance (" + lowerBounds[0] + ", " + upperBounds[0] +
		//										") :: Communication Cost (" + lowerBounds[1] + ", " + upperBounds[1] + ")");
		//System.out.println ("Exploration Granularity (" + explorationGranularity[0] + ", " + explorationGranularity[1] + ")");		
	}
	
	/* (non-Javadoc)
	 * @see exploration.ExplorationParameters#getCostsFromModel()
	 */
	@Override
	public int[] getCostsFromModel ()
	{
		int [] result = new int[dimensions];
		Map<String, String> model = satSolver.getModel ();		
		result[0] = satSolver.getWorkLoadImbalance (model);
		result[1] = satSolver.getCommunicationCost (model);		
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
				satSolver.generateWorkImbalanceConstraint (constraintValue);
				break;
			
			// Number of Processors.
			case 1:
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
	public void setSolver (WrkLdCommCostConstraints solver)
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
