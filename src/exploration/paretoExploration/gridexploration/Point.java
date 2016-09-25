package exploration.paretoExploration.gridexploration;

/**
 * A point in the multi-dimensional space.
 * 
 * @author Pranav Tendulkar
 *
 */
public class Point
{
	/**
	 * Co-ordinates of the point 
	 */
	private double [] coordinates; 	/*!< coordinates */
	
	/**
	 * Number of dimensions of the space, the point is present.  
	 */
	private int dimensions;     		/*!< dimension */

	/**
	 * Initialize a point object
	 * 
	 * @param dim number of dimensions.
	 */
	public Point (int dim)
	{
		coordinates = new double[dim];
		dimensions = dim;

		for (int var = 0; var < dimensions; ++var)
			coordinates[var] = 0;

	}

	/**
	 * Initialize a point object
	 * 
	 * @param dim number of dimensions.
	 * @param val value to be initialize for every dimension.
	 */
	public Point (int dim, double val)
	{
		coordinates = new double[dim];
		dimensions = dim;

		for (int var = 0; var < dimensions; ++var)
			coordinates[var] = val;


	}

	/**
	 * Initialize a point object same as other point
	 * 
	 * @param p another point p 
	 */
	public Point (Point p)
	{
		coordinates = new double[p.dimensions];
		dimensions = p.dimensions;

		for (int var = 0; var < dimensions; ++var)
			coordinates[var] = p.coordinates[var];
	}

	/**
	 * Initialize a point object
	 * 
	 * @param dim number of dimensions
	 * @param coord co-ordinate for each dimension
	 */
	public Point (int dim, double [] coord)
	{
		coordinates = new double[dim];
		dimensions = dim;

		for (int var = 0; var < dimensions; var++)
			coordinates[var] = coord[var];
	}

	
	/**
	 * Initialize a point object
	 * 
	 * @param coord co-ordinate for each dimension
	 */
	public Point(int[] coord)
	{
		dimensions = coord.length;
		coordinates = new double[dimensions];
		for(int i=0;i<dimensions;i++)
			coordinates[i] = coord[i];
	}

	/**
	 * Get number of dimensions
	 * 
	 * @return number of dimensions.
	 */
	int dim() {return dimensions;}

	/**
	 * Divide every co-ordinate with a value and 
	 * return new Point object with divided co-ordinates.
	 * This object remains unchanged. 
	 * 
	 * @param value value to be divided with
	 * @return Point new Point object with divided co-ordinates
	 */
	Point divide (double value)
	{
		double coord[] = new double[dimensions];

		for (int var = 0; var < dimensions; ++var)
			coord[var] = coordinates[var] / value;

		return new Point (dimensions, coord);
	}
	
	/**
	 * Add this point with another point and 
	 * return new Point object with added co-ordinates.
	 * This object remains unchanged.
	 *  
	 * @param p another point to be added with
	 * @return new Point object with added co-ordinates
	 */
	Point plus (Point p)
	{
		double coord[] = new double[dimensions];

		for (int var = 0; var < dimensions; ++var)
			coord[var] = coordinates[var] + p.coordinates[var];

		return new Point (dimensions, coord);
	}

	
	/**
	 * Add this point with another values and 
	 * return new Point object with added co-ordinates.
	 * This object remains unchanged.
	 * 
	 * @param values values to be added to each dimension.
	 * @return new Point object with added co-ordinates
	 */
	Point plus (double values[])
	{
		double coord[] = new double[dimensions];

		for (int var = 0; var < dimensions; ++var)
			coord[var] = coordinates[var] + values[var];			

		return new Point (dimensions, coord);
	}
	
	/**
	 * Add each dimension of this point with a value and 
	 * return new Point object with added co-ordinates.
	 * This object remains unchanged.
	 * 
	 * @param value value to be added to each dimension
	 * @return new Point with added value to each dimension
	 */
	Point plus (double value)
	{
		double coord[] = new double[dimensions];

		for (int var = 0; var < dimensions; ++var)
			coord[var] = coordinates[var] + value;			

		return new Point (dimensions, coord);
	}

	/**
	 * Subtract this point with another point p and 
	 * return a Point with new co-ordinates. We return (this - p).
	 * This object remains unchanged.
	 * 
	 * @param p point to be subtracted from the current
	 * @return new Point with subtracted value to each dimension
	 */
	Point minus (Point p)
	{
		double coord[] = new double[dimensions];

		for (int var = 0; var < dimensions; ++var)
			coord[var] = coordinates[var] - p.coordinates[var];

		return new Point (dimensions, coord);
	}


	/**
	 * Subtract a value from each dimension of this point 
	 * and return a new point with new co-ordinates.
	 * This object remains unchanged.
	 * 
	 * @param value value to be subtracted
	 * @return new Point with subtracted value
	 */
	Point minus (double value)
	{
		double coord[] = new double[dimensions];

		for (int var = 0; var < dimensions; ++var)
			coord[var] = coordinates[var] - value;		

		return new Point (dimensions, coord);
	}

	/**
	 * Multiply this point with another point and return a 
	 * new point. 
	 * This object remains unchanged.
	 * 
	 * @param p another Point p.
	 * @return new Point with co-ordinates as (this.co-ord * p.co-ord)
	 */
	Point multiply (Point p)
	{

		double coord[] = new double[dimensions];

		for (int var = 0; var < dimensions; ++var)
			coord[var] = coordinates[var] * p.coordinates[var];		

		return new Point (dimensions, coord);
	}

