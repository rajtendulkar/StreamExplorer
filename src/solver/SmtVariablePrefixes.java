package solver;

/**
 * This class contains declaration of all the prefixes used for variables in the solver. 
 * 
 * For every SMT variable we append this prefix in order to generate name 
 * of the variable. For example, the variable name start time for task A0
 * will be "xA0" derived as (startTimePrefix + task name). 
 * 
 * It is convenient to modify prefixes only at one place
 * 
 * @author Pranav Tendulkar
 */
public class SmtVariablePrefixes
{
	/* Design Flow Variables */
	/**
	 * DMA Forward transfer task prefix carrying tokens.
	 * From Writer cluster back to Reader cluster.
	 */
	public static final String dmaTokenTaskPrefix = "tok";
	
	/**
	 * DMA Status transfer task prefix carrying FIFO status.
	 * From Reader cluster back to writer cluster. 
	 */
	public static final String dmaStatusTaskPrefix = "stat";
	
	/**
	 * Function which will be called to update status of the FIFO.
	 * This will be used to in generating Schedule XML. 
	 */
	public static final String writerStatusUpdateTaskFunction = "FifoTxStatusUpdater";
	
	// Mapping and Partition Solver
	/**
	 * Cluster to which the task is allocated.
	 */
	public static final String clusterTaskPrefix = "clusterTask";
	
	/**
	 * Amount of work allocated to a cluster.
	 */
	public static final String clusterWorkAllocationPrefix = "workAllocatedCluster";
	
	/**
	 * Workload imbalance for a cluster
	 */
	public static final String workImbalancePrefix = "workImbalance";
	
	/**
	 * Execution time for an actor
	 */
	public static final String taskExecutionTimePrefix = "execTime";	
	
	/**
	 * Amount of Data Communicated between two actors
	 */
	public static final String dataCommunicatedPrefix = "dataComm";
	
	/**
	 *  Communication Cost for a channel
	 */
	public static final String commCostPrefix = "commCost";
	
	/**
	 * Total communication cost of the solution. 
	 */
	public static final String totalCommCostPrefix="totalCommCost";
	
	/**
	 * Total workload of the application. 
	 */
	public static final String totalWorkPrefix="totalWork";
	
	/**
	 * Total number of clusters used
	 */
	public static final String totalClustersUsedPrefix = "totalClustersUsed";
	
	/**
	 * Total workload imbalance 
	 */
	public static final String totalWorkImbalancePrefix = "totalWorkImbalance";
	
	/**
	 * Ideal Workload distribution = (total workload / number of processors)
	 */
	public static final String workDistributionPrefix = "workDistribution";
	
	/**
	 * Maximum workload on a cluster 
	 */
	public static final String maxWorkloadOnClusterPrefix = "maxWorkloadOnACluster";
	
	// Mapping Solver.
	
	/**
	 * Actor allocated to a cluster
	 */
	public static final String actorClusterAllocationPrefix = "_cluster";
	
	
	// public static final String distanceFunctionPrefix = "distance";
	
	// Generic Placement Solver
	/**
	 * Partition to cluster allocation
	 */
	public static final String partitionClusterAllocationPrefix = "clusterPartition";
		
	// Julien Solver
	/**
	 * Communication link to use
	 */
	public static final String linkPrefix="link-";
	
	/**
	 * Distance between two partitions
	 */
	public static final String distancePrefix = "dist";
	
	// Communication and Mapping Solver.
	
	/**
	 * Distance between clusters in x-dimension. 
	 */
	public static final String xDistancePrefix = "distX_";
	
	/**
	 * Distance between clusters in y-dimension. 
	 */
	public static final String yDistancePrefix = "distY_";
	
	// Pipelined and Non-Pipelined Scheduling Prefixs 	
	/**
	 * Total number of processors used
	 */
	public static final String totalProcPrefix = "totalProc";
	
	/**
	 * Total Buffer size of the schedule.
	 */
	public static final String totalBufferPrefix = "totalBuf";
	
	/**
	 * Latency of the schedule. 
	 */
	public static final String latencyPrefix = "latency";
	
