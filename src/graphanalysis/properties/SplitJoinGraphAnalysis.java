package graphanalysis.properties;


import java.util.*;
import spdfcore.*;
//import spdfcore.Channel.Link;
import spdfcore.stanalys.*;

/**
 * Different methods to check certain properties of a Split-join Graph.
 * 
 * @author Pranav Tendulkar
 *
 */
public class SplitJoinGraphAnalysis extends GraphAnalysisSdfAndHsdf
{
	/**
	 * Type of edge
	 */
	public enum EdgeType {SPLIT, JOIN, NEUTRAL};
	
	/**
	 * Initialize split-join analysis object.
	 * 
	 * @param graph input split-join graph
	 * @param solutions solutions of graph
	 * @param hsdf equivalent HSDF
	 */
	public SplitJoinGraphAnalysis (Graph graph, Solutions solutions, Graph hsdf)
	{
		super (graph, solutions, hsdf);
	}
	
	/**
	 * Initialize split-join analysis object.
	 *  
	 * @param graph input split-join graph
	 * @param solutions solutions of graph
	 */
	public SplitJoinGraphAnalysis (Graph graph, Solutions solutions)
	{
		super (graph, solutions);
	}	
	
	/**
	 * Get type of edge 
	 * 
	 * @param chnnl channel of split-join graph 
	 * @return Split, join or neutral depending on alpha
	 */
	public EdgeType getEdgeType (Channel chnnl)
	{
		Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
		Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
		int srcRepCnt = solutions.getSolution (srcActor).returnNumber ();
		int dstRepCnt = solutions.getSolution (dstActor).returnNumber ();
		
		if (srcRepCnt == dstRepCnt)
			return EdgeType.NEUTRAL;
		else if (srcRepCnt < dstRepCnt)
			return EdgeType.SPLIT;
		else
			return EdgeType.JOIN;		
	}
	
	// 
	/**
	 * Get  Alpha for Split Edges or 1/alpha for Join Edges.
	 * 
	 * @param chnnl Channel of split-join graph
	 * @return alpha for split edge or 1/alpha for join edge
	 */
	public int getAlpha (Channel chnnl)
	{
		Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
		Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
		int srcRepCnt = solutions.getSolution (srcActor).returnNumber ();
		int dstRepCnt = solutions.getSolution (dstActor).returnNumber ();
		
		if (srcRepCnt > dstRepCnt)
			return (srcRepCnt / dstRepCnt);
		else if (srcRepCnt < dstRepCnt)
			return (dstRepCnt / srcRepCnt);
		else
			return 1;
	}
	

	
	/**
	 * Get an actor with no predecessors
	 * 
	 * @return an actor with no predecessors
	 */
	public Actor getStartActor ()
	{		
		List<Actor> startActors = findSdfStartActors ();
		if (startActors.size () != 1)
			throw new RuntimeException ("Expected one start actor, but found : "+ startActors.size ());
		Actor startActr = startActors.get (0);
		return startActr;
		
//		List<Actor> startActrList = new ArrayList<Actor>();
//		Iterator<Actor> actrIter = graph.getActors ();
//		while (actrIter.hasNext ())
//		{
//			Actor actr = actrIter.next ();
//			if (actr.numIncomingLinks () == 0)
//				startActrList.add (actr);			
//		}
//		
//		if (startActrList.size () != 1)
//			throw new RuntimeException ("We were expecting only one start actor. but found " + startActrList.size ());
		
	}
	
