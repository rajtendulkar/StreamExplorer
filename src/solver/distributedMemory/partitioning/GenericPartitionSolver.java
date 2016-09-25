package solver.distributedMemory.partitioning;

import java.util.*;

import com.microsoft.z3.*;

import designflow.NonPipelinedScheduling;
import designflow.DesignFlowSolution;
import designflow.DesignFlowSolution.*;
import platform.model.Platform;
import solver.SmtVariablePrefixes;
import solver.Z3Solver;
import spdfcore.*;
import spdfcore.Actor.ActorType;
import spdfcore.stanalys.GraphExpressions;
import spdfcore.stanalys.Solutions;
import exploration.interfaces.oneDim.ClusterConstraints;
import exploration.interfaces.oneDim.CommunicationCostConstraints;
import exploration.interfaces.oneDim.MaxWorkLoadPerCluster;
import exploration.interfaces.oneDim.WorkloadImbalanceConstraints;
import exploration.interfaces.threeDim.MaxWrkLdCommCostClusterConstraints;
import exploration.interfaces.threeDim.WorkloadCommClusterConstraints;
import exploration.interfaces.twoDim.WrkLdCommCostConstraints;
import graphanalysis.CalculateBounds;

/**
 * Generic partition solve which finds a solution with 
 * workload assigned to a group, communication cost between the groups
 * and number of groups. It shouldn't be used directly, instead choose
 * PartitionSolverHSDF or PartitionSolverSDF depending on SDF or HSDF
 * the problem to be solved. 
 * 
 * Note : even if we know that actors are clustered into groups, we still
 * use the name clusters and number of clusters allocated etc. This is because
 * in the mapping problem (partition + allocation), we assign the actors directly
 * to the clusters. Hence we use the same solver headers. The clusters here should
 * be considered as soft clusters which is equal to groups.
 * 
 * @author Pranav Tendulkar
 *
 */
