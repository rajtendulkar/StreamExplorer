package graphanalysis;

import graphanalysis.properties.GraphAnalysisSdfAndHsdf;

import java.util.*;

import spdfcore.*;
import spdfcore.Channel.Link;
import spdfcore.stanalys.*;

/**
 * Calculate maximum and minimum bounds on the graph.
 * 
 * TODO: Check where all this class is used and if it produces correct results.
 * 
 * @author Pranav Tendulkar
 *
 */
public class CalculateBounds 
{
	/**
	 * Graph to be analysed 
	 */
	private Graph graph;
	
	/**
	 *  HSDF of the same graph.
	 */
	private Graph hsdf;
	
	/**
	 * Solutions of the graph containing repetition count. 
	 */
	private Solutions solutions;
	
	// We store some values here so that we don't have to re-calculate again and again.
	/**
	 * Maximum Number of processors to be used for a design space exploration.
	 */
	private int maxProcessors;
	
	/**
	 *  Minimum Period to be used for a design space exploration.
	 */
	int minPeriod;
	
	/**
	 * Minimum Latency to be used for a design space exploration.
	 */
	private int minLatency;
	
	/**
	 * Maximum Latency to be used for a design space exploration. 
	 */
	private int maxLatency;
	
	/**
	 * Minimum Buffer Size to be used for a design space exploration.
	 */
	private int minBufferSize;
	
	/**
	 * Maximum Latency to be used for a design space exploration. 
	 */
	private int maxBufferSize;
	
	/**
	 * Initialize the bounds calculation object.
	 * 
	 * @param graph input graph to be analyzed
	 * @param hsdf equivalent HSDF graph
	 * @param solutions solutions of the input graph 
	 */
	public CalculateBounds (Graph graph, Graph hsdf, Solutions solutions)
	{
		this.graph = graph;
		this.hsdf = hsdf;
		this.solutions = solutions;
		// Initialize all to zero.
		maxProcessors = 0;
		minLatency = 0;
		maxLatency = 0;
		minPeriod = 0;
		minBufferSize = 0;
		maxBufferSize = 0;		
	}
	
	/**
	 * Initialize the bounds calculation object.
	 * HSDF will be calculated internally.
	 * 
	 * @param graph input graph to be analyzed
	 * @param solutions solutions of the input graph 
	 */
	public CalculateBounds (Graph graph, Solutions solutions)
	{
		this(graph, null, solutions);		
		TransformSDFtoHSDF toHSDF = new TransformSDFtoHSDF ();
		hsdf = toHSDF.convertSDFtoHSDFWithUniqueChannels (graph);		
	}
	
	/**
	 * Find minimum workload for exploration.
	 * It is the smallest execution time for an actor in the graph.
	 * 
	 * @return minimum workload in the graph
	 */
	public int findMinWorkLoad ()
	{
		int minWorkLoad = Integer.MAX_VALUE;

		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			if (minWorkLoad > actr.getExecTime ())
				minWorkLoad = actr.getExecTime ();
		}

