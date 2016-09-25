package graphanalysis.scheduling;
import java.util.*;
import java.util.Map.Entry;

import solver.SmtVariablePrefixes;
import spdfcore.Graph;

/**
 * Convert Left Edge model to schedule
 * 
 * @author Peter Poplavko
 *
 */
public class LeftEdgeModelToSchedule
{	
	// Function to sort any hashmap by values.
	static <K,V extends Comparable<? super V>> SortedSet<Map.Entry<K,V>> entriesSortedByValues (Map<K,V> map) 
	{
        SortedSet<Map.Entry<K,V>> sortedEntries = new TreeSet<Map.Entry<K,V>>(
            new Comparator<Map.Entry<K,V>>() 
            {
                @Override public int compare (Map.Entry<K,V> e1, Map.Entry<K,V> e2) 
                {
                    int res = e1.getValue ().compareTo (e2.getValue ());
                    return res != 0 ? res : 1; // Special fix to preserve items with equal values
                }
            }
        );
        sortedEntries.addAll (map.entrySet ());
        return sortedEntries;
    }
	
	public Map<String,Integer> nonPipelined (Graph graph, Graph hsdf, Map<String, Integer> model)
	{
		Map<String,Integer> result = new TreeMap<String, Integer>();
		HashMap<String,Integer> startTimes = new HashMap<String, Integer>();
		int totalProc = model.get (SmtVariablePrefixes.totalProcPrefix);
		
		for (Entry<String, Integer> entry : model.entrySet ())
		{
			// We discard these headers.
			if (entry.getKey ().contains (SmtVariablePrefixes.tasksStartedBeforePrefix) || 
					entry.getKey ().contains (SmtVariablePrefixes.tasksEndedBeforePrefix) ||
					entry.getKey ().contains (SmtVariablePrefixes.procUtilPrefix))
				continue;
			else
			{
				// Put all the other headers in the result.
				result.put (entry.getKey (), entry.getValue ());
				
				// Collect the start times of tasks seperately for processing.
				if (entry.getKey ().startsWith (SmtVariablePrefixes.startTimePrefix))
					startTimes.put (entry.getKey (), entry.getValue ());					
			}
		}		
		
		// Sort the tasks according to their start times.
		SortedSet<Entry<String, Integer>> sortedStartTimes = entriesSortedByValues (startTimes);
		
		// Begin the actual processor allocation.
		int processor = 0;
		while (sortedStartTimes.isEmpty () == false)
		{
			int previousEndTime = 0;
			
			Iterator<Entry<String, Integer>> iterStart = sortedStartTimes.iterator ();			
			while (iterStart.hasNext ())
			{
				Entry<String, Integer> entry = iterStart.next ();
				String taskName = entry.getKey ().substring (1);				
				int taskDuration = hsdf.getActor (taskName).getExecTime ();
				int taskStartTime = entry.getValue ();
				
				// If the tasks don't overlap, allocate them a processor.
				if (taskStartTime >= previousEndTime)
				{
					result.put (SmtVariablePrefixes.cpuPrefix+taskName, processor);
					iterStart.remove ();
					previousEndTime = taskStartTime + taskDuration;
				}				
			}
			// Lets move to next processor for task allocation.
			processor++;
		}
		
		// Check if what we allocate is equal to computed by sat solver.
		if (processor != totalProc)
			throw new RuntimeException ("Expected Processors : " + totalProc + " Allocated Processors : " + processor);
		
		return result;		
	}	

	public static class Task
	{
		public final Object obj;

		public Task (Object ref)
		{
			obj = ref;
		}
		@Override
		public int hashCode ()
		{
			return obj.hashCode ();
		}
		@Override
		public boolean equals (Object other_task)
		{
			Task other = (Task) other_task;
			return other.obj.equals (obj);
		}
		@Override
		public String toString ()
		{
			return obj.toString ();
		}
	}

	//---------------------------------------------------
	public static class Int
	{
		public final int value;
		// must be final, too dangerous to make it modifiable (e.g. all copies of this class change their value,
		// which can have bad consequences for mapping to Proc, a derivative of Int

		public Int (int v)
		{
			value = v;
		}
		@Override
		public int hashCode ()
		{
			return value;
		}
		@Override
		public boolean equals (Object obj)
		{
			Int other = (Int) obj;
			return other.value == value;
		}
		@Override
		public String toString ()
		{
			return String.valueOf (value);
		}
	}

	//---------------------------------------------------
	public static class Proc extends Int
	{
		public Proc (int v)
		{
			super (v);
		}
	}

