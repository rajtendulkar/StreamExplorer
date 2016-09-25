package input;

import java.io.File;

import javax.xml.parsers.*;
import org.w3c.dom.*;

import spdfcore.*;

/**
 * Parse profiling information generated from the framework to 
 * build application graph.
 * 
 * @author Pranav Tendulkar
 *
 */
public class ParseProfileInfo
{
	/**
	 * Application graph
	 */
	private Graph graph;
	
	/**
	 * Whether to use MIN, MAX or MEAN execution time of the actors. 
	 * 
	 */
	private final String getExecTime = "MAX"; 
	
	/**
	 * Parse SDF graph properties
	 * 
	 * @param node root node of SDF properties.
	 */
	private void parseGraphSdfPropertiesNode (Node node)
	{
		NodeList child = node.getChildNodes ();
		if (child != null && child.getLength () > 0) 
		{
			for (int i=0;i<child.getLength ();i++)
			{
				Node propertyNode = child.item (i);
				if (propertyNode.getNodeName().equals ("actorProperties"))
				{
					String actor = propertyNode.getAttributes().getNamedItem("actor").getNodeValue();
					String actorExecTime = null;
					NodeList propChild = propertyNode.getChildNodes();
					for (int j=0;j<propChild.getLength ();j++)
					{
						Node actorPropNode = propChild.item (j);
						if (actorPropNode.getNodeName ().equals ("executionTime"))
						{
							if(getExecTime.equals("MIN"))
								actorExecTime = actorPropNode.getAttributes().getNamedItem("min").getNodeValue();
							else if(getExecTime.equals("MAX"))
								actorExecTime = actorPropNode.getAttributes().getNamedItem("max").getNodeValue();
							else if(getExecTime.equals("MEAN"))
								actorExecTime = actorPropNode.getAttributes().getNamedItem("mean").getNodeValue();
							else
								throw new RuntimeException("Unable to understand that actr exec time setting : " + getExecTime);
								
							break;
						}
					}
					
					double exectime = Double.parseDouble(actorExecTime);
					graph.getActor(actor).setExecTime((int)exectime);
				}
			}
		}
	}
	
	/**
	 * Parse SDF channels.
	 * 
	 * @param node root node to SDF channels
	 */
	private void parseGraphChannelsNode (Node node)
	{
		NamedNodeMap attributeMap = node.getAttributes ();
		int numChannels = Integer.parseInt(attributeMap.getNamedItem ("size").getNodeValue ());
		int scannedChannels = 0;
		
		NodeList child = node.getChildNodes ();
		if (child != null && child.getLength () > 0) 
		{
			for (int i=0;i<child.getLength ();i++)
			{
				Node channelNode = child.item (i);
				if (channelNode.getNodeName ().equals ("channel"))
				{
					NamedNodeMap channelAttributeMap = channelNode.getAttributes();
					String channelName = channelAttributeMap.getNamedItem ("name").getNodeValue ();
					String srcActor = channelAttributeMap.getNamedItem ("srcActor").getNodeValue ();
					String dstActor = channelAttributeMap.getNamedItem ("dstActor").getNodeValue ();
					int srcPort = Integer.parseInt(channelAttributeMap.getNamedItem ("srcPort").getNodeValue());
					int dstPort = Integer.parseInt(channelAttributeMap.getNamedItem ("dstPort").getNodeValue());
					String srcRate = channelAttributeMap.getNamedItem ("srcPortRate").getNodeValue();
					String dstRate = channelAttributeMap.getNamedItem ("dstPortRate").getNodeValue();
					int tokenSize = Integer.parseInt(channelAttributeMap.getNamedItem ("tokenSize").getNodeValue());
					int initialTokens = 0;					
					Node initialTokensNode = channelAttributeMap.getNamedItem ("initialTokens");
					if (initialTokensNode != null)		
						initialTokens = Integer.parseInt (initialTokensNode.getNodeValue ());	
					
					// Add the ports if necessary.
					Id outputPortId = new Id();
					outputPortId.setName("p" + Integer.toString(srcPort));
					outputPortId.setFunc(graph.getActor(srcActor).getFunc());
					
					if(graph.hasPort(outputPortId) == false)
					{
						Port outputPort  = new Port (Port.DIR.OUT);
						outputPort.setFunc (graph.getActor(srcActor).getFunc());
						outputPort.setName ("p" + Integer.toString(srcPort));        		
						outputPort.setRate (srcRate);
						graph.add (outputPort);
					}
					
					Id inputPortId = new Id();
					inputPortId.setName("p" + Integer.toString(dstPort));
					inputPortId.setFunc(graph.getActor(dstActor).getFunc());
					
					if(graph.hasPort(inputPortId) == false)
					{
						Port inputPort  = new Port (Port.DIR.IN);
						inputPort.setFunc (graph.getActor(dstActor).getFunc());
						inputPort.setName ("p" + Integer.toString(dstPort));        		
						inputPort.setRate (dstRate);
						graph.add (inputPort);
					}
					
					
					PortRef src = new PortRef ();
					PortRef snk = new PortRef ();
					
					src.setActorName (srcActor);
					src.setPortName ("p"+Integer.toString(srcPort));
					
					snk.setActorName (dstActor);
					snk.setPortName ("p"+Integer.toString(dstPort));
					
					Channel channel = new Channel ();
					channel.setName (channelName);
					graph.add (channel);
					channel.bind (src, snk);
					channel.setInitialTokens (initialTokens);
					channel.setTokenSize(tokenSize);
					
					scannedChannels++;
				}
			}
		}
		
		if(numChannels != scannedChannels)
			throw new RuntimeException("Expected channels : " + numChannels + " scanned channels : " + scannedChannels);
	}
	
