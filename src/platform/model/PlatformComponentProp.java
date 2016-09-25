package platform.model;

/**
 * Common Properties of the platform model components.
 * 
 * @author Pranav Tendulkar
 *
 */
public class PlatformComponentProp
{
	/**
	 * Name of the Component
	 */
	private final String name;
	
	/**
	 * Id of the Component 
	 */
	private final int id;
	
	/**
	 * Initializes Component properties.
	 * @param name name of the component
	 * @param id id of the component
	 */
	PlatformComponentProp (String name, int id)
	{
		this.name = new String (name);
		this.id = id;
	}
	
	/**
	 * Gets the name of the Component.
	 * @return name name of the component.
	 */
	public String getName () { return name; }
	
	/**
	 * Gets the Id of the Component.
	 * @return Id of the component.
	 */
	public int getId () { return id; }
	
	@Override
	public String toString ()
	{
		return name;
	}
}
