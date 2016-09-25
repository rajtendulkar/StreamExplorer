package graphanalysis.scheduling;


import java.util.*;

import output.DotGraph;

import spdfcore.*;
import spdfcore.Channel.Link;
import spdfcore.stanalys.*;

/**
 * APGAN : Acyclic Pairwise Grouping of Adjacent Nodes algorithm
 * 
 * @author Pranav Tendulkar
 *
 */
public class Apgan 
{	
	/**
	 * Generate Dot files at every stage
	 */
	public boolean generateSubGraphDotFiles = false;;
	/**
	 *  Solutions of the input graph
	 */
	private Solutions solutions = new Solutions ();
	
	/**
	 * Solutions at previous step of the the algorithm 
	 */
	private Solutions prevSolutions = new Solutions ();
	
	/**
	 * Expressions for the graph
	 */
	private GraphExpressions expressions = new GraphExpressions ();
	
	/**
	 * Reachability matrix
	 */
	HashMap<Actor, List<Actor>> reachability = new HashMap<Actor, List<Actor>>();
	
	/**
	 * Repetition count with respect to channels
	 */
	HashMap<Channel, Integer> repCount = new HashMap<Channel, Integer>();
	
	
	/**
	 * Internal variables to generate names of new actors / channels.
	 */
	private int omegacount = 1, portCount = 0;	
	
	/**
	 * Solution of omega actor.
	 */
	HashMap<String, String> equations = new HashMap<String,String>();
	
	/**
	 * Internal variables of the algorithm
	 */
	private Actor currentOmegaActor, collapsedLeftActor, collapsedRightActor;	
	
	
	/**
	 * Construct Reachability matrix of the input graph
	 * 
	 * This algorithm comes from the book - 
	 * Essential Java for Scientists and Engineers 
	 * page no. 247
	 * search in books.google.com as "Reachability Matrix Java"
	 * 
	 * @param graph input graph
	 */
	private void constructReachability (Graph graph) 
	{
		int actorCount = graph.countActors ();
		int count=0;
		int[][] adjacencyMatrix = new int [actorCount][actorCount];
		int[][] R = new int [actorCount][actorCount];
		int[][] B = new int [actorCount][actorCount];
		HashMap<Actor, Integer> actorIndex = new HashMap<Actor, Integer>();
		HashMap<Integer, Actor> actorReverseIndex = new HashMap<Integer, Actor>();
		
		
		// Initialize the matrix to zero.
		for (int i=0;i<actorCount;i++)
			for (int j=0;j<actorCount;j++)
			{
				adjacencyMatrix[i][j] = 0;
				R[i][j] = 0;
				B[i][j] = 0;
			}
		
		// First we have to know which actor will be stored where in the matrix.
		Iterator<Actor> actorIter = graph.getActors ();		
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			actorIndex.put (actr, count);
			actorReverseIndex.put (count++, actr);
			//System.out.println ("Actor:"+actr.getName ()+" Index:"+(count-1));
		}
		
