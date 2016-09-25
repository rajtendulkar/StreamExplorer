package platform.kalray.scheduleXML;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;

import solver.SmtVariablePrefixes;
import spdfcore.*;
import spdfcore.stanalys.Solutions;

/**
 * Generate a pipelined schedule XMl file to execute on the platform.
 * 
 * @author Pranav Tendulkar
 *
 */
public class PipelinedScheduleXml
{
	/**
	 * Number of clusters used in the schedule. 
	 */
	private final int numClustersUsed = 1;
	
	/**
	 * Number of processors in the cluster 
	 */
	private final int totalProcInCluster = 16;
	
	/**
	 * Add Channel elements in the XML file. 
	 * 
	 * @param graph application graph
	 * @param solutions solutions for application graph
	 * @param doc XML document 
	 * @param parent parent node where to add this element
	 * @param schedule Model with scheduling information 
	 */
	private void addChannelElements (Graph graph, Solutions solutions, Document doc, Element parent, Map<String, String> schedule)
	{
		int latency = Integer.parseInt(schedule.get(SmtVariablePrefixes.latencyPrefix));
		int period = Integer.parseInt(schedule.get(SmtVariablePrefixes.periodPrefix));
		int kMax = (int) Math.ceil((double)latency / period);
		
		Element rootElement = doc.createElement ("graphChannels");
		rootElement.setAttribute("size", Integer.toString(graph.countChannels()));
		parent.appendChild(rootElement);
		
		Iterator<Channel> chnnlIter = graph.getChannels ();
        while (chnnlIter.hasNext ())
        {
        	Channel chnnl = chnnlIter.next ();
        	Element channel = doc.createElement ("channel");
        	Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
        	Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
        	int srcRepCnt = solutions.getSolution (srcActor).returnNumber ();
        	//int dstRepCnt = solutions.getSolution (dstActor).returnNumber ();
        	int prodRate = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
        	int consRate = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());
        	channel.setAttribute ("name", chnnl.getName ());
        	channel.setAttribute ("srcActor", srcActor.getName ());
        	channel.setAttribute ("dstActor", dstActor.getName ());
        	channel.setAttribute ("srcPort", chnnl.getLink (Port.DIR.OUT).getPort ().getName ().substring (1));
        	channel.setAttribute ("dstPort", chnnl.getLink (Port.DIR.IN).getPort ().getName ().substring (1));            	
        	channel.setAttribute ("srcPortRate", chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
        	channel.setAttribute ("dstPortRate", chnnl.getLink (Port.DIR.IN).getPort ().getRate ());            	
        	channel.setAttribute ("tokenSize", Integer.toString (chnnl.getTokenSize ()));
        	
        	if (chnnl.getInitialTokens () > 0)
        		channel.setAttribute ("initialTokens", Integer.toString (chnnl.getInitialTokens ()));
        	
        	// Source and destination port rates
        	channel.setAttribute ("srcPortRate", Integer.toString (prodRate));
        	channel.setAttribute ("dstPortRate", Integer.toString (consRate));
        	
        	// Buffer Size
        	int bufferSize = -1;
        	if(schedule.containsKey(SmtVariablePrefixes.maxBufferPrefix + srcActor.getName () + dstActor.getName ()))
        		bufferSize = Integer.parseInt(schedule.get(SmtVariablePrefixes.maxBufferPrefix + srcActor.getName () + dstActor.getName ()));
        	else
        		bufferSize = kMax * srcRepCnt * prodRate;            	
            
        	channel.setAttribute ("channelSize", Integer.toString(bufferSize));        	
        	rootElement.appendChild (channel);           	
        }
	}
	
