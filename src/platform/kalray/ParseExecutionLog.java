package platform.kalray;


import java.io.*;
import java.util.*;

import output.GanttChart;
import output.GanttChart.Record;

/**
 * Parse the execution log generated from the Kalray platform.
 * 
 * @author Pranav Tendulkar
 */
public class ParseExecutionLog
{ 
	// I expect the following format in the rawData.txt	
	// actor : VLD cluster : 1 proc : 0 procSchedIndex : 0 instanceId : 0 iteration : 0 time : 412208	
	// From here onwards I will make sure I will be able to plot the Gantt Chart.

	// We will write the location where the values are to be found.
	/**
	 * Add plain blocks to the task execution where it starts and end FIFO writing.
	 */
	public boolean addFifoBlockingActors = true;
	
	/**
	 * Position where the actor name is located in the log file String. 
	 */
	private final int actorLocation = 2;
	
	/**
	 * Position where the cluster id is located in the log file String. 
	 */
	private final int clusterIdLocation = 5;
	
	/**
	 * Position where the processor id is located in the log file String. 
	 */
	private final int procIdLocation = 8;
	
	/**
	 * Position where the index in the processor schedule is located in the log file String. 
	 */
	private final int procSchedIndexLocation = 11;
	
	/**
	 * Position where the instance id is located in the log file String. 
	 */
	private final int instanceIdLocation = 14;
	
	/**
	 * Position where the iteration index is located in the log file String. 
	 */
	private final int iterationLocation = 17;
	
	/**
	 * Position where the time0 is located in the log file String.
	 * It is the time where task start FIFO read/write. 
	 */
	private final int t0Location = 20;
	
	/**
	 * Position where the time1 is located in the log file String.
	 * It is the time where the FIFO read/write start completes and actor
	 * starts to execute.  
	 */
	private final int t1Location = 23;
	
	/**
	 * Position where the time1 is located in the log file String.
	 * It is the time where the  actor finishes to execute and starts 
	 * FIFO read/write (DMA transfer). 
	 */
	private final int t2Location = 26;
	
	/**
	 * Position where the time3 is located in the log file String.
	 * It is the time where task ends FIFO read/write. 
	 */
	private final int t3Location = 29;
	
	/**
	 * 
	 */
	LinkedHashMap<String, Integer> actorInstances;
	
	/**
	 * Array of all log records 
	 */
	LogRecord logRecords[];
	
	/**
	 * total instances in the graph
	 */
	int totalInstances;
	
	/**
	 * total number of iterations 
	 */
	int totalIterations;

	/**
	 * Log record object which has information about
	 * one instance of actor executed includes its start and
	 * end times, processor allocated etc.
	 * 
	 * @author Pranav Tendulkar
	 *
	 */
	private class LogRecord implements Comparable<LogRecord>
	{
		private String actorName;
		private int iteration;
		private int instanceId;
		private long fifoStartTime;				// proc starts to start fifo reader n writer
		private long actorExecStartTime;		// actor starts to execute.
		private long fifoEndTime;				// actor finishes and proc starts to end fifo reader n writer
		private long finishTime;				// fifo end finishes.
		private int schedIndex;
		private int procId; 
		private int clusterId;

		
		/**
		 * Initialize the log record object.
		 * 
		 * @param actorName name of the actor
		 * @param iteration iteration index
		 * @param instanceId instance id
		 * @param schedIndex index of schedule on the processor
		 * @param procId processor id
		 * @param clusterId cluster id 
		 * @param fifoStartTime fifo read/write start time
		 * @param actorExecStartTime actor execution start time
		 * @param fifoEndTime DMA write start time
		 * @param finishTime finish time
		 */
		public LogRecord (String actorName, int iteration, int instanceId, int schedIndex,  int procId, int clusterId,
				long fifoStartTime, long actorExecStartTime, long fifoEndTime, long finishTime)
		{
			this.actorName = new String(actorName);
			this.iteration = iteration;
			this.instanceId = instanceId;
			this.fifoStartTime = fifoStartTime;
			this.actorExecStartTime = actorExecStartTime;
			this.fifoEndTime = fifoEndTime;
			this.finishTime = finishTime;
			this.schedIndex = schedIndex;
			this.procId = procId; 
			this.clusterId = clusterId;
		}
		
