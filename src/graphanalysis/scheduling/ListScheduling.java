package graphanalysis.scheduling;
import java.util.*;

import graphanalysis.TransformSDFtoHSDF;
import spdfcore.*;
import spdfcore.Channel.Link;
import spdfcore.stanalys.*;

/**
 * Perform List scheduling algorithm.
 * 
 * @author Pranav Tendulkar
 *
 */
public class ListScheduling 
{
	/**
	 * Input SDF graph
	 */
	private Graph sdf;
	
	/**
	 * Solutions of SDF GRAPH
	 */
	private Solutions solutions;
	
	/**
	 * An equivalent HSDF graph
	 */
	private Graph hsdf;
	
	/**
	 * List of predecessors of an actor 
	 */
	private Map<Actor, HashSet<Actor>> predecessors;
	
	/**
	 * Ready list for scheduling
	 */
	List<Actor> readyList;
	
	/**
	 * Tasks already allocated 
	 */
	List<Actor> allocatedActorList;
	
	/**
	 * Non allocated Tasks 
	 */
	List<Actor> nonAllocatedActorList;
	
	/**
	 * Schedule produced
	 */
	private Map<Actor, Integer[]> schedule;
	
	/**
	 * Latest time available on processors
	 */
	private int processorTime[];
	
	/**
	 * Strategy to select the tasks from ready list.
	 *
	 */
	public enum Strategy {RANDOM, LEXICOGRAPHIC, LARGEST_PROC_TIME, EARLIEST_START_TIME};
	
	/**
	 * assigns random unique index for each actor
	 */
	private Map<Actor, Integer> randomStrategyOrder;
	
	/**
	 * list of strategies to be applied on top of random
	 */
	public List<Strategy> selectionStrategies;

	/**
	 * Initialize list-scheduling algorithm.
	 * 
	 * @param sdfGraph input SDF graph
	 */
	public ListScheduling (Graph sdfGraph)
	{
		sdf = sdfGraph;

		GraphExpressions expressions = new GraphExpressions ();
		expressions.parse (sdf);
		solutions = new Solutions ();
		solutions.setThrowExceptionFlag (false);	    		
		solutions.solve (sdf, expressions);

		TransformSDFtoHSDF toHSDF = new TransformSDFtoHSDF ();
		hsdf = toHSDF.convertSDFtoHSDFWithUniqueChannels (sdf);
		predecessors = new HashMap<Actor, HashSet<Actor> >();
		schedule = new HashMap<Actor, Integer[]>();
		readyList = new ArrayList<Actor>();
		allocatedActorList = new ArrayList<Actor>();		
		nonAllocatedActorList = new ArrayList<Actor>();
		selectionStrategies = new ArrayList<Strategy>(); // by default empty => RANDOM strategy
		randomStrategyOrder= new HashMap<Actor, Integer>();
		computeRandomStrategy (); 
	}
	
	// 	// Function to print predecessors and successors	
	//	private void printPredSucc ()
	//	{
	//		System.out.println ("Predecessors : ");		
	//		
	//		for (Actor actr : predecessors.keySet ())
	//		{
	//			System.out.println ("Actr :" + actr.getName ());
	//			
	//			HashSet<Actor> pred = predecessors.get (actr);
	//			Iterator<Actor>predIter = pred.iterator ();
	//			while (predIter.hasNext ())
	//			{
	//				Actor predActor = predIter.next ();
	//				System.out.println (predActor.getName ());
	//			}			
	//		}
	//	}

