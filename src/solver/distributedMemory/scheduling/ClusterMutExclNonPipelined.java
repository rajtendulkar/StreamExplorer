package solver.distributedMemory.scheduling;

import designflow.DesignFlowSolution;
import designflow.DesignFlowSolution.*;
import exploration.interfaces.oneDim.BufferConstraints;
import exploration.interfaces.oneDim.LatencyConstraints;
import exploration.interfaces.twoDim.LatBuffConstraints;
import graphanalysis.TransformSDFtoHSDF;
import graphanalysis.properties.GraphAnalysisSdfAndHsdf;
//import graphoutput.DotGraph;

import java.util.*;

import output.GanttChart;
import output.GanttChart.Record;
import platform.model.*;

import com.microsoft.z3.*;

import solver.SmtVariablePrefixes;
import solver.Z3Solver;
import solver.distributedMemory.constraints.SchedulingConstraints;
import spdfcore.*;
import spdfcore.Actor.ActorType;
import spdfcore.Channel.Link;
import spdfcore.stanalys.*;


/**
 * Cluster scheduling for distributed memory.
 * 
 * 
 * TODO : Right now we are in the test phase and I don't want to kill the
 * already existing working code for scheduling in pipelined and non-pipelined
 * fashion. So what I do is first implement the scheduling separately for the 
 * cluster scenario and then see what is the damage. If it is not much, then 
 * we will merge both the code, just like for the shared memory scheduling.
 * 
 * @author Pranav Tendulkar
 *
 */
