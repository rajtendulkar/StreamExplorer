package exploration.oneDimensionExploration;

import java.io.*;
import java.util.*;
import solver.Z3Solver.SatResult;
import exploration.ExplorationParameters;
import exploration.Explorer;

/**
 * @author Pranav Tendulkar
 *
 */
public class BinarySearchOneDim extends Explorer
{
	/**
	 * List of SAT points from the exploration
	 */
	private List<Integer> satPointsList;
	
	/**
	 * List of UNSAT points from the exploration
	 */
	private List<Integer> unsatPointsList;
	
	/**
	 *  List of TIMED OUT points from the exploration
	 */
	private List<Integer> timedOutPointsList;
	
	/**
	 *  List of models for each SAT point
	 */
	private List<Map<String, String>> satPointsModelList;
	
	/**
	 * Exploration stops on timeout if this flag is set to true
	 */
	private boolean stopOnTimeout=false;
	
	/**
	 * Initialize Binary search explorer for one dimension 
	 * @param outputDirectory output directory to put log files
	 * @param perQueryTimeOutSeconds time out for each SMT query in seconds
	 * @param totalTimeOutInSeconds global time out value in seconds
	 * @param explorationParams exploration parameters
	 */
	public BinarySearchOneDim (String outputDirectory, int perQueryTimeOutSeconds, int totalTimeOutInSeconds, 
			ExplorationParameters explorationParams)
	{
		super (outputDirectory, 1, perQueryTimeOutSeconds, totalTimeOutInSeconds, explorationParams);
		System.out.println ("MinimizeOneDimensionCost :: Stop on timeout value : " + stopOnTimeout);
		satPointsList = new ArrayList<Integer>(); 
		unsatPointsList = new ArrayList<Integer>();
		timedOutPointsList = new ArrayList<Integer>();
		satPointsModelList = new ArrayList<Map<String, String>>();
	}
	
	/**
	 * Get List of SAT points
	 * @return list of SAT points
	 */
	public List<Integer> getSatPoints () { return cloneList(satPointsList); }
	
	/**
	 * Get List of UNSAT points
	 * @return list of UNSAT points
	 */
	public List<Integer> getUnsatPoints () { return cloneList (unsatPointsList); }
	
	/**
	 * Get List of TIMED OUT points
	 * @return list of TIMED OUT points
	 */
	public List<Integer> getTimedoutPoints () { return cloneList (timedOutPointsList); }
	
	/**
	 * Gets model of the minimum SAT value found during exploration.
	 * @return model of minimum SAT point
	 */
	public Map<String, String> getLeastSatPointModel ()
	{
		int leastPoint = getLeastSatPoint ();
		for(int i=0;i<satPointsList.size();i++)
		{
			if(satPointsList.get(i) == leastPoint)
			{
				Map<String, String> result = new HashMap<String, String>();
				Map<String, String> tempMap = satPointsModelList.get(i);
				for(String key : tempMap.keySet())
					result.put(key, tempMap.get(key));
				return result;
			}
		}
		
		return null;
	}
	
	/**
	 * Convert the string in the model file to List of model maps.
	 * 
	 * @param fileName name of the model file
	 * @param satPointsModelList list where SAT models will be stored.
	 */
	private void readModelFile (String fileName, List<Map<String, String>> satPointsModelList)
	{
		FileInputStream fstream;
		try 
		{
			fstream = new FileInputStream(fileName);		
			BufferedReader br = new BufferedReader(new InputStreamReader(fstream));

			String strLine;
			int lineNum=0;

			//Read File Line By Line
			while ((strLine = br.readLine()) != null)   
			{
				// add to the model.
				if(lineNum % 2 != 0)
				{
					Map<String, String> model = new HashMap<String, String>();					
					strLine = strLine.replace("{", "");
					strLine = strLine.replace("}", "");
					strLine = strLine.replaceAll("( )+", "");
					String split[] = strLine.split(",");
					
					for(int i=0;i<split.length;i++)
					{
						String subsplit[] = split[i].split("=");
						model.put(subsplit[0], subsplit[1]);
					}
					satPointsModelList.add(model);
				}
				lineNum++;
			}
			//Close the input stream
			br.close();
		
		} catch (FileNotFoundException e) { e.printStackTrace(); } 
		  catch (IOException e) { e.printStackTrace(); }
	}
	
