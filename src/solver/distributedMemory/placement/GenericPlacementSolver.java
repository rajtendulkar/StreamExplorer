package solver.distributedMemory.placement;

import java.util.*;

import platform.model.*;

import com.microsoft.z3.*;
import solver.*;
import designflow.DesignFlowSolution;
import designflow.DesignFlowSolution.*;
import exploration.interfaces.oneDim.CommunicationCostConstraints;

/**
 * Generic placement solver which maps a group of SDF actors to 
 * a cluster on target platform.
 * 
 * @author Pranav Tendulkar
 *
 */
public class GenericPlacementSolver extends Z3Solver implements CommunicationCostConstraints
{
	/**
	 * Partitioning solution
	 */
	Partition partition;
	/**
	 * Target platform
	 */
	Platform platform;
	/**
	 * SMT variables
	 */
	private Map<String, Expr> varDecl;
	/**
	 * Do we used modulo distances based on 
	 * arithmetic calculation (true) 
	 * or pre-calculated distances (false)?
	 */
	public boolean useModuloDistances = false;
	
	/**
	 * Build a placement solver.
	 * 
	 * @param partition partitioning solution
	 * @param platform target platform
	 */
	public GenericPlacementSolver (Partition partition, Platform platform)
	{
		this.partition = partition;
		this.platform = platform;
		varDecl = new TreeMap<String, Expr>();
	}
	
	/**
	 * Get SMT variable for communication cost between two groups
	 * 
	 * @param group1 group 1
	 * @param group2 group 2
	 * @return variable for communication cost between two groups
	 */
	private IntExpr commCostId (int group1, int group2)
	{
		return (IntExpr) varDecl.get (SmtVariablePrefixes.commCostPrefix + "_"  
										+ Integer.toString(group1) + "_" 
										+ Integer.toString(group2));
	}
	
	/**
	 * Get SMT variable for distance between two groups
	 * 
	 * @param group1 group 1
	 * @param group2 group 2
	 * @return variable for distance between two groups
	 */
	private IntExpr distanceId (int group1, int group2)
	{
		return (IntExpr) varDecl.get (SmtVariablePrefixes.distancePrefix + "_"  
										+ Integer.toString(group1) + "_" 
										+ Integer.toString(group2));
	}
	
	/**
	 * Get SMT variable for distance in x-dimension between two groups
	 * 
	 * @param group1 group 1
	 * @param group2 group 2
	 * @return variable for distance in x-dimension between two groups
	 */
	private IntExpr xDistanceId (int group1, int group2)
	{
		return (IntExpr) varDecl.get (SmtVariablePrefixes.xDistancePrefix + "_"  
										+ Integer.toString(group1) + "_" 
										+ Integer.toString(group2));
	}
	
	/**
	 * Get SMT variable for distance in y-dimension between two groups
	 * 
	 * @param group1 group 1
	 * @param group2 group 2
	 * @return variable for distance in y-dimension between two groups
	 */
	private IntExpr yDistanceId (int group1, int group2)
	{
		return (IntExpr) varDecl.get (SmtVariablePrefixes.yDistancePrefix + "_"  
										+ Integer.toString(group1) + "_" 
										+ Integer.toString(group2));
	}
	
	/**
	 * Get SMT variable for total communication cost of the problem 
	 * @return variable for total communication cost of the problem
	 */
	private IntExpr totalCommCostId () { return (IntExpr) varDecl.get (SmtVariablePrefixes.totalCommCostPrefix); }
	
	/**
	 * Get SMT variable for group to cluster allocation for a given group.
	 * 
	 * @param group group id
	 * @return variable for group to cluster allocation
	 */
	private IntExpr getClusterGroupAllocationId (int group) 
	{ 
		return (IntExpr) varDecl.get(SmtVariablePrefixes.partitionClusterAllocationPrefix + Integer.toString(group)); 
	}
	
	/**
	 * Declare all the SMT variables required for solving
	 */
	private void declareVariables()
	{
		int numGroups = partition.getNumGroups();
		IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.totalCommCostPrefix, "Int");
		varDecl.put (SmtVariablePrefixes.totalCommCostPrefix, id);
		
