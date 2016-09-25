package graphanalysis;
import java.util.*;
import spdfcore.*;
import spdfcore.stanalys.GraphExpressions;
import spdfcore.stanalys.Solutions;

/**
 * Algorithm to convert SDF graph to HSDF graph.
 * The algorithm is present in Bhattacharya book.
 *  
 * @author Pranav Tendulkar
 *
 */
public class TransformSDFtoHSDF 
{
	
	/**
	 * Set the properties of the port.
	 * 
	 * @param g graph
	 * @param actorName name of the actor
	 * @param portName name of the port
	 * @param rate rate of the port
	 */
	private void setPortRate (Graph g, String actorName, String portName, String rate ) 
	{
		Actor actor = g.getActor (actorName);
		String func = new String(actor.getFunc ());
		Id portId = new Id ();
		portId.setFunc (new String(func));
		portId.setName (new String(portName));
		Port port = g.getPort (portId);
		port.setRate (new String(rate));
	}	
	
	/**
	 * Convert SDF to HSDF graph with unique channels.
	 * One actor to another actor with only one edge. It means
	 * that some edges would have non-unity but equal rates.
	 * 
	 * @param sdfGraph input SDF graph.
	 * @return HSDF graph with unique channels between actors
	 */
	public Graph convertSDFtoHSDFWithUniqueChannels (Graph sdfGraph)
	{
		Iterator<Actor> iterActor = sdfGraph.getActors ();
		Graph hsdfGraph = new Graph ();
		List<String[]> channelMap = new ArrayList<String[]>();
		
		GraphExpressions expressions = new GraphExpressions ();
    	expressions.parse (sdfGraph);
		Solutions sdfSolutions = new Solutions ();
		sdfSolutions.setThrowExceptionFlag (true);
		sdfSolutions.solve (sdfGraph, expressions);
		
		while (iterActor.hasNext ())
		{
			Actor actor = iterActor.next ();
			for (int i=0;i<sdfSolutions.getSolution (actor).returnNumber ();i++)
			{
				Actor a = new Actor ();
	    		a.setFunc (new String(actor.getFunc ()));
	    		a.setName (new String(actor.getName () + "_" + Integer.toString (i)));
	    		a.setExecTime (actor.getExecTime ());
	    		a.setActorType(actor.getActorType());
	    		
	    		hsdfGraph.add (a);
			}
		}
			
		Iterator<Channel> chnnlIter = sdfGraph.getChannels ();
		while (chnnlIter.hasNext ())
		{
			Channel chnnl = chnnlIter.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
			
			int nA = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ()); // gSrcP->getRate ();
			int nB = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ()); // gDstP->getRate ();
	        int qA = sdfSolutions.getSolution (srcActor).returnNumber (); // repetitionVector[gSrcA->getId ()];
	        int qB = sdfSolutions.getSolution (dstActor).returnNumber (); //repetitionVector[gDstA->getId ()];
	        int d = chnnl.getInitialTokens (); // gC->getInitialTokens ();
	        
	        int count=0;
	        
