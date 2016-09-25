package solver.sharedMemory.combinedSolver.nonpipelined;

import java.util.*;

import output.GanttChart;
import output.GanttChart.Record;

import solver.SmtVariablePrefixes;
import solver.sharedMemory.combinedSolver.MutualExclusionSolver;
import spdfcore.*;

import com.microsoft.z3.*;

import exploration.interfaces.threeDim.LatProcBuffConstraints;
import exploration.interfaces.twoDim.LatProcConstraints;

/**
 * Mutual Exclusion solver for non-pipelined scheduling problem
 * 
 * @author Pranav Tendulkar
 *
 */
public class MutExNonPipelinedScheduling extends MutualExclusionSolver 
							implements LatProcConstraints, LatProcBuffConstraints 
{
	/**
	 * Build mutual exclusion solver object
	 * 
	 * @param inputGraph application graph SDF graph
	 */
	public MutExNonPipelinedScheduling (Graph inputGraph) 
	{
		super (inputGraph);		
	}

	/**
	 * Generate all the buffer calculations
	 */
	public void generateBufferCalculation () 
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
			int tokenSize = chnnl.getTokenSize();
			
			int bufferMaxBound = (chnnl.getInitialTokens () +
					  (Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ()) * srcRepCount)) * tokenSize;

			if (bufferAnalysisWithFunctions == true)
			{
				int maxBufIndex = srcRepCount + dstRepCount;			
				
				assertBufferBounds (srcActor.getName (), dstActor.getName (), maxBufIndex, bufferMaxBound);
				assertIndexLimits (srcActor.getName (), dstActor.getName (), maxBufIndex);
				assertUniqueIndex (srcActor.getName ()+dstActor.getName (), maxBufIndex);

				assertIndexOrdering (srcActor.getName (),
						dstActor.getName (),
						srcActor.getName ()+dstActor.getName (),
						srcRepCount, dstRepCount);			

				assertInitialTokens (srcActor.getName (), dstActor.getName (), chnnl.getInitialTokens() * chnnl.getTokenSize());

				assertBuffer (srcActor.getName (), dstActor.getName (), 
						srcRepCount, dstRepCount, maxBufIndex);

				assertMaxBuffer (srcActor.getName (), dstActor.getName (), maxBufIndex);
			}
			else
			{				
				assertBufferLinearNonPipelined (srcActor.getName (), dstActor.getName (),
					chnnl.getInitialTokens (), srcRepCount, dstRepCount, 
					Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ()) * tokenSize,
					Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ()) * tokenSize);
				
				try
				{
					assertMaxBufferLinear (srcActor.getName (), dstActor.getName (), 
						srcRepCount, ctx.mkInt (chnnl.getInitialTokens () * tokenSize));
				} catch (Z3Exception e) { e.printStackTrace (); }
				
				assertBufferBoundsLinear (srcActor.getName (), dstActor.getName (), srcRepCount, bufferMaxBound);
				
			}				
		}
		
		assertTotalBuffer ();				
	}

	/**
	 * The index of producer and consumers of the channel depend on the order of start times.
	 * Generate constraints to decide on this order.
	 * 
	 * @param srcActor source actor of the channel
	 * @param dstActor sink actor of the channel 
	 * @param idxName name of the index
	 * @param srcRepCount repetition count of source actor
	 * @param dstRepCount repetition count of sink actor
	 */
	private void assertIndexOrdering (String srcActor, String dstActor, String idxName, int srcRepCount, int dstRepCount)
	{
		/**********************************************************************************/
		// ;; Ordering of the indexes should be done according to their start times.
		// (assert (if (>= (xA 0) (xA 1)) (> (idxAB 1) (idxAB 2)) (< (idxAB 1) (idxAB 2))))
		/**********************************************************************************/		
		for (int i=0;i<srcRepCount;i++)
		{
			IntExpr idxA_i = (IntExpr) idxId (srcActor + dstActor, i);
			IntExpr xA_i = xId (srcActor, i);
			try
			{

				for (int j=i+1;j<srcRepCount;j++)
				{					
	
					IntExpr idxA_j = (IntExpr) idxId (srcActor + dstActor, j);
					IntExpr xA_j = xId (srcActor, j);
	
					generateAssertion ((BoolExpr) ctx.mkITE ( ctx.mkGe (xA_i, xA_j),
							ctx.mkGt (idxA_i, idxA_j), 
							ctx.mkLt (idxA_i, idxA_j)));
	
				}
	
				// (assert (if (>= (xA 0) (yB 1)) (> (idxAB 1) (idxAB 2)) (< (idxAB 1) (idxAB 2))))
				for (int j=0;j<dstRepCount;j++)
				{
	
					IntExpr idxB_j = (IntExpr) idxId (srcActor + dstActor, srcRepCount+j);
					IntExpr yB_j = yId (dstActor, j);
	
					generateAssertion ((BoolExpr) ctx.mkITE ( 
						ctx.mkGe (xA_i, yB_j),
						ctx.mkGt (idxA_i, idxB_j), 
						ctx.mkLt (idxA_i, idxB_j)));									
				}
			} catch (Z3Exception e) { e.printStackTrace (); }
		}			

		/**********************************************************************************/
		// ;; Ordering of the indexes should be done according to their start times.
		// (assert (if (> (+ (xB 1) dB) (+ (xB 2) dB)) (> (idxAB 5) (idxAB 6)) (< (idxAB 5) (idxAB 6))))
		/**********************************************************************************/

		for (int i=0;i<dstRepCount;i++)
		{
			IntExpr yB_i = yId (dstActor, i);
			IntExpr idxB_i = (IntExpr) idxId (srcActor+dstActor, i+srcRepCount);								

			for (int j=i+1;j<dstRepCount;j++)
			{
				IntExpr idxB_j = (IntExpr) idxId (srcActor+dstActor, j+srcRepCount);					
				IntExpr yB_j = yId (dstActor, j);

				try
				{
					generateAssertion ((BoolExpr) ctx.mkITE (ctx.mkGe (yB_i, yB_j),
						ctx.mkGt (idxB_i, idxB_j), ctx.mkLt (idxB_i, idxB_j)));
				} catch (Z3Exception e) { e.printStackTrace (); }

			}
		}
	}

	/**
	 * Assert constraint for initial tokens of the channel.
	 * 
	 * @param srcActor source actor of the channel
	 * @param dstActor sink actor of the channel
	 * @param initialTokens number of initial tokens
	 */
	private void assertInitialTokens (String srcActor, String dstActor, int initialTokens)
	{
		// (= (bufAB 0) intialTokens)
		try
		{
			generateAssertion (ctx.mkEq (buffId (srcActor + dstActor, 0), ctx.mkInt (initialTokens)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * Total buffer used in the schedule is sum of all the buffers.
	 */
	private void assertTotalBuffer ()
	{		
		IntExpr args[] = new IntExpr[graph.countChannels ()];
		int count = 0;

		Iterator<Channel> iterChnnl = graph.getChannels ();
		while (iterChnnl.hasNext ())
		{			
			Channel chnnl = iterChnnl.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
			// args[count++] = (IntExpr) ctx.mkMul(maxBufferId (srcActor.getName (), dstActor.getName ()), ctx.mkInt(chnnl.getTokenSize()));
			args[count++] = maxBufferId (srcActor.getName (), dstActor.getName ());
		}

		try
		{
			generateAssertion (ctx.mkEq (totalBufDecl, ctx.mkAdd (args)));
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * Generate calculation for buffer size at execution of a producer instance.
	 * 
	 * @param srcActor source actor
	 * @param dstActor sink actor
	 * @param initialTokens number of initial tokens
	 * @param srcRepCount source repetition count
	 * @param dstRepCount destination repetition count
	 * @param prodRate production rate on the channel
	 * @param consRate consumption rate on the channel
	 */
	private void assertBufferLinearNonPipelined (String srcActor, String dstActor, 
			int initialTokens, int srcRepCount, int dstRepCount, int prodRate, int consRate)
	{		
		// (assert (= bufferAtA_0-AB (- (- (+ (if (>= xA_0 xA_2) 2 0) (+ (if (>= xA_0 xA_1) 2 0) 0)) (if (>= xA_0 (+ xB_0 2)) 3 0)) (if (>= xA_0 (+ xB_1 2)) 3 0))))		
		// (assert (= maxBufAtA_0-AB (- (- (+ 0 2 (if (>= xA_0 xA_1) 2 0) (if (>= xA_0 xA_2) 2 0)) (if (>= xA_0 (+ xB_0 2)) 3 0)) (if (>= xA_0 (+ xB_1 2)) 3 0))))

		try
		{
			if (srcActor.equals (dstActor) == false)
			{
				ArithExpr initialTokensId = ctx.mkAdd (ctx.mkInt (initialTokens), ctx.mkInt (prodRate));
				
				for (int i=0;i<srcRepCount;i++)
				{
					ArithExpr previousId = initialTokensId;
					IntExpr buffId = bufferAtId (srcActor, dstActor, i);
					IntExpr currXId = xId (srcActor, i); 
					
					
					for (int j=0;j<srcRepCount;j++)
					{
						if (i==j)
							continue;
						
						Expr ifStId  = ctx.mkITE (ctx.mkGe (currXId, xId (srcActor,j)), 
										ctx.mkInt (prodRate), 
										ctx.mkInt (0));
						
						previousId = ctx.mkAdd ((ArithExpr)ifStId, previousId);
					}
					
					for (int j=0;j<dstRepCount;j++)
					{
						Expr ifStId  = ctx.mkITE (ctx.mkGe (currXId, yId (dstActor,j)), 
								ctx.mkInt (consRate), 
								ctx.mkInt (0));
						
						previousId = ctx.mkSub (previousId, (ArithExpr)ifStId);
					}
					
					generateAssertion (ctx.mkEq (buffId, previousId));
				}
			}
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * Non-lazy scheduling constraints.
	 * Note: They slow down the solver.
	 */
	@SuppressWarnings("unused")
	private void nonLazyConstraint()
	{
		List<Actor> actrList = new ArrayList<Actor>();
		Iterator<Actor> actorIter = hsdf.getActors ();
		while (actorIter.hasNext ())
		{				
			Actor actr = actorIter.next ();
			actrList.add (actr);
		}
		
		List<BoolExpr> orArgsList = new ArrayList<BoolExpr>();		
		
		try
		{
			for (int i=0;i< actrList.size ();i++)
			{
				orArgsList.clear();
				
				Actor actr1 = actrList.get (i);
				IntExpr xI = xId (actr1.getName ());
				
				orArgsList.add(ctx.mkEq(xI, ctx.mkInt(0)));
				
				HashSet<Channel>incomingChnlSet = actr1.getChannels(Port.DIR.IN);
				for(Channel chnnl : incomingChnlSet)
				{
					IntExpr yJ = yId(chnnl.getOpposite(actr1).getName());
					orArgsList.add(ctx.mkEq(xI, yJ));
				}				
				
				for (int j=0;j< actrList.size ();j++)
				{
					if(i == j)
						continue;
					
					Actor actr2 = actrList.get (j);	
					IntExpr yJ = yId (actr2.getName ());					
					
					orArgsList.add(ctx.mkEq(xI, yJ));					
				}
				
				BoolExpr orArgs[] = orArgsList.toArray(new BoolExpr[orArgsList.size()]);					
				generateAssertion(ctx.mkOr(orArgs));
			}
		} catch (Z3Exception e) { e.printStackTrace (); }
	}
	
	/**
	 * Processor optimal allocation of tasks.
	 * Note: they make the solver slower.
	 */
	@SuppressWarnings("unused")
	private void testProcSymConstraint ()
	{		
		List<Actor> actrList = graph.getActorList();		
		List<BoolExpr> orArgsList = new ArrayList<BoolExpr>();		
		
		for (int i=0;i< actrList.size ();i++)
		{
			Actor actr = actrList.get (i);
			for (int j=0;j<solutions.getSolution (actr).returnNumber ();j++)
			{
				orArgsList.clear();
				
				for (int k=0;k<solutions.getSolution (actr).returnNumber ();k++)
				{
					if(j == k)
						continue;
					
					// (assert (=> (= (cpuA 0) (cpuA 1)) (or (>= (xA 0) (yA 1)) (>= (xA 1) (yA 0)))))
					IntExpr cpuJ = cpuId (actr.getName (), j);
					IntExpr cpuK = cpuId (actr.getName (), k);


					IntExpr xJ = xId (actr.getName (), j);
					IntExpr yJ = yId (actr.getName (), j);
					
					IntExpr xK = xId (actr.getName (), k);					
					IntExpr yK = yId (actr.getName (), k);
					
					// Final Assertion
					try
					{						
						orArgsList.add(ctx.mkAnd(ctx.mkEq(cpuK, ctx.mkSub(cpuJ, ctx.mkInt(1))), 
												ctx.mkLt(xJ, yK), ctx.mkLt(xK, yJ)));
					} catch (Z3Exception e) { e.printStackTrace (); }
				}				

				for (int k=i+1;k<actrList.size ();k++)
				{
					Actor otherActor = actrList.get (k);
					for (int l=0;l<solutions.getSolution (otherActor).returnNumber ();l++)
					{
						// (=> (= (cpuD 0) (cpuC 0)) (or (>= (xD 0) (+ (xC 0) dC)) (>= (xC 0) (+ (xD 0) dD))))

						// (cpuD 0)						
						IntExpr cpuJ = cpuId (actr.getName (), j);
						IntExpr cpuK = cpuId (otherActor.getName (), l);

						// (xD 0)
						IntExpr xJ = xId (actr.getName (), j); 
						IntExpr xK = xId (otherActor.getName (), l);
						IntExpr yJ = yId (actr.getName (), j); 
						IntExpr yK = yId (otherActor.getName (), l);								

						// Final Assertion
						try
						{
							orArgsList.add(ctx.mkAnd(ctx.mkEq(cpuK, ctx.mkSub(cpuJ, ctx.mkInt(1))), 
									ctx.mkLt(xJ, yK), ctx.mkLt(xK, yJ)));
							
						} catch (Z3Exception e) { e.printStackTrace (); }
					}						
				}
				
				try
				{
					IntExpr cpuJ = cpuId (actr.getName (), j);
					BoolExpr orArgs[] = orArgsList.toArray(new BoolExpr[orArgsList.size()]);					
					generateAssertion(ctx.mkImplies(ctx.mkGt(cpuJ, ctx.mkInt(0)), ctx.mkOr(orArgs)));
				} catch (Z3Exception e) { e.printStackTrace (); }
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see solver.sharedMemory.combinedSolver.MutualExclusionSolver#assertNonPipelineConstraints()
	 */
	@Override
	public void assertNonPipelineConstraints ()
	{
		LeftEdgeNonPipelined leftEdge = null;
		latencyDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.latencyPrefix, "Int");
		if (bufferAnalysis == true)
			totalBufDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.totalBufferPrefix, "Int");
		totalProcDecl = (IntExpr) addVariableDeclaration (SmtVariablePrefixes.totalProcPrefix, "Int");

		generateActorTimeDefinitions ();
		
		if (leftEdgeAlgorithm == true)
		{
			leftEdge = new LeftEdgeNonPipelined ();
			leftEdge.generateLeftEdgeDefinitions ();
		}	
		else
			generateCpuDefinitions ();

		if (processorSymmetry == true)
			generateProcessorSymmetryDefinitions ();

		if (bufferAnalysis == true)
			generateBufferAnalysisDefinitions ();	

		// Generate All Constraints
		assertStartTimeBounds ();

		if (leftEdgeAlgorithm == true)
			leftEdge.nonPipelinedConstraints ();
		else
		{
			generateCpuBounds ();
			generateMutualExclusion ();	
		}

		if (processorSymmetry == true && leftEdgeAlgorithm == false)
			processorSymmetryConstraints ();
		
		if (graphSymmetry == true)
			graphSymmetryLexicographic ();

		generateActorPrecedences ();
		
		generateLatencyCalculation ();

		if (bufferAnalysis == true)
			generateBufferCalculation ();
		
		minLatencyBound ();
		
		// TODO : Experimental failed constraints.
		// testProcSymConstraint();
		
		// nonLazyConstraint();
	}

	/* (non-Javadoc)
	 * @see solver.sharedMemory.combinedSolver.MutualExclusionSolver#getLatency(java.util.Map)
	 */
	@Override
	public int getLatency (Map<String, String> model) 
	{
		return Integer.parseInt (model.get (SmtVariablePrefixes.latencyPrefix));
	}
	
	/**
	 * Left-edge scheduling for non-pipelined problem.
	 * 
	 * @author Pranav Tendulkar
	 */
	private class LeftEdgeNonPipelined extends LeftEdge
	{
		public LeftEdgeNonPipelined () {}
		
		/**
		 * Generate all the variables to solve the problem
		 */
		public void generateLeftEdgeDefinitions () { generateLeftEdgeCpuDefinitions (false); }
		
		/**
		 * Generate constraints to solve the problem with Left-edge
		 */
		public void nonPipelinedConstraints ()
		{
			int totalHsdfActors = hsdf.countActors ();
			int count = 0;
			IntExpr allStartIds[] = new IntExpr [totalHsdfActors];
			IntExpr allEndIds[] = new IntExpr [totalHsdfActors];		
			
			Iterator<Actor> actorIter = graph.getActors ();
			while (actorIter.hasNext ())
			{
				Actor actr = actorIter.next ();
				int repCnt = solutions.getSolution (actr).returnNumber ();
				for (int i=0;i<repCnt;i++)
				{
					allStartIds[count] = xId (actr.getName (), i);
					allEndIds[count++] = yId (actr.getName (), i);
				}
			}
			
			leftEdgeMaxProcUtilization ();
			
			actorIter = graph.getActors ();
			while (actorIter.hasNext ())
			{
				Actor actr = actorIter.next ();
				int repCount = solutions.getSolution (actr).returnNumber ();
				
				try
				{
					if (repCount == 1)
					{
						IntExpr procUtilId = procUtilId (actr.getName (), 0);
						generateAssertion (ctx.mkEq (procUtilId, ctx.mkInt (0)));
					}
					else
					{			
						for (int i=0;i<repCount;i++)
						{
							IntExpr startedBeforeId = tasksStartedBeforeId (actr.getName (), i);
							IntExpr endedBeforeId = tasksEndedBeforeId (actr.getName (), i);
							IntExpr procUtilId = procUtilId (actr.getName (), i);
							
							// (assert (and (>= startedBefore_A_0 0) (< startedBefore_A_0 3)))
							generateAssertion (ctx.mkAnd (ctx.mkGe (startedBeforeId, ctx.mkInt (0)), 
														  ctx.mkLt (startedBeforeId, ctx.mkInt (totalHsdfActors))));
							
							// (assert (and (>= endedBefore_A_0 0) (< endedBefore_A_0 3)))
							generateAssertion (ctx.mkAnd (ctx.mkGe (endedBeforeId, ctx.mkInt (0)), 
									  ctx.mkLt (endedBeforeId, ctx.mkInt (totalHsdfActors))));
							
							// (assert (= proc_A_0 (- startedBefore_A_0 endedBefore_A_0)))
							generateAssertion (ctx.mkEq (procUtilId, ctx.mkAdd (ctx.mkInt (1), ctx.mkSub (startedBeforeId, endedBeforeId))));
							
							IntExpr startTimeId = xId (actr.getName (), i);
							IntExpr endTimeId = yId (actr.getName (), i);
							
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
								count = 0;
								for (int j=0;j<totalHsdfActors;j++)
								{
									if (endTimeId != allEndIds[j])
										addArgs[count++] = (ArithExpr) ctx.mkITE (ctx.mkLe (allEndIds[j], startTimeId), 
												ctx.mkInt (1), 
												ctx.mkInt (0));
								}
								
								generateAssertion (ctx.mkEq (endedBeforeId, ctx.mkAdd (addArgs)));
							}
							else
							{	
								buildOverlappingActorList ();
								Actor hsdfActor = hsdf.getActor (actr.getName () + "_" + Integer.toString (i));
								HashSet<Actor> overlappingActors = overlappingActorList.get (hsdfActor);
								if (overlappingActors.size () > 0)
								{
									if (graphSymmetry == true)
									{
										if (overlappingActorWithGraphSymList.containsKey (hsdfActor))
										{
											HashSet<Actor> startBeforeActors = overlappingActorWithGraphSymList.get (hsdfActor).get (0);
											HashSet<Actor> endBeforeActors = overlappingActorWithGraphSymList.get (hsdfActor).get (1);								
											
											ArithExpr addArgs[] = new ArithExpr [overlappingActors.size ()];
											
											// (assert (= startedBefore_A_0 (+ (if (<= xB_0 xA_0) 1 0) (if (<= xC_0 xA_0) 1 0))))
											count = 0;					
											for (Actor overlapActr : overlappingActors)
											{
												if (startBeforeActors.contains (overlapActr))
													addArgs[count++] = (ArithExpr) ctx.mkITE (ctx.mkEq (xId (overlapActr.getName ()), startTimeId), 
															ctx.mkInt (1), 
															ctx.mkInt (0));
												else										
													addArgs[count++] = (ArithExpr) ctx.mkITE (ctx.mkLe (xId (overlapActr.getName ()), startTimeId), 
															ctx.mkInt (1), 
															ctx.mkInt (0));
											}
											generateAssertion (ctx.mkEq (startedBeforeId, ctx.mkAdd (addArgs)));
											
											if (overlappingActors.size () - endBeforeActors.size () == 0)
											{
												generateAssertion (ctx.mkEq (endedBeforeId, ctx.mkInt (0)));
											}
											else
											{
												addArgs = new ArithExpr [overlappingActors.size () - endBeforeActors.size ()];
												
												// (assert (= endedBefore_A_0 (+ (if (<= (+ xB_0 1) xA_0) 1 0) (if (<= (+ xC_0 1) xA_0) 1 0))))
												count = 0;
												for (Actor overlapActr : overlappingActors)
												{
													if (endBeforeActors.contains (overlapActr))
														continue;
													addArgs[count++] = (ArithExpr) ctx.mkITE (ctx.mkLe (yId (overlapActr.getName ()), startTimeId), 
															ctx.mkInt (1), 
															ctx.mkInt (0));
												}						
												generateAssertion (ctx.mkEq (endedBeforeId, ctx.mkAdd (addArgs)));
											}
										}
									}
									else
									{
										ArithExpr addArgs[] = new ArithExpr [overlappingActors.size ()];
										
										// (assert (= startedBefore_A_0 (+ (if (<= xB_0 xA_0) 1 0) (if (<= xC_0 xA_0) 1 0))))
										count = 0;					
										for (Actor overlapActr : overlappingActors)
										{
											addArgs[count++] = (ArithExpr) ctx.mkITE (ctx.mkLe (xId (overlapActr.getName ()), startTimeId), 
													ctx.mkInt (1), 
													ctx.mkInt (0));
										}
										generateAssertion (ctx.mkEq (startedBeforeId, ctx.mkAdd (addArgs)));
										
										// (assert (= endedBefore_A_0 (+ (if (<= (+ xB_0 1) xA_0) 1 0) (if (<= (+ xC_0 1) xA_0) 1 0))))
										count = 0;
										for (Actor overlapActr : overlappingActors)
										{						
											addArgs[count++] = (ArithExpr) ctx.mkITE (ctx.mkLe (yId (overlapActr.getName ()), startTimeId), 
													ctx.mkInt (1), 
													ctx.mkInt (0));
										}						
										generateAssertion (ctx.mkEq (endedBeforeId, ctx.mkAdd (addArgs)));
									}
								}
							}
						}
					}
				} catch (Z3Exception e) { e.printStackTrace (); }
			}		
		}
	}

	/**
	 * Generate a Gantt chart from a model
	 * 
	 * @param model model generated from the SMT solver
	 * @param outputFileName output file name
	 */
	public void modelToGantt(Map<String, String> model, String outputFileName)
	{	
		int maxProcessors = 64;
		
		// Generate the Gantt Chart.
		GanttChart ganttChart = new GanttChart ();
		ganttChart.addNamesToActorInGraph = true;
		ganttChart.addLegendInGraph = false;

		// I will first form a processor map to processor index.
		boolean procUsed[] = new boolean [maxProcessors];
		int newProcIndex[] = new int [maxProcessors];


		for(int i=0;i<maxProcessors;i++)
		{
			procUsed[i] = false;
			newProcIndex[i] = -1;
		}
		

		Iterator<Actor> actrIter = graph.getActors();
		while(actrIter.hasNext())
		{
			Actor actr = actrIter.next();
			for(int instanceId=0;instanceId<solutions.getSolution(actr).returnNumber();instanceId++)
			{
				int procIndex = Integer.parseInt(model.get(SmtVariablePrefixes.cpuPrefix + actr.getName() + "_" + Integer.toString (instanceId)));				
				procUsed[procIndex] = true;
			}			
		}

		int procCount = 0;
		
		for(int i=0;i<maxProcessors;i++)
		{
			if(procUsed[i] == true)
				newProcIndex[i] = procCount++;
			
		}		

		// First All the data flow actors.
		int actorColor = 0;
		actrIter = graph.getActors();
		while(actrIter.hasNext())
		{
			Actor actr = actrIter.next();

			for(int instanceId=0;instanceId<solutions.getSolution(actr).returnNumber();instanceId++)
			{
				long startTime = Integer.parseInt(model.get(SmtVariablePrefixes.startTimePrefix + actr.getName() + "_" + Integer.toString (instanceId)));
				long endTime = actr.getExecTime() + startTime;
				int procIndex = -1;
				String procName;
				boolean printNameInGraph=true;

				procIndex = Integer.parseInt(model.get(SmtVariablePrefixes.cpuPrefix + actr.getName() + "_" + Integer.toString (instanceId)));				
				procIndex = newProcIndex[procIndex];
				procName = "Proc"+Integer.toString(procIndex);
				printNameInGraph = true;

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

		ganttChart.plotChart(outputFileName, -1);		
	}
}
