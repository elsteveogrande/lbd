package cc.obrien.lbd.layer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;

import cc.obrien.lbd.Device;


/**
 * Maps blocks to a local filesystem file.
 * When blocks are written to a device and into this storage, the blocks' values are stashed in the file.
 * They can later be read from that file; if a block is not previously written, a read from that location will result in a <code>false</code> value
 * from the {@link #readBlock(long, int, byte[])} method.
 * @author sobrien
 */
abstract public class FileStorage extends Layer
{
	/** the File that describes the file's path; not to be confused with the random access file in this class called {@link #file} */
	public final File fileObject;
	
	
	/** random access to support seek, tell, read, write (w/o input/output streams) */
	protected final RandomAccessFile file;
	
	
	/** the (advisory) lock on the random acces file {@link #file} */
	private volatile FileLock fileLock = null;
	
	
	/**
	 * @param file a RW file
	 * @param device device this belongs to
	 * @param writable file can be written to? (if not, IOException is thrown during {@link #writeBlock(long, int, byte[])} calls
	 * @param canCacheReadsIndefinitely ok to cache blocks that we've read?
	 * @throws IOException if we couldn't create a RandomAccessFile in the desired mode
	 */
	public FileStorage(File file, Device device, boolean writable, boolean canCacheReadsIndefinitely) throws IOException
	{
		super(device, writable, canCacheReadsIndefinitely);
		
		this.fileObject = file;
		this.file = new RandomAccessFile(file, writable ? "rw" : "r");
	}
	
	
	/**
	 * lock the {@link #file}
	 * @throws IOException 
	 */
	protected void lockFile() throws IOException
	{
		if(this.isWritable())
			this.fileLock = this.file.getChannel().lock();
	}
	
	
	@Override
	public void stop() throws IOException
	{
		super.stop();
		
		try
		{
			if(fileLock != null)
			{
				fileLock.release();
			}
		}
		catch (IOException e)
		{
			// spit out exception
			e.printStackTrace();

			// but move along
		}
	}
}
