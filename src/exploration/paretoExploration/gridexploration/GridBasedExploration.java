package exploration.paretoExploration.gridexploration;

import java.io.*;
import java.util.*;
import solver.Z3Solver.SatResult;
import exploration.*;

/**
 * Grid-based Design Space Exploration of multi-dimensional design space.
 * In this algorithm, we specify the design space to be explored, the upper 
 * and lower bounds. The algorithm then divides this design space using grid
 * and then queries all the points on the grid, only if they don't fall in the
 * known area (forward cone of SAT point or backward cone of UNSAT point).
 * 
 * @author Pranav Tendulkar
 *
 */
public class GridBasedExploration extends Explorer 
{
	/**
	 * Current epsilon value of the grid. Initially this value is 0.5, and we 
	 * divide it by 2 to make grid finer at every iteration.
	 */
	private double epsilon;
	
	/**
	 * List of all the SAT points explored. 
	 */
	private List<Point> satPointsList; 
	// private List<Point> unsatPointsList;
	// private List<Point> timedOutPointsList;
	
	/**
	 * List of Pareto Points from the exploration. 
	 */
	private List<Point> paretoPoints;
	
	/**
	 * Model for every SAT point discovered in the exploration. 
	 */
	private List<Map<String, String>> satPointsModelList;
	
	/**
	 * Model for every Pareto point from the exploration. 
	 */
	private List<Map<String, String>> paretoModelList;
	
	/**
	 * SAT points in the exploration. In this list we do not save any dominated SAT points.
	 */
	private List<Point> algoSatPointsList; 
	
	/**
	 * UNSAT points in the exploration. In this list we do not save any dominated UNSAT points.
	 */
	private List<Point> algoUnsatPointsList;
	
	/**
	 * Lower bounds for the exploration.
	 */
	private int lowerBounds[];
	
	/**
	 * Upper bounds for the exploration. 
	 */
	private int upperBounds[];

	/**
	 * Initialize the grid-based explorer.
	 *  
	 * @param opDir output directory to write all the log files
	 * @param perQueryTimeOutSeconds time out per query in seconds
	 * @param totalTimeOutInSeconds global time out in seconds for all the exploration
 	 * @param explParams Exploration parameters
	 */
	public GridBasedExploration (String opDir, int perQueryTimeOutSeconds, 
								int totalTimeOutInSeconds, ExplorationParameters explParams) 
	{
		super (opDir, explParams.getDimensions (), perQueryTimeOutSeconds, totalTimeOutInSeconds, explParams);
		
		if (dimensions < 2)
			throw new RuntimeException ("At least 2 dimensions should be present.");
		
		satPointsList = new ArrayList<Point>();		
		// unsatPointsList = new ArrayList<Point>();
		// timedOutPointsList = new ArrayList<Point>();
		satPointsModelList = new ArrayList<Map<String, String>>();
		
		paretoPoints = new ArrayList<Point>();
		paretoModelList = new ArrayList<Map<String, String>>();
		
		algoSatPointsList = new ArrayList<Point>();
		algoUnsatPointsList = new ArrayList<Point>();
	}
	
	/**
	 * Add UNSAT point to the list. We remove the
	 * dominated points so that we have size of the list
	 * as minimum as possible.
	 * 
	 * @param point new point to be added
	 */
	private void addUnsatToList (Point point)
	{
		
		// We remove all the dominated points.
		for(int i=0;i<algoUnsatPointsList.size();i++)
		{
			Point unsatPt = algoUnsatPointsList.get(i);
			if(unsatPt.lessThanOrEquals(point))
			{
				algoUnsatPointsList.remove(i);
				i--;
			}
		}
		algoUnsatPointsList.add(new Point(point));
	}
	
