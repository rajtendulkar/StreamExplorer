package experiments.others;
import input.ParseApplicationGraph;
import input.CommandLineArgs;

import java.io.*;
import java.util.*;

import output.*;
import graphanalysis.scheduling.*;
import graphanalysis.scheduling.ListScheduling.Strategy;
import spdfcore.*;

/**
 * Test list scheduling algorithm.
 * 
 * @author Pranav Tendulkar
 */
public class TryListScheduling 
{	
	/**
	 * Entry point for list scheduling algorithm.
	 * 
	 * @param args command line arguments
	 */
	public static void main (String[] args)
	{
		boolean generateGantt = false;
		boolean lexicographic = false;
		boolean longest_proc_time = false;
		CommandLineArgs processedArgs = new CommandLineArgs (args);
		processedArgs.printConfig ();
		String graphName = processedArgs.extractNameFromPath(processedArgs.applicationGraphFileName);
		
		ParseApplicationGraph xmlParse = new ParseApplicationGraph ();
		Graph g = xmlParse.parseSingleGraphXml (processedArgs.applicationGraphFileName);
		
		ListScheduling lstSched = new ListScheduling (g);
		
		if (lexicographic == true)
			lstSched.selectionStrategies.add (Strategy.LEXICOGRAPHIC);
		if (longest_proc_time == true)
			lstSched.selectionStrategies.add (Strategy.LARGEST_PROC_TIME);
		Map<String, String> schedule = lstSched.generateSchedule (processedArgs.processorConstraint);
		
		int latency = Integer.parseInt (schedule.get ("latency"));
		int totalProc = Integer.parseInt (schedule.get ("totalProc"));
		int totalBuffer = Integer.parseInt (schedule.get ("totalBuffer"));
		
		// Create the output Directory first if it doesn't exist
		File directory = new File (processedArgs.outputDirectory);
		directory.mkdirs ();	
		
		try 
		{
			
			String modelFileName = graphName.concat ("_model.txt");
			FileWriter modelFile = new FileWriter (processedArgs.outputDirectory + "/" + modelFileName);
			FileWriter outputFile = new FileWriter (processedArgs.outputDirectory + "/" + graphName + "_output.txt");
			outputFile.write ("latency : " + Integer.toString (latency)+"\n");
			outputFile.write ("totalProc : " + Integer.toString (totalProc)+"\n");
			outputFile.write ("totalBuffer : " + Integer.toString (totalBuffer)+"\n");
			modelFile.write (schedule.toString ());
			modelFile.close ();
			outputFile.close ();
		} 
		catch (IOException e) 
		{ e.printStackTrace (); }
		
		// System.out.println ("Sched : " + schedule.toString ());
		
		if (generateGantt == true)
		{
		
			Map<String, Integer> taskDuration = new HashMap<String, Integer>();
			
			Iterator<Actor> iterActr = g.getActors ();
			while (iterActr.hasNext ())
			{
				Actor actr = iterActr.next ();
				taskDuration.put (actr.getName (), actr.getExecTime ());
			}
			
			GanttChart ganttplot = new GanttChart ();
			ganttplot.plotChart (schedule, taskDuration, processedArgs.outputDirectory+"/"+graphName+"_gantt.pdf", 0);
		}
	}
}
