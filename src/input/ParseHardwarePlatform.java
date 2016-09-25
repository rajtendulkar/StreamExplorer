package input;

import java.io.IOException;
import java.util.*;

import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import platform.model.*;


/**
 * Parse Platfom XML to build a platform model.
 * 
 * We first scan and save the information from the XML file.
 * And then build a platform from this information available.
 * This helps to have number of "const" variables in the model, which cannot
 * be altered later on.
 * 
 * @author Pranav Tendulkar
 */
public class ParseHardwarePlatform
{	
	/**
	 * Class to save the properties of platform.
	 *
	 */
	private class PlatformProp
	{
		public int dmaSetupTime=-1;
		
		public void clear()
		{
			dmaSetupTime = -1;
		}
	}
	
	/**
	 * Class to save scanned information about DMA from the XML.
	 */
	private class DmaInfo
	{
		public String name=null;
		public int id=-1;
		public String cluster=null;
	}
	
	/**
	 * Class to save scanned information about Memory from the XML.
	 *
	 */
	private class MemoryInfo
	{
		public String name=null;
		public int id=-1;
		public int numProcessors=0;
		public int numClusters=0;
		public long size=-1;
		public String unit=null;
		public int latency=0;				
		public String proc[];
		public String cluster[];
	}
	
	/**
	 * Class to save scanned information about Network Link from the XML.
	 *
	 */
	private class LinkInfo
	{
		private String name=null;
		private int id=-1;
		private int srcPort=-1;
		private int dstPort=-1;
		private int latency=0;
		private String src=null;
		private String dst=null;
	}
	
	/**
	 * Class to save scanned information about Processor from the XML.
	 *
	 */
	private class ProcessorInfo
	{
		private String name=null;
		private String cluster=null;
		private int id=-1;
		private int speed=0;
		private int links=0;
		private int numMemory=0;		
	}
	
	/**
	 * Class to save scanned information about Cluster from the XML.
	 *
	 */
	private class ClusterInfo
	{
		public String name=null;
		public int id=-1;
		public int numMemory=0;
		public int numProcs=0;
		public int links=0;
		public int numDma=0;
		public int speed=0;
	}
	
	/**
	 * Scanned cluster nodes from XML.
	 */
	private List<ClusterInfo> clusters = new ArrayList<ClusterInfo>();
	
	/**
	 * Scanned processor nodes from XML. 
	 */
	private List<ProcessorInfo> processors = new ArrayList<ProcessorInfo>();
	
	/**
	 * Scanned network link nodes from XML.
	 */
	private List<LinkInfo> links = new ArrayList<LinkInfo>();
	
	/**
	 * Scanned memory nodes from XML. 
	 */
	private List<MemoryInfo> memory = new ArrayList<MemoryInfo>();
	
	/**
	 * Scanned DMA nodes from XML.
	 */
	private List<DmaInfo> dma = new ArrayList<DmaInfo>();
	
	/**
	 * Scanned platform properties nodes from XML. 
	 */
	private PlatformProp platformProperties = new PlatformProp();
	
	// This is for a bug in XML parsing in Java - 
	// Bug id :: 6564400
	// http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6564400
	/**
	 * Remove the white spaces from XML nodes
	 * @param e root element
	 */
	public static void removeWhitespaceNodes (Element e) 
	{
		NodeList children = e.getChildNodes ();
		for (int i = children.getLength () - 1; i >= 0; i--) 
		{
			Node child = children.item (i);
			if (child instanceof Text && ((Text) child).getData ().trim ().length () == 0)
				e.removeChild (child);
			else if (child instanceof Element) 
				removeWhitespaceNodes ((Element) child);
		}
	}
	
