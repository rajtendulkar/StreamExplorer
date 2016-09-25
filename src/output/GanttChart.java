package output;
import java.io.*;
import java.util.*;

import solver.SmtVariablePrefixes;
import spdfcore.*;

/**
 * Plot a Gantt Chart from a schedule.
 * We require Gnuplot installed on the system to generate PDF output.
 * 
 * @author Pranav Tendulkar
 *
 */
public class GanttChart 
{
	/**
	 * Add name to the Gantt chart
	 */
	public boolean addNamesToActorInGraph=true;
	
	/**
	 * Add legend in the Gantt chart 
	 */
	public boolean addLegendInGraph=true;
	
	/**
	 * Height of the task in the Gantt chart 
	 */
	public final double taskBoxHeight = 0.6;
	
	/**
	 * Rotate text in the Gantt chart by this angle 
	 */
	public int textRotationAngle = 0;
	
	/**
	 * Generate PDF output. 
	 */
	public boolean generatePdfOutput = true;
	
	/**
	 * Name of tasks
	 */
	private HashSet<String> tasks;
	
	/**
	 * Position of processor in the Gantt chart
	 */
	private HashMap<Integer, Double> processors;
	
	/**
	 * Name of each processor
	 */
	private HashMap<Integer, String> processorNames;
	
	/**
	 * Colors mapped to tasks.
	 */
	public HashMap<String, String> colorBook;
	
	/**
	 * List of records to generate Gantt Chart.
	 */
	private List<Record> records;
	
	/**
	 * Random number generator 
	 */
	Random randomNumberGen;
	
	/**
	 * Random set of colors encoded in Hex.
	 */
	private final String defaultColors [] = {"#FFB300", "#FFD573", "#A67400", "#A62F00", "#FF9B73",
											 "#FF7640", "#FFD500", "#FFE873", "#A68A00", "#F2FD3F", 
											 "#9AA400",	"#F5FD72", "#7F9F00", "#F4FA3E", "#5FD4BA",
											 "#35D4A4", "#6B8FD4", "#5CCCCC", "#61D74C", "#FFFF73",
											 "#A3A500", "#CE0071", "#00A383", "#88B32D", "#0ACF00",
											 "#086FA1", "#626D52", "#9689A2", "#F8D892", "#804450",
											 "#838A11", "#CB0713", "#029D7C", "#9BAECB", "#9BAECB", 
											 "#75CF9F", "#D5FF71", "#C997DD", "#50406F", "#027D6E",
											 "#F79C01", "#9B5849", "#9B5849", "#0331CE", "#701D4B", 
											 "#2E7243", "#484B6C", "#D11E5F", "#463C5D", "#A60E2E", 
											 "#267FAA", "#A9B4E7", "#818D45", "#BBF923", "#506086", 
											 "#CBF4A5", "#34A375", "#C4E40F", "#9BA578", "#EB9E30", 
											 "#A9AD30", "#BCD94F", "#D3BFBE", "#73897E", "#90A900", 
											 "#BEBC90", "#5F6231", "#F8DDA1", "#0C4CE2", "#524D2C", 
											 "#B18D15", "#498B87", "#CED128", "#2A9AF7", "#188765", 
											 "#DDFC49", "#639479", "#F06B2E", "#B9112C", "#F9CC1C", 
											 "#D99E92", "#D75F8C", "#FC9F3D", "#DAB5E6", "#959576", 
											 "#B08304", "#B4146B", "#4E7C77", "#550A33", "#565311", 
											 "#AC9C87", "#66927D", "#D8CB2C", "#168B89", "#9B849E",
											 "#8B3E2F", "#698B22", "#98FB98", "#9AFF9A", "#90EE90",
											 "#7CCD7C", "#548B54", "#2E8B57", "#54FF9F", "#4EEE94",
											 "#43CD80", "#00FF7F", "#00FF7F", "#00BFFF", "#00BFFF",
											 "#00B2EE", "#009ACD", "#00688B", "#1E90FF", "#1E90FF",
											 "#1C86EE", "#1874CD", "#104E8B", "#68838B", "#87CEFA"};
	
