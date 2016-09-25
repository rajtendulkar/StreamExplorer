package exploration.paretoExploration.distanceexploration;

public class Knee
{
	private Point g;				// g in paper;
	private double h[];
	private Point b;
	private double r;
	private Knee descendants[];
	private Point unsatGen[];		// s1...sk
	
	public Knee (Point p)
	{
		int dimensions = p.dim();
		g = new Point(p);
		h = new double[dimensions];
		descendants = new Knee[dimensions];
		unsatGen = new Point[dimensions];
		
		for(int i=0;i<dimensions;i++)
			descendants[i] = null;
	}
	
	public Knee (int dimensions, double max)
	{
		g = new Point (dimensions);
		h = new double[dimensions];
		for(int i=0;i<dimensions;i++)
			h[i] = 0.0;
		
		descendants = new Knee[dimensions];
		unsatGen = new Point[dimensions];
		for(int i=0;i<dimensions;i++)
		{
			descendants[i] = null;
			unsatGen[i] = new Point(dimensions, max);
			unsatGen[i].set(i, 0);
		}
	}
	
	@Override
	public String toString()
	{
		return g.toString();
	}
	
	boolean checkGenerators()
	{
		//check generators/knee coordinates consistency

	    for(int i=0; i<g.dim(); ++i)
	    {
	        double min=Double.MAX_VALUE;
	        for(int j=0; j<g.dim(); ++j)
	        {
	            if (unsatGen[j].get(i) < min)
	                min = unsatGen[j].get(i);
	        }
	        
	        if(min != g.get(i))
	            return false;
	    }
	    
	    return true;
	}
	
	public int numUnsatGen()
	{
		int size=0;
		for(int i=0;i<g.dim();i++)
			if(unsatGen[i] != null) size++; 
		return size;
	}
	
	public Point getUnsatGen(int k)
	{
		return unsatGen[k];
	}
	
	public void addUnsatGen(int dimension, Point p)
	{
		unsatGen[dimension] = p;
	}
	

	public Point getG()
	{
		return g;
	}
	
	public void setG(Point g)
	{
		this.g = g;
	}
	
	public int numDescendants ()
	{
		int count = 0;
		for(int i=0;i<g.dim();i++)
			if(descendants[i] != null) count++;
		return count;
	}
	
	public void addDescendant (int dim, Knee kneeNode)
	{
		descendants[dim] = kneeNode;
	}
	
	public void removeDescendant (int dim)
	{
		descendants[dim] = null;
	}
	
	public Knee getDescendant (int dim)
	{
		return descendants[dim];
	}
	
	public double getH(int dim)
	{
		return h[dim];
	}

	public void setH(double h, int dim)
	{
		this.h[dim] = h;
	}

	public Point getB()
	{
		return b;
	}

	public void setB(Point b)
	{
		this.b = b;
	}

	public double getR()
	{
		return r;
	}

	public void setR(double r)
	{
		this.r = r;
	}	
}