	/**
	 * Build the platform model from the XML file.
	 * 
	 * @param root XML root element
	 * @return Platform model built from XML file
	 */
	private Platform parsePlatformGraph (Element root) 
	{
		// Reset all the lists 
		clusters.clear();
		processors.clear();	
		links.clear();	
		memory.clear();
		dma.clear();	
		platformProperties.clear();
		
		String name=null; 
		int numClusters=0, numDmaEngines=0, numProcessors=0, numMemories=0, numLinks=0;
		
		// The main attributes of the graph.
		NamedNodeMap rootAttr = root.getAttributes ();
		for (int i=0;i<rootAttr.getLength ();i++)
		{
			Node node = rootAttr.item (i);
			if (node.getNodeName ().equals ("name"))
				name = new String (node.getNodeValue ());
			else if (node.getNodeName ().equals ("processors"))
				numProcessors = (Integer.parseInt (node.getNodeValue ()));
			else if (node.getNodeName ().equals ("clusters"))
				numClusters = (Integer.parseInt (node.getNodeValue ()));
			else if (node.getNodeName ().equals ("links"))
				numLinks = (Integer.parseInt (node.getNodeValue ()));
			else if (node.getNodeName ().equals ("dma"))
				numDmaEngines = (Integer.parseInt (node.getNodeValue ()));
			else if (node.getNodeName ().equals ("memories"))
				numMemories = (Integer.parseInt (node.getNodeValue ()));
			else
				System.out.println ("Ignored Main Platform Graph Attribute : " + node.getNodeName ());
		}
		
		// The child nodes describing everything.
		NodeList child = root.getChildNodes ();
		if (child != null && child.getLength () > 0) 
		{			
			for (int i=0;i<child.getLength ();i++)
			{
				Node node = child.item (i);
				if (node.getNodeName ().equals ("cluster"))
					processClusterNode (node);
				else if (node.getNodeName ().equals ("processor"))
					processProcessorNode (node);
				else if (node.getNodeName ().equals ("link"))
					processLinkNode (node);
				else if (node.getNodeName ().equals ("memory"))
					processMemoryNode ( node);
				else if (node.getNodeName ().equals ("dma"))
					processDmaNode ( node);
				else if (node.getNodeName ().equals ("platformProperties"))
					processPlatformProperties (node);
				else
					System.out.println ("Ignored main node " + node.getNodeName ());
			}
		}		
		
		// Formulate the platform
		Platform platform = new Platform (name, numClusters, numDmaEngines, numProcessors, numMemories, numLinks);
		
		// Build Clusters
		if(numClusters != clusters.size())
			throw new RuntimeException("Number of clusters mismatch. Expected " + numClusters + " clusters. Scanned " + clusters.size() + " clusters.");
		
		for(ClusterInfo clInfo : clusters)
		{			
			Cluster cluster = new Cluster(clInfo.name, clInfo.id, clInfo.speed, clInfo.numProcs, clInfo.links, clInfo.numMemory, clInfo.numDma);
			platform.addCluster(cluster);
		}
		
		// Build DMA Engines
		if(numDmaEngines != dma.size())
			throw new RuntimeException("Number of DMA mismatch. Expected " + numDmaEngines + " DMA. Scanned " + dma.size() + " DMA.");
		
		for(DmaInfo dmaInf : dma)
		{
			Cluster cluster = null;
			if(dmaInf.cluster != null)
				cluster = platform.getCluster(dmaInf.cluster);
			DmaEngine dmaEng = new DmaEngine(dmaInf.name, dmaInf.id, cluster);
			platform.addDmaEngine(dmaEng);
			if(dmaInf.cluster != null)
				cluster.addDma(dmaEng);
		}
		
		// Build Processors
		if(numProcessors != processors.size())
			throw new RuntimeException("Number of processors mismatch. Expected " + numProcessors + " processors. Scanned " + processors.size()+" processors.");
		for (ProcessorInfo procInfo : processors)
		{
			Cluster cluster = null;
			if(procInfo.cluster != null)
				cluster = platform.getCluster(procInfo.cluster);
			Processor processor = new Processor(procInfo.name, procInfo.id, procInfo.speed, procInfo.links, procInfo.numMemory, cluster);
			platform.addProcessor(processor);
			if(procInfo.cluster != null)
				cluster.addProcessor(processor);
		}
		
		// Build Network Links
		if(numLinks != links.size())
			throw new RuntimeException("Number of links mismatch. Expected " + numLinks + " links. Scanned " + links.size() + " links.");
		
		for (LinkInfo lnkInfo : links)
		{
			// TODO : I assume here that processors and clusters will not have same names
			
			// If it was cluster or processor, it will set it to null.
			Processor srcProcessor=platform.getProcessor(lnkInfo.src);
			Processor dstProcessor=platform.getProcessor(lnkInfo.dst); 
			Cluster srcCluster=platform.getCluster(lnkInfo.src);
			Cluster dstCluster=platform.getCluster(lnkInfo.dst);
			
			NetworkLink lnk = new NetworkLink (lnkInfo.name, lnkInfo.id, lnkInfo.srcPort, lnkInfo.dstPort, lnkInfo.latency, 
					srcProcessor, dstProcessor, srcCluster, dstCluster);
			
			// Add the network link to processor / cluster.
			if (srcProcessor != null)
				srcProcessor.addLink(lnk, lnkInfo.srcPort);
			else if (srcCluster != null)
				srcCluster.addLink(lnk, lnkInfo.srcPort);
			
			if (dstProcessor != null)
				dstProcessor.addLink(lnk, lnkInfo.dstPort);
			else if (dstCluster != null)
				dstCluster.addLink(lnk, lnkInfo.dstPort);
			
			// Add the link to the platform.
			platform.addLink(lnk);
		}
		
		// Build Memories
		if(numMemories != memory.size())
			throw new RuntimeException("Number of memories mismatch. Expected " + numMemories + " memories. Scanned " + memory.size() + " memories.");
		
		for (MemoryInfo memInfo : memory)
		{
			long sizeInBytes = memInfo.size;
			
			if(memInfo.unit.equalsIgnoreCase("b") || memInfo.unit.equalsIgnoreCase("bytes"))
				sizeInBytes *= 1;
			else if(memInfo.unit.equalsIgnoreCase("kb") || memInfo.unit.equalsIgnoreCase("kilobytes"))
				sizeInBytes *= 1024;
			else if(memInfo.unit.equalsIgnoreCase("mb") || memInfo.unit.equalsIgnoreCase("megabytes"))
				sizeInBytes *= (1024 * 1024);
			else
				throw new RuntimeException("Unit of size " + memInfo.unit + " is unknown for memory " + memInfo.name + ".");
			
			Memory mem = new Memory(memInfo.name, memInfo.id, sizeInBytes, memInfo.latency, memInfo.numClusters, memInfo.numProcessors);
			
			for(int i=0;i<memInfo.numClusters;i++)
			{
				mem.addCluster(platform.getCluster(memInfo.cluster[i]));
				platform.getCluster(memInfo.cluster[i]).addMemory(mem);
				
			}
			
			for(int i=0;i<memInfo.numProcessors;i++)
			{
				mem.addProcessor(platform.getProcessor(memInfo.proc[i]));
				platform.getProcessor(memInfo.proc[i]).addMemory(mem);
				
			}
			
			// Add memory to the platform.
			platform.addMemory(mem);
		}
		
		// Set Platform Properties
		platform.setDmaSetupTime(platformProperties.dmaSetupTime);
		
		platform.sortElements ();
		platform.validatePlatform ();
		
		return platform;
	}
	