	/**
	 * Multiply this point with a constant value and return a
	 * new Point object.
	 * This object remains unchanged.
	 * 
	 * @param value value to multiply with each co-ordinate
	 * @return new Point with multipled co-ordinate
	 */
	Point multiply (double value)
	{
		double coord[] = new double[dimensions];

		for (int var = 0; var < dimensions; ++var)
			coord[var] = coordinates[var] * value;

		return new Point (dimensions, coord);
	}

	/**
	 * Check if this point has same co-ordinates as other Point p.
	 * 
	 * @param p other Point p
	 * @return if they are equal return true else return false
	 */
	boolean equals (Point p)
	{
		for (int i = 0; i < dimensions; ++i)
		{
			if (coordinates[i] != p.coordinates[i])
				return false;
		}
		return true;
	}
	
	/**
	 * Check if this point has co-ordinates less than of other Point p.
	 * 
	 * @param p other Point p
	 * @return if they are less than return true, else return false
	 */
	boolean lessThan (Point p)
	{
		for (int i = 0; i < dimensions; ++i)
		{
			if (coordinates[i] >= p.coordinates[i])
				return false;
		}

		return true;
	}

	/**
	 * Check if this point has co-ordinates less than or equal of other Point p.
	 * 
	 * @param p other Point p
	 * @return if they are less or equal than return true, else return false
	 */
	boolean lessThanOrEquals (Point p)
	{

		for (int i = 0; i < dimensions; ++i)
		{
			if (coordinates[i] > p.coordinates[i])
				return false;
		}

		return true;
	}

	/**
	 * Check if this point has co-ordinates greater than of other Point p.
	 * 
	 * @param p other Point p
	 * @return if they are greater than return true, else return false
	 */
	boolean greaterThan (Point p)
	{

		for (int i = 0; i < dimensions; ++i)
		{
			if (coordinates[i] <= p.coordinates[i])
				return false;
		}

		return true;
	}

	/**
	 * Check if this point has co-ordinates greater than or equal of other Point p.
	 * 
	 * @param p other Point p
	 * @return if they are greater than or equal return true, else return false
	 */
	boolean greaterThanOrEquals (Point p)
	{

		for (int i = 0; i < dimensions; ++i)
		{
			if (coordinates[i] < p.coordinates[i])
				return false;
		}

		return true;
	}

	/**
	 * Convert double co-ordinates to integer.
	 * 
	 * @return new Point with all co-ordinates in integer.
	 */
	Point toInteger()
	{

		double coord[] = new double[dimensions];

		for (int i = 0; i < dimensions; ++i)
			coord[i] = (int) coordinates[i];

		return new Point (dimensions, coord);
	}
	
	/**
	 * Get integer values of the co-ordinates 
	 * @return array of integer co-ordinates
	 */
	int [] getIntegerCoordinates() 
	{
		Point pt = toInteger();
		int ptArray[] = new int [dimensions];
		for(int i=0;i<dimensions;i++)
			ptArray[i] = (int) pt.get(i);
		return ptArray;
	}

	/**
	 * Get co-ordinates of the point.
	 * @return double array of co-ordinates of the point.
	 */
	double[] getCoordinates() { return coordinates;	}

	/**
	 * Set each dimension to a constant value.
	 * 
	 * @param value value to be set
	 */
	void set (double value)
	{
		for(int i=0;i<dimensions;i++)
			coordinates[i] = value;
	}
	
	/**
	 * Set each dimension to a constant value.
	 * 
	 * @param value value to be set
	 */
	void set (int value)
	{
		for(int i=0;i<dimensions;i++)
			coordinates[i] = value;
	}
	
	/**
	 * Set different values for different dimensions of the point.
	 * 
	 * @param value integer array of values to be set
	 */
	void set (int value[])
	{
		if(value.length != dimensions)
			throw new RuntimeException("Dimensions Mismatch");
		for(int i=0;i<dimensions;i++)
			coordinates[i] = value[i];
	}
	
	/**
	 * Set different values for different dimensions of the point.
	 * 
	 * @param value double array of values to be set
	 */
	void set (double value[])
	{
		if(value.length != dimensions)
			throw new RuntimeException("Dimensions Mismatch");
		for(int i=0;i<dimensions;i++)
			coordinates[i] = value[i];
	}
	
	/**
	 * Set value of only one dimension of the point.
	 * 
	 * @param dimension dimension to be set
	 * @param value value to be set
	 */
	void set (int dimension, double value)
	{
		if (dimension < dimensions)
			coordinates[dimension] = value;
		else
		{
			throw new RuntimeException ("ERROR : Point.set");
		}
	}
	
	/**
	 * Gets value at the specified dimension.
	 * 
	 * @param dimension dimension of the point
	 * @return co-ordinate value at this dimension
	 */
	public double get (int dimension) { return coordinates[dimension]; }
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		String result = "(" + coordinates[0];
		for (int var = 1; var < dimensions; ++var)
		{
			result = result.concat(", " + coordinates[var]);
		}
		result = result.concat(")");
		return result;
	}
}