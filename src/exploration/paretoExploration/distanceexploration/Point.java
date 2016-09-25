package exploration.paretoExploration.distanceexploration;

import java.io.*;

public class Point
{
	private double [] coordinates; 	/*!< coordinates */
	private int dimensions;     		/*!< dimension */

	public Point (int dim)
	{
		coordinates = new double[dim];
		dimensions = dim;

		for (int var = 0; var < dimensions; ++var)
			coordinates[var] = 0;

	}

	public Point (int dim, double val)
	{
		coordinates = new double[dim];
		dimensions = dim;

		for (int var = 0; var < dimensions; ++var)
			coordinates[var] = val;


	}

	public Point (Point p)
	{
		coordinates = new double[p.dimensions];
		dimensions = p.dimensions;

		for (int var = 0; var < dimensions; ++var)
			coordinates[var] = p.coordinates[var];
	}

	public Point (int dim, double [] coord)
	{
		coordinates = new double[dim];
		dimensions = dim;

		for (int var = 0; var < dimensions; var++)
			coordinates[var] = coord[var];
	}


	public Point(int[] queryModel)
	{
		dimensions = queryModel.length;
		coordinates = new double[dimensions];
		for(int i=0;i<dimensions;i++)
			coordinates[i] = queryModel[i];
		// TODO Auto-generated constructor stub
	}

	public int dim() {return dimensions;}

	public Point divide (double value)
	{
		double coord[] = new double[dimensions];

		for (int var = 0; var < dimensions; ++var)
			coord[var] = coordinates[var] / value;

		return new Point (dimensions, coord);
	}
	
	public Point plus (Point p)
	{
		double coord[] = new double[dimensions];

		for (int var = 0; var < dimensions; ++var)
			coord[var] = coordinates[var] + p.coordinates[var];

		return new Point (dimensions, coord);
	}

	public Point plus (double n)
	{
		double coord[] = new double[dimensions];

		for (int var = 0; var < dimensions; ++var)
			coord[var] = coordinates[var] + n;			

		return new Point (dimensions, coord);
	}

	public Point minus (Point p)
	{
		double coord[] = new double[dimensions];

		for (int var = 0; var < dimensions; ++var)
			coord[var] = coordinates[var] - p.coordinates[var];

		return new Point (dimensions, coord);
	}


	public Point minus (double n)
	{
		double coord[] = new double[dimensions];

		for (int var = 0; var < dimensions; ++var)
			coord[var] = coordinates[var] - n;		

		return new Point (dimensions, coord);
	}

	public Point multiply (Point p)
	{

		double coord[] = new double[dimensions];

		for (int var = 0; var < dimensions; ++var)
			coord[var] = coordinates[var] * p.coordinates[var];		

		return new Point (dimensions, coord);
	}

	public Point multiply (double n)
	{
		double coord[] = new double[dimensions];

		for (int var = 0; var < dimensions; ++var)
			coord[var] = coordinates[var] * n;

		return new Point (dimensions, coord);
	}

	public boolean equals (Point p)
	{
		for (int i = 0; i < dimensions; ++i)
		{
			if (coordinates[i] != p.coordinates[i])
				return false;
		}
		return true;
	}
	
	public boolean lessThan (Point p)
	{
		for (int i = 0; i < dimensions; ++i)
		{
			if (coordinates[i] >= p.coordinates[i])
				return false;
		}

		return true;
	}

	public boolean lessThanOrEquals (Point p)
	{

		for (int i = 0; i < dimensions; ++i)
		{
			if (coordinates[i] > p.coordinates[i])
				return false;
		}

		return true;
	}

	public boolean greaterThan (Point p)
	{

		for (int i = 0; i < dimensions; ++i)
		{
			if (coordinates[i] <= p.coordinates[i])
				return false;
		}

		return true;
	}

	public boolean greaterThanOrEquals (Point p)
	{

		for (int i = 0; i < dimensions; ++i)
		{
			if (coordinates[i] < p.coordinates[i])
				return false;
		}

		return true;
	}

	public Point toInteger()
	{

		double coord[] = new double[dimensions];

		for (int i = 0; i < dimensions; ++i)
			coord[i] = (int) coordinates[i];

		return new Point (dimensions, coord);
	}
	
	public int [] getIntegerCoordinates() 
	{
		Point pt = toInteger();
		int ptArray[] = new int [dimensions];
		for(int i=0;i<dimensions;i++)
			ptArray[i] = (int) pt.get(i);
		return ptArray;
	}

	public double[] getCoordinates() { return coordinates;	}

	public void set (int i, double val)
	{
		if (i < dimensions)
			coordinates[i] = val;
		else
		{
			throw new RuntimeException ("ERROR : Point.set");
		}
	}
	
	public double get(int i) { return coordinates[i]; }
	

	public void trunc (double ub)
	{
		for (int i = 0; i < dimensions; ++i)
		{
			if (coordinates[i] > ub)
				coordinates[i] = ub;
		}
	}

	public void display ()
	{		
		System.out.print ("(" + coordinates[0]);
		for (int var = 1; var < dimensions; ++var)
		{
			System.out.print (", " + coordinates[var]);
		}
		System.out.println(")");
	}
	
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
	
	public void display (PrintWriter out)
	{		
		out.print ("(" + coordinates[0]);
		for (int var = 1; var < dimensions; ++var)
		{
			out.print (", " + coordinates[var]);
		}
		out.println(")");
	}
	
	public Point calculateMeet (Point p)
	{
		Point result = new Point(dimensions);
		
		for(int i=0;i<dimensions;i++)
			result.set(i, (coordinates[i] < p.get(i)? coordinates[i] : p.get(i)));
		
		return result;
	}
	
	public Point calculateJoin (Point p)
	{
		Point result = new Point(dimensions);
		
		for(int i=0;i<dimensions;i++)
			result.set(i, (coordinates[i] > p.get(i)? coordinates[i] : p.get(i)));
		
		return result;
	}
	
	public double distance (Point p)
	{
		double distance = 0.0;
		for(int i=0;i<dimensions;i++)
		{
			double tempDistance = (p.get(i) - coordinates[i]);
			if((tempDistance > 0) && (distance < tempDistance))
					distance = tempDistance;
		}
		return distance;
	}
}