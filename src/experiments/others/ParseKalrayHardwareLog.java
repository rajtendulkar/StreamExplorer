package experiments.others;
import output.DotGraph;
import platform.kalray.ParseExecutionLog;
import input.ParseApplicationGraph;
import input.CommandLineArgs;
import spdfcore.Graph;


/**
 * Generate execution log generated on the Kalray platform and generate a Gantt chart.
 * 
 * @author Pranav Tendulkar
 */
public class ParseKalrayHardwareLog
{
	/**
	 * Parse raw hardware log and generate gantt chart for an iteration
	 * 
	 * @param args command line arguments
	 */
	private static void parseHardwareLog (String[] args)
	{
		CommandLineArgs processedArgs = new CommandLineArgs (args);		
		
		// Parsing of the raw data from the kalray machine.
		ParseExecutionLog parseLog = new ParseExecutionLog();
		parseLog.parseLogFile(processedArgs.hardwareLogFileName);	
		parseLog.plotGanntChart(1, processedArgs.ganttChartFileName);
	}
	
	/**
	 * Generate DOT files of application graph.
	 * 
	 * @param args command line arguments
	 */
	private static void generateDotFile (String[] args)
	{
		CommandLineArgs processedArgs = new CommandLineArgs (args);
				
		ParseApplicationGraph applicationXmlParse = new ParseApplicationGraph ();
		Graph g = applicationXmlParse.parseSingleGraphXml(processedArgs.applicationGraphFileName);
		DotGraph dotG = new DotGraph ();
		dotG.generateDotFromGraph (g, processedArgs.outputDirectory + "out.dot");
	}
	
	/**
	 * @param args command line arguments
	 */
	public static void main (String[] args) 
	{
		// Parse Hardware Log.
		parseHardwareLog (args);
		
		// Generate Dot File
		generateDotFile (args);
	}
}
