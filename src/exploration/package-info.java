/**
 * package contains classes for performing design space exploration.
 * 
 * This set of code contains all the generic classes required to perform
 * design space exploration. We can implement different algorithms such as 
 * binary search, grid-based exploration in this package. Each sub-package 
 * is described as follows :
 * 
 *  interfaces : This sub-package contains different interfaces for example latency interface, 
 *  which should be implemented by different solvers. The advantage is that, the design space
 *  exploration algorithms then can use any Solver without having to modify their code. For example
 *  mutual exclusion solver implements {@link exploration.interfaces.oneDim.LatencyConstraints} interface.
 *  It defines standard functions which should be implemented. For example, here it should be 
 *  getLatency() to get latency from the model and generateLatencyConstraint() to generate a latency constraint
 *  for design space exploration.
 *  
 *  parameters : This sub-package contains parameters classes which are again used for design space
 *  exploration. For example, we want to perform one dimensional exploration for latency parameter, the
 *  we define "LatencyParams" class which uses LatencyConstraints solver to perform design space exploration.
 *  It implements the functions which are required by the any design space exploration algorithm. 
 *  
 *  oneDimensionExploration : This sub-package contains methods to perform exploration for a single dimension.
 *  The other dimensions are either constant or not considered.
 *  
 *  paretoExploration : This sub-package contains methods to perform multi-dimension exploration. For example
 *  grid based algorithm.
 *  
 * @author Pranav Tendulkar
 * 
 */

package exploration;