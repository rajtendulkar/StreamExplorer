package platform.tilera.scheduleXML;

import graphanalysis.properties.GraphAnalysisSdfAndHsdf;

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;

import org.w3c.dom.*;

import solver.SmtVariablePrefixes;
import spdfcore.*;
import spdfcore.stanalys.*;

import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;


/**
 * Generate a non-pipelined schedule for Tilera platform. We can used the
 * XML generated from this class directly in the framework running on the
 * platform to execute an application schedule. 
 * 
 * TODO: In our generate XML we have a strict requirement for the srcPort and dstPort
 * of the channel. The programmer / application writer must be aware what is the effect
 * of assignment of the ports. In the input XML, if port p0 of actor A which is connected
 * to port p0 of actor B, it means that the output XML will also allocate port 0 for each
 * of them. Now there is no error checking for duplication of ports here. Secondly, the 
 * ActorInfo in the runtime of the Tilera has structure FIFO *incomingChannels and 
 * FIFO *outgoingChannels. Thus in ActorInfo structure for actor A, the outgoingChannels[0]
 * will point to this channel, whereas in incomingChannels[0] of actor B will point to this
 * channel. Now remember, that in this XML, each actor has number of incoming and outgoing 
 * channels. It is equal to allocation of array of incoming and outgoing FIFO's. Thus, if 
 * you write number of incoming channels equal to 1, and the port number of actor A equal to
 * lets say 2, then the runtime will crash. Since outgoingChannels[2] is not valid. However
 * there is no error-checking done currently for this ! So be careful !
 * 
 * @author Pranav Tendulkar
 */
public class NonPipelinedScheduleXml
{	
	/**
	 * Add Channel elements in the XML file. 
	 * 
	 * @param graph application graph
	 * @param solutions solutions for application graph
	 * @param doc XML document
	 * @param parent parent node where to add channels node
	 * @param model model for non-pipelined schedule
	 */
	private void addChannelElements (Graph graph, Solutions solutions, Document doc, Element parent, Map<String,String> model)
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
        	String bufferSizeStr = model.get (SmtVariablePrefixes.maxBufferPrefix + srcActor.getName () + dstActor.getName ());        	
        	if (bufferSizeStr != null)
        	{
        		int bufferSize = Integer.parseInt(bufferSizeStr) / chnnl.getTokenSize();
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
	 * @param doc XML document 
	 * @param parent parent node where to add this element 
	 * @param model model for non-pipelined schedule
	 */
	private void addActorElements (Graph graph, Solutions solutions, Document doc, Element parent, Map<String,String> model)
	{	
		Element rootElement = doc.createElement ("graphActors");
		rootElement.setAttribute ("size", Integer.toString(graph.countActors()));
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
	 * For each processor build a static order schedule.
	 * 
	 * @param model non-pipelined schedule.
	 */
	private Map<Integer, List<String>> buildProcessorSchedule (Map<String,String> model)
	{
		Map<Integer, List<String>> procSchedule = new HashMap<Integer, List<String>>();
		
		Iterator<String> mapIter = model.keySet ().iterator ();
		while (mapIter.hasNext ())
		{
			String key = mapIter.next ();
			if (key.contains ("cpu"))
			{
				if (procSchedule.containsKey (model.get (key)) == false)
				{
					List<String> actrList = new ArrayList<String>();
					procSchedule.put (Integer.parseInt (model.get (key)), actrList);
				}
			}
		}
		
		mapIter = model.keySet ().iterator ();
		while (mapIter.hasNext ())
		{
			String key = mapIter.next ();
			if (key.startsWith ("x"))
			{
				int proc = Integer.parseInt (model.get ("cpu" + key.substring (1)));
				List<String>schedList = procSchedule.get (proc);
				if (schedList.size () == 0)
				{
					schedList.add (key.substring (1));
				}
				else
				{
					int actrStartTime = Integer.parseInt (model.get (key));
					for (int i=schedList.size ()-1;i>=0;i--)
					{
						int otherStartTime = Integer.parseInt (model.get ("x"+schedList.get (i)));
						if (actrStartTime > otherStartTime)
						{
							schedList.add (i+1, key.substring (1));
							break;
						}
						
						else if (i==0)
							schedList.add (0, key.substring (1));
					}
				}
			}
		}
		
		return procSchedule;
	}
	
	/**
	 * Add Schedule information elements in the XML file. 
	 * 
	 * @param graph application graph
	 * @param solutions solutions for application graph
	 * @param doc XML document 
	 * @param parent parent node where to add this element 
	 * @param model model for non-pipelined schedule
	 */
	private void addScheduleElement (Graph graph, Solutions solutions, Document doc, Element parent, Map<String,String> model)
	{				
		Map<Integer, List<String>> procSchedule = buildProcessorSchedule (model);
		
		// We have to verify if all the processors are used or not.
		// It might happen that SMT solver gives total processors used = 4
		// and it does not allocate any task to a processor. In that case
		// the constraints are still satisfied. However, we cannot tolerate that
		// in the schedule XML.
		
		HashSet<Integer> uniqueProcUsed = new HashSet<Integer>();
		GraphAnalysisSdfAndHsdf graphAnalysis = new GraphAnalysisSdfAndHsdf(graph, solutions);
		for (Actor actr : graph.getActorList())
		{
			for(Actor hsdfActr : graphAnalysis.getSdfToAllHsdfActors(actr))
			{
				int cpuAllocated = Integer.parseInt(model.get(SmtVariablePrefixes.cpuPrefix + hsdfActr.getName()));
				uniqueProcUsed.add(new Integer(cpuAllocated));
			}
		}
		
		int totalProc = uniqueProcUsed.size();
        Element schedule = doc.createElement ("schedule");
        schedule.setAttribute ("size", Integer.toString (totalProc));
        parent.appendChild (schedule);
        
        Iterator<Integer>schedIter = procSchedule.keySet ().iterator ();
        while (schedIter.hasNext ())
        {
        	int procId = schedIter.next ();
        	List<String> sched = procSchedule.get (procId);
        	Element processor = doc.createElement ("processor");
        	processor.setAttribute ("id", Integer.toString (procId));
        	processor.setAttribute ("size", Integer.toString (sched.size ()));
        	
        	for (int i=0;i<sched.size ();i++)
        	{
        		Element schedActor = doc.createElement ("actor");
        		schedActor.setAttribute ("name", sched.get (i).substring (0, sched.get (i).indexOf ("_")));
        		schedActor.setAttribute ("instance", sched.get (i).substring (sched.get (i).indexOf ("_")+1, sched.get (i).length ()));
        		processor.appendChild (schedActor);
        	}
        	
        	schedule.appendChild (processor);            	
        }
	}
	
	/**
	 * Generate Schedule XML for application schedule to execute on the platform.
	 * 
	 * @param outputFileName output file name
	 * @param graph application graph
	 * @param solutions solutions for application graph
	 * @param model model for non-pipelined schedule
	 */
	public void generateSolutionXml (String outputFileName, Graph graph, Solutions solutions, Map<String,String> model)
	{		
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
            addActorElements (graph, solutions, doc, mapping, model);
            
            // Add all channel elements
            addChannelElements (graph, solutions, doc, mapping, model);
            
            // Add schedule element
            addScheduleElement (graph, solutions, doc, mapping, model);

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
