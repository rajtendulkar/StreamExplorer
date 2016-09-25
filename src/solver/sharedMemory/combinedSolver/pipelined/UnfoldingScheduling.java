package solver.sharedMemory.combinedSolver.pipelined;

import exploration.interfaces.oneDim.LatencyConstraints;
import exploration.interfaces.oneDim.PeriodConstraints;
import exploration.interfaces.oneDim.ProcessorConstraints;
import exploration.interfaces.twoDim.PeriodProcConstraints;
import graphanalysis.TransformSDFtoHSDF;
import graphanalysis.properties.GraphAnalysisSdfAndHsdf;

import java.util.*;

import com.microsoft.z3.*;

import solver.SmtVariablePrefixes;
import solver.Z3Solver;
import spdfcore.*;
import spdfcore.stanalys.*;


/**
 * The pipelined problem is solved by unfolding the application graph by a number of
 * times and scheduling it.
 * The constraints for this method are defined in the paper - "Meeting deadlines cheaply"
 * 
 * @author Pranav Tendulkar
 *
 */
public class UnfoldingScheduling extends Z3Solver
implements ProcessorConstraints, LatencyConstraints, PeriodConstraints, PeriodProcConstraints
{
	/**
	 * Application SDF graph
	 */
	protected Graph graph;
	/**
	 * Equivalent Application HSDF graph
	 */
	protected Graph hsdf;
	/**
	 * Number of times the graph should be unfold
	 */
	public int numGraphUnfold=1;
	/**
	 * Processor symmetry
	 */
	public boolean processorSymmetry = false;
	/**
	 * Task Symmetry
	 */
	public boolean graphSymmetry = false;
	/**
	 * Graph analysis object to get trivial properties
	 */
	private GraphAnalysisSdfAndHsdf graphAnalysis;

	// This list contains the actors which are from HSDF graph.
	/**
	 * List of actors without successors.
	 */
	protected List<Actor> lastActorList = null;
	/**
	 * List of actors without predecessors.
	 */
	protected List<Actor> startActorList = null;

	/**
	 * Solutions to application graph
	 */
	protected Solutions solutions;

	/**
	 * SMT variables for start time of tasks.
	 */
	private Map<String, IntExpr> startTimeDecl;
	/**
	 * SMT variables for end time of tasks.
	 */
	private Map<String, IntExpr> endTimeDecl;
	/**
	 * SMT variables for duration of actors
	 */
	private Map<String, IntExpr> durationDecl;
	/**
	 * SMT variables for processor allocated to the tasks.
	 */
	private Map<String, IntExpr> cpuDecl;
	/**
	 * SMT variables for processor symmetry 
	 */
	private Map<String, IntExpr> symmetryDecl;

	/**
	 * SMT variable for Latency calculation of the schedule
	 */
	protected IntExpr latencyDecl;
	/**
	 * SMT variable for Period calculation of the schedule
	 */
	protected IntExpr periodDecl;
	/**
	 * SMT variable for total processors used in the schedule
	 */
	protected IntExpr totalProcDecl;	

	/**
	 * Get the SMT variable for latency calculation of the schedule
	 * @return variable for latency calculation of the schedule
	 */
	public IntExpr getLatencyDeclId () { return latencyDecl; }	

	/**
	 * Get the SMT variable for period calculation of the schedule
	 * 
	 * @return variable for period calculation of the schedule
	 */
	public IntExpr getPeriodDeclId () { return periodDecl; }	

	/**
	 * Get the SMT variable for total processors used in the schedule
	 * 
	 * @return variable for total processors used in the schedule
	 */
	public IntExpr getProcDeclId () { return totalProcDecl; }

	/**
	 * Get SMT variable for start time of a task.
	 * 
	 * @param name name of the task
	 * @return variable for start time of a task
	 */
	IntExpr xId (String name) 		 			 { return startTimeDecl.get (SmtVariablePrefixes.startTimePrefix + name); }

	/**
	 * Get SMT variable for start time of an actor instance.
	 * 
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable for start time of an actor instance
	 */
	IntExpr xId (String name, int index) 		 { return startTimeDecl.get (SmtVariablePrefixes.startTimePrefix + name + "_" + Integer.toString (index)); }

	/**
	 * Get SMT variable for end time of a task.
	 * 
	 * @param name name of the task
	 * @return variable for end time of a task
	 */
	IntExpr yId (String name) 		 			 { return endTimeDecl.get (SmtVariablePrefixes.endTimePrefix + name); }

	/**
	 * Get SMT variable for end time of an actor instance.
	 * 
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable for end time of an actor instance
	 */
	IntExpr yId (String name, int index) 		 { return endTimeDecl.get (SmtVariablePrefixes.endTimePrefix + name + "_" + Integer.toString (index)); }

	/**
	 * Get SMT variable for processor allocated to a task.
	 * 
	 * @param name name of the task
	 * @return variable for processor allocated to a task
	 */
	IntExpr cpuId (String name) 	 			 { return cpuDecl.get (SmtVariablePrefixes.cpuPrefix + name); }

	/**
	 * Get SMT variable for processor allocated to an actor instance
	 * 
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable for processor allocated to an actor instance
	 */
	IntExpr cpuId (String name, int index) 	 	 { return cpuDecl.get (SmtVariablePrefixes.cpuPrefix + name + "_" + Integer.toString (index)); }	

	/**
	 * Get SMT variable for duration of an actor.
	 * 
	 * @param name name of the actor
	 * @return variable for duration of an actor
	 */
	IntExpr durationId (String name)			 { return durationDecl.get (SmtVariablePrefixes.durationPrefix+ name); }

	/**
	 * Get SMT variable for maximum processor index on which a task can run for processor symmetry constraints.
	 * 
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable for maximum processor index on which a task can run
	 */
	IntExpr maxCpuId (String name, int index) 	 { return symmetryDecl.get (SmtVariablePrefixes.maxCpuPrefix+ name+Integer.toString (index)); }

	/**
	 * Build an unfolding solver for pipelined scheduling
	 * 
	 * @param inputGraph application graph SDF
	 */
	public UnfoldingScheduling (Graph inputGraph) 
	{		
		graph = inputGraph;

		TransformSDFtoHSDF toHSDF = new TransformSDFtoHSDF ();
		hsdf = toHSDF.convertSDFtoHSDFWithUniqueChannels (graph);

		GraphExpressions expressions = new GraphExpressions ();
		expressions.parse (graph);
		solutions = new Solutions ();
		solutions.setThrowExceptionFlag (false);
		solutions.solve (graph, expressions);

		graphAnalysis = new GraphAnalysisSdfAndHsdf (graph, solutions, hsdf);
		lastActorList = graphAnalysis.findHsdfEndActors ();

		startTimeDecl = new TreeMap<String, IntExpr>();
		endTimeDecl = new TreeMap<String, IntExpr>();		
		durationDecl = new TreeMap<String, IntExpr>();
		cpuDecl = new TreeMap<String, IntExpr>();
		symmetryDecl = new TreeMap<String, IntExpr>();
	}

	/**
	 * Define the start time SMT variables for all the tasks.
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
			int repCount = solutions.getSolution (actr).returnNumber () * numGraphUnfold;

			for (int i=0;i<repCount;i++)
			{
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.startTimePrefix + actr.getName () +"_"+ Integer.toString (i), "Int");
				startTimeDecl.put (SmtVariablePrefixes.startTimePrefix + actr.getName () +"_"+ Integer.toString (i), id);
			}
		}		
	}

	/**
	 * Define actor duration SMT variables for all the actors
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
	 * Define the end time SMT variables for all the tasks.
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
			int repCount = solutions.getSolution (actr).returnNumber () * numGraphUnfold;

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
	 * Generate the actor start times, end times, and actor durations
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
	 * Define variables for processor allocation of tasks
	 */
	private void generateCpuDefinitions ()
	{
		String arguments[] = new String[1];
		arguments[0] = "Int";
		// Processor Allocated to each Instance.		
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();	

			int repCount = solutions.getSolution (actr).returnNumber () * numGraphUnfold;			
			for (int i=0;i<repCount;i++)
			{
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.cpuPrefix + actr.getName () + "_" + Integer.toString (i), "Int");
				cpuDecl.put (SmtVariablePrefixes.cpuPrefix + actr.getName () + "_" + Integer.toString (i), id);
			}
		}		
	}

	/**
	 * Generate constraints for task precedences
	 */
	private void actorPrecedences ()
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
			for (int i=0;i<solutions.getSolution (dstActor).returnNumber ()*numGraphUnfold;i++)
			{
				List<Integer> tempIntList = new ArrayList<Integer>();
				precedenceList.put (i, tempIntList);
			}

			if (chnnl.getInitialTokens () == 0)
			{
				// Since we have equal repetition count, the precedence is one is to one.
				if (solutions.getSolution (srcActor).returnNumber () == solutions.getSolution (dstActor).returnNumber ())
				{					
					for (int i=0;i<solutions.getSolution (srcActor).returnNumber ()*numGraphUnfold;i++)					
						precedenceList.get (i).add (i);
				}
				else
				{
					// Since we have unequal rates and repetition count, we have to schedule accordingly.
					// The Tokens are produced on FIFO order, thus tokens generated by first producer instance
					// should be consumed by first consumer. if extra left, it can be consumed by second consumer
					// if they are less than consumption rate, the first consumer will then consume from
					// second producer instance.
					int repDst = solutions.getSolution (dstActor).returnNumber ()*numGraphUnfold;
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
					int repDst = solutions.getSolution (dstActor).returnNumber ()*numGraphUnfold;					
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

			for (int i=0;i<solutions.getSolution (dstActor).returnNumber ()*numGraphUnfold;i++)
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
			for (int j=0;j<solutions.getSolution (actr).returnNumber () * numGraphUnfold;j++)
			{
				for (int k=j+1;k<solutions.getSolution (actr).returnNumber () * numGraphUnfold;k++)
				{
					// (assert (=> (= (cpuA 0) (cpuA 1)) (or (>= (xA 0) (yA 1)) (>= (xA 1) (yA 0)))))
					IntExpr cpuJ = cpuId (actr.getName (), j);
					IntExpr cpuK = cpuId (actr.getName (), k);


					IntExpr xJ = xId (actr.getName (), j);
					IntExpr xK = xId (actr.getName (), k);
					IntExpr yJ = yId (actr.getName (), j);
					IntExpr yK = yId (actr.getName (), k);

					try
					{
						// Final Assertion
						generateAssertion (ctx.mkImplies (ctx.mkEq (cpuJ, cpuK), 
								ctx.mkOr (ctx.mkGe (xJ, yK), 
										ctx.mkGe (xK, yJ))));
					} catch (Z3Exception e) { e.printStackTrace (); }
				}				

				for (int k=i+1;k<actrList.size ();k++)
				{
					Actor otherActor = actrList.get (k);
					for (int l=0;l<solutions.getSolution (otherActor).returnNumber () * numGraphUnfold;l++)
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

						try
						{
							// Final Assertion
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
	 * Generate bounds on start and end times with respect to the period.
	 */
	private void periodConstraints ()
	{
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{				
			Actor actr = actorIter.next ();
			int repCount = solutions.getSolution (actr).returnNumber ();

			try
			{
				for (int i=0;i<numGraphUnfold;i++)
				{
					for (int j=0;j<repCount;j++)
					{
						if (i == 0)
						{
							// (assert (and (>= xA_0 0) (<= yA_0 latency)))
							generateAssertion (ctx.mkAnd (ctx.mkGe (xId (actr.getName (), i*repCount + j), ctx.mkInt (0)), 
									ctx.mkLe (yId (actr.getName (), i*repCount + j), getLatencyDeclId ())));
						}
						else if (i == 1)
						{
							// (assert (and (>= xA_1 period) (<= yA_1 (+ latency period))))
							generateAssertion (ctx.mkAnd (ctx.mkGe (xId (actr.getName (), i*repCount + j), getPeriodDeclId ()), 
									ctx.mkLe (yId (actr.getName (), i*repCount + j), ctx.mkAdd (getLatencyDeclId (), getPeriodDeclId ()))));
						}
						else
						{
							// (assert (and (>= xA_1 (* I period)) (<= yA_1 (+ latency (* I period)))))
							ArithExpr mulId = ctx.mkMul (ctx.mkInt (i), getPeriodDeclId ());
							generateAssertion (ctx.mkAnd (ctx.mkGe (xId (actr.getName (), i*repCount + j),mulId), 
									ctx.mkLe (yId (actr.getName (), i*repCount + j), ctx.mkAdd (getLatencyDeclId (), mulId))));
						}
					}
				}
			} catch (Z3Exception e) { e.printStackTrace (); }
		}
	}

	/**
	 * Generate latency calculation of the schedule
	 */
	protected void generateLatencyCalculation ()
	{
		if (lastActorList.size () == 0)
			throw new RuntimeException ("Unable to find the End Actor");

		try
		{
			if (lastActorList.size () == 1)
			{
				Actor actr = lastActorList.get (0);
				String actorName = (actr.getName ()).replaceAll ("_[0-9]$", "");
				int actorInstance = Integer.parseInt (actr.getName ().substring (actr.getName ().indexOf ("_") + 1));
				generateAssertion (ctx.mkEq (latencyDecl, yId (actorName, actorInstance)));			
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

				BoolExpr eq = ctx.mkGe (bufActId0,bufActId1);
				BoolExpr left = ctx.mkEq (latencyDecl, bufActId0);
				BoolExpr right = ctx.mkEq (latencyDecl, bufActId1);

				generateAssertion ((BoolExpr)ctx.mkITE (eq,left, right));												 	
			}
			else
			{			
				BoolExpr orArgs[] = new BoolExpr[lastActorList.size ()];
				BoolExpr andArgs[] = new BoolExpr[lastActorList.size ()];

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
			}
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/**
	 * Generate constraints for time periodicity of the schedule.
	 */
	private void timePeriodicity ()
	{
		if (numGraphUnfold <= 1)
			return; 

		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{				
			Actor actr = actorIter.next ();
			int repCount = solutions.getSolution (actr).returnNumber ();
			for (int i=1;i<numGraphUnfold;i++)
			{
				// (assert (= xA_1 (+ xA_0 period)))
				for (int j=0;j<repCount;j++)
				{
					try
					{
						generateAssertion (ctx.mkEq (xId (actr.getName (), (i * repCount) + j), 
								ctx.mkAdd (xId (actr.getName (), ((i-1) * repCount) + j), getPeriodDeclId ())));
					} catch (Z3Exception e) { e.printStackTrace (); }
				}
			}
		}
	}

	/**
	 * Generate constraints for machine periodicity of the schedule.
	 */
	private void machinePeriodicity ()
	{
		if (numGraphUnfold <= 1)
			return;

		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{				
			Actor actr = actorIter.next ();
			int repCount = solutions.getSolution (actr).returnNumber ();
			for (int i=1;i<numGraphUnfold;i++)
			{
				// (assert (= cpuA_0 cpuA_1))
				for (int j=0;j<repCount;j++)
				{
					try
					{
						generateAssertion (ctx.mkEq (cpuId (actr.getName (), (i * repCount) + j), 
								cpuId (actr.getName (), ((i-1) * repCount) + j)));
					} catch (Z3Exception e) { e.printStackTrace (); }
				}
			}
		}
	}

	/**
	 * Generate constraints for lower and upper bounds on the processor allocated to the tasks.
	 */
	private void assertCpuBounds ()
	{
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();	

			int repCount = solutions.getSolution (actr).returnNumber () * numGraphUnfold;			
			for (int i=0;i<repCount;i++)
			{
				try
				{
					generateAssertion (ctx.mkAnd (ctx.mkGe (cpuId (actr.getName (), i), ctx.mkInt (0)), 
							ctx.mkLt (cpuId (actr.getName (), i), getProcDeclId ())));
				} catch (Z3Exception e) { e.printStackTrace (); }
			}
		}
	}

	/* (non-Javadoc)
	 * @see solver.Z3Solver#resetSolver()
	 */
	@Override
	public void resetSolver ()
	{
		periodDecl = null;
		latencyDecl = null;
		totalProcDecl = null;

		startTimeDecl.clear ();
		endTimeDecl.clear ();		
		durationDecl.clear ();
		cpuDecl.clear ();

		statementCountAfterPush = 0;
		pushedContext = false;
		contextStatements.clear ();

		try { z3Solver.reset (); } catch (Z3Exception e) { e.printStackTrace (); }		
		// ctx.resetSolver (z3Solver);
	}

	/**
	 * Generate task symmetry constraints
	 */
	private void graphSymmetryLexicographic ()
	{
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			int repCnt = solutions.getSolution (actr).returnNumber ();

			for (int i=1;i<repCnt;i++)
				try {
					generateAssertion (ctx.mkLe (xId (actr.getName (), i-1), xId (actr.getName (), i)));
				} catch (Z3Exception e) { e.printStackTrace (); }

			// Note : Very restrictive constraint.
			// It will not allow one iteration to start before previous has finished.
			// for (int i=1;i<numGraphUnfold;i++)
			//	generateAssertion (ctx.mkLe (xId (actr.getName (), (repCnt*i)-1), xId (actr.getName (), (repCnt*i))));				
		}
	}

	/**
	 * Generate processor symmetry constraints
	 */
	private void processorSymmetryConstraints ()
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
	 * Generate definitions for processor symmetry
	 */
	private void generateProcessorSymmetryDefinitions ()
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
	 * Generate a lower bound on the period.
	 */
	private void minPeriodBound ()
	{
		int maxLatency = 0;
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			maxLatency += (solutions.getSolution (actr).returnNumber () * actr.getExecTime ());
		}

		// Assert that Period >= (Sum (exec.times) / No of Processors)
		try { generateAssertion (ctx.mkGe (getPeriodDeclId (), ctx.mkDiv (ctx.mkInt (maxLatency), getProcDeclId ())));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/**
	 * Generate a lower bound on the latency.
	 */
	private void minLatencyBound ()
	{
		int maxLatency = 0;
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			maxLatency += (solutions.getSolution (actr).returnNumber () * actr.getExecTime ());
		}

		// Assert that Latency >= (Max Latency / No of Processors)
		try {generateAssertion (ctx.mkGe (getLatencyDeclId (), ctx.mkDiv (ctx.mkInt (maxLatency),getProcDeclId ())));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	//	private void maxLatencyBound ()
	//	{
	//		int maxLatency = 0;
	//		Iterator<Actor> actrIter = graph.getActors ();
	//		while (actrIter.hasNext ())
	//		{
	//			Actor actr = actrIter.next ();
	//			maxLatency += (solutions.getSolution (actr).returnNumber () * actr.getExecTime ());
	//		}
	//		
	//		// Assert that Latency <= summation of (exec. times of all actors)
	//		generateAssertion (ctx.mkLe (getLatencyDeclId (), ctx.mkInt (maxLatency)));
	//	}

	/**
	 * Upper bound on the latency based on Omega_max
	 */
	private void omegaUnfoldingConstraint ()
	{
		int omegaMax = 0;
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			int distance = graphAnalysis.getMaxDistanceFromSrc (actr);
			if (distance > omegaMax)
				omegaMax = distance;
		}

		// latency <= 2*period*(Omega_max+1)
		try { generateAssertion (ctx.mkLe (getLatencyDeclId (), ctx.mkMul (getPeriodDeclId (), ctx.mkInt ((omegaMax+1)*2))));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/**
	 * Special scheduling constraint for self-edge actors
	 */
	private void selfEdgeActorConstraint ()
	{
		Iterator<Channel> chnnlIter = hsdf.getChannels ();
		while (chnnlIter.hasNext ())
		{
			Channel chnnl = chnnlIter.next ();
			// Check if channel has initial tokens
			int initialTokens = chnnl.getInitialTokens ();
			if (initialTokens > 0)
			{
				Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
				Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();

				// for an edge (U,V) with t initial tokens 
				// Y (U) - X (V) <= t*period 
				try
				{
					if (initialTokens == 1)
						generateAssertion (ctx.mkLe (ctx.mkSub (yId (srcActor.getName ()), xId (dstActor.getName ())), getPeriodDeclId ()));
					else
						generateAssertion (ctx.mkLe (ctx.mkSub (yId (srcActor.getName ()), xId (dstActor.getName ())), ctx.mkMul (ctx.mkInt (initialTokens), getPeriodDeclId ())));
				} catch (Z3Exception e) { e.printStackTrace (); }
			}
		}
	}

	/**
	 * Lower bound on the period of the schedule
	 */
	private void assertMinPeriod ()
	{
		// Period >= max (exec. time of all actors).
		int maxExecTime = Integer.MIN_VALUE;
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			if (actr.getExecTime () > maxExecTime)
				maxExecTime = actr.getExecTime ();
		}
		
		try { generateAssertion (ctx.mkGe (getPeriodDeclId (), ctx.mkInt (maxExecTime)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/**
	 * Non-lazy schedule constraints.
	 * Note: This slows down the solver.
	 */
	@SuppressWarnings("unused")
	private void nonLazynessConstraint()
	{
		Map<String, IntExpr> latestTimeDecl = new HashMap<String, IntExpr>();

		for(Actor actr : hsdf.getActorList())
		{
			IntExpr id = (IntExpr) addVariableDeclaration ("latPred" + actr.getName (), "Int");
			latestTimeDecl.put ("latPred" + actr.getName (), id);
		}

		for(Actor actr : hsdf.getActorList())
		{
			HashSet<Channel> incomingChannels = actr.getChannels(Port.DIR.IN);

			if(incomingChannels.size() == 0)
			{
				try
				{
					generateAssertion(ctx.mkLt(xId(actr.getName()), getPeriodDeclId()));
				} catch (Z3Exception e) { e.printStackTrace(); }
			}
			else
			{
				List<Actor> hsdfPredecessorList = new ArrayList<Actor>();

				for(Channel chnnl : incomingChannels)
				{
					Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
					hsdfPredecessorList.add(srcActor);
				}				

				if (hsdfPredecessorList.size() == 1)
				{
					try
					{
						generateAssertion(ctx.mkLt(ctx.mkSub(xId(actr.getName()), yId(hsdfPredecessorList.get(0).getName())), getPeriodDeclId()));
					} catch (Z3Exception e) { e.printStackTrace(); }
				}
				else
				{
					if(graphSymmetry == true)
					{
						// Eliminate graph symmetry actors.
						for(int i=0;i<hsdfPredecessorList.size();i++)
							for(int j=i+1;j<hsdfPredecessorList.size();j++)
							{								
								String nameA1 = hsdfPredecessorList.get(i).getName();
								String nameA2 = hsdfPredecessorList.get(j).getName();

								if(nameA1.substring (0, nameA1.indexOf ("_")).equals(nameA2.substring (0, nameA2.indexOf ("_"))))
								{
									int index1 = Integer.parseInt(nameA1.substring(nameA1.indexOf("_")+1,nameA1.length()));
									int index2 = Integer.parseInt(nameA2.substring(nameA2.indexOf("_")+1,nameA2.length()));

									if(index1 < index2)
									{
										hsdfPredecessorList.remove(i);
										i--;
										break;
									}
									else if (index1 > index2)
									{
										hsdfPredecessorList.remove(j);
										j--;
									}
									else
									{
										throw new RuntimeException("we have two edges coming from same hsdf actor");
									}

								}
							}
					}

					if(hsdfPredecessorList.size() == 1)
					{
						try
						{
							generateAssertion(ctx.mkLt(ctx.mkSub(xId(actr.getName()), yId(hsdfPredecessorList.get(0).getName())), 
									getPeriodDeclId()));
						} catch (Z3Exception e) { e.printStackTrace(); }
					}
					else
					{
						// now the latest time.
						// latestTimeDecl = max(predecessors)
						IntExpr id = latestTimeDecl.get ("latPred" + actr.getName ());

						BoolExpr[] orArgs =new BoolExpr[hsdfPredecessorList.size()];
						BoolExpr[] andArgs = new BoolExpr[hsdfPredecessorList.size()];

						for(int i=0;i<hsdfPredecessorList.size();i++)
						{
							Actor predActor = hsdfPredecessorList.get(i);

							try
							{
								orArgs[i] = (ctx.mkEq(id, yId(predActor.getName())));
								andArgs[i] = (ctx.mkGe(id, yId(predActor.getName())));
							} catch (Z3Exception e) { e.printStackTrace(); }
						}

						try
						{
							generateAssertion(ctx.mkOr(orArgs));
							generateAssertion(ctx.mkAnd(andArgs));						
							generateAssertion(ctx.mkLt(ctx.mkSub(xId(actr.getName()), id), getPeriodDeclId()));
						} catch (Z3Exception e) { e.printStackTrace(); }
					}
				}					
			}
		}
	}

	/**
	 * Generate all the constraints for pipelined scheduling
	 */
	public void assertPipelineConstraints ()
	{
		// Generate All Definitions.
		periodDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.periodPrefix, "Int");
		latencyDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.latencyPrefix, "Int");
		totalProcDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.totalProcPrefix, "Int");

		generateActorTimeDefinitions ();

		generateCpuDefinitions ();

		if (processorSymmetry == true)
			generateProcessorSymmetryDefinitions ();

		// nonLazynessConstraint();

		periodConstraints ();

		actorPrecedences ();

		assertMutualExclusion ();

		assertCpuBounds ();

		timePeriodicity ();

		machinePeriodicity ();

		if (graphSymmetry == true)
			graphSymmetryLexicographic ();

		if (processorSymmetry == true)
			processorSymmetryConstraints ();

		generateLatencyCalculation ();

		// Assert that Period >= max (exec. times)
		assertMinPeriod ();

		// Assert that Latency >= (Sum (exec.times) / No of Processors)
		minLatencyBound ();

		// Assert that Period >= (Sum (exec.times) / No of Processors)
		minPeriodBound ();

		// latency <= 2*period*(Omega_max+1)
		omegaUnfoldingConstraint ();

		selfEdgeActorConstraint ();

		try{ generateAssertion (ctx.mkGe (getLatencyDeclId (), ctx.mkInt (graphAnalysis.getLongestDelay ())));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}

	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.PeriodConstraints#getPeriod(java.util.Map)
	 */
	@Override
	public int getPeriod (Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.periodPrefix));
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
	 * @see exploration.interfaces.oneDim.PeriodConstraints#generatePeriodConstraint(int)
	 */
	@Override
	public void generatePeriodConstraint (int periodConstraint)
	{
		try
		{
			generateAssertion (ctx.mkEq (periodDecl, ctx.mkInt (periodConstraint)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
}