	/**
	 * Parse SDF actors.
	 * 
	 * @param node root node to SDF actors
	 */
	private void parseGraphActorsNode (Node node)
	{
		NamedNodeMap attributeMap = node.getAttributes ();
		int numActors = Integer.parseInt(attributeMap.getNamedItem ("size").getNodeValue ());
		int scannedActors = 0;
		
		NodeList child = node.getChildNodes ();
		if (child != null && child.getLength () > 0) 
		{
			for (int i=0;i<child.getLength ();i++)
			{
				Node actorNode = child.item (i);
				if (actorNode.getNodeName ().equals ("actor"))
				{
					NamedNodeMap actorAttributeMap = actorNode.getAttributes ();
					String actorName = actorAttributeMap.getNamedItem ("name").getNodeValue ();
					String functionName = actorAttributeMap.getNamedItem ("function").getNodeValue ();
					
					Actor actr = new Actor ();
					
					actr.setName (actorName);
					actr.setFunc (functionName);					
					graph.add (actr);					
					
					scannedActors++;
				}
			}
		}
		
		if(numActors != scannedActors)
			throw new RuntimeException("Expected actors : " + numActors + " scanned actors : " + scannedActors);
	}
	
	/**
	 * Parse profiling XML file from framework and build application graph.
	 * 
	 * @param fileName input file name of the XML profile file
	 * @return application graph 
	 */
	public Graph parseProfileXml (String fileName)
	{
		graph = new Graph();
		
		//get the factory
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance ();

		try 
		{
			File f = new File(fileName);
			if(f.exists() != true)
				throw new RuntimeException("The input File does not exist.");
			
			//Using factory get an instance of document builder
			DocumentBuilder db = dbf.newDocumentBuilder ();

			//parse using builder to get DOM representation of the XML file
			Document dom = db.parse (fileName);

			//get the root element
			Element docEle = dom.getDocumentElement ();
			
			//get a nodelist of  elements
			parseGraphActorsNode (docEle.getElementsByTagName ("graphActors").item(0));
			parseGraphChannelsNode (docEle.getElementsByTagName ("graphChannels").item(0));
			parseGraphSdfPropertiesNode (docEle.getElementsByTagName ("sdfProperties").item(0));
			
		}catch (Exception e) { e.printStackTrace (); }
		
		return graph;
	}
}
