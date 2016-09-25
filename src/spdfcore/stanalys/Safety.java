package spdfcore.stanalys;

import spdfcore.*;

import java.util.*;

/**
 *  Performs the check for the safety criterion
 *
 */
public class Safety {
	//--- data -------
	private boolean throwException = true;	
	//----------------
	
	/**
	 * check parameter modification safety according to the safety criterion;
	 * this ensures that the graph has a periodic schedule.
	 *  
	 * @param paramcomm - complete paramcomm object (where modifiers and users were established)  
	 * @param solutions - balance equation solution
	 * @return -- problematic parameter if unsafe (when throwException==false); *null* if safe.
	 */
	public String check (ParamComm paramcomm, Solutions solutions) {
		Iterator<String> modifiedParams = paramcomm.getModifiedParameters ();
		while (modifiedParams.hasNext ()) {
			String parameter = modifiedParams.next ();
			Actor periodActor = paramcomm.getPeriodActor (parameter);
			Expression solPeriodActor = solutions.getSolution (periodActor);
			
			Iterator<Actor> users = paramcomm.getUserSet (parameter).iterator ();
			while (users.hasNext ()) {
				Actor user = users.next ();
				Expression solUser = solutions.getSolution (user);
								
				//--- safety criterion: solUser is a multiple of solPeriodActor --
				Expression gcd = Expression.gcd (solPeriodActor, solUser);
				if (!gcd.equals (solPeriodActor)) {
					if (throwException) {
						throw new RuntimeException ("Safety violation:" +
							" Parameter: \"" + parameter + "\"" +
							" User: " + user.getName () + 
							" User solution: " + solUser +
							" is not a multiple of " +
							" periodActor solution: " + solPeriodActor							
							);
					}
					return parameter; // report a problematic parameter
				}
			}
		}
		return null; // no problematic parameter
	}

}