	/**
	 * Add SAT point to the list. We remove the
	 * dominated points so that we have size of the list
	 * as minimum as possible.
	 * 
	 * @param point new point to be added
	 */
	private void addSatToList (Point point)
	{
		
		// We remove all the dominated points.
		for(int i=0;i<algoSatPointsList.size();i++)
		{
			Point satPt = algoSatPointsList.get(i);
			if(satPt.greaterThanOrEquals(point))
			{
				algoSatPointsList.remove(i);
				i--;
			}
		}
		algoSatPointsList.add(new Point(point));
	}
	
	/**
	 * Convert the string in the model file to List of model maps.
	 * 
	 * @param fileName name of the model file
	 * @param satPointsModelList list where SAT models will be stored.
	 */
	private void readModelFile (String fileName, List<Map<String, String>>satPointsModelList)
	{
		FileInputStream fstream;
		try 
		{
			fstream = new FileInputStream(fileName);		
			BufferedReader br = new BufferedReader(new InputStreamReader(fstream));

			String strLine;
			int lineNum=0;

			//Read File Line By Line
			while ((strLine = br.readLine()) != null)   
			{
				// Print the content on the console
				if(lineNum % 2 != 0)
				{
					Map<String, String> model = new HashMap<String, String>();					
					strLine = strLine.replace("{", "");
					strLine = strLine.replace("}", "");
					strLine = strLine.replaceAll("( )+", "");
					String split[] = strLine.split(",");
					
					for(int i=0;i<split.length;i++)
					{
						String subsplit[] = split[i].split("=");
						model.put(subsplit[0], subsplit[1]);
					}
					satPointsModelList.add(model);
				}
				lineNum++;
			}
			//Close the input stream
			br.close();
		
		} catch (FileNotFoundException e) { e.printStackTrace(); } 
		  catch (IOException e) { e.printStackTrace(); }
	}
	
	/**
	 * Read a log file of exploration to read SAT / UNSAT / TIMED OUT points.
	 * 
	 * @param fileName name of the file to be read
	 * @param paramStrings String in the file between the information field
	 * @param listToAdd list where all the read points will be stored
	 */
	private void readFileToArray (String fileName, String paramStrings[],  List<Point> listToAdd)
	{
		FileInputStream fstream;
		try
		{			
			fstream = new FileInputStream(fileName);
			
			BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
			
			String strLine;

			//Read File Line By Line
			while ((strLine = br.readLine()) != null)   
			{
				for (int i=0;i<dimensions;i++)
					strLine = strLine.replace(paramStrings[i], "");
				// Remove unwanted spaces.		
				strLine = strLine.replaceAll("( )+", " ");
				strLine = strLine.trim();				
				// split the string to numbers
				String split[] = strLine.split(" ");
				
				int [] point = new int [dimensions];
				for (int i=0;i<dimensions;i++)
					point[i] = Integer.parseInt(split[i]);
				
				listToAdd.add(new Point(point));
			}

			//Close the input stream
			br.close();			
			
		} catch (FileNotFoundException e) { e.printStackTrace(); } 
		  catch (IOException e) { e.printStackTrace(); }
	}
	
	/**
	 * If we want to continue a previous exploration, we could just read the log file and 
	 * continue from the point where the exploration stopped. If we stopped in middle of a
	 * SMT query, of course that query is lost.
	 * 
	 * @param resultsDirectory the directory where all the log files are stored.
	 */
	public void readExploredPoints (String resultsDirectory) 
	{	
		String paramStrings[] = new String[dimensions];
		paretoPoints = new ArrayList<Point>();
		
		for (int i=0;i<dimensions;i++)
			paramStrings[i] = explParams.getConstraintName (i) + " :";
		
		readFileToArray (resultsDirectory + satPointsFileName, paramStrings, satPointsList);
		// readFileToArray (resultsDirectory + unsatPointsFileName, paramStrings, unsatPointsList);
		// readFileToArray (resultsDirectory + timedOutPointsFileName, paramStrings, timedOutPointsList);
		readFileToArray (resultsDirectory + paretoPointsFileName, paramStrings, paretoPoints);
		readModelFile   (resultsDirectory +  modelFileName, satPointsModelList);	
	}
	
