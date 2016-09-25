package exploration.paretoExploration.distanceexploration;

import java.util.*;
import solver.Z3Solver.SatResult;
import exploration.*;

public class DistanceBasedExploration extends Explorer 
{
	private List<Point> satPointsList; 					// S_1 in the paper.
	private List<Point> unsatPointsList; 					// S_1 in the paper.
	Knee kneeTreeRoot;									// Root of Knee Tree.	
	private int lowerBounds[];
	private int upperBounds[];

	public DistanceBasedExploration (String opDir, int perQueryTimeOutSeconds, 
								int totalTimeOutInSeconds, ExplorationParameters explParams) 
	{
		super (opDir, explParams.getDimensions (), perQueryTimeOutSeconds, totalTimeOutInSeconds, explParams);
		
		if (dimensions < 2)
			throw new RuntimeException ("At least 2 dimensions should be present.");

		satPointsList = new ArrayList<Point>();
		unsatPointsList = new ArrayList<Point>();
	}
	
	private void propSat (Knee kneePoint, Point s)
	{
		if(s.lessThanOrEquals(kneePoint.getB()))
		{
			double r = 0;
			Point b = new Point(dimensions);
			
			if(kneePoint.numDescendants() > 0)
			{
				for(int i=0;i<dimensions;i++)
				{
					Knee desc = kneePoint.getDescendant(i);
					if(desc != null)
					{
						propSat (desc, s);
						r = ((desc.getR() > r) ? desc.getR() : r);
						b = b.calculateMeet(desc.getB());
					}
				}
			}
			else
			{
				double distance = kneePoint.getG().distance(s);
				r = ((kneePoint.getR() < distance) ? kneePoint.getR() : distance);
				b = kneePoint.getG().plus(r);
			}
			
			kneePoint.setR(r);
			kneePoint.setB(b);
		}
	}
	
	private boolean propUnSat (Knee kneePoint, Point s)
	{
		boolean ex = true;
		if(kneePoint.getG().lessThan(s))
		{
			ex = false;
			double r = 0;
			Point b = new Point(dimensions);
			
			if(kneePoint.numDescendants() != 0)
			{
				// Non-leaf node
				for(int i=0;i<dimensions;i++)
				{
					Knee desc = kneePoint.getDescendant(i);
					if(desc != null)
					{
						boolean exDesc = propUnSat (desc, s);
						if(exDesc == false)
							kneePoint.removeDescendant(i);
						else
						{
							ex = true;
							r = ((desc.getR() > r) ? desc.getR() : r);
							b = b.calculateJoin(desc.getB());
						}
					}
				}
			}
			else
			{
				// Leaf- Node
				for(int i=0;i<dimensions;i++)
				{					
					if(s.get(i) < kneePoint.getH(i))
					{
						ex = true;
						// create a new node.
						
						Point unsatGens[] = new Point[dimensions];
						for(int k=0;k<dimensions;k++)
							unsatGens[k] = kneePoint.getUnsatGen(k);
						// unsatGens[i] = s;
						
						Point point = orderedMeet(unsatGens);
						point.set(i, s.get(i));						
						
						Knee newKneeNode = new Knee(point);						
						
						newKneeNode.setR (distanceWithSatPoints(newKneeNode.getG()));
						newKneeNode.setB(newKneeNode.getG().plus(newKneeNode.getR()));
						
						kneePoint.addDescendant(i, newKneeNode);
						
						for(int j=0;j<dimensions;j++)
						{
							if(i != j)
								newKneeNode.addUnsatGen(j, kneePoint.getUnsatGen(j));
							else
								newKneeNode.addUnsatGen(j, s);
						}
						
						unsatGens[i] = s;
						double h[] = hVector(unsatGens);
						for(int j=0;j<dimensions;j++)
							newKneeNode.setH(h[j], j);

						if(newKneeNode.checkGenerators() == false)
							throw new RuntimeException("Check Generators failed.");
						
						r = ((r > newKneeNode.getR()) ? r :  newKneeNode.getR());
						b = b.calculateJoin(newKneeNode.getB());
					}							
				}
			}
			
			kneePoint.setR(r);
			kneePoint.setB(b);
		}		
		return ex;
	}
	
	private Point[] orderedSet (Point[] points)
	{
		Point orderedSet[] = new Point[points.length];
		if (points.length != dimensions)
			throw new RuntimeException("I don't know how to handle this case");
		
		for(int i=0;i<dimensions;i++)
		{
			int point=-1;
			double value = Double.MAX_VALUE;
			for(int j=0;j<points.length;j++)
			{				
				if((points[i] != null) && (value > points[i].get(j)))
				{
					value = points[i].get(j);
					point = i;
				}
			}
			
			orderedSet[i] = points[point];
		}
		
		return orderedSet;
	}
	
