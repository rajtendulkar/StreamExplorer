package underDevelopment;
/**
 * 
 */

import spdfcore.*;
import spdfcore.Channel.Link;
import spdfcore.stanalys.*;
import graphanalysis.BellmanFord;

import java.util.*;

/**
 *  Modification chart
 */
public class QuasiStaticScheduling 
{

	////////////////////////////////////	
	/////////// data ///////////////////
	////////////////////////////////////	
	private Graph graph;	
	private ParamComm paramcomm;
	private Solutions solutions;
	private Graph multiplicityGraph = new Graph ();
	private HashMap<Channel, String> edgeQuantities = new HashMap<Channel, String>();
	private int portCount = 0;
	private HashMap<Actor, Level> levelMap= new HashMap<Actor, Level>();
	// Contains a lookup for actor in multiplicity graph to one or more actors in original graph.
	private HashMap<Actor, HashSet<Actor>> actorMap= new HashMap<Actor, HashSet<Actor>>();
	
	public QuasiStaticScheduling (Graph graph, ParamComm paramcomm, Solutions solutions) 
	{
		this.graph = graph;		
		this.paramcomm = paramcomm;		
		this.solutions = solutions;
	}
	
	private void generateMultiplicityGraph ()
	{
		// First add all actors to multiplicity graph.
		Iterator<Actor> actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor graphActor = actorIter.next ();
			Actor actr = new Actor ();
			actr.setName (graphActor.getName ());
			actr.setFunc (graphActor.getFunc ());
			multiplicityGraph.add (actr);
			
			// Add cross-reference to actorMap.
			HashSet<Actor> hashSet = new HashSet<Actor>();
			hashSet.add (graphActor);
			actorMap.put (actr, hashSet);
		}
		