	/**
	 * Add Actor elements in the XML file. 
	 * 
	 * @param graph application graph
	 * @param solutions solutions for application graph
	 * @param doc XML document 
	 * @param parent parent node where to add this element 
	 */
	private void addActorElements (Graph graph, Solutions solutions, Document doc, Element parent)
	{
		int numActors = graph.countActors();
		
		Element rootElement = doc.createElement ("graphActors");
		rootElement.setAttribute ("size", Integer.toString(numActors));
		parent.appendChild (rootElement);
		
		Iterator<Actor> actrIter = graph.getActors ();
        while (actrIter.hasNext ())            
        {
        	Actor actr = actrIter.next ();
        	Element actor = doc.createElement ("actor");
        	actor.setAttribute ("name", actr.getName ());
        	actor.setAttribute ("function", actr.getFunc());
        	actor.setAttribute ("instances", Integer.toString (solutions.getSolution (actr).returnNumber ()));
        	actor.setAttribute ("numPorts", Integer.toString (actr.numIncomingLinks () + actr.numOutgoingLinks ()));
        	rootElement.appendChild (actor);
        }
	}
	
	/**
	 * Add FIFO elements in the XML file. 
	 * 
	 * @param graph application graph
	 * @param solutions solutions for application graph
	 * @param doc XML document 
	 * @param parent parent node where to add this element
	 * @param schedule Model with scheduling information 
	 */
	private void addFifoElement(Graph graph, Solutions solutions, Map<String, String> schedule, Document doc, Element parent)
	{
		int latency = Integer.parseInt(schedule.get(SmtVariablePrefixes.latencyPrefix));
		int period = Integer.parseInt(schedule.get(SmtVariablePrefixes.periodPrefix));
		int kMax = (int) Math.ceil((double)latency / period);
		
		Element fifoAllocationElement = doc.createElement ("fifoallocation");
		fifoAllocationElement.setAttribute ("size", Integer.toString(graph.countChannels()));
		parent.appendChild(fifoAllocationElement);
		
		Iterator<Channel> chnnlIter = graph.getChannels ();
        while (chnnlIter.hasNext ())
        {
        	Channel chnnl = chnnlIter.next ();
        	Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
        	Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
        	int srcRepCnt = solutions.getSolution (srcActor).returnNumber ();
        	//int dstRepCnt = solutions.getSolution (dstActor).returnNumber ();
        	int prodRate = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
        	// int consRate = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());
        	        	
        	Element fifoElement = doc.createElement ("fifo");
        	fifoElement.setAttribute("name", chnnl.getName());
        	
        	int srcCluster = 0;
        	int dstCluster = 0;
        	
        	// Right now the fifo size is same as the channel size. we have one fifo per channel.
        	int bufferSize = -1;
        	if(schedule.containsKey(SmtVariablePrefixes.maxBufferPrefix + srcActor.getName () + dstActor.getName ()))
        		bufferSize = Integer.parseInt(schedule.get(SmtVariablePrefixes.maxBufferPrefix + srcActor.getName () + dstActor.getName ()));
        	else
        		bufferSize = kMax * srcRepCnt * prodRate;

        	fifoElement.setAttribute("srcCluster", Integer.toString (srcCluster));
        	fifoElement.setAttribute("dstCluster", Integer.toString (dstCluster));        	
        	fifoElement.setAttribute ("fifoSize", Integer.toString(bufferSize));
        	
        	fifoAllocationElement.appendChild(fifoElement);
        }		
	}
	
	/**
	 * @param schedule pipelined schedule
	 * @param processor processor index
	 * @return List of actor name and instance id sorted according to the xPrime values for a processor
	 */
	private List<Entry<String,Integer>> getScheduleForProcSortedWithXprime (Map<String, String> schedule, int processor)
	{
		int period = Integer.parseInt(schedule.get(SmtVariablePrefixes.periodPrefix));
		List<Entry<String,Integer>> result = new ArrayList<Entry<String,Integer>>();
		Map <Integer, String> unsortedResult = new HashMap <Integer, String> (); 
		
		for(String key : schedule.keySet())
		{
			if((key.startsWith(SmtVariablePrefixes.cpuPrefix)) && (Integer.parseInt(schedule.get(key)) == processor))
			{
				String actorName = key.substring(SmtVariablePrefixes.cpuPrefix.length());
				int startTime = Integer.parseInt(schedule.get(SmtVariablePrefixes.startTimePrefix + actorName));
				unsortedResult.put((startTime%period), actorName);
			}
		}
		
		Map<Integer, String> sortedResult = new TreeMap<Integer, String>(unsortedResult);
		
		for (Map.Entry<Integer, String> entry : sortedResult.entrySet()) 
		{
			String totalName = entry.getValue();
			String actorName = totalName.substring(0,totalName.indexOf("_"));
			int actorIndex = Integer.parseInt(totalName.substring(totalName.indexOf("_")+1));
			result.add(new AbstractMap.SimpleEntry<String, Integer>(actorName, actorIndex));
		}
		
		return result;
	}
	