	/**
	 * Read a log file of exploration to read SAT / UNSAT / TIMED OUT points.
	 * 
	 * @param fileName name of the file to be read
	 * @param paramStrings String in the file between the information field
	 * @param listToAdd list where all the read points will be stored
	 */
	private void readFileToArray (String fileName, String paramStrings[],  List<Integer> listToAdd)
	{
		FileInputStream fstream;
		try
		{			
			fstream = new FileInputStream(fileName);
			
			BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
			
			String strLine;

			//Read File Line By Line
			while ((strLine = br.readLine()) != null)   
			{
				for (int i=0;i<dimensions;i++)
					strLine = strLine.replace(paramStrings[i], "");
				// Remove unwanted white spaces.		
				strLine = strLine.replaceAll("( )+", " ");
				strLine = strLine.trim();				
				// split the string to numbers
				String split[] = strLine.split(" ");
				
				int point = Integer.parseInt(split[0]);				
				listToAdd.add(point);
			}

			//Close the input stream
			br.close();			
			
		} catch (FileNotFoundException e) { e.printStackTrace(); } 
		  catch (IOException e) { e.printStackTrace(); }
	}
	
	/**
	 * If we want to continue a previous exploration, we could just read the log file and 
	 * continue from the point where the exploration stopped. If we stopped in middle of a
	 * SMT query, of course that query is lost.
	 * 
	 * @param resultsDirectory the directory where all the log files are stored.
	 */
	public void readExploredPoints (String resultsDirectory) 
	{	
		String paramStrings[] = new String[dimensions];
		
		for (int i=0;i<dimensions;i++)
			paramStrings[i] = explParams.getConstraintName (i) + " :";
		
		// read all the SAT points.
		readFileToArray (resultsDirectory + satPointsFileName, paramStrings, satPointsList);
		// read all the UNSAT points.
		readFileToArray (resultsDirectory + unsatPointsFileName, paramStrings, unsatPointsList);
		// read all the TIMED OUT points.
		readFileToArray (resultsDirectory + timedOutPointsFileName, paramStrings, timedOutPointsList);
		// read all SAT point models.
		readModelFile   (resultsDirectory +  modelFileName, satPointsModelList);	
	}
	
	/**
	 * Get the minimum value of SAT point that was found during exploration.
	 * 
	 * @return minimum value of SAT point
	 */
	public int getLeastSatPoint () 
	{
		int leastPoint = Integer.MAX_VALUE;
		for(int i=0;i<satPointsList.size();i++)
		{
			if(satPointsList.get(i) < leastPoint)
				leastPoint = satPointsList.get(i);
		}
		return leastPoint;
	}
	
	/**
	 * Create a new instance of the list.
	 * 
	 * @param list list to be copied
	 * @return new instance of the list
	 */
	private List<Integer> cloneList (List<Integer> list)
	{
		List<Integer> result = new ArrayList<Integer>();
		for(int i=0;i<list.size();i++)
			result.add(list.get(i));
		return result;
	}
	
	
	/**
	 * Perform one dimension binary search exploration
	 */
	public void explore ()
	{
		int lowerBound[] = explParams.getLowerBounds ();
		int upperBound[] = explParams.getUpperBounds ();
		int query[] = new int [1];
		
		if (lowerBound.length != 1 || upperBound.length != 1)
			throw new RuntimeException ("This is not a one dimensional exploration !");
		
		explParams.pushSolverContext ();
		
		while ((lowerBound[0] <= upperBound[0]) && ((totalExplTime/1000) <= totalQueryTimeOutInSeconds))
		{
			query[0] = lowerBound[0] + ((upperBound[0] - lowerBound[0]) / 2);
			
			SatResult result = smtQuery (query);
			if (result == SatResult.SAT)
			{
				int [] queryModel = explParams.getCostsFromModel ();
				satPointsList.add (queryModel[0]);
				
				Map<String, String> model = explParams.getModelFromSolver ();
				satPointsModelList.add (model);
				
				upperBound[0] = queryModel[0]-1;
			}
			else if (result == SatResult.UNSAT)
			{
				unsatPointsList.add (query[0]);
				lowerBound[0] = query[0]+1;
			}
			else if ((result == SatResult.TIMEOUT) || (result == SatResult.UNKNOWN))
			{
				timedOutPointsList.add (query[0]);
				lowerBound[0] = query[0]+1;
				if (stopOnTimeout == true)
					break;
			}
			else
				throw new RuntimeException ("Uninterpreted Result : " + result.toString ());
			
			explParams.popSolverContext (1);
			explParams.pushSolverContext ();
			System.out.println();
		}
		
		// Write the pareto points file
		String pointString = explParams.getConstraintName(0)  + " : " + getLeastSatPoint() + " ";		
		outputToFile (paretoPointsfile, pointString + "\n");		
		
		System.out.println ("Finished Exploration in " + totalExplTime/1000 + " seconds");
	}
}
