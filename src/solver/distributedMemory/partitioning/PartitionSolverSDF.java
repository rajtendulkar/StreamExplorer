package solver.distributedMemory.partitioning;

import java.util.*;

import com.microsoft.z3.*;

import designflow.NonPipelinedScheduling;
import designflow.DesignFlowSolution;
import designflow.DesignFlowSolution.*;
import platform.model.Platform;
import solver.SmtVariablePrefixes;
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

/**
 * An SDF based partition solver for application graph.
 * This partition solver creates groups of different SDF actors
 * and then calculates workload, number of groups and communication costs.
 * 
 * @author Pranav Tendulkar
 *
 */
public class PartitionSolverSDF extends GenericPartitionSolver
			implements  WorkloadCommClusterConstraints, WrkLdCommCostConstraints, MaxWrkLdCommCostClusterConstraints,
					WorkloadImbalanceConstraints, CommunicationCostConstraints, ClusterConstraints, MaxWorkLoadPerCluster
{	
	/**
	 * Build a partition solver object
	 * 
	 * @param inputGraph input application graph
	 * @param hsdf equivalent HSDF graph
	 * @param solutions solutions to the application graph
	 * @param platform target platform
	 * @param outputDirectory output directory to generate output files
	 */
	public PartitionSolverSDF (Graph inputGraph, Graph hsdf, Solutions solutions, Platform platform, String outputDirectory)
	{
		super (inputGraph, hsdf, solutions, platform);
	}	
	
	/* (non-Javadoc)
	 * @see solver.distributedMemory.partitioning.GenericPartitionSolver#generatePartitioningConstraints()
	 */
	@Override
	public void generatePartitioningConstraints ()
	{
		// define all the cluster variables
		defineClusterVariables ();
		// define all the variables for different tasks
		defineTaskVariables ();
		// workload calculation for each group
		clusterTaskWorkAllocation ();
		// Upper bound on which group an actor can be allocated
		taskLimits ();
		// Communication costs depending on producer and consumer of same channel are in same group or not.
		communicationCosts ();
		// total communication cost of the problem
		totalCommunicationCost ();
		
		// workload imbalance between groups or maximum workload allocated among the clusters
		if (useImbalance == true)
			totalWorkImbalance ();
		else
			maxWorkLoadOnCluster();
	}
	
	/* (non-Javadoc)
	 * @see solver.distributedMemory.partitioning.GenericPartitionSolver#setDesignFlowSolution(designflow.DesignFlowSolution, java.util.Map)
	 */
	@Override
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
	 * @param model model from the SMT solver
	 * @return partition aware graph
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
		
	/* (non-Javadoc)
	 * @see solver.distributedMemory.partitioning.GenericPartitionSolver#getMaxWorkLoadPerCluster(java.util.Map)
	 */
	@Override
	public int getMaxWorkLoadPerCluster(Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.maxWorkloadOnClusterPrefix));
	}

	/* (non-Javadoc)
	 * @see solver.distributedMemory.partitioning.GenericPartitionSolver#generateMaxWorkloadPerClusterConstraint(int)
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
	 * @see solver.distributedMemory.partitioning.GenericPartitionSolver#getTotalClustersUsed(java.util.Map)
	 */
	@Override
	public int getTotalClustersUsed (Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.totalClustersUsedPrefix));
	}

	/* (non-Javadoc)
	 * @see solver.distributedMemory.partitioning.GenericPartitionSolver#generateClusterConstraint(int)
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
	 * @see solver.distributedMemory.partitioning.GenericPartitionSolver#generateCommunicationCostConstraint(int)
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
	 * @see solver.distributedMemory.partitioning.GenericPartitionSolver#getCommunicationCost(java.util.Map)
	 */
	@Override
	public int getCommunicationCost (Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.totalCommCostPrefix));
	}

	/* (non-Javadoc)
	 * @see solver.distributedMemory.partitioning.GenericPartitionSolver#generateWorkImbalanceConstraint(int)
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
	 * @see solver.distributedMemory.partitioning.GenericPartitionSolver#getWorkLoadImbalance(java.util.Map)
	 */
	@Override
	public int getWorkLoadImbalance (Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.totalWorkImbalancePrefix));
	}
	
	/* (non-Javadoc)
	 * @see solver.distributedMemory.partitioning.GenericPartitionSolver#modelToPartition(java.util.Map, designflow.DesignFlowSolution)
	 */
	@Override
	/**
	 * Requires Partition Aware Graph in the design flow solution already to be set.
	 */
	protected Partition modelToPartition (Map<String, String> model, DesignFlowSolution designFlowSolution)
	{		
		int totalCommCost = Integer.parseInt (model.get (SmtVariablePrefixes.totalCommCostPrefix));
		int numGroups = Integer.parseInt (model.get (SmtVariablePrefixes.totalClustersUsedPrefix));
		
		Partition partition = designFlowSolution.new Partition (numGroups, totalCommCost, true);
		
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			int allocatedGroup = Integer.parseInt (model.get (SmtVariablePrefixes.clusterTaskPrefix + actr.getName ()));
			partition.addSdfActorToGroup (allocatedGroup, actr.getName());
		}
		
		return partition;
	}
	
	/**
	 * 
	 */
	public void defineTaskVariables ()
	{		
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			
			IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.clusterTaskPrefix + actr.getName (), "Int");
			varDecl.put (SmtVariablePrefixes.clusterTaskPrefix + actr.getName (), id);
						
		}
		
		Iterator<Channel> chnnlIter = graph.getChannels ();
		while (chnnlIter.hasNext ())
		{
			Channel chnnl = chnnlIter.next ();			
			IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.commCostPrefix + chnnl.getName () , "Int");
			varDecl.put (SmtVariablePrefixes.commCostPrefix + chnnl.getName (), id);
		}
	}
	
	/**
	 * 
	 */
	private void taskLimits ()
	{
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();			
			try 
			{				
				generateAssertion (ctx.mkAnd (ctx.mkGe (clusterTaskAllocationId (actr.getName ()), ctx.mkInt (0)), 
											ctx.mkLt (clusterTaskAllocationId (actr.getName ()), totalClustersUsedId ())));
			
			} catch (Z3Exception e) { e.printStackTrace (); }
		}
	}
	
	/**
	 * 
	 */
	private void totalCommunicationCost ()
	{
		try 
		{
			int count = 0;
			int numChannels = graph.countChannels ();
			ArithExpr addArgs[] = new ArithExpr[numChannels];
			
			Iterator<Channel>chnnlIter = graph.getChannels ();
			while (chnnlIter.hasNext ())
			{
				Channel chnnl = chnnlIter.next ();
				addArgs[count++] = commCostId (chnnl.getName ());
			}		
			generateAssertion (ctx.mkEq (totalCommCostId (), ctx.mkAdd (addArgs)));
			
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * 
	 */
	private void clusterTaskWorkAllocation ()
	{
		try 
		{
			for (int i=0;i<platform.getNumClusters ();i++)
			{
				int count = 0;
				IntExpr workAllocationId = clusterAllocatedWorkId (i);
				ArithExpr addArgs[] = new IntExpr[graph.countActors ()];
				
				Iterator<Actor> actrIter = graph.getActors ();
				while (actrIter.hasNext ())
				{
					Actor actr = actrIter.next ();
					int repCount = solutions.getSolution (actr).returnNumber ();
					int execTime = actr.getExecTime ();					
					int workLoad = (repCount * execTime);					
					
					addArgs[count++] = (ArithExpr) ctx.mkITE (ctx.mkEq (clusterTaskAllocationId (actr.getName ()), ctx.mkInt (i)), ctx.mkInt (workLoad), ctx.mkInt (0));										
				}			
					
				generateAssertion (ctx.mkEq (workAllocationId, ctx.mkAdd (addArgs)));			
			}
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * 
	 */
	private void communicationCosts ()
	{
		Iterator<Channel> chnnlIter = graph.getChannels ();
		while (chnnlIter.hasNext ())
		{
			Channel chnnl = chnnlIter.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
			
			int tokenSize = chnnl.getTokenSize ();
			int srcRate = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
			int srcRepCount = solutions.getSolution(srcActor).returnNumber();
			
			// Since srcRepCount * srcRate = dstRepCount * dstRate.
			int commCost = srcRepCount * srcRate * tokenSize;
			
			try 
			{
				generateAssertion ((BoolExpr) ctx.mkITE (ctx.mkEq (clusterTaskAllocationId (srcActor.getName ()), clusterTaskAllocationId (dstActor.getName ())), 
						ctx.mkEq (commCostId (chnnl.getName ()), ctx.mkInt (0)), 
						ctx.mkEq (commCostId (chnnl.getName ()), ctx.mkInt (commCost))));
			} catch (Z3Exception e) { e.printStackTrace (); }
		}
	}
}