	/**
	 * Generate a list of all predecessors
	 */
	private void generatePredecessorsList ()
	{		
		Iterator<Actor> actrIter = hsdf.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();			

			HashSet<Actor> pred = new HashSet<Actor>();

			for(Link lnk : actr.getLinks (Port.DIR.IN))			
				pred.add (lnk.getOpposite ().getActor ());
			
			predecessors.put (actr, pred);						
		}
	}

	/**
	 * Generate a schedule using list scheduling for fixed number of processors
	 * 
	 * @param numProcessors number of processors
	 * @return tasks with their start times
	 */
	public Map<String, String> generateSchedule (int numProcessors)
	{	
		// Clear all the lists.
		predecessors.clear ();
		readyList.clear ();
		allocatedActorList.clear ();
		nonAllocatedActorList.clear ();

		processorTime = new int [numProcessors];		

		// Initialize processor times
		for (int i=0;i<numProcessors;i++)
			processorTime[i] = 0;

		// Add all the actors to the non-allocated list.
		Iterator<Actor> totalActorIter = hsdf.getActors ();
		while (totalActorIter.hasNext ())		
			nonAllocatedActorList.add (totalActorIter.next ());

		// Generate Predecessor Successor List.
		generatePredecessorsList ();

		while (nonAllocatedActorList.size () > 0)
		{		
			// Select the processor with minimum time.
			int procWithMinTime = selectProcWithMinTime (processorTime);		
			int minTime = processorTime[procWithMinTime];

			calculateReadyActors (minTime);

			Actor readyActor = selectReadyActor ();

			if (readyActor != null)
			{							
				// Allocate the processor to the actor.
				Integer[] params = new Integer[2];
				params[0] = procWithMinTime;
				params[1] = minTime;

				schedule.put (readyActor, params);

				processorTime[procWithMinTime] += readyActor.getExecTime ();

				readyList.remove (readyActor);
				allocatedActorList.add (readyActor);
				nonAllocatedActorList.remove (readyActor);				
			}
			else
				processorTime[procWithMinTime] = getTimeIncrement (numProcessors, procWithMinTime);
		}		
		return generateModel ();
	}

	/**
	 * Function to increment the time of the processor in case there is no ready actor.
	 * 
	 * @param numProcessors total number of processors
	 * @param forProcessor the processor for which the time should be incremented
	 * @return minimum time increment for a processor
	 */
	private int getTimeIncrement (int numProcessors, int forProcessor)
	{
		int minimumTimeIncrement = Integer.MAX_VALUE;
		int timeIncrement[] = new int[numProcessors];
		for (int i=0;i<numProcessors;i++)
			timeIncrement[i] = 0;

		// Calculate Maximum Time on all the processors
		for (Actor actr : schedule.keySet ())
		{
			Integer[] schedParams = schedule.get (actr);
			int actrProcessor = schedParams[0];
			int actrEndTime = schedParams[1] + actr.getExecTime ();

			if (actrProcessor == forProcessor)
				continue;

			if (actrEndTime > timeIncrement[actrProcessor])
				timeIncrement[actrProcessor] = actrEndTime;
		}

		// Calculate minimum of all of the max times on all the processors.
		for (int i=0;i<numProcessors;i++)
			if ((timeIncrement[i] < minimumTimeIncrement) && (timeIncrement[i] > processorTime[forProcessor]))
				minimumTimeIncrement = timeIncrement[i];		

		return minimumTimeIncrement;
	}
	
	/**
	 * Calculate the buffer size for the schedule
	 * @param model schedule containing tasks and their start times
	 * @return total buffer size
	 */
	private int calculateBuffer (Map<String, String> model)
	{
		int bufferCost=0;
		Map<Integer,List<String>> channelActorStartTimes = new HashMap<Integer,List<String>>();
		
		Iterator<Channel> chnnlIter = sdf.getChannels ();		
		while (chnnlIter.hasNext ())
		{			
			Channel chnnl = chnnlIter.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
			int srcRepCount = solutions.getSolution (srcActor).returnNumber ();
			int dstRepCount = solutions.getSolution (dstActor).returnNumber ();
			int srcPortRate = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
			int dstPortRate = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());
			int currentBuffer[] = new int [srcRepCount + dstRepCount + 1];
			channelActorStartTimes.clear ();			
			
			// Don't reverse this order. first we add destination actors to the list, so that if there are 
			// source actors starting at the same time as destination actor is ending, it will first 
			// subtract the buffersize and then add it. thus correct max will be calculated.
			for (int i=0;i<dstRepCount;i++)
			{
				int endtime = Integer.parseInt (model.get ("x"+dstActor.getName ()+"_"+Integer.toString (i))) + dstActor.getExecTime ();
				if (channelActorStartTimes.containsKey (endtime))
					channelActorStartTimes.get (endtime).add (dstActor.getName ()+"_"+Integer.toString (i));
				else
				{
					List<String> newList = new ArrayList<String>();
					newList.add (dstActor.getName ()+"_"+Integer.toString (i));
					channelActorStartTimes.put (endtime, newList);
				}
			}
			
			for (int i=0;i<srcRepCount;i++)
			{
				int starttime = Integer.parseInt (model.get ("x"+srcActor.getName ()+"_"+Integer.toString (i)));
				if (channelActorStartTimes.containsKey (starttime))
					channelActorStartTimes.get (starttime).add (srcActor.getName ()+"_"+Integer.toString (i));
				else
				{
					List<String> newList = new ArrayList<String>();
					newList.add (srcActor.getName ()+"_"+Integer.toString (i));
					channelActorStartTimes.put (starttime, newList);
				}
			}
			
			// Sort the map by keyset
			Map<Integer,List<String>> treeMap = new TreeMap<Integer,List<String>>(channelActorStartTimes);
			
			int count = 1;
			for (Map.Entry<Integer,List<String>> entry : treeMap.entrySet ())
			{
				List<String> actors = entry.getValue ();
				for (int i=0;i<actors.size ();i++)
				{
					if (actors.get (i).startsWith (srcActor.getName ()))
						currentBuffer[count] = currentBuffer[count-1] + srcPortRate;
					else if (actors.get (i).startsWith (dstActor.getName ()))
						currentBuffer[count] = currentBuffer[count-1] - dstPortRate;
					else
						throw new RuntimeException ("Illegal actor : " + actors.get (i) + " For Channel " + srcActor.getName () +"--" + dstActor.getName ());
					count++;
				}					
			}
			// calculate the peak buffer usage.
			Arrays.sort (currentBuffer);
			bufferCost += currentBuffer[currentBuffer.length-1];
			
			//System.out.println ("Channel "+srcActor.getName () + "--" + dstActor.getName () + " : " + currentBuffer[currentBuffer.length-1]);
		}
		return bufferCost;
	}

	/**
	 * Generate a model from the schedule.
	 * 
	 * @return Model containing start times, processor allocations for all the tasks
	 */
	private Map<String, String> generateModel () 
	{
		Map<String, String> result = new HashMap<String, String>();
		int maxExecutionTime = Integer.MIN_VALUE;
		int maxProc = Integer.MIN_VALUE;

		for (Actor actr : schedule.keySet ())
		{
			Integer[] schedParams = schedule.get (actr);
			result.put ("cpu"+actr.getName (), Integer.toString (schedParams[0]));
			result.put ("x"+actr.getName (), Integer.toString (schedParams[1]));

			if (schedParams[0] > maxProc)
				maxProc = schedParams[0];

			if (schedParams[1] + actr.getExecTime () > maxExecutionTime)
				maxExecutionTime = schedParams[1] + actr.getExecTime ();			
		}
		result.put ("latency", Integer.toString (maxExecutionTime));
		result.put ("totalProc", Integer.toString (maxProc));
		result.put ("totalBuffer", Integer.toString (calculateBuffer (result)));
		return result;
	}

	/**
	 * This function selects actor from the ready list.
	 * We can customize this function to do different variants in the list scheduling algorithm.
	 * 
	 * @return Selected ready actor
	 */
	private Actor selectReadyActor ()
	{
		if (readyList.isEmpty ())
			return null;

		// It would have been faster to select just the minimal actor in the list
		//  but for better debug purposes we want to see the whole sorted list
		Collections.sort (readyList, new Comparator<Actor>() {
			@Override
			public int compare (final Actor object1, final Actor object2) {
				// go through strategies and still if not comparable then apply RANDOM
				Iterator <Strategy> strategyIter = selectionStrategies.iterator (); 
				int diff =0;
				while (strategyIter.hasNext ()) {
					diff = compareByStrategy (strategyIter.next (), object1, object2);
					if (diff!=0) break;
				}
				// selection was made
				if (diff!=0) return diff;
				return compareByStrategy (Strategy.RANDOM, object1, object2);		        
			}
		});

		return readyList.get (0);
	}

	//=========================================
	//  Compute random strategy order
	//=======================================
	/**
	 * 
	 */
	private void computeRandomStrategy () 
	{
		Random rand = new Random ();
		int range = hsdf.countActors ();
		int maxRange = range;
		int i;

		ArrayList<Integer> originalList = new ArrayList<Integer>();
		for (i=0; i<maxRange; i++)  {
			originalList.add (i);
		}

		ArrayList<Integer> randomizedList = new ArrayList<Integer>();
		for (i=0; i<maxRange; i++, range--) 
		{
			int index = rand.nextInt (range);
			Integer rand_index = originalList.get (index);
			originalList.remove (index);
			randomizedList.add (rand_index);			
		}

		Iterator<Actor> totalActorIter = hsdf.getActors ();
		for (i=0; i<maxRange; i++)  {			
			Actor actor = totalActorIter.next ();
			randomStrategyOrder.put (actor, randomizedList.get (i));			
		}
	}


	/**
	 * This functions supports the comparator to select an actor
	 * between multiple ready actors.
	 * 
	 * ============================================================<br>
	 * Smallest one has higher priority<br>
	 * <br>
	 * < 0  means actor1 < actor2<br>
	 * > 0  means actor1 > actor2<br>
	 * = 0  means actor1 is not comparable to actor2<br>
	 * ============================================================<br>
	 * 
	 * @param strategy strategy to compare the actors
	 * @param actor1 an actor
	 * @param actor2 other actor
	 * @return Which actor should be selected according to the strategy
	 */
	private int compareByStrategy (Strategy strategy, Actor actor1, Actor actor2) {
		switch (strategy)
		{
		default:
			throw new RuntimeException ("Unknown strategy!");

		case RANDOM:
			int rand_index1 = randomStrategyOrder.get (actor1);
			int rand_index2 = randomStrategyOrder.get (actor2);
			int rand_diff   = rand_index1 - rand_index2;
			if (rand_diff == 0)
				throw new RuntimeException ("Bad random strategy!");
			return rand_diff;

		case LARGEST_PROC_TIME:
			int exe1 = actor1.getExecTime ();
			int exe2 = actor2.getExecTime ();
			int exe_diff = (-exe1) - (-exe2);  // largest one should have higher priority
			return exe_diff;

		case EARLIEST_START_TIME:
			int est1 = getMinStartTime (actor1);
			int est2 = getMinStartTime (actor2);
			int est_diff = est1 - est2;
			return est_diff;


		case LEXICOGRAPHIC:
			String actr1Name = actor1.getName ();
			String actr2Name = actor2.getName ();

			if (actr1Name.substring (0, actr1Name.indexOf ("_")).equals (actr2Name.substring (0, actr2Name.indexOf ("_"))))
			{
				// this is required when we have instances greater than 10. 
				// because if we compare A_10 and A_4, then it doesn't give A_4 < A_10, because of string compare.
				int instance1 = Integer.parseInt (actr1Name.substring (actr1Name.indexOf ("_")+1));
				int instance2 = Integer.parseInt (actr2Name.substring (actr2Name.indexOf ("_")+1));
				return (instance1 - instance2);
			}
			else
				return 0;			
		}
	}


	/**
	 * Depending on when the predecessors end, calculate the start time for the actor.
	 * 
	 * @param readyActor ready actor selected
	 * @return minimum start time available on the processors
	 */
	private int getMinStartTime (Actor readyActor) 
	{
		int minStartTime = 0;

		HashSet<Actor> predList = predecessors.get (readyActor);
		for (Actor pred : predList)
		{
			int predEndTime = (schedule.get (pred))[1] + pred.getExecTime ();
			if (predEndTime > minStartTime)
				minStartTime = predEndTime;
		}		
		return minStartTime;
	}

	/**
	 * Calculate ready actors
	 * 
	 * @param time current time
	 */
	private void calculateReadyActors (int time) 
	{		
		for (Actor actr : nonAllocatedActorList)
		{
			if (readyList.contains (actr))
				continue;

			HashSet<Actor> predecList = predecessors.get (actr);
			boolean ready = true;
			// System.out.println ("Actor : " + actr.getName ());
			for (Actor predec : predecList)
			{
				if (allocatedActorList.contains (predec) == false)
				{
					ready = false;
					break;
				}

				int predEndTime = (schedule.get (predec))[1] + predec.getExecTime ();
				if (predEndTime > time)
				{
					ready = false;
					break;
				}				
			}

			if (ready == true)
			{
				readyList.add (actr);
			}			
		}
	}

	/**
	 * Find processor available with minimum start time
	 * 
	 * @param processorTime array with processor times
	 * @return processor index with minimum start time
	 */
	private int selectProcWithMinTime (int[] processorTime) 
	{
		int minValue = Integer.MAX_VALUE;
		int minIndex = -1;
		for (int i=0;i<processorTime.length;i++)
		{
			if (processorTime[i]  == 0)
				return i;

			if (processorTime[i] < minValue)
			{
				minValue = processorTime[i];
				minIndex = i;
			}			
		}
		return minIndex;
	}
}