	/**
	 * Generate Processor schedule information.
	 *  
	 * @param doc XML document
	 * @param proc processor index
	 * @param procSchedule Scheduling information for the processor.
	 * @param clusterElement parent cluster node where to add this element
	 */
	private void generateProcSchedule (Document doc, int proc, List<Entry<String,Integer>> procSchedule, Element clusterElement)
	{
		Element processorElement = doc.createElement ("processor");
		processorElement.setAttribute("id", Integer.toString(proc));
		
		processorElement.setAttribute("size", Integer.toString(procSchedule.size()));
		clusterElement.appendChild(processorElement);
		
		for(Entry<String,Integer> map : procSchedule)
		{
			String actr = map.getKey();
			int instanceId = map.getValue();
			Element actorInstanceElement = doc.createElement ("actor");
			actorInstanceElement.setAttribute("name", actr);
			actorInstanceElement.setAttribute("instance", Integer.toString(instanceId));
			processorElement.appendChild(actorInstanceElement);			
		}
	}
	
	/**
	 * Add schedule element to the XML file.
	 * 
	 * @param graph application graph
	 * @param solutions solutions for application graph
	 * @param doc XML document 
	 * @param parent parent node where to add this element
	 * @param schedule Model with scheduling information 
	 * 	 
	 */
	private void addScheduleElement (Graph graph, Solutions solutions, Map<String, String> schedule, Document doc, Element parent)
	{		
		int procUsedCount = 0;
		
		Element scheduleElement = doc.createElement ("schedule");
		scheduleElement.setAttribute ("size", Integer.toString(numClustersUsed));
		parent.appendChild(scheduleElement);
    	
		String clusterId = "0";
		
		Element clusterElement = doc.createElement ("cluster");
		clusterElement.setAttribute ("id", clusterId);		
		
		scheduleElement.appendChild(clusterElement);
		
		for(int i=0;i<totalProcInCluster;i++)
		{
			List<Entry<String,Integer>> procSchedule = getScheduleForProcSortedWithXprime (schedule, i);
			
			if(procSchedule.size() != 0)
			{
				procUsedCount++;
				generateProcSchedule (doc, i, procSchedule, clusterElement);
			}
		}
		
		clusterElement.setAttribute ("size", Integer.toString(procUsedCount));
	}
	
	/**
	 * @param graph
	 * @param schedule
	 * @param k
	 * @param processor
	 * @param yPrimeLess
	 * @return Actors that belong to a given K
	 */
	private Map<Integer, String> getActorsBelongToK (Graph graph, Map<String, String> schedule, int k, int processor, boolean yPrimeLess)
	{
		int period = Integer.parseInt(schedule.get(SmtVariablePrefixes.periodPrefix));
		Map <Integer, String> unsortedStartTimes = new HashMap <Integer, String> ();
		
		for(String key : schedule.keySet())
		{
			if ((key.startsWith(SmtVariablePrefixes.cpuPrefix)) && (Integer.parseInt(schedule.get(key)) == processor))
			{
				String actorName = key.substring(SmtVariablePrefixes.cpuPrefix.length());
				int startTime = Integer.parseInt(schedule.get(SmtVariablePrefixes.startTimePrefix + actorName));
				if((startTime >= k*period) && (startTime < (k+1)*period))
					unsortedStartTimes.put(startTime%period, actorName);	
			}			
		}
		
		Map<Integer, String> sortedResult = new TreeMap<Integer, String>(unsortedStartTimes);		
		return sortedResult;		
	}
	