		// Calculate Adjacency Matrix
		actorIter = graph.getActors ();		
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			for(Link lnk : actr.getLinks (Port.DIR.OUT))  // get All Outgoing Links			
				adjacencyMatrix[actorIndex.get (actr)][actorIndex.get (lnk.getOpposite ().getActor ())] = 1;
		}
		
		// Copy adjacency to R and B.
		for (int i=0;i<actorCount;i++)
			for (int j=0;j<actorCount;j++)
			{				
				R[i][j] = adjacencyMatrix[i][j];
				B[i][j] = adjacencyMatrix[i][j];
			}

		// Calculate Reachability Matrix
		for (int i=1;i<=(actorCount-2);i++)
		{
			// B = B * Adjacency
			for (int j=0;j<actorCount;j++)
				for (int k=0;k<actorCount;k++)
					for (int l=0;l<actorCount;l++)
						B[j][k] += B[j][l] * adjacencyMatrix[l][k];
			
			// R = R + B
			for (int j=0;j<actorCount;j++)
				for (int k=0;k<actorCount;k++)
					R[j][k] += B[j][k]; 
		}
		
		// Now we populate in our format.

		// Calculate the reachability matrix		
		actorIter = graph.getActors ();
		reachability.clear ();
		
		// First I want to create all the lists for each actor so as not to have null reference later on.
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			List<Actor> tempActList = new ArrayList<Actor>();
			reachability.put (actr, tempActList);
		}
		
		actorIter = graph.getActors ();
		while (actorIter.hasNext ())
		{
			Actor actr = actorIter.next ();
			int indx = actorIndex.get (actr);
			for (int i=0;i<actorCount;i++)
			{
				// Insert in reachability matrix only if not equal to zero.
				if (R[indx][i] != 0)
				{
					reachability.get (actr).add (actorReverseIndex.get (i));
				}
			}						
		}
		// Finished constructing Reachability Matrix.
	}
	
	/**
	 * Construction repetition count for each channel.
	 * 
	 * @param inputGraph input graph
	 */
	private void getRepetitionCount (Graph inputGraph)
	{
		Iterator<Channel> channels = inputGraph.getChannels ();
		int g;
		
		repCount.clear ();
		
		while (channels.hasNext ())
		{
			Channel channel = channels.next ();
			Channel.Link linkIN = channel.getLink (Port.DIR.IN);
			Actor actorIN = linkIN.getActor ();
									
			Channel.Link linkOUT = channel.getLink (Port.DIR.OUT);
			Actor actorOUT  = linkOUT.getActor ();
									
			g = (Expression.gcd (solutions.getSolution (actorOUT), solutions.getSolution (actorIN))).returnNumber ();			
			
			repCount.put (channel, g);			
		}
	}
	
	/**
	 * Select an edge for collapsing.
	 * 
	 * @return Channel to collapse
	 */
	public Channel selectEdge ()
	{
		int max = 0;
		List<Channel> channelList=new ArrayList<Channel>();
		
		// Clear the Existing List.
		channelList.clear ();		
		
		//iterating over values only
		for (Integer value : repCount.values ()) 
		{
			if (max < value)
				max = value;
		
		}
		//System.out.println ("Maximum = " + max);
		
		// Formulate list of channels which has the max repetition count.
		Iterator<Map.Entry<Channel, Integer>> entries = repCount.entrySet ().iterator ();
		while (entries.hasNext ()) 
		{
		    Map.Entry<Channel, Integer> entry = entries.next ();
		    if (entry.getValue () == max)
		    {
		    	//System.out.println ("Key = " + entry.getKey () + ", Value = " + entry.getValue ());
		    	channelList.add (entry.getKey ());
		    }
		}
		
		// Now we have to select from the List of Channels.
		// First check if the channel introduces a cycle
		// If it does, remove it .
		for (int i=0;i<channelList.size();i++)
		{
			Channel tempChannel = channelList.get (i);
			if (edgeIntroducesCycle (tempChannel))
			{
				//System.out.println ("Removing the List element" + tempChannel.toString ());
				channelList.remove (i);
				i--;
			}
		}
		
		// Now select in alphabetical order from the channelList.
		if (channelList.size () <= 0)
		{
			System.out.println ("Error the Channel Size is zero.");
			return null;
		}
		else if (channelList.size () > 1)
		{
			// Return alphabetically first element.
			Channel retChannl= channelList.get (0);
			String channelName= retChannl.toString ();
			for(int i=1;i<channelList.size();i++)
			{
				Channel tempChannel = channelList.get (i);				
				if (channelName.compareToIgnoreCase (tempChannel.toString ()) > 0)
					retChannl = tempChannel;							
			}
			
			return retChannl;
		}
		else //if (channelList.size () == 1)
		{
			return channelList.get (0);
		}		
	}	

	/**
	 * Check if this channel introduces a cycle
	 * 
	 * @param channel channel to check
	 * @return true if we find a cycle, false otherwise
	 */
	private boolean edgeIntroducesCycle (Channel channel) 
	{		
		Actor inActor, outActor;
		inActor = channel.getLink (Port.DIR.IN).getActor ();
		outActor = channel.getLink (Port.DIR.OUT).getActor ();
		
		// Now in this we have to find out that -
		// Is there any actor which is reachable from outActor
		// And this actor has a reachable inActor.
		// If yes, it introduces a cycle.
		
		Iterator<Actor> outGoingActorIter = reachability.get (outActor).iterator ();
		while (outGoingActorIter.hasNext ())
		{
			Actor out = outGoingActorIter.next ();
			Iterator<Actor> outIter = reachability.get (out).iterator ();
			while (outIter.hasNext ())
			{
				if (outIter.next () == inActor)
				{
					//System.out.println ("Found a Cycle in the Graph");
					return true;
				}
			}			
		}		
		return false;
	}
	
	/**
	 * Create a new link between two actors.
	 * 
	 * @param graph input graph
	 * @param rate rate
	 * @param oppositeActor actor on other side
	 * @param oppositePort port on other side
	 * @param multPortRate new port rate
	 * @param newActor newly inserted actor
	 */
	private void createNewLink (Graph graph, String rate, Actor oppositeActor, Port oppositePort, 
			                   int multPortRate, Actor newActor)
	{
		// Add new Port to the graph. 
		Port.DIR direction = oppositePort.getDir ().getOpposite ();
		Port p = new Port (direction);
		p.setName ("p"+Integer.toString (portCount));
		p.setFunc ("omega"+Integer.toString (omegacount));
		p.setRate (Integer.toString (Integer.parseInt (rate) * multPortRate));
		graph.add (p);
		
		// Add new PortRef to the graph
		PortRef portRefSrc = new PortRef ();
		portRefSrc.setActor (newActor);
		portRefSrc.setPort (p);
		
		PortRef portRefSnk = new PortRef ();
		portRefSnk.setActor (oppositeActor);
		portRefSnk.setPort (oppositePort);
				
		// Add new Channel to the Graph
		Channel chnnl = new Channel ();
		graph.add (chnnl);
		
		if (direction  == Port.DIR.OUT)
			chnnl.bind (portRefSrc,portRefSnk);
		else
			chnnl.bind (portRefSnk,portRefSrc);
		
		portCount++;
	}
	
	/**
	 * Collapse a selected channel in the graph.
	 * 
	 * @param slctChannel selected channel
	 * @return new Graph with collapsed channel
	 */
	private Graph collapseChannelInGraph (Channel slctChannel) 
	{
		Actor inActor, outActor, newActor;
		Graph graph = slctChannel.getGraph ();
		portCount=0;
		
		//System.out.println ("Collapsing Channel");		
		
		// Actor which has incoming edge.
		inActor = slctChannel.getLink (Port.DIR.IN).getActor ();
		// Actor which has outgoing edge.
		outActor = slctChannel.getLink (Port.DIR.OUT).getActor ();
		
		// Collapsed Actor Stored for future reference.
		collapsedLeftActor = outActor;
		collapsedRightActor = inActor;		
		
		// New Actor which is going to be inserted in the graph
		newActor = new Actor ();
		newActor.setFunc ("omega"+ Integer.toString (omegacount));
		newActor.setName ("omega" + Integer.toString (omegacount));
		graph.add (newActor);
		
		currentOmegaActor = newActor;
		
		// Let us first handle the incoming actor first.
		int multPortRate = (int) Math.ceil (((double)solutions.getSolution (inActor).returnNumber () 
				/ (double)solutions.getSolution (outActor).returnNumber ()));
		
		LinkedList<Object> environmentList = new LinkedList<Object>();
		for(Link lnk : inActor.getAllLinks ())
		{
			
			if (lnk.getOpposite ().getActor () != inActor && lnk.getOpposite ().getActor () != outActor) {
				environmentList.add (new Integer (multPortRate));
				environmentList.add (lnk.getPort ().getRate ());
				environmentList.add (lnk.getOpposite ().getActor ());				
				environmentList.add (lnk.getOpposite ().getPort ());				
			}			
		}
		
		// Let us now handle the outgoing actor.
		multPortRate = (int) Math.ceil (((double)solutions.getSolution (outActor).returnNumber () 
				/ (double)solutions.getSolution (inActor).returnNumber ()));

		for(Link lnk : outActor.getAllLinks ())
		{			
			if (lnk.getOpposite ().getActor () != inActor && lnk.getOpposite ().getActor () != outActor) 
			{
				environmentList.add (new Integer (multPortRate));
				environmentList.add (lnk.getPort ().getRate ());
				environmentList.add (lnk.getOpposite ().getActor ());				
				environmentList.add (lnk.getOpposite ().getPort ());				
			}			
		}
		
		// Remove both the Actors
		graph.remove (inActor);
		graph.remove (outActor);
		
		// Now connect the environment to the new actor
		Iterator<Object> environmentIter = environmentList.iterator (); 
		while (environmentIter.hasNext ()) 
		{
			multPortRate        = ((Integer) environmentIter.next ()).intValue ();
			String rate         = (String) environmentIter.next ();
			Actor oppositeActor = (Actor) environmentIter.next ();
			Port oppositePort   = (Port)  environmentIter.next ();
			
			createNewLink (graph, rate, oppositeActor, oppositePort, 
	                   multPortRate, newActor);
		}
		
		//System.out.println ("In Actor"+ outActor.toString ()+" has "+incomingPorts + "incoming ports and "+ outgoingPorts + "outgoing ports");
		omegacount++;
		
		return graph;		
	}
	
	/**
	 * Storing APGAN equations
	 */
	private void storeEquation ()
	{		
		String equation = new String ("");		
		
		int omegaRepCount = solutions.getSolution (currentOmegaActor).returnNumber ();
		String omegaStr = currentOmegaActor.getName ();
		
		Actor leftActor = collapsedLeftActor;
		Actor rightActor = collapsedRightActor;
		
		equation += "(";		
		equation += "("+(Integer.toString (prevSolutions.getSolution (leftActor).returnNumber ()/omegaRepCount)+"*"+leftActor.getName ())+")";
		equation += "*";
		equation += "("+(Integer.toString (prevSolutions.getSolution (rightActor).returnNumber ()/omegaRepCount)+"*"+rightActor.getName ())+")";
		equation += ")";
		equations.put (omegaStr, equation);		
	}
	
	/**
	 * Get APGAN schedule. It should be single-appearance schedule (SAS).  
	 * @return schedule 
	 */
	public String getStringSchedule ()
	{
		String schedule = new String ("omega"+Integer.toString (omegacount-1));
		while (schedule.matches ("(?i).*omega.*"))
		{
			String omegaStr = schedule.substring (schedule.indexOf ("omega"),schedule.indexOf ("omega")+6);
			schedule = schedule.replaceAll (omegaStr,equations.get (omegaStr));
		}
		
		// Beautification of the string. Can we do more?
		schedule = schedule.replaceAll ("1\\*","");		
		
		return schedule;		
	}
	
	/**
	 * Generate Single appearance schedule using APGAN algorithm.
	 * 
	 * @param inputGraph input SDF graph
	 */
	public void generateScheduleApgan (Graph inputGraph)
	{		
		int count=0;
		DotGraph dotG = new DotGraph ();
		
		// Clear and Build the input Graph Expressions
		expressions.clear ();
		expressions.parse (inputGraph);			
		
		// Clear previous and Calculate the Repetition Vector
		solutions.clear ();
		solutions.solve (inputGraph, expressions);		
		
		prevSolutions.clear ();
		prevSolutions.copy (solutions);
		
		while (inputGraph.getChannels ().hasNext ())
		{						
			//System.out.println ("Iteration:"+count);					
			
			// Get Repetition Count
			getRepetitionCount (inputGraph);
			
			// Construct Reachability Matrix
			constructReachability (inputGraph);
			
			// Select the Edge
			Channel slctChannel = selectEdge ();
			
			// Merge the nodes in the graph
			inputGraph = collapseChannelInGraph (slctChannel);
			
			// Clear and Build the input Graph Expressions
			expressions.clear ();
			expressions.parse (inputGraph);			
			
			// Clear previous and Calculate the Repetition Vector
			solutions.clear ();
			solutions.solve (inputGraph, expressions);
						
			// Print the solutions
			//solutions.dump ();			
			
			// Skip the First Iteration always.
			storeEquation ();
			
			prevSolutions.clear ();
			prevSolutions.copy (solutions);
			
			if (generateSubGraphDotFiles)
			{				
				dotG.generateDotFromGraph (inputGraph, "outputFiles/apgan-subgraph" + count + ".dot");				
			}			
			count++;
		}		
	}
}