	/**
	 * Initialize data structures for Gantt chart object 
	 */
	public GanttChart ()
	{
		records = new ArrayList<Record>();
		tasks = new HashSet<String>();
		processors = new HashMap<Integer, Double>();
		colorBook = new HashMap<String, String>();
		randomNumberGen = new Random ();
		processorNames = new HashMap<Integer, String>();
	}
	
	/*********************************************************************************************
	 ********************************* output file generation *************************************/
	/**
	 * Method to generate GNU plot file.
	 * We add a red vertical line to mark the period.
	 * 
	 * @param fileName name of output GNU plot file
	 * @param period period from the model.
	 */
	private void genereteGnuConfigFile (String fileName, int period)
	{
		try
		{
			String configFile = fileName.replace ("pdf", "gpl");
			
			if (configFile.endsWith (".gpl") == false)
				configFile = configFile.concat (".gpl");
			
			// Create file 
			FileWriter fstream = new FileWriter (configFile);
			BufferedWriter out = new BufferedWriter (fstream);			

			double xmin = 0;
			double xmax = calculateXMax ();
			double ymin = 0 + (taskBoxHeight / 2);
			double ymax = processors.size () + 1 - (taskBoxHeight / 2);

			// Generate the outputFile.
			out.write ("set xrange ["+String.format ("%.6f",xmin)+":"+String.format ("%.6f",xmax)+"]\n");
			out.write ("set yrange ["+String.format ("%.6f",ymin)+":"+String.format ("%.6f",ymax)+"]\n");
			out.write ("set autoscale x\n");
			out.write ("set xlabel \"time\"\n");
			out.write ("set ylabel \"Processors\"\n");
			out.write ("set title \"Gantt chart\"\n");
			out.write ("set ytics (");

			{
				int count=0;
				Iterator<Integer> procIter = processors.keySet ().iterator ();
				while (procIter.hasNext ())
				{
					int proc = procIter.next ();
					out.write ("\"" + processorNames.get(proc) + "\" "+ String.format ("%.0f",processors.get (proc)));
					if (count<processors.size ()-1)
						out.write (", ");
					count++;
				}
			}

			out.write (")\n");
			if(addLegendInGraph == true)
				out.write ("set key outside width +2\n");
			else
				out.write ("set key off\n");
			
			// Light colored grid.
			out.write("set style line 12 lc rgb '#808080' lt 0 lw 1\n");
			out.write("set grid back ls 12\n");
			// Normal Grid.
			// out.write ("set grid xtics\n");
			// out.write ("set grid ytics\n");
			out.write ("set palette model RGB\n");
			out.write ("unset colorbox\n");
			
			if (period > 0)
			{
				// for (int i=(int)xmax;i>0;i-=period)
				for (int i=0;i<(int)xmax;i+=period)
					out.write ("set arrow from "+Integer.toString (i)+","+ymin+" to "+Integer.toString (i)+","
											+ymax+" nohead lt 3 lw 4 lc rgb \"red\"\n");
			}

			for (int i=0;i<records.size (); i++)
			{
				out.write ("set object "+ (i+1) +" rectangle from " 
						+ String.format ("%.6f",records.get (i).bottomLeftX) 
						+", " + records.get (i).bottomLeftY 
						+ " to " + String.format ("%.6f",records.get (i).topRightX) + ", " 
						+ records.get (i).topRightY 
						+ " fillcolor rgb "
						+ records.get (i).color + " fillstyle solid 0.8\n");

				// this is a global flag.
				if(addNamesToActorInGraph == true)
				{
					// this will selectively disable one actor.
					if(records.get (i).printNameInGraph == true)
					{
						out.write ("set label "+ (i+1) +" at "
							+ String.format ("%.1f", ((double)(records.get (i).startTime + records.get (i).endTime)/2))
							+ " , " + String.format ("%.0f", (records.get (i).bottomLeftY + records.get (i).topRightY)/2) 
							/*+ " rotate by 90 left" */ + "  \""
							+ records.get (i).actorName +"\" front center ");
						if(textRotationAngle > 0)
							out.write("rotate by " + Integer.toString(textRotationAngle));
						out.write("\n");
					}
				}
			}

			Collections.sort (records);

			out.write ("plot ");
			for (int i=0;i<records.size ();i++)
			{
				if (i != 0)
					out.write ("\t");
				out.write ("-1 title \"" + records.get (i).actorName + "\" with lines linecolor rgb " + 
						records.get (i).color + "  linewidth 6");
				if (i != records.size () -1)
					out.write (", \\\n");
			}

			//Close the output stream
			out.close ();
		}catch (Exception e){ System.err.println ("Error: " + e.getMessage ());}
	}
	