	        for (int i = 1; i <= qA; i++)
	        {	    
	            for (int k = 1; k <= nA; k++)
	            {	            	
	                int j = 1 + (int)Math.floor ((double)((d + (i-1) * nA + k - 1) %  (nB*qB)) / (double)(nB));
	                
	                // Initial tokens
	                int t = (int) Math.floor ((double)(d + (i-1)*nA + k-1) /(double)(nB*qB));
	                
	                String result[] = new String[5];
	                result[0] = new String (srcActor.getName () +"_" +Integer.toString (i-1));
	                result[1] = new String (dstActor.getName () +"_"+ Integer.toString (j-1));
	                result[2] = new String (Integer.toString (t));
	                result[3] = new String (Integer.toString (chnnl.getTokenSize ()));
	                result[4] = new String(chnnl.getName () + "_" + Integer.toString (count++));
	                
	                channelMap.add (result);
	            }
	        }           
		}
		
		HashMap<String, Integer> portCountMap = new HashMap<String, Integer>();
		
		for (int i=0;i<channelMap.size ();i++)
		{
			String result[] = channelMap.get (i);
			int initialTokens = Integer.parseInt (result[2]);
			int portRate = 1;			
			for (int j=i+1;j<channelMap.size ();j++)
			{
				String tempResult[] = channelMap.get (j);
				if (result[0].equals (tempResult[0]) && result[1].equals (tempResult[1]))
				{
					portRate += 1;
					initialTokens += Integer.parseInt (tempResult[2]);
					channelMap.remove (j);
					j--;
				}
			}
			
			int srcPortCount = 0;
			int dstPortCount = 0;
			
			if (portCountMap.containsKey (result[0]))
			{
				srcPortCount = portCountMap.get (result[0]);
				portCountMap.remove (result[0]);				
			}
			
			portCountMap.put (result[0], srcPortCount+1);
			
			if (portCountMap.containsKey (result[1]))
			{
				dstPortCount = portCountMap.get (result[1]);
				portCountMap.remove (result[1]);				
			}
			
			portCountMap.put (result[1], dstPortCount+1);			
			
			// Create Port on Source Node
			Actor hSrcA = hsdfGraph.getActor (result[0]);
			Actor hDstA = hsdfGraph.getActor (result[1]);
			
            Port pSrc = new Port (Port.DIR.OUT);
            String portName = "p"+hSrcA.getName ()+"_"+Integer.toString (srcPortCount); 
			pSrc.setName (new String(portName));
			pSrc.setFunc (new String(hSrcA.getFunc ()));
			hsdfGraph.add (pSrc);
			setPortRate (hsdfGraph, hSrcA.getName (), portName, Integer.toString (portRate));
			
			// Create Port on Destination Node	    			
			Port pSnk = new Port (Port.DIR.IN);
			portName = "p"+hDstA.getName ()+"_"+Integer.toString (dstPortCount);
			pSnk.setName (new String(portName));
			pSnk.setFunc (new String(hDstA.getFunc ()));
			hsdfGraph.add (pSnk);
			setPortRate (hsdfGraph, hDstA.getName (), portName, Integer.toString (portRate));
            
            PortRef src = new PortRef ();
            src.setActor (hSrcA);
            src.setPort (pSrc);	                
            
            PortRef snk = new PortRef ();
            snk.setActor (hDstA);
            snk.setPort (pSnk);           
            
            Channel prodcons = new Channel ();	                
            hsdfGraph.add (prodcons);
            prodcons.bind (src, snk);
            prodcons.setInitialTokens (initialTokens);
            prodcons.setTokenSize (Integer.parseInt (result[3]));
            prodcons.setName (new String(result[4]));
		}
		
		return hsdfGraph;
	}	
	
	/**
	 * Convert SDF to HSDF graph with all rates equal to 1.
	 * 
	 * @param sdfGraph input SDF graph.
	 * @return HSDF graph with unit rates
	 */
	public Graph convertSDFtoHSDF (Graph sdfGraph)
	{
		Iterator<Actor> iterActor = sdfGraph.getActors ();
		Graph hsdfGraph = new Graph ();
		
		GraphExpressions expressions = new GraphExpressions ();
    	expressions.parse (sdfGraph);
		Solutions sdfSolutions = new Solutions ();
		sdfSolutions.setThrowExceptionFlag (false);
		sdfSolutions.solve (sdfGraph, expressions);
		
		while (iterActor.hasNext ())
		{
			Actor actor = iterActor.next ();
			for (int i=0;i<sdfSolutions.getSolution (actor).returnNumber ();i++)
			{
				Actor a = new Actor ();
	    		a.setFunc (new String(actor.getFunc ()));
	    		a.setName (new String(actor.getName () + "_" + Integer.toString (i)));
	    		a.setExecTime (actor.getExecTime ());
	    		a.setActorType(actor.getActorType());
	    		
	    		hsdfGraph.add (a);	    		
			}
		}
			
		Iterator<Channel> chnnlIter = sdfGraph.getChannels ();
		while (chnnlIter.hasNext ())
		{
			Channel chnnl = chnnlIter.next ();
			Actor srcActor = chnnl.getLink (Port.DIR.OUT).getActor ();
			Actor dstActor = chnnl.getLink (Port.DIR.IN).getActor ();
			
			int nA = Integer.parseInt (chnnl.getLink (Port.DIR.OUT).getPort ().getRate ()); // gSrcP->getRate ();
			int nB = Integer.parseInt (chnnl.getLink (Port.DIR.IN).getPort ().getRate ()); // gDstP->getRate ();
	        int qA = sdfSolutions.getSolution (srcActor).returnNumber (); // repetitionVector[gSrcA->getId ()];
	        int qB = sdfSolutions.getSolution (dstActor).returnNumber (); //repetitionVector[gDstA->getId ()];
	        int d = chnnl.getInitialTokens (); // gC->getInitialTokens ();
	        
	        int count=0;
	        
	        for (int i = 1; i <= qA; i++)
	        {   
	            // Get pointer to source actor
	        	Actor hSrcA = hsdfGraph.getActor (srcActor.getName () +"_" +Integer.toString (i-1));	            
	    
	            for (int k = 1; k <= nA; k++)
	            {
	            	int l = 1 + (d + (i-1)*nA + k - 1) % (nB*qB);
	                int j = 1 + (int)Math.floor ((double)((d + (i-1) * nA + k - 1) %  (nB*qB)) / (double)(nB));
	                
	                Actor hDstA = hsdfGraph.getActor (dstActor.getName () +"_"+ Integer.toString (j-1));
	                
	                // System.out.println ("conSrc : "+ hSrcA.getName () +" Dst : "+ hDstA.getName ());
	                
	                // Create Port on Source Node
	                Port pSrc = new Port (Port.DIR.OUT);	    			
	    			pSrc.setName (new String(hSrcA.getName ()+hDstA.getName ()+"_out"+Integer.toString (k-1)));
	    			pSrc.setFunc (new String(hSrcA.getFunc ()));
	    			hsdfGraph.add (pSrc);
	    			setPortRate (hsdfGraph, hSrcA.getName (), hSrcA.getName ()+hDstA.getName ()+"_out"+Integer.toString (k-1), "1" );
	    			
	    			// Create Port on Destination Node	    			
	    			Port pSnk = new Port (Port.DIR.IN);
	    			pSnk.setName (new String(hSrcA.getName ()+hDstA.getName ()+"_in"+Integer.toString (l-1)));
	    			pSnk.setFunc (new String(hDstA.getFunc ()));
	    			hsdfGraph.add (pSnk);
	    			setPortRate (hsdfGraph, hDstA.getName (), hSrcA.getName ()+hDstA.getName ()+"_in"+Integer.toString (l-1), "1" );
	                
	                PortRef src = new PortRef ();
	                src.setActor (hSrcA);
	                src.setPort (pSrc);	                
	                
	                PortRef snk = new PortRef ();
	                snk.setActor (hDstA);
	                snk.setPort (pSnk);
	                
	                // Initial tokens
	                int t = (int) Math.floor ((double)(d + (i-1)*nA + k-1) /(double)(nB*qB));
	                
	                Channel prodcons = new Channel ();	                
	                hsdfGraph.add (prodcons);
	                prodcons.bind (src, snk);
	                prodcons.setInitialTokens (t);
	                prodcons.setTokenSize (chnnl.getTokenSize ());
	                prodcons.setName (new String(chnnl.getName () + "_" + Integer.toString (count++)));
	            }
	        }        
		}		
		return hsdfGraph;
	}
}