	/**
	 * Get list of all the SAT points found during the exploration.
	 * 
	 * @return List of SAT points
	 */
	public List<Point> getSatPoints () { return  (satPointsList); }
	// public List<Point> getUnsatPoints () { return (unsatPointsList); }
	// public List<Point> getTimedoutPoints () { return (timedOutPointsList); }
	/**
	 * Get all the Pareto points found from this exploration.
	 * 
	 * @return list of Parete points
	 */
	public List<Point> getParetoPoints () { return (paretoPoints); }
	
	/**
	 * Get list of model for each Pareto point found in exploration.
	 * 
	 * @return list of model for every Pareto point
	 */
	public List<Map<String, String>> getParetoModels ()
	{
		paretoModelList = new ArrayList<Map<String, String>>();
		if	(paretoPoints.isEmpty())
			calcParetoPoints ();
		
		for (Point paretoPoint : paretoPoints)
		{
			for (int i=0;i<satPointsList.size ();i++)
			{
				Point satPoint = satPointsList.get (i);
				
				if (satPoint.equals(paretoPoint) == true)
				{
					paretoModelList.add (satPointsModelList.get (i));
					break;
				}
			}
		}		
		return paretoModelList;		
	}
	
	/**
	 * Calculate Pareto points from the list of SAT and UNSAT points
	 */
	private void calcParetoPoints ()
	{
		paretoPoints = new ArrayList<Point>();

		// No sat Points found.
		if (satPointsList.size () == 0)
			return;
		
		paretoPoints.add (satPointsList.get(0));
		
		for (int i=1;i<satPointsList.size ();i++)
		{
			boolean addToParetoList = true;
			Point point1 = satPointsList.get (i);
			
			for (int j=0;j<paretoPoints.size ();j++)
			{
				Point point2 = paretoPoints.get (j);
				
				if (point1.greaterThanOrEquals(point2))
				{
					// Greater -- No need to compare further.
					// We found that it is not a pareto point.
					addToParetoList = false;
					break;
				}
				else if (point1.lessThanOrEquals(point2))
				{
					// Smaller
					// This is not a pareto point, remove it from pareto list.
					paretoPoints.remove (j);
					j--;
				}
				else
				{
					// Incomparable Points
					// Nothing to be done. 
				}				
			}
			
			if (addToParetoList == true)			
				paretoPoints.add (point1);
			
		}
		
		for (int i=0;i<paretoPoints.size ();i++)
		{
			String pointString= "";
			int point[] = paretoPoints.get(i).getIntegerCoordinates();
			
			for (int j=0;j<dimensions;j++)
				pointString = pointString.concat (explParams.getConstraintName (j) 
											+ " : " + point[j] + " ");
			
			outputToFile (paretoPointsfile, pointString + "\n");
		}					
	}
	
	/**
	 * Check if the granularity of the exploration has reached for each dimension.
	 * 
	 * @return true if exploration granularity has reached, else false
	 */
	private boolean checkIfExplorationGranularityReached()
	{
		for (int i=0;i<dimensions;i++)
		{
			int point1 = (int) (lowerBounds[i] + (upperBounds[i] - lowerBounds[i]) * epsilon);
			int point2 = (int) (lowerBounds[i] + (upperBounds[i] - lowerBounds[i]) * epsilon * 2);
			
			if (explParams.getExplorationGranularity (i) < (point2 - point1))
				return false;
		}
		
		System.out.println("Epsilon : " + epsilon+ " Gran : " + explParams.getExplorationGranularity (0) +", " + explParams.getExplorationGranularity (1));
		return true;
	}
	
	
	/**
	 * Perform a SMT query.
	 * 
	 * @param queryPoint The point where the query should be asked.
	 * @return SAT / UNSAT / TIMED OUT depending on the result from the SMT solver.
	 */
	private SatResult performQuery (Point queryPoint)
	{
		SatResult result = smtQuery (queryPoint.getIntegerCoordinates());
		
		// System.out.print(" Query Point : " + queryPoint);
		
		if (result == SatResult.SAT)
		{
			Point queryModel = new Point(explParams.getCostsFromModel());
			satPointsList.add (queryModel);
			
			Map<String, String> model = explParams.getModelFromSolver ();
			satPointsModelList.add (model);
			
			// System.out.print(" Model unscaled" + queryModel +" scaled : " + scaledModel);
			addSatToList (queryModel);					
		}
		else if (result == SatResult.UNSAT)
		{
			// unsatPointsList.add (new Point(queryPoint));
			addUnsatToList (new Point(queryPoint));
		}
		else if ((result == SatResult.TIMEOUT) || (result == SatResult.UNKNOWN))
		{
			// timedOutPointsList.add (new Point(queryPoint));
			addUnsatToList (new Point(queryPoint));
		}
		
		explParams.popSolverContext (1);
		explParams.pushSolverContext ();
		System.out.println(" Total Time : " + totalExplTime/1000 + " seconds");
		
		return result;
	}
	