public abstract class GenericPartitionSolver extends Z3Solver
			implements  WorkloadCommClusterConstraints, WrkLdCommCostConstraints, MaxWrkLdCommCostClusterConstraints,
					WorkloadImbalanceConstraints, CommunicationCostConstraints, ClusterConstraints, MaxWorkLoadPerCluster
{
	/**
	 * Application Graph
	 */
	protected Graph graph;
	/**
	 * Equivalent HSDF graph of application graph
	 */
	protected Graph hsdf;
	/**
	 * Solutions of the application graph.
	 */
	protected Solutions solutions;
	/**
	 * Total workload of the application graph  
	 */
	protected int totalWork = 0;
	/**
	 * Map of SMT variables
	 */
	protected Map<String, Expr> varDecl;
	/**
	 * Target platform
	 */
	protected Platform platform;

	/**
	 * Use Maximum workload per cluster (false)
	 * or workload imbalance between the clusters (true)
	 */
	public boolean useImbalance=false;
	
	/**
	 * Initialize a generic partition solver object
	 * 
	 * @param inputGraph application graph
	 * @param hsdf equivalent HSDF graph
	 * @param solutions solutions to the application graph
	 * @param platform target platform
	 */
	public GenericPartitionSolver (Graph inputGraph, Graph hsdf, Solutions solutions, Platform platform)
	{
		graph = inputGraph;
		this.hsdf = hsdf;
		this.platform = platform;
		this.solutions = solutions;
		
		varDecl = new TreeMap<String, Expr>();
		
		CalculateBounds bounds = new CalculateBounds (graph, solutions);
		totalWork = bounds.findTotalWorkLoad ();
	}
	
	/**
	 * Get SMT variable of total workload imbalance
	 * @return variable of total workload imbalance
	 */
	protected IntExpr totalWorkImbalanceId () { return (IntExpr) varDecl.get (SmtVariablePrefixes.totalWorkImbalancePrefix); }
	
	/**
	 * Get SMT variable of workload imbalance on a cluster
	 * @param clusterId cluster id
	 * @return variable of workload imbalance on a cluster
	 */
	protected IntExpr clusterImbalanceId (int clusterId) { return (IntExpr) varDecl.get (SmtVariablePrefixes.workImbalancePrefix + Integer.toString (clusterId));}
	
	/**
	 * Get SMT variable of workload allocated on a cluster
	 * @param clusterId cluster id
	 * @return variable of workload allocated on a cluster
	 */
	protected IntExpr clusterAllocatedWorkId (int clusterId) { return (IntExpr) varDecl.get (SmtVariablePrefixes.clusterWorkAllocationPrefix + Integer.toString (clusterId));}
	
	/**
	 * Get SMT variable of total number of clusters used in the problem
	 * @return variable of total number of clusters used in the problem
	 */
	protected IntExpr totalClustersUsedId () { return (IntExpr) varDecl.get (SmtVariablePrefixes.totalClustersUsedPrefix); }
	
	/**
	 * Get SMT variable of total communication cost
	 * @return variable of total communication cost
	 */
	protected IntExpr totalCommCostId () { return (IntExpr) varDecl.get (SmtVariablePrefixes.totalCommCostPrefix); }
	
	/**
	 * Get SMT variable of communication cost for a channel in application graph
	 * @param name name of the channel
	 * @return variable of communication cost for a channel
	 */
	protected IntExpr commCostId (String name)  { return (IntExpr) varDecl.get (SmtVariablePrefixes.commCostPrefix + name ); }
	
	/**
	 * Get SMT variable of maximum workload allocated to all clusters
	 * @return variable of maximum workload allocated to all clusters
	 */
	protected IntExpr maxWorkloadOnClusterId() { return (IntExpr) varDecl.get (SmtVariablePrefixes.maxWorkloadOnClusterPrefix ); }
	
	/**
	 * Get SMT variable of an actor instance allocated to a cluster
	 * 
	 * @param name actor name 
	 * @param instanceId instance id
	 * @return variable of an actor instance allocated to a cluster
	 */
	protected IntExpr clusterTaskAllocationId (String name, int instanceId) 
	{ 
		return (IntExpr) varDecl.get (SmtVariablePrefixes.clusterTaskPrefix + name + "_" + Integer.toString (instanceId)); 
	}
	
	/**
	 * Get SMT variable of an actor allocated to a cluster
	 * 
	 * @param name name of the actor
	 * @return variable of an actor allocated to a cluster
	 */
	protected IntExpr clusterTaskAllocationId (String name) 
	{ 
		return (IntExpr) varDecl.get (SmtVariablePrefixes.clusterTaskPrefix + name); 
	}
	
	/**
	 * Define all the variables required for clusters.
	 */
	protected void defineClusterVariables ()
	{
		IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.totalCommCostPrefix, "Int");
		varDecl.put (SmtVariablePrefixes.totalCommCostPrefix, id);		
		
		id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.totalClustersUsedPrefix, "Int");
		varDecl.put (SmtVariablePrefixes.totalClustersUsedPrefix, id);
		
		if (useImbalance == true)
		{		
			id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.totalWorkImbalancePrefix, "Int");
			varDecl.put (SmtVariablePrefixes.totalWorkImbalancePrefix, id);
			
			// Amount of work allocated to each cluster.
			for (int i=0;i<platform.getNumClusters ();i++)
			{
				id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.clusterWorkAllocationPrefix + Integer.toString (i), "Int");
				varDecl.put (SmtVariablePrefixes.clusterWorkAllocationPrefix + Integer.toString (i), id);
				
				id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.workImbalancePrefix + Integer.toString (i), "Int");
				varDecl.put (SmtVariablePrefixes.workImbalancePrefix + Integer.toString (i), id);
			}
			
		}		
		else
		{
			id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.maxWorkloadOnClusterPrefix, "Int");
			varDecl.put (SmtVariablePrefixes.maxWorkloadOnClusterPrefix, id);
			
			// Amount of work allocated to each cluster.
			for (int i=0;i<platform.getNumClusters ();i++)
			{
				id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.clusterWorkAllocationPrefix + Integer.toString (i), "Int");
				varDecl.put (SmtVariablePrefixes.clusterWorkAllocationPrefix + Integer.toString (i), id);
			}
		}		
	}	
	
	/**
	 * Calculate the total workload imbalance between the clusters.
	 * It is sum of all workload imbalance.
	 */
	protected void totalWorkImbalance ()
	{
		ArithExpr addArgs[] = new ArithExpr[platform.getNumClusters ()];
		
		for (int i=0;i<platform.getNumClusters ();i++)
			addArgs[i] = clusterImbalanceId (i);
		
		try
		{
			generateAssertion (ctx.mkEq (totalWorkImbalanceId (), ctx.mkAdd (addArgs)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}	
		
	/**
	 * Calculate workload imbalance for all the clusters.
	 * 
	 * @param numClustersUsed total number of clusters used
	 */
	protected void calculateWorkImbalance (int numClustersUsed)
	{
		try
		{
			for (int i=0;i<platform.getNumClusters ();i++)
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
	 * Calculate maximum workload on a cluster
	 */
	protected void maxWorkLoadOnCluster ()
	{		
		try
		{
			BoolExpr orArgs[] = new BoolExpr[platform.getNumClusters ()];
			BoolExpr andArgs[] = new BoolExpr[platform.getNumClusters ()];
			
			for (int i=0;i<platform.getNumClusters ();i++)
			{
				orArgs[i] = ctx.mkEq(maxWorkloadOnClusterId(), clusterAllocatedWorkId(i));
				andArgs[i] = ctx.mkGe(maxWorkloadOnClusterId(), clusterAllocatedWorkId(i));
				
				// generateAssertion(ctx.mkLe(clusterAllocatedWorkId(i), maxWorkloadOnClusterId()));
			}
			generateAssertion(ctx.mkOr(orArgs));
			generateAssertion(ctx.mkAnd(andArgs));
		} catch (Z3Exception e) { e.printStackTrace (); }		
	}
	
	/**
	 * Generate all the partitioning constraints
	 */
	public abstract void generatePartitioningConstraints ();
	
	
	/**
	 * Set design flow solution parameters such as partition aware graph, solutions, etc.
	 * 
	 * @param designFlowSolution design flow solution to be modified
	 * @param model model from the SMT solver for partitioning
	 */
	public void setDesignFlowSolution (DesignFlowSolution designFlowSolution, Map<String, String> model)
	{
		Graph partitionAwareGraph = modelToPartitionAwareGraph(model);			
		GraphExpressions expressions = new GraphExpressions ();
		expressions.parse (partitionAwareGraph);
		Solutions partitionGraphSolutions = new Solutions ();
		partitionGraphSolutions.setThrowExceptionFlag (false);	    		
		partitionGraphSolutions.solve (partitionAwareGraph, expressions);
		
		// Set the partition aware graph in the design flow solution
		designFlowSolution.setpartitionAwareGraph(partitionAwareGraph);
		designFlowSolution.setPartitionAwareGraphSolutions(partitionGraphSolutions);
		
		// set the partition info. in the design flow solution.
		designFlowSolution.setPartition (modelToPartition(model, designFlowSolution));
	}
	
	/**
	 * Convert a model to partition aware graph by inserting DMA actors.
	 * 
	 * @param model model of solution from the SMT solver
	 * @return Partition Aware graph
	 */
	private Graph modelToPartitionAwareGraph (Map<String, String> model)
	{
		Graph partitionAwareGraph = new Graph(graph);
		HashSet<Channel> channels = new HashSet<Channel>(partitionAwareGraph.getChannelList());
		
		for (Channel chnnl : channels)
		{
			String channelName = chnnl.getName();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor (); 
		    Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
		    int srcRate = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
		    int dstRate = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());
		    
		    int srcGroup = 0, dstGroup = 0;
		    
		    srcGroup = Integer.parseInt(model.get(SmtVariablePrefixes.clusterTaskPrefix + srcActor.getName()));
		    dstGroup = Integer.parseInt(model.get(SmtVariablePrefixes.clusterTaskPrefix + dstActor.getName()));
			
		    if(srcGroup != dstGroup)
		    {
		    	{
		    		// We insert the DMA task from writer to reader, which carries token + status
				    String newActorName = SmtVariablePrefixes.dmaTokenTaskPrefix + chnnl.getName();
				    int tokenSize = chnnl.getTokenSize();
				    
				    // Let us add a new actor to the graph
					Actor newActor = new Actor(newActorName, newActorName, 0, ActorType.COMMUNICATION);		
					partitionAwareGraph.add(newActor);
					
					Port inputPort  = new Port (Port.DIR.IN, newActorName, "p0", Integer.toString(srcRate));
					Port outputPort1 = new Port (Port.DIR.OUT, newActorName, "p1", Integer.toString(srcRate));			
					Port outputPort2 = new Port (Port.DIR.OUT, newActorName, "p2", Integer.toString(srcRate));
					partitionAwareGraph.add(inputPort);
					partitionAwareGraph.add(outputPort1);
					partitionAwareGraph.add(outputPort2);					
					
					// Insert new actor between A and B.
					Channel[] chnnls = partitionAwareGraph.insertNewActorBetweenTwoActors (srcActor, dstActor, chnnl, newActor, "p0", "p1");
					// Warning : chnnl is no longer valid after this statement since it will be removed by function above.
					
					// Don't forget to set token sizes and initial tokens.
					chnnls[0].setTokenSize(tokenSize);
					chnnls[0].setInitialTokens(0);
					chnnls[1].setTokenSize(tokenSize);
					chnnls[1].setInitialTokens(0);
		    	}
		    	
		    	{
		    		// Lets say we consume 100 clock cycles for updating one token status.
		    		int execTime = 100 * srcRate;
		    		// We add a Finish DMA Task to update the FIFO Token Status at the writer side.
		    		// The name of this task will be equal to the channel name.		    		
				    String newActorName = channelName;
				    // Let us add a new actor to the graph
					Actor newActor = new Actor(newActorName, newActorName, execTime, ActorType.DATAFLOW);
					partitionAwareGraph.add(newActor);
					
					Id portId = new Id();
					portId.setFunc(newActorName);
					portId.setName("p0");					
					
					if(partitionAwareGraph.hasPort(portId) == false)
					{
						Port inputPort  = new Port (Port.DIR.IN, newActorName, "p0", Integer.toString(srcRate));
						partitionAwareGraph.add(inputPort);
					}
					
					Port inputPort = partitionAwareGraph.getPort(portId);
					
					// Connect DMA Actor -- > New Actor
					PortRef src = new PortRef ();
					PortRef snk = new PortRef ();
					
					src.setActorName (SmtVariablePrefixes.dmaTokenTaskPrefix + channelName);
					src.setPortName ("p2");
					
					snk.setActor(newActor);
					snk.setPort (inputPort);
					
					Channel chnnl1 = new Channel();
					chnnl1.setName(SmtVariablePrefixes.dmaTokenTaskPrefix + channelName + "to" + newActor.getName());
					partitionAwareGraph.add (chnnl1);
					
					chnnl1.bind (src, snk);
					chnnl1.setTokenSize(0);
					chnnl1.setInitialTokens(0);
		    	}
				
		    	{
		    		// Now I can take a random number and build a new name for the ports. 
		    		// Or otherwise I know how many ports are already there which are named like p0, p1... and I add new Pn.
		    		// I go for the second approach.
		    		int numPortsDstActors = dstActor.numIncomingLinks() + dstActor.numOutgoingLinks();
		    		
		    		Port portAtDst=new Port (Port.DIR.OUT, dstActor.getFunc (), 
		    								"p" + Integer.toString(numPortsDstActors), 
		    								Integer.toString(dstRate));
		    		partitionAwareGraph.add (portAtDst);

		    		// We insert the DMA task from reader, which carries only status
					String newActorName = SmtVariablePrefixes.dmaStatusTaskPrefix +  channelName;
				    
				    // Let us add a new actor to the graph
					Actor newActor = new Actor(newActorName, newActorName, 0, ActorType.COMMUNICATION);		
					partitionAwareGraph.add(newActor);
					
					Port inputPort  = new Port (Port.DIR.IN, newActorName, "p0", Integer.toString(dstRate));			
					partitionAwareGraph.add(inputPort);
					
					// Connect Src Actor -- > New Actor
					PortRef src = new PortRef ();
					PortRef snk = new PortRef ();
					
					src.setActorName (dstActor.getName());
					src.setPortName (portAtDst.getName());
					
					snk.setActorName (newActor.getName());
					snk.setPortName ("p0");
					
					Channel chnnl1 = new Channel();
					
					chnnl1.setName(dstActor.getName() + "to" + newActor.getName());
					partitionAwareGraph.add (chnnl1);
					
					chnnl1.bind (src, snk);					
					
					// Don't forget to set token sizes and initial tokens.
					chnnl1.setTokenSize(NonPipelinedScheduling.fifoStatusSizeInBytes);			
					chnnl1.setInitialTokens(0);
		    	}				
		    }
		}
		
		return partitionAwareGraph;
	}
	
	/**
	 * Sets a new partition in design flow solution
	 *  
	 * @param model model of solution from the SMT solver
	 * @param designFlowSolution design flow solution to be modified
	 * @return new partition that was created for this design flow solution
	 */
	protected abstract Partition modelToPartition (Map<String, String> model, DesignFlowSolution designFlowSolution);
	
	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.MaxWorkLoadPerCluster#getMaxWorkLoadPerCluster(java.util.Map)
	 */
	@Override
	public int getMaxWorkLoadPerCluster(Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.maxWorkloadOnClusterPrefix));
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.MaxWorkLoadPerCluster#generateMaxWorkloadPerClusterConstraint(int)
	 */
	@Override
	public void generateMaxWorkloadPerClusterConstraint(int constraintValue) 
	{
		try
		{
			generateAssertion (ctx.mkLe (maxWorkloadOnClusterId (), ctx.mkInt (constraintValue)));
		} catch (Z3Exception e) { e.printStackTrace (); }		
	}
	
	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.ClusterConstraints#getTotalClustersUsed(java.util.Map)
	 */
	@Override
	public int getTotalClustersUsed (Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.totalClustersUsedPrefix));
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.ClusterConstraints#generateClusterConstraint(int)
	 */
	@Override
	public void generateClusterConstraint (int numClusters) 
	{
		if (useImbalance == true)
			calculateWorkImbalance (numClusters);

		try 
		{
			for (int i=0;i<numClusters;i++)
				generateAssertion (ctx.mkGt (clusterAllocatedWorkId (i), ctx.mkInt (0)));
			for (int i=numClusters;i<platform.getNumClusters ();i++)
				generateAssertion (ctx.mkEq (clusterAllocatedWorkId (i), ctx.mkInt (0)));
			
			generateAssertion (ctx.mkEq (totalClustersUsedId (), ctx.mkInt (numClusters)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.CommunicationCostConstraints#generateCommunicationCostConstraint(int)
	 */
	@Override
	public void generateCommunicationCostConstraint (int constraintValue) 
	{
		try
		{
			generateAssertion (ctx.mkLe (totalCommCostId (), ctx.mkInt (constraintValue)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.CommunicationCostConstraints#getCommunicationCost(java.util.Map)
	 */
	@Override
	public int getCommunicationCost (Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.totalCommCostPrefix));
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.WorkloadImbalanceConstraints#generateWorkImbalanceConstraint(int)
	 */
	@Override
	public void generateWorkImbalanceConstraint (int constraintValue) 
	{
		try 
		{
			generateAssertion (ctx.mkLe (totalWorkImbalanceId (), ctx.mkInt (constraintValue)));
		} catch (Z3Exception e) { e.printStackTrace (); }		
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.WorkloadImbalanceConstraints#getWorkLoadImbalance(java.util.Map)
	 */
	@Override
	public int getWorkLoadImbalance (Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.totalWorkImbalancePrefix));
	}
}
