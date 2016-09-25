package input;

/**
 * Process command line arguments for the experiments.
 * 
 * @author Pranav Tendulkar
 */
public class CommandLineArgs 
{
	/**
	 * Latency scaling factor
	 */
	public double maxLatencyScalingFactor = 1.0;
	
	/**
	 * Time out per query in seconds
	 */
	public int timeOutPerQueryInSeconds = 180;
	
	/**
	 * Global time out in seconds 
	 */
	public int totalTimeOutInSeconds = 1800;
	
	/**
	 * Number of clusters to be used 
	 */
	public int clusterConstraint = 0;
	
	/**
	 * Number of processors to be used
	 */
	public int processorConstraint = 0;
	
	/**
	 * A constraint on Period 
	 */
	public int periodConstraint = 1;
	
	/**
	 * A constraint on Latency
	 */
	public int latencyConstraint = 0;
	
	/**
	 * Application Graph file name
	 */
	public String applicationGraphFileName = "";
	
	/**
	 * Platform graph file name
	 */
	public String platformGraphFileName = "";
	
	/**
	 * Output Directory
	 */
	public String outputDirectory = "";
	
	/**
	 * Hardware execution log file 
	 */
	public String hardwareLogFileName = "";
	
	/**
	 * Output Gantt chart file name 
	 */
	public String ganttChartFileName = "";
	
	/**
	 * Profile XML file name 
	 */
	public String profileXmlFileName="";
	
	/**
	 * Output XML file name 
	 */
	public String outputXmlFileName="";
	
	/**
	 * Perform omega Analysis 
	 */
	public boolean omegaAnalysis = false;
	
	/**
	 * Generate HSDF graph
	 */
	public boolean printHsdf = false;
	
	/**
	 * Add processor symmetry constraints 
	 */
	public boolean processorSymmetry = false;
	
	/**
	 * Add task symmetry constraints 
	 */
	public boolean graphSymmetry = false;
	
	/**
	 * Perform buffer analysis 
	 */
	public boolean bufferAnalysis = false;
	
	/**
	 * Use functions in SMT solving for Buffer Analysis 
	 */
	public boolean bufferAnalysisWithFunctions = false;
	
	/**
	 * Left Edge Analysis
	 */
	public boolean leftEdge = false;
	
	/**
	 * Mutual Exclusion
	 */
	public boolean mutualExclusionGraphAnalysis = false;
	
	/**
	 * Use Quantifier in SMT solving 
	 */
	public boolean useQuantifier = false;
	
	/**
	 * 
	 */
	public boolean useMaxFunction = false;
	
	/**
	 * Use type variables in SMT solving for pipelined scheduling  
	 */
	public boolean typeDifferentiateAlgo = false;
	
	/**
	 * Use tetris symmetry 
	 */
	public boolean tetrisSymmetry = false;
	
	/**
	 * Disable Prime variables 
	 */
	public boolean disablePrime = false;
	
	/**
	 * Test Period Symmetry 
	 */
	public boolean periodSymmetry = false;
	
	/**
	 * Use minimum latency for period exploration 
	 */
	public boolean minLatencyForPeriodExpl = false;
	
	/**
	 * 
	 * Pipelined Scheduling Solvers : UNFOLDING_SOLVER -- PERIOD_LOCALITY
	 * Non-Pipelined Scheduling Solvers : MATRIX_SOLVER -- MUTUAL_EXCLUSION
	 * 
	 * @author Pranav Tendulkar
	 *
	 */
	public enum SolverType {UNFOLDING_SOLVER, PERIOD_LOCALITY, MATRIX_SOLVER, MUTUAL_EXCLUSION, DUMMY_INVALID};	
	
	/**
	 * Solver to use for SMT Solving 
	 */
	public SolverType solver = SolverType.DUMMY_INVALID;	
	
	/**
	 * Initialize the command line parser object
	 * 
	 * @param args command line arguments
	 */
	public CommandLineArgs (String[] args)
	{		
		parseArgs (args);
	}
	
	/**
	 * The last field in the whole path contains the file name.
	 * We extract it in this function.
	 * 
	 * @param path Whole path string including filename
	 * @return the last part of the string containing file name
	 */
	public String extractNameFromPath (String path)
	{
		String parts[] = path.split("/");
		String parts2[] = parts[parts.length-1].split("\\.");
		String name = parts2[0];
		
		return name;
	}