		for(int i=0;i<numGroups;i++)
		{
			id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.partitionClusterAllocationPrefix + Integer.toString(i), "Int");
			varDecl.put (SmtVariablePrefixes.partitionClusterAllocationPrefix + Integer.toString(i), id);
		}
		
		for(int i=0;i<numGroups;i++)
		{
			for(int j=i+1;j<numGroups;j++)
			{				
				if(partition.commCostBetweenGroups(i, j) > 0)
				{
					if (useModuloDistances == true)
					{
						String xDist = SmtVariablePrefixes.xDistancePrefix + "_"  + Integer.toString(i) + "_" + Integer.toString(j);
						String yDist = SmtVariablePrefixes.yDistancePrefix + "_"  + Integer.toString(i) + "_" + Integer.toString(j);					
					
						id = (IntExpr) addVariableDeclaration (xDist, "Int");
						varDecl.put (xDist, id);
					
						id = (IntExpr) addVariableDeclaration (yDist, "Int");
						varDecl.put (yDist, id);
					}
					else
					{
						String dist = SmtVariablePrefixes.distancePrefix + "_"  + Integer.toString(i) + "_" + Integer.toString(j);
						
						id = (IntExpr) addVariableDeclaration (dist, "Int");
						varDecl.put (dist, id);
					}
					
					String commCost = SmtVariablePrefixes.commCostPrefix + "_"  + Integer.toString(i) + "_" + Integer.toString(j);
					id = (IntExpr) addVariableDeclaration (commCost, "Int");
					varDecl.put (commCost, id);
				}				
			}
		}
	}
	
	/**
	 * Generate if then else statements such that 
	 * if group1 is on cluster 1 and group2 is on cluster 2 then
	 * distance between them is x-units.
	 */
	public void ifElseDistanceCalculation ()
	{
		int numGroups = partition.getNumGroups();
		List<int[]> costList = new ArrayList<int[]>();
		
		// create a cost list first.
		for(int i=0;i<numGroups;i++)
		{
			for(int j=i+1;j<numGroups;j++)
			{
				int commCostBetweeenGroups = partition.commCostBetweenGroups(i, j);
				if(commCostBetweeenGroups > 0)
				{
					int [] point = new int[3];
					point[0] = i;
					point[1] = j;
					point[2] = commCostBetweeenGroups;
					costList.add(point);
				}
			}
		}
		
		// generate if else list.
		HashMap<Integer, List<int[]>> distanceMap = platform.getDistanceMap();
		
		for(int distance : distanceMap.keySet())
		{
			List<int[]> distanceCost = distanceMap.get(distance);
			
			for(int[] dist : distanceCost)
			{
				int srcCluster = dist[0];
				int dstCluster = dist[1];
				
				for (int[]groups : costList)
				{
					int srcGroup = groups[0];
					int dstGroup = groups[1];
					
					try
					{
						generateAssertion(ctx.mkImplies(
									ctx.mkAnd(
											ctx.mkEq(getClusterGroupAllocationId(srcGroup), ctx.mkInt(srcCluster)),
											ctx.mkEq(getClusterGroupAllocationId(dstGroup), ctx.mkInt(dstCluster))),
									ctx.mkEq(distanceId(srcGroup, dstGroup), ctx.mkInt(distance))));
						
						generateAssertion(ctx.mkImplies(
								ctx.mkAnd(
										ctx.mkEq(getClusterGroupAllocationId(srcGroup), ctx.mkInt(dstCluster)),
										ctx.mkEq(getClusterGroupAllocationId(dstGroup), ctx.mkInt(srcCluster))),
								ctx.mkEq(distanceId(srcGroup, dstGroup), ctx.mkInt(distance))));						
					} catch (Z3Exception e) { e.printStackTrace(); }					
				}				
			}			
		}
		
		
		for (int[]groups : costList)
		{
			int srcGroup = groups[0];
			int dstGroup = groups[1];
			int commCost = groups[2];
			
			try 
			{						
				// (assert (= commCostP0P1 (* distP0P1 commCostBetweenPartition)))
				generateAssertion(ctx.mkEq(commCostId(srcGroup,dstGroup), ctx.mkMul(ctx.mkInt(commCost),  distanceId(srcGroup, dstGroup))));
				
			} catch (Z3Exception e) { e.printStackTrace(); }			
		}		
	}
	
	/**
	 * This works only for Mesh platforms.
	 * Assume we number all the clusters from 0 to 15.
	 * Then we can calculate the distance between two clusters by
	 * simple arithmetic calculation. This methods does it assuming 
	 * mesh network.
	 */
	public void moduloDistanceCalculation ()
	{
		int numGroups = partition.getNumGroups();
		
		for(int i=0;i<numGroups;i++)
		{
			for(int j=i+1;j<numGroups;j++)
			{				
				int commCostBetweeenGroups = partition.commCostBetweenGroups(i, j); 
				if(commCostBetweeenGroups > 0)
				{
					try 
					{
						IntExpr xDistId = xDistanceId(i, j);
						IntExpr yDistId = yDistanceId(i, j);
						
						generateAssertion(ctx.mkGe(xDistId, ctx.mkInt(0)));
						generateAssertion(ctx.mkGe(yDistId, ctx.mkInt(0)));
						
						
						BoolExpr orArgs[] = new BoolExpr[2];
						
						// (assert (or (= distXP0P1 (- (div clusterPartition0 4) (div clusterPartition1 4))) 
						//			   (= distXP0P1 (- (div clusterPartition1 4) (div clusterPartition0 4)))))

						orArgs[0] = ctx.mkEq(xDistId, ctx.mkSub(ctx.mkDiv(getClusterGroupAllocationId(i), ctx.mkInt(4)), 
												ctx.mkDiv(getClusterGroupAllocationId(j), ctx.mkInt(4))));
						orArgs[1] = ctx.mkEq(xDistId, ctx.mkSub(ctx.mkDiv(getClusterGroupAllocationId(j), ctx.mkInt(4)), 
								ctx.mkDiv(getClusterGroupAllocationId(i), ctx.mkInt(4))));
						
						generateAssertion(ctx.mkOr(orArgs));
						
						// (assert (or (= distYP0P1 (- (mod clusterPartition0 4) (mod clusterPartition1 4))) 
						//			   (= distYP0P1 (- (mod clusterPartition1 4) (mod clusterPartition0 4)))))

						orArgs[0] = ctx.mkEq(yDistId, ctx.mkSub(ctx.mkMod(getClusterGroupAllocationId(i), ctx.mkInt(4)), 
								ctx.mkMod(getClusterGroupAllocationId(j), ctx.mkInt(4))));
						orArgs[1] = ctx.mkEq(yDistId, ctx.mkSub(ctx.mkMod(getClusterGroupAllocationId(j), ctx.mkInt(4)), 
								ctx.mkMod(getClusterGroupAllocationId (i), ctx.mkInt(4))));
						
						generateAssertion(ctx.mkOr(orArgs));
						
						
						// (assert (= commCostP0P1 (* (+ distXP0P1 distYP0P1) 1)))
						generateAssertion(ctx.mkEq(commCostId(i,j), 
												ctx.mkMul(ctx.mkInt(commCostBetweeenGroups), 
														  ctx.mkAdd(xDistId, yDistId))));
						
					} catch (Z3Exception e) { e.printStackTrace(); }					
				}
			}
		}
	}
	
	/**
	 * Put Lower and upper bound on cluster where a group cal be allocated 
	 */
	private void boundsOnGroupCluster()
	{
		try 
		{
			for(int i=0;i<partition.getNumGroups();i++)
			{
				// (assert (and (>= clusterPartition0 0) (< clusterPartition0 16)))				
					generateAssertion(ctx.mkAnd(ctx.mkGe(getClusterGroupAllocationId(i), ctx.mkInt(0)),
							ctx.mkLt(getClusterGroupAllocationId(i), ctx.mkInt(platform.getNumClusters()))));
			}
		} catch (Z3Exception e) { e.printStackTrace(); }
	}
	
	/**
	 * Total distance based communication cost for the problem.
	 */
	private void totalCommCostCalculation ()
	{
		int numCommCosts = 0;
	
		int numGroups = partition.getNumGroups();
	
		for(int i=0;i<numGroups;i++)
		{
			for(int j=i+1;j<numGroups;j++)
			{
				if(partition.commCostBetweenGroups(i, j) > 0)
					numCommCosts++;
			}
		}
		
		if (numCommCosts == 0)
		{
			// Do Nothing
		}
		else if (numCommCosts == 1)
		{
			for(int i=0;i<numGroups;i++)
			{
				for(int j=i+1;j<numGroups;j++)
				{
					if(partition.commCostBetweenGroups(i, j) > 0)
					{
						try
						{
							generateAssertion(ctx.mkEq(totalCommCostId(), commCostId(i,j)));
							return;
						} catch (Z3Exception e) { e.printStackTrace(); }
					}
				}
			}						
		}
		else 
		{
			int tempArgCount = 0;
			IntExpr addArgs[] = new IntExpr[numCommCosts];
			for(int i=0;i<numGroups;i++)
			{
				for(int j=i+1;j<numGroups;j++)
				{
					if(partition.commCostBetweenGroups(i, j) > 0)
						addArgs[tempArgCount++] = commCostId(i,j);
				}
			}
			try
			{
				generateAssertion(ctx.mkEq(totalCommCostId(), ctx.mkAdd(addArgs)));
			} catch (Z3Exception e) { e.printStackTrace(); }
		}					
	}
	
	/**
	 * One group should be allocated only to one platform cluster.
	 */
	private void distinctClusterAllocation ()
	{
		try 
		{
			int numGroups = partition.getNumGroups();
			IntExpr[] groupId = new IntExpr[numGroups];
			for(int i=0;i<numGroups;i++)
					groupId[i] = getClusterGroupAllocationId(i);
			
			generateAssertion(ctx.mkDistinct(groupId));

		} catch (Z3Exception e) { e.printStackTrace(); }
	}
	
	/**
	 * Generate all the placement constraints required to solve the problem.
	 */
	public void generatePlacementConstraints ()
	{
		// Declare the variables.
		declareVariables ();
		
		// Bounds on Cluster Variable Allocation
		boundsOnGroupCluster ();
		
		distinctClusterAllocation ();
		
		// Distance Variables related statements
		if(useModuloDistances == true)
			moduloDistanceCalculation ();
		else
			ifElseDistanceCalculation ();
		
		// Total Communication Cost 
		totalCommCostCalculation ();		
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.CommunicationCostConstraints#generateCommunicationCostConstraint(int)
	 */
	@Override
	public void generateCommunicationCostConstraint(int constraintValue) 
	{
		try 
		{
			generateAssertion(ctx.mkLe(totalCommCostId(), ctx.mkInt(constraintValue)));
		} catch (Z3Exception e) { e.printStackTrace(); }
		
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.CommunicationCostConstraints#getCommunicationCost(java.util.Map)
	 */
	@Override
	public int getCommunicationCost(Map<String, String> model) 
	{
		return Integer.parseInt(model.get(SmtVariablePrefixes.totalCommCostPrefix));
	}
	
	/**
	 * Convert model obtained from SMT solver to a mapping solution.
	 * 
	 * @param model model obtained from the SMT solver
	 * @param designFlowSolution design flow solution
	 * @return Mapping solution for the given design flow solution
	 */
	public Mapping modelToMapping (Map<String, String> model, DesignFlowSolution designFlowSolution)
	{		
		Mapping mapping = designFlowSolution.new Mapping (true);
		
		int numGroups = partition.getNumGroups();
		for(int i=0;i<numGroups;i++)
		{
			int clusterAllocated = Integer.parseInt(model.get(SmtVariablePrefixes.partitionClusterAllocationPrefix + Integer.toString(i)));
			Cluster cluster = platform.getCluster(clusterAllocated);
			
			mapping.addGroupToCluster (cluster, i);
		}
		
		return mapping;
	}
}
