package platform.kalray.scheduleXML;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;

import platform.model.*;
import solver.SmtVariablePrefixes;
import spdfcore.*;
import spdfcore.stanalys.Solutions;
import designflow.DesignFlowSolution;

/**
 * Generate a non-pipelined schedule XMl file to execute on the platform. 
 * 
 * @author Pranav Tendulkar
 *
 */
public class NonPipelinedScheduleXml
{
	/**
	 * Tags used for DMA Fifo on the platform.
	 */
	private int tags[];
	
	/**
	 * Add Channel elements in the XML file. 
	 * 
	 * @param graph application graph
	 * @param solutions solutions for application graph
	 * @param designSolution design flow solution
	 * @param doc XML document 
	 * @param parent parent node where to add this element 
	 */
	private void addChannelElements (Graph graph, Solutions solutions, DesignFlowSolution designSolution, Document doc, Element parent)
	{
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
        	int bufferSize = designSolution.getSchedule().getBufferSize(chnnl);
        	if (bufferSize != -1)
        	{
        		if(designSolution.getSchedule().getAllocatedCluster(srcActor.getName()) != designSolution.getSchedule().getAllocatedCluster(dstActor.getName()))
        			bufferSize /= 2;
            	
            	channel.setAttribute ("channelSize", Integer.toString(bufferSize));	            	
        	}
        	else
        	{
        		// We can set the channel size to some maximum value, where the schedule will not fail.
        		channel.setAttribute ("channelSize", Integer.toString (srcRepCnt * prodRate));
        	}
        	
        	rootElement.appendChild (channel);           	
        }
	}
	
	/**
	 * Add Actor elements in the XML file. 
	 * 
	 * @param graph application graph
	 * @param solutions solutions for application graph
	 * @param designSolution design flow solution
	 * @param doc XML document 
	 * @param parent parent node where to add this element 
	 */
	private void addActorElements (Graph graph, Solutions solutions, DesignFlowSolution designSolution, Document doc, Element parent)
	{
		HashSet<String> addedActors = designSolution.getSchedule().getNonAppGraphActors();
		int numActors = graph.countActors() + addedActors.size();
		
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
        
        Solutions partitionGraphSolutions = designSolution.getPartitionAwareGraphSolutions();
        Graph partitionAwareGraph = designSolution.getpartitionAwareGraph();
        for(String actr : addedActors)
        {
        	Element actor = doc.createElement ("actor");
        	actor.setAttribute ("name", actr);        	
        	actor.setAttribute ("instances", Integer.toString (partitionGraphSolutions.getSolution (partitionAwareGraph.getActor(actr)).returnNumber ()));
        	
        	Channel chnnl = graph.getChannel(actr);
        	if(chnnl != null)
        	{
        		actor.setAttribute ("numPorts", Integer.toString (0));
        		actor.setAttribute ("function", SmtVariablePrefixes.writerStatusUpdateTaskFunction);
        	}
        	else
        	{
        		Actor grphActor = partitionAwareGraph.getActor(actr);
        		actor.setAttribute ("numPorts", Integer.toString (grphActor.numIncomingLinks () + grphActor.numOutgoingLinks ()));
        		actor.setAttribute ("function", grphActor.getFunc());
        	}
        	rootElement.appendChild (actor);
        }
	}
	
	/**
	 * Add Schedule information elements in the XML file. 
	 * 
	 * @param graph application graph
	 * @param solutions solutions for application graph
	 * @param designSolution design flow solution
	 * @param doc XML document 
	 * @param parent parent node where to add this element 
	 */
	private void addScheduleElement (Graph graph, Solutions solutions, DesignFlowSolution designSolution, Document doc, Element parent)
	{
		int numClustersUsed = designSolution.getPartition().getNumGroups();
		
		Element scheduleElement = doc.createElement ("schedule");
		scheduleElement.setAttribute ("size", Integer.toString(numClustersUsed));
		parent.appendChild(scheduleElement);
    	
		for(Cluster cluster : designSolution.getMapping().usedClusters())
		{
			int numProcUsed = designSolution.getSchedule().numProcessorsUsed(cluster);
			String clusterId = Integer.toString(cluster.getId());
			
			Element clusterElement = doc.createElement ("cluster");
			clusterElement.setAttribute ("id", clusterId);
			clusterElement.setAttribute ("size", Integer.toString(numProcUsed));
			
			scheduleElement.appendChild(clusterElement);
			
			for(int i=0;i<cluster.getNumProcInCluster();i++)
			{
				Processor processor = cluster.getProcessor(i);
				List<Entry<String,Integer>> schedule = designSolution.getSchedule().getSortedActorInstances (processor);
				
				if(schedule.size() != 0)
				{
					Element processorElement = doc.createElement ("processor");
					processorElement.setAttribute("id", Integer.toString(processor.getId()));
					
					processorElement.setAttribute("size", Integer.toString(schedule.size()));
					clusterElement.appendChild(processorElement);
					
					for(Entry<String,Integer> map : schedule)
					{
						String actr = map.getKey();
						int instanceId = map.getValue();
						Element actorInstanceElement = doc.createElement ("actor");
						actorInstanceElement.setAttribute("name", actr);
						actorInstanceElement.setAttribute("instance", Integer.toString(instanceId));
						processorElement.appendChild(actorInstanceElement);			
					}
				}
			}			
		}
	}
	
	/**
	 * Add FIFO elements in the XML file. 
	 * 
	 * @param graph application graph
	 * @param solutions solutions for application graph
	 * @param designSolution design flow solution
	 * @param platform platform model for Kalray platform
	 * @param doc XML document 
	 * @param parent parent node where to add this element 
	 */
	private void addFifoElement(Graph graph, Solutions solutions, DesignFlowSolution designSolution, 
								Platform platform, Document doc, Element parent)
	{
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
        	
        	Cluster srcCluster = designSolution.getSchedule().getAllocatedCluster(srcActor.getName());
        	Cluster dstCluster = designSolution.getSchedule().getAllocatedCluster(dstActor.getName());
        	
        	Element fifoElement = doc.createElement ("fifo");
        	fifoElement.setAttribute("name", chnnl.getName());
        	
        	// Right now the fifo size is same as the channel size. we have one fifo per channel.
        	int bufferSize = designSolution.getSchedule().getBufferSize(chnnl);        	
        	if (bufferSize == -1)
        		bufferSize = srcRepCnt * prodRate;

        	fifoElement.setAttribute("srcCluster", Integer.toString (srcCluster.getId()));
        	fifoElement.setAttribute("dstCluster", Integer.toString (dstCluster.getId()));
        	
        	if(srcCluster != dstCluster)
        	{
        		// We have to divide the channel size by two for our FIFO implementation.
        		if(designSolution.getSchedule().getBufferSize(chnnl) != -1)
        			bufferSize /= 2;
        		
        		fifoElement.setAttribute("dstTokenPortalTag",  Integer.toString(tags[platform.getClusterIndex(dstCluster)]++));
	        	fifoElement.setAttribute("srcStatusPortalTag", Integer.toString(tags[platform.getClusterIndex(srcCluster)]++));
	        	
	        	int maxSrcWriters = designSolution.getSchedule().getMaxDmaEnginesUsed(srcCluster, chnnl.getName());
	        	int maxDstWriters = designSolution.getSchedule().getMaxDmaEnginesUsed(dstCluster, chnnl.getName());	        	
	        	
	        	fifoElement.setAttribute("srcMaxWriters", Integer.toString(maxSrcWriters));
	        	fifoElement.setAttribute("dstMaxWriters", Integer.toString(maxDstWriters));
        	}
        	
        	fifoElement.setAttribute ("fifoSize", Integer.toString(bufferSize));
        	
        	fifoAllocationElement.appendChild(fifoElement);
        }		
	}
	
	/**
	 * Add DMA information elements in the XML file. It contains information for DMA for 
	 * FIFO.
	 * 
	 * @param graph application graph
	 * @param solutions solutions for application graph
	 * @param designSolution design flow solution
	 * @param platform platform model for Kalray platform
	 * @param doc XML document 
	 * @param parent parent node where to add this element
	 */
	private void addDmaScheduleElement (Graph graph, Solutions solutions, DesignFlowSolution designSolution, 
			Platform platform, Document doc, Element parent)
	{
		Element dmaScheduleElement = doc.createElement ("dmaSchedule");		
		parent.appendChild(dmaScheduleElement);
		int numClustersUsed = 0;
		for(Cluster cluster : designSolution.getMapping().usedClusters())
		{
			int clusterScheduleSize = 0;
			Element clusterScheduleElement = null;
			for (int i=0;i<cluster.getNumDmaInCluster();i++)
			{
				DmaEngine dma = cluster.getDmaEngine(i);				
				List<Entry<String,Integer>> schedule = designSolution.getSchedule().getSortedActorInstances (dma);
				if(schedule.size() != 0)
				{
					if (clusterScheduleElement == null)
					{
						clusterScheduleElement = doc.createElement ("cluster");
						clusterScheduleElement.setAttribute("id", Integer.toString(cluster.getId()));
					}
					
					Element dmaInstanceScheduleElement = doc.createElement ("dma");
					dmaInstanceScheduleElement.setAttribute("id", Integer.toString(dma.getId()));
					dmaInstanceScheduleElement.setAttribute("size", Integer.toString(schedule.size()));
					clusterScheduleElement.appendChild(dmaInstanceScheduleElement);
					
					for(Entry<String,Integer>dmaEntry : schedule)
					{
						// I should get the channelName.
						Element dmaInstanceElement = doc.createElement ("dmaInstance");
						int instanceId = dmaEntry.getValue();
						String chanelName = "";
						String dmaTaskName = dmaEntry.getKey();
						
						if (dmaTaskName.startsWith(SmtVariablePrefixes.dmaTokenTaskPrefix))
							chanelName = dmaTaskName.replaceAll(SmtVariablePrefixes.dmaTokenTaskPrefix, "");
						else if (dmaTaskName.startsWith(SmtVariablePrefixes.dmaStatusTaskPrefix))
							chanelName = dmaTaskName.replaceAll(SmtVariablePrefixes.dmaStatusTaskPrefix, "");
						else
							throw new RuntimeException("Is this really a DMA Task : " + dmaTaskName);					
						
						dmaInstanceElement.setAttribute("channelName", chanelName);						
						dmaInstanceElement.setAttribute("instanceId", Integer.toString(instanceId));
						dmaInstanceScheduleElement.appendChild(dmaInstanceElement);
					}
					
					clusterScheduleSize++;
				}
			}
				
			if(clusterScheduleElement != null)
			{
				clusterScheduleElement.setAttribute("size", Integer.toString(clusterScheduleSize));
				dmaScheduleElement.appendChild(clusterScheduleElement);
				numClustersUsed++;
			}
		}
		
		dmaScheduleElement.setAttribute ("size", Integer.toString(numClustersUsed));
	}
	
	/**
	 * Generate Schedule XML for application schedule to execute on the platform.
	 * 
	 * @param outputFileName output file name
	 * @param graph application graph
	 * @param solutions solutions for application graph
	 * @param platform platform model for Kalray platform
	 * @param designSolution design flow solution 
	 */
	public void generateSolutionXml (String outputFileName, Graph graph, Solutions solutions, Platform platform, DesignFlowSolution designSolution)
	{
		tags = new int[platform.getNumClusters()];
		
		// The first 3 tags in Kalray are used by the run-time manager.
		for(int i=0;i<platform.getNumClusters();i++)
			tags[i] = 4;
		
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
            addActorElements (graph, solutions, designSolution, doc, mapping);
            
            // Add all channel elements
            addChannelElements (graph, solutions, designSolution, doc, mapping);
            
            // Add schedule element
            addScheduleElement (graph, solutions, designSolution, doc, mapping);
            
            // Add Fifo Allocation Element
            addFifoElement (graph, solutions, designSolution, platform, doc, mapping);    
            
            // Add DMA Schedule Element
            addDmaScheduleElement (graph, solutions, designSolution, platform, doc, mapping);
        
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
