package solver.distributedMemory.scheduling;

import designflow.DesignFlowSolution;
import designflow.DesignFlowSolution.*;
import exploration.interfaces.oneDim.LatencyConstraints;
import exploration.interfaces.oneDim.PeriodConstraints;
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

// Right now we are in the test phase and I don't want to kill the
// already existing working code for scheduling in pipelined and non-pipelined
// fashion. So what I do is first implement the scheduling seperately for the 
// cluster scenario and then see what is the damage. If it is not much, then 
// we will merge both the code.
public class ClusterMutExclPipelined extends Z3Solver 
	implements LatencyConstraints, PeriodConstraints
{
	private Graph graph;
	// private Graph hsdf;
	// private Solutions solutions;
	private Graph partitionAwareGraph;
	private Graph partitionAwareHsdf;
	private Solutions partitionGraphSolutions;
	private Platform platform;
	private SchedulingConstraints schedulingConstraints;
	private IntExpr latencyDecl;
	private IntExpr periodDecl;
	public boolean graphSymmetry = false;
	public boolean processorSymmetry = false;
	public boolean bufferAnalysis = false;
	public boolean useMaxBuffer = true;
	
	private Map<String, IntExpr> startTimeDecl;
	private Map<String, IntExpr> endTimeDecl;
	private Map<String, IntExpr> durationDecl;
	private Map<String, IntExpr> cpuDecl;
	private Map<String, IntExpr> bufferDecl;
	private Map<String, IntExpr> symmetryDecl;

	// Actor which have at least one actor connected in other cluster.
	private HashSet<Actor> multiClusterActors;

	public ClusterMutExclPipelined (Graph graph, Graph hsdf, Solutions solutions, 
			Graph partitionAwareGraph, Graph partitionAwareHsdf, Solutions partitionGraphSolutions,
			Platform platform, SchedulingConstraints schedulingConstraints)
	{
		this.graph = graph;
		//this.hsdf = hsdf;
		// this.solutions = solutions;
		this.partitionAwareGraph = partitionAwareGraph;
		this.schedulingConstraints = schedulingConstraints;
		this.platform = platform;
		this.partitionGraphSolutions = partitionGraphSolutions;
		this.partitionAwareHsdf = partitionAwareHsdf;

		startTimeDecl 	= new TreeMap<String, IntExpr>();
		endTimeDecl 	= new TreeMap<String, IntExpr>();		
		durationDecl 	= new TreeMap<String, IntExpr>();
		cpuDecl 		= new TreeMap<String, IntExpr>();
		symmetryDecl    = new TreeMap<String, IntExpr>();

		multiClusterActors = new HashSet<Actor>();
		calculateMulticlusterActorChannels();
	}

	private void calculateMulticlusterActorChannels()
	{
		Iterator<Actor> actrIter = partitionAwareGraph.getActors();
		while(actrIter.hasNext())
		{
			Actor actr = actrIter.next();
			if(actr.getActorType() == ActorType.DATAFLOW)
			{
				HashSet<Channel> chnnlSet = actr.getAllChannels();
				for(Channel chnnl : chnnlSet)
				{
					if(chnnl.getOpposite(actr).getActorType() == ActorType.COMMUNICATION)
					{
						multiClusterActors.add(actr);
						break;
					}
				}
			}
		}
	}	

	public IntExpr getPeriodDeclId () { return periodDecl; }
	public IntExpr getLatencyDeclId () { return latencyDecl; }	
	private IntExpr xId (String name, int index) 		 { return startTimeDecl.get (SmtVariablePrefixes.startTimePrefix + name + "_" + Integer.toString (index)); }
	//private IntExpr xId (String name) 		 { return startTimeDecl.get (SolverPrefixs.startTimePrefix + name); }
	private IntExpr yId (String name, int index) 		 { return endTimeDecl.get (SmtVariablePrefixes.endTimePrefix + name + "_" + Integer.toString (index)); }
	private IntExpr yId (String name) 		 { return endTimeDecl.get (SmtVariablePrefixes.endTimePrefix + name); }
	private IntExpr yDotId (String name) 		 { return endTimeDecl.get (SmtVariablePrefixes.endDotTimePrefix + name); }
	private IntExpr yDotId (String name, int index) 		 { return endTimeDecl.get (SmtVariablePrefixes.endDotTimePrefix + name + "_" + Integer.toString (index)); }
	private IntExpr durationId (String name)			 { return durationDecl.get (SmtVariablePrefixes.durationPrefix+ name); }
	//private IntExpr cpuId (String name) 	 { return cpuDecl.get (SolverPrefixs.cpuPrefix + name); }
	private IntExpr cpuId (String name, int index) 	 { return cpuDecl.get (SmtVariablePrefixes.cpuPrefix + name + "_" + Integer.toString (index)); }	
	private IntExpr maxCpuId (String name, int index) 	 { return symmetryDecl.get (SmtVariablePrefixes.maxCpuPrefix+ name + Integer.toString (index)); }

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

	private void generateActorTimeDefinitions () 
	{		
		// Define Start Times
		defineStartTimes ();

		// Define Actor Durations.
		defineActorDuration ();

		// Define End Times		
		defineEndTimes ();				
	}
	
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

	private Actor getpartitionAwareGraphActor (Actor actr)
	{
		return partitionAwareGraph.getActor(actr.getName());
	}
	
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
	
	private Channel getLastDmaPort (Actor actr)
	{
		List<Channel> result = getSortedDmaPortList(actr);
		if(result.size() > 0)
			return result.get(result.size()-1);
		else
			return null;
	}
	
	private boolean hasActorDmaOutput (Actor actr)
	{
		for(Channel chnnl : actr.getChannels(Port.DIR.OUT))
			if(chnnl.getOpposite(actr).getActorType() == ActorType.COMMUNICATION)
				return true;
		return false;
	}

	private void assertDataFlowMutualExclusion (List<Actor> actrList)
	{		
		for (int i=0;i< actrList.size ();i++)
		{
			Actor actr = getpartitionAwareGraphActor(actrList.get (i));

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
					Actor otherActor = getpartitionAwareGraphActor(actrList.get (k));
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

	private void assertCommunicationMutualExclusion (List<Actor> actrList)
	{		
		for (int i=0;i< actrList.size ();i++)
		{
			Actor actr = getpartitionAwareGraphActor(actrList.get (i));			

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
					Actor otherActor = getpartitionAwareGraphActor(actrList.get (k));

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

	private void assertMutualExclusion ()
	{
		// Mutual Exclusion for Dataflow actors
		HashMap<Cluster, HashSet<String>> actorToClusterMap = schedulingConstraints.getActorsMappedToCluster ();		
		for(Cluster cluster : actorToClusterMap.keySet())
		{
			List<String> actrNameList = new ArrayList<String>(actorToClusterMap.get(cluster));
			List<Actor> actrList = new ArrayList<Actor>();
			for(String actrName : actrNameList)
				actrList.add(partitionAwareGraph.getActor(actrName));
			assertDataFlowMutualExclusion (actrList);			
		}

		// Mutual Exclusion for Communication Actors.	
		actorToClusterMap = schedulingConstraints.getActorsMappedToDmaOfCluster ();		
		for(Cluster cluster : actorToClusterMap.keySet())
		{
			List<String> actrNameList = new ArrayList<String>(actorToClusterMap.get(cluster));
			List<Actor> actrList = new ArrayList<Actor>();
			for(String actrName : actrNameList)
				actrList.add(partitionAwareGraph.getActor(actrName));
			assertCommunicationMutualExclusion (actrList);			
		}
	}

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
								partitionGraphSolutions.getSolution(
										partitionAwareGraph.getActor(actr.getName())).returnNumber();
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

	public Schedule modelToSchedule (Map<String, String> model, DesignFlowSolution designSolution)
	{
		Schedule schedule = designSolution.new Schedule();

		// Add Start times
		Iterator<Actor> actrIter = partitionAwareGraph.getActors();
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
				for(int i=0;i<partitionGraphSolutions.getSolution(actr).returnNumber();i++)
				{
					int cpuIndex = Integer.parseInt(model.get(SmtVariablePrefixes.cpuPrefix + actr.getName() + "_" + Integer.toString (i)));
					int startTime = Integer.parseInt(model.get(SmtVariablePrefixes.startTimePrefix + actr.getName() + "_" + Integer.toString (i)));
					schedule.addActor (actr.getName(), i, platform.getProcessor(cpuIndex), startTime);
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
	
	private void procSymInCluster (HashSet<String>actrAllocated, int startProcIndex)
	{
		int actorCount = 0;
		IntExpr prevMaxCpuId=null, prevCpuId=null, currCpuId=null, currMaxCpuId=null;

		for (String actrName : actrAllocated)
		{
			Actor actr = partitionAwareGraph.getActor(actrName);
			int repCount = partitionGraphSolutions.getSolution (getpartitionAwareGraphActor(actr)).returnNumber ();

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
	
	private void dmaSymInCluster (List<String>dmaActorList, int startDmaIndex)
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
			
			List<String>dmaAllocation = schedulingConstraints.getActorsAllocatedToDmaOfCluster(cluster);
			
			if(dmaAllocation.size() == 0)
				continue;
			
			// DMA Symmetry.
			dmaSymInCluster (dmaAllocation, platform.getDmaEngineIndex(cluster.getDmaEngine(0)));			
		}
	}

	private void clusterDmaStartTimePrecedences()
	{
		// Note: This is a bug-fix for the deadlock situation we saw.
		// In the Solver, we should have this constraint as
		//	     S DMA Port 0 <= S DMA Port 1 ... <= S DMA Port (n-1)
		//	This is the sequence in which the DMA starts in our framework.
		//	In order to avoid a deadlock, due to DMA Schedule, we assert this constraint.
		//
		//	The example of this deadlock is that
		//	Actor Instance 0 and Actor Instance 1 have DMA scheduled on same engine.
		//	instance 1 waits for buffer space from instance 0, hence needs the DMA of instance 0 to finish.
		//	In DMA schedule, DMA of instance 1 is scheduled before DMA of instance 0.
		
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
	
	private void assertMinPeriod ()
	{
		// Period >= max (exec. time of all actors).
		int maxExecTime = Integer.MIN_VALUE;
		Iterator<Actor> actrIter = partitionAwareGraph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			if (actr.getExecTime () > maxExecTime)
				maxExecTime = actr.getExecTime ();
		}
		try
		{
			generateAssertion (ctx.mkGe (getPeriodDeclId (), ctx.mkInt (maxExecTime)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	private void assertNonOverlapPeriodConstraint()
	{
		// Mutual Exclusion for Dataflow actors
		HashMap<Cluster, HashSet<String>> actorToClusterMap = schedulingConstraints.getActorsMappedToCluster ();		
		for(Cluster cluster : actorToClusterMap.keySet())
		{
			List<String> actrNameList = new ArrayList<String>(actorToClusterMap.get(cluster));	
			List<Actor> actrList = new ArrayList<Actor>();
			for(String actrName : actrNameList)
				actrList.add(partitionAwareGraph.getActor(actrName));
			assertNonOverlapPeriodConstraintDataflow (actrList);			
		}

		// Mutual Exclusion for Communication Actors.	
		actorToClusterMap = schedulingConstraints.getActorsMappedToDmaOfCluster();		
		for(Cluster cluster : actorToClusterMap.keySet())
		{
			List<String> actrNameList = new ArrayList<String>(actorToClusterMap.get(cluster));
			List<Actor> actrList = new ArrayList<Actor>();
			for(String actrName : actrNameList)
				actrList.add(partitionAwareGraph.getActor(actrName));
			assertNonOverlapPeriodConstraintCommunication (actrList);			
		}
	}
	
	// This constraint restricts the actors of previous period overtaking the actors of next period
	// on the same processor.
	private void assertNonOverlapPeriodConstraintDataflow (List<Actor> actrList)
	{		
		// (assert (=> (= cpuA_0 cpuB_0) (and (<= (+ xA_0 1) (+ xB_0 period)) (<= (+ xB_0 2) (+ xA_0 period)))))		
		for (int i=0;i< actrList.size ();i++)
		{
			Actor actr = actrList.get (i);
			boolean hasDmaOutput = hasActorDmaOutput (actr);
			
			for (int j=0;j<partitionGraphSolutions.getSolution (actr).returnNumber ();j++)
			{
				IntExpr cpuJ = cpuId (actr.getName (),j);
				
				IntExpr xJ = xId (actr.getName (), j);
				IntExpr yJ;
				
				if (hasDmaOutput == false)	
					yJ = yId (actr.getName (), j);
				else
					yJ = yDotId (actr.getName (), j);
								
				for (int k=j+1;k<partitionGraphSolutions.getSolution (actr).returnNumber ();k++)
				{
					IntExpr cpuK = cpuId (actr.getName () ,k);
					
					BoolExpr []andArgs = new BoolExpr [2];					
					
					IntExpr xK = xId (actr.getName (), k);
					IntExpr yK = yId (actr.getName (), k);
					
					if (hasDmaOutput == false)
						yK = yId (actr.getName (), k);
					else
						yK = yDotId (actr.getName (), k);
					
					try
					{
						andArgs[0] = ctx.mkLe (yJ, ctx.mkAdd (xK, periodDecl));
						andArgs[1] = ctx.mkLe (yK, ctx.mkAdd (xJ, periodDecl));
						
						generateAssertion (ctx.mkImplies (ctx.mkEq (cpuJ, cpuK), ctx.mkAnd (andArgs)));
					} catch (Z3Exception e) { e.printStackTrace (); }
				}				
			
				for (int k=i+1;k<actrList.size ();k++)
				{
					Actor otherActor = actrList.get (k);
					boolean otherActorHasDmaOutput = hasActorDmaOutput (otherActor);					
					
					for (int l=0;l<partitionGraphSolutions.getSolution (otherActor).returnNumber ();l++)
					{
						IntExpr cpuL = cpuId (otherActor.getName (), l);
						
						BoolExpr []andArgs = new BoolExpr [2];
						
						IntExpr xL = xId (otherActor.getName (), l);
						IntExpr yL;
						
						if(otherActorHasDmaOutput == false)
							yL = yId (otherActor.getName (), l);
						else
							yL = yDotId (otherActor.getName (), l);																	
						
						try
						{
							andArgs[0] = ctx.mkLe (yJ, ctx.mkAdd (xL, periodDecl));
							andArgs[1] = ctx.mkLe (yL, ctx.mkAdd (xJ, periodDecl));						
							
							generateAssertion (ctx.mkImplies (ctx.mkEq (cpuJ, cpuL), ctx.mkAnd (andArgs)));
						} catch (Z3Exception e) { e.printStackTrace (); }
					}						
				}				
			}				
		}
	}
	
	// This constraint restricts the actors of previous period overtaking the actors of next period
	// on the same processor.
	private void assertNonOverlapPeriodConstraintCommunication (List<Actor> actrList)
	{
		// (assert (=> (= cpuA_0 cpuB_0) (and (<= (+ xA_0 1) (+ xB_0 period)) (<= (+ xB_0 2) (+ xA_0 period)))))		
		for (int i=0;i< actrList.size ();i++)
		{
			Actor actr = actrList.get (i);
			for (int j=0;j<partitionGraphSolutions.getSolution (actr).returnNumber ();j++)
			{
				IntExpr cpuJ = cpuId (actr.getName (),j);
				
				IntExpr xJ = xId (actr.getName (), j);
				IntExpr yJ = yId (actr.getName (), j);
								
				for (int k=j+1;k<partitionGraphSolutions.getSolution (actr).returnNumber ();k++)
				{
					IntExpr cpuK = cpuId (actr.getName () ,k);
					
					BoolExpr []andArgs = new BoolExpr [2];					
					
					IntExpr xK = xId (actr.getName (), k);
					IntExpr yK = yId (actr.getName (), k);					
					
					try
					{
						andArgs[0] = ctx.mkLe (yJ, ctx.mkAdd (xK, periodDecl));
						andArgs[1] = ctx.mkLe (yK, ctx.mkAdd (xJ, periodDecl));
						
						generateAssertion (ctx.mkImplies (ctx.mkEq (cpuJ, cpuK), ctx.mkAnd (andArgs)));
					} catch (Z3Exception e) { e.printStackTrace (); }
				}				
			
				for (int k=i+1;k<actrList.size ();k++)
				{
					Actor otherActor = actrList.get (k);
					
					for (int l=0;l<partitionGraphSolutions.getSolution (otherActor).returnNumber ();l++)
					{
						IntExpr cpuL = cpuId (otherActor.getName (), l);
						
						BoolExpr []andArgs = new BoolExpr [2];
						
						IntExpr xL = xId (otherActor.getName (), l);
						IntExpr yL = yId (otherActor.getName (), l);											
						
						try
						{
							andArgs[0] = ctx.mkLe (yJ, ctx.mkAdd (xL, periodDecl));
							andArgs[1] = ctx.mkLe (yL, ctx.mkAdd (xJ, periodDecl));						
							
							generateAssertion (ctx.mkImplies (ctx.mkEq (cpuJ, cpuL), ctx.mkAnd (andArgs)));
						} catch (Z3Exception e) { e.printStackTrace (); }
					}						
				}				
			}				
		}
	}

	public void assertPipelineConstraints ()
	{
		// First define all the variables.
		latencyDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.latencyPrefix, "Int");
		periodDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.periodPrefix, "Int");

		generateActorTimeDefinitions();

		generateCpuDefinitions();

		if (processorSymmetry == true)
			generateProcessorSymmetryDefinitions ();

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
		
		minLatencyBound();
		
		assertMinPeriod();

		clusterDmaStartTimePrecedences();
		
		assertNonOverlapPeriodConstraint();

		generateLatencyCalculation();
	}

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

				Record record = ganttChart.new Record (procName, procIndex, 
						startTime, endTime, 
						actr.getName() + "_" + Integer.toString(instanceId), actorColor);
				record.printNameInGraph = printNameInGraph;
				ganttChart.addRecord(record);
			}
			actorColor++;
		}

		ganttChart.plotChart(outputFileName, getPeriod(model));		
	}

	
	@Override
	public int getLatency(Map<String, String> model)
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.latencyPrefix));
	}

	@Override
	public void generateLatencyConstraint(int latency)
	{
		try
		{
			generateAssertion (ctx.mkLe (latencyDecl, ctx.mkInt (latency)));
		} catch (Z3Exception e) { e.printStackTrace (); }		
	}
	
	@Override
	public void resetSolver()
	{
		super.resetSolver();
		latencyDecl = null;	
		startTimeDecl.clear();
		endTimeDecl.clear();
		durationDecl.clear();
		cpuDecl.clear();
		bufferDecl.clear();
		symmetryDecl.clear();
		System.gc();
	}

	@Override
	public int getPeriod (Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.periodPrefix));
	}

	@Override
	public void generatePeriodConstraint (int periodConstraint)
	{		
		try
		{
			generateAssertion (ctx.mkEq (periodDecl, ctx.mkInt (periodConstraint)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}	
}
