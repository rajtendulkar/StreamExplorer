package solver.sharedMemory.combinedSolver;

import exploration.interfaces.oneDim.BufferConstraints;
import exploration.interfaces.oneDim.LatencyConstraints;
import exploration.interfaces.oneDim.ProcessorConstraints;
import graphanalysis.*;
import graphanalysis.properties.GraphAnalysisSdfAndHsdf;

import java.util.*;
import com.microsoft.z3.*;

import solver.SmtVariablePrefixes;
import solver.Z3Solver;
import spdfcore.*;
import spdfcore.Channel.Link;
import spdfcore.stanalys.*;

/**
 * Mutual exclusion solver parent class
 * 
 * @author Pranav Tendulkar
 *
 */
public abstract class MutualExclusionSolver extends Z3Solver
		implements ProcessorConstraints, LatencyConstraints, BufferConstraints
{
	/**
	 * Application SDF graph 
	 */
	protected Graph graph;
	/**
	 * Equivalent HSDF graph of application graph
	 */
	protected Graph hsdf;
	
	/**
	 * Enable processor symmetry constraints
	 */
	public boolean processorSymmetry = false;
	/**
	 * Enable task symmetry constraints
	 */
	public boolean graphSymmetry = false;
	/**
	 * Enable buffer analysis using functions (true) 
	 * or with if-else statements (false)
	 */
	public boolean bufferAnalysisWithFunctions = false;
	/**
	 * Enable buffer analysis
	 */
	public boolean bufferAnalysis = false;
	/**
	 * Solve by using left-edge algorithm
	 */
	public boolean leftEdgeAlgorithm = false;
	/**
	 * Use graph analysis to generate mutual exclusion
	 * constraints. We determine only those actors which
	 * can execute in parallel and generate mutual exclusion
	 * constraints for them.
	 */
	public boolean mutualExclusionGraphAnalysis = false;
	
	// This list contains the actors which are from HSDF graph.
	/**
	 * list of actors which finish in the end.
	 */
	protected List<Actor> lastActorList = null;
	/**
	 * list of actors which can start at time zero.
	 */
	protected List<Actor> startActorList = null;
	/**
	 * List of overlapping actors
	 */
	protected Map<Actor, HashSet<Actor>> overlappingActorList;
	/**
	 * 
	 */
	protected Map<Actor, List<HashSet<Actor>>> overlappingActorWithGraphSymList;
		
	/**
	 * Solutions of application graph
	 */
	protected Solutions solutions;
	
	/**
	 * SMT variables for start times of the tasks
	 */
	private Map<String, IntExpr> startTimeDecl;
	/**
	 * SMT variables for end times of the tasks
	 */
	private Map<String, IntExpr> endTimeDecl;
	
	/**
	 * SMT variables for duration of the tasks
	 */
	private Map<String, IntExpr> durationDecl;
	/**
	 * SMT variables for processor allocation of the tasks
	 */
	private Map<String, IntExpr> cpuDecl;
	/**
	 * SMT variables for buffer calculation of the channels
	 */
	private Map<String, IntExpr> bufferDecl;
	/**
	 * SMT variable for processor symmetry
	 */
	private Map<String, IntExpr> symmetryDecl;
	/**
	 * SMT variables for Function declaration for buffer analysis
	 */
	private Map<String, FuncDecl> bufferFuncDecl;
	
	/**
	 * SMT variable for latency calculation 
	 */
	protected IntExpr latencyDecl;	
	/**
	 * SMT variable for buffer size calculation
	 */
	protected IntExpr totalBufDecl;
	/**
	 * SMT variable for total processors used
	 */
	protected IntExpr totalProcDecl;	
	
	/**
	 * Get SMT variable for latency of the schedule.
	 * @return variable for latency of the schedule
	 */
	public IntExpr getLatencyDeclId () { return latencyDecl; }	
	/**
	 * Get SMT variable for total buffer size of the schedule
	 * @return variable for total buffer size of the schedule
	 */
	public IntExpr getBufDeclId () { return totalBufDecl; }	
	/**
	 * Get SMT variable for total processors used in the schedule
	 * @return variable for total processors used in the schedule
	 */
	public IntExpr getProcDeclId () { return totalProcDecl; }

	/**
	 * Initialize the mutual exclusion solver 
	 * 
	 * @param inputGraph application SDF graph 
	 */
	public MutualExclusionSolver (Graph inputGraph) 
	{		
		graph = inputGraph;
		
		TransformSDFtoHSDF toHSDF = new TransformSDFtoHSDF ();
		hsdf = toHSDF.convertSDFtoHSDFWithUniqueChannels (graph);		
		
		GraphExpressions expressions = new GraphExpressions ();
		expressions.parse (graph);
		solutions = new Solutions ();
		solutions.setThrowExceptionFlag (false);	    		
		solutions.solve (graph, expressions);
		
		int repetitionSum = 0;
		for (Iterator<Actor> actrIter = graph.getActors ();actrIter.hasNext ();)
		{
			Actor actr = actrIter.next ();
			repetitionSum += solutions.getSolution (actr).returnNumber ();
		}		
		System.out.println ("Repetition Sum : " + repetitionSum);
		
		GraphAnalysisSdfAndHsdf analysis = new GraphAnalysisSdfAndHsdf (graph, solutions, hsdf);
		startActorList = analysis.findHsdfStartActors ();
		lastActorList = analysis.findHsdfEndActors ();		
						
		startTimeDecl 	= new TreeMap<String, IntExpr>();
		endTimeDecl 	= new TreeMap<String, IntExpr>();		
		durationDecl 	= new TreeMap<String, IntExpr>();
		cpuDecl 		= new TreeMap<String, IntExpr>();
		bufferDecl 		= new TreeMap<String, IntExpr>();
		symmetryDecl 	= new TreeMap<String, IntExpr>();
		bufferFuncDecl 	= new TreeMap<String, FuncDecl>();
	}
	
	/**
	 * Get all the successors which do not have any initial tokens
	 * on the channels connecting to it. We go through entire chain
	 * till the end of the graph. So this method won't support cyclic
	 * graphs.
	 * 
	 * @param actr actor 
	 * @return list of all the successors in the graph of this actor
	 */
	private List<Actor> getSuccessors (Actor actr) 
	{
		List<Actor> result = new ArrayList<Actor>();
		for (Link lnk : actr.getLinks (Port.DIR.OUT))
		{
			if (lnk.getChannel ().getInitialTokens () != 0)
				continue;
			Actor successor = lnk.getOpposite ().getActor ();
			result.add (successor);
			result.addAll (getSuccessors (successor));
		}
		return result;
	}
	
	/**
	 * Get all the predecessors which do not have any initial tokens
	 * on the channels connecting to it. We go through entire chain
	 * till the end of the graph. So this method won't support cyclic
	 * graphs.
	 * 
	 * @param actr actor 
	 * @return list of all the predecessors in the graph of this actor
	 */
	private List<Actor> getPredecessors (Actor actr) 
	{
		List<Actor> result = new ArrayList<Actor>();
		for (Link lnk : actr.getLinks (Port.DIR.IN))
		{
			if (lnk.getChannel ().getInitialTokens () != 0)
				continue;
			Actor predecessor = lnk.getOpposite ().getActor ();
			result.add (predecessor);
			result.addAll (getPredecessors (predecessor));
		}		
		return result;
	}
	
//	private void printOverlapList ()
//	{
//		for (Map.Entry<Actor, HashSet<Actor>> mapEntry : overlappingActorList.entrySet ())
//		{
//			Actor actr = mapEntry.getKey ();
//			System.out.println ("Actor :: " + actr.getName ());
//			HashSet<Actor> overlapActors = mapEntry.getValue ();			
//			
//			for (Actor overlapActr : overlapActors)
//			{
//				System.out.println ("O : " + overlapActr.getName ());
//			}
//		}
//	}
	
	/**
	 * Build a list of actors which can execute in parallel.
	 */
	protected void buildOverlappingActorList ()
	{		
		overlappingActorList = new HashMap<Actor, HashSet<Actor> >();
		overlappingActorWithGraphSymList = new HashMap<Actor, List<HashSet<Actor>>>();
		
		Iterator<Actor> actrIter = hsdf.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			HashSet<Actor> overlappingActors = new HashSet<Actor>();
			
			List<Actor> nonOverlapActors = new ArrayList<Actor>();
			nonOverlapActors.addAll (getPredecessors (actr));
			nonOverlapActors.addAll (getSuccessors (actr));
			
			if (graphSymmetry == true)
			{				
				String actorName = actr.getName ().substring (0, actr.getName ().indexOf ('_'));				
				int repCount = solutions.getSolution (graph.getActor (actorName)).returnNumber ();
				int thisActorCount = Integer.parseInt (actr.getName ().substring (actr.getName ().indexOf ('_')+1));
				
				if (repCount > 1)
				{
					HashSet<Actor> startBeforeActors = new HashSet<Actor>();
					HashSet<Actor> endBeforeActors = new HashSet<Actor>();
					List<HashSet<Actor>> tempList = new ArrayList<HashSet<Actor>>();
					overlappingActorWithGraphSymList.put (actr, tempList);
					
					tempList.add (startBeforeActors);
					tempList.add (endBeforeActors);
					
					for (int i=0;i<repCount;i++)
					{
						if (i < thisActorCount)
							nonOverlapActors.addAll (getPredecessors (hsdf.getActor (actorName+"_"+Integer.toString (i))));
						else if (i == thisActorCount)
							continue;
						else
						{
							nonOverlapActors.addAll (getSuccessors (hsdf.getActor (actorName+"_"+Integer.toString (i))));
							startBeforeActors.add (hsdf.getActor (actorName+"_"+Integer.toString (i)));
							endBeforeActors.add (hsdf.getActor (actorName+"_"+Integer.toString (i)));
						}
					}
				}				
			}
			
			// Form the result.
			Iterator<Actor> actrIter2 = hsdf.getActors ();
			while (actrIter2.hasNext ())
			{
				Actor actr2 = actrIter2.next ();
				if ((nonOverlapActors.contains (actr2) == false) && (actr.getName ().equals (actr2.getName ()) == false))
					overlappingActors.add (actr2);
			}
			
			overlappingActorList.put (actr, overlappingActors);			
			// Search Overlapping actors for this actor.
		}
	}
	
	/**
	 * Get list of actors without any predecessors.
	 * 
	 * @return list of actors without any predecessors.
	 */
	public List<Actor> getStartActors () { return startActorList; }
	
	/**
	 *  Get list of actors without any successors.
	 *  
	 * @return list of actors without any successors
	 */
	public List<Actor> getLastActors () { return lastActorList; }
	
	/*****************************************************************
	 * These methods are present only in one of the derived class.
	 * That is the reason why, I cannot define them as abstract. 
	 *****************************************************************/
	/**
	 * Generate constraints for solving pipelined scheduling problem.
	 */
	public void assertPipelineConstraints ()
	{
		throw new RuntimeException ("Reached Base Class Method\n");
	}
	
	/**
	 * Generate constraints for solving non-pipelined scheduling problem.
	 */
	public void assertNonPipelineConstraints ()
	{
		throw new RuntimeException ("Reached Base Class Method\n");
	}
	
	/**
	 * Generate period cost for a problem
	 * @param periodConstraint period cost constraint
	 */
	public void generatePeriodConstraint (int periodConstraint)
	{		
		throw new RuntimeException ("Reached Base Class Method\n");
	}
	
	/*****************************************************************/			
	
	/**
	 * Get SMT variable for start time of a task.
	 * 
	 * @param name name of the task
	 * @return variable for start time of a task
	 */
	protected IntExpr xId (String name) 		 			 { return startTimeDecl.get (SmtVariablePrefixes.startTimePrefix + name); }
	
	/**
	 * Get SMT variable for start time of an actor instance.
	 * 
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable for start time of an actor instance
	 */
	protected IntExpr xId (String name, int index) 		 { return startTimeDecl.get (SmtVariablePrefixes.startTimePrefix + name + "_" + Integer.toString (index)); }
	
	/**
	 * Get SMT variable for end time of a task.
	 * 
	 * @param name name of the task
	 * @return variable for end time of a task
	 */
	protected IntExpr yId (String name) 		 			 { return endTimeDecl.get (SmtVariablePrefixes.endTimePrefix + name); }
	
	/**
	 * Get SMT variable for end time of an actor instance.
	 * 
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable for end time of an actor instance
	 */
	protected IntExpr yId (String name, int index) 		 { return endTimeDecl.get (SmtVariablePrefixes.endTimePrefix + name + "_" + Integer.toString (index)); }
	
	/**
	 * Get SMT variable for processor allocated to a task.
	 * 
	 * @param name name of the task
	 * @return variable for processor allocated to a task
	 */
	protected IntExpr cpuId (String name) 	 			 { return cpuDecl.get (SmtVariablePrefixes.cpuPrefix + name); }
	
	/**
	 * Get SMT variable for processor allocated to an actor instance
	 * 
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable for processor allocated to an actor instance
	 */
	protected IntExpr cpuId (String name, int index) 	 { return cpuDecl.get (SmtVariablePrefixes.cpuPrefix + name + "_" + Integer.toString (index)); }	
	
	/**
	 * Get SMT variable for a production rate on a channel.
	 * 
	 * @param name name of the channel
	 * @return variable for a production rate on a channel
	 */
	protected IntExpr prodRateId (String name)			 { return bufferDecl.get (SmtVariablePrefixes.productionRatePrefix + name); }
	
	/**
	 * Get SMT variable for a consumption rate on a channel.
	 * 
	 * @param name name of the channel
	 * @return variable for a consumption rate on a channel
	 */
	protected IntExpr consRateId (String name)			 { return bufferDecl.get (SmtVariablePrefixes.consumptionRatePrefix + name); }
	
	/**
	 * Get SMT variable for duration of an actor.
	 * 
	 * @param name name of the actor
	 * @return variable for duration of an actor
	 */
	protected IntExpr durationId (String name)			 { return durationDecl.get (SmtVariablePrefixes.durationPrefix+ name); }
	
	/**
	 * Get SMT variable for maximum processor index on which a task can run for processor symmetry constraints.
	 * 
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable for maximum processor index on which a task can run
	 */
	protected IntExpr maxCpuId (String name, int index) 	 { return symmetryDecl.get (SmtVariablePrefixes.maxCpuPrefix+ name+Integer.toString (index)); }
	
	/**
	 * Get SMT variable for maximum buffer size for a channel.
	 * @param srcActor producer actor of the channel
	 * @param dstActor consumer actor of the channel
	 * @return variable for maximum buffer size for a channel
	 */
	protected IntExpr maxBufferId (String srcActor, String dstActor) { return bufferDecl.get (SmtVariablePrefixes.maxBufferPrefix + srcActor + dstActor); }
	
	/**
	 * Get SMT variable to calculate tasks started before this task. 
	 * It is required only in the left-edge algorithm
	 * 
	 * @param actorName name of the actor
	 * @param instanceId instance id
	 * @return variable to calculate tasks started before this task
	 */
	protected IntExpr tasksStartedBeforeId (String actorName, int instanceId) { return cpuDecl.get (SmtVariablePrefixes.tasksStartedBeforePrefix + actorName + "_" + Integer.toString (instanceId)); }
	
	/**
	 * Get SMT variable to calculate tasks ended before this task. 
	 * It is required only in the left-edge algorithm
	 * 
	 * @param actorName name of the actor
	 * @param instanceId instance id
	 * @return variable to calculate tasks ended before this task
	 */
	protected IntExpr tasksEndedBeforeId (String actorName, int instanceId) { return cpuDecl.get (SmtVariablePrefixes.tasksEndedBeforePrefix + actorName + "_" + Integer.toString (instanceId)); }
	
	/**
	 * Get SMT variable to calculate processor utilization at this actor instance
	 * Required for left-edge algorithm
	 * 
	 * @param actorName name of the actor
	 * @param instanceId instance id
	 * @return variable to calculate processor utilization at this actor instance
	 */
	protected IntExpr procUtilId (String actorName, int instanceId) { return cpuDecl.get (SmtVariablePrefixes.procUtilPrefix + actorName + "_" + Integer.toString (instanceId)); }
	
	/**
	 * Get SMT variable for maximum index of processor on which a task can run.
	 * This is required for processor symmetry constraints.
	 * 
	 * @param name name of the task
	 * @return variable for maximum index of processor on which a task can run
	 */
	protected IntExpr maxCpuId (String name) 	 { return symmetryDecl.get (SmtVariablePrefixes.maxCpuPrefix+ name); }	
	
	/**
	 * Get SMT variable for buffer size at a producer of a channel connecting two actors
	 * 
	 * @param srcActor producer actor
	 * @param dstActor consumer actor
	 * @param index index of the buffer, <= repetition count of srcActor 
	 * @return variable for buffer size at a producer of a channel connecting two actors
	 */
	protected IntExpr bufferAtId (String srcActor, String dstActor, int index) 
			{ return bufferDecl.get (SmtVariablePrefixes.bufferAtPrefix + srcActor +"_" + Integer.toString (index) + "-" + srcActor + dstActor); };
	
	/**
	 * Get SMT variable for buffer size at a given index of the producer for a channel.
	 * This is required when calculating buffer size using functions.
	 * 
	 * @param name name of the channel
	 * @param index instance id of the producer
	 * @return function application at the given index buff(0) for example.
	 */
	protected Expr buffId (String name, int index) 	 
	{ 
		Expr result = null;
		try
		{
			result = ctx.mkApp (bufferFuncDecl.get (SmtVariablePrefixes.bufferPrefix + name), ctx.mkInt (index));
		} catch (Z3Exception e) { e.printStackTrace (); }
		return result;
	}
	
	/**
	 * Get SMT variable for buffer size at a given index of the producer for a channel.
	 * The index is specified with an expression rather than integer.
	 * This is required when calculating buffer size using functions.
	 * 
	 * @param name name of the channel
	 * @param indexId index expression
	 * @return function application at the given index buff(a+b) for example.
	 */
	protected Expr buffId (String name, Expr indexId) 
	{ 
		Expr result = null;
		try
		{
			result = ctx.mkApp (bufferFuncDecl.get (SmtVariablePrefixes.bufferPrefix + name), indexId);
		} catch (Z3Exception e) { e.printStackTrace (); }
		return result;
	}
	
	/**
	 * Get SMT variable for index function application at a given value.
	 * 
	 * @param name name of the index variable
	 * @param index location index for the function
	 * @return variable for index function application, for example idxAB(0)
	 */
	protected Expr idxId (String name, int index) 	 
	{ 
		Expr result = null;
		try
		{
			result = ctx.mkApp (bufferFuncDecl.get (SmtVariablePrefixes.bufferIndexPrefix + name), ctx.mkInt (index));
		} catch (Z3Exception e) { e.printStackTrace (); }
		return result;
	}
	
	/**
	 * Set a random seed for Z3 solver.
	 * 
	 * @param seed integer value as a seed.
	 */
	public void setRandomSeed (int seed) { setRandomSeed (z3Solver, seed); }
	

	/**
	 * Generate constraints to make sure at least one task is allocated to a processor.
	 * Note : Didn't give much benefit to the solver.
	 * 
	 * @param numProcessors total number of processors.
	 */
	protected void allProcessorUtilisedConstraints (int numProcessors)
	{
		int totalRepCount = 0;
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{				
			Actor actr = actorIter.next ();
			totalRepCount += solutions.getSolution (actr).returnNumber ();
		}
		
		BoolExpr orArgs[] = new BoolExpr[totalRepCount];
		
		for (int i=0;i<numProcessors;i++)
		{
			int count = 0;
			actorIter = graph.getActors ();
			try
			{
				while (actorIter.hasNext ())
				{				
					Actor actr = actorIter.next ();
					for (int j=0;j<solutions.getSolution (actr).returnNumber ();j++)				
						orArgs[count++] = ctx.mkEq (cpuId (actr.getName (), j), ctx.mkInt (i));				
				}
				
				generateAssertion (ctx.mkOr (orArgs));
			} catch (Z3Exception e) { e.printStackTrace (); }
		}
	}

	/**
	 * Generate constraint to decide an order processor allocation for the tasks.
	 * Note : Didn't give much benefit to the solver.
	 */
	protected void processorOverlapSymmetryConstraints ()
	{
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{				
			Actor actr = actorIter.next ();
			int repCount = solutions.getSolution (actr).returnNumber ();

			for (int j=0;j<repCount-1;j++)
			{
				for (int k=j+1;k<repCount;k++)
				{
					// (assert (implies (and (< xa4_2 (+ xa4_6 10)) (< xa4_6 (+ xa4_2 10))) (< cpua4_2 cpua4_6)))
					try
					{
						generateAssertion (ctx.mkImplies (
							ctx.mkAnd (ctx.mkLt (xId (actr.getName (), j), yId (actr.getName (), k)), 
									ctx.mkLt (xId (actr.getName (), k), yId (actr.getName (), j))),
							// Implication
							ctx.mkLt (cpuId (actr.getName (), j), cpuId (actr.getName (), k))));
					} catch (Z3Exception e) { e.printStackTrace (); }
				}
			}
		}
	}
	
	/**
	 * Generate Task symmetry constraints
	 */
	protected void graphSymmetryLexicographic ()
	{
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			if (solutions.getSolution (actr).returnNumber () > 1)
			{
				for (int i=1;i<solutions.getSolution (actr).returnNumber ();i++)
				{
					try
					{
						generateAssertion (ctx.mkLe (xId (actr.getName (), i-1), xId (actr.getName (), i)));
					} catch (Z3Exception e) { e.printStackTrace (); }
				}
			}
		}
	}

