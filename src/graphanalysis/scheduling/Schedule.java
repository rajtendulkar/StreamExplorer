package graphanalysis.scheduling;

import java.util.*;

/**
 * Some unused schedule class 
 * 
 * @author Pranav Tendulkar
 */



public class Schedule
{	
	private String scheduleInfixStr = null;
	private String schedulePostfixStr = null;
	private Node scheduleRootNode = null; 
	private int primeCount = 0;
	
	class Node
	{
		private String nodeValue = null;
		private Node leftNode = null;
		private Node rightNode = null;
		
		private Node getLeft   ()		{ return leftNode; }
		private Node getRight  ()		{ return rightNode; }
		private String getData ()		{ return nodeValue; }
	}
	
	private int getPrimeNumber ()
	{
		int[] primeNumbers = 
		{ 2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43,   
		  47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, 101, 103, 
		  107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163, 
		  167, 173, 179, 181, 191, 193, 197, 199, 211, 223, 227, 
		  229, 233, 239, 241, 251, 257, 263, 269, 271, 277, 281};
		
		return primeNumbers[primeCount++];
	}
	
	/**
	 * Compare two schedules.
	 * Currently I take two schedules, replace the actor instances in the
	 * post-fix expressions with prime numbers and evaluate these 
	 * expression to generate a unique number. Then we can compare
	 * if this number is equal or not.
	 * */	
	public boolean compareTo (Schedule anotherSchedule)
	{
		String sched1 = schedulePostfixStr;
		String sched2 = anotherSchedule.schedulePostfixStr;
		// Reset the prime count.
		primeCount = 0;
		
		HashMap<String, Integer> map = new HashMap<String, Integer>(); 
		
		// Replace all the actors with prime numbers
		// for example replace A = 1, B = 2 , C = 5, D = 7 so on...
		
		StringTokenizer tokenizer = new StringTokenizer (sched1," ");
		while (tokenizer.hasMoreTokens ()) 
		{
			String token = tokenizer.nextToken ().trim ();
			// Do nothing.
			if (token.equals ("*") || token.equals ("+") || token.equals ("-") || token.equals ("/"));
			else
			{
				try
				{
					Integer.parseInt (token);
				}
				catch (NumberFormatException e) 
				{
					int prime = getPrimeNumber ();
					// It is not a number. We have to replace them all with some number.
					map.put (token, prime);
					sched1.replaceAll (token, Integer.toString (prime));					
				}
			}			
		}
		
		// Replace the same actors in second string with same primes.
		Iterator<String> strIter = map.keySet ().iterator ();
		while (strIter.hasNext ())
		{
			String str = strIter.next ();			
			sched2.replaceAll (str, Integer.toString (map.get (str)));
		}
		
		// Evaluate the String
		int sched1Eval = evaluatePostfix (sched1);
		int sched2Eval = evaluatePostfix (sched2);
		
		if (sched1Eval == sched2Eval)
			return true;
		else
			return false;		
	}
	
	private int evaluatePostfix (String x)
	{
		Stack <Integer> intStack = new Stack <Integer>();
		StringTokenizer tokenizer = new StringTokenizer (x," ");
		while (tokenizer.hasMoreTokens ()) 
		{
			String token = tokenizer.nextToken ().trim ();			
			
			if (token.equals ("+"))
			{
				int result = intStack.pop ()+intStack.pop ();
				intStack.push (result);
			}
			else if (token.equals ("-"))
			{
				int u = intStack.pop ();
				int result=intStack.pop () - u;				
				intStack.push (result);
			}
			else if (token.equals ("*"))
			{
				int result = intStack.pop ()*intStack.pop ();
				intStack.push (result);
			}
			else if (token.equals ("/"))
			{
				int u = intStack.pop ();
				int result=intStack.pop () / u;				
				intStack.push (result);				
			}
			else
			{
				try 
				{
					int num = Integer.parseInt (token);
					// Surely this is an integer number.
					intStack.push (num);					
				} 
				catch (NumberFormatException e) { return -1; }				
			}			
		}		
		return intStack.pop ();
	}
	