	private double[] hVector (Point[] unsatGens)
	{
		double[] result = new double[dimensions];
	
		for(int i=0;i<dimensions;i++)
		{
			double minValue = Double.MAX_VALUE;
			for(int j=0;j<dimensions;j++)
			{
				if(i == j) continue;
				if(unsatGens[j].get(i) < minValue)
					minValue = unsatGens[j].get(i);
			}
			result[i] = minValue;
		}		
		return result;
	}
	
	
	private Point orderedMeet(Point[] unsatGens)
	{
		Point orderedSet[] = orderedSet(unsatGens);
		
		Point point = new Point (dimensions);
		
		for(int i=0;i<dimensions;i++)			
			point.set(i, orderedSet[i].get(i));
		
		return point;
	}

	private double distanceWithSatPoints (Point p)
	{
		double distance = Double.MAX_VALUE;
		for(Point satPt : satPointsList)
		{
			double ptDist = p.distance(satPt);
			if(distance > ptDist)
				distance = ptDist;
				
		}
		return distance;
	}
	
	private Point[] selectPoint (Knee kneePoint)
	{
		double maxDistance = Double.MIN_VALUE;
		
		// Where do we find how to select a point?
		if(kneePoint.numDescendants() == 0)
		{
			Point result[] = new Point[2];
			for(Point satPoint : satPointsList)
			{
				double tempDist = kneePoint.getG().distance(satPoint);
				if(tempDist > maxDistance)
				{
					maxDistance = tempDist;
					result[0] = satPoint;
					result[1] = kneePoint.getG();
				}
			}
			return result;
		}
		else
		{
			Point result[] = new Point[2];
			result[0] = result[1] = null;
			
			for(int i=0;i<dimensions;i++)
			{
				if(kneePoint.getDescendant(i) != null)
				{
					Point tempResult[] = selectPoint (kneePoint.getDescendant(i));
					double tempDistance = tempResult[1].distance(tempResult[0]);
					
					Point queryPoint = tempResult[0].plus(tempResult[1]);
					queryPoint = queryPoint.divide(2);
					if(unsatPointsContain(queryPoint) == true)
						tempDistance = Double.MIN_VALUE;
					
					if(tempDistance >= maxDistance)
					{
						result = tempResult;
						maxDistance = tempDistance;
					}
				}
			}
			
			return result;
		}
	}
	
	private boolean unsatPointsContain(Point queryPoint)
	{
		for(Point pt : unsatPointsList)
		{
			if(pt.equals(queryPoint) == true)
				return true;
		}
		return false;
	}

	private void addSatPointToList (Point p)
	{
		// Remove the dominated sat points.
		for(int i=0;i<satPointsList.size();i++)
		{
			Point satPoint = satPointsList.get(i);
			if(p.lessThan(satPoint) == true)
			{
				satPointsList.remove(i);
				i--;
			}
		}
		
		satPointsList.add(p);
	}
	
	public void paretoExploration ()
	{
		totalExplTime = 0;

		// Get the Exploration Bounds
		lowerBounds = explParams.getLowerBounds ();
		upperBounds = explParams.getUpperBounds ();
		
		// New object to calculate the scaling factor.
		ScalePoint scalePoints = new ScalePoint(lowerBounds, upperBounds);
		
		// Push the context before we start.
		explParams.pushSolverContext ();		
		
		// Perform the initialization		
		satPointsList.add(new Point(dimensions, (float) 1.0));
		
		kneeTreeRoot= new Knee(dimensions, 1.0);
		for(int i=0;i<dimensions;i++)
			kneeTreeRoot.setH(1.0, i);
		
		kneeTreeRoot.setB(new Point(dimensions, (float) 1.0));
		kneeTreeRoot.setR(1.0);
		
		if(kneeTreeRoot.checkGenerators() == false)
			throw new RuntimeException("Check Generators failed.");
		
		while ((totalExplTime/1000) <= totalQueryTimeOutInSeconds)
		{
			Point[] minDistPoints = selectPoint (kneeTreeRoot);			
			
			Point queryPoint = minDistPoints[0].plus(minDistPoints[1]);
			queryPoint = queryPoint.divide(2);
			
			System.out.println("Select Point " + minDistPoints[0] +", "+ minDistPoints[1]+ "  Query Point : " + queryPoint);
			Point scaledPoint = scalePoints.unScale(queryPoint);
			
			SatResult result = smtQuery (scaledPoint.getIntegerCoordinates());
			
			if(result == SatResult.SAT)
			{
				int [] queryModel = explParams.getCostsFromModel ();
				Point scaledPt = scalePoints.scale(new Point(queryModel));				
				
				propSat(kneeTreeRoot, scaledPt);
				
				addSatPointToList(queryPoint);
			}
			else
			{
				propUnSat (kneeTreeRoot, queryPoint);
				unsatPointsList.add(queryPoint);
			}			
		}
		
		System.out.println ("Finished Exploration in " + totalExplTime + " seconds");
	}	
}