// Old Symmetry Breaking Constraints which considers cousins.
//	protected void graphSymmetryConstraints ()
//	{
//		GraphSymmetryBreaking symBreaking = new GraphSymmetryBreaking (graph, solutions);
//		symBreaking.generateSymmetryGraph (outputDirectory + "symmetry.dot");
//		symBreaking.generateIndexForSymmetryEdges ();
//		List<Entry<String, String>> symEdges = symBreaking.getSymmetryEdges ();
//		
//		for (int i=0;i<symEdges.size ();i++)
//		{
//			String srcActorStr = symEdges.get (i).getKey ();
//			String dstActorStr = symEdges.get (i).getValue ();
//			String[] srcSplit = srcActorStr.split ("_");
//			String[] dstSplit = dstActorStr.split ("_");
//			String srcActor = srcSplit[0];
//			String dstActor = dstSplit[0];
//			int srcInstance = Integer.parseInt (srcSplit[1]);
//			int dstInstance = Integer.parseInt (dstSplit[1]);
//			
//			generateAssertion (ctx.mkLe (xId (srcActor, srcInstance), xId (dstActor, dstInstance)));			
//		}
//	}

	/**
	 * Generate Processor symmetry definitions
	 */
	protected void generateProcessorSymmetryDefinitions ()
	{
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{				
			Actor actr = actorIter.next ();
			int repCount = solutions.getSolution (actr).returnNumber ();
			for (int i=0;i<repCount;i++)
			{
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.maxCpuPrefix + actr.getName () + Integer.toString (i), "Int");
				symmetryDecl.put (SmtVariablePrefixes.maxCpuPrefix+ actr.getName () +Integer.toString (i), id);
			}
		}
	}
	
	/**
	 * Generate processor symmetry constraints
	 */
	protected void processorSymmetryConstraints ()
	{		
		IntExpr prevMaxCpuId=null, prevCpuId=null, currCpuId=null, currMaxCpuId=null;
		int actorCount = 0;		
		Iterator<Actor> actorIter = graph.getActors ();

		while (actorIter.hasNext ())
		{				
			Actor actr = actorIter.next ();
			int repCount = solutions.getSolution (actr).returnNumber ();
			
			for (int i=0;i<repCount;i++)
			{
				currMaxCpuId = maxCpuId (actr.getName (), i);
				currCpuId 	 = cpuId (actr.getName (), i);

				try
				{
					if (actorCount == 0)
					{
						// This is a first actor instance.									
						generateAssertion (ctx.mkEq (currCpuId, ctx.mkInt (0)));				
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
	 * Assert mutual exclusion constraints by performing graph analysis
	 * to form a list of tasks which can potentially execute in parallel
	 * and apply mutual exclusion only on such tasks.
	 */
	private void assertMutualExclusionWithGraphAnalysis ()
	{
		
		buildOverlappingActorList ();
		// printOverlapList ();
		
		for (Map.Entry<Actor, HashSet<Actor>> mapEntry : overlappingActorList.entrySet ())
		{
			Actor actr = mapEntry.getKey ();
			HashSet<Actor> overlapActors = mapEntry.getValue ();
			
			IntExpr idxCpuA = cpuId (actr.getName ());
			IntExpr idx_xA = xId (actr.getName ()); 
			IntExpr idx_yA = yId (actr.getName ());
			
			for (Actor overlapActr : overlapActors)
			{
				// (=> (= (cpuA 0) (cpuB 0)) (or (>= (xA 0) (+ (xB 0) dB)) (>= (xB 0) (+ (xA 0) dA))))
				
				IntExpr idxCpuB = cpuId (overlapActr.getName ());
				IntExpr idx_xB = xId (overlapActr.getName ());				 
				IntExpr idx_yB = yId (overlapActr.getName ());												

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

	/**
	 * Generate mutual exclusion constraints for all the tasks.
	 */
	private void assertMutualExclusion ()
	{								
		List<Actor> actrList = new ArrayList<Actor>();
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{				
			Actor actr = actorIter.next ();
			actrList.add (actr);				
		}
		
		for (int i=0;i< actrList.size ();i++)
		{
			Actor actr = actrList.get (i);
			for (int j=0;j<solutions.getSolution (actr).returnNumber ();j++)
			{
				for (int k=j+1;k<solutions.getSolution (actr).returnNumber ();k++)
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
							ctx.mkOr (ctx.mkGe (xJ, yK), 
									ctx.mkGe (xK, yJ))));
					} catch (Z3Exception e) { e.printStackTrace (); }
				}				

				for (int k=i+1;k<actrList.size ();k++)
				{
					Actor otherActor = actrList.get (k);
					for (int l=0;l<solutions.getSolution (otherActor).returnNumber ();l++)
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
	 * Generate constraints for lower and upper bounds on the start times of tasks 
	 */
	protected void assertStartTimeBounds ()
	{
		int totalRepCount = 0;
		// In this old code, we have every actor start time xA >= 0
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{				
			Actor actr = actorIter.next ();
			totalRepCount += solutions.getSolution (actr).returnNumber ();
		}

		BoolExpr orArgs[] = new BoolExpr[totalRepCount];
		BoolExpr andArgs[] = new BoolExpr[2];
		int count = 0;

		actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			int actorRepCount = solutions.getSolution (actr).returnNumber ();
			for (int i=0;i< actorRepCount;i++)
			{
				// (xD 0)
				IntExpr startTimeIdx = xId (actr.getName (), i);				

				// (assert (and (>= xA_0 0) (<= xA_0 latency)))
				try
				{
					orArgs[count++] = ctx.mkEq (startTimeIdx, ctx.mkInt (0));
	
					andArgs[0] = ctx.mkGe (startTimeIdx, ctx.mkInt (0));
					andArgs[1] = ctx.mkLe (startTimeIdx, getLatencyDeclId ());
					generateAssertion (ctx.mkAnd (andArgs));
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
	 * Generate constraints for lower and upper bounds on buffer size of a channel.
	 * 
	 * Explanation on buffer with Functions: 
	 * 
	 * Lets suppose that we have a channel with 3 producers A_0, A_1, A_2 and
	 * 2 consumers B_0 and B_1
	 * We allocate an index to all the producers and consumers of the channel.
	 * 
	 * (idxAB 0) is index of A_0
	 * (idxAB 1) is index of A_1
	 * (idxAB 2) is index of A_2
	 * (idxAB 3) is index of B_0
	 * (idxAB 4) is index of B_1
	 * 
	 * Now lower bound on index is 1 and upper bound is 5 inclusive.
	 * index 0 is reserved for the initial tokens.
	 * Depending on the start and end times the index is allocated.
	 * If first A_0 executes it will have index value 0, if A_1 executes next then it will
	 * have index value 1 and so on. The values will be distinct and between (1,5).
	 * 
	 * When a producer starts, the value of buffer is incremented by production rate and
	 * when a consumer ends, the value is decremented by consumption rate.
	 * 
	 * For example index value of A_0 is 1, i.e. (idxAB 0) = 1. So it records the buffer size as
	 * bufAB (idxAB 0) =  bufAB ((idxAB 0) - 1) + production rate
	 * Similarly if index value of B_0 is 2, i.e. (idxAB 3) = 2, it records buffer size as :
	 * bufAB (idxAB 3) =  bufAB ((idxAB 3) - 1) + consumption rate
	 * 
	 * value of buffAB(0) will be initial tokens
	 * value of buffAB(1) will be  buffAB(0) + production rate
	 * value of buffAB(2) will be buffAB(0) - consumption rate
	 * and so on.
	 *  
	 * A maximum on all of them gives the max buffer size.  
	 *  
	 * @param srcActor source actor of a channel
	 * @param dstActor sink actor of a channel
	 * @param maxBufIndex maximum buffer index of a channel 
	 * @param upperBound upper bound on the buffer size
	 */
	protected void assertBufferBounds (String srcActor, String dstActor, int maxBufIndex, int upperBound)
	{
		/**********************************************************************************/
		// All buffer values are greater than or equal to zero (no negative tokens).
		// (assert (and (>= (bufAB 0) 0) (>= (bufAB 1) 0) (>= (bufAB 2) 0) (>= (bufAB 3) 0) 
		// (>= (bufAB 4) 0) (>= (bufAB 5) 0) ))
		/**********************************************************************************/
		BoolExpr args[] = new BoolExpr[2];
		for (int i=0;i<maxBufIndex;i++)
		{
			try
			{
				args[0] = ctx.mkGe ((IntExpr) buffId (srcActor + dstActor, i), ctx.mkInt (0));
				args[1] = ctx.mkLe ((IntExpr) buffId (srcActor + dstActor, i), ctx.mkInt (upperBound));
				generateAssertion (ctx.mkAnd (args));
			} catch (Z3Exception e) { e.printStackTrace (); }
		}		
	}

	/**
	 * Generate constraints for lower and upper bounds on the index allocation
	 * 
	 * @param srcActor source actor of a channel
	 * @param dstActor sink actor of a channel
	 * @param maxBufIndex maximum buffer index of a channel 
	 */
	protected void assertIndexLimits (String srcActor, String dstActor, int maxBufIndex)
	{
		/**********************************************************************************/
		// Index is allocated in a range.			
		// (assert (and (> (idxAB 1) 0) (< (idxAB 1) 7)))
		/**********************************************************************************/

		for (int i=0; i<maxBufIndex;i++)
		{
			try
			{
				generateAssertion (ctx.mkAnd (ctx.mkGt ((IntExpr) idxId (srcActor + dstActor, i), ctx.mkInt (0)), 
					ctx.mkLt ((IntExpr) idxId (srcActor + dstActor, i), ctx.mkInt (maxBufIndex+1))));
			} catch (Z3Exception e) { e.printStackTrace (); }
		}	
	}

	/**
	 * Generate constraints to specify that all the indices are unique.
	 * 
	 * @param indexName name of the index variable
	 * @param maxBufIndex maximum buffer index
	 */
	protected void assertUniqueIndex (String indexName, int maxBufIndex)
	{
		/**********************************************************************************/
		// Each index is completely unique. No two indices are the same.
		//(assert (and 
		// (/= (idxAB 1) (idxAB 2)) (/= (idxAB 1) (idxAB 3)) (/= (idxAB 1) (idxAB 4)) 
		// (/= (idxAB 1) (idxAB 5)) (/= (idxAB 1) (idxAB 6)) (/= (idxAB 2) (idxAB 3)) 
		// (/= (idxAB 2) (idxAB 4)) (/= (idxAB 2) (idxAB 5)) (/= (idxAB 2) (idxAB 6)) 
		// (/= (idxAB 3) (idxAB 4)) (/= (idxAB 3) (idxAB 5)) (/= (idxAB 3) (idxAB 6)) 
		// (/= (idxAB 4) (idxAB 5)) (/= (idxAB 4) (idxAB 6)) (/= (idxAB 5) (idxAB 6))))
		/**********************************************************************************/

		Expr[] args = new Expr[maxBufIndex];
		for (int i=0; i<maxBufIndex;i++)
			args[i] = idxId (indexName, i);

		try
		{
			generateAssertion (ctx.mkDistinct (args));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}	

	/**
	 * Generate constraints to calculate the buffer at each index
	 * 
	 * @param srcActor source actor of the channel
	 * @param dstActor sink actor of the channel
	 * @param srcRepCount repetition count of the source actor
	 * @param dstRepCount repetition count of the sink actor
	 * @param maxBufIndex maximum value of index
	 */
	protected void assertBuffer (String srcActor, String dstActor, int srcRepCount, int dstRepCount, int maxBufIndex)
	{
		/**********************************************************************************/
		// (assert (= (bufAB (idxAB 3)) (+ (bufAB (- (idxAB 3) 1)) pAB)))
		/**********************************************************************************/				
		for (int i=0; i<srcRepCount;i++)
		{
			try
			{
				generateAssertion (ctx.mkEq (buffId (srcActor + dstActor , idxId (srcActor+dstActor, i)), 
					ctx.mkAdd ((ArithExpr)buffId (srcActor+dstActor, ctx.mkSub ((ArithExpr)idxId (srcActor+dstActor, i), (ArithExpr)ctx.mkInt (1))), 
							prodRateId (srcActor + dstActor))));
			} catch (Z3Exception e) { e.printStackTrace (); }
		}


		/**********************************************************************************/
		// (assert (= (bufAB (idxAB 4)) (- (bufAB (- (idxAB 4) 1)) cAB)))
		/**********************************************************************************/
		for (int i=(srcRepCount); i<maxBufIndex;i++)
		{
			try
			{
				generateAssertion (ctx.mkEq (buffId ((srcActor + dstActor) , idxId (srcActor+dstActor, i)), 
					ctx.mkSub ((ArithExpr)buffId (srcActor+dstActor, ctx.mkSub ((ArithExpr)idxId (srcActor+dstActor, i), ctx.mkInt (1))), 
							consRateId (srcActor + dstActor))));
			} catch (Z3Exception e) { e.printStackTrace (); }
		}	
	}
	
	/**
	 * Generate constraints to calculate the maximum size of the buffer.
	 * 
	 * @param srcActor source actor of the channel
	 * @param dstActor sink actor of the channel
	 * @param srcRepCount source repetition count
	 * @param initialTokensId expression pointing to initial tokens on the channel
	 */
	protected void assertMaxBufferLinear (String srcActor, String dstActor, int srcRepCount, Expr initialTokensId)
	{
		try
		{
			if (srcActor.equals (dstActor) == true)
			{
				IntExpr maxBuffId = maxBufferId (srcActor, srcActor);
				generateAssertion (ctx.mkEq (maxBuffId, initialTokensId));
			}
			else
			{		
				BoolExpr orArgs[] = new BoolExpr[srcRepCount+1];
				BoolExpr andArgs[] = new BoolExpr[srcRepCount+1];
		
				IntExpr maxBuffId = maxBufferId (srcActor, dstActor); // bufferDecl.get ("maxBuf" + srcActor + dstActor);
				
				orArgs[0] = ctx.mkEq (maxBuffId, initialTokensId);
				andArgs[0] = ctx.mkGe (maxBuffId, (ArithExpr)initialTokensId);
		
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
	 * Generate constraints for upper and lower bounds on the buffer size at every index and maximum buffer size
	 * of a channel.
	 *  
	 * @param srcActor source actor of the channel
	 * @param dstActor sink actor of the channel
	 * @param srcRepCount source repetition count
	 * @param bound upper bound on the buffer size
	 */
	protected void assertBufferBoundsLinear (String srcActor, String dstActor, int srcRepCount, int bound)
	{
		BoolExpr andArgs[] = new BoolExpr[2];
		
		try
		{		
			if (srcActor.equals (dstActor) == false)
			{			
				for (int i=0;i<srcRepCount;i++)
				{			
					IntExpr buffId = bufferAtId (srcActor, dstActor, i);
					andArgs[0] = ctx.mkGt (buffId, ctx.mkInt (0));
					andArgs[1] = ctx.mkLe (buffId, ctx.mkInt (bound));
					generateAssertion (ctx.mkAnd (andArgs));
				}
			}
				
			IntExpr maxBuffId = maxBufferId (srcActor, dstActor);
			andArgs[0] = ctx.mkGt (maxBuffId, ctx.mkInt (0));
			andArgs[1] = ctx.mkLe (maxBuffId, ctx.mkInt (bound));		
			generateAssertion (ctx.mkAnd (andArgs));
		} catch (Z3Exception e) { e.printStackTrace (); }		
	}

	/**
	 * Generate constraints to calculate maximum buffer size for a channel.
	 * 
	 * @param srcActor source actor of the channel
	 * @param dstActor sink actor of the channel
	 * @param maxBufIndex maximum value of index for the buffer
	 */
	protected void assertMaxBuffer (String srcActor, String dstActor, int maxBufIndex)
	{
		/**********************************************************************************/
		// Find out the Maximum Buffer
		// (assert (or (= maxBufAB (bufAB 0)) (= maxBufAB (bufAB 1)) (= maxBufAB (bufAB 2)) (= maxBufAB (bufAB 3)) (= maxBufAB (bufAB 4))))
		// (assert (and (>= maxBufAB (bufAB 0)) (>= maxBufAB (bufAB 1)) (>= maxBufAB (bufAB 2)) (>= maxBufAB (bufAB 3)) (>= maxBufAB (bufAB 4))))
		/**********************************************************************************/		
		BoolExpr orIds[] = new BoolExpr [maxBufIndex];
		IntExpr maxBufId = maxBufferId (srcActor, dstActor); 
		// bufferDecl.get (SolverPrefixs.maxBufferPrefix+srcActor + dstActor);

		try
		{
			for (int i=0;i<maxBufIndex;i++)					
				orIds[i] = ctx.mkEq (maxBufId, buffId (srcActor+dstActor, i));

			generateAssertion (ctx.mkOr (orIds));

			BoolExpr andIds[] = new BoolExpr [maxBufIndex];
			for (int i=0;i<maxBufIndex;i++)					
				andIds[i] = ctx.mkGe (maxBufId, (IntExpr) buffId (srcActor+dstActor, i));

			generateAssertion (ctx.mkAnd (andIds));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}	

	/**
	 * Generate constraints to calculate latency of the schedule
	 */
	protected void generateLatencyCalculation ()
	{
		if (lastActorList.size () == 0)
			throw new RuntimeException ("Unable to find the End Actor");

		if (lastActorList.size () == 1)
		{
			Actor actr = lastActorList.get (0);
			String actorName = (actr.getName ()).replaceAll ("_[0-9]$", "");
			int actorInstance =  Integer.parseInt (actr.getName ().substring (actr.getName ().indexOf ("_") + 1));
			try
			{
				generateAssertion (ctx.mkEq (latencyDecl, yId (actorName, actorInstance)));
			} catch (Z3Exception e) { e.printStackTrace (); }
		}
		else if (lastActorList.size () == 2)
		{
			Actor actr0 = lastActorList.get (0);
			Actor actr1 = lastActorList.get (1);
			String actor0Name = (actr0.getName ()).replaceAll ("_[0-9]*$", "");
			String actor1Name = (actr1.getName ()).replaceAll ("_[0-9]*$", "");			
			int actorInstance0 = Integer.parseInt (actr0.getName ().substring (actr0.getName ().indexOf ("_") + 1));
			int actorInstance1 = Integer.parseInt (actr1.getName ().substring (actr1.getName ().indexOf ("_") + 1));

			IntExpr bufActId0 = yId (actor0Name, actorInstance0);			
			IntExpr bufActId1 = yId (actor1Name, actorInstance1);

			try
			{
				BoolExpr eq = ctx.mkGe (bufActId0,bufActId1);
				BoolExpr left = ctx.mkEq (latencyDecl, bufActId0);
				BoolExpr right = ctx.mkEq (latencyDecl, bufActId1);
	
				generateAssertion ((BoolExpr) ctx.mkITE (eq,left, right));
			} catch (Z3Exception e) { e.printStackTrace (); }
		}
		else
		{			
			BoolExpr orArgs[] = new BoolExpr[lastActorList.size ()];
			BoolExpr andArgs[] = new BoolExpr[lastActorList.size ()];

			try
			{
				for (int i=0;i<lastActorList.size ();i++)
				{
					Actor actrI = lastActorList.get (i);				
					String actorIName = actrI.getName ().replaceAll ("_[0-9]*", "");
					int actorInstanceI = Integer.parseInt (actrI.getName ().replaceAll ("[a-z,A-Z,0-9]*_", ""));
	
					orArgs[i] = ctx.mkEq (latencyDecl, yId (actorIName, actorInstanceI));
					andArgs[i] = ctx.mkGe (latencyDecl, yId (actorIName, actorInstanceI));								
				}
	
				generateAssertion (ctx.mkOr (orArgs));
				generateAssertion (ctx.mkAnd (andArgs));
			} catch (Z3Exception e) { e.printStackTrace (); }
		}
	}

	/**
	 * Generate constraints for task precedences
	 */
	public void generateActorPrecedences ()
	{
		// Actor Precedences
		Iterator<Channel> iterChnnl = graph.getChannels ();
		while (iterChnnl.hasNext ())
		{
			Channel chnnl = iterChnnl.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();			

			HashMap<Integer, List<Integer>> precedenceList = new HashMap<Integer, List<Integer>>();
			// Initialize the List.
			for (int i=0;i<solutions.getSolution (dstActor).returnNumber ();i++)
			{
				List<Integer> tempIntList = new ArrayList<Integer>();
				precedenceList.put (i, tempIntList);
			}

			if (chnnl.getInitialTokens () == 0)
			{
				// Since we have equal repetition count, the precedence is one is to one.
				if (solutions.getSolution (srcActor).returnNumber () == solutions.getSolution (dstActor).returnNumber ())
				{					
					for (int i=0;i<solutions.getSolution (srcActor).returnNumber ();i++)					
						precedenceList.get (i).add (i);
				}
				else
				{
					// Since we have unequal rates and repetition count, we have to schedule accordingly.
					// The Tokens are produced on FIFO order, thus tokens generated by first producer instance
					// should be consumed by first consumer. if extra left, it can be consumed by second consumer
					// if they are less than consumption rate, the first consumer will then consume from
					// second producer instance.
					int repDst = solutions.getSolution (dstActor).returnNumber ();
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
					for (int i=(initialTokens/portRate);i<solutions.getSolution (srcActor).returnNumber ();i++)					
						precedenceList.get (i).add (i-(initialTokens/portRate));
				}
				else
				{
					// With initial tokens, we need to consider multiple iterations.
					int repDst = solutions.getSolution (dstActor).returnNumber ();					
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

			// Actor Precedences 
			// (assert (and (>= (xB 0) (+ (xA 0) dA)) (>= (xB 0) (+ (xA 1) dA))))
			// (assert (and (>= (xB 1) (+ (xA 1) dA)) (>= (xB 1) (+ (xA 2) dA))))

			//Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			//Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();	

			for (int i=0;i<solutions.getSolution (dstActor).returnNumber ();i++)
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
	 * Define all end time SMT variables
	 */
	private void defineEndTimes ()
	{
		// Start Times
		Iterator<Actor> actorIter = graph.getActors ();
		String arguments[] = new String[1];
		arguments[0] = "Int";
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			int repCount = solutions.getSolution (actr).returnNumber ();

			for (int i=0;i<repCount;i++)
			{
				try
				{
					IntExpr id = (IntExpr) ctx.mkAdd (xId (actr.getName (), i), durationId (actr.getName ()));
					// (IntExpr) addVariableDeclaration (SolverPrefixs.endTimePrefix + actr.getName () +"_"+ Integer.toString (i), "Int");
					endTimeDecl.put (SmtVariablePrefixes.endTimePrefix + actr.getName () +"_"+ Integer.toString (i), id);
				} catch (Z3Exception e) { e.printStackTrace (); }
			}			
		}		
	}

	/**
	 * Define all start time SMT variables
	 */
	private void defineStartTimes ()
	{
		// Start Times
		Iterator<Actor> actorIter = graph.getActors ();

		String arguments[] = new String[1];
		arguments[0] = "Int";
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();	
			int repCount = solutions.getSolution (actr).returnNumber ();

			for (int i=0;i<repCount;i++)
			{
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.startTimePrefix + actr.getName () +"_"+ Integer.toString (i), "Int");
				startTimeDecl.put (SmtVariablePrefixes.startTimePrefix + actr.getName () +"_"+ Integer.toString (i), id);
			}
		}		
	}

	/**
	 * Define SMT variables for actor duration
	 */
	private void defineActorDuration ()
	{
		// Define Actor Durations		
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			try
			{
				IntExpr id = ctx.mkInt (actr.getExecTime ()); 
				// (IntExpr) addVariableDeclaration (SolverPrefixs.durationPrefix + actr.getName (), "Int");
				durationDecl.put (SmtVariablePrefixes.durationPrefix + actr.getName (), id);
			} catch (Z3Exception e) { e.printStackTrace (); }
		}	
	}

	/**
	 * Define variables for production rates
	 */
	private void defineProductionRates ()
	{
		// Production Rates of Channels		
		Iterator<Channel> iterChnnl = graph.getChannels ();
		while (iterChnnl.hasNext ())
		{
			Channel chnnl = iterChnnl.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();

			int srcRate = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ()) * chnnl.getTokenSize();
			try
			{
				IntExpr id = ctx.mkInt (srcRate);
				// (IntExpr) addVariableDeclaration (SolverPrefixs.productionRatePrefix + srcActor.getName () + dstActor.getName (), "Int");
				bufferDecl.put (SmtVariablePrefixes.productionRatePrefix + srcActor.getName () + dstActor.getName (), id);
			} catch (Z3Exception e) { e.printStackTrace (); }
		}		
	}

	/**
	 * Define variables for consumption rates
	 */
	private void defineConsumptionRates ()
	{
		// Consumption Rates of Channels		
		Iterator<Channel> iterChnnl = graph.getChannels ();
		while (iterChnnl.hasNext ())
		{
			Channel chnnl = iterChnnl.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();			

			int dstRate = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ()) * chnnl.getTokenSize();
			try
			{
				IntExpr id = ctx.mkInt (dstRate);
				// (IntExpr) addVariableDeclaration (SolverPrefixs.consumptionRatePrefix + srcActor.getName () + dstActor.getName (), "Int");
				bufferDecl.put (SmtVariablePrefixes.consumptionRatePrefix + srcActor.getName () + dstActor.getName (), id);
			} catch (Z3Exception e) { e.printStackTrace (); }
		}		
	}

	/**
	 * Define all the variables for calculating communication buffer size
	 */
	private void defineChannelBuffers ()
	{
		try
		{
			Sort arguments[] = new Sort[1];
			arguments[0] = ctx.getIntSort ();
	
			// Buffer Definitions		
			Iterator<Channel> iterChnnl = graph.getChannels ();
			while (iterChnnl.hasNext ())
			{
				Channel chnnl = iterChnnl.next ();
				Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
				Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
	
				if (bufferAnalysisWithFunctions == true)
				{					
					FuncDecl funcDecl =  ctx.mkFuncDecl (SmtVariablePrefixes.bufferIndexPrefix + srcActor.getName () + dstActor.getName (), arguments, ctx.getIntSort ());
					bufferFuncDecl.put (SmtVariablePrefixes.bufferIndexPrefix + srcActor.getName () + dstActor.getName (), funcDecl);
	
					funcDecl =  ctx.mkFuncDecl (SmtVariablePrefixes.bufferPrefix + srcActor.getName () + dstActor.getName (), arguments, ctx.getIntSort ());
					bufferFuncDecl.put (SmtVariablePrefixes.bufferPrefix + srcActor.getName () + dstActor.getName (), funcDecl);
				}
				else
				{			
					int repCount = solutions.getSolution (srcActor).returnNumber ();
	
					// Generate definitions only for writer of the channel.
					// Since we consider that readers of the channel don't contribute to max channel size.
					// Self-Edge? No need to generate a variable.
					if (srcActor != dstActor)
					{					
						for (int i=0;i<repCount;i++)
						{
							IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.bufferAtPrefix + srcActor.getName () +"_"
															 + Integer.toString (i) + "-" + srcActor.getName () 
															 + dstActor.getName (), "Int");
							bufferDecl.put (SmtVariablePrefixes.bufferAtPrefix + srcActor.getName () +"_"
									 + Integer.toString (i) + "-" + srcActor.getName () 
									 + dstActor.getName (), id);
						}
					}
				}
	
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.maxBufferPrefix + srcActor.getName () + dstActor.getName (), "Int");
				bufferDecl.put (SmtVariablePrefixes.maxBufferPrefix + srcActor.getName () + dstActor.getName (), id);
			}
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/**
	 * Define variables for processor allocation of tasks
	 */
	public void generateCpuDefinitions ()
	{
		String arguments[] = new String[1];
		arguments[0] = "Int";
		// Processor Allocated to each Instance.		
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
					
			int repCount = solutions.getSolution (actr).returnNumber ();			
			for (int i=0;i<repCount;i++)
			{
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.cpuPrefix + actr.getName () + "_" + Integer.toString (i), "Int");
				cpuDecl.put (SmtVariablePrefixes.cpuPrefix + actr.getName () + "_" + Integer.toString (i), id);
			}
			
		}		
	}	
	
	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.ProcessorConstraints#generateProcessorConstraint(int)
	 */
	@Override
	public void generateProcessorConstraint (int numProcessors)
	{
		try
		{
			generateAssertion (ctx.mkLe (totalProcDecl, ctx.mkInt (numProcessors)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}	

	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.LatencyConstraints#generateLatencyConstraint(int)
	 */
	@Override
	public void generateLatencyConstraint (int latencyConstraint)
	{	
		try
		{
			generateAssertion (ctx.mkLe (latencyDecl, ctx.mkInt (latencyConstraint)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.BufferConstraints#generateBufferConstraint(int)
	 */
	@Override
	public void generateBufferConstraint (int bufferConstraint)
	{		
		try
		{
			generateAssertion (ctx.mkLe (totalBufDecl, ctx.mkInt (bufferConstraint)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/* (non-Javadoc)
	 * @see solver.Z3Solver#resetSolver()
	 */
	@Override
	public void resetSolver ()
	{
		pushedContext = false;
		statementCountAfterPush = 0;
		contextStatements.clear ();

		latencyDecl = null;		
		totalBufDecl = null;
		totalProcDecl = null;		
		startTimeDecl.clear ();
		endTimeDecl.clear ();
		durationDecl.clear ();
		cpuDecl.clear ();
		bufferDecl.clear ();
		bufferFuncDecl.clear();
		try { z3Solver.reset (); } catch (Z3Exception e) { e.printStackTrace (); }
	}


	/**
	 * Generate start times, end times and duration of tasks / actors.
	 */
	public void generateActorTimeDefinitions () 
	{		
		// Define Start Times
		defineStartTimes ();

		// Define Actor Durations.
		defineActorDuration ();

		// Define End Times		
		defineEndTimes ();				
	}	

	/**
	 * Generate all the definitions required for buffer size calculation
	 */
	public void generateBufferAnalysisDefinitions () 
	{		
		// Define Production Rates
		defineProductionRates ();

		// Define Consumption Rates
		defineConsumptionRates ();

		// Define Channel Buffer
		defineChannelBuffers ();		
	}

	/**
	 * Generate constraints for lower and upper bounds on processor allocated to tasks.
	 */
	public void generateCpuBounds () 
	{		
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();			
						
			for (int i=0;i<solutions.getSolution (actr).returnNumber ();i++)
			{				
				IntExpr cpuIdxId = cpuId (actr.getName (), i);

				try
				{
					generateAssertion (ctx.mkAnd (ctx.mkGe (cpuIdxId, ctx.mkInt (0)), 
						ctx.mkLt (cpuIdxId, totalProcDecl)));
				} catch (Z3Exception e) { e.printStackTrace (); }
			}			
		}				
	}
	
	/**
	 * Generate lower bound for latency of the schedule
	 */
	protected void minLatencyBound ()
	{
		int maxLatency = 0;
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			maxLatency += (solutions.getSolution (actr).returnNumber () * actr.getExecTime ());
		}
		
		// Assert that Latency >= (Max Latency / No of Processors)
		try { generateAssertion (ctx.mkGe (getLatencyDeclId (), ctx.mkDiv (ctx.mkInt (maxLatency),getProcDeclId ())));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/**
	 * Generate the mutual exclusion constraints
	 */
	public void generateMutualExclusion () 
	{	
		if (mutualExclusionGraphAnalysis == false)
			assertMutualExclusion ();
		else
			assertMutualExclusionWithGraphAnalysis ();
	}
	
	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.LatencyConstraints#getLatency(java.util.Map)
	 */
	@Override
	public int getLatency (Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.latencyPrefix));
	}
	
	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.ProcessorConstraints#getProcessors(java.util.Map)
	 */
	@Override
	public int getProcessors (Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.totalProcPrefix));
	}
	
	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.BufferConstraints#getTotalBufferSize(java.util.Map)
	 */
	@Override
	public int getTotalBufferSize (Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.totalBufferPrefix));
	}
	
	/**
	 * Solve the scheduling problem by using Left-Edge instead of
	 * mutual exclusion.
	 * 
	 * @author Pranav Tendulkar
	 *
	 */
	protected abstract class LeftEdge
	{
		/**
		 * Generate constraints for calculating processor utilization
		 */
		protected void leftEdgeMaxProcUtilization ()
		{
			int totalHsdfActors = hsdf.countActors ();
			int count = 0;
			
			Iterator<Actor> actorIter = graph.getActors ();
			
			IntExpr allStartIds[] = new IntExpr [totalHsdfActors];
			IntExpr allEndIds[] = new IntExpr [totalHsdfActors];
			IntExpr procUtilIds[] = new IntExpr [totalHsdfActors];
			
			actorIter = graph.getActors ();
			while (actorIter.hasNext ())
			{
				Actor actr = actorIter.next ();
				int repCnt = solutions.getSolution (actr).returnNumber ();
				for (int i=0;i<repCnt;i++)
				{
					allStartIds[count] = xId (actr.getName (), i);
					allEndIds[count] = yId (actr.getName (), i);
					procUtilIds[count++] = procUtilId (actr.getName (), i);
				}
			}
			
			try
			{
				// calculate maximum processor utilization - totalProc
				BoolExpr orArgs[] = new BoolExpr [totalHsdfActors];
				BoolExpr andArgs[] = new BoolExpr [totalHsdfActors];
				for (int j=0;j<totalHsdfActors;j++)
				{
					orArgs[j] = ctx.mkEq (getProcDeclId (), procUtilIds[j]);
					andArgs[j] = ctx.mkGe (getProcDeclId (), procUtilIds[j]);
				}
				
				generateAssertion (ctx.mkOr (orArgs));
				generateAssertion (ctx.mkAnd (andArgs));
			} catch (Z3Exception e) { e.printStackTrace (); }
		}
		
		/**
		 * Generate the SMT variable definitions required to calculate the
		 * processor utilization.
		 *  
		 * @param pipelined if pipelined scheduling (true) or non-pipelined (false)
		 */
		protected void generateLeftEdgeCpuDefinitions (boolean pipelined)
		{
			Iterator<Actor> actorIter = graph.getActors ();
			while (actorIter.hasNext ())
			{
				Actor actr = actorIter.next ();
				int repCount = solutions.getSolution (actr).returnNumber ();
				for (int i=0;i<repCount;i++)
				{
					if (((pipelined == false) && (repCount > 1)) || (pipelined == true))
					{
						IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.tasksStartedBeforePrefix + actr.getName () + "_" + Integer.toString (i), "Int");
						cpuDecl.put (SmtVariablePrefixes.tasksStartedBeforePrefix + actr.getName () + "_" + Integer.toString (i), id);
						
						id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.tasksEndedBeforePrefix + actr.getName () + "_" + Integer.toString (i), "Int");
						cpuDecl.put (SmtVariablePrefixes.tasksEndedBeforePrefix + actr.getName () + "_" + Integer.toString (i), id);
					}
					
					IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.procUtilPrefix + actr.getName () + "_" + Integer.toString (i), "Int");
					cpuDecl.put (SmtVariablePrefixes.procUtilPrefix + actr.getName () + "_" + Integer.toString (i), id);
				}
			}
		}
	}
}
