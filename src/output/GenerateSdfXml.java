package output;

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

import spdfcore.*;
import spdfcore.Channel.Link;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;

/**
 * Generates an application XML file from the Graph object.
 * 
 * @author Pranav Tendulkar
 *
 */
public class GenerateSdfXml 
{
	/**
	 * 
	 * Generate Application Graph XML file
	 * 
	 * @param sdfGraph Graph object
	 * @param outputFileName output file name
	 */
	public void generateOutput (Graph sdfGraph, String outputFileName)
	{
		if ((outputFileName.endsWith(".xml") == false) && (outputFileName.endsWith(".XML") == false))
			outputFileName = outputFileName.concat(".xml");
		
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

            //create the root element and add it to the document
            //create child element, add an attribute, and add to root
            Element rootElement = doc.createElement ("sdf3");
            doc.appendChild (rootElement);
            
            rootElement.setAttribute("type", "sdf");
            
            Element appGraphElement = doc.createElement ("applicationGraph");
            rootElement.appendChild(appGraphElement);            
            
            Element sdfElement = doc.createElement ("sdf");
            sdfElement.setAttribute("name", sdfGraph.getGraphAppName());
            sdfElement.setAttribute("type", sdfGraph.getGraphAppName());  
            appGraphElement.appendChild(sdfElement);
            
            Element sdfPropElement = doc.createElement ("sdfProperties");
            appGraphElement.appendChild(sdfPropElement);
            
            Iterator<Actor> actrIter = sdfGraph.getActors ();
            while (actrIter.hasNext ())            
            {
            	Actor actr = actrIter.next ();
            	Element actor = doc.createElement ("actor");
            	actor.setAttribute ("name", actr.getName ());
            	actor.setAttribute ("type", actr.getFunc ());
            	
            	for(Link link : actr.getAllLinks())
            	{
            		// default input port
            		String type = "in";            		
            		if(link.getPort().getDir() == Port.DIR.OUT)
            			type = "out";
            		
            		Element linkElement = doc.createElement ("port");
            		linkElement.setAttribute("name", link.getPort().getName());
            		linkElement.setAttribute("type", type);
            		linkElement.setAttribute("rate", link.getPort().getRate());
            		actor.appendChild(linkElement);
            	}
            	
            	Element actorProp = doc.createElement ("actorProperties");
            	actorProp.setAttribute("actor", actr.getName());
            	
            	Element procElement = doc.createElement ("processor");
            	procElement.setAttribute("type", "Kalray");
            	procElement.setAttribute("default", "true");
            	actorProp.appendChild(procElement);
            	
            	Element execTimeElement = doc.createElement ("executionTime");
            	execTimeElement.setAttribute("time", Integer.toString(actr.getExecTime()));
            	procElement.appendChild(execTimeElement);
            	
            	sdfElement.appendChild (actor);
            	sdfPropElement.appendChild(actorProp);
            }
            
            Iterator<Channel> chnnlIter = sdfGraph.getChannels ();
            while (chnnlIter.hasNext ())
            {
            	Channel chnnl = chnnlIter.next ();
            	Element channel = doc.createElement ("channel");
            	channel.setAttribute("name", chnnl.getName());
            	channel.setAttribute("srcActor", chnnl.getLink(Port.DIR.OUT).getActor().getName());
            	channel.setAttribute("dstActor", chnnl.getLink(Port.DIR.IN).getActor().getName());
            	channel.setAttribute("srcPort", chnnl.getLink(Port.DIR.OUT).getPort().getName());
            	channel.setAttribute("dstPort", chnnl.getLink(Port.DIR.IN).getPort().getName());            	
            	
            	Element chnnlProp = doc.createElement ("channelProperties");
            	chnnlProp.setAttribute("channel", chnnl.getName());
            	Element tokenSizeElem = doc.createElement ("tokenSize");
            	tokenSizeElem.setAttribute("sz", Integer.toString(chnnl.getTokenSize()));
            	chnnlProp.appendChild(tokenSizeElem);
            	
            	sdfElement.appendChild (channel);  
            	sdfPropElement.appendChild(chnnlProp);
            }            
        
            /////////////////
            //Output the XML

            //set up a transformer
            TransformerFactory transfac = TransformerFactory.newInstance ();
            Transformer trans = transfac.newTransformer ();
            trans.setOutputProperty (OutputKeys.OMIT_XML_DECLARATION, "no");
            trans.setOutputProperty (OutputKeys.INDENT, "yes");
            trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            trans.setOutputProperty (OutputKeys.ENCODING, "UTF-8");

            //create string from xml tree
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

		} catch (Exception e) { e.printStackTrace (); }
	}	
}