	/**
	 * Calculate where the point would reside in the design space.
	 * 
	 * Imagine the grid has 5 steps in horizontal and vertical dimension
	 * in 2D exploration. So there will be total 25 intersections. If we
	 * number these points, we want to know the co-ordinates in the design
	 * space to perform a query.
	 * 
	 * @param dimension dimension where the value is to be obtained
	 * @param epsilon current epsilon value
	 * @param point the number of point
	 * @return value corresponding to the design space.
	 */
	private double pointToValue (int dimension, double epsilon, int point)
	{
		return (lowerBounds[dimension] + point * epsilon * (upperBounds[dimension] - lowerBounds[dimension]));
	}
	
	/**
	 * Perform a binary search on a given dimension.
	 * 
	 * @param dimension dimension to be explored
	 * @param lowerBound lower bound to be explored
	 * @param upperBound upper bound to be explored
	 * @param queryPoint current Point which is being queried
	 * @param higherDimEpsilon epsilon to be used for the higher dimension
	 * @return true if global timeout, false otherwise
	 */
	private boolean binarySearch (int dimension, double lowerBound, double upperBound, Point queryPoint, double higherDimEpsilon)
	{
		boolean timeOut = false;

		if(dimension != (dimensions-1))
		{			
			double currentEpsilon = 0.5;
			
			// Only for the higher dimensions, we used the passed epsilon.
			// this will make the search easier. we search sparsely seperated
			// points in higher dimension and then move to the closely located
			// points later.
			while(currentEpsilon >= higherDimEpsilon)
			{
				int numPoints = (int) (1 / currentEpsilon)+1;				
				int lowerPoint = 0;
				int upperPoint = numPoints-1;
			
				while (lowerPoint <= upperPoint)
				{
					queryPoint.set(dimension, pointToValue (dimension, epsilon, lowerPoint));				
					timeOut = binarySearch (dimension+1, lowerBounds[dimension+1], upperBounds[dimension+1], queryPoint, currentEpsilon);
					if(timeOut == true) return timeOut;
					lowerPoint++;
				}
				
				currentEpsilon /= 2;
			}
		}
		else
		{
			// Last Dimension : We don't use the passed epsilon, but
			// the real epsilon.
			
			// We check if the lowest point in this dimension is 
			// SAT, no need to do a binary search,
			// the search is already done.
			queryPoint.set(dimension, lowerBounds[dimension]);
			if(checkIfSatOrUnsat (queryPoint) == SatResult.SAT)
				return false;
			
			// Similarly, if we have the highest point as unsat, then
			// there is no point in exploring the other points.
			queryPoint.set(dimension,  upperBounds[dimension]);
			if(checkIfSatOrUnsat (queryPoint) == SatResult.UNSAT)
				return false;
			
			// We already have a set of SAT and UNSAT Points. 
			// We should explored in the true exploration bounds.
			double bounds[] = getTrueBounds(queryPoint);
			
			int numPoints = (int) (1 / epsilon)+1;				
			int lowerPoint = 0;
			int upperPoint = numPoints-1;
			
			if(bounds[0] > lowerBound)
			{
				while(bounds[0] > lowerBound)
				{
					lowerPoint++;
					lowerBound += (upperBounds[dimension] - lowerBounds[dimension] * epsilon);
				}
			}
			
			if(bounds[1] > upperBound)
			{
				while(bounds[1] < upperBound)
				{
					upperPoint--;
					upperBound -= (upperBounds[dimension] - lowerBounds[dimension] * epsilon);
				}
			}			
			
			while (lowerPoint <= upperPoint)
			{
				int currentPoint = (int) (lowerPoint + Math.floor(((double)(upperPoint - lowerPoint)/2)));
				
				queryPoint.set(dimension, pointToValue (dimension, epsilon, currentPoint));
				
				SatResult result = checkIfSatOrUnsat (queryPoint);
				if (result == SatResult.UNKNOWN)
				{
					result = performQuery(queryPoint);
				}

				if(result == SatResult.SAT)
					upperPoint = currentPoint - 1;
				else
					lowerPoint = currentPoint + 1;
				
				// Return true if we finished the global time-budget
				if ((((totalExplTime/1000) > totalQueryTimeOutInSeconds)) || (containsLowestPoint() == true))
					return true;
			}
		}
		
		return false;
	}

