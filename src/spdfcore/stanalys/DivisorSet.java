package spdfcore.stanalys;

import java.util.*;

/**
 *  implements an expression c1^w1 * c2^w2....*p1^u1*p2^u2*...
 *  where ck, pk are numbers or symbolic parameters 
 *  and   wk, uk are powers 
 */
final public class DivisorSet  implements Cloneable {
	private HashMap<String, Integer> multiset = new HashMap<String, Integer>(); 

	public int getPower (int divisor ) {
		return getPower (new Integer (divisor).toString ());
	}
	
	public int getPower (String divisor ) {
		Integer power = multiset.get (divisor);
		if (power==null) 
			return 0;
		else
			return power.intValue ();
	}

	public void setPower (int divisor, int power ) {
		setPower (new Integer (divisor).toString (), power);
	}

	public void setPower (String divisor, int power ) {
		if (power<0)
			throw new RuntimeException ("Negative power!");
		else if (power==0)
			multiset.remove (divisor);
		else
			// it is safe to use String as key, no need to make a copy of it,
			// because String is not a mutable object (contents cannot change)
			multiset.put (divisor, new Integer (power));
	}
	
	public void incrPower (int divisor, int powerIncrease) {
		incrPower (new Integer (divisor).toString (), powerIncrease); 
	}

	public void incrPower (String divisor, int powerIncrease) {
		int currentPower = getPower (divisor);
		int newPower= currentPower + powerIncrease;
		setPower (divisor, newPower);
	}

	public Iterator<Map.Entry<String, Integer>> getIterator () {
		return multiset.entrySet ().iterator ();
	}
	
	// increment powers in this set with the powers from another set
	public void incrPowerWith (DivisorSet otherDivisorSet) {
		incrPowerWith (otherDivisorSet, false);
	}

	// increment or decrement powers in this set with the powers from another set
	public void incrPowerWith (DivisorSet otherDivisorSet, boolean negative) {
		Iterator<Map.Entry<String, Integer>> otherEntries = otherDivisorSet.getIterator ();
		while (otherEntries.hasNext ()) {
			Map.Entry<String, Integer> otherEntry = otherEntries.next ();
			String otherDivisor = otherEntry.getKey ();
			int      otherPower = otherEntry.getValue ().intValue ();
			if (negative)
				otherPower = -otherPower;
			this.incrPower (otherDivisor, otherPower);
		}
	}
	
	static private int min (int a, int b) {
		if (a<b)
			return a;
		else 
			return b;
	}
	
	// multiset intersections operation:
	//   new_power (divisor) = min (  power1 (divisor), power2 (divisor) )
	// if DivisorSet are primary then this implements "greatest common divisor"	
	public void intersectionWith (DivisorSet otherDivisorSet) {
		Iterator<Map.Entry<String, Integer>> otherEntries = otherDivisorSet.getIterator ();
		Iterator<Map.Entry<String, Integer>> myEntries =           this.getIterator ();
		
		// minimize w.r.t. "other"
		while (otherEntries.hasNext ()) {
			Map.Entry<String, Integer> otherEntry = otherEntries.next ();
			String otherDivisor = otherEntry.getKey ();
			int      otherPower = otherEntry.getValue ().intValue ();
			int myPower = this.getPower (otherDivisor);
			int newPower = min (myPower, otherPower);
			setPower (otherDivisor, newPower);
		}
		
		// remove the elements not present in "other"
		LinkedList<String> divisorsToRemove = new LinkedList<String>(); 
		while (myEntries.hasNext ()) {
			Map.Entry<String, Integer> myEntry = myEntries.next ();
			String myDivisor = myEntry.getKey ();
			int otherPower = otherDivisorSet.getPower (myDivisor);
			if (otherPower==0)
				// cannot remove elements from map while iterating there
				divisorsToRemove.add (myDivisor);
		}
		
		// now do the removal
		Iterator<String> removedDivisorIter = divisorsToRemove.iterator (); 
		while (removedDivisorIter.hasNext ()) {
			String myDivisor = removedDivisorIter.next (); 
			this.multiset.remove (myDivisor);
		}
	}
	
	// need to clone all mutable fields.
	// "multiset" lookup table is mutable, so clone it.
	@Override
	@SuppressWarnings ("unchecked")
	public Object clone () {
		DivisorSet copy = new DivisorSet ();
		copy.multiset = (HashMap<String, Integer>) this.multiset.clone ();
		return copy;
	}
	
	// Call the HashMap's "equals" method, which calls "equals" for 
	// all keys and values
	@Override
	public boolean equals (Object obj) {
		DivisorSet other = (DivisorSet) obj;
		return other.multiset.equals (this.multiset);
	}
	
	@Override
	public int hashCode () {
		return multiset.hashCode ();
	}	
}