		@Override
		public String toString()
		{
			return "Iteration : " + iteration + " Actor : " + actorName 
								  + " instance : " + instanceId + " cluster : " 
								  + clusterId + " proc : " + procId + " schedIndex : " + schedIndex;
		}

		@Override
		public int compareTo(LogRecord o)
		{
			if(iteration < o.iteration)
				return -1;
			else if(iteration > o.iteration)
				return 1;
			else
			{
				// they belong to the same iteration.
				if (clusterId < o.clusterId)
					return -1;
				else if (clusterId > o.clusterId)
					return 1;
				else
				{
					// they are on same cluster.
					if (procId < o.procId)
						return -1;
					else if (procId > o.procId)
						return 1;
					else
					{
						// they are on the same processor.
						if (schedIndex < o.schedIndex)
							return -1;
						else if (schedIndex > o.schedIndex)
							return 1;
						else 
							return 0;
					}
				}
			}
		}
	}

	/**
	 * Initialize parse log object
	 */
	public ParseExecutionLog()
	{
		actorInstances = new LinkedHashMap<String, Integer>();
		logRecords = null;
		totalInstances = 0;
		totalIterations = 0;
	}
	
	/**
	 * Plot Gannt chart
	 * 
	 * @param iterationId iteration index
	 * @param outputFileName output file name
	 */
	public void plotGanntChart (int iterationId, String outputFileName)
	{
		GanttChart ganttChart = new GanttChart ();
		ganttChart.addNamesToActorInGraph = true;
		ganttChart.textRotationAngle = 0;
		ganttChart.addLegendInGraph = false;		
		
		// We have implemented a sorted array of records. this saves us a lot of trouble ! :)		
		int startIndex = iterationId * totalInstances;
		
		int prevCluster = logRecords[startIndex].clusterId;
		int prevProc = logRecords[startIndex].procId;		
		int procIndex = 0;
		
		int count = 0;
		HashMap<String, Integer> actrColor = new HashMap<String, Integer>();
		for(String actr : actorInstances.keySet())
			actrColor.put(actr, count++);
		
		long minTime = Long.MAX_VALUE;
		
		for(int i=0;i<totalInstances;i++)
		{			
			LogRecord logRecord = logRecords[startIndex+i];
			if (minTime > logRecord.fifoStartTime)
				minTime = logRecord.fifoStartTime;
		}
		
		
		for(int i=0;i<totalInstances;i++)
		{			
			LogRecord logRecord = logRecords[startIndex+i];
			
			if (prevCluster != logRecord.clusterId || prevProc != logRecord.procId)
			{
				procIndex++;
				prevCluster = logRecord.clusterId;
				prevProc = logRecord.procId;				
			}
			
			if(addFifoBlockingActors == true)
			{
				// One actor before execution.
				Record record1 = ganttChart.new Record ("C"+Integer.toString(logRecord.clusterId)+"--"+"Proc"+Integer.toString(logRecord.procId), 
						procIndex, 
						logRecord.fifoStartTime-minTime, 
						logRecord.actorExecStartTime-1-minTime, 
						logRecord.actorName+"_"+Integer.toString(logRecord.instanceId)+"_fifo_begin", "#FFFFFF");
				
				record1.printNameInGraph = false;
				ganttChart.addRecord(record1);
				
				// One actor after execution.
				Record record2 = ganttChart.new Record ("C"+Integer.toString(logRecord.clusterId)+"--"+"Proc"+Integer.toString(logRecord.procId), 
						procIndex, 
						logRecord.fifoEndTime-minTime, 
						logRecord.finishTime-minTime, 
						logRecord.actorName+"_"+Integer.toString(logRecord.instanceId)+"_fifo_end", "#FFFFFF");
				
				record2.printNameInGraph = false;
				ganttChart.addRecord(record2);
			}
				
			Record record = ganttChart.new Record ("C"+Integer.toString(logRecord.clusterId)+"--"+"Proc"+Integer.toString(logRecord.procId), 
										procIndex, 
										logRecord.actorExecStartTime-minTime, 
										logRecord.fifoEndTime-1-minTime, 
										logRecord.actorName+"_"+Integer.toString(logRecord.instanceId));
			
			
			ganttChart.addRecord(record);			
		}
		
		ganttChart.plotChart(outputFileName, -1);	
	}