	/**
	 * Add pre-schedule element to XML file.
	 * 
	 * @param graph application graph
	 * @param solutions solutions for application graph
	 * @param doc XML document 
	 * @param parent parent node where to add this element
	 * @param schedule piplined schedule model 
	 */
	private void addPreScheduleElement (Graph graph, Solutions solutions, Map<String, String> schedule, Document doc, Element parent)
	{
		int latency = Integer.parseInt(schedule.get(SmtVariablePrefixes.latencyPrefix));
		int period = Integer.parseInt(schedule.get(SmtVariablePrefixes.periodPrefix));
		int kMax = (int) Math.ceil((double)latency / period);
		
		if(kMax > 1)
		{
			Element scheduleElement = doc.createElement ("preschedule");
			scheduleElement.setAttribute ("size", Integer.toString(numClustersUsed));
			parent.appendChild(scheduleElement);
	    	
			String clusterId = "0";			
			Element clusterElement = doc.createElement ("cluster");
			clusterElement.setAttribute ("id", clusterId);
			scheduleElement.appendChild(clusterElement);
			int procAddedSchedCount = 0;
			
			for(int proc=0;proc<totalProcInCluster;proc++)
			{
				Element processorElement = doc.createElement ("processor");
				processorElement.setAttribute ("id", Integer.toString(proc));
				int procSchedSize = 0;				
				
				for(int i=0;i<kMax-1;i++)
				{
					Map<Integer, String> sortedActors = new TreeMap<Integer, String>();
					for(int j=0;j<=i;j++)
						sortedActors.putAll(getActorsBelongToK (graph, schedule, j, proc, false));
										
					for(int startTime : sortedActors.keySet())
					{
						procSchedSize++;
						String totalName = sortedActors.get(startTime);
						String actorName = totalName.substring(0,totalName.indexOf("_"));
						int actorIndex = Integer.parseInt(totalName.substring(totalName.indexOf("_")+1));
						
						Element actorInstanceElement = doc.createElement ("actor");
						actorInstanceElement.setAttribute("name", actorName);
						actorInstanceElement.setAttribute("instance", Integer.toString(actorIndex));
						processorElement.appendChild(actorInstanceElement);						
					}
					processorElement.setAttribute("size", Integer.toString(procSchedSize));
				}
				
				if(procSchedSize > 0)
				{
					clusterElement.appendChild(processorElement);
					procAddedSchedCount++;
				}
			}
			clusterElement.setAttribute ("size", Integer.toString(procAddedSchedCount));			
		}
	}
	
	/**
	 * Add Post schedule element to the XML file.
	 * 
	 * @param graph application graph
	 * @param solutions solutions for application graph
	 * @param doc XML document 
	 * @param parent parent node where to add this element
	 */
	private void addPostScheduleElement (Graph graph, Solutions solutions, Map<String, String> schedule, Document doc, Element parent)
	{
		int latency = Integer.parseInt(schedule.get(SmtVariablePrefixes.latencyPrefix));
		int period = Integer.parseInt(schedule.get(SmtVariablePrefixes.periodPrefix));
		int kMax = (int) Math.ceil((double)latency / period);
		
		if(kMax > 0)
		{
			Element scheduleElement = doc.createElement ("postschedule");
			scheduleElement.setAttribute ("size", Integer.toString(numClustersUsed));
			parent.appendChild(scheduleElement);
	    	
			String clusterId = "0";
			
			Element clusterElement = doc.createElement ("cluster");
			clusterElement.setAttribute ("id", clusterId);
			scheduleElement.appendChild(clusterElement);
			int procAddedSchedCount = 0;
			
			for(int proc=0;proc<totalProcInCluster;proc++)
			{
				Element processorElement = doc.createElement ("processor");
				processorElement.setAttribute ("id", Integer.toString(proc));
				int procSchedSize = 0;
				
				for(int i=1;i<=kMax;i++)
				{
					Map<Integer, String> sortedActors = new TreeMap<Integer, String>();
					for(int j=i;j<=kMax;j++)
						sortedActors.putAll(getActorsBelongToK (graph, schedule, j, proc, false));
					
					for(int startTime : sortedActors.keySet())
					{
						procSchedSize++;
						String totalName = sortedActors.get(startTime);
						String actorName = totalName.substring(0,totalName.indexOf("_"));
						int actorIndex = Integer.parseInt(totalName.substring(totalName.indexOf("_")+1));
						
						Element actorInstanceElement = doc.createElement ("actor");
						actorInstanceElement.setAttribute("name", actorName);
						actorInstanceElement.setAttribute("instance", Integer.toString(actorIndex));
						processorElement.appendChild(actorInstanceElement);						
					}
					processorElement.setAttribute("size", Integer.toString(procSchedSize));
				}
				
				if(procSchedSize > 0)
				{
					clusterElement.appendChild(processorElement);
					procAddedSchedCount++;
				}
			}
			clusterElement.setAttribute ("size", Integer.toString(procAddedSchedCount));
		}
	}
	
