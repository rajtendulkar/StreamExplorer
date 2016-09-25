package solver.sharedMemory.combinedSolver.pipelined;

import java.util.*;

import com.microsoft.z3.*;

import exploration.interfaces.oneDim.PeriodConstraints;
import exploration.interfaces.twoDim.PeriodProcConstraints;
import graphanalysis.properties.GraphAnalysisSdfAndHsdf;
import solver.SmtVariablePrefixes;
import solver.sharedMemory.combinedSolver.MutualExclusionSolver;
import spdfcore.*;
import spdfcore.Channel.Link;

/**
 * The pipelined problem is solved using this class by 
 * a Mutual exclusion solver. Different constraints that
 * are used to solve this problem are explained in the
 * Verimag report :  
 * "Strictly Periodic Scheduling of Acyclic Synchronous Dataflow Graphs using SMT Solvers"
 * Verimag Research Report no TR-2014-5
 * 
 * @author Pranav Tendulkar
 *
 */
public class MutExPipelinedScheduling extends MutualExclusionSolver 
	implements PeriodProcConstraints, PeriodConstraints
{	
	// Type I and Type II types for tasks.
	/**
	 * Enable differentiation in the types of tasks, depending 
	 * on yPrime of task is less than xPrime (type I)
	 *  or xPrime of task is less than yPrime (type II)
	 */
	public boolean typeDifferentiateAlgo=false;
	// Do not generate any prime variable calculations.
	/**
	 * Calculate pipelined schedule without using prime 
	 * variables (period locality)
	 */
	public boolean disablePrimes=false;
	/**
	 * Enable Period symmetry
	 */
	public boolean periodSymmetry=false;
	/**
	 * Enable pipeline scheduling using omega.
	 */
	public boolean omegaAnalysis=false;

	/**
	 * SMT variable for calculating period of the schedule.
	 */
	private IntExpr periodDecl;
	/**
	 * SMT variable for calculating kMax.
	 */
	private IntExpr kMaxDecl;
	/**
	 * SMT variable for calculating processor utilization at the start
	 * of the period. This variable is required for left-edge pipelined
	 * scheduling.
	 */
	private IntExpr procUtilAtPeriodStartId;
	/**
	 * SMT k-variables mapped with name
	 */
	private Map<String, IntExpr> kDecl;
	/**
	 * SMT prime variables mapped with name
	 */
	private Map<String, IntExpr> primeDecl;
	/**
	 * SMT variables for buffer calculation 
	 */
	private Map<String, IntExpr> buffPipelinedDecl;
	/**
	 * SMT variables for Left-edge scheduling to decide
	 * the order of task execution.
	 */
	private Map<String, BoolExpr> afterVarDecl;
	/**
	 * SMT period symmetry variables
	 */
	private Map<String, IntExpr> periodSymVarDecl;
	/**
	 * SMT cap variables for Omega analysis
	 */
	private Map<String, IntExpr> capVarDecl;
	/**
	 * Graph analysis object to determine some properties of the graph
	 */
	private GraphAnalysisSdfAndHsdf graphAnalysis;
	
	/**
	 * Build a mutual exclusion pipelined solver
	 * 
	 * @param inputGraph input application graph SDF
	 */
	public MutExPipelinedScheduling (Graph inputGraph) 
	{
		super (inputGraph);
		kDecl = new TreeMap<String, IntExpr>();
		primeDecl = new TreeMap<String, IntExpr>();
		buffPipelinedDecl = new TreeMap<String, IntExpr>();
		afterVarDecl = new TreeMap<String, BoolExpr>();
		periodSymVarDecl = new TreeMap<String, IntExpr>();
		capVarDecl = new TreeMap<String, IntExpr>();
		graphAnalysis = new GraphAnalysisSdfAndHsdf (graph, solutions, hsdf);
	}

	/**
	 * Get SMT variable capX of a task.
	 * 
	 * @param name name of the task
	 * @return variable capX of a task
	 */
	private IntExpr capXId (String name) 		 		 	 { return capVarDecl.get (SmtVariablePrefixes.capXPrefix + name); }
	
	/**
	 * Get SMT variable capY of a task
	 * 
	 * @param name name of the task
	 * @return variable capY of a task
	 */
	private IntExpr capYId (String name) 		 		 	 { return capVarDecl.get (SmtVariablePrefixes.capYPrefix + name); }
	
	/**
	 * Get SMT variable capX predicted of a task.
	 * 
	 * @param name name of the task
	 * @return variable capX predicted of a task.
	 */
	private IntExpr capPredXId (String name) 		 		 { return capVarDecl.get (SmtVariablePrefixes.capPredXPrefix + name); }
	
	/**
	 *  Get SMT variable capX of an actor instance
	 *  
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable capX of an actor instance
	 */
	private IntExpr capXId (String name, int index) 		 		 	 { return capVarDecl.get (SmtVariablePrefixes.capXPrefix + name + "_" + index); }
	
	/**
	 *  Get SMT variable capY of an actor instance
	 *  
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable capY of an actor instance
	 */
	private IntExpr capYId (String name, int index) 		 		 	 { return capVarDecl.get (SmtVariablePrefixes.capYPrefix + name + "_" + index); }
	
	/**
	 * Get SMT variable capX predicted of an actor instance
	 * 
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable capX predicted of an actor instance
	 */
	private IntExpr capPredXId (String name, int index) 		 		 { return capVarDecl.get (SmtVariablePrefixes.capPredXPrefix + name + "_" + index); }
	

	/**
	 * Get SMT variable for period of the schedule.
	 * 
	 * @return variable for period of the schedule
	 */
	public IntExpr getPeriodDeclId () { return periodDecl; }
	
	/**
	 * Get SMT variable for k predicted of a task.
	 * 
	 * @param name name of the task
	 * @return variable for k predicted of a task
	 */
	private IntExpr kPredId (String name) 		 		 	 { return periodSymVarDecl.get (SmtVariablePrefixes.predKVarPrefix + name); }
	
	/**
	 * Get SMT variable for k start of a task.
	 * 
	 * @param name name of the task
	 * @return variable for k start of a task
	 */
	private IntExpr kStartId (String name) 		 		 	 { return kDecl.get (SmtVariablePrefixes.kStartPrefix + name); }
	
	/**
	 * Get SMT variable for k end of a task.
	 * 
	 * @param name name of the task
	 * @return variable for k end of a task
	 */
	private IntExpr kEndId (String name) 					 { return kDecl.get (SmtVariablePrefixes.kEndPrefix + name); }
	
	/**
	 * Get SMT variable for k start of an actor instance.
	 * 
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable for k start of an actor instance
	 */
	private IntExpr kStartId (String name, int index) 		 { return kDecl.get (SmtVariablePrefixes.kStartPrefix + name +"_"+Integer.toString (index)); }
	
	/**
	 * Get SMT variable for k end of an actor instance.
	 * 
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable for k end of an actor instance
	 */
	private IntExpr kEndId (String name, int index) 		 { return kDecl.get (SmtVariablePrefixes.kEndPrefix + name +"_"+Integer.toString (index)); }
	
	/**
	 * Get SMT variable for xPrime of an actor instance. 
	 * 
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable for xPrime of an actor instance
	 */
	private IntExpr startPrimeId (String name, int index) 	 { return primeDecl.get (SmtVariablePrefixes.startPrimePrefix + name +"_" + Integer.toString (index)); }
	
	/**
	 * Get SMT variable for yPrime of an actor instance. 
	 * 
	 * @param name name of the actor
	 * @param index instance id
	 * @return variable for yPrime of an actor instance
	 */
	private IntExpr endPrimeId (String name, int index) 	 { return primeDecl.get (SmtVariablePrefixes.endPrimePrefix + name +"_" + Integer.toString (index)); }
	
	/**
	 * Get SMT variable for initial tokens on a channel
	 * 
	 * @param name name of the channel
	 * @return variable for initial tokens on a channel
	 */
	private IntExpr initialTokenId (String name) 		 	 { return buffPipelinedDecl.get (SmtVariablePrefixes.initialTokenPrefix + name); }
	
	/**
	 * Get SMT variable for kMax of the schedule.
	 * 
	 * @return variable for kMax of the schedule
	 */
	private IntExpr getKmaxDeclId () 						 { return kMaxDecl; }
	
	/**
	 * Get SMT boolean variable for order between the actor1 and actor2
	 * 
	 * @param name1 name of actor1
	 * @param index1 instance id of actor1
	 * @param name2 name of actor2
	 * @param index2 instance id of actor2
	 * @return boolean variable for order between the actor1 and actor2
	 */
	BoolExpr afterVariableId (String name1, int index1, String name2, int index2) 
	{ 
		return afterVarDecl.get (SmtVariablePrefixes.afterVarPrefix + name1+Integer.toString (index1)+"_"+name2+Integer.toString (index2)); 
	}
	
	/**
	 * Generate constraints to calculate the initial tokens in the channel at the start of the period.
	 * 
	 * @param srcActor source actor of the channel
	 * @param dstActor sink actor of the channel
	 * @param prodRate token production rate
	 * @param consRate token consumption rate
	 * @param srcRepCount source actor repetition count
	 * @param dstRepCount destination actor repetition count
	 */
	private void generateInitialTokensCalculation (String srcActor, String dstActor, int prodRate, int consRate, int srcRepCount, int dstRepCount)
	{
		// (assert (= initTokensAB (- (* 2 (- kmax kxA_0)) (* 1 (+ (- kmax kyB_0) (- kmax kyB_1))))))
		// (assert (= initTokensBC (- (* 1 (+ (- kmax kxB_0) (- kmax kxB_1))) (* 2 (- kmax kyC_0)))))
		
		try
		{
			IntExpr initTokenId = initialTokenId (srcActor+dstActor);
			IntExpr kMaxId = getKmaxDeclId ();
			ArithExpr prodId = null;
			ArithExpr consId = null;
			
			if (srcRepCount == 1)
				prodId = ctx.mkMul (ctx.mkInt (prodRate), ctx.mkSub (kMaxId, kStartId (srcActor, 0)));
			else
			{
				ArithExpr addArgs[] = new ArithExpr[srcRepCount];
				for (int i=0;i<srcRepCount;i++)
					addArgs[i] = ctx.mkSub (kMaxId, kStartId (srcActor, i));
				prodId = ctx.mkMul (ctx.mkInt (prodRate), ctx.mkAdd (addArgs));
			}
			 
			if (dstRepCount == 1)
				consId = ctx.mkMul (ctx.mkInt (consRate), ctx.mkSub (kMaxId, kEndId (dstActor, 0)));
			else
			{
				ArithExpr addArgs[] = new ArithExpr[dstRepCount];
				for (int i=0;i<dstRepCount;i++)
					addArgs[i] = ctx.mkSub (kMaxId, kEndId (dstActor, i));
				consId = ctx.mkMul (ctx.mkInt (consRate), ctx.mkAdd (addArgs));
			}
			
			generateAssertion (ctx.mkEq (initTokenId, ctx.mkSub (prodId, consId)));
		} catch (Z3Exception e) { e.printStackTrace (); }		
	}
	
	/**
	 * Generate constraints for calculation of the channel buffer size when the producer of the channel executes.
	 * 
	 * @param srcActor source actor of the channel
	 * @param dstActor sink actor of the channel
	 * @param prodRate token production rate
	 * @param consRate token consumption rate
	 * @param srcRepCount source actor repetition count
	 * @param dstRepCount destination actor repetition count
	 */
	private void generateBufferAtProducerCalculation (String srcActor, String dstActor, int prodRate, int consRate, int srcRepCount, int dstRepCount)
	{
		try
		{
			ArithExpr initTokenId = ctx.mkAdd (initialTokenId (srcActor+dstActor), ctx.mkInt (prodRate));
			ArithExpr leftId = null;
			ArithExpr rightId = null;
		
			// (assert (= buffAtA_0-AB (- (+ initTokensAB 2) (+ (if (> yprimeB_0 xprimeA_0) 0 1) (if (> yprimeB_1 xprimeA_0) 0 1)))))
			// (assert (= buffAtB_0-BC (- (+ (+ 1 initTokensBC) (if (> xprimeB_1 xprimeB_0) 0 1)) (if (> yprimeC_0 xprimeB_0) 0 2))))
			
			for (int i=0;i<srcRepCount;i++)
			{
				if (srcRepCount > 1)
				{
					int count = 0;
					ArithExpr addArgs[] = new ArithExpr[srcRepCount];
					addArgs[count++] = initTokenId;
					
					for (int j=0;j<srcRepCount;j++)
					{
						if (i == j)
							continue;
						addArgs[count++] = (ArithExpr) ctx.mkITE (ctx.mkGt (startPrimeId (srcActor, j), startPrimeId (srcActor, i)), 
														ctx.mkInt (0), ctx.mkInt (prodRate));
					}
					leftId = ctx.mkAdd (addArgs);
				}
				else
					leftId = initTokenId;
				
				if (dstRepCount > 1)
				{
					ArithExpr addArgs[] = new ArithExpr[dstRepCount];
					for (int j=0;j<dstRepCount;j++)
					{
						addArgs[j] = (ArithExpr) ctx.mkITE (ctx.mkGt (endPrimeId (dstActor, j), startPrimeId (srcActor, i)), 
												ctx.mkInt (0), ctx.mkInt (consRate));
					}
					rightId = ctx.mkAdd (addArgs);
				}
				else
					rightId = (ArithExpr) ctx.mkITE (ctx.mkGt (endPrimeId (dstActor, 0), startPrimeId (srcActor, i)), 
							ctx.mkInt (0), ctx.mkInt (consRate));
					
				generateAssertion (ctx.mkEq (bufferAtId (srcActor, dstActor, i), ctx.mkSub (leftId, rightId)));
			}
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * Generate constraints to calculate maximum buffer size of a channel.
	 * 
	 * @param srcActor source actor of the channel
	 * @param dstActor sink actor of the channel
	 * @param srcRepCount source actor repetition count
	 */
	private void generateMaxBufferCalculation (String srcActor, String dstActor, int srcRepCount)
	{
		// (assert (or (= maxBufBC buffAtB_0-BC) (= maxBufBC buffAtB_1-BC)))
		// (assert (and (>= maxBufBC buffAtB_0-BC) (>= maxBufBC buffAtB_1-BC)))
		try
		{
			IntExpr maxBuffId = maxBufferId (srcActor, dstActor);
			if (srcRepCount == 1)
			{
				generateAssertion (ctx.mkEq (maxBuffId, bufferAtId (srcActor, dstActor, 0)));
			}
			else
			{
				BoolExpr orArgs[] = new BoolExpr[srcRepCount];
				BoolExpr andArgs[] = new BoolExpr[srcRepCount];
				
				for (int i=0;i<srcRepCount;i++)
				{
					orArgs[i] = ctx.mkEq (maxBuffId, bufferAtId (srcActor, dstActor, i));
					andArgs[i] = ctx.mkGe (maxBuffId, bufferAtId (srcActor, dstActor, i));
				}
				
				generateAssertion (ctx.mkOr (orArgs));
				generateAssertion (ctx.mkAnd (andArgs));
			}
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * Generate constraints for lower and upper bound on the channel.
	 * 
	 * @param srcActor source actor of the channel
	 * @param dstActor sink actor of the channel
	 * @param prodRate token production rate
	 */
	private void generateBufferBounds (String srcActor, String dstActor, int prodRate)
	{
		// (assert (and (> maxBufAB 0) (<= maxBufAB (* 2 (+ 1 kmax)))))
		IntExpr maxBuffId = maxBufferId (srcActor, dstActor);
		BoolExpr andArgs[] = new BoolExpr[2];
		try
		{
			andArgs[0] = ctx.mkGt (maxBuffId, ctx.mkInt (0));
			andArgs[1] = ctx.mkLe (maxBuffId, 
							ctx.mkMul (ctx.mkInt (prodRate), 
							ctx.mkAdd (ctx.mkInt (1), getKmaxDeclId ())));
			generateAssertion (ctx.mkAnd (andArgs));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * Generate all the constraints required for calculation
	 * of the buffer size in a pipelined schedule.
	 */
	public void generateBufferCalculationsPipelined () 
	{
		int count=0;
		ArithExpr addArgs[] = new ArithExpr[graph.countChannels ()];
		
		Iterator<Channel> chnnlIter = graph.getChannels ();
		while (chnnlIter.hasNext ())
		{
			Channel chnnl = chnnlIter.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
			int srcRepCount = solutions.getSolution (srcActor).returnNumber ();
			int dstRepCount = solutions.getSolution (dstActor).returnNumber ();
			int prodRate = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
			int consRate = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());
			int tokenSize = chnnl.getTokenSize();

			// TODO: We have not yet thought about initial tokens on SDF channels.
			// Initial Tokens Calculation
			generateInitialTokensCalculation (srcActor.getName (), dstActor.getName (), prodRate*tokenSize, consRate*tokenSize, srcRepCount, dstRepCount);
			
			// Buffer Calculation
			generateBufferAtProducerCalculation (srcActor.getName (), dstActor.getName (), prodRate*tokenSize, consRate*tokenSize, srcRepCount, dstRepCount);
			
			// Max Buffer Calculation
			generateMaxBufferCalculation (srcActor.getName (), dstActor.getName (), srcRepCount);
			
			// Bounds on Max Buffer
			generateBufferBounds (srcActor.getName (), dstActor.getName (), prodRate*tokenSize);
			
			addArgs[count++] = maxBufferId (srcActor.getName (), dstActor.getName ());
		}
		
		try
		{
			if (count == 1)
				generateAssertion (ctx.mkEq (getBufDeclId (), addArgs[0]));
			else
				generateAssertion (ctx.mkEq (getBufDeclId (), ctx.mkAdd (addArgs)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/* (non-Javadoc)
	 * @see solver.sharedMemory.combinedSolver.MutualExclusionSolver#generatePeriodConstraint(int)
	 */
	@Override
	public void generatePeriodConstraint (int periodConstraint)
	{		
		try
		{
			generateAssertion (ctx.mkEq (periodDecl, ctx.mkInt (periodConstraint)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/* (non-Javadoc)
	 * @see solver.sharedMemory.combinedSolver.MutualExclusionSolver#resetSolver()
	 */
	@Override
	public void resetSolver ()
	{
		super.resetSolver ();
		periodDecl = null;
		kDecl.clear ();
		kMaxDecl = null;
		primeDecl.clear ();
		periodSymVarDecl.clear ();
		capVarDecl.clear ();
	}
	
	/* (non-Javadoc)
	 * @see exploration.interfaces.oneDim.PeriodConstraints#getPeriod(java.util.Map)
	 */
	@Override
	public int getPeriod (Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.periodPrefix));
	}
	
	//
	/**
	 * Generate constraints to contain an actor inside a period.
	 * 
	 * Note : This constraint restricts the actors of previous period 
	 * overtaking the actors of next period on the same processor.
	 */
	private void assertNonOverlapPeriodConstraint ()
	{
		// (assert (=> (= cpuA_0 cpuB_0) (and (<= (+ xA_0 1) (+ xB_0 period)) (<= (+ xB_0 2) (+ xA_0 period)))))
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
				IntExpr cpuJ = cpuId (actr.getName (),j);
				
				IntExpr xJ = xId (actr.getName (), j);
				IntExpr yJ = yId (actr.getName (), j);
								
				for (int k=j+1;k<solutions.getSolution (actr).returnNumber ();k++)
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
					
					for (int l=0;l<solutions.getSolution (otherActor).returnNumber ();l++)
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
	
	/**
	 * Define variables required for initial tokens of all the channels.
	 */
	private void defineInitialTokens ()
	{
		// Production Rates of Channels		
		Iterator<Channel> iterChnnl = graph.getChannels ();
		while (iterChnnl.hasNext ())
		{
			Channel chnnl = iterChnnl.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
			
			IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.initialTokenPrefix + srcActor.getName () + dstActor.getName (), "Int");
			buffPipelinedDecl.put (SmtVariablePrefixes.initialTokenPrefix + srcActor.getName () + dstActor.getName (), id);			
		}
	}
	
	/**
	 * Generate a constraint for lower bound on the 
	 * period.
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
		try
		{
			generateAssertion (ctx.mkGe (getPeriodDeclId (), ctx.mkInt (maxExecTime)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * Define all the K-variables required for period calculation
	 */
	private void defineKVariables ()
	{
		// Start Times
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();	
			int repCount = solutions.getSolution (actr).returnNumber ();

			for (int i=0;i<repCount;i++)
			{
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.kStartPrefix + actr.getName () +"_"+ Integer.toString (i), "Int");
				kDecl.put (SmtVariablePrefixes.kStartPrefix + actr.getName () +"_"+ Integer.toString (i), id);
				
				id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.kEndPrefix + actr.getName () +"_"+ Integer.toString (i), "Int");
				kDecl.put (SmtVariablePrefixes.kEndPrefix + actr.getName () +"_"+ Integer.toString (i), id);
			}
		}
	}
	
	/**
	 * Define all the prime variables (xPrime and yPrime) for scheduling.
	 */
	private void definePrimeVariables ()
	{
		// Start Times
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();	
			int repCount = solutions.getSolution (actr).returnNumber ();

			for (int i=0;i<repCount;i++)
			{
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.startPrimePrefix + actr.getName () +"_"+ Integer.toString (i), "Int");
				primeDecl.put (SmtVariablePrefixes.startPrimePrefix + actr.getName () +"_"+ Integer.toString (i), id);
				
				id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.endPrimePrefix + actr.getName () +"_"+ Integer.toString (i), "Int");
				primeDecl.put (SmtVariablePrefixes.endPrimePrefix + actr.getName () +"_"+ Integer.toString (i), id);
			}
		}
	}
	
	/**
	 * Generate SMT constraints for Prime Variable Calculations
	 */
	private void generatePrimeVariableCalculation ()
	{
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();	
			int repCount = solutions.getSolution (actr).returnNumber ();

			for (int i=0;i<repCount;i++)
			{
				try
				{
					// (assert (= xprimeA_0 (- xA_0 (* period kxA_0))))
					generateAssertion (ctx.mkEq (startPrimeId (actr.getName (), i), 
						ctx.mkSub (xId (actr.getName (), i), 
								ctx.mkMul (getPeriodDeclId (), kStartId (actr.getName (), i)))));
					
					// (assert (= yprimeA_0 (- yA_0 (* period kyA_0))))
					generateAssertion (ctx.mkEq (endPrimeId (actr.getName (), i), 
							ctx.mkSub (yId (actr.getName (), i), 
									ctx.mkMul (getPeriodDeclId (), kEndId (actr.getName (), i)))));
				} catch (Z3Exception e) { e.printStackTrace (); }				
			}
		}
	}
	
	/**
	 * Generate SMT constraints for K-Variable Calculations.
	 */
	private void generateKvariableCalculations ()
	{
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();	
			int repCount = solutions.getSolution (actr).returnNumber ();

			for (int i=0;i<repCount;i++)
			{
				try
				{
					// (assert (= kxA_0 (div xA_0 period)))
					generateAssertion (ctx.mkEq (kStartId (actr.getName (), i), 
								ctx.mkDiv (xId (actr.getName (), i), getPeriodDeclId ())));
					
					// (assert (= kyA_0 (div yA_0 period)))
					generateAssertion (ctx.mkEq (kEndId (actr.getName (), i), 
							ctx.mkDiv (yId (actr.getName (), i), getPeriodDeclId ())));
				} catch (Z3Exception e) { e.printStackTrace (); }
			}
		}
	}
	
//	private void assertMaxLatency ()
//	{
//		// Latency is less than sum of exec. time of all actors.
//		// (assert (<= latency 4
//		int totalExecTime = 0;
//		Iterator<Actor> actrIter = graph.getActors ();
//		while (actrIter.hasNext ())
//		{
//			Actor actr = actrIter.next ();
//			totalExecTime += (solutions.getSolution (actr).returnNumber () * actr.getExecTime ());
//		}
//		
//		generateAssertion (ctx.mkLe (getLatencyDeclId (), ctx.mkInt (totalExecTime)));
//	}
	
	/**
	 * Generate SMT constraint for calculation of Kmax variable.
	 * Kmax = (sum of execution times of all tasks) / period.  
	 */
	private void assertMinKmax ()
	{
		int totalExecTime = 0;
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			totalExecTime += (solutions.getSolution (actr).returnNumber () * actr.getExecTime ());
		}
		
		try
		{
			// (assert (= kmax (div 4 period)))
			generateAssertion (ctx.mkEq (getKmaxDeclId (), ctx.mkDiv (ctx.mkInt (totalExecTime), getPeriodDeclId ())));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * Generate a SMT constraint for lower bound on Period 
	 * 
	 * Period >= (Sum exec.times) / (No of Processors)
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
		} catch (Z3Exception e) { e.printStackTrace(); }
	}
	
	/**
	 * Generate a constraint on Latency depending on Omega_max value.
	 * 
	 * latency <= 2*period*(Omega_max+1)
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
		} catch (Z3Exception e) { e.printStackTrace(); }
	}
	
	/**
	 * Generate SMT constraints for scheduling self-edge actors
	 * (end time - start time) <= (initial tokens * period)
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
				} catch (Z3Exception e) { e.printStackTrace(); }
			}
		}
	}
	
	/**
	 * Generate constraints for pipelined scheduling using period symmetry constraints. 
	 */
	private void testPeriodSymmetry ()
	{
		if ((periodSymmetry == true) && (disablePrimes == true))
			throw new RuntimeException ("Enable Prime Variables for Period symmetry.");
	
		kMaxDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.kMaxPrefix, "Int");		
		
		PeriodSymmetry periodSym = null;
		periodSym = new PeriodSymmetry ();
		periodSym.definePeriodSymVariables ();
		
		definePrimeVariables ();
		defineKVariables ();
		
		if (processorSymmetry == true)
			generateProcessorSymmetryDefinitions ();	
		
		generateCpuDefinitions ();
		
		periodSym.periodSymmetryConstraints ();
		
		assertNonOverlapPeriodConstraint ();		
		generateCpuBounds ();		
		generateMutualExclusion ();
		
		// Prime Variable Calculations
		generatePrimeVariableCalculation ();
		
		// K-Variable Calculations.
		generateKvariableCalculations ();
		
		// Processor Symmetry constraints.
		if (processorSymmetry == true)
			processorSymmetryConstraints ();

		assertMinKmax ();
	}

	/**
	 * Generate constraints for pipelined scheduling using by disabling prime variables
	 * This is also called as Period locality in our report.
	 */
	private void testDisablePrime ()
	{		
		if (processorSymmetry == true)
			generateProcessorSymmetryDefinitions ();	
		
		generateCpuDefinitions ();
		
		assertNonOverlapPeriodConstraint ();		
		generateCpuBounds ();		
		generateMutualExclusion ();
		
		// Processor Symmetry constraints.
		if (processorSymmetry == true)
			processorSymmetryConstraints ();
	}
	
	/**
	 * Generate constraints for pipelined scheduling using Type constraints.
	 * The constraints will contain Type I and Type II variables.
	 *  This is also called as Modulo scheduling in our report.
	 */
	private void testTypeConstraints ()
	{
		if ((disablePrimes == true) && (typeDifferentiateAlgo == true))
			throw new RuntimeException ("Currently we need prime variables for type constraints.");
		
		kMaxDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.kMaxPrefix, "Int");
		
		TypeConstraints typeConstraints=null;
		typeConstraints = new TypeConstraints ();
		typeConstraints.defineTypeVariables ();
		
		if (processorSymmetry == true)
			generateProcessorSymmetryDefinitions ();	
		
		definePrimeVariables ();
		defineKVariables ();
		generateCpuDefinitions ();
		
		// Type constraints
		typeConstraints.typeMutexConstraints ();
		
		// Prime Variable Calculations
		generatePrimeVariableCalculation ();
		
		// K-Variable Calculations.
		generateKvariableCalculations ();
		
		// Processor Symmetry constraints.
		if (processorSymmetry == true)
			processorSymmetryConstraints ();

		assertMinKmax ();
	}
	
	/**
	 * Generate constraints for pipelined scheduling using Left-edge method of solving problem. 
	 */
	private void testLeftEdgeConstraints ()
	{
		kMaxDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.kMaxPrefix, "Int");
		
		LeftEdgePipelined leftEdge = null;
		
		definePrimeVariables ();
		defineKVariables ();
		
		leftEdge = new LeftEdgePipelined ();
		leftEdge.leftEdgeDefinitions ();
		
		leftEdge.generateLeftEdgePipelinedConstraints ();
		
		// Prime Variable Calculations
		generatePrimeVariableCalculation ();
		
		// K-Variable Calculations.
		generateKvariableCalculations ();
		
		assertMinKmax ();
	}
		
	/**
	 * Generate constraints for pipelined scheduling using Omega analysis.
	 * This is also called as Modulo Scheduling with Difference Logic in our report.
	 */
	private void testOmegaAnalysis ()
	{
		OmegaAnalysis omegaAnalysis = new OmegaAnalysis ();
		TypeConstraints typeConstraints=null;
		typeConstraints = new TypeConstraints ();
		typeConstraints.defineTypeVariables ();
		
		if (processorSymmetry == true)
			generateProcessorSymmetryDefinitions ();	
		
		definePrimeVariables ();
		
		omegaAnalysis.defineCapVariables ();
		generateCpuDefinitions ();
		
		// Processor Symmetry constraints.
		if (processorSymmetry == true)
			processorSymmetryConstraints ();
		
		// Type constraints
		typeConstraints.typeMutexConstraints ();
		
		omegaAnalysis.capPrimeCalculation ();
		omegaAnalysis.capVariableCalculation ();
		omegaAnalysis.capPredCalculations ();
		omegaAnalysis.capPeriodCalculation ();
		
		omegaAnalysis.xPrimeBounds ();
		
		generateCpuBounds ();
	}
	
	/**
	 *  Generate constraints for pipelined scheduling using Prime and K variables.
	 */
	private void testPlainConstraints ()
	{
		kMaxDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.kMaxPrefix, "Int");

		definePrimeVariables ();
		defineKVariables ();
		generateCpuDefinitions ();
		
		if (processorSymmetry == true)
			generateProcessorSymmetryDefinitions ();
		
		// Cpu Mutual Exclusion Constraints
		assertNonOverlapPeriodConstraint ();		
		generateCpuBounds ();		
		generateMutualExclusion ();
		
		// Prime Variable Calculations
		generatePrimeVariableCalculation ();
		
		// K-Variable Calculations.
		generateKvariableCalculations ();

		// Processor Symmetry constraints.
		if (processorSymmetry == true)
			processorSymmetryConstraints ();
		
		assertMinKmax ();
	}
	
	/* (non-Javadoc)
	 * @see solver.sharedMemory.combinedSolver.MutualExclusionSolver#assertPipelineConstraints()
	 */
	@Override
	public void assertPipelineConstraints ()
	{		
		// Generate All Definitions.
		periodDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.periodPrefix, "Int");
		latencyDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.latencyPrefix, "Int");
		totalProcDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.totalProcPrefix, "Int");

		if (bufferAnalysis == true)
		{
			totalBufDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.totalBufferPrefix, "Int");		
			generateBufferAnalysisDefinitions ();
			// Initial tokens are required in pipelined scheduling seperately.
			if (bufferAnalysisWithFunctions == false)			
				defineInitialTokens ();
		}
		
		// Actor start times, duration etc.
		generateActorTimeDefinitions();
		
		if ((disablePrimes == true) && (bufferAnalysis == true))
			throw new RuntimeException ("Currently we need prime variables for buffer calculations. Please enable Primes.");			
		
		if (periodSymmetry == true)
			testPeriodSymmetry ();
		else if (leftEdgeAlgorithm == true)
			testLeftEdgeConstraints ();
		else if (disablePrimes == true)
			testDisablePrime ();
		else if (typeDifferentiateAlgo == true)
			testTypeConstraints ();
		else if (omegaAnalysis == true)
			testOmegaAnalysis ();
		else
			testPlainConstraints ();

		// All common constraints
		
		// Actor Precedences
		generateActorPrecedences ();
		
		// Generate All Constraints
		assertStartTimeBounds ();
		
		// Lexicographic graph symmetry constraints.
		if (graphSymmetry == true)	
			graphSymmetryLexicographic ();
		
		// Latency Calculation
		generateLatencyCalculation ();
		
		// Assert that Period >= min (exec. times)
		assertMinPeriod ();
		
		// Assert that Latency >= (Sum (exec.times) / No of Processors)
		minLatencyBound ();
		
		// Assert that Period >= (Sum (exec.times) / No of Processors)
		minPeriodBound ();
		
		omegaUnfoldingConstraint ();
		
		selfEdgeActorConstraint ();
		
		try { generateAssertion (ctx.mkGe (getLatencyDeclId (), ctx.mkInt (graphAnalysis.getLongestDelay ())));
		} catch (Z3Exception e) { e.printStackTrace(); }
		// System.out.println ("Longest Delay : " + graphAnalysis.getLongestDelay ());
	
		// Buffer Calculation Constraints.
		if (bufferAnalysis == true)
			generateBufferCalculationsPipelined ();			
	}
	
	/**
	 * Class to generate variables and constraints for pipelined scheduling
	 * using Omega Analysis.
	 * This is also called as Modulo Scheduling with Difference Logic in our report.
	 * 
	 * @author Pranav Tendulkar
	 *
	 */
	private class OmegaAnalysis
	{
		/**
		 * Generate SMT CAP variables for all the tasks. 
		 */
		public void defineCapVariables ()
		{
			Iterator<Actor> actrIter = hsdf.getActors ();
			while (actrIter.hasNext ())
			{
				Actor actr = actrIter.next ();
				IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.capXPrefix + actr.getName (), "Int");
				capVarDecl.put (SmtVariablePrefixes.capXPrefix + actr.getName (), id);
				
				id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.capYPrefix + actr.getName (), "Int");
				capVarDecl.put (SmtVariablePrefixes.capYPrefix + actr.getName (), id);
				
				if (actr.numIncomingLinks () != 0)
				{
					id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.capPredXPrefix + actr.getName (), "Int");
					capVarDecl.put (SmtVariablePrefixes.capPredXPrefix + actr.getName (), id);
				}
			}
		}
		
		/**
		 * Generate SMT constraints to calculate the values of capX, capPred variables.
		 */
		public void capPredCalculations ()
		{
			Iterator<Actor> actrIter = hsdf.getActors ();
			while (actrIter.hasNext ())
			{
				Actor actr = actrIter.next ();
				String actrName = actr.getName ();
				
				if (actr.numIncomingLinks () == 0)
				{
					// Source actor with no predecessors
					// (assert (= capXA_0 0))
						try 
						{
							generateAssertion (ctx.mkEq (capXId (actrName), ctx.mkInt (0)));
						} catch (Z3Exception e) { e.printStackTrace (); }
				}
				else if (actr.numIncomingLinks () == 1)
				{
					// Only one predecessor
					// (assert (= capPredXB_0 capYA_0
					List<Actor> incomingActrs = new ArrayList<Actor>(graphAnalysis.getImmediatelyConnectedActors(actr, Port.DIR.IN));
					if (incomingActrs.size () != 1)
						throw new RuntimeException ("The size of the list of incoming actors expected 1 and found : " + incomingActrs.size ());
					try 
					{ 
						generateAssertion (ctx.mkEq (capPredXId (actrName), capYId (incomingActrs.get (0).getName ())));
					} catch (Z3Exception e) { e.printStackTrace (); }
				}
				else
				{
					// With more than 2 predecessors, we should calculate the max.
					// (assert (or (= capPredXD_0 capYB_0) (= capPredXD_0 capYC_0)))
					// (assert (and (>= capPredXD_0 capYB_0) (>= capPredXD_0 capYC_0)))
					List<Actor> incomingActrs = new ArrayList<Actor>(graphAnalysis.getImmediatelyConnectedActors(actr, Port.DIR.IN));
					
					if (incomingActrs.size () < 2)
						throw new RuntimeException ("The size of the list of incoming actors expected at least 2 and found : " + incomingActrs.size ());
					BoolExpr andArgs[] = new BoolExpr [incomingActrs.size ()];
					BoolExpr orArgs[]  = new BoolExpr [incomingActrs.size ()];
					for (int i=0;i<incomingActrs.size ();i++)
					{
						try
						{
							orArgs[i]  = ctx.mkEq (capPredXId (actrName), capYId (incomingActrs.get (i).getName ()));
							andArgs[i] = ctx.mkGe (capPredXId (actrName), capYId (incomingActrs.get (i).getName ()));
						} catch (Z3Exception e) { e.printStackTrace (); }
					}
					
					try
					{
						generateAssertion (ctx.mkOr (orArgs));
						generateAssertion (ctx.mkAnd (andArgs));
					} catch (Z3Exception e) { e.printStackTrace (); }
				}			
			}
		}
		
		/**
		 * Generate SMT constraints for calculating capX and capY variables depending on 
		 * xPrime and yPrime of the tasks.
		 */
		public void capVariableCalculation ()
		{
			// (assert (=> (<  (+ xPrimeA_0 durationA) period) (and (= capYA_0 capXA_0) (= yPrimeA_0 (+ xPrimeA_0 durationA)))))
			// (assert (=> (>= (+ xPrimeA_0 durationA) period) (and (= capYA_0 (+ capXA_0 period)) (= yPrimeA_0 (- (+ xPrimeA_0 durationA) period)))))
			
			Iterator<Actor> actrIter = graph.getActors ();
			while (actrIter.hasNext ())
			{
				Actor actr = actrIter.next ();
				String actrName = actr.getName ();
				int repCount = solutions.getSolution (actr).returnNumber ();
				for (int i=0;i<repCount;i++)
				{
					try
					{
						BoolExpr ifId = ctx.mkLt (ctx.mkAdd (startPrimeId (actrName, i), durationId (actrName)), getPeriodDeclId ());
						BoolExpr thenId = ctx.mkAnd (ctx.mkEq (capYId (actrName, i), capXId (actrName, i)), 
								ctx.mkEq (endPrimeId (actrName, i), ctx.mkAdd (startPrimeId (actrName, i), durationId (actrName))));
						
						generateAssertion (ctx.mkImplies (ifId, thenId));
						
						ifId = ctx.mkGe (ctx.mkAdd (startPrimeId (actrName, i), durationId (actrName)), getPeriodDeclId ());
						thenId = ctx.mkAnd (ctx.mkEq (capYId (actrName, i), ctx.mkAdd (capXId (actrName, i), getPeriodDeclId ())), 
								ctx.mkEq (endPrimeId (actrName, i), ctx.mkSub (ctx.mkAdd (startPrimeId (actrName, i), durationId (actrName)), getPeriodDeclId ())));
						
						generateAssertion (ctx.mkImplies (ifId, thenId));
					} catch (Z3Exception e) { e.printStackTrace (); }
				}
			}
		}
		
		/**
		 * Generate SMT constraints for calculating xPrime variables
		 * depending on capX variables.
		 */
		public void capPrimeCalculation ()
		{
			// omega is the max distance between source and this actor.
			// (assert (=> (= capXB_0 0) (= xPrimeB_0 xB_0)))
			// (assert (=> (= capXB_0 period) (= xPrimeB_0 (- xB_0 period))))
			// (assert (=> (= capXB_0 (* 2 period)) (= xPrimeB_0 (- xB_0 (* 2 period)))))
			// ...
			// (assert (=> (= capXB_0 (* (2*omega) period)) (= xPrimeB_0 (- xB_0 (* (2*omega) period)))))
			Iterator<Actor> actrIter = graph.getActors ();
			while (actrIter.hasNext ())
			{
				Actor actr = actrIter.next ();
				int maxDistance = graphAnalysis.getMaxDistanceFromSrc (actr);
				String actrName = actr.getName ();
				int repCount = solutions.getSolution (actr).returnNumber ();
				for (int i=0;i<repCount;i++)
				{
					if (maxDistance == 0)
					{
						// This is source actor. so no capPred.
						try
						{
							generateAssertion (ctx.mkEq (startPrimeId (actrName, i), xId (actrName, i)));
						} catch (Z3Exception e) { e.printStackTrace (); }
					}
					else
					{
						for (int j=0;j<=maxDistance*2;j++)
						{
							if (j == 0)
							{
								// (assert (=> (= capXB_0 0) (= xPrimeB_0 xB_0)))
								try
								{
									generateAssertion (ctx.mkImplies (ctx.mkEq (capXId (actrName, i), ctx.mkInt (0)), 
														ctx.mkEq (startPrimeId (actrName, i), xId (actrName, i))));
								} catch (Z3Exception e) { e.printStackTrace (); }
							}
							else if (j == 1)
							{
								// (assert (=> (= capXB_0 period) (= xPrimeB_0 (- xB_0 period))))
								try
								{
									generateAssertion (ctx.mkImplies (ctx.mkEq (capXId (actrName, i), getPeriodDeclId ()), 
										ctx.mkEq (startPrimeId (actrName, i), ctx.mkSub (xId (actrName, i), getPeriodDeclId ()))));
								} catch (Z3Exception e) { e.printStackTrace (); }
							}
							else
							{
								// (assert (=> (= capXB_0 (* 2 period)) (= xPrimeB_0 (- xB_0 (* 2 period)))))
								try
								{
									generateAssertion (ctx.mkImplies (ctx.mkEq (capXId (actrName, i), ctx.mkMul (ctx.mkInt (j), getPeriodDeclId ())), 
										ctx.mkEq (startPrimeId (actrName, i), ctx.mkSub (xId (actrName, i), ctx.mkMul (ctx.mkInt (j), getPeriodDeclId ())))));
								} catch (Z3Exception e) { e.printStackTrace (); }
							}
						}
					}
				}
			}
		}
	
		/**
		 * Generate SMT constraints for Lower and upper bounds on xPrime variables
		 */
		public void xPrimeBounds ()
		{
			// (assert (and (>= xPrimeA_0 0) (< xPrimeA_0 period)))
			Iterator<Actor> actrIter = graph.getActors ();
			while (actrIter.hasNext ())
			{
				Actor actr = actrIter.next ();
				int repCount = solutions.getSolution (actr).returnNumber ();
				for (int i=0;i<repCount;i++)
					try
					{
						generateAssertion (ctx.mkAnd (
											ctx.mkGe (startPrimeId (actr.getName (), i), ctx.mkInt (0)),
											ctx.mkLt (startPrimeId (actr.getName (), i), getPeriodDeclId ())));
					} catch (Z3Exception e) { e.printStackTrace (); }
			}
		}
	
		// Very difficult to give names to the functions now ! :(
		/**
		 * Generate SMT constraints for capX variables based on period.
		 */
		public void capPeriodCalculation ()
		{
			// (assert (or (= capXA_0 capPredXA_0) (= capXA_0 (+ capPredXA_0 period))))
			Iterator<Actor> actrIter = graph.getActors ();
			while (actrIter.hasNext ())
			{
				Actor actr = actrIter.next ();
				if (actr.numIncomingLinks () != 0)
				{
					int repCount = solutions.getSolution (actr).returnNumber ();
					for (int i=0;i<repCount;i++)
						try
						{
							generateAssertion (ctx.mkOr (
												ctx.mkEq (capXId (actr.getName (), i), capPredXId (actr.getName (), i)),
												ctx.mkEq (capXId (actr.getName (), i), ctx.mkAdd (capPredXId (actr.getName (), i), getPeriodDeclId ()))));
						} catch (Z3Exception e) { e.printStackTrace (); }
				}
			}
		}
	}
	
	/**
	 * Class to generate variables and constraints for pipelined scheduling
	 * using Left-edge algorithm.
	 * 
	 * @author Pranav Tendulkar
	 *
	 */
	private class LeftEdgePipelined extends LeftEdge
	{
		/**
		 * Generate SMT variable definitions for left-edge pipelined scheduling.
		 */
		public void leftEdgeDefinitions ()
		{
			generateLeftEdgeCpuDefinitions (true);
			procUtilAtPeriodStartId = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.procUtilAtPeriodStart, "Int");	
		}
		
		/**
		 * Generate all the constraints required for left-edge scheduling
		 * for pipelined mode.
		 */
		private void generateLeftEdgePipelinedConstraints ()
		{
			int totalHsdfActors = hsdf.countActors ();
			
			int count = 0;
			IntExpr allStartIds[] = new IntExpr [totalHsdfActors];
			IntExpr allEndIds[] = new IntExpr [totalHsdfActors];
			ArithExpr periodStartArgs[] = new ArithExpr[totalHsdfActors];
			
			try
			{
			
				Iterator<Actor> actorIter = graph.getActors ();
				while (actorIter.hasNext ())
				{
					Actor actr = actorIter.next ();
					int repCnt = solutions.getSolution (actr).returnNumber ();
					for (int i=0;i<repCnt;i++)
					{
						periodStartArgs[count] = (ArithExpr) ctx.mkITE (ctx.mkLt (startPrimeId (actr.getName (),i), endPrimeId (actr.getName (),i)), 
																	ctx.mkInt (0), ctx.mkInt (1));
						allStartIds[count] = startPrimeId (actr.getName (), i);
						allEndIds[count++] = endPrimeId (actr.getName (), i);
					}
				}
				
				//  (assert (= SolverPrefixs.procUtilAtPeriodStart (+ (if (<= xPrimeA_0 yPrimeA_0) 1 0) (if (<= xPrimeB_0 yPrimeB_0) 1 0) 
				//			(if (<= xPrimeB_1 yPrimeB_1) 1 0)  (if (<= xPrimeC_0 yPrimeC_0) 1 0))))
				generateAssertion (ctx.mkEq (procUtilAtPeriodStartId, ctx.mkAdd (periodStartArgs)));
				
				actorIter = graph.getActors ();
				while (actorIter.hasNext ())
				{
					Actor actr = actorIter.next ();
					int repCount = solutions.getSolution (actr).returnNumber ();
					
					for (int i=0;i<repCount;i++)
					{
						IntExpr startedBeforeId = tasksStartedBeforeId (actr.getName (), i);
						IntExpr endedBeforeId = tasksEndedBeforeId (actr.getName (), i);
						IntExpr procUtilId = procUtilId (actr.getName (), i);
						
						// (assert (and (>= startedBefore_A_0 0) (< startedBefore_A_0 3)))
						generateAssertion (ctx.mkAnd (ctx.mkGe (startedBeforeId, ctx.mkInt (0)), 
													  ctx.mkLe (startedBeforeId, ctx.mkInt (totalHsdfActors))));
						
						// (assert (and (>= endedBefore_A_0 0) (< endedBefore_A_0 3)))
						generateAssertion (ctx.mkAnd (ctx.mkGe (endedBeforeId, ctx.mkInt (0)), 
								  ctx.mkLe (endedBeforeId, ctx.mkInt (totalHsdfActors))));
						
						// (assert (= proc_A_0 (+ 1 (- startedBefore_A_0 endedBefore_A_0))))
						generateAssertion (ctx.mkEq (procUtilId, ctx.mkAdd (procUtilAtPeriodStartId, 
																	ctx.mkInt (1), 
																	ctx.mkSub (startedBeforeId, endedBeforeId))));
						
						IntExpr startTimeId = startPrimeId (actr.getName (), i);
						// long endTimeId = endPrimeId (actr.getName (), i);
		
						if (mutualExclusionGraphAnalysis == false)
						{				
							// (assert (= startedBefore_A_0 (+ (if (<= xB_0 xA_0) 1 0) (if (<= xC_0 xA_0) 1 0))))					
							ArithExpr addArgs[] = new ArithExpr [totalHsdfActors-1];				
							count = 0;
							for (int j=0;j<totalHsdfActors;j++)
							{
								if (startTimeId != allStartIds[j])
									addArgs[count++] = (ArithExpr) ctx.mkITE (ctx.mkLe (allStartIds[j], startTimeId), 
											ctx.mkInt (1), 
											ctx.mkInt (0));
							}
							
							generateAssertion (ctx.mkEq (startedBeforeId, ctx.mkAdd (addArgs)));
								
							// (assert (= endedBefore_A_0 (+ (if (<= (+ xB_0 1) xA_0) 1 0) (if (<= (+ xC_0 1) xA_0) 1 0))))
							addArgs = new ArithExpr [totalHsdfActors];
							count = 0;
							for (int j=0;j<totalHsdfActors;j++)
							{
								//if (endTimeId != allEndIds[j])
									addArgs[count++] = (ArithExpr) ctx.mkITE (ctx.mkLe (allEndIds[j], startTimeId), 
											ctx.mkInt (1), 
											ctx.mkInt (0));
							}
							
							generateAssertion (ctx.mkEq (endedBeforeId, ctx.mkAdd (addArgs)));
						}
						else
						{
							throw new RuntimeException ("Mutual Exclusion is not yet implemented.");
						}
					}		
				}
			} catch (Z3Exception e) { e.printStackTrace (); }
			
			leftEdgeMaxProcUtilization ();
		}
	}
	
	/**
	 * Class to test type constraints for pipelined scheduling.
	 * 
	 * @author Pranav Tendulkar
	 *
	 */
	private class TypeConstraints
	{		
		/**
		 * Generate SMT constraints for type variables
		 */
		public void typeMutexConstraints ()
		{
			generateCpuBounds ();
			afterVariableCalculation ();
			
			Map<String, Integer> solutionSet = new TreeMap<String,Integer>();
			
			Iterator<Actor> actrIter = graph.getActors ();
			while (actrIter.hasNext ())
			{
				Actor actr = actrIter.next ();
				solutionSet.put (actr.getName (), solutions.getSolution (actr).returnNumber ());			
			}
			
			generateTypeIConstraints (solutionSet);
			generateTypeII_1Constraint (solutionSet);
			generateTypeII_2Constraint (solutionSet);			
		}
		
		/**
		 * Generate SMT constraints for calculating values of after variables.
		 */
		public void afterVariableCalculation ()
		{
			Map<String, Integer> solutionSet = new TreeMap<String,Integer>();
			
			Iterator<Actor> actrIter = graph.getActors ();
			while (actrIter.hasNext ())
			{
				Actor actr = actrIter.next ();
				solutionSet.put (actr.getName (), solutions.getSolution (actr).returnNumber ());			
			}
			
			List<String> actrList =  new ArrayList<String>(solutionSet.keySet ());
			
			for (int i=0;i<actrList.size ();i++)
			{
				String actr1 = actrList.get (i);
				int repCnt1 = solutionSet.get (actr1);
				
				for (int j=0;j<repCnt1;j++)
				{			
					for (int k=0;k<actrList.size ();k++)
					{
						String actr2 = actrList.get (k);
						int repCnt2 = solutionSet.get (actr2);
						
						for (int l=0;l<repCnt2;l++)
						{
							// (assert (if (>= xPrimeA_0 yPrimeA_0) (= after_a0_a0 true) (= after_a0_a0 false)))
							try {
									generateAssertion ((BoolExpr) ctx.mkITE (
													ctx.mkGe (startPrimeId (actr2, l), endPrimeId (actr1, j)),
													ctx.mkEq (afterVariableId (actr1, j, actr2, l), ctx.mkTrue ()),
													ctx.mkEq (afterVariableId (actr1, j, actr2, l), ctx.mkFalse ())));
							} catch (Z3Exception e) { e.printStackTrace (); }
						}					
					}
				}
			}
		}
		
		/**
		 * For Type II tasks there are two different constraints needed.
		 * This is the first one saying that if two tasks are on same processor
		 * only one can be of Type II.
		 * 
		 * @param solutionSet solution set containing actor name with its repetition count as key
		 */
		public void generateTypeII_1Constraint (Map<String, Integer> solutionSet)
		{			
			List<String> actrList =  new ArrayList<String>(solutionSet.keySet ());
			
			for (int i=0;i<actrList.size ();i++)
			{
				String actr1 = actrList.get (i);
				int repCnt1 = solutionSet.get (actr1);
				
				for (int j=0;j<repCnt1;j++)
				{
					for (int m=j+1;m<repCnt1;m++)
					{

						// (assert (=> (= cpuA_0 cpuB_0) (or (= after_a0_a0 false) (= after_b0_b0 false)))) 
						try {
								generateAssertion (ctx.mkImplies (
									ctx.mkEq (cpuId (actr1, j), cpuId (actr1, m)),
									ctx.mkOr (ctx.mkEq (afterVariableId (actr1,j,actr1,j), ctx.mkFalse ()), 
											ctx.mkEq (afterVariableId (actr1,m,actr1,m), ctx.mkFalse ()))));
						} catch (Z3Exception e) { e.printStackTrace (); }
					}
					
					for (int k=i+1;k<actrList.size ();k++)
					{
						String actr2 = actrList.get (k);
						int repCnt2 = solutionSet.get (actr2);
						
						for (int l=0;l<repCnt2;l++)
						{
							// (assert (=> (= cpuA_0 cpuB_0) (or (= after_a0_a0 false) (= after_b0_b0 false)))) 
							try {
									generateAssertion (ctx.mkImplies (
										ctx.mkEq (cpuId (actr1, j), cpuId (actr2, l)),
										ctx.mkOr (ctx.mkEq (afterVariableId (actr1,j,actr1,j), ctx.mkFalse ()), 
												ctx.mkEq (afterVariableId (actr2,l,actr2,l), ctx.mkFalse ()))));
							} catch (Z3Exception e) { e.printStackTrace (); }
						}					
					}
				}
			}
		}
		
		/**
		 * For Type II tasks there are two different constraints needed.
		 * This is the first one saying that if two tasks are on same processor
		 * and one of them is type II then the other two after variables should always be true.
		 * 
		 * @param solutionSet solution set containing actor name with its repetition count as key
		 */
		public void generateTypeII_2Constraint (Map<String, Integer> solutionSet)
		{
			List<String> actrList =  new ArrayList<String>(solutionSet.keySet ());
			
			for (int i=0;i<actrList.size ();i++)
			{
				String actr1 = actrList.get (i);
				int repCnt1 = solutionSet.get (actr1);
				
				// Type II-2 constraints
				for (int j=0;j<actrList.size ();j++)
				{
					String actr2 = actrList.get (j);
					int repCnt2 = solutionSet.get (actr2);
					
					for (int k=0;k<repCnt1;k++)
					{
						for (int l=0;l<repCnt2;l++)
						{
							if ((i == j) && (k == l))
								continue;
							
							// Type II-2
							// (assert (=> (and (= cpuA_0 cpuB_0) (= after_a0_a0 true)) (and (= after_a0_b0 true) (= after_b0_a0 true))))
							try {
									generateAssertion (ctx.mkImplies (
										ctx.mkAnd (ctx.mkEq (cpuId (actr1, k), cpuId (actr2, l)), 
												  ctx.mkEq (afterVariableId (actr1,k,actr1,k), ctx.mkTrue ())), 
										ctx.mkAnd (ctx.mkEq (afterVariableId (actr1,k,actr2,l), ctx.mkTrue ()), 
												  ctx.mkEq (afterVariableId (actr2,l,actr1,k), ctx.mkTrue ()))));
							} catch (Z3Exception e) { e.printStackTrace (); }
						}
					}
				}
			}
		}
		
		/**
		 * Generate Type I mutual exclusion constraints.
		 * 
		 * @param solutionSet solutionSet solution set containing actor name with its repetition count as key
		 */
		public void generateTypeIConstraints (Map<String, Integer> solutionSet)
		{			
			List<String> actrList =  new ArrayList<String>(solutionSet.keySet ());
			
			for (int i=0;i<actrList.size ();i++)
			{
				String actr1 = actrList.get (i);
				int repCnt1 = solutionSet.get (actr1);
				
				for (int j=0;j<repCnt1;j++)
				{
					for (int m=j+1;m<repCnt1;m++)
					{
						
						// Type I
						// (assert (=> (= cpuA_0 cpuB_0) (or (= after_a0_b0 true) (= after_b0_a0 true))))						
						try {
								generateAssertion (ctx.mkImplies (
									ctx.mkEq (cpuId (actr1, j), cpuId (actr1, m)),
									ctx.mkOr (ctx.mkEq (afterVariableId (actr1,j,actr1,m), ctx.mkTrue ()), 
											ctx.mkEq (afterVariableId (actr1,m,actr1,j), ctx.mkTrue ()))));
						} catch (Z3Exception e) { e.printStackTrace (); }
					}
					
					for (int k=i+1;k<actrList.size ();k++)
					{
						String actr2 = actrList.get (k);
						int repCnt2 = solutionSet.get (actr2);
						
						for (int l=0;l<repCnt2;l++)
						{
							// Type I
							// (assert (=> (= cpuA_0 cpuB_0) (or (= after_a0_b0 true) (= after_b0_a0 true))))						
							try {
									generateAssertion (ctx.mkImplies (
										ctx.mkEq (cpuId (actr1, j), cpuId (actr2, l)),
										ctx.mkOr (ctx.mkEq (afterVariableId (actr1,j,actr2,l), ctx.mkTrue ()), 
												ctx.mkEq (afterVariableId (actr2,l,actr1,j), ctx.mkTrue ()))));
							} catch (Z3Exception e) { e.printStackTrace (); }
						}					
					}
				}
			}
		}
		
		/**
		 * Define Type variables for all the tasks
		 */
		public void defineTypeVariables ()
		{
			Map<String, Integer> solutionSet = new TreeMap<String,Integer>();
			
			Iterator<Actor> actrIter = graph.getActors ();
			while (actrIter.hasNext ())
			{
				Actor actr = actrIter.next ();
				solutionSet.put (actr.getName (), solutions.getSolution (actr).returnNumber ());			
			}
			
			List<String> actrList =  new ArrayList<String>(solutionSet.keySet ());
			
			for (int i=0;i<actrList.size ();i++)
			{
				String actr1 = actrList.get (i);
				int repCnt1 = solutionSet.get (actr1);
				
				for (int j=0;j<repCnt1;j++)
				{			
					for (int k=0;k<actrList.size ();k++)
					{
						String actr2 = actrList.get (k);
						int repCnt2 = solutionSet.get (actr2);
						
						for (int l=0;l<repCnt2;l++)
						{
							BoolExpr id = (BoolExpr) addVariableDeclaration (SmtVariablePrefixes.afterVarPrefix + actr1 + Integer.toString (j) + "_" + actr2 + Integer.toString (l), "Bool");
							afterVarDecl.put (SmtVariablePrefixes.afterVarPrefix + actr1 + Integer.toString (j) + "_" + actr2 + Integer.toString (l), id);
						}					
					}
				}
			}		
		}
	}

	/**
	 * Class and methods to test Period symmetry constraints for Pipelined scheduling
	 * @author Pranav Tendulkar
	 *
	 */
	private class PeriodSymmetry
	{
		/**
		 * Define all the variables that are needed to define the period symmetry constraints.
		 */
		public void definePeriodSymVariables ()
		{
			Iterator<Actor> actrIter = graph.getActors ();
			while (actrIter.hasNext ())
			{
				Actor actr = actrIter.next ();
				int repCount = solutions.getSolution (actr).returnNumber ();
				int numIncomingLinks = actr.numIncomingLinks ();
				
				if (numIncomingLinks > 1)
				{
					// Define the variable
					for (int i=0;i<repCount;i++)
					{
						IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.predKVarPrefix + actr.getName () + "_" + Integer.toString (i), "Int");
						periodSymVarDecl.put (SmtVariablePrefixes.predKVarPrefix + actr.getName () + "_" + Integer.toString (i), id);
					}
				}
				else if (numIncomingLinks == 1)
				{			
					for (Link lnk : actr.getLinks (Port.DIR.IN))
					{										
						if (repCount < solutions.getSolution (lnk.getOpposite ().getActor ()).returnNumber ())																
						{
							for (int i=0;i<repCount;i++)
							{
								IntExpr id = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.predKVarPrefix + actr.getName () + "_" + Integer.toString (i), "Int");
								periodSymVarDecl.put (SmtVariablePrefixes.predKVarPrefix + actr.getName () + "_" + Integer.toString (i), id);
							}
						}					
					}				
				}							
			}
		}
		
		/**
		 * Generate SMT constraints for period symmetry.
		 */
		public void periodSymmetryConstraints ()
		{
			Iterator<Actor> actrIter = hsdf.getActors ();
			while (actrIter.hasNext ())
			{
				Actor actr = actrIter.next ();
				
				try
				{
					if (actr.numIncomingLinks () == 0)
					{
						// ;; no predecessors
						// (assert (and (>= kxA_0 0) (<= kxA_0 1)))
						generateAssertion (ctx.mkAnd (ctx.mkGe (kStartId (actr.getName ()), ctx.mkInt (0)),
								ctx.mkLe (kStartId (actr.getName ()), ctx.mkInt (1))));
		
					}
					else if (actr.numIncomingLinks () == 1)
					{
						IntExpr predecessorId=null;
						for (Link lnk : actr.getLinks (Port.DIR.IN))
							predecessorId = kEndId (lnk.getOpposite ().getActor ().getName ());					


					// (assert (>= kxB_0 kxA_0))
					// (assert (<= kxB_0 (+ 1 kxA_0)))
						generateAssertion (ctx.mkGe (kStartId (actr.getName ()), predecessorId));
						generateAssertion (ctx.mkLe (kStartId (actr.getName ()), ctx.mkAdd (predecessorId, ctx.mkInt (1))));
					}
					else
					{
						// Define the variable
						IntExpr predId = kPredId (actr.getName ());
		
						// (assert (and (>= kPredB_0 kyA_0) (>= kPredB_0 kyA_1)))
						// (assert (or (= kPredB_0 kyA_0) (= kPredB_0 kyA_1)))
						BoolExpr andArgs[] = new BoolExpr[actr.numIncomingLinks ()];
						BoolExpr orArgs[]  = new BoolExpr[actr.numIncomingLinks ()];
						
						{
							int count = 0;
							for (Link lnk : actr.getLinks (Port.DIR.IN))
							{
								andArgs[count] = ctx.mkGe (predId, kEndId (lnk.getOpposite ().getActor ().getName ()));
								orArgs[count++] = ctx.mkEq (predId, kEndId (lnk.getOpposite ().getActor ().getName ()));
							}
							
							generateAssertion (ctx.mkOr (orArgs));
							generateAssertion (ctx.mkAnd (andArgs));
						}
						
						// (assert (<= kxB_0 (+ kPredB_0 1)))
						// (assert (>= kxB_0 kPredB_0))
						generateAssertion (ctx.mkGe (kStartId (actr.getName ()), predId));
						generateAssertion (ctx.mkLe (kStartId (actr.getName ()), ctx.mkAdd (predId, ctx.mkInt (1))));
					}
				} catch (Z3Exception e) { e.printStackTrace (); }
			}
		}
	}
}
