package exploration;

import java.util.Map;
import solver.Z3Solver.SatResult;

/**
 * An interface class to the design space exploration algorithms.
 * This class contains methods which any design space algorithm needs 
 * to perform an exploration of a given design space. 
 * 
 * @author Pranav Tendulkar
 *
 */
public abstract class ExplorationParameters
{
	/**
	 * Number of dimensions to be explored 
	 */
	protected final int dimensions;
	
	/**
	 * Lower bounds for all the dimensions.
	 */
	protected int lowerBounds[]=null;
	
	/**
	 * Upper bounds for all the dimensions.
	 */
	protected int upperBounds[]=null;
	
	/**
	 * Granularity of Exploration. For grid-based exploration 
	 * this is the size of grid beyond which the grid will not be
	 * made finer. 
	 */
	protected int explorationGranularity[]=null;
	
	/**
	 * Name of the constraints for each dimension.
	 * Important because they are used when writing the log files.
	 */
	protected String constraintNames[]=null;	
	
	/**
	 * Set a constraint for a particular dimension. This will be called
	 * for every query when exploration algorithm wants to set a cost constraint
	 * for evaluation.
	 * 
	 * @param dimension dimension of which the constraint will be set
	 * @param value value to which the constraint will be set
	 */
	public abstract void setConstraint (int dimension, int value);
	
	/**
	 * If the query was SAT, then this method will be called to get
	 * the costs from the model given by the solver.
	 * 
	 * @return integer costs for every dimension taken from the model 
	 */
	public abstract int[] getCostsFromModel ();
	
	/**
	 * Get model of a SAT point from the solver.
	 * 
	 * @return contains an SMT variable and its value as given by the Solver.
	 */
	public abstract Map<String, String> getModelFromSolver ();
	
	/**
	 * After the constraints are set, the solver is queried for evaluation.
	 * 
	 * @param timeOutInSeconds time out for this query.
	 * @return SatResult depending on the result from the solver
	 */
	public abstract SatResult solverQuery (int timeOutInSeconds);
	
	/**
	 * Save the Solver Context to save it before we add the cost constraints. 
	 */
	public abstract void pushSolverContext ();	
	
	/**
	 * Retrieve back the solver context to add new const constraints.
	 * @param numContext number of pop should be made to the stack. Generally 1.
	 */
	public abstract void popSolverContext (int numContext);
	
	/**
	 * Initialization exploration parameters object.
	 * 
	 * @param dimensions number of dimensions to be explored.
	 */
	protected ExplorationParameters (int dimensions)
	{
		this.dimensions = dimensions;
		
		lowerBounds 		= new int[dimensions];
		upperBounds 		= new int[dimensions];
		constraintNames 	= new String[dimensions];
		explorationGranularity = new int[dimensions];
		
		for (int i=0;i<dimensions;i++)
			explorationGranularity[i] = 1;
	}	
	
	// In case we want to explore different ranges of bounds
	// we must have a provision to set the bounds.
	/**
	 * Set lower and upper bounds for the exploration for each dimension. 
	 * 
	 * @param lowerBound lower bound for each dimension
	 * @param upperBound upper bound for each dimension
	 */
	public void setBounds (int[] lowerBound, int[] upperBound)
	{
		if (lowerBound != null)
		{
			for (int i=0;i<dimensions;i++)
				lowerBounds[i] = lowerBound[i];
		}
		
		if (upperBound != null)
		{
			for (int i=0;i<dimensions;i++)
				upperBounds[i] = upperBound[i];
		}		
	}
	
	/**
	 * Set only upper bound of a dimension.
	 * 
	 * @param dimension dimension to be updated
	 * @param bound value which should be set as a bound.
	 */
	public void setUpperBound (int dimension, int bound)
	{
		upperBounds[dimension] = bound;
	}
	
	/**
	 * Set only lower bound of a dimension.
	 * 
	 * @param dimension dimension to be updated
	 * @param bound value which should be set as a bound.
	 */
	public void setLowerBound (int dimension, int bound)
	{
		lowerBounds[dimension] = bound;
	}
	
	/**
	 * Set exploration granularity for a given dimension.
	 * Exploration granularity is the step-size for grid-based
	 * exploration algorithm.
	 * 
	 * @param dimension dimension to be set for the granularity
	 * @param granularity value of granularity
	 */
	public void setExplorationGranularity (int dimension, int granularity) 
	{		
		explorationGranularity[dimension] = granularity;		
	}
	
	/**
	 * Gets the exploration granularity for a dimension.
	 * 
	 * @param dimension dimension to get the granularity
	 * @return Exploration granularity for the dimension
	 */
	public int getExplorationGranularity (int dimension) 
	{		
		return explorationGranularity[dimension];		
	}
	
	/**
	 * Get all lower bounds.
	 * 
	 * @return array of lower bounds for all the dimensions.
	 */
	public int[] getLowerBounds () 
	{		
		return lowerBounds.clone ();		
	}

	/**
	 * Get all upper bounds.
	 * 
	 * @return array of upper bounds for all the dimensions.
	 */
	public int[] getUpperBounds ()
	{
		return upperBounds.clone ();
	}
	
	/**
	 * Gets the name of the constraint for a dimension. 
	 * For example : "Latency" , "Number of Processors Used" etc.
	 * 
	 * @param dimension dimension to get the constraint name
	 * @return name of the constraint for a dimension
	 */
	public String getConstraintName (int dimension)
	{
		return constraintNames[dimension];
	}
	
	/**
	 * Gets number of dimensions in the exploration.
	 * 
	 * @return number of dimensions.
	 */
	public int getDimensions () { return dimensions; }
}