	//---------------------------------------------------
	public static class Iter extends Int
	{
		public Iter (int v)
		{
			super (v);
		}
	}

	//---------------------------------------------------
	public static class IterTask
	{
		public final Iter iter;
		public final Task task;

		public IterTask (Iter ini_iter, Task ini_task)
		{
			iter = ini_iter;
			task = ini_task;
		}
		@Override
		public int hashCode ()
		{
			return iter.hashCode () + task.hashCode ();
		}
		@Override
		public boolean equals (Object obj)
		{
			IterTask other = (IterTask) obj;
			return other.task.equals (task) && other.iter.equals (iter);
		}
		@Override
		public String toString ()
		{
			return task.toString () + "[" + iter.toString () + "]";
		}
	}

	//---------------------------------------------------
	public static class UnrolMapping
	{
		public int unrol_factor = -1;
		public HashMap< IterTask, Proc > mapping = new HashMap<IterTask, Proc>();
	}

	//---------------------------------------------------
	public static interface Model
	{
		public Set<Task> taskSet ();
		public int startTime (Task task);
		public int executionTime (Task task);
	}

	//----------------------------------------------------
	public static int get_latency (Model m)
	{
		Set<Task> tasks = m.taskSet ();
		Iterator<Task> taskiter = tasks.iterator ();
		int latency = 0;
		while (taskiter.hasNext ())
		{
			Task task = taskiter.next ();
			int x = m.startTime (task);
			int d = m.executionTime (task);
			int y = x + d;
			if (y > latency) latency = y;
		}
		return latency;
	}

	//---------------------------------------------------
	/**
	 *
	 * UnfoldModel : private temporary schedule and mapping, use UnrolMapping to access the results of LeftEdge.
	 *    <p>
	 *    In UnfoldModel, a given Model gets unfolded virtually infinite number of iterations
	 *    with a given period.
	 */
	static class UnfoldModel
	{
		Model m;
		int period;
		ArrayList<Task> taskList = new ArrayList<Task>();	// tasks sorted by xPrime, i.e. start_time % period
		HashMap<IterTask, Proc> mapping = new HashMap<IterTask, Proc>();

		public UnfoldModel (Model ini_m, int ini_period)
		{
			m      = ini_m;
			period = ini_period;

			taskList.addAll (m.taskSet ());

			// sort by "xPrime", i.e. start_time % period
			Collections.sort (taskList, new Comparator<Task>()
			{
				@Override
				public int compare (final Task task1, final Task task2)
				{
					int xPrime1 = m.startTime (task1) % period;
					int xPrime2 = m.startTime (task2) % period;
					if (xPrime1 != xPrime2) return xPrime1 - xPrime2;
					return task1.toString ().compareTo (task2.toString ());
				}
			});

		}

		public int  getPeriod ()
		{
			return period;
		}
		public void putMapping (IterTask task, Proc proc)
		{
			mapping.put (task, new Proc (proc.value));
		}
		public Proc getMapping (IterTask task)
		{
			return mapping.get (task);
		}

		public int startTime (IterTask itertask)
		{
			return m.startTime (itertask.task) + period * itertask.iter.value;
		}

		public int executionTime (IterTask itertask)
		{
			return m.executionTime (itertask.task);
		}

		LinkedList<IterTask> getSortedTasksForPeriod (int from_time)
		{
			if ( (from_time % period) != 0)
			{
				throw new RuntimeException ("from_time must be a multiple of period!");
			}
			int from_k = from_time / period;

			LinkedList<IterTask> itertaskList = new LinkedList<IterTask>();

			int previous_time_debug = 0;
			Iterator<Task> taskit = taskList.iterator ();
			while (taskit.hasNext ())
			{
				Task task = taskit.next ();
				int x  = m.startTime (task); // first time this task starts
				int k  = x / period;        // the period where it starts
				if (from_k >= k)            // task is present in period from_k
				{
					Iter iter = new Iter (from_k - k);
					IterTask itertask = new IterTask (iter, task);
					itertaskList.add (itertask);
					// debug the sorting of itertasks
					int x_iter = startTime (itertask);
					if (x_iter < from_time || x_iter >= from_time + period || x_iter < previous_time_debug)
					{
						throw new RuntimeException ("Bug in itertask!");
					}
					previous_time_debug = x_iter;
				}
			}

			return itertaskList;
		}
	}

	//-----------------------------------------------------
	static class OccupancyVector
	{
		int occupancy[];