	/**
	 * Generate PDF from GNU plot.
	 * 
	 * @param outputFileName output PDF file name
	 */
	private void generatePdf (String outputFileName)
	{
		String pdfGenerationConfigFile = "inputFiles/gantt_chart/plotPdf.gpl";
		// System.out.println ("We assume that you have Gnuplot installed on your system !!");
		String configFile = outputFileName.replace ("pdf", "gpl");
		
		if (configFile.endsWith (".gpl") == false)
			configFile = configFile.concat (".gpl");

		try 
		{
			Runtime r = Runtime.getRuntime ();
			String command = "gnuplot " + pdfGenerationConfigFile + " "+ configFile;
			Process p = r.exec (command);
			p.waitFor ();

			// If there is any error, it will get printed here.
			BufferedReader b = new BufferedReader (new InputStreamReader (p.getInputStream ()));
			String line = "";

			while ((line = b.readLine ()) != null) {
				System.out.println (line);
			}

			// Generated the file, now lets move it to appropriate location.
			command = "mv -f output.pdf " + outputFileName;
			p = r.exec (command);
			p.waitFor ();

		} 
		catch (IOException e) { e.printStackTrace (); } 
		catch (InterruptedException e) { e.printStackTrace (); }

		System.out.println ("Finished generating PDF.");
	}
	
	/*********************************************************************************************
	 ********************************* color book processing *************************************/	 
	
	/**
	 * Pick up a random color from the color book we have.
	 * @return Random string of a color
	 */
	private String pickRandomColor ()
	{				
		int randomNumber =  Math.abs (randomNumberGen.nextInt () % defaultColors.length);		
		return "\"" + defaultColors[randomNumber] + "\"";		
	}
	
	/**
	 * Assign a color to each actor.
	 */
	private void generateColorBook ()
	{
		for (int i=0;i<records.size ();i++)
		{
			String actorName = records.get (i).actorName;
			String actorColor = records.get (i).color;
			
			if (colorBook.get(actorName) == null && actorColor == null)
				colorBook.put (actorName, pickRandomColor ());
			else if (colorBook.get(actorName) == null && actorColor != null)
				colorBook.put (actorName, actorColor);
		}
	}

	/*********************************************************************************************/

	/**
	 * Record of each task in the Gantt chart.
	 * 
	 * Note : make sure that for each processor index the name is the same. 
	 * We don't check if names are same or not.
	 */
	public class Record implements Comparable<Record>
	{
		/** 
		 * Print name of the task in the Gantt chart 
		 */
		public boolean printNameInGraph = true;
		/** 
		 * Name of the processor 
		 */
		String processorName;
		/** 
		 * Index of the processor 
		 */
		int processorIndex;
		
		/** 
		 * Start time of the task 
		 */		
		long startTime;
		/** 
		 * End time of the task 
		 */
		long endTime;
		/** 
		 * Name of the actor 
		 */
		String actorName;		

		/** 
		 * Bottom left x co-ordinate 
		 */
		private double bottomLeftX;
		/** 
		 * Bottom left y co-ordinate 
		 */
		private double bottomLeftY;
		/** 
		 * Top Right x co-ordinate 
		 */
		private double topRightX;
		/** 
		 * Top Right y co-ordinate 
		 */
		private double topRightY;
		/** 
		 * Vertical placement in the Gantt chart
		 */
		private double yPosition;

		/** 
		 * Color assigned to the task 
		 */
		String color;

		/** 
		 * Compare record with another record.
		 * We compare a task with other task. If the names
		 * are equal then we compare instance ids.
		 * 
		 * anotherInstance another record to compare 
		 */
		@Override
		public int compareTo (Record anotherInstance)
		{
			if(actorName.equals(anotherInstance.actorName) == true)
				return 0;
			
			String tempStr1 = actorName.replaceAll ("[0-9]+","");
			String tempStr2 = anotherInstance.actorName.replaceAll ("[0-9]+","");

			if (tempStr1.equals (tempStr2))
			{
				String tStr1 = actorName.replaceAll ("[^\\d.]", "");
				String tStr2 = anotherInstance.actorName.replaceAll ("[^\\d.]", "");
				
				 try 
				 {
					    return Integer.parseInt (tStr1) - Integer.parseInt (tStr2);
				 } catch (NumberFormatException e) {
					 return actorName.compareTo (anotherInstance.actorName);
				 }
			}
			else	
				return actorName.compareTo (anotherInstance.actorName);
		}
		