		return minWorkLoad;
	}
	
	/**
	 * Communication cost for a channel is calculated as 
	 * (source port rate * token size ). This method will
	 * return a minimum over all the channels. 
	 * 
	 * @return minimum communication cost
	 */
	public int findMinCommunicationCost ()
	{
		int minCommCost = Integer.MAX_VALUE;
		Iterator<Channel> chnnlList = hsdf.getChannels ();
		while (chnnlList.hasNext ())
		{
			Channel chnnl = chnnlList.next ();
			int chnnlCost = (Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ()) * chnnl.getTokenSize ());
			if (minCommCost > chnnlCost)
				minCommCost = chnnlCost;
		}
		
		return minCommCost;
	}
	
	/**
	 * Communication cost for a channel is calculated as 
	 * (source port rate * token size ). This method will
	 * return a sum over all the channels.
	 * 
	 * @return maximum communication cost
	 */
	public int findMaxCommunicationCost ()
	{
		int communicationCost = 0;
		Iterator<Channel> chnnlList = hsdf.getChannels ();
		while (chnnlList.hasNext ())
		{
			Channel chnnl = chnnlList.next ();
			communicationCost += (Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ()) * chnnl.getTokenSize ());
		}

		return communicationCost;
	}
	
	// Maximum Workload imbalance can occur only when only one cluster gets
	// all the work, while remaining are empty.
	/**
	 * Total workload will be equal to sum of execution times
	 * of all the actors in the graph.
	 * 
	 * @return total workload in the graph
	 */
	public int findTotalWorkLoad ()
	{
		int maxWorkLoad = 0;

		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			maxWorkLoad += (actr.getExecTime () * solutions.getSolution (actr).returnNumber ());
		}

		return maxWorkLoad;
	}	
	
	// A Crude Upper Limit
	/**
	 * A loose bound on maximum number of processors that can be used for scheduling.
	 * 
	 * @return maximum number of processor this graph can use
	 */
	public int findMaxProcessors () 
	{
		if (maxProcessors == 0)
		{
			Iterator<Actor> actrList = graph.getActors ();
			while (actrList.hasNext ())
			{
				Actor actr = actrList.next ();
				int thisActorProc = solutions.getSolution (actr).returnNumber ();
	
				// If this actor has a self-loop then, it cannot run in parallel. add just one processor for it.
				for(Channel chnnl : actr.getChannels (Port.DIR.IN))
				{				
					if ((chnnl.getLink (Port.DIR.OUT).getActor () == chnnl.getLink (Port.DIR.IN).getActor ()) )
					{
						thisActorProc = 1;
						break;
					}
				}			
				maxProcessors += thisActorProc;			
			}	
		}
		return maxProcessors;
	}

	// A Crude Upper Limit in Bytes now !!!
	/**
	 * Maximum buffer size for an application in terms of bytes.
	 * 
	 * @return Maximum buffer size in bytes.
	 */
	public int findMaxBufferSizeInBytes () 
	{
		if (maxBufferSize == 0)
		{			
			Iterator<Channel> chnnlList = graph.getChannels ();
			while (chnnlList.hasNext ())
			{
				Channel chnnl = chnnlList.next ();
				int initialTokens = chnnl.getInitialTokens ();
	
//				if (chnnl.getLink (Port.DIR.OUT).getActor () == chnnl.getLink (Port.DIR.IN).getActor ())
//					maxBufferSize += initialTokens * chnnl.getTokenSize();
//				else
				{
					Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
					maxBufferSize += (chnnl.getTokenSize() * (initialTokens + 
								(solutions.getSolution (srcActor).returnNumber () * 
										Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ()))));
				}
			}			
		}
		
		return maxBufferSize;
	}
	
	/**
	 * Maximum buffer size for an application in terms of tokens.
	 * 
	 * @return Maximum buffer size in tokens.
	 */
	public int findMaxBufferSizeInTokens () 
	{
		if (maxBufferSize == 0)
		{			
			Iterator<Channel> chnnlList = graph.getChannels ();
			while (chnnlList.hasNext ())
			{
				Channel chnnl = chnnlList.next ();
				int initialTokens = chnnl.getInitialTokens ();
	
//				if (chnnl.getLink (Port.DIR.OUT).getActor () == chnnl.getLink (Port.DIR.IN).getActor ())
//					maxBufferSize += initialTokens * chnnl.getTokenSize();
//				else
				{
					Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
					maxBufferSize += (chnnl.getTokenSize() * (initialTokens + 
								(solutions.getSolution (srcActor).returnNumber () * 
										Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ()))));
				}
			}			
		}
		
		return maxBufferSize;
	}
	
