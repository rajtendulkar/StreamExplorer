package input;

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import spdfcore.*;

/**
 * Parse Application graph from an XML file.
 * 
 * @author Pranav Tendulkar
 *
 */
public class ParseApplicationGraph
{	
	/**
	 * Parse Actor properties from XML file.
	 * 
	 * @param actrPropNode root actor node in XML.
	 * @param g graph to be constructed
	 */
	private void parseActorPropertiesElement (Node actrPropNode, Graph g)
	{
		// TODO: We can have multiple execution times for the actor.
		// However we consider only first one right now.
		
		NamedNodeMap actorAttr = actrPropNode.getAttributes ();
		Actor actr = g.getActor (actorAttr.getNamedItem ("actor").getNodeValue ());
		
		NodeList child = actrPropNode.getChildNodes ();
		if (child != null && child.getLength () > 0) 
		{			
			for (int i=0;i<child.getLength ();i++)
			{
				Node node = child.item (i);
				if (node.getNodeName ().equals ("processor"))
				{
					NodeList procChild = node.getChildNodes ();
					for (int j=0;j<procChild.getLength ();j++)
					{
						Node procNode = procChild.item (j);
						if (procNode.getNodeName ().equals ("executionTime"))
						{
							NamedNodeMap procChildAttr = procNode.getAttributes ();
							actr.setExecTime (Integer.parseInt (procChildAttr.getNamedItem ("time").getNodeValue ()));
							break;
						}
						else if (procNode.getNodeName ().equals ("memory"))
						{}
					}					
				}				
			}
		}		
	}
	
	/**
	 * Parse Channel properties from XML file.
	 * 
	 * @param channelPropNode root channel node in XML.
	 * @param g graph to be constructed
	 */
	private void parseChannelPropertiesElement (Node channelPropNode, Graph g)
	{
		NamedNodeMap channelAttr = channelPropNode.getAttributes ();
		Channel chnnl = g.getChannel (channelAttr.getNamedItem ("channel").getNodeValue ());
		
		NodeList child = channelPropNode.getChildNodes ();
		if (child != null && child.getLength () > 0) 
		{			
			for (int i=0;i<child.getLength ();i++)
			{
				Node node = child.item (i);
				if (node.getNodeName ().equals ("tokenSize"))
				{
					NamedNodeMap tokenAttr = node.getAttributes ();
					String tokenSize = tokenAttr.getNamedItem ("sz").getNodeValue ();
					chnnl.setTokenSize (Integer.parseInt (tokenSize));
				}
			}
		}		
	}
	
	/**
	 *  Parse Channel information from XML file.
	 *  
	 * @param channelNode root channel node in XML.
	 * @param g graph to be constructed
	 */
	private void parseChannelElement (Node channelNode, Graph g)
	{
		NamedNodeMap channelNodeAttr = channelNode.getAttributes ();		
		
		String chnnlName= channelNodeAttr.getNamedItem ("name").getNodeValue ();		
		int initialTokens = 0;
		PortRef src = new PortRef ();
		PortRef snk = new PortRef ();
		
		src.setActorName (channelNodeAttr.getNamedItem ("srcActor").getNodeValue ());
		src.setPortName (channelNodeAttr.getNamedItem ("srcPort").getNodeValue ());
		
		snk.setActorName (channelNodeAttr.getNamedItem ("dstActor").getNodeValue ());
		snk.setPortName (channelNodeAttr.getNamedItem ("dstPort").getNodeValue ());
		
		Node initialTokensNode = channelNodeAttr.getNamedItem ("initialTokens");
		if (initialTokensNode != null)		
			initialTokens = Integer.parseInt (initialTokensNode.getNodeValue ());			
               
		Channel channel = new Channel ();
		channel.setName (chnnlName);
		g.add (channel);
		channel.bind (src, snk);
		channel.setInitialTokens (initialTokens);
	}
	
	/**
	 * Parse Actor information from XML file.
	 * 
	 * @param actorNode root actor node in XML.
	 * @param g graph to be constructed
	 */
	private void parseActorElement (Node actorNode, Graph g)
	{
		Actor actr = new Actor ();
		
		NamedNodeMap actorNodeAttr = actorNode.getAttributes ();
		actr.setName (actorNodeAttr.getNamedItem ("name").getNodeValue ());
		actr.setFunc (actorNodeAttr.getNamedItem ("type").getNodeValue ());
		
		g.add (actr);
		
		NodeList child = actorNode.getChildNodes ();
		if (child != null && child.getLength () > 0) 
		{			
			for (int i=0;i<child.getLength ();i++)
			{
				Node node = child.item (i);				
				if (node.getNodeName ().equals ("port"))
				{
					NamedNodeMap portNodeAttr = node.getAttributes ();
					
					String portName = portNodeAttr.getNamedItem ("name").getNodeValue ();
					String portRate = portNodeAttr.getNamedItem ("rate").getNodeValue ();
					
					Port.DIR portDirection;
					if (portNodeAttr.getNamedItem ("type").getNodeValue ().equals ("in"))
						portDirection = Port.DIR.IN;
					else
						portDirection = Port.DIR.OUT;					
					
					Id portId = new Id();
					portId.setFunc(actr.getFunc ());
					portId.setName(portName);
					if(g.hasPort(portId) == false)
					{
					
						Port port  = new Port (portDirection);
						// I am doing this so that we have unique name per port
						// else it will create another exception.
						port.setFunc (actr.getFunc ());
						port.setName (portName);        		
						port.setRate (portRate);
						g.add (port);					
					}
				}
			}
		}		
	}
	
