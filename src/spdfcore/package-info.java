/**
 * package contains components to build an application graph.
 * 
 * The intention of these components was to support SPDF dataflow 
 * model which has support for dynamic rates and other things.
 * However, we later started to use the same code for Split-Join and
 * SDF graphs which had static rates. So the core functionality still
 * supports SPDF model. However the other infrastructure built around
 * it is considering SDF model. 
 * 
 * Terminology of components in this package.
 * 
 *  A link between a channel and an actor what is usually called a port.  
 *  In our case, we reserve the term 'port' for class of link sharing the same
 *  name, rate and function within the given actor type. 
 *  Thus a link is instantiation of a port.	
 * 
 * @author Pranav Tendulkar
 * 
 */
package spdfcore;