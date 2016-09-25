/** 
 * Old And Deprecated Symmetry Breaking Class.
 * This was old symmetry constraints, where we considered difference
 * between cousins and brothers. However, later on we changed the theory.
 * So this should not be used, but is kept as a reference.
 * 
 * @author Pranav Tendulkar
 * 
 */


//package graphanalysis;
//import graphanalysis.properties.GraphAnalysisSdfAndHsdf;
//
//import java.io.FileWriter;
//import java.io.IOException;
//import java.io.PrintWriter;
//import java.util.*;
//import java.util.Map.Entry;
//
//import output.DotGraph;
//
//import spdfcore.*;
//import spdfcore.Channel.*;
//import spdfcore.stanalys.*;
//

//
//public class GraphSymmetryBreaking 
//{
//	Graph graph;
//	Graph hsdfGraph;
//	Solutions solutions;
//	HashMap<Actor, String[]> indexStrings;
//	HashSet<Actor> visitedActors;
//	
//	public GraphSymmetryBreaking (Graph inputGraph, Solutions sol)
//	{
//		graph = inputGraph;
//		TransformSDFtoHSDF toHSDF = new TransformSDFtoHSDF ();
//		hsdfGraph = toHSDF.convertSDFtoHSDF (graph);
//		solutions = sol;
//		indexStrings = new HashMap<Actor, String[]>();
//		visitedActors = new HashSet<Actor>();
//	}
//	
//	private int countLevels (String str)
//	{
//		int count =0;
//		for (int i=0;i<str.length ();i++)
//			if (str.charAt (i) == '-')
//				count++;
//		return count+1;
//	}
//	
//	private void allocateStrings ()
//	{
//		Iterator<Actor> actrIter = graph.getActors ();
//		while (actrIter.hasNext ())
//		{
//			Actor actr = actrIter.next ();
//			int actrRepCount = solutions.getSolution (actr).returnNumber ();
//			
//			String []idxStr = new String[actrRepCount];
//			
//			for (int i=0;i<actrRepCount;i++)
//				idxStr[i] = "";
//			
//			indexStrings.put (actr, idxStr);
//		}
//	}
//	
//	public List<Actor> getProducerConnectedActors (Actor actr)
//	{		
//		HashSet<Actor> connectedActors = new HashSet<Actor>();
//		for(Link lnk : actr.getLinks (Port.DIR.IN))		
//			connectedActors.add (lnk.getOpposite ().getActor ());			
//
//		List<Actor> result = new ArrayList<Actor>(connectedActors);
//		return result;
//	}
//	
//	public List<Actor> getConsumerConnectedActors (Actor actr)
//	{		
//		HashSet<Actor> connectedActors = new HashSet<Actor>();
//		for(Link lnk : actr.getLinks (Port.DIR.OUT))
//		{
//			// We have to ignore self-edges.		
//			Actor child = lnk.getOpposite ().getActor ();
//			if (child.equals (actr) == false)
//				connectedActors.add (child);		
//		}
//		List<Actor> result = new ArrayList<Actor>(connectedActors);
//		return result;
//	}
//	
//	private List<Map.Entry<String, String>> parseLevel (int [][]index, int level, String actrName)
//	{
//		List<Entry<String, String>> result = new ArrayList<Map.Entry<String, String>>();
//		int levels   = index[0].length;
//		int repCount = index.length;
//		
//		if ((repCount == 1) && (level == 0))
//		{
//			// This is special case, with just one level.
//			for (int i=0;i<levels;i++)
//			{
//				for (int j=0;j<levels;j++)
//				{
//					if ((index[0][i] - index[0][j]) == -1)
//					{
//						Map.Entry<String, String> temp = 
//							    new AbstractMap.SimpleEntry<String, String>(
//							    		actrName + "_" + Integer.toString (i), 
//							    		actrName + "_" + Integer.toString (j));
//						result.add (temp);						
//					}
//				}
//			}
//		}
//		else
//		{		
//			for (int i=0;i<repCount;i++)
//			{
//				// The reason i have kept j to start from zero and not i,
//				// is that i don't want to assume that the index array will be 
//				// sorted regularly.
//				for (int j=0;j<repCount;j++)
//				{				
//					if ((index[i][level] - index[j][level]) == -1)
//					{
//						boolean edge = true;
//						for (int k=0;k<levels;k++)
//						{
//							if (k != level && (index[i][k] != index[j][k]))						
//							{
//								edge = false;
//								break;
//							}					
//						}
//						
//						if (edge)
//						{
//							Map.Entry<String, String> temp = 
//								    new AbstractMap.SimpleEntry<String, String>(
//								    		actrName + "_" + Integer.toString (i), 
//								    		actrName + "_" + Integer.toString (j));
//							result.add (temp);
//						}					
//					}
//				}
//			}
//		}
//		
//		return result;		
//	}
//	
//	public List<Map.Entry<String, String>> getSymmetryEdges ()
//	{
//		List<Entry<String, String>> result = new ArrayList<Map.Entry<String, String>>(); 
//		Iterator<Actor> actrIter = graph.getActors ();
//		while (actrIter.hasNext ())
//		{
//			Actor actr = actrIter.next ();
//			int actrRepCount = solutions.getSolution (actr).returnNumber ();
//			
//			if (actrRepCount != 1)
//			{			
//				String []idxStr = indexStrings.get (actr);
//				if (idxStr[0].equals ("") == false)
//				{
//					int levels = 0;
//					if (idxStr[0].contains ("-"))
//					{
//						// Reconstruct the Index Array.
//						levels = countLevels (idxStr[0]);
//						int [][]index = new int[actrRepCount][levels];
//						for (int i=0;i<actrRepCount;i++)
//						{
//							String[] split = idxStr[i].split ("-");
//							for (int j=0;j<levels;j++)
//								index[i][j] = Integer.parseInt (split[j]);							
//						}
//						
//						for (int i=0;i<levels;i++)
//						{
//							List<Map.Entry<String, String>> tempResult = parseLevel (index, i, actr.getName ());
//							result.addAll (tempResult);
//						}
//					}
//					else
//					{
//						int[][] index = new int[1][actrRepCount];
//						for (int i=0;i<actrRepCount;i++)
//							index[0][i] = Integer.parseInt (idxStr[i]);
//						
//						List<Map.Entry<String, String>> tempResult = parseLevel (index, 0, actr.getName ());
//						result.addAll (tempResult);
//						
//					}
//				}			
//			}
//		}
//		return result;
//	}
//	
//	public void generateLexicographicSymmetry (String fileName)
//	{
//		DotGraph dotG = new DotGraph ();
//		String dotGraphData = dotG.getDotDataInString (hsdfGraph);
//		
//		dotGraphData += "edge[constraint=false,minlen=2,style=dotted color=black];\n";
//		
//		
//		Iterator<Actor> actrIter = graph.getActors ();
//		while (actrIter.hasNext ())
//		{
//			Actor actr = actrIter.next ();
//			int repCount = solutions.getSolution (actr).returnNumber ();
//			
//			for (int i=0;i<repCount-1;i++)
//			{
//				// "D_3" -> "E_0"[minlen=2,
//				dotGraphData += ("\"" + actr.getName () + "_" + Integer.toString (i) + "\" "
//							 + "->" 
//							 + "\"" + actr.getName () + "_" + Integer.toString (i+1) + "\"\n");
//				
//			}			
//		}
//		
//		FileWriter fstream;
//		try 
//		{
//			fstream = new FileWriter (fileName);
//			PrintWriter out = new PrintWriter (fstream);
//			out.println ("digraph G {");
//			out.println (dotGraphData);
//			out.println ("}");
//			out.close ();
//			fstream.close ();
//		} 
//		catch (IOException e) 
//		{		
//			e.printStackTrace ();
//		}
//		
//	}
//	
//	public void generateSymmetryGraph (String fileName)
//	{
//		DotGraph dotG = new DotGraph ();
//		String dotGraphData = dotG.getDotDataInString (hsdfGraph);
//		
//		dotGraphData += "edge[constraint=false,minlen=2,style=dotted color=black];\n";
//		
//		generateIndexForSymmetryEdges ();
//		
//		List<Map.Entry<String, String>> symmetryEdges = getSymmetryEdges ();
//		for (int i=0;i<symmetryEdges.size ();i++)
//		{
//			// "D_3" -> "E_0"[minlen=2,
//			dotGraphData += ("\"" + symmetryEdges.get (i).getKey () + "\" "
//						 + "->" 
//						 + "\"" + symmetryEdges.get (i).getValue () + "\"\n");
//		}
//		
//		FileWriter fstream;
//		try 
//		{
//			fstream = new FileWriter (fileName);
//			PrintWriter out = new PrintWriter (fstream);
//			out.println ("digraph G {");
//			out.println (dotGraphData);
//			out.println ("}");
//			out.close ();
//			fstream.close ();
//		} 
//		catch (IOException e) 
//		{		
//			e.printStackTrace ();
//		}
//	}
//	
//	public void generateIndexForSymmetryEdges ()
//	{
//		int []repetitionCount = new int[graph.countActors ()];
//		Actor []path = new Actor[graph.countActors ()];	
//		
//		// Initialize to zero.
//		for (int i=0;i<graph.countActors ();i++)
//		{
//			repetitionCount[i] = 0;
//			path[i] = null;
//		}
//		
//		// Find the start actors.
//		GraphAnalysisSdfAndHsdf analysis = new GraphAnalysisSdfAndHsdf (graph, solutions, hsdfGraph);
//		List<Actor> startActors = analysis.findSdfStartActors ();		
//		
//		// Allocate the index Strings for each actor instance.
//		allocateStrings ();
//		
//		// Allocate the initial String to all the start Actors.
//		for (int i=0;i<startActors.size ();i++)
//		{
//			// Assign the index for start Actors.
//			// Generally it has solution one.
//			Actor sdfActor = startActors.get (i);			
//			int actrRepCount = solutions.getSolution (sdfActor).returnNumber ();
//			
//			if ((actrRepCount != 1) && (hasSelfEdge (sdfActor) == false))
//			{
//				String[] idxStr = indexStrings.get (sdfActor);
//				for (int j=0;j<actrRepCount;j++)
//				{
//					idxStr[j] = Integer.toString (j);
//				}
//			}
//		}
//		
//		visitedActors.clear ();
//		
//		// Now we move to the next Actors.
//		for (int i=0;i<startActors.size ();i++)
//		{
//			Actor sdfActor = startActors.get (i);
//			repetitionCount[0] = solutions.getSolution (sdfActor).returnNumber ();
//			path[0] = sdfActor;
//			visitedActors.add (sdfActor);
//			allocateIndex (sdfActor, repetitionCount, path);
//		}
//		
////		System.out.print ("Index Allocation");
////		Iterator<Actor> actrIter = graph.getActors ();
////		while (actrIter.hasNext ())
////		{
////			Actor actr = actrIter.next ();
////			int actrRepCount = solutions.getSolution (actr).returnNumber ();
////			
////			System.out.println ("\nActor : " + actr.getName ());
////			String[] idxStr = indexStrings.get (actr);
////			for (int i = 0; i<actrRepCount;i++)
////			{
////				System.out.print ("i : " + idxStr[i] + " ");
////			}			
////		}		
//	}
//	
//	private String[] getParentWithEqualRepIdxStrings (int repCount, Actor[] path)
//	{
//		String []result = null;
//		
//		for (int i=path.length-1;i>=0;i--)
//		{
//			if (path[i] == null)
//				continue;
//			
//			if (solutions.getSolution (path[i]).returnNumber () == repCount)
//			{
//				result = indexStrings.get (path[i]);
//				break;
//			}
//		}
//		
//		return result;
//	}
//	
//	private void allocateIndex (Actor parent, int []repCntVec, Actor []path)
//	{
//		// System.out.println ("Actor : " + parent.getName () + " Rep Vec : " + arrayToString (repCntVec));
//		
//		 List<Actor> connectedChildActors = getConsumerConnectedActors (parent);
//		 int parentRepCount = solutions.getSolution (parent).returnNumber ();
//		 int emptyIndex = findEmptyIndex (repCntVec);
//		 
//		 // Allocate Index for Each of the Connected Actor.
//		 for (int i=0;i<connectedChildActors.size ();i++)
//		 {
//			 Actor child = connectedChildActors.get (i);
//			 int childRepCount = solutions.getSolution (child).returnNumber ();
//			 String []childIdxStr = indexStrings.get (child);
//			 String []parentIdxStr = indexStrings.get (parent);
//			 
//			 if (visitedActors.contains (child) == false)
//			 {
//				 visitedActors.add (child);
//				 if ((childRepCount != 1) && (hasSelfEdge (child) == false))
//				 {
//					 if (parentRepCount == childRepCount)
//					 {					 
//						 // We should copy the index strings as we have 
//						 // 1 to 1 connection.
//						 if (parentIdxStr[0].equals (""))
//						 {
//							 // This happens when the parent does have a self-edge
//							 // It will not be having symmetry edge. But the children can
//							 // have symmetry edges. In that case, the symmetry edges will
//							 // be just 1st to 2nd, 2nd to 3rd, 3rd to ... so on.
//							 for (int j=0;j<childRepCount;j++)
//								 childIdxStr[j] = Integer.toString (j);
//						 }
//						 else
//						 {
//							 for (int j=0;j<childRepCount;j++)
//								 childIdxStr[j] = parentIdxStr[j];
//						 }							 
//					 }
//					 else if (parentRepCount < childRepCount)
//					 {
//						 // We have a Split Case over here.
//						 for (int j=0;j<childRepCount;j++)
//						 {
//							 String dashStr = "-";
//							 if (parentIdxStr[0].equals (""))
//								 dashStr ="";
//							 childIdxStr[j] = parentIdxStr[j/(childRepCount/parentRepCount)] + 
//									 dashStr + Integer.toString (j%(childRepCount/parentRepCount));
//						 }					 				 
//					 }
//					 else if (parentRepCount > childRepCount)
//					 {
//						 // We have a Join Case over here.
//						 // In our case, where we have strictly structured graphs, this
//						 // should be equal to some of the parent in the path which had same repetition count.
//						 String [] parentWithEqualRepIdxStr = getParentWithEqualRepIdxStrings (childRepCount, path);					 
//						 if (parentWithEqualRepIdxStr != null)
//						 {					 
//							 for (int j=0;j<childRepCount;j++)
//							 {
//								 childIdxStr[j] = parentWithEqualRepIdxStr[j];
//							 }
//						 }
//						 else
//						 {
//							 // Thus, we know that its not a regular structure of the graph.
//							 // For example, the split actor repetition count was 2 and join
//							 // actor repetition count is 4. So its not merged with the same
//							 // repetition counts as it was splited with. In this case, what
//							 // we have to do is go back to the actor where the split begin
//							 // and we ignore the intermediate actors which are present in
//							 // between this merge actor and the split actor. Thus we will have
//							 // a new split with less number of branches, which can give us the
//							 // symmetry edges.
//							 
//							 int psuedoParentRepCount = 0;
//							 String []psuedoParentIdxStr = null;
//							 
//							 for (int j=path.length-1;j>=0;j--)
//							 {
//								 if (path[j] == null)
//									 continue;
//								 
//								 
//								 if (solutions.getSolution (path[j]).returnNumber () < childRepCount)
//								 {
//									 // System.out.println ("Child : " + child.getName () + " Psuedo Parent : " + path[j].getName ());
//									 psuedoParentRepCount = solutions.getSolution (path[j]).returnNumber ();
//									 psuedoParentIdxStr = indexStrings.get (path[j]);
//									 break;
//								 }
//							 }
//							 
//							 if (psuedoParentIdxStr != null)
//							 {
//								 for (int j=0;j<childRepCount;j++)
//								 {
//									 String dashStr = "-";
//									 if (parentIdxStr[0].equals (""))
//										 dashStr ="";
//									 childIdxStr[j] = psuedoParentIdxStr[j/(childRepCount/psuedoParentRepCount)] + 
//											 dashStr + Integer.toString (j%(childRepCount/psuedoParentRepCount));
//								 }
//							 }
//							 else
//							 {
//								 // This is a join case where the graph starts with the start actor and directly 
//								 // joins without starting from splitting.
//								 for (int j=0;j<childRepCount;j++)
//								 {								 
//									 childIdxStr[j] = Integer.toString (j);
//								 }							 
//							 }
//						 }
//					 }
//				 }
//				 
//				 // Call recursively allocateIndex on its childs.
//				 repCntVec[emptyIndex] = childRepCount;
//				 path[emptyIndex] = child;
//				 allocateIndex (child, 
//						 Arrays.copyOf (repCntVec, repCntVec.length),
//						 Arrays.copyOf (path, path.length));
//				 repCntVec[emptyIndex] = 0;
//			 }
//		 }			
//	}
//	
//	private boolean hasSelfEdge (Actor actr) 
//	{
//		for(Link lnk : actr.getLinks (Port.DIR.OUT))
//		{		
//			if (lnk.getOpposite ().getActor ().equals (actr))
//				return true;
//		}		
//		return false;
//	}
//
//	private int findEmptyIndex (int[] array) 
//	{
//		for (int i=0;i<array.length;i++)
//			if (array[i] == 0)
//				return i;
//		return -1;
//	}
//}
