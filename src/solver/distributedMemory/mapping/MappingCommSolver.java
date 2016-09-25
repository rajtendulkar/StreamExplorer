package solver.distributedMemory.mapping;
import exploration.interfaces.oneDim.CommunicationCostConstraints;
import exploration.interfaces.oneDim.WorkloadImbalanceConstraints;
import exploration.interfaces.twoDim.WrkLdCommCostConstraints;
import graphanalysis.*;
import java.util.*;

import platform.model.*;
import solver.*;
import spdfcore.*;
import spdfcore.stanalys.*;
import com.microsoft.z3.*;


/**
 * Combine solving of mapping and communication cost problem.
 * We deal with number of clusters used, communication cost and workload imbalance.
 * 
 * These solver constraints are taken from Julien's Paper
 * "Multi-Criteria Optimization for Mapping Programs to Multi-Processors"
 * 
 * @author Pranav Tendulkar
 *
 */
public class MappingCommSolver extends Z3Solver
		implements WorkloadImbalanceConstraints, WrkLdCommCostConstraints, CommunicationCostConstraints
{
	/**
	 * Application Graph
	 */
	private Graph graph;
	
	/**
	 * Equivalent HSDF graph
	 */
	private Graph hsdf;
	
	/**
	 * Platform model 
	 */
	private Platform platform;
	
	/**
	 * 
	 */
	private int totalWork=0;
	
	/**
	 * Minimum distance in the network on chip between two clusters
	 */
	private int minDistance = -1;
	/**
	 * Maximum distance in the network on chip between two clusters
	 */
	private int maxDistance = -1;
	
	/**
	 * Different SMT variables
	 */
	private Map<String, Expr> varDecl;
	
	/**
	 * Build a mapping solver.
	 * 
	 * @param inputGraph input application graph (SDF)
	 * @param hsdf equivalent HSDF of application graph
	 * @param solutions solutions of application graph
	 * @param platform platform for solving the problem
	 */
	public MappingCommSolver (Graph inputGraph, Graph hsdf, Solutions solutions, Platform platform)
	{
		graph = inputGraph;
		this.platform = platform;
		this.hsdf = hsdf;	        
        
		contextStatements = new ArrayList<String>();
		varDecl = new TreeMap<String, Expr>();
		
		CalculateBounds bounds = new CalculateBounds (graph, solutions);
		totalWork = bounds.findTotalWorkLoad ();
	}
	
	/**
	 * Get Total communication cost SMT variable
	 * 
	 * @return total communication cost variable 
	 */
	private IntExpr totalCommCostId () { return (IntExpr) varDecl.get (SmtVariablePrefixes.totalCommCostPrefix); }
	
	/**
	 * Get Total workload imbalance cost SMT variable
	 * 
	 * @return total workload imbalance cost variable
	 */
	private IntExpr totalWorkImbalanceId () { return (IntExpr) varDecl.get (SmtVariablePrefixes.totalWorkImbalancePrefix); }
	
	/**
	 * Get Total clusters used cost SMT variable
	 * 
	 * @return total clusters used cost variable
	 */
	private IntExpr totalClustersUsedId () { return (IntExpr) varDecl.get (SmtVariablePrefixes.totalClustersUsedPrefix); }
	
	/**
	 * Get workload allocated to a specific cluster SMT variable
	 * 
	 * @param clusterId cluster id
	 * @return workload allocated to a cluster variable
	 */
	private IntExpr clusterAllocatedWorkId (int clusterId) { return (IntExpr) varDecl.get (SmtVariablePrefixes.clusterWorkAllocationPrefix + Integer.toString (clusterId));}
	
	/**
	 * Get workload imbalance for a cluster SMT variable
	 * 
	 * @param clusterId cluster id
	 * @return workload imbalance for a cluster variable
	 */
	private IntExpr clusterImbalanceId (int clusterId) { return (IntExpr) varDecl.get (SmtVariablePrefixes.workImbalancePrefix + Integer.toString (clusterId));}
	
	/**
	 * Get actor to cluster assignment boolean SMT variable
	 * 
	 * @param actorName name of the actor
	 * @param clusterId cluster id 
	 * @return actor to cluster assignment boolean variable
	 */
	private BoolExpr assignedClusterId (String actorName , int clusterId) { return (BoolExpr) varDecl.get (actorName + "-" + Integer.toString (clusterId)); }
	
	/**
	 * Communication distance of a channel SMT variable
	 * 
	 * @param chnnl application channel
	 * @param distance distance between producer and consumer of channel in number of hops
	 * @return boolean variable for Communication distance of a channel
	 */
	private BoolExpr commLink (Channel chnnl, int distance)
	{
		return (BoolExpr) varDecl.get (SmtVariablePrefixes.linkPrefix + chnnl.getLink (Port.DIR.OUT).getActor ().getName () 
				+ "-" + chnnl.getLink (Port.DIR.IN).getActor ().getName () + "-dist"+Integer.toString (distance));
	}
	
	/**
	 * Duration of actor 
	 * 
	 * @param actorName name of actor
	 * @return Integer expression for actor duration
	 */
	private IntNum taskDurationId (String actorName) 
	{ 
		if (actorName.contains ("_"))
		{
			String temp = actorName.substring (0, actorName.indexOf ("_"));
			return (IntNum) varDecl.get (temp);
		}
		else
			return (IntNum) varDecl.get (actorName);
	}
	
	
	
	/**
	 * Define required SMT variables for all the cluster
	 */
	private void defineClusterVariables ()
	{
		// Amount of work allocated to each cluster.
		for (int i=0;i<platform.getNumProcessors ();i++)
		{
			IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.clusterWorkAllocationPrefix + Integer.toString (i), "Int");
			varDecl.put (SmtVariablePrefixes.clusterWorkAllocationPrefix + Integer.toString (i), id);
			
			id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.workImbalancePrefix + Integer.toString (i), "Int");
			varDecl.put (SmtVariablePrefixes.workImbalancePrefix + Integer.toString (i), id);
		}
		
		IntExpr id = (IntExpr) addVariableDeclaration ("totalClustersUsed", "Int");
		varDecl.put (SmtVariablePrefixes.totalClustersUsedPrefix, id);
		
		id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.totalWorkImbalancePrefix, "Int");
		varDecl.put (SmtVariablePrefixes.totalWorkImbalancePrefix, id);
	}	
	
	/**
	 * Define required SMT variables for all the tasks
	 */
	private void defineTaskVariables ()
	{
		Iterator<Actor>actrIter = hsdf.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			for (int i=0;i<platform.getNumProcessors ();i++)
			{
				BoolExpr id = (BoolExpr) addVariableDeclaration (actr.getName () + "-" + Integer.toString (i), "Bool");
				varDecl.put (actr.getName () + "-" + Integer.toString (i), id);
			}			
		}
		
		actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			try 
			{
				IntNum excTime = ctx.mkInt (actr.getExecTime ());
				varDecl.put (actr.getName (), excTime);
				
			} catch (Z3Exception e) { e.printStackTrace (); }
		}
	}
	
	/**
	 * Define required SMT variables for all the communication channels
	 */
	private void defineCommunicationVariables ()
	{
		Iterator<Channel>chnnlIter = hsdf.getChannels ();
		while (chnnlIter.hasNext ())
		{
			Channel chnnl = chnnlIter.next ();
			for (int i=minDistance+1;i<=maxDistance;i++)
			{
				BoolExpr id = (BoolExpr) addVariableDeclaration (SmtVariablePrefixes.linkPrefix + chnnl.getLink (Port.DIR.OUT).getActor ().getName () 
						+ "-" + chnnl.getLink (Port.DIR.IN).getActor ().getName () + "-dist"+Integer.toString (i), "Bool");
				
				varDecl.put (SmtVariablePrefixes.linkPrefix + chnnl.getLink (Port.DIR.OUT).getActor ().getName () 
						+ "-" + chnnl.getLink (Port.DIR.IN).getActor ().getName () + "-dist"+Integer.toString (i), id);
			}
		}
	}
	
	/**
	 * Calculation of work allocated per cluster
	 */
	private void workAllocatedPerCluster ()
	{
		try 
		{
			// Calculate Amount of work allocated to each cluster.
			for (int i=0;i<platform.getNumProcessors ();i++)
			{
				int count = 0;
				ArithExpr addArgs[] = new ArithExpr[hsdf.countActors ()];
				
				Iterator<Actor>actrIter = hsdf.getActors ();
				while (actrIter.hasNext ())
				{
					Actor actr = actrIter.next ();					
					addArgs[count++] = (ArithExpr) ctx.mkITE (assignedClusterId (actr.getName (), i), taskDurationId (actr.getName ()), ctx.mkInt (0));					
				}
				
				generateAssertion (ctx.mkEq (clusterAllocatedWorkId (i), ctx.mkAdd (addArgs)));
			}
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * Calculate workload imbalance depending on number of clusters used.
	 * 
	 * @param numClustersUsed number of clusers used
	 */
	private void calculateWorkImbalance (int numClustersUsed)
	{
		try
		{
			for (int i=0;i<platform.getNumProcessors ();i++)
			{
				BoolExpr orArgs[] = new BoolExpr[2];
				
				orArgs[0] = ctx.mkEq (clusterImbalanceId (i), ctx.mkSub (ctx.mkInt (totalWork / numClustersUsed), clusterAllocatedWorkId (i)));
				orArgs[1] = ctx.mkEq (clusterImbalanceId (i), ctx.mkSub (clusterAllocatedWorkId (i), ctx.mkInt (totalWork / numClustersUsed)));
				
				generateAssertion (ctx.mkGe (clusterImbalanceId (i), ctx.mkInt (0)));
				generateAssertion ((BoolExpr) ctx.mkITE (ctx.mkGt (clusterAllocatedWorkId (i), ctx.mkInt (0)), ctx.mkOr (orArgs), 
						ctx.mkEq (clusterImbalanceId (i), ctx.mkInt (0))));
			}
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * Calculate total workload imbalance
	 */
	private void totalWorkImbalance ()
	{
		ArithExpr addArgs[] = new ArithExpr[platform.getNumProcessors ()];
		
		for (int i=0;i<platform.getNumProcessors ();i++)
			addArgs[i] = clusterImbalanceId (i);
		
		try
		{
			generateAssertion (ctx.mkEq (totalWorkImbalanceId (), ctx.mkAdd (addArgs)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * A constraint specifying : every task should be allocated to a unique processor
	 */
	private void uniqueProcessorAllocation ()
	{
		try
		{
			Iterator<Actor>actrIter = hsdf.getActors ();
			while (actrIter.hasNext ())
			{
				Actor actr = actrIter.next ();
				BoolExpr orArgs[] = new BoolExpr[platform.getNumProcessors ()];
				for (int i=0;i<platform.getNumProcessors ();i++)
				{
					BoolExpr andArgs[] = new BoolExpr[platform.getNumProcessors ()];
					for (int j=0;j<platform.getNumProcessors ();j++)
					{
						if (i == j)
							andArgs[j] = assignedClusterId (actr.getName (), j);
						else
							andArgs[j] = ctx.mkNot (assignedClusterId (actr.getName (), j));
					}
					orArgs[i] = ctx.mkAnd (andArgs);						
				}
				generateAssertion (ctx.mkOr (orArgs));
			}
			
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * A constraint specifying : depending on clusters where producer and consumer of channel is allocated the
	 * distance between them will be calculated
	 */
	private void communicationLinksAllocation ()
	{
		try
		{
			BoolExpr andArgs[] = new BoolExpr[platform.getNumProcessors () * platform.getNumProcessors () * hsdf.countChannels ()];
			int count = 0;
			
			Iterator<Channel> chnnlIter = hsdf.getChannels ();
			while (chnnlIter.hasNext ())
			{
				Channel chnnl = chnnlIter.next ();
				Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
				Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
				for (int i=0;i<platform.getNumProcessors ();i++)
				{
					for (int j=0;j<platform.getNumProcessors ();j++)
					{					
						BoolExpr orArgs[] = new BoolExpr[3];
						BoolExpr smallAndArgs[] = new BoolExpr[maxDistance-minDistance];
						orArgs[0] = ctx.mkNot (assignedClusterId (srcActor.getName (), i));
						orArgs[1] = ctx.mkNot (assignedClusterId (dstActor.getName (), j));

						int currDistance = platform.getMinDistance (i, j);
						
						if ((maxDistance - minDistance) == 1)
						{
							if (i == j)
								orArgs[2] = ctx.mkNot (commLink (chnnl, maxDistance));
							else
								orArgs[2] = commLink (chnnl, maxDistance);
						}
						else
						{
							int smallArgsCount = 0;
							
							for (int k=minDistance+1;k<=maxDistance;k++)
							{
								if (k == currDistance)
									smallAndArgs[smallArgsCount++] = commLink (chnnl, k);
								else
									smallAndArgs[smallArgsCount++] = ctx.mkNot (commLink (chnnl, k));
							}
							orArgs[2] = ctx.mkAnd (smallAndArgs);							
						}
						andArgs[count++] = ctx.mkOr (orArgs);
					}				
				}		
			}
			generateAssertion (ctx.mkAnd (andArgs));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * A calculation of total number of clusters used in allocation
	 * If workload is allocated to a processor then it should be counted as used
	 * or unused otherwise.
	 */
	private void totalClustersUsed ()
	{
		try 
		{
			ArithExpr addExpr[] = new ArithExpr[platform.getNumProcessors ()];
			for (int i=0;i<platform.getNumProcessors ();i++)
					addExpr[i] = (ArithExpr) ctx.mkITE (ctx.mkEq (clusterAllocatedWorkId (i),  ctx.mkInt (0)), ctx.mkInt (0) , ctx.mkInt (1));
			generateAssertion (ctx.mkEq (totalClustersUsedId (), ctx.mkAdd (addExpr)));
		} catch (Z3Exception e) { e.printStackTrace (); }
		
	}
	
	/**
	 * A cost constraint on total number of clusters used 
	 * @param numClusters number of clusters to use
	 */
	public void totalClustersUsed (int numClusters)
	{
		calculateWorkImbalance (numClusters);
		try 
		{
			generateAssertion (ctx.mkEq (totalClustersUsedId (), ctx.mkInt (numClusters)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.WorkloadImbalanceConstraints#generateWorkImbalanceConstraint(int)
	 */
	@Override
	public void generateWorkImbalanceConstraint (int imbalanceConstraint)
	{
		try 
		{
			generateAssertion (ctx.mkLe (totalWorkImbalanceId (), ctx.mkInt (imbalanceConstraint)));
		} catch (Z3Exception e) { e.printStackTrace (); }		
	}
	
	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.CommunicationCostConstraints#generateCommunicationCostConstraint(int)
	 */
	@Override
	public void generateCommunicationCostConstraint (int commCostConstraint)
	{
		try
		{
			generateAssertion (ctx.mkLe (totalCommCostId (), ctx.mkInt (commCostConstraint)));
		} catch (Z3Exception e) { e.printStackTrace (); }		
	}
	
	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.WorkloadImbalanceConstraints#getWorkLoadImbalance(java.util.Map)
	 */
	@Override
	public int getWorkLoadImbalance (Map<String,String>model)
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.totalWorkImbalancePrefix));
	}
	
	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.CommunicationCostConstraints#getCommunicationCost(java.util.Map)
	 */
	@Override
	public int getCommunicationCost (Map<String,String>model)
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.totalCommCostPrefix));
	}
	
	/**
	 * Total communication cost depending on the task allocation.
	 */
	private void totalCommunicationCost ()
	{
		try
		{
			int count=0;
			ArithExpr addArgs[] = new ArithExpr[hsdf.countChannels ()]; 
			Iterator<Channel> chnnlIter = hsdf.getChannels ();
			while (chnnlIter.hasNext ())
			{
				Channel chnnl = chnnlIter.next ();
				int cost = chnnl.getTokenSize () *  Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
				
				Expr costExpr = ctx.mkInt (minDistance * cost);
				
				for (int i=maxDistance;i>minDistance;i--)
					costExpr = ctx.mkITE (commLink (chnnl, i), ctx.mkInt (i * cost), costExpr);
				
				addArgs[count++] = (ArithExpr) costExpr;
			}
			generateAssertion (ctx.mkEq (totalCommCostId (), ctx.mkAdd (addArgs)));
		} catch (Z3Exception e) { e.printStackTrace (); }			
	}
	
	/**
	 * Generate all the constraints required to solve this mapping problem.
	 */
	public void generateMappingConstraints ()
	{		
		minDistance = platform.getMinDistanceInPlatform ();
		maxDistance = platform.getMaxDistanceInPlatform ();		
		
		IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.totalCommCostPrefix, "Int");
		varDecl.put (SmtVariablePrefixes.totalCommCostPrefix, id);		
		
		defineClusterVariables ();
		
		defineTaskVariables ();
		
		defineCommunicationVariables ();
		
		workAllocatedPerCluster ();		
		
		totalWorkImbalance ();
		
		uniqueProcessorAllocation ();
		
		communicationLinksAllocation ();
		
		totalClustersUsed ();
		
		totalCommunicationCost ();		
	}
}
