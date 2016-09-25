package exploration;

import java.io.*;
import java.text.*;
import java.text.SimpleDateFormat;
import java.util.*;

import solver.Z3Solver.SatResult;

/**
 * Abstract Design Space Exploration class.
 * 
 * @author Pranav Tendulkar
 *
 */
public abstract class Explorer 
{
	/**
	 * Opened log files for writing or not. 
	 */
	private boolean filesOpened = false;
	/**
	 * Number of dimensions for exploration. 
	 */
	protected final int dimensions;
	
	/**
	 * Time out in Seconds for each query. 
	 */
	protected int perQuerytimeOutInSeconds;
	
	/**
	 * Global time out in seconds for the exploration. 
	 */
	protected int totalQueryTimeOutInSeconds;
	
	/**
	 * Time required for current SMT query. 
	 */
	protected double timeTakenForCurrentQuery;
	
	/**
	 * Current Exploration time (in milliseconds). 
	 */
	protected double totalExplTime = 0;	// This is in milliseconds.
	
	/**
	 * Exploration parameters. It is an interface to call generic SMT related
	 * functions. 
	 */
	protected ExplorationParameters explParams;
	
	/**
	 * Output directory to generate the log files. 
	 */
	protected String outputDir = "";
	
	/**
	 * File which contains models for every SAT point. 
	 */
	protected FileWriter modelFile;
	
	/**
	 * Log file containing all explored points. 
	 */
	protected FileWriter explorePointsfile;
	
	/**
	 * Log file containing all UNSAT points. 
	 */
	protected FileWriter unSatPointsfile;
	
	/**
	 * Log file containing all SAT points. 
	 */
	protected FileWriter satPointsfile;
	
	/**
	 * Log file containing Pareto Points (final result).
	 */
	protected FileWriter paretoPointsfile;
	
	/**
	 * Log file containing all TIMED OUT points.
	 */
	protected FileWriter timedOutPointsfile;
	
	/**
	 * Filename for models file.
	 */
	protected String modelFileName = "satPointModels.txt";
	
	/**
	 * Filename for explored points file. 
	 */
	protected String exploredPointsFileName = "exploredPoints.txt";
	
	/**
	 * Filename for UNSAT points file. 
	 */
	protected String unsatPointsFileName = "unSatPoints.txt";
	
	/**
	 * Filename for SAT points file.
	 */
	protected String satPointsFileName = "satPoints.txt";
	
	/**
	 * Filename for Pareto points file. 
	 */
	protected String paretoPointsFileName = "paretoPoints.txt";
	
	/**
	 * Filename for TIMED OUT points file. 
	 */
	protected String timedOutPointsFileName = "timedOutPoints.txt";
	
	/**
	 * Format to generate the time for Log files. 
	 */
	private NumberFormat formatter = new DecimalFormat("#0.000000");     
	
	/**
	 * Initialize Explorer class object.
	 * 
	 * @param opDir output directory
	 * @param dimensions total number of dimensions in exploration
	 * @param perQueryTimeOutSeconds per SMT query time out in seconds
	 * @param totalTimeOutInSeconds global time out in second for entire exploration
	 * @param explorationParams exploration parameters
	 */
	public Explorer (String opDir, int dimensions, int perQueryTimeOutSeconds, int totalTimeOutInSeconds, 
							ExplorationParameters explorationParams)
	{
		if (opDir == null)
			throw new RuntimeException("Output directory not specified.");
		
		outputDir = opDir;
		
		totalQueryTimeOutInSeconds = totalTimeOutInSeconds;
		perQuerytimeOutInSeconds = perQueryTimeOutSeconds;
		explParams = explorationParams;		
		this.dimensions = dimensions;
		
		// Note : Don't open files here for writing. 
		// If we do that, our old results will get over-written 
		// and we won't be able to do readExploredPoints() to continue
		// from previous execution.
		
		// Warning : Don't un-comment the following lines.
		// Open the Files to write the exploration logs.
		// openFiles();
	}
	
	/**
	 * Open log files for writing.
	 */
	private void openFiles()
	{		
		try 
		{
			// Create the output Directory first if it doesn't exist
			File directory = new File (outputDir);
			directory.mkdirs ();			

			modelFile = new FileWriter (outputDir + modelFileName);
			explorePointsfile = new FileWriter (outputDir + exploredPointsFileName);
			unSatPointsfile = new FileWriter (outputDir + unsatPointsFileName);
			satPointsfile = new FileWriter (outputDir + satPointsFileName);
			paretoPointsfile = new FileWriter (outputDir + paretoPointsFileName);
			timedOutPointsfile = new FileWriter (outputDir + timedOutPointsFileName);
			filesOpened = true;
		} 
		catch (IOException e) 
		{
			System.out.println ("Unable to Open a File for Output");
			e.printStackTrace ();
		}
	}
	