//	public int findMinBufferInBytes()
//	{
//		int minBuffer = Integer.MAX_VALUE;
//		Iterator<Channel> chnnlList = graph.getChannels ();
//		while (chnnlList.hasNext ())
//		{
//			Channel chnnl = chnnlList.next ();
//			int initialTokens = chnnl.getInitialTokens ();
//			
//			int prodRate = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
//			int consRate = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());
//			Expression prodRateExpr = new Expression (Integer.toString (prodRate));
//			Expression consRateExpr = new Expression (Integer.toString (consRate));
//			Expression gcdExpr = Expression.gcd (prodRateExpr, consRateExpr);
//			int gcd = Integer.parseInt (gcdExpr.toString ());
//			
//			int minBufferSize = ((prodRate + consRate - gcd + (initialTokens % gcd))*chnnl.getTokenSize());
//			if (minBufferSize < minBuffer)
//				minBuffer = minBufferSize;
//		}
//		
//		return minBuffer;
//	}
	
	/**
	 * Minimum buffer size required for execution of application in terms of bytes.
	 * The formula is taken from Bhattacharya book on software synthesis.
	 * 
	 * @return Minimum buffer size in bytes.
	 */
	public int findMinBufferSizeInBytes () 
	{
		if (minBufferSize == 0)
		{			
			Iterator<Channel> chnnlList = graph.getChannels ();
			while (chnnlList.hasNext ())
			{
				Channel chnnl = chnnlList.next ();
				int initialTokens = chnnl.getInitialTokens ();
	
//				if (chnnl.getLink (Port.DIR.OUT).getActor () == chnnl.getLink (Port.DIR.IN).getActor ())
//					minBufferSize += initialTokens * chnnl.getTokenSize();
//				else
				{
					// pAB + cAB - gcd (pAB, cAB) +  (iniAB mod  gcd (pAB, cAB))
	
					int prodRate = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
					int consRate = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());
	
					Expression prodRateExpr = new Expression (Integer.toString (prodRate));
					Expression consRateExpr = new Expression (Integer.toString (consRate));
					Expression gcdExpr = Expression.gcd (prodRateExpr, consRateExpr);
					int gcd = Integer.parseInt (gcdExpr.toString ());
	
					minBufferSize += ((prodRate + consRate - gcd + (initialTokens % gcd))*chnnl.getTokenSize());
	
					// System.out.println ("Prod Rate : " + prodRate + " cons Rate : " + consRate + " GCD : " + gcd);				
	
					// This is the old buffer constraint. we now have tighter lower bound.
					// if ((Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ())) > initialTokens)
					//	buffSize += Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
					// else
					//	buffSize += initialTokens;
				}
			}
		}
		return minBufferSize;
	}

	/**
	 * Minimum buffer size required for execution of application in terms of tokens.
	 * 
	 * @return Minimum buffer size in tokens.
	 */
	public int findMinBufferSizeInTokens () 
	{
		if (minBufferSize == 0)
		{			
			Iterator<Channel> chnnlList = graph.getChannels ();
			while (chnnlList.hasNext ())
			{
				Channel chnnl = chnnlList.next ();
				int initialTokens = chnnl.getInitialTokens ();
	
//				if (chnnl.getLink (Port.DIR.OUT).getActor () == chnnl.getLink (Port.DIR.IN).getActor ())
//					minBufferSize += initialTokens;
//				else
				{
					// pAB + cAB - gcd (pAB, cAB) +  (iniAB mod  gcd (pAB, cAB))
	
					int prodRate = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
					int consRate = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ());
	
					Expression prodRateExpr = new Expression (Integer.toString (prodRate));
					Expression consRateExpr = new Expression (Integer.toString (consRate));
					Expression gcdExpr = Expression.gcd (prodRateExpr, consRateExpr);
					int gcd = Integer.parseInt (gcdExpr.toString ());
	
					minBufferSize += (prodRate + consRate - gcd + (initialTokens % gcd));
	
					// System.out.println ("Prod Rate : " + prodRate + " cons Rate : " + consRate + " GCD : " + gcd);				
	
					// This is the old buffer constraint. we now have tighter lower bound.
					// if ((Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ())) > initialTokens)
					//	buffSize += Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ());
					// else
					//	buffSize += initialTokens;
				}
			}
		}
		return minBufferSize;
	}	


	
	/**
	 * Minimum Period for the exploration of the graph.
	 * 
	 * @return minimum period of the graph for exploration
	 */
	public int findGraphMinPeriod ()
	{
		// Min Period equals to min. graph latency.
		if (minPeriod == 0)
		{			
			Iterator<Actor> actrList = graph.getActors ();
			while (actrList.hasNext ())
			{
				Actor actr = actrList.next ();
				if (actr.getExecTime () > minPeriod)
					minPeriod = actr.getExecTime ();
			}			
		}
			
		return minPeriod;
	}
	
	/**
	 * Maximum period for the period exploration of the graph.
	 * It can be infinite, but then we can use non-pipelined
	 * scheduling instead of pipelined.
	 * 
	 * @return maximum period of the graph
	 */
	public int findGraphMaxPeriod ()
	{
		// Max Period equals to max. graph latency. It can repeat periodically on single processor.
		if (maxLatency == 0)
			return findGraphMaxLatency ();
		else
			return maxLatency;
	}	

	/**
	 * Finds minimum latency of the graph
	 * 
	 * @return minimum latency of the graph for exploration 
	 */
	public int findGraphMinLatency () 
	{
		if (minLatency == 0)
		{
			GraphAnalysisSdfAndHsdf analysis = new GraphAnalysisSdfAndHsdf (graph, solutions, hsdf);
			List<Actor> startActors = analysis.findHsdfStartActors ();
			List<Actor> lastActors = analysis.findHsdfEndActors ();
	
			// Now let us remove the channels with initial Tokens.
			List<Channel> chnnlList = new ArrayList<Channel>();
			Iterator<Actor> actrList = hsdf.getActors ();
			while (actrList.hasNext ())
			{
				Actor actr = actrList.next ();
				for (Link lnk : actr.getLinks (Port.DIR.IN))
				{				
					if (lnk.getChannel ().getInitialTokens () >= Integer.parseInt (lnk.getPort ().getRate ()))
					{
						// We have to remove the channel.
						chnnlList.add (lnk.getChannel ());					
					}
				}
			}
	
			for (int i=0;i<chnnlList.size ();i++)
				hsdf.remove (chnnlList.get (i));			
	
			if ((lastActors.size ()) != 0 && (startActors.size () != 0))
			{
				HashMap<Channel, String> edgQty = new HashMap<Channel, String>();
				Iterator<Channel> chnnlIter = hsdf.getChannels ();
	
				while (chnnlIter.hasNext ())
				{					
					Channel chnnl = chnnlIter.next ();
					edgQty.put (chnnl, Integer.toString (chnnl.getLink (Port.DIR.OUT).getActor ().getExecTime ()));
				}
	
				BellmanFord bmFrd = new BellmanFord (hsdf, edgQty);
	
				for (int i=0;i<startActors.size ();i++)
				{
					for (int j=0;j<lastActors.size ();j++)
					{
						Actor srcActor = hsdf.getActor (startActors.get (i).getName ());
						Actor dstActor = hsdf.getActor (lastActors.get (j).getName ());
	
						Stack<Actor> longestPath = bmFrd.searchPath (srcActor, dstActor, true);
	
						if (longestPath != null)
						{					
							int pathLatency = 0;
	
							for (int k=0;k<longestPath.size ();k++)
								pathLatency += longestPath.get (k).getExecTime ();
	
							if (minLatency < pathLatency)
								minLatency = pathLatency;
						}
					}
				}
			}
	
			// This is the old crude way. If the above method fails, then 
			// we should use the old way to find the min. latency.
			if (minLatency == 0)
			{
				actrList = graph.getActors ();
				while (actrList.hasNext ())
				{
					Actor actr = actrList.next ();
					if (minLatency < actr.getExecTime ())
						minLatency = actr.getExecTime ();			
				}			
			}
		}
		
		return minLatency;
	}
	
	/**
	 * Maximum latency of the graph for scheduling. This is
	 * equal to sum of (execution times * repetition count) for
	 * all the actors.
	 * 
	 * @return Maximum latency of the graph
	 */
	public int findGraphMaxLatency () 
	{		
		if (maxLatency == 0)
		{
			Iterator<Actor> actrList = graph.getActors ();
			while (actrList.hasNext ())
			{
				Actor actr = actrList.next ();
				maxLatency += (actr.getExecTime () * solutions.getSolution (actr).returnNumber ());
			}
		}
		return maxLatency;
	}
}
