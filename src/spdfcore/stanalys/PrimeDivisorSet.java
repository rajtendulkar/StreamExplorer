package spdfcore.stanalys;

//import java.util.*;

public class PrimeDivisorSet {
	
	// returns the set of prime DivisorSet and their powers, e.g.:
	// for 440
	//    "2"   -- power 3 
	//    "11"  -- power 1
	//    "5"   -- power 1
	public static DivisorSet getDivisorSet (int num) {
		if (num<=1)
			throw new RuntimeException ("Invalid number");
		DivisorSet primeDivisorSet = new DivisorSet ();
		while (num>1) {
			int div = getMinimalPrimeDivisor (num);
			num = num/div;
			primeDivisorSet.incrPower (div,1);			
		}
		return primeDivisorSet; 
	}
	
	public static int getMinimalPrimeDivisor (int num) {
		if (num<=1)
			throw new RuntimeException ("Invalid number");
		for (int i = 2; i<=num/2; i++) {
			if ((num % i) == 0)
				return i;
		}
		return num;
	}

}