		/**
		 * Build a record for Gantt chart
		 * 
		 * @param processorName name of the processor
		 * @param processorIndex index of the processor
		 * @param startTime start time of the task
		 * @param endTime end time of the task
		 * @param actorName name of the task
		 * @param colorIndex index of the color in color book.
		 */
		public Record (String processorName, int processorIndex, long startTime, long endTime, String actorName, int colorIndex)
		{
			this(processorName, processorIndex, startTime, endTime, actorName);
			color = new String("\"" + defaultColors[(colorIndex%(defaultColors.length))] + "\"");
		}
		
		/**
		 * Build a record for Gantt chart
		 * 
		 * @param processorName name of the processor
		 * @param processorIndex index of the processor
		 * @param startTime start time of the task
		 * @param endTime end time of the task
		 * @param actorName name of the task
		 * @param color Hex color to be assigned to the task
		 */
		public Record (String processorName, int processorIndex, long startTime, long endTime, String actorName, String color)
		{
			this(processorName, processorIndex, startTime, endTime, actorName);			
			
			if(color.length() != 7 || (color.startsWith("#") != true))
			{
				throw new RuntimeException("Color Specification should be like #000000");
			}
			// this will throw an error if it is not a number.
			@SuppressWarnings("unused")
			int number = Integer.parseInt(color.substring(1), 16);			
			this.color = new String("\"" + color + "\"");
		}

		
		/**
		 * Build a record for Gantt chart. 
		 * Color is randomly selected.
		 * 
		 * @param processorName name of the processor
		 * @param processorIndex index of the processor
		 * @param startTime start time of the task
		 * @param endTime end time of the task
		 * @param actorName name of the task
		 */
		public Record (String processorName, int processorIndex, long startTime, long endTime, String actorName)
		{
			this.processorName = processorName;
			this.processorIndex = processorIndex;
			this.startTime = startTime;
			this.endTime = endTime;
			this.actorName = actorName;
			this.color = null;
			processorNames.put(processorIndex, processorName);
		}
		
		/**
		 * Build a record for Gantt chart. 
		 * Color is randomly selected.
		 * 
		 * @param processorIndex index of the processor
		 * @param startTime start time of the task
		 * @param endTime end time of the task
		 * @param actorName name of the task
		 */
		public Record (int processorIndex, long startTime, long endTime, String actorName)
		{
			this.processorName = Integer.toString(processorIndex);
			this.processorIndex = processorIndex;
			this.startTime = startTime;
			this.endTime = endTime;
			this.actorName = actorName;
			this.color = null;
			processorNames.put(processorIndex, processorName);
		}
	}

	/**
	 * Calculate the rectangles in the Gantt chart.
	 */
	private void calculateRectangles () 
	{		
		for (int i=0;i<records.size ();i++)
		{
			Record record = records.get(i);
			record.yPosition = processors.get (record.processorIndex);
			record.bottomLeftX = record.startTime;
			record.bottomLeftY = record.yPosition - 0.5 * taskBoxHeight;
			record.topRightX = record.endTime;
			record.topRightY = record.yPosition + 0.5 * taskBoxHeight;
			record.color = colorBook.get (record.actorName);
		}
	}
	
	/**
	 * Generate unique tasks and processors from the records
	 */
	private void generateUniqueTasksAndProcs ()
	{
		tasks.clear ();
		processors.clear ();

		SortedSet<Integer> uniqueProc = new TreeSet<Integer>();

		// Check what is highest index of the processor we have used.
		// If we used only processor whose index is 256 and 257, then we still support this.
		for (int i=0;i<records.size ();i++)
			uniqueProc.add (records.get (i).processorIndex);

		// In the processors hashmap for each processor index we put a value on the y-axis where it should be located.
		int count=0;		
		for(int proc : uniqueProc)
		{			
			processors.put (proc, (double) (uniqueProc.size () - count));
			count++;
		}

		// We add tasks to hash set 
		for (int i=0;i<records.size ();i++)		
			tasks.add (records.get (i).actorName);		
	}