	/**
	 * Get bounds according to the unknown area where we should perform the binary search
	 * exploration.
	 * 
	 * @param queryPoint Current query point under consideration
	 * @return Returns array containing lower and upper bound for the last dimension
	 */
	private double[] getTrueBounds(Point queryPoint)
	{		
		Point tempPoint = new Point(queryPoint);
		double bounds[] = new double[2];
		
		tempPoint.set(dimensions-1, lowerBounds[dimensions-1]);
		// Find the minimal sat point.
		for(int i=0;i<algoUnsatPointsList.size();i++)
		{
			Point unsatPoint = algoUnsatPointsList.get(i);
			if(unsatPoint.greaterThanOrEquals(tempPoint))
				tempPoint.set(dimensions-1, unsatPoint.get(dimensions-1));
		}
		
		bounds[0] = tempPoint.get(dimensions - 1);
					
		tempPoint.set(dimensions-1, upperBounds[dimensions-1]);
		
		// Find the minimal sat point.
		for(int i=0;i<algoSatPointsList.size();i++)
		{
			Point satPoint = algoSatPointsList.get(i);
			if(satPoint.lessThanOrEquals(tempPoint))
				tempPoint.set(dimensions-1, satPoint.get(dimensions-1));
		}
		
		bounds[1] = tempPoint.get(dimensions - 1);	
		
		return bounds;
	}

	/**
	 * If the exploration happens such that the point (0,0) returns a SAT value,
	 * then there is no need in exploring other points, because it will anyways 
	 * dominate all the other points.
	 * 
	 * TODO : Perhaps this was old method, I must check if this should be still used
	 * or not.
	 * 
	 * @return true if sat point list contains the lowest dominating point.
	 */
	private boolean containsLowestPoint()
	{
		// If the sat points contains the lowest point (0,0) it will
		// dominate every thing else and hence the list size will be equal to 1.
		if((algoSatPointsList.size() == 1) && (algoSatPointsList.get(0).equals(new Point(dimensions, 0.0))))
				return true;
		return false;
	}
	
	/**
	 * Prints the upper, lower bounds and exploration granularity.
	 */
	private void printLimitInfo()
	{
		System.out.print("Exploration Limits");
		for(int i=0;i<dimensions;i++)
		{
			System.out.print(" :: " + explParams.getConstraintName(i) + " (" + lowerBounds[i] + ", " 
					+ upperBounds[i] + ")");
		}
		
		System.out.print("\nExploration Granularity (" + explParams.getExplorationGranularity(0));
		for(int i=1;i<dimensions;i++)
			System.out.print(", " + explParams.getExplorationGranularity(i));
		System.out.println(")");
	}