	/**
	 * Scan platform properties node
	 * 
	 * @param node platform properties node from XML file
	 */
	private void processPlatformProperties(Node node)
	{
		NodeList childList = node.getChildNodes ();
		if (childList != null && childList.getLength () > 0) 
		{
			for(int i=0;i<childList.getLength();i++)
			{
				Node child = childList.item(i);
				if (child.getNodeName ().equals ("dma"))
				{
					String time = child.getAttributes().getNamedItem("setupTime").getNodeValue();
					platformProperties.dmaSetupTime = Integer.parseInt(time);
				}
			}
		}
	}

	/**
	 * Process DMA node of XML file.
	 * 
	 * @param root DMA node from XML file
	 */
	private void processDmaNode(Node root)
	{
		DmaInfo dmaInfo = new DmaInfo();
		NamedNodeMap nodeAttr = root.getAttributes ();
		for (int i=0;i<nodeAttr.getLength ();i++)
		{
			Node node = nodeAttr.item (i);
			if (node.getNodeName ().equals ("name"))
				dmaInfo.name = new String (node.getNodeValue ());
			else if (node.getNodeName ().equals ("id"))
				dmaInfo.id = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("cluster"))
				dmaInfo.cluster = new String (node.getNodeValue ());
			else
				System.out.println ("Ignored DMA Engine Property : " + node.getNodeName ());			
		}
		dma.add(dmaInfo);
	}

	/**
	 * Process Memory node of XML file.
	 * 
	 * @param root Memory node from XML file
	 */
	private void processMemoryNode(Node root)
	{
		MemoryInfo memInfo = new MemoryInfo();
		NamedNodeMap nodeAttr = root.getAttributes ();
		for (int i=0;i<nodeAttr.getLength ();i++)
		{
			Node node = nodeAttr.item (i);
			if (node.getNodeName ().equals ("name"))
				memInfo.name = new String (node.getNodeValue ());
			else if (node.getNodeName ().equals ("id"))
				memInfo.id = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("numProcessors"))
				memInfo.numProcessors = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("numClusters"))
				memInfo.numClusters = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("latency"))
				memInfo.latency = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("size"))
				memInfo.size = Long.parseLong (node.getNodeValue ());
			else if (node.getNodeName ().equals ("unit"))
				memInfo.unit = new String (node.getNodeValue ());
			else
				System.out.println ("Ignored Memory Property : " + node.getNodeName ());				
		}
		
		if (memInfo.numClusters > 0)
		{
			memInfo.cluster = new String[memInfo.numClusters];
			NodeList child = root.getChildNodes ();
			if (child != null && child.getLength () > 0) 
			{
				int count = 0;
				for (int i=0;i<child.getLength ();i++)
				{
					Node childNode = child.item (i);
					if (childNode.getNodeName ().equals ("cluster"))
					{
						memInfo.cluster[count++] = new String(childNode.getAttributes().getNamedItem("name").getNodeValue());
					}
				}
			}
		}
		
		if (memInfo.numProcessors > 0)
		{
			memInfo.proc = new String[memInfo.numProcessors];
			NodeList child = root.getChildNodes ();
			if (child != null && child.getLength () > 0) 
			{
				int count = 0;
				for (int i=0;i<child.getLength ();i++)
				{
					Node childNode = child.item (i);
					if (childNode.getNodeName ().equals ("processor"))
					{
						memInfo.proc[count++] = new String(childNode.getAttributes().getNamedItem("name").getNodeValue());
					}
				}
			}
		}		
		memory.add(memInfo);
	}
	
	/**
	 * Process Network Link node of XML file.
	 * 
	 * @param root Network link node from XML file
	 */
	private void processLinkNode(Node root)
	{
		LinkInfo linkInfo = new LinkInfo();
		NamedNodeMap nodeAttr = root.getAttributes ();
		for (int i=0;i<nodeAttr.getLength ();i++)
		{
			Node node = nodeAttr.item (i);
			if (node.getNodeName ().equals ("name"))
				linkInfo.name = new String (node.getNodeValue ());
			else if (node.getNodeName ().equals ("id"))
				linkInfo.id = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("src"))
				linkInfo.src = new String (node.getNodeValue ());
			else if (node.getNodeName ().equals ("dst"))
				linkInfo.dst = new String (node.getNodeValue ());
			else if (node.getNodeName ().equals ("srcPort"))
				linkInfo.srcPort = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("dstPort"))
				linkInfo.dstPort = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("latency"))
				linkInfo.latency = Integer.parseInt (node.getNodeValue ());
			else
				System.out.println ("Ignored NetworkLink Property : " + node.getNodeName ());				
		}
		links.add(linkInfo);
	}
		
	/**
	 * Process the Processor node of XML file.
	 * 
	 * @param root Processor node from XML file
	 */
	private void processProcessorNode(Node root)
	{
		ProcessorInfo procInfo = new ProcessorInfo();
		NamedNodeMap nodeAttr = root.getAttributes ();
		for (int i=0;i<nodeAttr.getLength ();i++)
		{
			Node node = nodeAttr.item (i);
			if (node.getNodeName ().equals ("name"))
				procInfo.name = new String (node.getNodeValue ());
			else if (node.getNodeName ().equals ("id"))
				procInfo.id = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("cluster"))
				procInfo.cluster = new String (node.getNodeValue ());
			else if (node.getNodeName ().equals ("memory"))
				procInfo.numMemory = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("speed"))
				procInfo.speed = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("links"))
				procInfo.links = Integer.parseInt (node.getNodeValue ());
			else
				System.out.println ("Ignored Processor Property : " + node.getNodeName ());				
		}
		processors.add(procInfo);
	}
	
	

	/**
	 * Process the Cluster node of XML file.
	 * 
	 * @param root Cluster node from XML file
	 */
	private void processClusterNode(Node root)
	{
		ClusterInfo clusterInfo = new ClusterInfo();
		NamedNodeMap nodeAttr = root.getAttributes ();
		for (int i=0;i<nodeAttr.getLength ();i++)
		{
			Node node = nodeAttr.item (i);
			if (node.getNodeName ().equals ("name"))
				clusterInfo.name = new String (node.getNodeValue ());
			else if (node.getNodeName ().equals ("procsInCluster"))
				clusterInfo.numProcs = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("id"))
				clusterInfo.id = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("memory"))
				clusterInfo.numMemory = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("dma"))
				clusterInfo.numDma = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("speed"))
				clusterInfo.speed = Integer.parseInt (node.getNodeValue ());
			else if (node.getNodeName ().equals ("links"))
				clusterInfo.links = Integer.parseInt (node.getNodeValue ());
			else
				System.out.println ("Ignored Cluster Property : " + node.getNodeName ());
		}
		clusters.add(clusterInfo);
	}

	/**
	 * Parse the platform XML file and build platform model.
	 * 
	 * @param fileName XML file of the platform to be scanned
	 * @return platform model
	 */
	public Platform parsePlatformXml (String fileName)
	{
		Platform platform=null;
		//get the factory
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance ();
		
		try 
		{
			//Using factory get an instance of document builder
			DocumentBuilder db = dbf.newDocumentBuilder ();
			// db.setErrorHandler (myErrorHandler);
			
			//parse using builder to get DOM representation of the XML file
			Document dom = db.parse (fileName);

			//get the root element
			Element docEle = dom.getDocumentElement ();

			//get a nodelist of  elements
			NodeList nl = docEle.getElementsByTagName ("architectureGraph");
			if (nl.getLength () != 1 )
				throw new RuntimeException ("Number of platform graphs present in this xml is " + nl.getLength () + ". Expecting 1.");
			
			if (nl != null && nl.getLength () > 0) 
			{
				// for (int i = 0 ; i < nl.getLength ();i++)
				{
					// get the element					
					Element el = (Element)nl.item (0);
					removeWhitespaceNodes (el);
					platform = parsePlatformGraph (el);
				}
			}
		}catch (ParserConfigurationException pce) {
			pce.printStackTrace ();
		}catch (SAXException se) {
			se.printStackTrace ();
		}catch (IOException ioe) {
			ioe.printStackTrace ();
		}
		
		return platform;
	}
}