	/**
	 * Parse which solver to use.
	 * 
	 * @param argument the solver to use
	 * @return Enum type which solver to use.
	 */
	private SolverType parseSolverType (String argument)
	{
		if (argument.equalsIgnoreCase ("unfolding"))
			return SolverType.UNFOLDING_SOLVER;
		else if (argument.equalsIgnoreCase ("periodLocality"))
			return SolverType.PERIOD_LOCALITY;
		else if (argument.equalsIgnoreCase ("matrixSolver")) 
			return SolverType.MATRIX_SOLVER;
		else if (argument.equalsIgnoreCase ("mutualExclusion")) 
			return SolverType.MUTUAL_EXCLUSION;
		else
			throw new RuntimeException ("Was expecting Solver Type unfolding / periodLocality, but found " + argument);
	}
	
	/**
	 * Check if an input is boolean true or false.
	 * 
	 * @param argument String containing "True" or "False"
	 * @return boolean true or false
	 */
	private boolean stringToBoolean (String argument)
	{
		if (argument.equalsIgnoreCase ("true"))
			return true;
		else if (argument.equalsIgnoreCase ("false"))
			return false;
		else
			throw new RuntimeException ("Was expecting True or False, but found " + argument);
	}
	
	/**
	 * Print current state of all the arguments
	 */
	public void printConfig ()
	{
		// System.out.println ("Configuration : ");
		System.out.println ("Local Time Out : " 		 	  + timeOutPerQueryInSeconds + " seconds");
		System.out.println ("Global Time Out : " 		  + totalTimeOutInSeconds + " seconds");
		System.out.println ("Application Graph File Name : " + applicationGraphFileName);
		System.out.println ("Profile Graph File Name : " + profileXmlFileName);
		System.out.println ("Output Xml File Name : " + outputXmlFileName);
		System.out.println ("Platform Graph File Name : " + platformGraphFileName);
		System.out.println ("Hardware Log File Name : " + hardwareLogFileName);
		System.out.println ("Gantt Chart File Name : " + ganttChartFileName);
		System.out.println ("Output Graph Dir : " 		  + outputDirectory);
		System.out.println ("Print HSDF Graph : " 		  + printHsdf);
		System.out.println ("Use Quantifiers : " 		  + useQuantifier);
		System.out.println ("Use Max Integer Function : "  + useMaxFunction);
		System.out.println ("Enable Processor Symmetry : " + processorSymmetry);
		System.out.println ("Enable Graph Symmetry : " 	  + graphSymmetry);
		System.out.println ("Enable Buffer Analysis : " 	  + bufferAnalysis);
		System.out.println ("Enable Functions in Buffer Analysis : " + bufferAnalysisWithFunctions);
		System.out.println ("Use Left Edge Algorithm : "  + leftEdge);				
		System.out.println ("Enable Mutual Exclusion Graph Analysis : " + mutualExclusionGraphAnalysis);
		System.out.println ("Use Quantifiers in Sched Matrix : " + useQuantifier);
		System.out.println ("Use Max Integer Function in Sched Matrix : " + useMaxFunction);
		System.out.println ("Processor Constraint for Solver : " + processorConstraint);
		System.out.println ("Type I Type II Algorithm for Pipelined Constraints : " + typeDifferentiateAlgo);
		System.out.println ("Omega Constraints for Pipelined Constraints : " + omegaAnalysis);
		System.out.println ("Factor to Scale Max Latency : " + maxLatencyScalingFactor);
		System.out.println ("Enable Minimum Latency for Period Exploration : " + minLatencyForPeriodExpl);		
		System.out.println ("Tetris Symmetry : " + tetrisSymmetry);
		System.out.println ("Disable Prime Variables : " + disablePrime);
		System.out.println ("Period Symmetry Constraints : " + periodSymmetry);
		System.out.println ("Period Constraint : " + periodConstraint);
		System.out.println ("Latency Constraint : " + latencyConstraint);
		System.out.println ("Cluster Constraint : " + clusterConstraint);
		System.out.println ("Solver : " + solver.toString ());
	}
	