	/**
	 * Generate pipelined schedule XML for the platform.
	 * The XML file scheduling information has two elements 
	 * pre-schedule : to execute before all the overlapped iterations starts. (< kmax)
	 * post-schedule : after a fixed no. of iterations finish framework execute this 
	 * 				   schedule to finish application execution.
	 * 
	 * TODO : Replace schedule with design flow solution
	 * TODO : It supports only one cluster currently
	 * 
	 * @param outputFileName output schedule xml file name
	 * @param graph application graph 
	 * @param solutions solutions to the application graph
	 * @param schedule Model of the pipelined schedule 
	 */
	public void generateSolutionXml (String outputFileName, Graph graph, Solutions solutions, Map<String, String> schedule)
	{
		// TODO: For the Kalray I am now assuming only one cluster pipelined scheduling.
		// We have yet to consider multi-cluster scenario where we have to DMA.
		
		try 
		{			
			//We need a Document
            DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance ();
            DocumentBuilder docBuilder = dbfac.newDocumentBuilder ();
            Document doc = docBuilder.newDocument ();
            
            doc.setXmlStandalone (true);
            doc.setXmlVersion ("1.0");

            ////////////////////////
            //Creating the XML tree
            // create the root element and add it to the document
            // create child element, add an attribute, and add to root
            Element mapping = doc.createElement ("mapping");
            doc.appendChild (mapping);           
            
            // Add all actor elements
            addActorElements (graph, solutions, doc, mapping);
            
            // Add all channel elements
            addChannelElements (graph, solutions, doc, mapping, schedule);
            
            // Add pre-schedule element
            addPreScheduleElement (graph, solutions, schedule, doc, mapping);
            addScheduleElement (graph, solutions, schedule, doc, mapping);
            addPostScheduleElement (graph, solutions, schedule, doc, mapping);
            
            // Add Fifo Allocation Element
            addFifoElement (graph, solutions, schedule, doc, mapping);   
        
            /////////////////
            // Output the XML

            // set up a transformer
            TransformerFactory transfac = TransformerFactory.newInstance ();
            Transformer trans = transfac.newTransformer ();
            trans.setOutputProperty (OutputKeys.OMIT_XML_DECLARATION, "no");
            trans.setOutputProperty (OutputKeys.INDENT, "yes");
            trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            trans.setOutputProperty (OutputKeys.ENCODING, "UTF-8");

            // create string from xml tree
            StringWriter sw = new StringWriter ();
            StreamResult result = new StreamResult (sw);
            DOMSource source = new DOMSource (doc);
            trans.transform (source, result);
            // print xml
            // String xmlString = sw.toString ();
            // System.out.println ("Here's the xml:\n\n" + xmlString);
            
            FileWriter fw = new FileWriter (outputFileName);
            fw.write (sw.toString ());
            fw.close ();            

		} catch (ParserConfigurationException e) {
			e.printStackTrace ();
		} catch (TransformerConfigurationException e) {
			e.printStackTrace ();
		} catch (TransformerException e) {
			e.printStackTrace ();
		} catch (IOException e) {
			e.printStackTrace ();
		}
	}	
}