	/**
	 * Write message to the log file.
	 * 
	 * @param fstreamOutput the file to write
	 * @param msg string to write
	 */
	protected void outputToFile (FileWriter fstreamOutput, String msg)
	{
		try
		{
			// append to the file.						
			fstreamOutput.write (msg);		
			fstreamOutput.flush ();
		} catch (Exception e)
		{
			//Catch exception if any
			System.err.println ("Error File Generation: " + e.getMessage ());
		}
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#finalize()
	 */
	@Override
	public void finalize ()
	{
		// Close all the open Files.
		try 
		{			
			modelFile.flush ();
			modelFile.close ();

			explorePointsfile.flush (); 
			explorePointsfile.close ();

			unSatPointsfile.flush (); 
			unSatPointsfile.close ();

			satPointsfile.flush ();
			satPointsfile.close ();

			paretoPointsfile.flush ();
			paretoPointsfile.close ();

			timedOutPointsfile.flush ();
			timedOutPointsfile.close ();	

		} catch (IOException e)  { e.printStackTrace (); }
	}
	
	/**
	 * Perform the SMT query
	 * 
	 * @param constraints constraints of the query for every dimension
	 * @return SatResult contains result of query
	 */
	protected SatResult smtQuery (int constraints[])
	{
        if(filesOpened == false)
            openFiles();
        
		// Set the constraints.
		for (int i=0;i<dimensions;i++)
			explParams.setConstraint (i, constraints[i]);			

		SimpleDateFormat sdfDate = new SimpleDateFormat ("HH:mm:ss");
		Date now = new Date ();
		
		String pointString= "";
		
		for (int i=0;i<dimensions;i++)			
			pointString = pointString.concat (explParams.getConstraintName (i) + " : " + Integer.toString (constraints[i]) + " ");
		
		System.out.print ("<"+sdfDate.format (now)+"> " + pointString);
		
		long startTime = System.nanoTime (); 
		SatResult result = explParams.solverQuery (perQuerytimeOutInSeconds);
		long endTime = System.nanoTime ();
		timeTakenForCurrentQuery = ((endTime - startTime) / (double) 1000000);

		totalExplTime += timeTakenForCurrentQuery;
		
		String currentQueryTimeString = formatter.format(timeTakenForCurrentQuery/1000) + " seconds";
			
		System.out.print (" Result : " + result.toString () + " Time : " + currentQueryTimeString);		
		
		outputToFile (explorePointsfile, pointString + " Result : " + result.toString ()  + " Time : " + currentQueryTimeString + "\n");

		if (result == SatResult.SAT)
		{								
			Map<String, String> model = explParams.getModelFromSolver ();
			
			// Note: This code was added in order to serve the reading of the pareto exploration results
			// from a file. We need to dump the acquired sat points to the sat point files, otherwise the
			// sat points and pareto points don't match each other. However I remember that there was some
			// reason why I had not done it. Probably some scripting issue or something else. Hence please
			// verify later if this is causing any other code to break.
			pointString = "";			
			int costs[] = explParams.getCostsFromModel();			
			for (int i=0;i<dimensions;i++)			
				pointString = pointString.concat (explParams.getConstraintName (i) + " : "  + Integer.toString (costs[i]) + " ");
			
			outputToFile (satPointsfile, pointString + " Result : " + result.toString () 
					+ " Time : " + currentQueryTimeString + "\n");
			outputToFile (modelFile, pointString + " Result : " + result.toString () 
					+ " Time : " + currentQueryTimeString + "\n" + model.toString () + "\n");			
		}
		else if ((result == SatResult.UNSAT))
		{	
			outputToFile (unSatPointsfile, pointString + " Result : " + result.toString () 
							+ " Time : " + currentQueryTimeString + "\n");
		}
		else if ((result == SatResult.TIMEOUT) || (result == SatResult.UNKNOWN))
		{
			outputToFile (timedOutPointsfile, pointString + " Result : " + result.toString () 
					+ " Time : " + currentQueryTimeString + "\n");
		}
		else
			throw new RuntimeException ("Unexpected Result "+ result.toString () + " at : " + pointString);

		return result;		
	}	
}