	/**
	 * Print help for arguments
	 */
	public void printHelp ()
	{
		System.out.println ("FLAG : DESCRIPTION : DEFAULT VALUE");
		System.out.println ("-localtimeout <Local TimeOut in Seconds> : Local Time Out in Seconds : " + timeOutPerQueryInSeconds);
		System.out.println ("-globaltimeout <Global TimeOut in Seconds> : Global Time Out in Seconds : " + totalTimeOutInSeconds);
		System.out.println ("-ag <Application Graph File Name> : Change the Application Graph File : " + applicationGraphFileName);
		System.out.println ("-px <Profile Xml File Name> : Change the Profile XML File : " + profileXmlFileName);
		System.out.println ("-ox <Output Xml File Name> : Change the Output XML File : " + outputXmlFileName);		
		System.out.println ("-pg <Platform Graph File Name> : Change the Platform Graph File : " + platformGraphFileName);
		System.out.println ("-lg <Hardware Log File Name> : Change the Hardware Log File : " + hardwareLogFileName);
		System.out.println ("-gc <Gantt Chart File Name> : Change the Hardware Log File : " + ganttChartFileName);
		System.out.println ("-od <Output Files Directory> : Change the Output Graph Directory : " + outputDirectory);
		System.out.println ("-printHsdf <True / False> : Print HSDF Graph : " + printHsdf);
		System.out.println ("-psym <True / False> : Enable Processor Symmetry : " + processorSymmetry);
		System.out.println ("-gsym <True / False> : Enable Graph Symmetry : " + graphSymmetry);
		System.out.println ("-buffer <True / False> : Enable Buffer Analysis : " + bufferAnalysis);
		System.out.println ("-bufferfunctions <True / False> : Enable Functions in Buffer Analysis : " + bufferAnalysisWithFunctions);
		System.out.println ("-leftedge <True / False> : Use left edge algorithm : " + leftEdge);
		System.out.println ("-mutexgraph <True / False> : Enable Mutual Exclusion Graph Analysis : " + mutualExclusionGraphAnalysis);
		System.out.println ("-quant <True / False> : Use Quantifiers in Sched Matrix : " + useQuantifier);
		System.out.println ("-maxfunc <True / False> : Use Max Integer Function in Sched Matrix : " + useMaxFunction);
		System.out.println ("-proc <No. Of Processors> : Processor Constraint for Solver : " + processorConstraint);
		System.out.println ("-typeConstraints <True / False> : Type I Type II Constraints" + typeDifferentiateAlgo);
		System.out.println ("-omegaConstraints <True / False> : Omega Constraints for Pipelined Constraints : " + omegaAnalysis);
		System.out.println ("-maxLatScale <greater than 0 and less than equal to 1> : Factor to Scale Max Latency : " + maxLatencyScalingFactor);
		System.out.println ("-minLatencyForPeriod <True / False> : Enable Longest Path Latency for Period Exploration (default is sum of all exec. times): " + minLatencyForPeriodExpl);		
		System.out.println ("-tetrisSym <True / False> : Tetris Symmetry in Sched. Matrix : " + tetrisSymmetry);
		System.out.println ("-disablePrime <True / False> : Disable Prime Variables in Pipeline Scheduling : " + disablePrime);
		System.out.println ("-periodSym <True / False> : Period Symmetry Constraints in Pipeline Scheduling : " + periodSymmetry);
		System.out.println ("-period <Period Constraint Value> : Period Constraint for Solver : " + periodConstraint);
		System.out.println ("-latency <Period Constraint Value> : Period Constraint for Solver : " + latencyConstraint);
		System.out.println ("-clusters <Cluster Constraint Value> : Cluster Constraint for Solver : " + clusterConstraint);
		System.out.println ("-solver <Solver Type> : which solver To Use <unfolding / periodLocality / matrixSolver / mutualExclusion> : " + solver.toString ());
	}
		