public class ClusterMutExclNonPipelined extends Z3Solver 
	implements LatencyConstraints, BufferConstraints, LatBuffConstraints
{
	/**
	 * Application Graph
	 */
	private Graph graph;
	/**
	 * Equivalent HSDF of application graph
	 */
	private Graph hsdf;
	/**
	 * Solutions for application graph
	 */
	private Solutions solutions;
	/**
	 * Partition aware graph
	 */
	private Graph partitionAwareGraph;
	/**
	 * Equivalent HSDF of partition aware graph
	 */
	private Graph partitionAwareHsdf;
	/**
	 * Solutions for partition aware graph
	 */
	private Solutions partitionGraphSolutions;
	/**
	 * Target platform
	 */
	private Platform platform;
	/**
	 * Scheduling constraints
	 */
	private SchedulingConstraints schedulingConstraints;
	/**
	 * SMT variable for latency of the application
	 */
	private IntExpr latencyDecl;	
	/**
	 * SMT variable for total buffer usage of the application
	 */
	private IntExpr totalBufDecl;
	/**
	 * SMT variable for maximum buffer usage per cluster of the application
	 */
	private IntExpr maxBufDecl;
	/**
	 * Enable Task Symmetry 
	 */
	public boolean graphSymmetry = false;
	/**
	 * Enable Processor Symmetry
	 */
	public boolean processorSymmetry = false;
	/**
	 * Enable calculation of communication buffer size
	 */
	public boolean bufferAnalysis = false;
	/**
	 * Use maximum buffer per cluster (true) 
	 * or total buffer used in the schedule (false)
	 */
	public boolean useMaxBuffer = true;
	/**
	 * output directory to generate different files
	 */
	private String outputDirectory;
	
	/**
	 * Schedule optimizer for improving latency, number of processors
	 * used.
	 */
	private OptimizeSchedule optimizeSchedule = null;
	
	/**
	 * Every time i want to have model from the solver, I don't have 
	 * to solve the model for non-lazy and optimal proc schedule.
	 * we just store it and use it till the next query is asked.
	 * This variable helps determine if this a new SMT Query or not.
	 */
	private boolean newSatQuery = true;
	/**
	 * Current SMT query model from a SAT point obtained.
	 */
	private Map<String, String> satQueryModel;

	/**
	 * SMT variables for start times of all the tasks.
	 */
	private Map<String, IntExpr> startTimeDecl;
	/**
	 * SMT variables for end times of all the tasks.
	 */
	private Map<String, IntExpr> endTimeDecl;
	/**
	 * SMT variables for duration of all the tasks.
	 */
	private Map<String, IntExpr> durationDecl;
	/**
	 * SMT variables for allocated processor of all the tasks.
	 */
	private Map<String, IntExpr> cpuDecl;
	/**
	 * SMT variables for buffer calculation of all the channels.
	 */
	private Map<String, IntExpr> bufferDecl;
	/**
	 * SMT variables for Processor Symmetry
	 */
	private Map<String, IntExpr> symmetryDecl;

	/**
	 * Initialize a Cluster scheduler based on mutual exclusion.
	 * 
	 * @param graph application graph
	 * @param hsdf equivalent HSDF graph of application graph 
	 * @param solutions solutions of application graph
	 * @param partitionAwareGraph partition aware graph
	 * @param partitionAwareHsdf equivalent HSDF of partition aware graph
	 * @param partitionGraphSolutions solutions of partition aware graph
	 * @param platform target platform
	 * @param outputDirectory output directory
	 * @param schedulingConstraints scheduling constraints
	 */
	public ClusterMutExclNonPipelined (Graph graph, Graph hsdf, Solutions solutions, 
			Graph partitionAwareGraph, Graph partitionAwareHsdf, Solutions partitionGraphSolutions,
			Platform platform, String outputDirectory, SchedulingConstraints schedulingConstraints)
	{
		this.graph = graph;
		this.hsdf = hsdf;
		this.solutions = solutions;
		this.partitionAwareGraph = partitionAwareGraph;
		this.schedulingConstraints = schedulingConstraints;
		this.platform = platform;
		this.partitionGraphSolutions = partitionGraphSolutions;
		this.partitionAwareHsdf = partitionAwareHsdf;
		this.outputDirectory = outputDirectory;

		startTimeDecl 	= new TreeMap<String, IntExpr>();
		endTimeDecl 	= new TreeMap<String, IntExpr>();		
		durationDecl 	= new TreeMap<String, IntExpr>();
		cpuDecl 		= new TreeMap<String, IntExpr>();
		bufferDecl 		= new TreeMap<String, IntExpr>();
		symmetryDecl    = new TreeMap<String, IntExpr>();
	}
	

	/**
	 * Get SMT variable for latency calculation of the application.
	 * 
	 * @return variable for latency calculation of the application
	 */
	public IntExpr getLatencyDeclId () { return latencyDecl; }	
	
	/**
	 * Get SMT variable for buffer usage calculation of the application
	 * 
	 * @return variable for buffer usage calculation of the application
	 */
	public IntExpr getBufDeclId () 
	{ 
		if(useMaxBuffer == false)
			return totalBufDecl;
		else
			return maxBufDecl;
	}	
	
	/**
	 * Get SMT variable for start time of an instance of an actor.
	 * 
	 * @param name name of the actor
	 * @param index index of the instance
	 * @return variable for start time of an actor instance
	 */
	private IntExpr xId (String name, int index) 		 { return startTimeDecl.get (SmtVariablePrefixes.startTimePrefix + name + "_" + Integer.toString (index)); }
	
	/**
	 * Get SMT variable for start time of an actor instance
	 * 
	 * @param name actor instance name
	 * @return variable for start time of an actor instance
	 */
	private IntExpr xId (String name) 		 { return startTimeDecl.get (SmtVariablePrefixes.startTimePrefix + name); }
	
	/**
	 * Get SMT variable for end time of an instance of an actor.
	 * 
	 * @param name name of the actor
	 * @param index index of the instance
	 * @return variable for end time of an actor instance
	 */
	private IntExpr yId (String name, int index) 		 { return endTimeDecl.get (SmtVariablePrefixes.endTimePrefix + name + "_" + Integer.toString (index)); }
	
	/**
	 * Get SMT variable for end time of an actor instance
	 * 
	 * @param name actor instance name
	 * @return variable for end time of an actor instance
	 */
	private IntExpr yId (String name) 		 { return endTimeDecl.get (SmtVariablePrefixes.endTimePrefix + name); }
	
	/**
	 * Get SMT variable for end time of an actor instance for actors
	 * which have DMA connected at its output
	 * 
	 * @param name actor instance name
	 * @return variable for end time of an actor instance with DMA at output
	 */
	private IntExpr yDotId (String name) 		 { return endTimeDecl.get (SmtVariablePrefixes.endDotTimePrefix + name); }
	
	/**
	 * Get SMT variable for end time of an actor instance for actors
	 * which have DMA connected at its output
	 * 
	 * @param name actor name
	 * @param index index of the instance
	 * @return variable for end time of an actor instance with DMA at output
	 */
	private IntExpr yDotId (String name, int index) 		 { return endTimeDecl.get (SmtVariablePrefixes.endDotTimePrefix + name + "_" + Integer.toString (index)); }
	
	/**
	 * Get SMT variable for duration of an actor
	 * 
	 * @param name actor name
	 * @return variable for duration of an actor
	 */
	private IntExpr durationId (String name)			 { return durationDecl.get (SmtVariablePrefixes.durationPrefix+ name); }
	
	/**
	 * Get SMT variable for processor allocated of an actor instance
	 * 
	 * @param name actor instance name
	 * @return variable for processor allocated of an actor instance
	 */
	private IntExpr cpuId (String name) 	 { return cpuDecl.get (SmtVariablePrefixes.cpuPrefix + name); }
	
	/**
	 * Get SMT variable for processor allocated of an actor instance
	 *  
	 * @param name actor name
	 * @param index index of the instance
	 * @return variable for processor allocated of an actor instance
	 */
	private IntExpr cpuId (String name, int index) 	 { return cpuDecl.get (SmtVariablePrefixes.cpuPrefix + name + "_" + Integer.toString (index)); }
	
	/**
	 * Get SMT variable for maximum buffer for channel connecting two actors.
	 * 
	 * @param srcActor producer actor
	 * @param dstActor consumer actor
	 * @return variable for maximum buffer for channel connecting two actors
	 */
	private IntExpr maxBufferId (String srcActor, String dstActor) { return bufferDecl.get (SmtVariablePrefixes.maxBufferPrefix + srcActor + dstActor); }
	
	/**
	 * Get SMT variable for buffer size at a producer of a channel connecting two actors
	 * 
	 * @param srcActor producer actor
	 * @param dstActor consumer actor
	 * @param index index of the buffer, <= repetition count of srcActor 
	 * @return variable for buffer size at a producer of a channel connecting two actors
	 */
	private IntExpr bufferAtId (String srcActor, String dstActor, int index) 
	{ return bufferDecl.get (SmtVariablePrefixes.bufferAtPrefix + srcActor +"_" + Integer.toString (index) + "-" + srcActor + dstActor); };
	
	/**
	 * Get SMT variable for buffer usage for a cluster
	 * 
	 * @param clusterName name of the cluster
	 * @return variable for buffer usage for a cluster
	 */
	private IntExpr clusterBufferId(String clusterName) { return bufferDecl.get(SmtVariablePrefixes.clusterBufferPrefix + clusterName); }
	
	/**
	 * Get SMT variable for max CPU id for processor symmetry
	 * 
	 * @param name name of the actor
	 * @param index index
	 * @return variable for max CPU id for processor symmetry
	 */
	private IntExpr maxCpuId (String name, int index) 	 { return symmetryDecl.get (SmtVariablePrefixes.maxCpuPrefix+ name + Integer.toString (index)); }

	/**
	 * Define start time variables for all the tasks.
	 */
	private void defineStartTimes ()
	{
		// Start Times
		Iterator<Actor> actorIter = partitionAwareGraph.getActors ();

		String arguments[] = new String[1];
		arguments[0] = "Int";
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();	
			int repCount = partitionGraphSolutions.getSolution (actr).returnNumber ();

			for (int i=0;i<repCount;i++)
			{
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.startTimePrefix + actr.getName () +"_"+ Integer.toString (i), "Int");
				startTimeDecl.put (SmtVariablePrefixes.startTimePrefix + actr.getName () +"_"+ Integer.toString (i), id);
			}
		}		
	}

	/**
	 * Define actor duration variables for all the tasks.
	 */
	private void defineActorDuration ()
	{
		// Define Actor Durations		
		Iterator<Actor> actorIter = partitionAwareGraph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			//try
			//{
				// IntExpr id = ctx.mkInt (actr.getExecTime ()); 
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.durationPrefix + actr.getName (), "Int");
				durationDecl.put (SmtVariablePrefixes.durationPrefix + actr.getName (), id);
			//} catch (Z3Exception e) { e.printStackTrace (); }
		}	
	}

	/**
	 * Define end time variables for all the tasks.
	 */
	private void defineEndTimes ()
	{
		// End Times
		Iterator<Actor> actorIter = partitionAwareGraph.getActors ();
		String arguments[] = new String[1];
		arguments[0] = "Int";
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			int repCount = partitionGraphSolutions.getSolution (actr).returnNumber ();
			
			boolean hasDmaOutput = false;
			for(Channel chnnl : actr.getChannels(Port.DIR.OUT))
				if(chnnl.getOpposite(actr).getActorType() == ActorType.COMMUNICATION)
				{
					hasDmaOutput = true;
					break;
				}

			for (int i=0;i<repCount;i++)
			{
				//try
				// {
					// IntExpr id = (IntExpr) ctx.mkAdd (xId (actr.getName (), i), durationId (actr.getName ()));
				    IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.endTimePrefix + actr.getName () +"_"+ Integer.toString (i), "Int");
					endTimeDecl.put (SmtVariablePrefixes.endTimePrefix + actr.getName () +"_"+ Integer.toString (i), id);
					if(hasDmaOutput == true)
					{
						id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.endDotTimePrefix + actr.getName () +"_"+ Integer.toString (i), "Int");
						endTimeDecl.put (SmtVariablePrefixes.endDotTimePrefix + actr.getName () +"_"+ Integer.toString (i), id);
					}
				//} catch (Z3Exception e) { e.printStackTrace (); }
			}
		}		
	}
	
	/**
	 * Generate a constraint for actor duration variables
	 */
	private void assertActorDuration()
	{
		Iterator<Actor> actorIter = partitionAwareGraph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			try
			{ 
				IntExpr id = durationId(actr.getName ());				
				generateAssertion(ctx.mkEq(id, ctx.mkInt(actr.getExecTime())));
			} catch (Z3Exception e) { e.printStackTrace (); }
		}
	}
	
	/**
	 * Generate a constraint for end time calculation of all the tasks
	 * yA_0 = xA_0 + dA
	 */
	private void assertEndTime()
	{
		Iterator<Actor> actorIter = partitionAwareGraph.getActors ();
		String arguments[] = new String[1];
		arguments[0] = "Int";
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			int repCount = partitionGraphSolutions.getSolution (actr).returnNumber ();
			
			Channel lastDmaChannel = getLastDmaPort (actr);
			
			for (int i=0;i<repCount;i++)
			{
				// yA_0 = xA_0 + dA
				try
				{
					IntExpr endTime = (IntExpr) ctx.mkAdd (xId (actr.getName (), i), durationId (actr.getName ()));
					generateAssertion(ctx.mkEq(yId(actr.getName (), i), endTime));
					
					if(hasActorDmaOutput (actr) == true)
					{
						// Take into account the dma setup time. 
						// the actor finishes only when dma setup is done.
						// so yActor = xDma + dma Setup Time.
						ArithExpr endDotTime;
						if(platform.getDmaSetupTime() > 0)
							endDotTime = ctx.mkAdd(xId(lastDmaChannel.getOpposite(actr).getName(), i), 
								ctx.mkInt(platform.getDmaSetupTime()));
						else
							endDotTime = xId(lastDmaChannel.getOpposite(actr).getName(), i);
						generateAssertion(ctx.mkEq(yDotId(actr.getName (), i), endDotTime));						
					}					
				} catch (Z3Exception e) { e.printStackTrace(); }
			}
		}
	}

	/**
	 * Generate definitions for processor allocation of all the tasks 
	 */
	private void generateCpuDefinitions ()
	{
		String arguments[] = new String[1];
		arguments[0] = "Int";
		// Processor Allocated to each Instance.		
		Iterator<Actor> actorIter = partitionAwareGraph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();			
			int repCount = partitionGraphSolutions.getSolution (actr).returnNumber ();			
			for (int i=0;i<repCount;i++)
			{
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.cpuPrefix + actr.getName () + "_" + Integer.toString (i), "Int");
				cpuDecl.put (SmtVariablePrefixes.cpuPrefix + actr.getName () + "_" + Integer.toString (i), id);
			}
		}		
	}

	/**
	 * Generate start,end times and duration for all the tasks.
	 */
	private void generateActorTimeDefinitions () 
	{		
		// Define Start Times
		defineStartTimes ();

		// Define Actor Durations.
		defineActorDuration ();

		// Define End Times		
		defineEndTimes ();				
	}
	
	/**
	 * Generate constraint for having at least one actor to start at zero time.
	 */
	private void zeroStartTimeActor()
	{
		int totalRepCount = 0;
		// In this old code, we have every actor start time xA >= 0
		Iterator<Actor> actorIter = partitionAwareGraph.getActors ();
		while (actorIter.hasNext ())
		{				
			Actor actr = actorIter.next ();
			if(actr.getActorType() == ActorType.DATAFLOW)
				totalRepCount += partitionGraphSolutions.getSolution (actr).returnNumber ();
		}

		BoolExpr orArgs[] = new BoolExpr[totalRepCount];
		int count = 0;
		actorIter = partitionAwareGraph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			int actorRepCount = partitionGraphSolutions.getSolution (actr).returnNumber ();
			for (int i=0;i< actorRepCount;i++)
			{
				// (xD 0)
				IntExpr startTimeIdx = xId (actr.getName (), i);				

				try
				{
					if(actr.getActorType() == ActorType.DATAFLOW)
						orArgs[count++] = ctx.mkEq (startTimeIdx, ctx.mkInt (0));
				} catch (Z3Exception e) { e.printStackTrace (); }
			}
		}

		// (assert (or (= xA_0 0) (= xB_0 0) (= xB_1 0) (= xC_0 0)))
		try
		{
			generateAssertion (ctx.mkOr (orArgs));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/**
	 * Lower and upper bounds on the start times of the actor
	 */
	private void assertStartTimeBounds ()
	{		
		BoolExpr andArgs[] = new BoolExpr[2];

		Iterator<Actor> actorIter = partitionAwareGraph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			int actorRepCount = partitionGraphSolutions.getSolution (actr).returnNumber ();
			for (int i=0;i< actorRepCount;i++)
			{
				// (xD 0)
				IntExpr startTimeIdx = xId (actr.getName (), i);				

				// (assert (and (>= xA_0 0) (<= xA_0 latency)))
				try
				{
					andArgs[0] = ctx.mkGe (startTimeIdx, ctx.mkInt (0));
					andArgs[1] = ctx.mkLe (startTimeIdx, getLatencyDeclId ());
					generateAssertion (ctx.mkAnd (andArgs));
				} catch (Z3Exception e) { e.printStackTrace (); }
			}
		}		
	}

	/**
	 * Lower and upper bound on the processor allocation of the tasks.
	 */
	public void assertTaskCpuBounds () 
	{		
		Iterator<Actor> actorIter = partitionAwareGraph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			if(actr.getActorType() == ActorType.DATAFLOW)
			{
				Cluster cluster = schedulingConstraints.getActorAllocatedCluster(actr.getName());
				int startProcIndex = platform.getProcIndex(cluster.getProcessor(0));
				for (int i=0;i<partitionGraphSolutions.getSolution (actr).returnNumber ();i++)
				{
					IntExpr cpuIdxId = cpuId (actr.getName (), i);

					try
					{
						generateAssertion (ctx.mkAnd (ctx.mkGe (cpuIdxId, ctx.mkInt (startProcIndex)), 
								ctx.mkLt (cpuIdxId, ctx.mkInt(startProcIndex + cluster.getNumProcInCluster()))));
					} catch (Z3Exception e) { e.printStackTrace (); }					
				}
			}
		}
	}
	
	/**
	 * We sort the ports of an actor by their names.
	 * This is done to determine the starting order for DMA.
	 * 
	 * @param actr actor for which ports are to be sorted
	 * @return sorted list of ports of the actor by name
	 */
	private List<Link> getSortedPortList (Actor actr)
	{
		List<Link> links = new ArrayList<Link> (actr.getAllLinks());
	
		// First we sort the channels according to the name of the ports.
		Collections.sort(links, new Comparator<Link>() {
	
			@Override
			public int compare(Link o1, Link o2) 
			{
				String portName1 = o1.getPort().getName().replaceAll ("[0-9]+","");
				String portName2 = o2.getPort().getName().replaceAll ("[0-9]+","");
				
				if(portName1.equals(portName2))
				{
	                String tStr1 = o1.getPort().getName().replaceAll ("[^\\d.]", "");
	                String tStr2 = o2.getPort().getName().replaceAll ("[^\\d.]", "");
	                return Integer.parseInt (tStr1) - Integer.parseInt (tStr2);
				}
				else
					return o1.getPort().getName().compareTo(o2.getPort().getName());
			}
		});
		return links;
	}
	
	
	/**
	 * We sort the ports of an actor by their names.
	 * This is done to determine the starting order for DMA.
	 * We eliminate the unwanted ports from the list
	 * 
	 * @param actr actor for which ports are to be sorted
	 * @return sorted list of channels of the actor by name
	 */
	private List<Channel> getSortedDmaPortList (Actor actr)
	{
		List<Channel> result = new ArrayList<Channel>();
		
		if(graph.hasActor(actr.getName()) == false)
			return result;
		
		Actor graphActor = graph.getActor(actr.getName());
		List<Link> graphActorPortList = getSortedPortList (graphActor);		
		List<Link> partitionActrPortList = getSortedPortList (actr);
		
		// First we remove the channels which are unaffected. Means not connected by DMA.
		for(int i=0;i<graphActorPortList.size();i++)
		{
			Link lnk = graphActorPortList.get(i);
			Actor oppositeActor = lnk.getChannel().getOpposite(graphActor);
			
			for(int j=0;j<partitionActrPortList.size();j++)
			{
				// Check if same link exists in partition aware graph.
				// If it does exist, then remove it from the list.
				Link paLink = partitionActrPortList.get(j);
				if(paLink.getChannel().getOpposite(actr).getName().equals(oppositeActor.getName()) == true)
				{
					graphActorPortList.remove(i);
					i--;
					
					partitionActrPortList.remove(j); 
					j--;
					break;
				}
			}
		}
		
		// Now we check the remaining links and determine what is correct dma ordering.
		if(graphActorPortList.size() > 0)
		{
			for(int i=0;i<graphActorPortList.size();i++)
			{
				Link lnk = graphActorPortList.get(i);
				Channel chnnl = lnk.getChannel();
				Port.DIR direction;
				
				if(chnnl.getLink(Port.DIR.IN) == lnk)
					direction = Port.DIR.IN;
				else
					direction = Port.DIR.OUT;
				
				// if this was a input channel, then only we need to check for the dma status task.
				if (direction == Port.DIR.IN)
				{					
					for(int j=0;j<partitionActrPortList.size();j++)
					{
						Link paLink = partitionActrPortList.get(j);
						Channel paChannel = paLink.getChannel();
						if(paChannel.getOpposite(actr).getName().equals(SmtVariablePrefixes.dmaStatusTaskPrefix + chnnl.getName()))
						{
							result.add(paChannel);
							break;
						}
					}
				}
				
				// we check for dma token task for output channel.
				else if (direction == Port.DIR.OUT)
				{
					for(int j=0;j<partitionActrPortList.size();j++)
					{
						Link paLink = partitionActrPortList.get(j);
						Channel paChannel = paLink.getChannel();
						if(paChannel.getOpposite(actr).getName().equals(SmtVariablePrefixes.dmaTokenTaskPrefix + chnnl.getName()))
						{
							result.add(paChannel);
							break;
						}
					}
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Get the last DMA port of the actor when sorted
	 * by name.
	 * 
	 * @param actr actor instance
	 * @return last DMA port of the actor
	 */
	private Channel getLastDmaPort (Actor actr)
	{
		List<Channel> result = getSortedDmaPortList(actr);
		if(result.size() > 0)
			return result.get(result.size()-1);
		else
			return null;
	}
	
	/**
	 * Check if the actor has DMA at its output or not.
	 * 
	 * @param actr actor instance
	 * @return true if actor has DMA at its output, false otherwise
	 */
	private boolean hasActorDmaOutput (Actor actr)
	{
		for(Channel chnnl : actr.getChannels(Port.DIR.OUT))
			if(chnnl.getOpposite(actr).getActorType() == ActorType.COMMUNICATION)
				return true;
		return false;
	}

	/**
	 * Generate Mutual Exclusion constraints for the dataflow actors.
	 * 
	 * @param actrList list of dataflow actors
	 */
	private void assertDataFlowMutualExclusion (List<String> actrList)
	{		
		for (int i=0;i< actrList.size ();i++)
		{
			Actor actr = partitionAwareGraph.getActor(actrList.get(i));

			boolean hasDmaOutput = hasActorDmaOutput (actr);
			// Check if this actor has a communication task at the output.

			for (int j=0;j<partitionGraphSolutions.getSolution (actr).returnNumber ();j++)
			{
				for (int k=j+1;k<partitionGraphSolutions.getSolution (actr).returnNumber ();k++)
				{
					// (assert (=> (= (cpuA 0) (cpuA 1)) (or (>= (xA 0) (yA 1)) (>= (xA 1) (yA 0)))))
					IntExpr cpuJ = cpuId (actr.getName (), j);
					IntExpr cpuK = cpuId (actr.getName (), k);

					IntExpr xJ = xId (actr.getName (), j);
					IntExpr xK = xId (actr.getName (), k);
					
					IntExpr yJ;
					IntExpr yK;
					
					if (hasDmaOutput == false)
					{	
						yJ = yId (actr.getName (), j);
						yK = yId (actr.getName (), k);
					}
					else
					{
						yJ = yDotId (actr.getName (), j);
						yK = yDotId (actr.getName (), k);
					}
					

					// Final Assertion
					try
					{
						generateAssertion (ctx.mkImplies (ctx.mkEq (cpuJ, cpuK),  
											ctx.mkOr (ctx.mkGe (xJ, yK), ctx.mkGe (xK, yJ))));
					} catch (Z3Exception e) { e.printStackTrace (); }
					
				}

				for (int k=i+1;k<actrList.size ();k++)
				{
					Actor otherActor = partitionAwareGraph.getActor(actrList.get (k));
					boolean otherActorHasDmaOutput = hasActorDmaOutput (otherActor);
					
					for (int l=0;l<partitionGraphSolutions.getSolution (otherActor).returnNumber ();l++)
					{
						// (=> (= (cpuD 0) (cpuC 0)) (or (>= (xD 0) (+ (xC 0) dC)) (>= (xC 0) (+ (xD 0) dD))))	
						// (cpuD 0)						
						IntExpr idxCpuA = cpuId (actr.getName (),j );
						IntExpr idxCpuB = cpuId (otherActor.getName (), l);

						// (xD 0)
						IntExpr idx_xA = xId (actr.getName (), j); 
						IntExpr idx_xB = xId (otherActor.getName (), l);
						IntExpr idx_yA, idx_yB;
						
						if(hasDmaOutput == false)
							idx_yA = yId (actr.getName (), j);
						else
							idx_yA = yDotId (actr.getName (), j);
						
						if(otherActorHasDmaOutput == false)
							idx_yB = yId (otherActor.getName (), l);
						else
							idx_yB = yDotId (otherActor.getName (), l);

						// Final Assertion
						try
						{
							generateAssertion (ctx.mkImplies (ctx.mkEq (idxCpuA, idxCpuB), 
									ctx.mkOr (ctx.mkGe (idx_xA, idx_yB), 
											ctx.mkGe (idx_xB, idx_yA))));
						} catch (Z3Exception e) { e.printStackTrace (); }					
					}						
				}
			}				
		}		
	}	

	/**
	 * Generate Mutual Exclusion constraints for the communication actors.
	 * 
	 * @param actrList list of communication actors
	 */
	private void assertCommunicationMutualExclusion (List<String> actrList)
	{		
		for (int i=0;i< actrList.size ();i++)
		{
			Actor actr = partitionAwareGraph.getActor(actrList.get (i));			

			for (int j=0;j<partitionGraphSolutions.getSolution (actr).returnNumber ();j++)
			{
				for (int k=j+1;k<partitionGraphSolutions.getSolution (actr).returnNumber ();k++)
				{
					// (assert (=> (= (cpuA 0) (cpuA 1)) (or (>= (xA 0) (yA 1)) (>= (xA 1) (yA 0)))))
					IntExpr cpuJ = cpuId (actr.getName (), j);
					IntExpr cpuK = cpuId (actr.getName (), k);

					IntExpr xJ = xId (actr.getName (), j);
					IntExpr xK = xId (actr.getName (), k);

					IntExpr yJ = yId (actr.getName (), j);
					IntExpr yK = yId (actr.getName (), k);

					// Final Assertion
					try
					{
						generateAssertion (ctx.mkImplies (ctx.mkEq (cpuJ, cpuK),  
								ctx.mkOr (ctx.mkGe (xJ, yK), ctx.mkGe (xK, yJ))));
					} catch (Z3Exception e) { e.printStackTrace (); }										
				}

				for (int k=i+1;k<actrList.size ();k++)
				{
					Actor otherActor = partitionAwareGraph.getActor(actrList.get (k));

					for (int l=0;l<partitionGraphSolutions.getSolution (otherActor).returnNumber ();l++)
					{
						// (=> (= (cpuD 0) (cpuC 0)) (or (>= (xD 0) (+ (xC 0) dC)) (>= (xC 0) (+ (xD 0) dD))))	
						// (cpuD 0)						
						IntExpr idxCpuA = cpuId (actr.getName (),j );
						IntExpr idxCpuB = cpuId (otherActor.getName (), l);

						// (xD 0)
						IntExpr idx_xA = xId (actr.getName (), j); 
						IntExpr idx_xB = xId (otherActor.getName (), l);						

						IntExpr idx_yA = yId (actr.getName (), j); 
						IntExpr idx_yB = yId (otherActor.getName (), l);

						// Final Assertion
						try
						{
							generateAssertion (ctx.mkImplies (ctx.mkEq (idxCpuA, idxCpuB), 
									ctx.mkOr (ctx.mkGe (idx_xA, idx_yB), 
											ctx.mkGe (idx_xB, idx_yA))));
						} catch (Z3Exception e) { e.printStackTrace (); }
					}						
				}
			}				
		}		
	}

	/**
	 * Generate all the mutual exclusion constraints
	 */
	private void assertMutualExclusion ()
	{
		// Mutual Exclusion for Dataflow actors
		HashMap<Cluster, HashSet<String>> actorToClusterMap = schedulingConstraints.getActorsMappedToCluster ();		
		for(Cluster cluster : actorToClusterMap.keySet())
		{
			List<String> actrList = new ArrayList<String>(actorToClusterMap.get(cluster));			
			assertDataFlowMutualExclusion (actrList);			
		}

		// Mutual Exclusion for Communication Actors.	
		actorToClusterMap = schedulingConstraints.getActorsMappedToDmaOfCluster ();		
		for(Cluster cluster : actorToClusterMap.keySet())
		{
			List<String> actrList = new ArrayList<String>(actorToClusterMap.get(cluster));			
			assertCommunicationMutualExclusion (actrList);			
		}
	}

	/**
	 * Generate a list of precedences between the producer and consumer task instances.
	 * 
	 * @param chnnl channel for which precedences should be derived
	 * @return precedence map containing index of consumer and list of preceding indexes of all the producers
	 */
	private HashMap<Integer, List<Integer>> calculateActorPrecedences (Channel chnnl)
	{
		// Actor Precedences				
		Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
		Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();			

		HashMap<Integer, List<Integer>> precedenceList = new HashMap<Integer, List<Integer>>();
		// Initialize the List.
		for (int i=0;i<partitionGraphSolutions.getSolution (dstActor).returnNumber ();i++)
		{
			List<Integer> tempIntList = new ArrayList<Integer>();
			precedenceList.put (i, tempIntList);
		}

		if (chnnl.getInitialTokens () == 0)
		{
			// Since we have equal repetition count, the precedence is one is to one.
			if (partitionGraphSolutions.getSolution (srcActor).returnNumber () == partitionGraphSolutions.getSolution (dstActor).returnNumber ())
			{					
				for (int i=0;i<partitionGraphSolutions.getSolution (srcActor).returnNumber ();i++)					
					precedenceList.get (i).add (i);
			}
			else
			{
				// Since we have unequal rates and repetition count, we have to schedule accordingly.
				// The Tokens are produced on FIFO order, thus tokens generated by first producer instance
				// should be consumed by first consumer. if extra left, it can be consumed by second consumer
				// if they are less than consumption rate, the first consumer will then consume from
				// second producer instance.
				int repDst = partitionGraphSolutions.getSolution (dstActor).returnNumber ();
				int rateSrc = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
				int rateDst = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());

				int srcIndex = 0;
				int srcTokens = rateSrc;

				for (int i=0;i<repDst;i++)
				{
					int dstTokens = rateDst;

					while (dstTokens > 0)
					{
						precedenceList.get (i).add (srcIndex);						

						if (dstTokens <= srcTokens )
						{
							srcTokens -= dstTokens;
							dstTokens = 0;								
						}
						else
						{
							dstTokens -= srcTokens;
							srcTokens = 0;
						}

						if (srcTokens == 0)
						{
							srcIndex ++;
							srcTokens += rateSrc;
						}							
					}
				}					
			}
		}
		else
		{
			// Check if this is Stateful actor?
			if (srcActor == dstActor)
			{
				// we can have more than one initial tokens on self-edge.
				int initialTokens = chnnl.getInitialTokens ();
				// the production rate has to be equal to consumption rate. otherwise graph is inconsistent.
				int portRate = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());					
				for (int i=(initialTokens/portRate);i<partitionGraphSolutions.getSolution (srcActor).returnNumber ();i++)					
					precedenceList.get (i).add (i-(initialTokens/portRate));
			}
			else
			{
				// With initial tokens, we need to consider multiple iterations.
				int repDst = partitionGraphSolutions.getSolution (dstActor).returnNumber ();					
				int rateSrc = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
				int rateDst = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());
				int initialTokens = chnnl.getInitialTokens ();

				int srcIndex = 0;
				int srcTokens = rateSrc;

				for (int i=0;i<repDst;i++)
				{
					if (initialTokens >= rateDst)
					{
						initialTokens -= rateDst;							
						continue;
					}

					int dstTokens = rateDst;

					while (dstTokens > 0)
					{
						precedenceList.get (i).add (srcIndex);						

						if (dstTokens <= (srcTokens + initialTokens ))
						{								
							srcTokens -= (dstTokens - initialTokens);
							dstTokens = 0;
							initialTokens = 0;
						}
						else
						{
							dstTokens -= (srcTokens + initialTokens);
							srcTokens = 0;
							initialTokens = 0;								
						}
						if (srcTokens == 0)
						{
							srcIndex ++;
							srcTokens += rateSrc;
						}							
					}						
				}
			}				
		}

		return precedenceList;
	}

	/**
	 * Generate latency calculation for the application graph.
	 */
	private void generateLatencyCalculation ()
	{
		GraphAnalysisSdfAndHsdf analysis = new GraphAnalysisSdfAndHsdf (partitionAwareGraph, partitionGraphSolutions, partitionAwareHsdf);
		List<Actor> lastActorList = analysis.findHsdfEndActors ();
		if (lastActorList.size () == 0)
			throw new RuntimeException ("Unable to find the End Actor");
		
		BoolExpr orArgs[] = new BoolExpr[lastActorList.size()];
		BoolExpr andArgs[] = new BoolExpr[lastActorList.size()];
		
		// for(Actor actr : lastActorList)
		// generateAssertion(ctx.mkLe(yId (actr.getName()), getLatencyDeclId()));
		// generateAssertion(ctx.mkLe(yDotId (actr.getName()), getLatencyDeclId()));
		
		try
		{
			
			for(int i=0;i<lastActorList.size();i++)
			{
				Actor actr = lastActorList.get(i);
				
				if(yDotId (actr.getName()) == null)
				{
					orArgs[i] = ctx.mkEq(getLatencyDeclId(), yId (actr.getName()));
					andArgs[i] = ctx.mkGe(getLatencyDeclId(), yId (actr.getName()));
				}					
				else
				{
					orArgs[i] = ctx.mkEq(getLatencyDeclId(), yDotId (actr.getName()));
					andArgs[i] = ctx.mkGe(getLatencyDeclId(), yDotId (actr.getName()));
				}				
			}
			
			generateAssertion(ctx.mkOr(orArgs));
			generateAssertion(ctx.mkAnd(andArgs));
		}
		catch (Z3Exception e) { e.printStackTrace(); }
	}

	/**
	 * Generate all the actor precedence constraints.
	 */
	private void actorPrecedences ()
	{
		// Actor Precedences
		Iterator<Channel> iterChnnl = partitionAwareGraph.getChannels ();
		while (iterChnnl.hasNext ())
		{
			Channel chnnl = iterChnnl.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();	
			HashMap<Integer, List<Integer>> precedenceList = calculateActorPrecedences (chnnl);

			// Actor Precedences 
			// (assert (and (>= (xB 0) (+ (xA 0) dA)) (>= (xB 0) (+ (xA 1) dA))))
			// (assert (and (>= (xB 1) (+ (xA 1) dA)) (>= (xB 1) (+ (xA 2) dA))))

			//Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			//Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();	

			for (int i=0;i<partitionGraphSolutions.getSolution (dstActor).returnNumber ();i++)
			{
				if (precedenceList.get (i).size () == 0)
					continue;

				for (int j=0;j<precedenceList.get (i).size ();j++)
				{
					// System.out.println ("Src : " + srcActor.getName () + " Dst : " + dstActor.getName () + " i : " + i + " j : " + j);
					IntExpr srcIdxId = xId (dstActor.getName (),i);
					IntExpr dstIdxId = yId (srcActor.getName (), precedenceList.get (i).get (j));

					try
					{
						generateAssertion (ctx.mkGe (srcIdxId, dstIdxId));
					} catch (Z3Exception e) { e.printStackTrace (); }
				}
			}			
		}		
	}

	/**
	 * Lower bound on the latency.
	 */
	private void minLatencyBound ()
	{
		int dataFlowMaxLatency = 0, commTaskMaxLatency = 0;
		int latency = 0;
		
		for(Cluster cluster : platform.getAllClusters())
		{
			HashSet<String> actors = schedulingConstraints.getActorsAllocatedToCluster(cluster);
			if(actors.size() != 0)
			{
				int tempLatency = 0;
				for(String actrName : actors)
				{
					Actor actr = partitionAwareGraph.getActor(actrName);
					tempLatency += actr.getExecTime() * 
								partitionGraphSolutions.getSolution(
										partitionAwareGraph.getActor(actr.getName())).returnNumber();
				}
				
				dataFlowMaxLatency += (tempLatency / cluster.getNumProcInCluster());
			}
			
			HashSet<String> dmaActors = schedulingConstraints.getActorsMappedToDmaOfCluster(cluster);
			if(dmaActors.size() != 0)
			{
				int tempLatency = 0;
				for(String actrName : dmaActors)
				{
					Actor actr = partitionAwareGraph.getActor(actrName);
					tempLatency += actr.getExecTime() * 
								partitionGraphSolutions.getSolution(partitionAwareGraph.getActor(actr.getName())).returnNumber();
				}
				
				commTaskMaxLatency += (tempLatency / cluster.getNumDmaInCluster());
			}			
		}
		
		// in best case, the dma and comp. tasks are overlapping with each other. so we take max.
		latency = ((dataFlowMaxLatency > commTaskMaxLatency) ? dataFlowMaxLatency : commTaskMaxLatency);		
		
		// Assert that Latency >= (Max Latency / No of Processors)
		try { generateAssertion (ctx.mkGe (getLatencyDeclId (), ctx.mkInt (latency)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/**
	 * Lower and upper bounds on DMA engines allocated to
	 * communication tasks. 
	 */
	private void assertDmaTaskProcBounds ()
	{
		for(int i=0;i<platform.getNumClusters();i++)
		{
			Cluster cluster = platform.getCluster(i);
			int numDmaEngines = cluster.getNumDmaInCluster();
			int dmaStartIndex = platform.getDmaEngineIndex(cluster.getDmaEngine(0));
			List<String> commTasksOnThisCluster = schedulingConstraints.getActorsAllocatedToDmaOfCluster(cluster);

			for(String commTaskName : commTasksOnThisCluster)
			{
				Actor commTask = partitionAwareGraph.getActor(commTaskName);
				int repCount = partitionGraphSolutions.getSolution (commTask).returnNumber ();
				try
				{					
					for (int j=0;j<repCount;j++)
					{
						IntExpr dmaEngineId = cpuId(commTask.getName(), j);
						if (numDmaEngines > 1)
							generateAssertion (ctx.mkAnd(ctx.mkGe(dmaEngineId, ctx.mkInt(dmaStartIndex)), 
									ctx.mkLt(dmaEngineId, ctx.mkInt(dmaStartIndex + numDmaEngines))));
						else
							generateAssertion (ctx.mkEq(dmaEngineId, ctx.mkInt(dmaStartIndex)));
					}
				} catch (Z3Exception e) { e.printStackTrace (); }
			}
		}
	}

	/**
	 * Task Symmetry constraints
	 */
	private void graphSymmetryLexicographic ()
	{
		Iterator<Actor> actrIter = partitionAwareGraph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			if (partitionGraphSolutions.getSolution (actr).returnNumber () > 1)
			{
				for (int i=1;i<partitionGraphSolutions.getSolution (actr).returnNumber ();i++)
				{
					try
					{
						generateAssertion (ctx.mkLe (xId (actr.getName (), i-1), xId (actr.getName (), i)));
					} catch (Z3Exception e) { e.printStackTrace (); }
				}
			}
		}
	}

	/**
	 * Generate a Schedule of design flow solution from the model obtained from the SMT solver.
	 * @param model the model obtained from the SMT solver.
	 * @param designSolution design flow solution
	 * @return a schedule for design flow solution
	 */
	public Schedule modelToSchedule (Map<String, String> model, DesignFlowSolution designSolution)
	{
		Schedule schedule = designSolution.new Schedule();

		// Add Start times
		Iterator<Actor> actrIter = graph.getActors();
		while(actrIter.hasNext())
		{
			Actor actr = actrIter.next();

			for(int i=0;i<solutions.getSolution(actr).returnNumber();i++)
			{
				int cpuIndex = Integer.parseInt(model.get(SmtVariablePrefixes.cpuPrefix + actr.getName() + "_" + Integer.toString (i)));
				int startTime = Integer.parseInt(model.get(SmtVariablePrefixes.startTimePrefix + actr.getName() + "_" + Integer.toString (i)));
				schedule.addActor (actr.getName(), i, platform.getProcessor(cpuIndex), startTime);
			}
		}

		actrIter = partitionAwareGraph.getActors();
		while(actrIter.hasNext())
		{
			Actor actr = actrIter.next();
			if(actr.getActorType() == ActorType.COMMUNICATION)
			{
				for(int i=0;i<partitionGraphSolutions.getSolution(actr).returnNumber();i++)
				{
					int cpuIndex = Integer.parseInt(model.get(SmtVariablePrefixes.cpuPrefix + actr.getName() + "_" + Integer.toString (i)));
					int startTime = Integer.parseInt(model.get(SmtVariablePrefixes.startTimePrefix + actr.getName() + "_" + Integer.toString (i)));
					schedule.addActor (actr.getName(), i, platform.getDmaEngine(cpuIndex), startTime);
				}
			}
			else if(actr.getActorType() == ActorType.DATAFLOW)
			{
				if(graph.hasActor(actr.getName()) == false)
				{
					for(int i=0;i<partitionGraphSolutions.getSolution(actr).returnNumber();i++)
					{
						int cpuIndex = Integer.parseInt(model.get(SmtVariablePrefixes.cpuPrefix + actr.getName() + "_" + Integer.toString (i)));
						int startTime = Integer.parseInt(model.get(SmtVariablePrefixes.startTimePrefix + actr.getName() + "_" + Integer.toString (i)));
						schedule.addActor (actr.getName(), i, platform.getProcessor(cpuIndex), startTime);
					}
				}
			}
		}

		// Add Buffer Sizes to the schedule.
		if(bufferAnalysis == true)
		{
			Iterator<Channel> chnnlIter = graph.getChannels();
			while(chnnlIter.hasNext())
			{
				Channel chnnl = chnnlIter.next();
				Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
				Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();

				int bufferSize = 0;
				// Check if src and destination is in the same cluster.
				if(schedulingConstraints.getActorAllocatedCluster(srcActor.getName()) == schedulingConstraints.getActorAllocatedCluster(dstActor.getName()))
					bufferSize = Integer.parseInt(model.get(SmtVariablePrefixes.maxBufferPrefix + srcActor.getName() + dstActor.getName()));
				else
				{
					Actor dmaTokenTask = partitionAwareGraph.getActor(SmtVariablePrefixes.dmaTokenTaskPrefix + chnnl.getName());
					bufferSize = Integer.parseInt(model.get(SmtVariablePrefixes.maxBufferPrefix + srcActor.getName() + dmaTokenTask.getName()));
					bufferSize += Integer.parseInt(model.get(SmtVariablePrefixes.maxBufferPrefix + dmaTokenTask.getName() + dstActor.getName()));
				}

				bufferSize /= chnnl.getTokenSize();

				schedule.addBufferSize (chnnl.getName(), bufferSize);
			}
		}

		return schedule;		
	}

	/**
	 * Generate all the definitions required for calculating communication buffer size
	 */
	private void generateBufferAnalysisDefinitions ()
	{
		try
		{
			if(useMaxBuffer == true)
			{
				// clusterBufferPrefix
				HashMap<Cluster, HashSet<String>>mapping = schedulingConstraints.getActorsMappedToCluster();
				for(Cluster cluster : mapping.keySet())
				{
					IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.clusterBufferPrefix + cluster.getName(), "Int");
					bufferDecl.put (SmtVariablePrefixes.clusterBufferPrefix + cluster.getName(), id);
				}
			}
			
			Sort arguments[] = new Sort[1];
			arguments[0] = ctx.getIntSort ();

			// Buffer Definitions		
			Iterator<Channel> iterChnnl = graph.getChannels ();
			while (iterChnnl.hasNext ())
			{
				Channel chnnl = iterChnnl.next ();
				Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
				Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
				int srcRepCount = solutions.getSolution (srcActor).returnNumber ();
				int dstRepCount = solutions.getSolution (dstActor).returnNumber ();
				int initialTokens = chnnl.getInitialTokens();

				if(schedulingConstraints.getActorAllocatedCluster(srcActor.getName()) != schedulingConstraints.getActorAllocatedCluster(dstActor.getName()))
				{
					// We have source and destination on different clusters.
					// Now this is very crude way of doings things... what to do... I am in a hurry !
					Actor dmaTokenTask = partitionAwareGraph.getActor(SmtVariablePrefixes.dmaTokenTaskPrefix + chnnl.getName());
					
					if (((srcRepCount > 1) && (dstRepCount > 1)) || (initialTokens != 0))
					{
						for (int i=0;i<srcRepCount;i++)
						{
							IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.bufferAtPrefix + srcActor.getName () +"_"
									+ Integer.toString (i) + "-" + srcActor.getName () 
									+ dmaTokenTask.getName (), "Int");
							bufferDecl.put (SmtVariablePrefixes.bufferAtPrefix + srcActor.getName () +"_"
									+ Integer.toString (i) + "-" + srcActor.getName () 
									+ dmaTokenTask.getName (), id);
						}
	
						for (int i=0;i<srcRepCount;i++)
						{
							IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.bufferAtPrefix + dmaTokenTask.getName () +"_"
									+ Integer.toString (i) + "-" + dmaTokenTask.getName () 
									+ dstActor.getName (), "Int");
							bufferDecl.put (SmtVariablePrefixes.bufferAtPrefix + dmaTokenTask.getName () +"_"
									+ Integer.toString (i) + "-" + dmaTokenTask.getName () 
									+ dstActor.getName (), id);
						}
					}

					IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.maxBufferPrefix + srcActor.getName () + dmaTokenTask.getName (), "Int");
					bufferDecl.put (SmtVariablePrefixes.maxBufferPrefix + srcActor.getName () + dmaTokenTask.getName (), id);

					id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.maxBufferPrefix + dmaTokenTask.getName () + dstActor.getName (), "Int");
					bufferDecl.put (SmtVariablePrefixes.maxBufferPrefix + dmaTokenTask.getName () + dstActor.getName (), id);

				}
				else
				{	
					// Generate definitions only for writer of the channel.
					// Since we consider that readers of the channel don't contribute to max channel size.
					// Self-Edge? No need to generate a variable.
					
					if (((srcRepCount > 1) && (dstRepCount > 1)) || (initialTokens != 0))
					{
						for (int i=0;i<srcRepCount;i++)
						{
							IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.bufferAtPrefix + srcActor.getName () +"_"
									+ Integer.toString (i) + "-" + srcActor.getName () 
									+ dstActor.getName (), "Int");
							bufferDecl.put (SmtVariablePrefixes.bufferAtPrefix + srcActor.getName () +"_"
									+ Integer.toString (i) + "-" + srcActor.getName () 
									+ dstActor.getName (), id);
						}
					}

					IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.maxBufferPrefix + srcActor.getName () + dstActor.getName (), "Int");
					bufferDecl.put (SmtVariablePrefixes.maxBufferPrefix + srcActor.getName () + dstActor.getName (), id);
				}
			}
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/**
	 * Constraint to determine the buffer size at the start of a producer of the channel depending 
	 * on the start times of other producer and consumer instances.
	 * 
	 * @param srcActor source actor
	 * @param dstActor sink actor 
	 * @param initialTokens number of initial tokens on the channel
	 * @param tokenSize token size
	 * @param srcRepCount repetition count of the source actor
	 * @param dstRepCount repetition count of the sink actor
	 * @param prodRate production rate of tokens
	 * @param consRate consumption rate of tokens
	 */
	private void assertBufferLinearNonPipelined (String srcActor, String dstActor,  int initialTokens, int tokenSize, 
			int srcRepCount, int dstRepCount, int prodRate, int consRate)
	{		
		// (assert (= bufferAtA_0-AB (- (- (+ (if (>= xA_0 xA_2) 2 0) (+ (if (>= xA_0 xA_1) 2 0) 0)) (if (>= xA_0 (+ xB_0 2)) 3 0)) (if (>= xA_0 (+ xB_1 2)) 3 0))))		
		// (assert (= maxBufAtA_0-AB (- (- (+ 0 2 (if (>= xA_0 xA_1) 2 0) (if (>= xA_0 xA_2) 2 0)) (if (>= xA_0 (+ xB_0 2)) 3 0)) (if (>= xA_0 (+ xB_1 2)) 3 0))))

		ArithExpr addArgs1[] = new ArithExpr[srcRepCount];
		ArithExpr addArgs2[] = new ArithExpr[dstRepCount];
		
		try
		{
			if (srcActor.equals (dstActor) == false)
			{
				for (int i=0;i<srcRepCount;i++)
				{
					IntExpr buffId = bufferAtId (srcActor, dstActor, i);
					IntExpr currXId = xId (srcActor, i);
					
					int count = 0;
					for (int j=0;j<srcRepCount;j++)
					{
						if (i==j)
							continue;

						addArgs1[count++]  = (ArithExpr) ctx.mkITE (ctx.mkGe (currXId, xId (srcActor,j)), 
								ctx.mkInt (prodRate*tokenSize), 
								ctx.mkInt (0));
					}
					
					addArgs1[srcRepCount-1] = ctx.mkInt((prodRate + initialTokens) * tokenSize); 

					for (int j=0;j<dstRepCount;j++)
					{
						addArgs2[j]  = (ArithExpr) ctx.mkITE (ctx.mkGe (currXId, yId (dstActor,j)), 
								ctx.mkInt (consRate*tokenSize), 
								ctx.mkInt (0));
					}

					generateAssertion (ctx.mkEq (buffId, ctx.mkSub(ctx.mkAdd(addArgs1), ctx.mkAdd(addArgs2))));
				}
			}
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/**
	 * Assert buffer constraints for channels with DMA at the output. 
	 * 
	 * @param channelName name of the channel
	 * @param srcActor source actor
	 * @param dstActor sink actor
	 * @param initialTokens number of initial tokens
	 * @param tokenSize size of the token
	 * @param srcRepCount source actor repetition count
	 * @param dstRepCount sink actor repetition count
	 * @param prodRate token production rate on the channel
	 * @param consRate token consumption rate on the channel
	 */
	private void assertBufferLinearNonPipelinedWithDma (String channelName, String srcActor, String dstActor,  
			int initialTokens, int tokenSize,
			int srcRepCount, int dstRepCount, int prodRate, int consRate)
	{		
		// (assert (= bufferAtA_0-AB (- (- (+ (if (>= xA_0 xA_2) 2 0) (+ (if (>= xA_0 xA_1) 2 0) 0)) (if (>= xA_0 (+ xB_0 2)) 3 0)) (if (>= xA_0 (+ xB_1 2)) 3 0))))		
		// (assert (= maxBufAtA_0-AB (- (- (+ 0 2 (if (>= xA_0 xA_1) 2 0) (if (>= xA_0 xA_2) 2 0)) (if (>= xA_0 (+ xB_0 2)) 3 0)) (if (>= xA_0 (+ xB_1 2)) 3 0))))

		// get the dma token task and dma status task
		String dmaTaskName = SmtVariablePrefixes.dmaTokenTaskPrefix + channelName;
		String dmaStatusTaskName = SmtVariablePrefixes.dmaStatusTaskPrefix + channelName;
		String dmaWriterStatusTaskName = channelName;

		try
		{
			// This part serves the source actor to DMA actor
			for (int i=0;i<srcRepCount;i++)
			{
				IntExpr buffId = bufferAtId (srcActor, dmaTaskName, i);
				IntExpr currXId = xId (srcActor, i);
				ArithExpr addArgs1[] = new ArithExpr[srcRepCount];
				ArithExpr addArgs2[] = new ArithExpr[srcRepCount];

				int count = 0;
				for (int j=0;j<srcRepCount;j++)
				{
					if (i==j)
						continue;

					addArgs1[count++]  = (ArithExpr) ctx.mkITE (ctx.mkGe (currXId, xId (srcActor, j)), 
							ctx.mkInt (prodRate*tokenSize), 
							ctx.mkInt (0));					
				}
				
				addArgs1[srcRepCount-1] = ctx.mkInt((initialTokens + prodRate)*tokenSize);
				
				for (int j=0;j<srcRepCount;j++)
				{
					// System.out.println(dmaWriterStatusTaskName + " : " + j);
					addArgs2[j]  = (ArithExpr) ctx.mkITE (ctx.mkGe (currXId, yId (dmaWriterStatusTaskName, j)), 
							ctx.mkInt (prodRate*tokenSize), 
							ctx.mkInt (0));
				}

				generateAssertion (ctx.mkEq (buffId, ctx.mkSub(ctx.mkAdd(addArgs1), ctx.mkAdd(addArgs2))));				
			}

			// This part serves the DMA actor to destination actor.
			// I don't have to change initial tokens Id.

			for (int i=0;i<srcRepCount;i++)
			{
				IntExpr buffId = bufferAtId (dmaTaskName, dstActor, i);
				IntExpr currXId = xId (dmaTaskName, i);
				
				ArithExpr addArgs1[] = new ArithExpr[srcRepCount];
				ArithExpr addArgs2[] = new ArithExpr[dstRepCount];
				

				int count = 0;
				for (int j=0;j<srcRepCount;j++)
				{
					if (i==j)
						continue;

					addArgs1[count++]  = (ArithExpr) ctx.mkITE (ctx.mkGe (currXId, xId (dmaTaskName, j)), 
							ctx.mkInt (prodRate*tokenSize), 
							ctx.mkInt (0));					
				}
				
				addArgs1[srcRepCount-1] = ctx.mkInt((initialTokens*tokenSize + prodRate*tokenSize));

				for (int j=0;j<dstRepCount;j++)
				{
					addArgs2[j]  = (ArithExpr) ctx.mkITE (ctx.mkGe (currXId, yId (dmaStatusTaskName, j)), 
							ctx.mkInt (consRate*tokenSize), 
							ctx.mkInt (0));					
				}

				generateAssertion (ctx.mkEq (buffId, ctx.mkSub(ctx.mkAdd(addArgs1), ctx.mkAdd(addArgs2))));				
			}

		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/**
	 * Calculate maximum buffer size for a given channel
	 * 
	 * @param srcActor source actor
	 * @param dstActor sink actor
	 * @param srcRepCount source actor repetition count
	 * @param initialTokens number of initial tokens on the channel
	 * @param tokenSize size of the token
	 */
	private void assertMaxBufferLinear (String srcActor, String dstActor, int srcRepCount, int initialTokens, int tokenSize)
	{
		try
		{
			if (srcActor.equals (dstActor) == true)
			{
				IntExpr maxBuffId = maxBufferId (srcActor, srcActor);
				generateAssertion (ctx.mkEq (maxBuffId, ctx.mkInt(initialTokens * tokenSize)));
			}
			else
			{		
				BoolExpr orArgs[] = new BoolExpr[srcRepCount+1];
				BoolExpr andArgs[] = new BoolExpr[srcRepCount+1];

				IntExpr maxBuffId = maxBufferId (srcActor, dstActor);

				orArgs[0] = ctx.mkEq (maxBuffId, ctx.mkInt(initialTokens * tokenSize));
				andArgs[0] = ctx.mkGe (maxBuffId, ctx.mkInt(initialTokens * tokenSize));

				for (int i=0;i< srcRepCount; i++)
				{
					IntExpr buffId = bufferAtId (srcActor, dstActor, i);

					orArgs[i+1] = ctx.mkEq (maxBuffId, buffId);
					andArgs[i+1] = ctx.mkGe (maxBuffId, buffId);
				}

				generateAssertion (ctx.mkOr (orArgs));
				generateAssertion (ctx.mkAnd (andArgs));
			}
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/**
	 * Generate constraints for upper and lower bounds on the buffer size of a channel.
	 * 
	 * @param srcActor source actor
	 * @param dstActor sink actor
	 * @param srcRepCount source repetition count
	 * @param lowerBound lower bound on buffer size
	 * @param upperBound upper bound on buffer size
	 */
	private void assertBufferBoundsLinear (String srcActor, String dstActor, int srcRepCount, int lowerBound, int upperBound)
	{
		BoolExpr andArgs[] = new BoolExpr[2];

		try
		{		
			if ((srcActor.equals (dstActor) == false))
			{			
				for (int i=0;i<srcRepCount;i++)
				{			
					IntExpr buffId = bufferAtId (srcActor, dstActor, i);
					// we cannot apply GCD lower bound here, because buffer will always start from zero (or initial tokens)
					// and will increase by value of production rate. so only max buffer can have the GCD lower bound.
					andArgs[0] = ctx.mkGe (buffId, ctx.mkInt (0));
					andArgs[1] = ctx.mkLe (buffId, ctx.mkInt (upperBound));
					generateAssertion(ctx.mkAnd(andArgs));
				}
			}

			IntExpr maxBuffId = maxBufferId (srcActor, dstActor);
			andArgs[0] = ctx.mkGe (maxBuffId, ctx.mkInt (lowerBound));
			andArgs[1] = ctx.mkLe (maxBuffId, ctx.mkInt (upperBound));		
			generateAssertion (ctx.mkAnd (andArgs));
		} catch (Z3Exception e) { e.printStackTrace (); }		
	}

	/**
	 * Generate constraints for maximum buffer size per cluster.
	 */
	private void assertMaxBuffer ()
	{
		try
		{
			// clusterBufferPrefix
			HashMap<Cluster, HashSet<String>>mapping = schedulingConstraints.getActorsMappedToCluster();
			// int numClusters = mapping.keySet().size();
			// BoolExpr orArgs[] = new BoolExpr[numClusters];
			// BoolExpr andArgs[] = new BoolExpr[numClusters];
		
			// int count = 0;
			for(Cluster cluster : mapping.keySet())
			{
				IntExpr clusterBuffId = clusterBufferId(cluster.getName());
				//andArgs[count] = ctx.mkGe(getBufDeclId(), clusterBuffId);
				// orArgs[count++] = ctx.mkEq(getBufDeclId(), clusterBuffId);
				generateAssertion(ctx.mkLe(clusterBuffId, getBufDeclId()));
			}
			
			// generateAssertion(ctx.mkOr(orArgs));
			// generateAssertion(ctx.mkAnd(andArgs));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * Generate constraints to calculate maximum buffer used in a cluster
	 */
	private void assertMaxClusterBuffer()
	{
		HashMap<Cluster, ArrayList<IntExpr>> bufferMap = new HashMap<Cluster, ArrayList<IntExpr>>();
		Iterator<Channel> iterChnnl = graph.getChannels ();
		
		while (iterChnnl.hasNext ())
		{
			Channel chnnl = iterChnnl.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
			Cluster srcCluster = schedulingConstraints.getActorAllocatedCluster(srcActor.getName());
			Cluster dstCluster = schedulingConstraints.getActorAllocatedCluster(dstActor.getName());
			
			if(srcCluster == dstCluster)
			{
				if(bufferMap.containsKey(srcCluster) == false)
					bufferMap.put(srcCluster, new ArrayList<IntExpr>());
				
				bufferMap.get(srcCluster).add(maxBufferId (srcActor.getName (), dstActor.getName ()));
			}
			else
			{				
				if(bufferMap.containsKey(srcCluster) == false)
					bufferMap.put(srcCluster, new ArrayList<IntExpr>());
				
				if(bufferMap.containsKey(dstCluster) == false)
					bufferMap.put(dstCluster, new ArrayList<IntExpr>());
				
				String dmaTaskName = SmtVariablePrefixes.dmaTokenTaskPrefix + chnnl.getName();
				bufferMap.get(srcCluster).add(maxBufferId (srcActor.getName (), dmaTaskName));
				bufferMap.get(dstCluster).add(maxBufferId (dmaTaskName, dstActor.getName ()));
			}
		}

		try
		{
			for(Cluster cluster : bufferMap.keySet())
			{
				List<IntExpr>buffers = bufferMap.get(cluster);
				IntExpr args[] = buffers.toArray(new IntExpr[buffers.size()]);
				if(buffers.size() > 1)
					generateAssertion (ctx.mkEq (clusterBufferId(cluster.getName()), ctx.mkAdd (args)));
				else
					generateAssertion (ctx.mkEq (clusterBufferId(cluster.getName()), buffers.get(0)));
			}
		} catch (Z3Exception e) { e.printStackTrace (); }
		
	}
	
	/**
	 * Generate calculation for total buffer size
	 */
	private void assertTotalBuffer ()
	{
		List<IntExpr> buffers = new ArrayList<IntExpr>();

		Iterator<Channel> iterChnnl = graph.getChannels ();
		while (iterChnnl.hasNext ())
		{
			Channel chnnl = iterChnnl.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
			if(schedulingConstraints.getActorAllocatedCluster(srcActor.getName()) != schedulingConstraints.getActorAllocatedCluster(dstActor.getName()))
			{
				String dmaTaskName = SmtVariablePrefixes.dmaTokenTaskPrefix + chnnl.getName();
				buffers.add(maxBufferId (srcActor.getName (), dmaTaskName));
				buffers.add(maxBufferId (dmaTaskName, dstActor.getName ()));
			}
			else				
				buffers.add(maxBufferId (srcActor.getName (), dstActor.getName ())); 			
		}

		try
		{
			IntExpr args[] = buffers.toArray(new IntExpr[buffers.size()]);
			generateAssertion (ctx.mkEq (getBufDeclId(), ctx.mkAdd (args)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/**
	 * Generate all the constraints for buffer-size calculation.
	 */
	private void assertBufferCalculation ()
	{		
		// Buffer Calculations.	
		Iterator<Channel> iterChnnl = graph.getChannels ();
		while (iterChnnl.hasNext ())
		{
			Channel chnnl = iterChnnl.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();

			int srcRepCount = solutions.getSolution (srcActor).returnNumber ();
			int dstRepCount = solutions.getSolution (dstActor).returnNumber ();
			int srcRate = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
			int dstRate = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());
			int initialTokens = chnnl.getInitialTokens();
			int tokenSize = chnnl.getTokenSize();

			// We check if this channel is split between the clusters.
			// First we calculate GCD.
			int ratesGcd = Integer.parseInt (Expression.gcd (new Expression (Integer.toString (srcRate)), 
											     new Expression (Integer.toString (dstRate))).toString());
			
			int bufferMinBound =  ((srcRate + dstRate - ratesGcd + (initialTokens % ratesGcd))*tokenSize);
			int bufferMaxBound = tokenSize * (chnnl.getInitialTokens () + (srcRate * srcRepCount));

			if(schedulingConstraints.getActorAllocatedCluster(srcActor.getName()) != schedulingConstraints.getActorAllocatedCluster(dstActor.getName()))
			{				
				String dmaTaskName = SmtVariablePrefixes.dmaTokenTaskPrefix + chnnl.getName();
				
				if (((srcRepCount == 1) || (dstRepCount == 1)) && (initialTokens == 0))
				{
					int bufferSize = (srcRepCount * srcRate * tokenSize);
					IntExpr maxBuffId1 = maxBufferId (srcActor.getName(), dmaTaskName);
					IntExpr maxBuffId2 = maxBufferId (dmaTaskName, dstActor.getName());
					try
					{
						generateAssertion (ctx.mkEq (maxBuffId1, ctx.mkInt(bufferSize)));
						generateAssertion (ctx.mkEq (maxBuffId2, ctx.mkInt(bufferSize)));
					}
					catch (Z3Exception e) { e.printStackTrace(); }
				}
				else
				{

					assertBufferLinearNonPipelinedWithDma (chnnl.getName(), srcActor.getName (), dstActor.getName (),
							chnnl.getInitialTokens (), chnnl.getTokenSize(), srcRepCount, dstRepCount, 
							srcRate, dstRate);
	
					assertMaxBufferLinear (srcActor.getName (), dmaTaskName,  srcRepCount, chnnl.getInitialTokens (), chnnl.getTokenSize());
					assertMaxBufferLinear (dmaTaskName, dstActor.getName (), srcRepCount, chnnl.getInitialTokens (), chnnl.getTokenSize());
	
					assertBufferBoundsLinear (srcActor.getName (), dmaTaskName, srcRepCount, bufferMinBound, bufferMaxBound);
					assertBufferBoundsLinear (dmaTaskName, dstActor.getName (), srcRepCount, bufferMinBound, bufferMaxBound);
	
					// We force the FIFO Buffers to be equal right now. 
					// This happens because of FIFO design, Petro argues that it hsould not be the same.
					IntExpr maxBuffId1 = maxBufferId (srcActor.getName(), dmaTaskName);
					IntExpr maxBuffId2 = maxBufferId (dmaTaskName, dstActor.getName());
					try
					{
						generateAssertion (ctx.mkEq (maxBuffId1, maxBuffId2));
						
						// We don't support the wrap-around in the DMA.
						generateAssertion(ctx.mkEq(ctx.mkMod(maxBuffId1, ctx.mkInt(srcRate*tokenSize)), ctx.mkInt(0)));
						generateAssertion(ctx.mkEq(ctx.mkMod(maxBuffId2, ctx.mkInt(srcRate*tokenSize)), ctx.mkInt(0)));
						
						if(srcRate != dstRate)
						{
							generateAssertion(ctx.mkEq(ctx.mkMod(maxBuffId1, ctx.mkInt(dstRate*tokenSize)), ctx.mkInt(0)));
							generateAssertion(ctx.mkEq(ctx.mkMod(maxBuffId2, ctx.mkInt(dstRate*tokenSize)), ctx.mkInt(0)));
						}						
					}
					catch (Z3Exception e) { e.printStackTrace(); }
				}
			}
			else
			{
				if (((srcRepCount == 1) || (dstRepCount == 1)) && (initialTokens == 0))
				{
					// Since this is a fork or join edge, we have a fixed buffer size.
					IntExpr maxBuffId = maxBufferId (srcActor.getName(), dstActor.getName());
					try
					{
						generateAssertion (ctx.mkEq (maxBuffId, ctx.mkInt(srcRepCount * srcRate * tokenSize)));
					}
					catch (Z3Exception e) { e.printStackTrace(); }					
				}
				else
				{
				
					assertBufferLinearNonPipelined (srcActor.getName (), dstActor.getName (),
							chnnl.getInitialTokens (), chnnl.getTokenSize(), srcRepCount, dstRepCount,  srcRate, dstRate);
	
					assertMaxBufferLinear (srcActor.getName (), dstActor.getName (),  srcRepCount, 
									chnnl.getInitialTokens (), chnnl.getTokenSize());			
					assertBufferBoundsLinear (srcActor.getName (), dstActor.getName (), 
									srcRepCount, bufferMinBound, bufferMaxBound);
				}
			}			
		}

		if(useMaxBuffer == false)
			assertTotalBuffer ();
		else
		{
			assertMaxClusterBuffer();
			assertMaxBuffer ();
		}
	}

	/**
	 * Generate definition for processor symmetry variables.
	 */
	private void generateProcessorSymmetryDefinitions ()
	{
		Iterator<Actor> actorIter = partitionAwareGraph.getActors ();
		while (actorIter.hasNext ())
		{				
			Actor actr = actorIter.next ();

			int repCount = partitionGraphSolutions.getSolution (actr).returnNumber ();
			for (int i=0;i<repCount;i++)
			{
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.maxCpuPrefix + actr.getName () + Integer.toString (i), "Int");
				symmetryDecl.put (SmtVariablePrefixes.maxCpuPrefix+ actr.getName () +Integer.toString (i), id);
			}
		}
	}
	
	/**
	 * Generate constraints for processor symmetry for the processors in the cluster.
	 * 
	 * @param actrAllocated actors allocated to a cluster
	 * @param startProcIndex starting index of the processor
	 */
	private void procSymInCluster (HashSet<String>actrAllocated, int startProcIndex)
	{
		int actorCount = 0;
		IntExpr prevMaxCpuId=null, prevCpuId=null, currCpuId=null, currMaxCpuId=null;

		for (String actrName : actrAllocated)
		{
			Actor actr = partitionAwareGraph.getActor(actrName);
			int repCount = partitionGraphSolutions.getSolution (partitionAwareGraph.getActor(actr.getName())).returnNumber ();

			for (int i=0;i<repCount;i++)
			{
				currMaxCpuId = maxCpuId (actr.getName (), i);
				currCpuId 	 = cpuId (actr.getName (), i);

				try
				{
					if (actorCount == 0)
					{
						// This is a first actor instance.									
						generateAssertion (ctx.mkEq (currCpuId, ctx.mkInt (startProcIndex)));				
					}
					else if (actorCount == 1)
					{					
						// This is second actor instance.
						generateAssertion (ctx.mkLe (currCpuId, ctx.mkAdd (currMaxCpuId, ctx.mkInt (1))));					
						generateAssertion (ctx.mkEq (currMaxCpuId, prevCpuId));					
					}
					else
					{
						BoolExpr orArgs[] = new BoolExpr[2];
						BoolExpr andArgs[] = new BoolExpr[2];					

						orArgs[0]  = ctx.mkEq (currMaxCpuId, prevCpuId);
						orArgs[1]  = ctx.mkEq (currMaxCpuId, prevMaxCpuId);
						andArgs[0] = ctx.mkGe (currMaxCpuId, prevCpuId);
						andArgs[1] = ctx.mkGe (currMaxCpuId, prevMaxCpuId);

						generateAssertion (ctx.mkLe (currCpuId, ctx.mkAdd (currMaxCpuId, ctx.mkInt (1))));					
						generateAssertion (ctx.mkOr (orArgs));
						generateAssertion (ctx.mkAnd (andArgs));					
					}
				} catch (Z3Exception e) { e.printStackTrace (); }

				prevMaxCpuId = currMaxCpuId;
				prevCpuId = currCpuId;

				actorCount++;
			}
		}
	}
	
	/**
	 * Generate constraints for processor symmetry for the DMA Engines in the cluster.
	 * 
	 * @param dmaActorList list of DMA actors allocated to the cluster
	 * @param startDmaIndex starting index of DMA engine
	 */
	private void dmaSymInCluster (List<String> dmaActorList, int startDmaIndex)
	{
		int actorCount = 0;
		IntExpr prevMaxCpuId=null, prevCpuId=null, currCpuId=null, currMaxCpuId=null;

		for (String actrName : dmaActorList)
		{
			Actor actr = partitionAwareGraph.getActor(actrName);
			int repCount = partitionGraphSolutions.getSolution (actr).returnNumber ();

			// now process communication actors.
			for (int i=0;i<repCount;i++)
			{
				currMaxCpuId = maxCpuId (actr.getName (), i);
				currCpuId 	 = cpuId (actr.getName (), i);

				try
				{
					if (actorCount == 0)
					{
						// This is a first actor instance.									
						generateAssertion (ctx.mkEq (currCpuId, ctx.mkInt (startDmaIndex)));
					}
					else if (actorCount == 1)
					{					
						// This is second actor instance.
						generateAssertion (ctx.mkLe (currCpuId, ctx.mkAdd (currMaxCpuId, ctx.mkInt (1))));
						generateAssertion (ctx.mkEq (currMaxCpuId, prevCpuId));					
					}
					else
					{
						BoolExpr orArgs[] = new BoolExpr[2];
						BoolExpr andArgs[] = new BoolExpr[2];					

						orArgs[0]  = ctx.mkEq (currMaxCpuId, prevCpuId);
						orArgs[1]  = ctx.mkEq (currMaxCpuId, prevMaxCpuId);
						andArgs[0] = ctx.mkGe (currMaxCpuId, prevCpuId);
						andArgs[1] = ctx.mkGe (currMaxCpuId, prevMaxCpuId);

						generateAssertion (ctx.mkLe (currCpuId, ctx.mkAdd (currMaxCpuId, ctx.mkInt (1))));					
						generateAssertion (ctx.mkOr (orArgs));
						generateAssertion (ctx.mkAnd (andArgs));					
					}
				} catch (Z3Exception e) { e.printStackTrace (); }

				prevMaxCpuId = currMaxCpuId;
				prevCpuId = currCpuId;
				actorCount++;
			}
		}
	}

	/**
	 * Generate all the processor symmetry constraints
	 */
	private void processorSymmetryConstraints ()
	{		
		for(int clIndex=0;clIndex<platform.getNumClusters();clIndex++)
		{
			Cluster cluster = platform.getCluster(clIndex);		
			HashSet<String>actrAllocated = schedulingConstraints.getActorsAllocatedToCluster (cluster);
			
			if(actrAllocated.size() == 0)
				continue;
			
			// Processor Symmetry
			procSymInCluster (actrAllocated, platform.getProcIndex(cluster.getProcessor(0)));
			
			List<String> dmaAllocation = schedulingConstraints.getActorsAllocatedToDmaOfCluster(cluster);
			
			if(dmaAllocation.size() == 0)
				continue;
			
			// DMA Symmetry.
			dmaSymInCluster (dmaAllocation, platform.getDmaEngineIndex(cluster.getDmaEngine(0)));			
		}
	}

	/**
	 * Generate special precedence constraints for start times of DMA actors.
	 * 
	 *  Note: This is a bug-fix for the deadlock situation we saw.
	 * 	   In the Solver, we should have this constraint as
	 *     S DMA Port 0 <= S DMA Port 1 ... <= S DMA Port (n-1)
	 *     This is the sequence in which the DMA starts in our framework.
	 *     In order to avoid a deadlock, due to DMA Schedule, we assert this constraint.
	 *     
	 *     The example of this deadlock is that
	 *     Actor Instance 0 and Actor Instance 1 have DMA scheduled on same engine.
	 *     instance 1 waits for buffer space from instance 0, hence needs the DMA of instance 0 to finish.
	 *     In DMA schedule, dma of instance 1 is scheduled before dma of instance 0.
	 */
	private void clusterDmaStartTimePrecedences()
	{
		int dmaSetupTime = platform.getDmaSetupTime();
		
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{				
			Actor actr = actorIter.next ();
			Actor partitionActor = partitionAwareGraph.getActor(actr.getName());

			List<Channel> sortedDmaChannels = getSortedDmaPortList (partitionActor);
			int repCount = partitionGraphSolutions.getSolution (partitionActor).returnNumber ();
						
			if (sortedDmaChannels.size() > 1)
			{			
				for (int i=1;i<sortedDmaChannels.size();i++)
				{					
					for(int j=0;j<repCount;j++)
					{
						IntExpr actr1 = xId(sortedDmaChannels.get(i).getOpposite(partitionActor).getName(), j);
						IntExpr actr2 = xId(sortedDmaChannels.get(i-1).getOpposite(partitionActor).getName(), j);
						
						try
						{
							if (dmaSetupTime > 0)
								generateAssertion(ctx.mkLe(ctx.mkAdd(actr2, ctx.mkInt(dmaSetupTime)), actr1));
							else
								generateAssertion(ctx.mkLe(actr2, actr1));
						}
						catch (Z3Exception e) { e.printStackTrace(); }
					}
				}
			}
		}
	}

	/**
	 * Generate all the non-pipelined scheduling constraints
	 */
	public void assertNonPipelineConstraints ()
	{
		// First define all the variables.
		latencyDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.latencyPrefix, "Int");
		if (bufferAnalysis == true)
		{
			if(useMaxBuffer == false)
				totalBufDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.totalBufferPrefix, "Int");
			else
				maxBufDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.maxBufferPrefix, "Int");				
		}

		generateActorTimeDefinitions();

		generateCpuDefinitions();

		if (processorSymmetry == true)
			generateProcessorSymmetryDefinitions ();

		if (bufferAnalysis == true)
			generateBufferAnalysisDefinitions();

		assertActorDuration();
		
		assertEndTime();
		
		assertStartTimeBounds();
		
		zeroStartTimeActor();

		assertTaskCpuBounds ();
		
		assertDmaTaskProcBounds();

		// Mutual Exclusion Constraints
		assertMutualExclusion ();

		actorPrecedences();

		if (graphSymmetry == true)
			graphSymmetryLexicographic ();

		if (processorSymmetry == true)
			processorSymmetryConstraints ();

		if (bufferAnalysis == true)
			assertBufferCalculation ();

		minLatencyBound();

		clusterDmaStartTimePrecedences();

		generateLatencyCalculation();
	}

	/**
	 * Convert a SMT model to Gantt chart.
	 * 
	 * @param model model obtained from the SMT solver
	 * @param outputFileName output file name
	 */
	public void modelToGantt(Map<String, String> model, String outputFileName)
	{
		// Generate the Gantt Chart.
		GanttChart ganttChart = new GanttChart ();
		ganttChart.addNamesToActorInGraph = true;
		ganttChart.addLegendInGraph = false;

		// I will first form a processor map to processor index.
		boolean procUsed[] = new boolean [platform.getNumProcessors()];
		int newProcIndex[] = new int [platform.getNumProcessors()];

		boolean dmaUsed[] = new boolean [platform.getNumDmaEngines()];
		int newDmaIndex[] = new int [platform.getNumDmaEngines()];

		for(int i=0;i<platform.getNumProcessors();i++)
		{
			procUsed[i] = false;
			newProcIndex[i] = -1;
		}

		for(int i=0;i<platform.getNumDmaEngines();i++)
		{
			dmaUsed[i] = false;
			newDmaIndex[i] = -1;
		}

		Iterator<Actor> actrIter = partitionAwareGraph.getActors();
		while(actrIter.hasNext())
		{
			Actor actr = actrIter.next();
			for(int instanceId=0;instanceId<partitionGraphSolutions.getSolution(actr).returnNumber();instanceId++)
			{
				int procIndex = Integer.parseInt(model.get(SmtVariablePrefixes.cpuPrefix + actr.getName() + "_" + Integer.toString (instanceId)));
				if(actr.getActorType() == ActorType.DATAFLOW)
					procUsed[procIndex] = true;
				else if(actr.getActorType() == ActorType.COMMUNICATION)					
					dmaUsed[procIndex] = true;
			}			
		}

		int procCount = 0;
		int dmaCount = 0;
		int currentIndex = 0;
		for(int i=0;i<platform.getNumClusters();i++)
		{
			Cluster cluster = platform.getCluster(i);
			for(int j=0;j<cluster.getNumProcInCluster();j++)
			{
				if (procUsed[procCount] == true)
					newProcIndex[procCount] = currentIndex++;				
				procCount++;				
			}

			for(int j=0;j<cluster.getNumDmaInCluster();j++)				
			{
				if (dmaUsed[dmaCount] == true)
					newDmaIndex[dmaCount] = currentIndex++;
				dmaCount++;
			}
		}		

		// First All the data flow actors.
		int actorColor = 0;
		actrIter = partitionAwareGraph.getActors();
		while(actrIter.hasNext())
		{
			Actor actr = actrIter.next();

			for(int instanceId=0;instanceId<partitionGraphSolutions.getSolution(actr).returnNumber();instanceId++)
			{
				long startTime = Integer.parseInt(model.get(SmtVariablePrefixes.startTimePrefix + actr.getName() + "_" + Integer.toString (instanceId)));
				long endTime = actr.getExecTime() + startTime;
				int procIndex = -1;
				String procName;
				boolean printNameInGraph=true;

				if(actr.getActorType() == ActorType.DATAFLOW)
				{
					procIndex = Integer.parseInt(model.get(SmtVariablePrefixes.cpuPrefix + actr.getName() + "_" + Integer.toString (instanceId)));
					Processor proc = platform.getProcessor(procIndex);
					procName = proc.getCluster().getName()+"--"+proc.getName();
					procIndex = newProcIndex[procIndex];
					if(graph.hasChannel(actr.getName()) == true)
						printNameInGraph = true;
					else
						printNameInGraph = true;
				}
				else
				{
					procIndex = Integer.parseInt(model.get(SmtVariablePrefixes.cpuPrefix + actr.getName() + "_" + Integer.toString (instanceId)));
					DmaEngine dma = platform.getDmaEngine(procIndex);
					procName = dma.getCluster().getName() + "--" + dma.getName();
					procIndex = newDmaIndex[procIndex];
					printNameInGraph = false;
				}

				if(procIndex == -1)
					throw new RuntimeException("Some error happened in calculation of new proc index.");

				Record record = ganttChart.new Record (procName, procIndex, startTime, endTime, 
						actr.getName() + "_" + Integer.toString(instanceId), actorColor);
				record.printNameInGraph = printNameInGraph;
				ganttChart.addRecord(record);
			}
			actorColor++;
		}

		ganttChart.plotChart(outputFileName, -1);		
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.BufferConstraints#getTotalBufferSize(java.util.Map)
	 */
	@Override
	public int getTotalBufferSize(Map<String, String> model)
	{
		if(useMaxBuffer == false)
			return Integer.parseInt (model.get (SmtVariablePrefixes.totalBufferPrefix));
		else
		{
			int maxBufferSize = Integer.MIN_VALUE;
			HashMap<Cluster, HashSet<String>>mapping = schedulingConstraints.getActorsMappedToCluster();
			// int numClusters = mapping.keySet().size();
			// BoolExpr orArgs[] = new BoolExpr[numClusters];
			// BoolExpr andArgs[] = new BoolExpr[numClusters];
		
			// int count = 0;
			for(Cluster cluster : mapping.keySet())
			{
				int bufferSize = Integer.parseInt(model.get(SmtVariablePrefixes.clusterBufferPrefix + cluster.getName()));
				if(bufferSize > maxBufferSize)
					maxBufferSize = bufferSize;
			}
			return maxBufferSize;
		}			
//		else
//			return Integer.parseInt (model.get (SolverPrefixs.maxBufferPrefix));
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.BufferConstraints#generateBufferConstraint(int)
	 */
	@Override
	public void generateBufferConstraint(int bufferConstraint)
	{
		newSatQuery = true;
		satQueryModel = null;
		try
		{
			generateAssertion (ctx.mkEq (getBufDeclId(), ctx.mkInt (bufferConstraint)));
		} catch (Z3Exception e) { e.printStackTrace (); }		
	}
	
	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.LatencyConstraints#getLatency(java.util.Map)
	 */
	@Override
	public int getLatency(Map<String, String> model)
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.latencyPrefix));
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.LatencyConstraints#generateLatencyConstraint(int)
	 */
	@Override
	public void generateLatencyConstraint(int latency)
	{
		newSatQuery = true;
		satQueryModel = null;
		try
		{
			generateAssertion (ctx.mkLe (latencyDecl, ctx.mkInt (latency)));
		} catch (Z3Exception e) { e.printStackTrace (); }		
	}
	
	/* (non-Javadoc)
	 * @see solver.Z3Solver#resetSolver()
	 */
	@Override
	public void resetSolver()
	{
		super.resetSolver();
		latencyDecl = null;	
		totalBufDecl = null;
		startTimeDecl.clear();
		endTimeDecl.clear();
		durationDecl.clear();
		cpuDecl.clear();
		bufferDecl.clear();
		symmetryDecl.clear();
		System.gc();
	}
	
	/* (non-Javadoc)
	 * @see solver.Z3Solver#getModel()
	 */
	@Override
	public Map<String, String> getModel()
	{		
		if(newSatQuery == true)
		{
			newSatQuery = false;
			HashMap<String,String> model = new HashMap<String,String>(super.getModel());
			
			// Only One time initialization.
			if(optimizeSchedule == null)
				optimizeSchedule = new OptimizeSchedule();
			
			HashMap<String,String> nonLazyModel = new HashMap<String,String>(optimizeSchedule.generateNonLazySchedule(model));			
			HashMap<String,String> procOptimalModel = new HashMap<String,String>(optimizeSchedule.generateProcOptimalSched(model, nonLazyModel));
			
			model.put(SmtVariablePrefixes.latencyPrefix, nonLazyModel.get(SmtVariablePrefixes.latencyPrefix));
			
			for(String str : procOptimalModel.keySet())
				model.put(str, procOptimalModel.get(str));
			
			satQueryModel = model;
			return model;
		}
		else
			return satQueryModel;
	}

	/**
	 * Schedule optimizer to improve latency and processor usage.
	 * We use another instance of ClusterMutExclNonPipelined class in order to generate only 
	 * a few constraints in addition to new non-lazy and processor optimizing
	 * constraints to solve the problem.
	 * 
	 * @author Pranav Tendulkar
	 */
	private class OptimizeSchedule
	{
		/**
		 * tasks mapped to the same processors.
		 */
		private HashMap<Integer, List<String>> dataFlowCpuMap = new HashMap<Integer, List<String>>();
		/**
		 * Communication tasks mapped to the same DMA engines
		 */
		private HashMap<Integer, List<String>> commCpuMap = new HashMap<Integer, List<String>>();
		/**
		 * Latency and Processor usage optimizer
		 */
		ClusterMutExclNonPipelined optiSolver = null;
		/**
		 * Verify if the latency was really reduced or not with
		 * extra constraints
		 */
		boolean verifyLatency = false;
		
		/**
		 * Build a new schedule optimizer object
		 */
		public OptimizeSchedule()
		{
			optiSolver = new ClusterMutExclNonPipelined(graph, hsdf, solutions, 
				partitionAwareGraph, partitionAwareHsdf, partitionGraphSolutions,
				platform, outputDirectory, schedulingConstraints);
		}
		
		/**
		 * Generate constraints for improving the processor usage and solve it.
		 * 
		 * @param procAllocationModel model containing processor allocation information for all the tasks
		 * @param startTimeModel model containing start times information for all the tasks
		 * @return model with improved processor allocation
		 */
		public Map<String,String> generateProcOptimalSched(HashMap<String, String> procAllocationModel, HashMap<String, String> startTimeModel)
		{
			initLists (procAllocationModel, startTimeModel);
			
			// First define all the variables.
			optiSolver.resetSolver();
			optiSolver.latencyDecl = (IntExpr) optiSolver.addVariableDeclaration (SmtVariablePrefixes.latencyPrefix, "Int");
			optiSolver.generateActorTimeDefinitions();			
			optiSolver.assertActorDuration();			
			optiSolver.assertEndTime();
			optiSolver.generateCpuDefinitions();
			optiSolver.generateProcessorSymmetryDefinitions();			
			optiSolver.assertTaskCpuBounds ();			
			optiSolver.assertDmaTaskProcBounds();			
			optiSolver.processorSymmetryConstraints();
			optiSolver.assertMutualExclusion();
			
			setActorTimes (startTimeModel);
			
			for(int clIndex=0;clIndex<platform.getNumClusters();clIndex++)
			{
				Cluster cluster = platform.getCluster(clIndex);		
				HashSet<String>actrAllocated = schedulingConstraints.getActorsAllocatedToCluster (cluster);
				HashSet<String>dmaActrAllocated = schedulingConstraints.getActorsMappedToDmaOfCluster(cluster);
				
				if(actrAllocated.size() == 0)
					continue;
				
				// Processor Symmetry
				strictProcSymConstraints(actrAllocated, platform.getProcIndex(cluster.getProcessor(0)), cluster, startTimeModel);
				// Dma Symmetry
				strictProcSymConstraints(dmaActrAllocated, platform.getDmaEngineIndex(cluster.getDmaEngine(0)), cluster, startTimeModel);
			}
			
			// Set the latency.
			try
			{
				int latency = Integer.parseInt(startTimeModel.get(SmtVariablePrefixes.latencyPrefix));
				optiSolver.generateAssertion(optiSolver.ctx.mkEq(optiSolver.getLatencyDeclId(), optiSolver.ctx.mkInt(latency)));
			} catch (Z3Exception e) { e.printStackTrace(); }
			
			optiSolver.generateSatCode(outputDirectory + "procOptimal.z3");
			
			SatResult result = optiSolver.checkSat(1000);
			if(result != SatResult.SAT)
				throw new RuntimeException("We were expecting a SAT result. something went wrong. result : " + result);
			
			Map<String,String>model = optiSolver.getModel(optiSolver.z3Solver);
			return model;
		}
		
		/**
		 * Generate a non-lazy schedule from the loose schedule obtained
		 * 
		 * @param model lazy schedule obtained from originally solving the problem
		 * @return model of non-lazy schedule
		 */
		public Map<String, String> generateNonLazySchedule (HashMap<String,String> model)
		{
			Graph bufferConstraintSdf = getBufferConstraintSdf (model);
			
			TransformSDFtoHSDF toHSDF = new TransformSDFtoHSDF ();
			Graph bufferHsdfGraph = toHSDF.convertSDFtoHSDFWithUniqueChannels (bufferConstraintSdf);
			
			// DotGraph dotG = new DotGraph ();
			// dotG.generateDotFromGraph (bufferConstraintSdf, outputDirectory + "bufferAware"+Integer.toString(count)+".dot");
			// dotG.generateDotFromGraph (bufferHsdfGraph, outputDirectory + "bufferAwareHsdf"+Integer.toString(count)+".dot");
			
			initLists (model, model);
			
			// First define all the variables.
			optiSolver.resetSolver();
			if(verifyLatency == true)
				optiSolver.latencyDecl = (IntExpr) optiSolver.addVariableDeclaration (SmtVariablePrefixes.latencyPrefix, "Int");
			optiSolver.generateActorTimeDefinitions();			
			optiSolver.assertActorDuration();			
			optiSolver.assertEndTime();
			// optiSolver.assertStartTimeBounds();
			optiSolver.clusterDmaStartTimePrecedences();
			
			if(verifyLatency == true)
			{
				optiSolver.generateLatencyCalculation();
				
				int latency = Integer.parseInt(model.get(SmtVariablePrefixes.latencyPrefix));
				try
				{
					optiSolver.generateAssertion(optiSolver.ctx.mkLe(optiSolver.getLatencyDeclId(), optiSolver.ctx.mkInt(latency)));
				} catch (Z3Exception e) { e.printStackTrace(); }
			}
			else
			{
				for(String str : model.keySet())
				{
					if(str.startsWith(SmtVariablePrefixes.startTimePrefix))
					{
						int startTime = Integer.parseInt(model.get(str));
						if(startTime == 0)
						{
							try
							{
								Expr startTimeId = optiSolver.xId(str.substring(SmtVariablePrefixes.startTimePrefix.length()));
								optiSolver.generateAssertion(optiSolver.ctx.mkEq(startTimeId, optiSolver.ctx.mkInt(0)));
							}
							catch (Z3Exception e) { e.printStackTrace(); }
						}
					}
				}
			}
			
			nonLazyConstraints (model, bufferHsdfGraph);
			
			optiSolver.generateSatCode(outputDirectory + "nonlazy.z3");
			
			SatResult result = optiSolver.checkSat(1000);
			if(result != SatResult.SAT)
				throw new RuntimeException("We were expecting a SAT result. something went wrong. result : " + result);
			
			Map<String,String> solverModel = optiSolver.getModel(optiSolver.z3Solver);
			
			if(verifyLatency == false)
			{
				int latency = Integer.MIN_VALUE;
				for(String str : solverModel.keySet())
				{				
					if((str.startsWith(SmtVariablePrefixes.endDotTimePrefix)) || 
							(str.startsWith(SmtVariablePrefixes.endTimePrefix)))
					{
						int endTime = Integer.parseInt(solverModel.get(str));
						if(endTime > latency)
							latency = endTime;
					}
				}
				
				if(latency == Integer.MIN_VALUE)
					throw new RuntimeException("We could not calculate the latency of the non-lazy schedule.");
				
				solverModel.put(SmtVariablePrefixes.latencyPrefix, Integer.toString(latency));
			}
			
			
			
			
			return solverModel;
		}
		
		/**
		 * Get actors which can potentially execute in parallel with this actor.
		 * 
		 * @param actor name of the actor
		 * @param clusterHsdfActors hsdf actors allocated to the cluster
		 * @param model model containing start and end times of the tasks
		 * @return list of overlapping actors
		 */
		private List<String> getOverlappingActors (String actor, List<String> clusterHsdfActors, HashMap<String, String> model)
		{
			List<String> result = new ArrayList<String>();
			
			int xActor = Integer.parseInt(model.get(SmtVariablePrefixes.startTimePrefix + actor));
			int yActor;
			
			if(model.containsKey(SmtVariablePrefixes.endDotTimePrefix + actor))
				yActor = Integer.parseInt(model.get(SmtVariablePrefixes.endDotTimePrefix + actor));
			else
				yActor = Integer.parseInt(model.get(SmtVariablePrefixes.endTimePrefix + actor));
			
			for(String otherActor : clusterHsdfActors)
			{
				if(actor.equals(otherActor)) 
					continue;
				
				int xOtherActor = Integer.parseInt(model.get(SmtVariablePrefixes.startTimePrefix + otherActor));				
				int yOtherActor;
				
				if(model.containsKey(SmtVariablePrefixes.endDotTimePrefix + otherActor))
					yOtherActor = Integer.parseInt(model.get(SmtVariablePrefixes.endDotTimePrefix + otherActor));
				else
					yOtherActor = Integer.parseInt(model.get(SmtVariablePrefixes.endTimePrefix + otherActor));
				
				// Add to the result list, only if the overlap exists.
				if(xActor < yOtherActor && xOtherActor < yActor)
					result.add(otherActor);
			}
			
			return result;
		}
		
		/**
		 * Generate strict processor constraint to allocate a new processor only 
		 * if the processor with lower index and no empty time slot.
		 * 
		 * TODO : we need to improve these constraints as they don't optimize the
		 * processors perfectly. Maybe replace it with left-edge algorithm
		 *  
		 * @param actrAllocated list of actor allocated to this cluster
		 * @param startProcIndex starting index of the processor
		 * @param cluster cluster where to perform allocation
		 * @param model model containing start time value
		 */
		private void strictProcSymConstraints(HashSet<String> actrAllocated, int startProcIndex, Cluster cluster, HashMap<String, String> model)
		{
			List<String> clusterHsdfActors = new ArrayList<String>();
			
			for(String actrName : actrAllocated)
			{
				// All the HSDF Actors.
				int repCount = partitionGraphSolutions.getSolution(partitionAwareGraph.getActor(actrName)).returnNumber();
				for(int i=0;i<repCount;i++)
					clusterHsdfActors.add(actrName + "_" + Integer.toString(i));
			}
			
			boolean []constraintAsserted = new boolean[clusterHsdfActors.size()];
			for(int i=0;i<clusterHsdfActors.size();i++)
				constraintAsserted[i] = false;
			
			try
			{
				for(int i=0;i<clusterHsdfActors.size();i++)
				{
					String actorA = clusterHsdfActors.get(i);
					List<String> overlappingActors = getOverlappingActors(actorA, clusterHsdfActors, model);
					
					if (overlappingActors.size() == 0)
					{
						optiSolver.generateAssertion(optiSolver.ctx.mkEq(optiSolver.cpuId(actorA), optiSolver.ctx.mkInt(startProcIndex)));
						constraintAsserted[i] = true;
					}
					else
					{
						IntExpr cpuId_A = optiSolver.cpuId(actorA);						
						BoolExpr orArgs[] = new BoolExpr[overlappingActors.size()];
						for(int j=0;j<overlappingActors.size();j++)
						{
							String actorB = overlappingActors.get(j);							
							IntExpr cpuId_B = optiSolver.cpuId(actorB);							
							
							orArgs[j] = optiSolver.ctx.mkEq(cpuId_B, optiSolver.ctx.mkSub(cpuId_A, optiSolver.ctx.mkInt(1)));
						}
						
						if(orArgs.length > 1)
							optiSolver.generateAssertion(optiSolver.ctx.mkImplies(optiSolver.ctx.mkGt(cpuId_A, optiSolver.ctx.mkInt(startProcIndex)), optiSolver.ctx.mkOr(orArgs)));
						else
							optiSolver.generateAssertion(optiSolver.ctx.mkImplies(optiSolver.ctx.mkGt(cpuId_A, optiSolver.ctx.mkInt(startProcIndex)), orArgs[0]));
						
						if (constraintAsserted[i] == false)
						{
							// One of these overlapping actors must be allocated on the first processor.
							
							BoolExpr[] minProcOrArgs = new BoolExpr[overlappingActors.size()+1];
							
							for(int j=0;j<overlappingActors.size();j++)
								minProcOrArgs[j] = optiSolver.ctx.mkEq(optiSolver.cpuId(overlappingActors.get(j)), optiSolver.ctx.mkInt(startProcIndex));
							minProcOrArgs[overlappingActors.size()] = optiSolver.ctx.mkEq(cpuId_A, optiSolver.ctx.mkInt(startProcIndex));
							
							optiSolver.generateAssertion(optiSolver.ctx.mkOr(minProcOrArgs));
							
							constraintAsserted[i] = true;
							for(int j=0;j<overlappingActors.size();j++)
							{
								for(int k=0;k<clusterHsdfActors.size();k++)
								{
									if(overlappingActors.get(j).equals(clusterHsdfActors.get(k)))
									{
										constraintAsserted[k] = true;
										break;
									}
								}
							}
						}
					}
				}
			} catch (Z3Exception e) { e.printStackTrace(); }			
		}
		
		/**
		 * Fix task start times to a given value in the model
		 * 
		 * @param model model containing task start times.
		 */
		private void setActorTimes (HashMap<String,String> model)
		{
			
			for(String str : optiSolver.startTimeDecl.keySet())
			{
				IntExpr id = optiSolver.startTimeDecl.get(str);
				int value = Integer.parseInt(model.get(str));
				try
				{
					optiSolver.generateAssertion(optiSolver.ctx.mkEq(id, optiSolver.ctx.mkInt(value)));
				} catch (Z3Exception e) { e.printStackTrace(); }
			}
		}
		
		public Actor getOutgoingPreviousDmaActor(Actor hsdfActr, Graph hsdfGraph)
		{			
			// First we have to get the SDF Actor.
			String sdfActorName = hsdfActr.getName().substring(0,hsdfActr.getName().indexOf("_"));
			int instanceId = Integer.parseInt(hsdfActr.getName().substring(hsdfActr.getName().indexOf("_")+1,hsdfActr.getName().length()));
			Actor sdfActor = partitionAwareGraph.getActor(sdfActorName);
			
			// This is the actor who starts the DMA.
			Actor parentActor = null;
			HashSet<Channel>incomingChannels = sdfActor.getChannels(Port.DIR.IN);
			for(Channel chnnl : incomingChannels)
			{
				Actor otherActor = chnnl.getOpposite(sdfActor);
				if(otherActor.getActorType() == ActorType.DATAFLOW)
				{
					if(parentActor != null)
						throw new RuntimeException("Are there two writers for One DMA?");
					parentActor = otherActor;
				}
			}
			
			if(parentActor == null)
				throw new RuntimeException("Is there no writers for this DMA?");
			
			List<Channel> outgoingDmaChannels = getSortedDmaPortList (parentActor);
			
			if(outgoingDmaChannels.get(0).getOpposite(parentActor).getName().equals(sdfActorName) == true)
				return null;
			else
			{
				for(int i=1;i<outgoingDmaChannels.size();i++)
				{
					if(outgoingDmaChannels.get(i).getOpposite(parentActor).getName().equals(sdfActorName) == true)
					{
						return hsdfGraph.getActor(outgoingDmaChannels.get(i-1).getOpposite(parentActor).getName()
									+ "_" + Integer.toString(instanceId));
					}
				}
				
				throw new RuntimeException("I don't find this actor, maybe the graph is broken?");
			}
		}
		
		/**
		 * Generate constraints to make a schedule non-lazy
		 * @param model lazy model obtained from the solver
		 * @param bufferConstraintHsdf HSDF graph containing the buffer sizes for all the channels
		 */
		private void nonLazyConstraints (HashMap<String,String> model, Graph bufferConstraintHsdf)
		{
			for(Actor actr : bufferConstraintHsdf.getActorList())
			{				
				HashSet<IntExpr> predecessorList = new HashSet<IntExpr>();
				String prevActorOnCpu = null;
				
				int cpuId = Integer.parseInt(model.get(SmtVariablePrefixes.cpuPrefix + actr.getName()));
				List<String> actrList = null;
				
				if (actr.getActorType() == ActorType.DATAFLOW)
					actrList = dataFlowCpuMap.get(cpuId);
				else
					actrList = commCpuMap.get(cpuId);
				
				if(actrList.get(0).equals(actr.getName()) == false)
				{
					// let us start this loop from 1, because if the actor is 
					// first to run on cpu, the it doesn't have any previous proc.
					for(int i=1;i<actrList.size();i++)
					{
						if(actrList.get(i).equals(actr.getName()))
						{
							prevActorOnCpu = actrList.get(i-1);
							if(optiSolver.yDotId(actrList.get(i-1)) != null)
								predecessorList.add(optiSolver.yDotId(actrList.get(i-1)));
							else
								predecessorList.add(optiSolver.yId(actrList.get(i-1)));
							break;
						}
					}
				}
				
				try
				{
					List<Channel> incomingChnnlList = new ArrayList<Channel>(actr.getChannels(Port.DIR.IN));						
					for(Channel chnnl : incomingChnnlList)
					{
						Actor predecessor = chnnl.getOpposite(actr);						
						int dstRate = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());
						
						// There is no actor precedence if we have sufficient initial tokens.
						if(chnnl.getInitialTokens() >= dstRate)
							continue;
			
						if((prevActorOnCpu == null) || ((prevActorOnCpu != null) && (predecessor.getName().equals(prevActorOnCpu)==false)))
							predecessorList.add(optiSolver.yId(predecessor.getName()));
					}
					
					if (actr.getActorType() == ActorType.COMMUNICATION)
					{
						// If we have a communication actor, then remember that we must have 
						// a lower bound for the start time as the dma actor from the previous port.
						Actor prevDmaActor = getOutgoingPreviousDmaActor (actr, bufferConstraintHsdf);
						if (prevDmaActor != null)
							predecessorList.add((IntExpr) optiSolver.ctx.mkAdd(optiSolver.xId(prevDmaActor.getName()), 
									optiSolver.ctx.mkInt(platform.getDmaSetupTime())));
					}
					
					// Assert the constraint now.
					if(predecessorList.size() == 0)
						optiSolver.generateAssertion(optiSolver.ctx.mkEq(optiSolver.xId(actr.getName()), optiSolver.ctx.mkInt(0)));	
					else
					{
						BoolExpr[] andArgs = new BoolExpr[predecessorList.size()];
						BoolExpr[] orArgs = new BoolExpr[predecessorList.size()];
						
						int count = 0;
						for(IntExpr expr : predecessorList)
						{
							andArgs[count] = optiSolver.ctx.mkGe(optiSolver.xId(actr.getName()), expr);
							orArgs[count++] = optiSolver.ctx.mkEq(optiSolver.xId(actr.getName()), expr);
						}
						
						if(orArgs.length == 1)
							optiSolver.generateAssertion(orArgs[0]);
						else
						{						
							optiSolver.generateAssertion(optiSolver.ctx.mkOr(orArgs));
							optiSolver.generateAssertion(optiSolver.ctx.mkAnd(andArgs));
						}
					}
				} catch (Exception e) { e.printStackTrace(); }
			}
		}
		
		/**
		 * Form the lists of tasks allocated to Processors and DMA engines.
		 * 
		 * @param procAllocationModel model containing processor allocation
		 * @param startTimeModel model containing start times of the tasks
		 */
		private void initLists (HashMap<String,String> procAllocationModel, HashMap<String,String> startTimeModel)
		{
			dataFlowCpuMap.clear();
			commCpuMap.clear();
			
			// First we seperate the dataflow and communication actors.						
			for(String str : cpuDecl.keySet())
			{
				String actorName = str.substring(SmtVariablePrefixes.cpuPrefix.length(), str.indexOf("_")); 
				int proc = Integer.parseInt(procAllocationModel.get(str));
				
				if(partitionAwareGraph.getActor(actorName).getActorType() == ActorType.DATAFLOW)
				{
					if(dataFlowCpuMap.containsKey(proc) == false)
						dataFlowCpuMap.put(proc, new ArrayList<String>());					
					dataFlowCpuMap.get(proc).add(str.substring(SmtVariablePrefixes.cpuPrefix.length()));
				}
				else
				{
					if(commCpuMap.containsKey(proc) == false)
						commCpuMap.put(proc, new ArrayList<String>());										
					commCpuMap.get(proc).add(str.substring(SmtVariablePrefixes.cpuPrefix.length()));
				}
			}
			
			// Let us sort the lists according to the start times.			
			HashMap<Integer, String> reverseMap = new HashMap<Integer, String>();
			
			// Lets treat first the dataflow actors.
			for(int proc : dataFlowCpuMap.keySet())
			{
				List<String> procList = dataFlowCpuMap.get(proc);				
				if(procList.size() > 1)
				{
					reverseMap.clear();
					int startTime[] = new int[procList.size()];
					
					for(int i=0;i<procList.size();i++)
					{
						startTime[i] = Integer.parseInt(startTimeModel.get(SmtVariablePrefixes.startTimePrefix + procList.get(i)));
						reverseMap.put(startTime[i], procList.get(i));
					}
					
					Arrays.sort(startTime);
					
					procList.clear();
					for(int i=0;i<startTime.length;i++)
						procList.add(reverseMap.get(startTime[i]));
				}
			}
			
			
			// Now the communication actors.
			for(int proc : commCpuMap.keySet())
			{
				List<String> procList = commCpuMap.get(proc);
				reverseMap.clear();
				if(procList.size() > 1)
				{
					int startTime[] = new int[procList.size()];
					
					for(int i=0;i<procList.size();i++)
					{
						startTime[i] = Integer.parseInt(startTimeModel.get(SmtVariablePrefixes.startTimePrefix + procList.get(i)));
						reverseMap.put(startTime[i], procList.get(i));
					}
					
					Arrays.sort(startTime);
					
					procList.clear();
					for(int i=0;i<startTime.length;i++)
						procList.add(reverseMap.get(startTime[i]));
				}
			}		
		}
		
		/**
		 * Generate a SDF graph from the model containing buffer size.
		 * 
		 * @param model model obtained from the SMT solver 
		 * @return Buffer aware SDF graph
		 */
		private Graph getBufferConstraintSdf (HashMap<String, String> model)
		{
			Graph bufferConstraintSdf = new Graph(partitionAwareGraph);
			
			for(Channel chnnl : graph.getChannelList())
			{
				Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
				Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
				int srcRate = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
				int dstRate = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());
				
				if(schedulingConstraints.getActorAllocatedCluster(srcActor.getName()) 
						== schedulingConstraints.getActorAllocatedCluster(dstActor.getName()))
				{
					int bufferSize = Integer.parseInt(model.get(SmtVariablePrefixes.maxBufferPrefix + srcActor.getName() + dstActor.getName())) / chnnl.getTokenSize();
					bufferConstraintSdf.insertNewChannelBetweenActors(bufferConstraintSdf.getActor(dstActor.getName()), 
									bufferConstraintSdf.getActor(srcActor.getName()), 
									chnnl.getTokenSize(), 
									bufferSize, dstRate, srcRate);
				}
				else
				{
					Actor dmaTokenTask = partitionAwareGraph.getActor(SmtVariablePrefixes.dmaTokenTaskPrefix + chnnl.getName());
					Actor dmaStatusTask = partitionAwareGraph.getActor(SmtVariablePrefixes.dmaStatusTaskPrefix + chnnl.getName()); 
					
					int bufferSize1 = Integer.parseInt(model.get(SmtVariablePrefixes.maxBufferPrefix + srcActor.getName () + dmaTokenTask.getName ())) / chnnl.getTokenSize();					
					int bufferSize2 = Integer.parseInt(model.get(SmtVariablePrefixes.maxBufferPrefix + dmaTokenTask.getName () + dstActor.getName ())) / chnnl.getTokenSize();
					
					bufferConstraintSdf.insertNewChannelBetweenActors(bufferConstraintSdf.getActor(chnnl.getName()), 
							bufferConstraintSdf.getActor(srcActor.getName()), 1,
							bufferSize1, srcRate, srcRate);
					
					bufferConstraintSdf.insertNewChannelBetweenActors(bufferConstraintSdf.getActor(dmaStatusTask.getName()), 
							bufferConstraintSdf.getActor(dmaTokenTask.getName()), 1,
							bufferSize2, dstRate, srcRate);					
				}
			}
			
			return bufferConstraintSdf;
		}
	}	
}
