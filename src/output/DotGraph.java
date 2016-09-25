package output;

import spdfcore.*;
import java.io.*;
import java.util.*;

/**
 * DOT Graph Generation from an Application Graph.
 * 
 * @author Pranav Tendulkar
 *
 */
public class DotGraph 
{
	/**
	 * 
	 * This function return a string which contains the code for the DOT file.
	 * 
	 * @param graph object which has the informations of actors, ports, channels etc.
	 * @return String containing dot file information
	 */
	public String getDotDataInString (Graph graph)
	{
		String dotData = "rankdir=LR;" + "\n";
		dotData += "edge[minlen=2,color=red, fontcolor=blue,labeldistance=1]\n";
		
		Iterator<Channel> channels = graph.getChannels ();
		if (channels.hasNext () == false)
		{
			// No Channels remaining. Maybe just one actor left?
			Iterator<Actor> actrIter = graph.getActors ();
			while (actrIter.hasNext ())
			{
				Actor actr = actrIter.next ();
				dotData += actr.getName () + "\n";
			}
		}
		else
		{		
			while (channels.hasNext ()) 
			{
				String printString = null;
				int initialTokens = 0;
			
				Channel channel = channels.next ();
				initialTokens = channel.getInitialTokens ();
				Channel.Link linkIN = channel.getLink (Port.DIR.IN);
				Actor actorOUT = linkIN.getActor ();
				Port portIN = linkIN.getPort ();
				String inRate = portIN.getRate ();
			
				Channel.Link linkOUT = channel.getLink (Port.DIR.OUT);	            	            
				Actor actorIN  = linkOUT.getActor ();
				Port portOUT = linkOUT.getPort ();
				String outRate = portOUT.getRate ();
            
	            printString = "\"" + actorIN.getName () + "\"";
	            printString += " -> ";
	            printString += ("\"" + actorOUT.getName () + "\"");
	            
	            if (inRate != null)
	            	printString += ("[headlabel=\"" + inRate + "\",");
	            
	            if (outRate != null)
	            	printString += ("taillabel=\"" + outRate + "\"");
	            
	            if (initialTokens != 0)
	            	printString += (", label=\"[" + Integer.toString (initialTokens) + "]\"");
	            
	            printString += "]";
	            	            
	            //System.out.println ("Output String :" + printString);
	            dotData += printString + "\n";
			}				
		}
		return dotData;
	}
	
	/**
	 * 
	 * This function generates a JPEG File from the input
	 * graph structure.
	 * 
	 * Note : this requires graphviz utility - "dot"
	 * 
	 * @param graph object which has the informations of actors, ports, channels etc.
	 */
	public void generateJpgFromGraph (Graph graph, String fileName)
	{
		String dotFileName = null, jpgFileName = null;
		if(fileName.endsWith(".jpg"))
		{
			dotFileName = fileName.replace(".jpg",".dot");
			jpgFileName = fileName;
		}
		else if(fileName.endsWith(".dot"))
		{
			jpgFileName = fileName.replace(".dot",".jpg");
			dotFileName = fileName;
		}
		else
		{
			jpgFileName = fileName.concat(".jpg");
			dotFileName = fileName.concat(".dot");
		}
		
		generateDotFromGraph (graph, dotFileName);
	
		String command = "/usr/bin/dot -Tjpg  "+ dotFileName + " -o " + jpgFileName;
		
		ProcessBuilder pr = new ProcessBuilder(command);
		File outputFile = new File(jpgFileName);
		pr.redirectOutput(outputFile );
		
		try
		{
			Process p = Runtime.getRuntime().exec (command);
			p.waitFor();
		}
		catch (IOException e) { e.printStackTrace(); }
		catch (InterruptedException e) { e.printStackTrace(); }	
	}
	
	/**
	 * 
	 * This function generates a Dot File from the input
	 * graph structure.
	 * 
	 * @param graph object which has the informations of actors, ports, channels etc.
	 */
	public void generateDotFromGraph (Graph graph, String fileName)
	{
		// Create directories only if necessary.
		if(fileName.contains("/"))
		{
			String directoryName = fileName.substring (0, fileName.lastIndexOf ("/"));
			if (directoryName != null)
			{
				File directory = new File (directoryName);
				directory.mkdirs ();
			}
		}
		
		//System.out.println ("Writing to output file : " + outputFileName);
		try
		{
			FileWriter fstream = new FileWriter (fileName);
			PrintWriter out = new PrintWriter (fstream);
			
			// Start of Dot File.
			out.println ("digraph G {");
			
			
			String dotGraphData = getDotDataInString (graph);
			out.println (dotGraphData);			
			
			// End of Dot File.
			out.println ("}");
			out.close ();
			fstream.close ();
		}catch (Exception e)
		{
			//Catch exception if any
			System.err.println ("Error: " + e.getMessage ());
		}		
	}
}