	/**
	 * Parse execution log file.
	 * 
	 * @param logFileName execution log file name
	 */
	public void parseLogFile (String logFileName)
	{
		try
		{
			long minStartTime = Long.MAX_VALUE;
			totalIterations = Integer.MIN_VALUE;
			int maxCluster = 0;
			int maxProc = 0;
			BufferedReader br = null;
			String strLine;

			LineNumberReader  lnr = new LineNumberReader(new FileReader(new File(logFileName)));
			lnr.read(); lnr.read();
			lnr.skip(Long.MAX_VALUE);
			int numLines = lnr.getLineNumber();

			lnr.close();
			lnr = new LineNumberReader(new FileReader(new File(logFileName)));

			while ((strLine = lnr.readLine ()) != null)
			{
				String split[] = strLine.split(" ");
				int iteration = Integer.parseInt(split[iterationLocation]);
				int clusterId = Integer.parseInt(split[clusterIdLocation]);
				int procId = Integer.parseInt(split[procIdLocation]);
				String actorName = split[actorLocation];
				int instanceId = Integer.parseInt(split[instanceIdLocation]);
				// by construction t0 < t1 < t2 < t3
				long t0 = Long.parseLong(split[t0Location]);
				
				if(t0 < minStartTime)
					minStartTime = t0;

				if (actorInstances.containsKey(actorName))
				{
					int currInstanceId = actorInstances.get(actorName);
					if (currInstanceId < instanceId)
						actorInstances.put(actorName, instanceId);
				}
				else
					actorInstances.put(actorName, instanceId);

				if(clusterId > maxCluster)
					maxCluster = clusterId;
				if(procId > maxProc)
					maxProc = procId;

				if(iteration > totalIterations)
					totalIterations = iteration;
			}
			
			lnr.close();

			// Increment actors
			totalInstances = 0;
			for(String actr : actorInstances.keySet())
			{
				int instances = actorInstances.get(actr) + 1;
				actorInstances.put(actr, instances);
				totalInstances += instances;
			}

			// We assume that all the indexes start from zero, which is a safe assumption.
			totalIterations ++;
			maxCluster++;
			maxProc++;

			if(numLines % totalIterations != 0)
				throw new RuntimeException("Is there any missing line? We are not able to match lines with number of iterations");

			logRecords = new LogRecord[totalInstances*totalIterations];			

			System.out.println("Num Lines : " + numLines + " Iterations " + totalIterations + " Actors : " + actorInstances.size() 
					+ " Max clusters : " + maxCluster + " Max Proc " + maxProc);

			// Reopen the log file.
			br = new BufferedReader (new InputStreamReader (new DataInputStream (new FileInputStream (logFileName))));

			int count = 0;
			while ((strLine = br.readLine ()) != null)
			{
				String split[] = strLine.split(" ");

				String actorName = split[actorLocation];
				int clusterId = Integer.parseInt(split[clusterIdLocation]);
				int procId = Integer.parseInt(split[procIdLocation]);
				int procSchedIndex = Integer.parseInt(split[procSchedIndexLocation]);
				int instanceId = Integer.parseInt(split[instanceIdLocation]);
				int iteration = Integer.parseInt(split[iterationLocation]);
				long t0 = Long.parseLong(split[t0Location]) - minStartTime;
				long t1 = Long.parseLong(split[t1Location]) - minStartTime;
				long t2 = Long.parseLong(split[t2Location]) - minStartTime;
				long t3 = Long.parseLong(split[t3Location]) - minStartTime;
				
				logRecords[count++] = new LogRecord (actorName, iteration, instanceId, procSchedIndex,  procId, clusterId, t0, t1, t2, t3);
			}
			
			// Sorting is very important as of now.
			Arrays.sort(logRecords);

			br.close();
		} catch (IOException e) { e.printStackTrace (); }
	}
}