	/**
	 * Calculate the maximum value for the X-axis of the Gantt chart.
	 * 
	 * @return max value for X-axis of Gantt chart
	 */
	private double calculateXMax ()
	{
		double max = Double.MIN_VALUE;

		for (int i=0;i<records.size ();i++)
			if (records.get (i).endTime > max)
				max = records.get (i).topRightX;

		return max;		
	}
	
	/**
	 * Generate the Gantt chart.
	 * 
	 * @param outputFileName output file name for GNU plot file
	 * @param period period of the schedule, -1 for non-pipelined schedule
	 */
	private void chartGeneration (String outputFileName, int period)
	{
		// If No External colorbook provided, generate a colorbook randomly using pre-defined colors
		if (colorBook.size () == 0)
			generateColorBook ();
		// Generate Unique Tasks and Resources
		generateUniqueTasksAndProcs ();
		// Calculate the Rectangles in the plot.
		calculateRectangles ();
		// Generate the GnuPlot File
		genereteGnuConfigFile (outputFileName, period);
		// Generate the PDF Output.
		if (generatePdfOutput)
			generatePdf (outputFileName);
	}
	
	/**
	 * Add a record to the list.
	 * 
	 * @param record record to be added
	 */
	public void addRecord (Record record)
	{
		records.add (record);
	}
	
	/**
	 * Plot the Gantt chart 
	 * 
	 * @param model Model containing the schedule
	 * @param sdfGraph SDF graph
	 * @param outputFileName output file name
	 */
	public void plotChart (Map<String,String> model, Graph sdfGraph, String outputFileName)
	{
		// Create the directory if necessary.
		File file = new File(outputFileName);
	    if(!file.exists())
	    {
	        file = file.getParentFile();
	        file.mkdirs();
	    }
	    
		int period = 0;
		Map<String,Integer> taskDuration = new HashMap<String,Integer>();
		
		Iterator<Actor> actrIter = sdfGraph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			taskDuration.put (actr.getName (), actr.getExecTime ());			
		}
		
		if (model.containsKey (SmtVariablePrefixes.periodPrefix))
			period = Integer.parseInt (model.get (SmtVariablePrefixes.periodPrefix));
		
		plotChart (model, taskDuration, outputFileName, period);		
	}

	/**
	 * Plot the Gantt chart. Records are added externally. 
	 * 
	 * @param outputFileName output file name
	 * @param period period of the schedule, -1 for non-pipelined schedule
	 */
	public void plotChart (String outputFileName, int period)
	{
		// In this case the records are added externally.	
		chartGeneration (outputFileName, period);
	}
	
	/**
	 * Plot the Gantt chart from the model and task durations. 
	 * 
	 * @param model Model containing start times and processor allocations
	 * @param taskDuration duration of tasks
	 * @param outputFileName output file name
	 * @param period period of the schedule, -1 for non-pipelined
	 */
	public void plotChart (Map<String,String> model, Map<String,Integer> taskDuration, 
							String outputFileName,  int period)
	{
		// Build The Records
		buildRecordsFromModel (model, taskDuration);	
		chartGeneration (outputFileName, period);
	}
	
	/** 
	 * Build records from the model
	 * 
	 * @param model model containing start times and processor allocations
	 * @param taskDuration task durations
	 */
	private void buildRecordsFromModel (Map<String, String> model, Map<String,Integer> taskDuration) 
	{
		records.clear ();
		
		Iterator<String> modelIter = model.keySet ().iterator ();
		while (modelIter.hasNext ())
		{
			String key = modelIter.next ();
			if ((key.startsWith ("x") == true) && (key.startsWith ("xPrime") == false))
			{
				String actorName = key.substring (1);
				int startTime = Integer.parseInt (model.get (key));
				int processor = Integer.parseInt (model.get ("cpu"+actorName));
				int endTime = startTime;
				String split[] = actorName.split ("_");
				int duration = taskDuration.get (split[0]);
				endTime += duration;
				
				records.add (new Record (processor, startTime, endTime, actorName));				
			}
		}
	}
}
