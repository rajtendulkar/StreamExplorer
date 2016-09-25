package exploration.paretoExploration.distanceexploration;

public class ScalePoint
{
	private int lowerBounds[];
	private int upperBounds[];
	
	public ScalePoint (int lowerBounds[], int upperBounds[]) 
	{
		this.lowerBounds = lowerBounds;
		this.upperBounds = upperBounds;
	}
	
	public Point scale (Point p)
	{
		Point newPoint = new Point(p);
		for(int i=0;i<p.dim();i++)
			newPoint.set(i, (p.get(i) - lowerBounds[i]) / (upperBounds[i] - lowerBounds[i]));
		return newPoint;
	}
	
	public Point unScale (Point p)
	{
		Point newPoint = new Point(p);
		for(int i=0;i<p.dim();i++)
			newPoint.set(i, (p.get(i) * (upperBounds[i] - lowerBounds[i])) + lowerBounds[i]);
			
		return newPoint;
	}
}