	/**
	 * Perform grid-based exploration
	 */
	public void explore ()
	{
		// Get the Exploration Bounds
		lowerBounds = explParams.getLowerBounds ();
		upperBounds = explParams.getUpperBounds ();
		
		printLimitInfo();
		
		Point queryPoint = new Point(dimensions);
		// initialize point to zero.
		queryPoint.set(0.0);
		
		explParams.pushSolverContext ();
		
		// Initialiaze the epsilon to 0.5
		epsilon = 0.5;
		
		boolean timeout = false;
		
		while (timeout == false)
		{
			timeout = binarySearch (0, lowerBounds[0], upperBounds[0], queryPoint, epsilon);
			if(checkIfExplorationGranularityReached() == true)
				break;
			epsilon /= 2;
			
		}
		
		// Calculate the pareto points
		calcParetoPoints ();
		
		System.out.println ("Finished Exploration in " + (totalExplTime/1000) + " seconds");
	}
	
	
	
	// Note: We ignore the timed out points in this case.
	// Suppose we want to make timedout points = unsat points
	// then if (timedOutPointsList.get (i)[j] != point[j]) should be
	// changed as if (point[j] > timedOutPointsList.get (i)[j])
	
	/**
	 * Check if the point falls in SAT or UNSAT known area.
	 * Known area is forward cone of SAT point and backward cone of UNSAT point.
	 * 
	 * @param point point to be checked if it falls in unknown area
	 * @return SAT if falls in sat area, UNSAT if in unsat area, or UNKNOWN if in unknown area
	 */
	private SatResult checkIfSatOrUnsat (Point point)
	{
		// Check for SatPoints
		for (int i=0;i<algoSatPointsList.size ();i++)
		{
			if (point.greaterThanOrEquals(algoSatPointsList.get(i)))
				return SatResult.SAT;				
		}
		
		// Check for UnsatPoints
		for (int i=0;i<algoUnsatPointsList.size ();i++)
		{
			if (point.lessThanOrEquals(algoUnsatPointsList.get (i)))
				return SatResult.UNSAT;
		}
		
		return SatResult.UNKNOWN;
	}	
	
	
	/****************************************************************************************************************
	 * 
	 * Below this point all the code is of the old implementation.
	 * It might have bugs, but I don't think there are any. I remember
	 * we had some problem with normalizing and de-normalizing of the
	 * points to a range of (0.0,1.0) for every dimension. But later
	 * we changed to take this into account and removed normalization of
	 * points.
	 * 
	 * We improvised 
	 * the old version to perform binary search in dimensions instead of 
	 * linear search to explore the design space in a better way.
	 * 	 
	 *****************************************************************************************************************/
	
//	/**
//	 * Calculate the lower bound on the epsilon, below which the exploration
//	 * granularity will be reached. So exploration should be stopped afterwards.
//	 * This method is used in old implementation.
//	 * 
//	 * @param lowerBounds Lower bounds of the exploration
//	 * @param upperBounds Upper bounds of the exploration
//	 * @return array of lower bounds on the epsilon values for each dimension 
//	 */
//	private double [] calcEpsilonLowerBound(int lowerBounds[], int upperBounds[])
//	{
//		double result[] = new double[dimensions];
//		
//		for(int i=0;i<dimensions;i++)
//		{
//			double bound = (upperBounds[i] - lowerBounds[i]);
//			double granularity = explParams.getExplorationGranularity (i);	
//		
//			result[i] = 1.0 / (granularity > 1.0 ? granularity : bound);
//		}
//		
//		return result;
//	}
	
//	/**
//	 * Check if this point falls in unknown area.
//	 * Known area is forward cone of SAT point and backward cone of UNSAT point.
//	 * This method is used in old implementation.
//	 * 
//	 * @param point point to be checked if it falls in unknown area
//	 * @return true if falls in unknown area, false otherwise
//	 */
//	private boolean checkIfUnknown (Point point)
//	{		
//		// Check for SatPoints
//		for (int i=0;i<algoSatPointsList.size ();i++)
//		{
//			if (point.greaterThanOrEquals(algoSatPointsList.get(i)))
//				return false;				
//		}
//		
//		// Check for UnsatPoints
//		for (int i=0;i<algoUnsatPointsList.size ();i++)
//		{
//			if (point.lessThanOrEquals(algoUnsatPointsList.get (i)))
//				return false;
//		}
//		
//		return true;
//	}
//	
//	/**
//	 * Old implementation of grid-based pareto exploration.
//	 */
//	public void oldParetoExploration ()
//	{
//		epsilon = 1.0;
//		totalExplTime = 0;
//		
//		boolean iterationFinished = true;
//		int lowerBounds[] = explParams.getLowerBounds ();
//		int upperBounds[] = explParams.getUpperBounds ();
//		
//		// scalePoints = new ScalePoint(lowerBounds, upperBounds);
//		
//		double epsilonLowerBound[] = calcEpsilonLowerBound(lowerBounds, upperBounds);
//		double epsilonPerDim[] = new double[dimensions];
//		for(int i=0;i<dimensions;i++)
//			epsilonPerDim[i] = 1.0;
//		
//		Point queryPoint = new Point(dimensions);
//		
//		explParams.pushSolverContext ();
//		
//		while (((totalExplTime/1000) <= totalQueryTimeOutInSeconds))
//		{
//			if (iterationFinished)
//			{
//				// If iteration has finished, we should proceed to the next one.
//				epsilon /= 2;
//				
//				for(int i=0;i<dimensions;i++)
//				{
//					if(epsilonPerDim[i]/2.0 >= epsilonLowerBound[i])
//						epsilonPerDim[i] /= 2.0;
//				}
//				
//				iterationFinished = false;
//				queryPoint.set(0.0);
//			}
//			
//			if (checkIfUnknown (queryPoint) == true)
//			{
//				SatResult result = smtQuery (queryPoint.getIntegerCoordinates());
//				
//				if (result == SatResult.SAT)
//				{
//					Point queryModel = new Point(explParams.getCostsFromModel());
//					satPointsList.add (queryModel);
//					
//					Map<String, String> model = explParams.getModelFromSolver ();
//					satPointsModelList.add (model);					
//					
//					addSatToList (queryModel);
//					
//				}
//				else if (result == SatResult.UNSAT)
//				{
//					// unsatPointsList.add (new Point(queryPoint));
//					addUnsatToList (new Point(queryPoint));
//				}
//				else if ((result == SatResult.TIMEOUT) || (result == SatResult.UNKNOWN))
//				{
//					// timedOutPointsList.add (new Point(queryPoint));
//					addUnsatToList (new Point(queryPoint));
//				}
//				
//				explParams.popSolverContext (1);
//				explParams.pushSolverContext ();
//			}
//					
//			queryPoint.set(0, queryPoint.get(0) + epsilonPerDim[0]);
//			
//			// Check if we have to update the higher dimensions.
//			if (queryPoint.get(0) > upperBounds[0])
//			{
//				int i=1;
//				queryPoint.set(0, lowerBounds[0]);
//				while (true)
//				{
//					if (i == dimensions)
//					{
//						iterationFinished = true;						
//						break;
//					}
//					
//					queryPoint.set(i, (queryPoint.get(i)+epsilonPerDim[i]));
//					if (queryPoint.get(i) > upperBounds[i])
//					{
//						queryPoint.set(i, lowerBounds[i]);
//						i++;
//					}
//					else
//						break;
//				}				
//			}
//			
//			// Check for exploration granularity.
//			if ((iterationFinished == true) && (checkIfExplorationGranularityReached() == true))
//			{
//				System.out.println ("Exploration Granularity Limit Reached");
//				break;
//			}
//		}
//		
//		calcParetoPoints ();
//		System.out.println ("Finished Exploration in " + (totalExplTime/1000) + " seconds");
//	}
	
}
