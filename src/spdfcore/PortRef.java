package spdfcore;

/**
 * Port reference is used to bind channel,
 * port and actor together.
 * 
 * @author Peter Poplavko
 *
 */
public class PortRef 
{

	/**
	 * Actor to which the port belonge to
	 */
	private Id actorId = new Id (); // misusing Id just to store name
	
	/**
	 * Port information
	 */
	private Id portId  = new Id ();

	/**
	 * Set name of the port
	 * @param name name of the port
	 */
	public void setPortName (String name) {
		portId.setName (name);
	}

	/**
	 * Set name of the actor to which the port belongs to
	 * @param name name of the actor
	 */
	public void setActorName (String name) {
		actorId.setName (name);
	}

	/**
	 * Get name of the port
	 * 
	 * @return name of the port
	 */
	public String getPortName () {
		return portId.getName ();
	}

	/**
	 * Get name of the actor to which the port belongs to.
	 * 
	 * @return name of the actor
	 */
	public String getActorName () {
		return actorId.getName ();
	}

	/**
	 * Set actor to which this port belongs to.
	 * 
	 * @param actor actor instance
	 */
	public void setActor (Actor actor) {
		setActorName (actor.getName ());
	}

	/**
	 * Set port name to which this port reference points to
	 * 
	 * @param port port instance
	 */
	public void setPort (Port port) {
		setPortName (port.getName ());
	}
}