		public OccupancyVector (int totalProc)
		{
			occupancy = new int[totalProc];
			Arrays.fill (occupancy, 0); // though java must have done it, do it again for clarity
		}
		public void put (Proc proc, int ini_occupancy)
		{
			occupancy[proc.value] = ini_occupancy;
		}
		public int get (Proc proc)
		{
			return occupancy[proc.value];
		}
		public int getTotalProc ()
		{
			return occupancy.length;
		}
		@Override
		public int hashCode ()
		{
			return Arrays.hashCode (occupancy);
		}
		@Override
		public boolean equals (Object obj)
		{
			OccupancyVector other = (OccupancyVector) obj;
			return Arrays.equals (other.occupancy, occupancy);
		}
		@Override
		public String toString ()
		{
			return Arrays.toString (occupancy);
		}
	}

	//--------------------------------------------------------------------------
	static OccupancyVector basicLeftEdge (OccupancyVector residual, UnfoldModel unf_model, int from_time)
	{
		int period  = unf_model.getPeriod ();
		int to_time = from_time + period;

		LinkedList<IterTask> sortedIterTasks = unf_model.getSortedTasksForPeriod (from_time);
		ListIterator<IterTask> taskit;

		Proc proc;
		int totalProc = residual.getTotalProc ();
		OccupancyVector final_residual = new OccupancyVector (totalProc);

		for (proc = new Proc (0); !sortedIterTasks.isEmpty () && proc.value < totalProc; proc = new Proc (proc.value + 1))
		{
			int occupancy = residual.get (proc) + from_time;
			taskit = sortedIterTasks.listIterator ();
			while (taskit.hasNext ())
			{
				IterTask task = taskit.next ();
				int x = unf_model.startTime (task);
				if (x >= occupancy)
				{
					unf_model.putMapping (task, proc);
					taskit.remove ();
					int d = unf_model.executionTime (task);
					occupancy = x + d;
				}
			}
			final_residual.put (proc, Math.max (0, occupancy - to_time));
		}
		if (!sortedIterTasks.isEmpty ())
		{
			throw new RuntimeException ("totalProc: " + totalProc + " is not enough to map the tasks!");
		}
		return final_residual;
	}

	//======================================
	//============= COMPUTE ================
	//======================================
	public static UnrolMapping compute (Model m, int period, int totalProc)
	{
		int latency = get_latency (m);
		int gamma   = (int) Math.ceil ( ( (double) latency) / period);
		UnfoldModel unf_model = new UnfoldModel (m, period);

		// Run the left-edge period-by period and detect machine periodicity
		//  (a) initialization
		OccupancyVector residual = new OccupancyVector (totalProc);
		int k;
		for (k = 0; k < gamma - 1; k++)
		{
			int from_time = k * period;
			residual = basicLeftEdge (residual, unf_model, from_time);
		}

		// (b) detect machine periodicity
		int large_number = 10000;  // give up looking for machine periodicity after this many periods
		HashMap<OccupancyVector, Iter> previous_occupancies = new HashMap<OccupancyVector, Iter>();
		Iter prev_k = null;
		for (; k < large_number; k++)
		{
			int from_time = k * period;
			previous_occupancies.put (residual, new Iter (k));
			residual = basicLeftEdge (residual, unf_model, from_time);
			prev_k = previous_occupancies.get (residual);
			if (prev_k != null) break;
		}

		if (prev_k == null)
		{
			throw new RuntimeException ("Unable to detect periodicity!");
		}

		// Copy the mapping in interval prev_k .. k
		UnrolMapping map = new UnrolMapping ();
		int final_k = k;
		map.unrol_factor = 1 + final_k - prev_k.value; // beta_machine
		//
		//  note, we dont simply copy the mapping, we "fold" the iteration
		//  of the task to interval 0...beta_machine
		//
		for (k = prev_k.value; k <= final_k; k++)
		{
			int from_time = k * period;
			LinkedList<IterTask> sortedIterTasks = unf_model.getSortedTasksForPeriod (from_time);
			ListIterator<IterTask> taskit = sortedIterTasks.listIterator ();
			while (taskit.hasNext ())
			{
				IterTask itertask  = taskit.next ();
				Proc  map_proc = unf_model.getMapping (itertask);
				int  iter = itertask.iter.value;
				Task task = itertask.task;
				Iter folded_iter = new Iter (iter % map.unrol_factor); // 0 .. beta - 1
				IterTask folded_itertask = new IterTask (folded_iter, task);
				map.mapping.put (folded_itertask, map_proc);
			}
		}
		return map;
	}
}