	/**
	 * Get an actor with no successors.
	 * 
	 * @return Actor with no successors
	 */
	public Actor getEndActor ()
	{
//		List<Actor> endActrList = new ArrayList<Actor>();
//		Iterator<Actor> actrIter = graph.getActors ();
//		while (actrIter.hasNext ())
//		{
//			Actor actr = actrIter.next ();
//			if (actr.numOutgoingLinks () == 0)
//				endActrList.add (actr);			
//		}
//		
//		if (endActrList.size () != 1)
//			throw new RuntimeException ("We were expecting only one end actor. but found " + endActrList.size ());
//		return endActrList.get (0);
		
		List<Actor> endActors = findSdfEndActors ();
		if (endActors.size () != 1)
			throw new RuntimeException ("Expected one end actor, but found : "+ endActors.size ());
		
		
		Actor endActr = endActors.get (0);
		return endActr;
	}
	
	
	/**************************************************************************************
	 * 
	 *  The methods below are working, but are un-used. So I just preferred to comment them.
	 * 
	 **************************************************************************************/
//	/**
//	 * @param actr
//	 * @return
//	 */
//	public boolean hasCousins (Actor actr)
//	{
//		int repCount = solutions.getSolution (actr).returnNumber ();
//		
//		if (repCount == 1)
//			return false;
//		else
//		{
//			for(Link lnk : actr.getLinks (Port.DIR.IN))			
//			{
//				if (getAlpha (lnk.getChannel ()) != repCount)
//					return true;
//			}
//		}
//		
//		return false;
//	}
//	
//	/**
//	 * @param path
//	 * @return
//	 */
//	public List<String> getSignature (List<Actor> path)
//	{
//		List<String> signature = new ArrayList<String>();
//		for (int i=1; i < path.size (); i++)
//		{
//			int consumerRepCnt = solutions.getSolution (path.get (i)).returnNumber ();
//			int producerRepCnt = solutions.getSolution (path.get (i-1)).returnNumber ();
//			if (consumerRepCnt >= producerRepCnt)
//				signature.add (Integer.toString (consumerRepCnt / producerRepCnt));
//			else
//			{
//				if (producerRepCnt / consumerRepCnt >= 1)
//				{
//					// We now try to remove the common factor in the integer division.
//					if ((producerRepCnt % consumerRepCnt) != 0)
//						throw new RuntimeException (" The producer repetition count should be multiple of that of the consumer.");					
//					producerRepCnt /= consumerRepCnt;
//					consumerRepCnt = 1;
//				}				
//				signature.add (consumerRepCnt  + "/" + producerRepCnt);
//			}
//		}
//		
//		return signature;
//	}
//	
//	/**
//	 * @param signature
//	 * @return
//	 */
//	public double getMultiplicity (List<String> signature)
//	{
//		double multiplicity = 1.0;
//		
//		for (String factor : signature)
//		{
//			if (factor.contains ("/"))
//			{
//				int numerator = Integer.parseInt (factor.substring (0, factor.indexOf ("/")));
//				int denominator = Integer.parseInt (factor.substring (factor.indexOf ("/")+1, factor.length ()));
//				multiplicity *= (double) numerator/denominator;
//			}
//			else
//				multiplicity *= Integer.parseInt (factor);
//		}
//		
//		return multiplicity;		
//	}
	
//	/**
//	 * @param signature
//	 * @return
//	 */
//	public boolean checkPathNesting (List<String> signature)
//	{
//		// Check for proper nesting of the path.
//		Stack<Integer> stack = new Stack<Integer>();
//		for (int i=0;i<signature.size ();i++)
//		{
//			String factor = signature.get (i);
//			
//			if (factor.contains ("/"))
//			{
//				int numerator = Integer.parseInt (factor.substring (0, factor.indexOf ("/")));
//				int denominator = Integer.parseInt (factor.substring (factor.indexOf ("/")+1, factor.length ()));
//				int stackValue = stack.peek ();
//				
//				if (numerator != 1)
//					throw new RuntimeException ("We don't yet support non-unity numerator joins");
//				
//				if (stackValue != denominator)
//				{
//					if (stackValue < denominator)
//					{
//						// Here we are trying to support a signature like 2 -- 2 -- 1/4
//						while (denominator > 1)
//						{
//							stackValue = stack.pop ();
//							if ((denominator % stackValue) != 0)
//							{
//								System.out.println ("Split-Join not in multiples");
//								return false;
//							}
//							denominator /= stackValue;
//						}
//					}
//					else
//					{
//						// Here we are trying to support a signature like 4 -- 1/2 -- 1/2
//						while (stackValue > 1)
//						{
//							factor = signature.get (i++);
//							numerator = Integer.parseInt (factor.substring (0, factor.indexOf ("/")));
//							denominator = Integer.parseInt (factor.substring (factor.indexOf ("/")+1, factor.length ()));
//							
//							if (numerator != 1)
//								throw new RuntimeException ("We don't yet support non-unity numerator joins");
//							
//							if ((stackValue % denominator) != 0)
//							{
//								System.out.println ("Split-Join not in multiples..");
//								return false;
//							}
//							stackValue  /= denominator;
//						}						
//					}
//				}
//				else
//					stack.pop ();
//			}
//			else
//			{
//				int splitValue = Integer.parseInt (factor);
//				if (splitValue != 1)
//					stack.push (splitValue);
//			}
//		}
//		
//		if (stack.isEmpty () == false)
//		{
//			System.out.println ("Unbalance Signatures : ");
//			while (stack.isEmpty () == false)
//				System.out.print (stack.pop () + " --");
//			System.out.println ();
//			return false;
//		}
//		
//		return true;
//	}	
	
//	/**
//	 * @return
//	 */
//	public boolean validateGraph ()
//	{		
//		Actor startActr = getStartActor ();
//		Actor endActr = getEndActor ();
//		
//		List<List<Actor>> paths = getPath (startActr, endActr);
//		for (List<Actor> path : paths)
//		{
//			// All paths should have multiplicity equal to 1.
//			List<String> signature = getSignature (path);
//			if (getMultiplicity (signature) != 1.0)
//			{
//				System.out.println ("Invalid Split-Join Graph Path with multiplicity not 1");
//				System.out.print ("Path : " + path.get (0).getName ());
//				for (int i=1; i < path.size (); i++)
//					System.out.print (" --> " + path.get (i).getName ());
//				System.out.println ();
//				System.out.print ("Signature : " + signature.get (0));
//				for (int i=1; i < signature.size (); i++)
//					System.out.print (" -- " + signature.get (i));
//				System.out.println ("\nMultiplicity : " + getMultiplicity (signature));
//				return false;
//			}
//			
//			if (checkPathNesting (signature) == false)
//				return false;
//		}		
//		
//		return true;
//	}
//	
//	/**
//	 * @param hsdfActor1
//	 * @param hsdfActor2
//	 * @return
//	 */
//	public boolean areBrothers (Actor hsdfActor1, Actor hsdfActor2)
//	{
//		int instanceId1 = getInstanceId (hsdfActor1);
//		int instanceId2 = getInstanceId (hsdfActor2);
//		
//		String name1 = hsdfActor1.getName ().substring (0, hsdfActor1.getName ().indexOf ("_"));
//		if (hsdfActor2.getName ().startsWith (name1) == false)
//			throw new RuntimeException ("Only instances of same actors are allowed to call this function.");
//		
//		Actor sdfActor = getHsdfToSdfActor (hsdfActor1);
//		
//		
//		List<List<Actor>> paths = getPath (getStartActor (), sdfActor);
//		List<Actor>path0 = paths.get (0);
//		
//		// Find correct alpha.
//		int alpha = 1;
//		for (int i=path0.size ()-1;i>0;i--)
//		{
//			// We believe that there should be only one channel here
//			HashSet<Channel> chnnlSet = graph.getChannel (path0.get (i-1), path0.get (i));
//			for (Channel chnnl : chnnlSet)
//			{
//				alpha = getAlpha (chnnl);
//				if (alpha == 1)
//					continue;
//			}						
//		}			
//			
//		if ((instanceId1 / alpha) == (instanceId2 / alpha))
//			return true;
//		else
//			return false;
//	}
//	
//	/**
//	 * 
//	 * TODO : I haven't considered split-join graphs with signature like this 2 - 2- 1/4
//	 * 
//	 * @return
//	 */
//	public HashMap<Integer, HashSet<Actor>> getActorInLevels ()
//	{		
//		HashMap<Integer, HashSet<Actor>> levels = new HashMap<Integer, HashSet<Actor>>();
//		
//		Actor startActr = getStartActor ();
//		Actor endActr = getEndActor ();
//		
//		List<List<Actor>> paths = getPath (startActr, endActr);
//		for (List<Actor> path : paths)
//		{
//			// All paths should have multiplicity equal to 1.
//			List<String> pathSignature = getSignature (path);
//			int currentLevel = 0;
//			
//			if (levels.containsKey (currentLevel) == false)
//				levels.put (currentLevel, new HashSet<Actor>());
//			levels.get (currentLevel).add (path.get (currentLevel));
//			
//			for (int i=0;i<pathSignature.size ();i++)
//			{
//				String sign = pathSignature.get (i);
//				if (sign.contains ("/"))
//				{
//					// Join Edge.
//					currentLevel--;
//				}
//				else
//				{
//					if (Integer.parseInt (sign) == 1)
//					{
//						// Neutral Edge.
//					}
//					else
//					{
//						// Split Edge.
//						currentLevel++;
//					}
//				}
//				
//				if (levels.containsKey (currentLevel) == false)
//					levels.put (currentLevel, new HashSet<Actor>());
//				levels.get (currentLevel).add (path.get (i+1));				
//			}			
//		}
//		
//		return levels;
//	}
}