	/**
	 * Parse the command line arguments
	 * 
	 * @param args array of command line arguments
	 */
	public void parseArgs (String[] args)
	{
		for (int i=0;i<args.length;i++)
		{				
			if (args[i].equalsIgnoreCase ("-localtimeout"))
				timeOutPerQueryInSeconds = Integer.parseInt (args[++i]);
			else if (args[i].equalsIgnoreCase ("-maxLatScale"))
				maxLatencyScalingFactor = Double.parseDouble (args[++i]);
			else if (args[i].equalsIgnoreCase ("-globaltimeout"))
				totalTimeOutInSeconds = Integer.parseInt (args[++i]);
			else if (args[i].equalsIgnoreCase ("-ag"))
				applicationGraphFileName =args[++i];
			else if (args[i].equalsIgnoreCase ("-px"))
				profileXmlFileName =args[++i];
			else if (args[i].equalsIgnoreCase ("-ox"))
				outputXmlFileName =args[++i];
			else if (args[i].equalsIgnoreCase ("-lg"))
				hardwareLogFileName = args[++i];
			else if (args[i].equalsIgnoreCase ("-gc"))
				ganttChartFileName = args[++i];
			else if (args[i].equalsIgnoreCase ("-pg"))
				platformGraphFileName =args[++i];
			else if (args[i].equalsIgnoreCase ("-od"))
			{
				outputDirectory = args[++i];
				if(outputDirectory.endsWith("/") == false)
					outputDirectory = outputDirectory.concat("/");
			}			
			else if (args[i].equalsIgnoreCase ("-minLatencyForPeriod"))
				minLatencyForPeriodExpl = stringToBoolean (args[++i]);	
			else if (args[i].equalsIgnoreCase ("-printHsdf"))
				printHsdf = stringToBoolean (args[++i]);
			else if (args[i].equalsIgnoreCase ("-psym"))			
				processorSymmetry = stringToBoolean (args[++i]);				
			else if (args[i].equalsIgnoreCase ("-gsym"))			
				graphSymmetry = stringToBoolean (args[++i]);			
			else if (args[i].equalsIgnoreCase ("-buffer"))
				bufferAnalysis = stringToBoolean (args[++i]);				
			else if (args[i].equalsIgnoreCase ("-bufferfunctions"))			
				bufferAnalysisWithFunctions = stringToBoolean (args[++i]);
			else if (args[i].equalsIgnoreCase ("-leftedge"))
				leftEdge = stringToBoolean (args[++i]);
			else if (args[i].equalsIgnoreCase ("-mutexgraph"))
				mutualExclusionGraphAnalysis = stringToBoolean (args[++i]);
			else if (args[i].equalsIgnoreCase ("-quant"))
				useQuantifier = stringToBoolean (args[++i]);
			else if (args[i].equalsIgnoreCase ("-maxfunc"))
				useMaxFunction = stringToBoolean (args[++i]);
			else if (args[i].equalsIgnoreCase ("-proc"))
				processorConstraint = Integer.parseInt (args[++i]);
			else if (args[i].equalsIgnoreCase ("-typeConstraints"))
				typeDifferentiateAlgo = stringToBoolean (args[++i]);
			else if (args[i].equalsIgnoreCase ("-omegaConstraints"))
				omegaAnalysis = stringToBoolean (args[++i]);
			else if (args[i].equalsIgnoreCase ("-tetrisSym"))
				tetrisSymmetry = stringToBoolean (args[++i]);
			else if (args[i].equalsIgnoreCase ("-disablePrime"))
				disablePrime = stringToBoolean (args[++i]);
			else if (args[i].equalsIgnoreCase ("-periodSym"))
				periodSymmetry = stringToBoolean (args[++i]);
			else if (args[i].equalsIgnoreCase ("-period"))
				periodConstraint = Integer.parseInt (args[++i]);
			else if (args[i].equalsIgnoreCase ("-latency"))
				latencyConstraint = Integer.parseInt (args[++i]);
			else if (args[i].equalsIgnoreCase ("-clusters"))
				clusterConstraint = Integer.parseInt (args[++i]);
			else if (args[i].equalsIgnoreCase ("-solver"))
				solver = parseSolverType (args[++i]);
			else if (args[i].equalsIgnoreCase ("-h"))
			{
				printHelp ();
				System.exit (0);
			}
			else
			{
				throw new RuntimeException ("Wrong Argument : " + args[i] + " :: -h for Help.");
			}
		}
	}
}