	/**
	 * Communication Buffer used in a cluster 
	 */
	public static final String clusterBufferPrefix = "totalBufCluster_";
	
	/**
	 * Start time of a task
	 */
	public static final String startTimePrefix = "x";
	
	/**
	 * End time of a task 
	 */
	public static final String endTimePrefix = "y";
	
	/**
	 * End time of a task with communication task at its output.
	 * It has to start the DMA. 
	 */
	public static final String endDotTimePrefix = "yDot";
	
	/**
	 * Duration of a task. 
	 */
	public static final String durationPrefix = "d";
	
	/**
	 * Processor to which a task is allocated. 
	 */
	public static final String cpuPrefix = "cpu";
	
	/**
	 * Index for a buffer. It is an index defined for a task
	 * where a task produces / consumes tokens. 
	 */
	public static final String bufferIndexPrefix = "idx";
	
	/**
	 * Buffer size for a channel.
	 */
	public static final String bufferPrefix = "buf";
	
	/**
	 * Maximum buffer required for a channel. 
	 */
	public static final String maxBufferPrefix = "maxBuf";
	
	/**
	 * Buffer when a task starts/finishes to execute 
	 */
	public static final String bufferAtPrefix = "buffAt";
	
	/**
	 * Determines how many tasks started before this task.
	 */
	public static final String tasksStartedBeforePrefix = "tasksStartedBefore_";
	
	/**
	 * Determines how many tasks started after this task. 
	 */
	public static final String tasksEndedBeforePrefix = "tasksEndedBefore_";
	
	/**
	 * Processor utilization for Left edge constraints.
	 */
	public static final String procUtilPrefix = "procUtil_";

	/**
	 * Rate of producing tokens on a channel for an actor.
	 */
	public static final String productionRatePrefix = "p";
	
	/**
	 * Rate of consuming tokens on a channel for an actor. 
	 */
	public static final String consumptionRatePrefix = "c";
	
	/**
	 * Maximum CPU a task can use. Used for processory symmetry.
	 */
	public static final String maxCpuPrefix = "maxCpu";
	
	// Pipelined Scheduling 
	/**
	 * xPrime variable for pipelined scheduling.
	 */
	public static final String startPrimePrefix = "xPrime";
	
	/**
	 * yPrime variable for pipelined scheduling. 
	 */
	public static final String endPrimePrefix = "yPrime";
	
	/**
	 * k Start variable for pipelined scheduling. 
	 */
	public static final String kStartPrefix = "kx";
	
	/**
	 * k End variable for pipelined scheduling. 
	 */
	public static final String kEndPrefix = "ky";
	
	/**
	 * k max variable for pipelined scheduling.
	 */
	public static final String kMaxPrefix = "kmax";
	
	/**
	 * Determines the order of the task.
	 */
	public static final String afterVarPrefix = "after_";
	
	/**
	 * Number of initial tokens in the channel. 
	 */
	public static final String initialTokenPrefix = "initTokens";
	
	/**
	 * Processor utilization at the start of the period. 
	 */
	public static final String procUtilAtPeriodStart = "procUtilAtPeriodStart";
	
	/**
	 * K predicted variable.
	 */
	public static final String predKVarPrefix = "kPred";
	
	/**
	 * Period of the schedule.
	 */
	public static final String periodPrefix = "period";
	
	/**
	 * Cap X variable for Omega Analysis.
	 */
	public static final String capXPrefix = "capX";
	
	/**
	 * Cap Y variable for Omega Analysis.
	 */
	public static final String capYPrefix = "capY";
	
	/**
	 * Cap Pred X variable for Omega Analysis.
	 */
	public static final String capPredXPrefix = "capPredX";
	
	// Declarations specific to the Matrix solver.
	/**
	 * Index of a task execution in the matrix
	 */
	public static String taskIndexPrefix = "index";
	/**
	 * Schedule matrix
	 */
	public static String schedMatrixPrefix = "schedMatrix";
	/**
	 * Name for the max function
	 */
	public static String maxFunctionPrefix = "max_integer";
	/**
	 * Enable time of a task
	 */
	public static String enableTimePrefix = "enable_";
}