		// Build the channels.
		int portCount = 0;
		actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			// This is X.
			Actor actorX = actorIter.next ();
			for(Link lnk : actorX.getAllLinks ())
			{
				// This is Y.
				Actor actorY = lnk.getOpposite ().getActor ();
				if (paramcomm.isPeriodActor (actorY))
				{
					// Only if Y is a period actor.
					// if gcd ( #Y, #X) = #Y
					Expression solX = solutions.getSolution (actorX);
					Expression solY = solutions.getSolution (actorY);
					
					//System.out.println ("actorX : " + actorX.getName () + " actorY : " + actorY.getName () + 
					//		" solX : " + solX.toString () + " solY : " + solY.toString () + " GCD : " + 
					//		Expression.gcd (solX, solY).toString ());
					
					if (Expression.gcd (solX, solY).toString ().equals (solY.toString ()))
					{
						// Now we have to add an edge from X to Y.
						//System.out.println ("Now we are adding edge!!");
						
						Port pout  = new Port (Port.DIR.OUT);
				        pout.setFunc (actorX.getFunc ());
				        pout.setName ("p"+Integer.toString (portCount++));				        
				        multiplicityGraph.add (pout);
				        
				        Port pin  = new Port (Port.DIR.IN);
				        pin.setFunc (actorY.getFunc ());
				        pin.setName ("p"+Integer.toString (portCount++));				        
				        multiplicityGraph.add (pin);
				        
				        PortRef src = new PortRef ();
				        src.setActor (actorX);
				        src.setPort (pout);				        
				        
				        PortRef snk = new PortRef ();
				        snk.setActor (actorY);
				        snk.setPort (pin);
				        
				        Channel chnnl = new Channel ();
				        multiplicityGraph.add (chnnl);
				        
				        chnnl.bind (src, snk);
				        
				        edgeQuantities.put (chnnl, solX.toString ());
					}
				}			
			}			
		}
	}
		
	private void mergeActorsWithEqualSolutions ()
	{
		Iterator<Actor> actorIter =  multiplicityGraph.getActors ();
				
		while (actorIter.hasNext ())
		{			
			Actor actor = actorIter.next ();
			if (paramcomm.isPeriodActor (actor))
			{
				// Again considering only outgoing channels, so as not to have same channels twice.
				for(Link lnk : actor.getLinks (Port.DIR.OUT)) // .getAllOutgoingLinks ();
				{				
					Actor oppositeActor = lnk.getOpposite ().getActor ();
					if (paramcomm.isPeriodActor (oppositeActor) == true &&
								(solutions.getSolution (actor).equals (solutions.getSolution (oppositeActor))))
					{
						System.out.println ("Merging Actor : " + actor.getName () + " and : " + oppositeActor.getName ());
						// Merge the Actors.
						collapseChannelInGraph (lnk.getChannel ());
						
						// Remove the edge Quantities
						edgeQuantities.remove (lnk.getChannel ());
						
						// Update Actor Iterator.
						actorIter =  multiplicityGraph.getActors ();
					}					
				}
			}			
		}		
	}
	
	private void createNewLink (Graph graph, Actor oppositeActor, Port oppositePort, 
			 Actor newActor, Channel oldChannel)
	{		
		// Add new Port to the graph. 
		Port.DIR direction = oppositePort.getDir ().getOpposite ();
		Port p = new Port (direction);
		p.setName ("p"+Integer.toString (portCount));		
		p.setFunc (newActor.getFunc ());		
		multiplicityGraph.add (p);

		// Add new PortRef to the graph
		PortRef portRefSrc = new PortRef ();
		portRefSrc.setActor (newActor);
		portRefSrc.setPort (p);

		PortRef portRefSnk = new PortRef ();
		portRefSnk.setActor (oppositeActor);
		portRefSnk.setPort (oppositePort);

		// Add new Channel to the Graph
		Channel chnnl = new Channel ();
		multiplicityGraph.add (chnnl);

		if (direction  == Port.DIR.OUT)
			chnnl.bind (portRefSrc,portRefSnk);
		else
			chnnl.bind (portRefSnk,portRefSrc);
		
		// Update the Edge Quantity references.
		String edgeQuantity = edgeQuantities.get (oldChannel);
		edgeQuantities.remove (oldChannel);
		edgeQuantities.put (chnnl, edgeQuantity);

		portCount++;
	}

	private void collapseChannelInGraph (Channel slctChannel) 
	{
		Actor inActor, outActor, newActor;
		Graph graph = slctChannel.getGraph ();
		portCount=0;
		
		//System.out.println ("Collapsing Channel");

		// Actor which has incoming edge.
		inActor = slctChannel.getLink (Port.DIR.IN).getActor ();
		// Actor which has outgoing edge.
		outActor = slctChannel.getLink (Port.DIR.OUT).getActor ();
		// New Actor which is going to be inserted in the graph
		newActor = new Actor ();
		newActor.setFunc (inActor.getFunc () +"::"+ outActor.getFunc ());
		newActor.setName (inActor.getName () +"::"+ outActor.getName ());
		multiplicityGraph.add (newActor);
		
		// Update the Actor Map.
		HashSet<Actor> hashSet = new HashSet<Actor>();
		hashSet.addAll (actorMap.get (inActor));
		hashSet.addAll (actorMap.get (outActor));
		actorMap.put (newActor, hashSet);

		// Let us first handle the incoming actor first.
		LinkedList<Object> environmentList = new LinkedList<Object>();
		for(Link lnk : inActor.getAllLinks ())
		{
			if (lnk.getOpposite ().getActor () != inActor && lnk.getOpposite ().getActor () != outActor) {
				environmentList.add (lnk.getOpposite ().getActor ());				
				environmentList.add (lnk.getOpposite ().getPort ());				
				environmentList.add (lnk.getChannel ());
			}
		}

		// Let us now handle the outgoing actor.
		for(Link lnk : outActor.getAllLinks ())
		{
			if (lnk.getOpposite ().getActor () != inActor && lnk.getOpposite ().getActor () != outActor) {
				environmentList.add (lnk.getOpposite ().getActor ());				
				environmentList.add (lnk.getOpposite ().getPort ());
				environmentList.add (lnk.getChannel ());
			}			
		}

		// Remove both the Actors
		multiplicityGraph.remove (inActor);
		multiplicityGraph.remove (outActor);

		// Now connect the environment to the new actor
		Iterator<Object> environmentIter = environmentList.iterator (); 
		while (environmentIter.hasNext ()) {			
			Actor oppositeActor = (Actor) environmentIter.next ();
			Port oppositePort   = (Port)  environmentIter.next ();
			Channel oldChannel = (Channel) environmentIter.next ();

			createNewLink (graph, oppositeActor, oppositePort, 
					newActor, oldChannel);
		}
		//System.out.println ("In Actor"+ outActor.toString ()+" has "+incomingPorts + "incoming ports and "
		// + outgoingPorts + "outgoing ports");			
	}
	
	private void addTerminateActor ()
	{
		int prtCnt = 0;
		Actor terminateActor = new Actor ();
		terminateActor.setFunc ("Terminate");
		terminateActor.setName ("Terminate");
		multiplicityGraph.add (terminateActor);
		
		Iterator<Actor>actorIter = multiplicityGraph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			
			if (actr == terminateActor)
				continue;
			
			// Add new Port to the Terminate Actor 
			Port pTerminate = new Port (Port.DIR.IN);
			pTerminate.setName ("p"+Integer.toString (prtCnt++));			
			pTerminate.setFunc (terminateActor.getFunc ());			
			multiplicityGraph.add (pTerminate);
			
			Port pActor = new Port (Port.DIR.OUT);
			pActor.setName ("pTer0");			
			pActor.setFunc (actr.getFunc ());			
			multiplicityGraph.add (pActor);			

			// Add new PortRef to the graph
			PortRef portRefSrc = new PortRef ();
			portRefSrc.setActor (actr);
			portRefSrc.setPort (pActor);

			PortRef portRefSnk = new PortRef ();
			portRefSnk.setActor (terminateActor);
			portRefSnk.setPort (pTerminate);

			// Add new Channel to the Graph
			Channel chnnl = new Channel ();
			multiplicityGraph.add (chnnl);

			chnnl.bind (portRefSrc,portRefSnk);
			if (solutions.contains (actr))
			{
				edgeQuantities.put (chnnl, solutions.getSolution (actr).toString ());
			}
			else
			{
				// This is a merged actor. We should be able to find out the solution.
				// We know that in case of merged actor, the actors merged have equal solutions.
				
				for (Actor mergedActor : actorMap.get (actr))
				{
					// This should break in first instance.					
					if (solutions.contains (mergedActor))
					{
						edgeQuantities.put (chnnl, solutions.getSolution (mergedActor).toString ());
						break;
					}
				}				
			}			
		}
	}

	private void constructActorWrappers ()
	{
		Actor terminateActor = multiplicityGraph.getActor ("Terminate");
		// Search for Longest Path for Every Actor
		Iterator<Actor>actorIter = multiplicityGraph.getActors ();
		HashSet<String> relevantParameters = new HashSet<String>();
		HashSet<String> modParams = new HashSet<String>();
		Actor mergedActor = null;
		
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			
			if (paramcomm.isPeriodActor (actr) || actr.getName ().equals ("Terminate"))
			{
				//System.out.println ("Skipping Period Actor: " + actr.getName ());
				continue;
			}
			
			relevantParameters.clear ();
			modParams.clear ();
			
			// search for the longest path.
			Stack<Actor> path = searchLongestPath (actr, terminateActor);
			if (path == null)
				System.out.println ("The path does not Exist from "+actr.getName ()+"to terminate actor !");
			else
			{
				//System.out.println ("Processing Actor: "+actr.getName ());
				//TODO: check the datePaper Condition.				
				if (solutions.contains (actr))
				{
					relevantParameters = paramcomm.getUsedParameterSet (actr);
					modParams = paramcomm.getModifiedParameterSet (actr);
					relevantParameters.addAll (modParams);
				}
				else
				{
					// This is a merged Actor.
					// Add the parameters of all the actors that are merged.					
					
					for (Actor actor : actorMap.get (actr))
					{
						relevantParameters.addAll (paramcomm.getUsedParameterSet (actor));
						modParams.addAll (paramcomm.getModifiedParameterSet (actor));
						relevantParameters.addAll (modParams);
					}
					

				}
				
				//System.out.println ("Relevant :" + relevantParameters.toString ());				
				HashSet<String> containsParameters = collectParamters (path);
				for (String param : containsParameters)
				{
					if (relevantParameters.contains (param) == false)
					{						
						throw new RuntimeException ("Date Paper Condition Failed : " +
								"Relevant Parameter not included : " + param);
					}
				}				
			}
			
			// Build a Linked List.
			
			//System.out.println ("ModParams of Actor " + actr.getName () + " :: " + modParams.toString ());
			
			// First reverse the path.
			Stack<Actor> tempPath = new Stack<Actor>();
			while (path.size () != 0)
				tempPath.add (path.pop ());			
			path = tempPath;
			
			
			Level firstLevel = new Level ();			
			Level currentLevel = firstLevel;
			Actor dstActor = path.pop ();
			while (path.size () != 0)
			{								
				Actor srcActor = path.pop ();				
				Channel chnnl = getChannel (srcActor, dstActor);
				if (chnnl == null)
				{
					System.out.println ("Some problem here that we did not find the channel !!");
				}				
				
				currentLevel.thisLevelParam = edgeQuantities.get (chnnl);
				
				if (modParams.contains (currentLevel.thisLevelParam))
					currentLevel.isModifiedParam = true;
				
				if (relevantParameters.contains (currentLevel.thisLevelParam))
				{
					currentLevel.isUsedParam = true;
					relevantParameters.remove (currentLevel.thisLevelParam);
				}
				
				//System.out.println ("Edge Qty : " + currentLevel.thisLevelParam);
				
				// Finished processing for this level. prepare for the next.
				currentLevel.nextLevel = new Level ();
				currentLevel = currentLevel.nextLevel;
				dstActor = srcActor;				
			}
			
			for (String param : relevantParameters)
			{				
				if (modParams.contains (param))
					currentLevel.isModifiedParam = true;
				else if (relevantParameters.contains (param))
				{
					currentLevel.isUsedParam = true;
					relevantParameters.remove (param);
				}
				
				currentLevel.thisLevelParam = param;
				currentLevel.nextLevel = new Level ();
				currentLevel = currentLevel.nextLevel;				
			}
			relevantParameters.clear ();
			
			if (mergedActor != null)
				currentLevel.thisLevelParam = mergedActor.getName ();
			else
				currentLevel.thisLevelParam = actr.getName ();
			currentLevel.nextLevel = null;			
			currentLevel.isThisActor =true;
			
			levelMap.put (actr, firstLevel);			
			//System.out.println ("Finished Processing Actor: "+actr.getName ());
		}		
	}
	
	public class Level
	{
		String thisLevelParam;
		boolean isModifiedParam = false;
		boolean isUsedParam = false;
		boolean isThisActor = false;
		Level nextLevel = null;
	}
	
	private Channel getChannel (Actor srcActor, Actor dstActor)
	{
		for(Link lnk : srcActor.getLinks (Port.DIR.OUT)) // .getAllOutgoingLinks ();
		{
			if (lnk.getOpposite ().getActor () == dstActor)
				return lnk.getChannel ();
		}		
		return null;
	}
	

	private HashSet<String> collectParamters (Stack<Actor> path)
	{
		HashSet<String> result = new HashSet<String>();
		@SuppressWarnings ("unchecked")
		Stack<Actor> tempPath = (Stack<Actor>) path.clone ();		
		while (tempPath.size () != 0)
		{
			Actor actr = tempPath.pop ();
			if (actr.getName () == "Terminate")
				continue;
			if (solutions.contains (actr))
			{
				result.addAll (paramcomm.getModifiedParameterSet (actr));
				HashSet<String> temp = paramcomm.getUsedParameterSet (actr);
				result.addAll (temp);
			}
			else
			{
				// This is a merged Actor.
				// Add the parameters of all the actors that are merged.
				result = new HashSet<String>();
				
				for (Actor actor : actorMap.get (actr))
				{
					result.addAll (paramcomm.getModifiedParameterSet (actor));
					result.addAll (paramcomm.getUsedParameterSet (actor));
				}
			}			
		}
		//System.out.println ("Collected Parameters : " + result.toString ());
		
		return result;
	}

	private Stack<Actor> searchLongestPath (Actor actr, Actor terminateActor)
	{
		HashMap<Channel, String> tempEdgeQuantities = new HashMap<Channel, String>();
		Iterator<Channel> chnnlIter = edgeQuantities.keySet ().iterator ();
		while (chnnlIter.hasNext ())
		{
			Channel chnnl = chnnlIter.next ();
			tempEdgeQuantities.put (chnnl, "1");
		}
		
		BellmanFord bmFrd = new BellmanFord (multiplicityGraph, tempEdgeQuantities);
		
		Stack<Actor> path = bmFrd.searchPath (actr, terminateActor, true);	
		return path;
	}

	public String getStringSchedule ()
	{
		// TODO Auto-generated method stub
		return "Schedule not yet implemented.";
	}
	
	// TODO: This Function is incomplete.
	private void getActorSchedule (Actor actr)
	{		
		Level actrLevel = null;
		
		//System.out.println ("Actor : "+actr.getName ());
		for (Actor a : levelMap.keySet ())
		{
			if (a.getName ().equals (actr.getName ()))
			{
				actrLevel = levelMap.get (a);
				break;
			}
		}		
		
		if (actrLevel == null)
		{			
			System.out.println ("Not Possible ! Non-Period Actors will not be merged anywhere." +
					"So it must exist in the levelMap. ");			
		}		
		
		//System.out.println ("ActorLevel : "+ actrLevel);		
		Stack<String> resultStack = new Stack<String>();
		String currentLevel = null;
		
		while (actrLevel != null)
		{
			//System.out.println ("This level param : "+ actrLevel.thisLevelParam);
			if (actrLevel.isModifiedParam == true)
			{
				currentLevel = insertElementAtCurrentLevel (currentLevel, ("push " + actrLevel.thisLevelParam + " ; "), true);				
			}
			else if (actrLevel.isUsedParam == true)
			{
				currentLevel = insertElementAtCurrentLevel (currentLevel, 
						("pop " + actrLevel.thisLevelParam + " ; " + actrLevel.thisLevelParam + " * "), false);
				resultStack.push (currentLevel);
				currentLevel = null;
			}
			else if (actrLevel.isThisActor)
				currentLevel = insertElementAtCurrentLevel (currentLevel, (actrLevel.thisLevelParam + " ; "), false);				 
			else
			{
				int param = -1;
				try
				{
					param = Integer.parseInt (actrLevel.thisLevelParam);
				}
				catch (NumberFormatException e)
				{}
				if (param == 1)
				{
					// Do Nothing if param is 1.
				}
				else // if (param == -1)
				{
					// This is symbolic parameter or Integer parameter but not 1.
					currentLevel = insertElementAtCurrentLevel (currentLevel, 
							(actrLevel.thisLevelParam + " * "), false);
					resultStack.push (currentLevel);
					currentLevel = null;
				}						
			}			
			actrLevel = actrLevel.nextLevel;
		}
		
		if (currentLevel != null)
			resultStack.push (currentLevel);
		
		String result = convertStacktoSchedule (resultStack);
		System.out.println ("Result : " + result);		
	}
	
	private String convertStacktoSchedule (Stack<String> stack)
	{
		String result = new String ("");		
		while (stack.size () != 0)
		{
			String str = stack.pop ();
			// Check if string contains push.
			if (str.contains ("push") == true)
			{
				str = "(" + str;				
				str = str.replaceFirst ("; push", ") ; push");
			}
			result = "(" + str + result + ")";						
		}
		
		return result;		
	}
	
	private String insertElementAtCurrentLevel (String level, String param, boolean atEnd)
	{
		if (level == null)
		{			
			level = param;
		}
		else
		{
			if (atEnd)
				level = level + param ;
			else
				level = param + level ;
		}
		return level;
	}

	// This algo is working correctly for this example.
	private Queue<Actor> topologicalSort (Graph graph)
	{
		// Using Breadth- First Search Algorithm.
		Actor startActor = getStartActor (graph);
		if (startActor == null)
		{
			System.out.println ("Start Actor not found !");
		}
		
		//System.out.println ("Start Actor : "+startActor.getName ());
		
		// Perform BFS
		Queue<Actor> bfsQueue = new LinkedList<Actor>();
		HashSet<Actor> visitedStates = new HashSet<Actor>();
		Queue<Actor> topoSort = new LinkedList<Actor>();
		
		bfsQueue.add (startActor);
		
		while (bfsQueue.size () != 0)
		{
			Actor actr = bfsQueue.remove ();
			if (paramcomm.isPeriodActor (actr))
				continue;
			for(Link lnk : actr.getLinks (Port.DIR.OUT)) // .getAllOutgoingLinks ();
			{			
				Actor lnkActor = lnk.getOpposite ().getActor ();
				if (paramcomm.isPeriodActor (lnkActor))
					continue;
				
				//System.out.println ("Actr "+ actr.getName () + " to Actor: "+lnkActor.getName ());
				if (visitedStates.contains (lnkActor) == false)
					bfsQueue.add (lnkActor);				
			}
			visitedStates.add (actr);
			topoSort.add (actr);
		}		
		return topoSort;		
	}

	private Actor getStartActor (Graph graph)
	{
		Iterator<Actor> actrIter = graph.getActors ();
		while (actrIter.hasNext ())
		{
			Actor actr = actrIter.next ();
			if (actr.getLinks (Port.DIR.IN).size() == 0)
				return actr;
		}		
		return null;
	}
	
	public void generateSchedule ()
	{		
		// Create Multiplicity Topology Graph
		generateMultiplicityGraph ();
		
		// Merge Actors with equal Solutions
		mergeActorsWithEqualSolutions ();
		
		// Add a Terminate Actor
		addTerminateActor ();
		
		// Construct Actor Wrappers
		constructActorWrappers ();
		
		// System.out.println ("Getting Topo Sort : ");
		
		// Topological Sort for Original Graph
		Queue<Actor> toposort = topologicalSort (graph);
		
		// Generate Schedule level by level		
		while (toposort.size () != 0)
		{
			Actor actr = toposort.remove ();
			//System.out.println ("Actr : "+ actr.getName ());
			getActorSchedule (actr);
		}		
	}
}