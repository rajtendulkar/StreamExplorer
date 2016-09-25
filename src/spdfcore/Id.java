package spdfcore;

/**
 *   Identifies a named entity: actor or port.
 *   It can be used as a map key.
 *   It contains three constant fields:
 *       <1> function (actor type, e.g "Idct")
 *       <2> name (actor/port name, e.g. "idct", "in" )    
 *       <3> graph.
 */
public class Id {

    private enum PROP {
      FUNC (0), NAME (1);
      PROP (int value) { this.value = value; }
      private final int value;
      int value () { return value; }
    }

    //-------- data -------
    private String id[] = new String[2];
    private Graph graph;
    //------------------
    //
    @Override
	public int hashCode () {
        return getName ().hashCode () + getFunc ().hashCode () + getGraph ().hashCode ();
    }

    @Override
	public boolean equals (Object obj) {
        Id other = (Id) obj;
        return other.getGraph ().equals ( this.getGraph ()) &&
        	   other.getName ().equals (  this.getName ())  &&
               other.getFunc ().equals (  this.getFunc ());
    }

    private void setProp (PROP prop, String txt) {
            if (id[prop.value ()]!=null)
              throw new RuntimeException ("Cannot change " + prop  + " of " + this.getClass ());
            id[prop.value ()] = txt;
        }
    
    private String getProp (PROP prop) {
      if (id[prop.value ()]==null)
              throw new RuntimeException ("Non-initialized property " + prop  + " of " + this.getClass ());
      return id[prop.value ()];
    }

    /**
     * Set the name of actor/port/modified parameter.
     * Allowed to be done only once per object. 
     * @param txt
     */
    public void setName (String txt) {
            setProp ( PROP.NAME, txt);
        }

    /**
     * Specify the 'function'(i.e. actor type) of the give actor/port/modifier. 
     * Allowed to be done only once per object. 
     * @param txt
     */
    public void setFunc (String txt) {
            setProp ( PROP.FUNC, txt);
        }

    /**
     * return the name of the port/actor/modified parameter  
     */
    public String getName () {
      return getProp (PROP.NAME);
    }

    /**
     * return the function (i.e. actor type) of the port/actor/modifier  
     */
    public String getFunc () {
      return getProp (PROP.FUNC);
    }

    /**
     * Called by the graph when the object is getting assigned to the graph
     * @param g
     */
    void setGraph (Graph g) {
        if (graph!=null)
            throw new RuntimeException ("setGraph () should be called only once!");
        graph=g;
    }

    /**
     * @return the graph to which the given actor/port/modified parameter 
     *  was added 
     */
    public Graph getGraph () {
        return graph;
    }

   /**
    * @return Id with the same name and func and graph
    */
   public Id cloneId () {
	   Id copy = new Id ();
	   if (id[PROP.NAME.value ()]!=null)
		   copy.setName (id[PROP.NAME.value ()]);
	   if (id[PROP.FUNC.value ()]!=null)
		   copy.setFunc (id[PROP.FUNC.value ()]);
	   if (graph!=null)
		   copy.setGraph (graph);
	   return copy;
   }
   
   @Override
public String toString () {
        return getName () + "@" + getFunc ();
    }
 }