	@Override
	public String toString ()
	{
		return toString (scheduleRootNode);
	}
	
	/**
	 * Returns a one-line string representation of the given subtree.
	 */
	private String toString (Node tree) 
	{
		// If node does not exist.
		if (tree == null) 
		{
			return "";
		}
		// If this is a leaf-node
		else if (tree.getLeft () == null && tree.getRight () == null) 
		{
			return ("[" + tree.nodeValue + "]");
		}
		else 
		{
			String result = "(";
			result += toString (tree.getLeft ());
			result += " " + tree.getData () + " ";
			result += toString (tree.getRight ());
			result += ")";
			return result;
		}
	}
	
	/**
	 * Construct the Schedule Tree from the PostFix Schedule 
	 * expression. 
	 * */
	private void constructScheduleTreeFromPostfix (String x)
	{
		Stack <Node> nodeStack = new Stack <Node>();
		StringTokenizer tokenizer = new StringTokenizer (x," ");
		while (tokenizer.hasMoreTokens ()) 
		{
			String token = tokenizer.nextToken ().trim ();
			//System.out.println ("Token : " + token);			
			if (token.equals ("+") || token.equals ("-") || token.equals ("*") || token.equals ("/"))
			{
				Node node = new Node ();
				node.nodeValue = new String (token);				
				node.leftNode = nodeStack.pop ();
				node.rightNode = nodeStack.pop ();
				
				nodeStack.push (node);
			}			
			else
			{				
				Node node = new Node ();
				node.nodeValue = new String (token);				
				
				nodeStack.push (node);								
			}			
		}
		scheduleRootNode = nodeStack.pop ();		
	}
	
	
	private static int priority (Object x)
	{
		if (x.equals ('+')||x.equals ('-'))
			return 1;
		else if (x.equals ('*')||x.equals ('/'))
			return 2;
		else
			return 0;
	}
	
	// Convert the Infix schedule string to 
	// postfix for easier parsing.
	private String InfixToPostfix (String x)
	{
		String output="";
		Stack<Character> S=new Stack<Character>();
		
		for (int i=0;i<x.length ();i++)
		{
			char c=x.charAt (i);

			if (c==('+')||c==('*')||c==('-')||c==('/'))
			{
				while (!S.empty () && priority (S.peek ())>= priority (c))
					output += S.pop () + " ";
				S.push (c);
			}
			else if (c=='(')
			{
				S.push (c);
			}
			else if (c==')')
			{
				while (!S.peek ().equals ('('))
					output+=S.pop () + " ";
				S.pop ();
			}
			else
			{
				output+=c;
				
				if (i<x.length () && Character.isLetterOrDigit (x.charAt (i+1)) == true)
				{
					while (i<x.length () && Character.isLetterOrDigit (x.charAt (i+1)) == true)
					{						
						output += x.charAt (i+1);
						i++;
					}
				}
				output += " ";
			}
		}
			
		while (!S.empty ())
			output+=S.pop ();
		
		return output;
	}
	
	/** 
	 * This function will check if the parentheses in the expression have
	 * matching number of open and closed brackets.
	 */
	private boolean validateParantheses (String string)
	{			
		int count = 0;

		for (int i=0;i<string.length ();i++)
		{
			char c=string.charAt (i);
			if (c=='(')
				count++;
			else
			{
				if (c==')')
					if (count == 0)
						return false;
					else
						count--;
			}
		}
		
		if (count != 0)
			return false;
		else
			return true;
	}
	
	public void parseScheduleString (String schedule)
	{
		scheduleInfixStr = new String (schedule);
		
		if (validateParantheses (scheduleInfixStr) == false)
		{
			throw new RuntimeException ("Parantheses of the schedule string have a mismatch !" +
					"Recheck the schedule String : " + schedule);
		}
		
		schedulePostfixStr = InfixToPostfix (scheduleInfixStr);
		constructScheduleTreeFromPostfix (schedulePostfixStr);		
	}
}
