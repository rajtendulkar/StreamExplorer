package logger;
import java.io.*;

/** 
 * 
 * This class will duplicate the console output to the log file specified by the file name.
 * If we want to have other logging facilities, just add more OutputStreams.
 * 
 * We can re-direct the output to file and console at the same time, just like the 'tee' utility. 
 *
 *<br><br>
 * Usage :
 * If you want to use this logger, just add following statement at the beginning of your code-
 * <pre>
 * {@literal @}SuppressWarnings({"unused", "resource"})
 * ConsoleLogger logger = new ConsoleLogger("log.txt");
 * </pre>
 * 
 * @author rajtendulkar 
 */
public class ConsoleLogger extends OutputStream 
{
	
	/**
	 * Default Console output stream.
	 */
	OutputStream console;
	
	/**
	 * Output Stream, like a file, to duplicate the console output.
	 */
	OutputStream logFile;

	/**
	 * We have to just create an object of this class and pass the filename
	 * where the log should be written. Nothing else needs to be done.
	 * 
	 * @param fileName Output File where the console should be written.
	 */
	public ConsoleLogger (String fileName) 
	{	
		console = System.out;		
		File file = new File(fileName);			
		
		try
		{
			if (!file.exists())
				file.createNewFile();
			logFile = new FileOutputStream(file);
		}
		catch (Exception e) { e.printStackTrace(); }
		
		PrintStream ptStream = new PrintStream(this);
		System.setOut(ptStream);
	}

	/**
	 * When writing to console also write the the Log File.
	 * 
	 * @see java.io.OutputStream#write(int)
	 * 
	 */
	@Override
	public void write(int arg0) throws IOException 
	{
		console.write(arg0);
		logFile.write(arg0);		
	}

	/**
	 * When writing to console also write the the Log File.
	 * 
	 * @see java.io.OutputStream#write(byte[])
	 * 
	 */
	@Override
	public void write(byte[] b) throws IOException
	{
		console.write(b);
		logFile.write(b);		
	}

	/**
	 * When writing to console also write the the log file.
	 * 
	 * @see java.io.OutputStream#write(byte[], int, int)
	 * 
	 */
	@Override
	public void write(byte[] b, int off, int len) throws IOException
	{
		console.write (b, off, len);
		logFile.write (b, off, len);		
	}

	
	/**
	 * 
	 * When closing the console, also close the log file.
	 * 
	 * @see java.io.OutputStream#close()
	 * 
	 */
	@Override
	public void close() throws IOException
	{		
		console.close();
		logFile.close();
	}

	/**
	 * When flushing the text to the console also flush to the log file.
	 * 
	 * @see java.io.OutputStream#flush()
	 * 
	 */
	@Override
	public void flush() throws IOException
	{
		console.flush();
		logFile.flush();		
	}
}