	/**
	 * Top level function to build SDF graph.
	 * 
	 * @param root XML root node
	 * @return Graph structure
	 */
	private Graph parseSdfGraph (Element root)
	{
		Graph graph = null;
		NodeList child = root.getChildNodes ();
		if (child != null && child.getLength () > 0) 
		{
			graph = new Graph ();
			for (int i=0;i<child.getLength ();i++)
			{
				Node sdfGraphnode = child.item (i);
				if (sdfGraphnode.getNodeName ().equals ("sdf"))
				{
					NamedNodeMap attr = sdfGraphnode.getAttributes ();
					graph.setGraphAppName (attr.getNamedItem ("name").getNodeValue ());
					// for (int j=0;j<attr.getLength ();j++)
					//	System.out.println ("Node : " + attr.item (j).getNodeName () + " Val : " + attr.item (j).getNodeValue ());					
					
					NodeList sdfChild = sdfGraphnode.getChildNodes ();
					if (sdfChild != null && sdfChild.getLength () > 0) 
					{
						for (int j=0;j<sdfChild.getLength ();j++)
						{
							Node graphNode = sdfChild.item (j);
							if (graphNode.getNodeName ().equals ("actor"))
								parseActorElement (graphNode, graph);
							else if (graphNode.getNodeName ().equals ("channel"))
								parseChannelElement (graphNode, graph);
						}
					}
					
				}
				else if (sdfGraphnode.getNodeName ().equals ("sdfProperties"))
				{
					NodeList sdfChild = sdfGraphnode.getChildNodes ();
					if (sdfChild != null && sdfChild.getLength () > 0) 
					{
						for (int j=0;j<sdfChild.getLength ();j++)
						{
							Node graphNode = sdfChild.item (j);
							
							if (graphNode.getNodeName ().equals ("actorProperties"))
								parseActorPropertiesElement (graphNode, graph);
							
							if (graphNode.getNodeName ().equals ("channelProperties"))
								parseChannelPropertiesElement (graphNode, graph);
						}
					}					
				}
				// else
				//	System.out.println ("Unhandled property : " + sdfGraphnode.getNodeName ());

			}
		}
		
		return graph;
	}

	/**
	 * Parse single application graph from XML file.
	 * 
	 * @param fileName file name to XML file
	 * @return graph structure from the XML file.
	 */
	public Graph parseSingleGraphXml (String fileName)
	{
		File f = new File (fileName);
		if (f.exists () == false)
			throw new RuntimeException ("Input file : " + fileName + " does not exist.");		
		List<Graph> result = parseMultipleGraphXml (fileName);
		if (result.size () != 1)
			throw new RuntimeException ("Expected Size of Application graph was 1, but found : " + result.size () 
					+ ". Correct the Graph XML or use parseMultipleGraphXml () for parsing.");
		return result.get (0);
	}
	
	/**
	 * Parse an XML file for application graph and build a graph structure.
	 * 
	 * @param fileName file name to XML file
	 * @return list of graphs present in the XML file
	 */
	public  List<Graph> parseMultipleGraphXml (String fileName) 
	{
		List<Graph> result = new ArrayList<Graph>();

		//get the factory
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance ();

		try 
		{
			//Using factory get an instance of document builder
			DocumentBuilder db = dbf.newDocumentBuilder ();

			//parse using builder to get DOM representation of the XML file
			Document dom = db.parse (fileName);

			//get the root element
			Element docEle = dom.getDocumentElement ();

			//get a nodelist of  elements
			NodeList nl = docEle.getElementsByTagName ("applicationGraph");
			if (nl != null && nl.getLength () > 0) 
			{
				for (int i = 0 ; i < nl.getLength ();i++)
				{
					Element el = (Element)nl.item (i);					
					Graph g = parseSdfGraph (el);					
					result.add (g);
				}
			}

		}catch (ParserConfigurationException pce) {
			pce.printStackTrace ();
		}catch (SAXException se) {
			se.printStackTrace ();
		}catch (IOException ioe) {
			ioe.printStackTrace ();
		}

		return result;
	}
}
