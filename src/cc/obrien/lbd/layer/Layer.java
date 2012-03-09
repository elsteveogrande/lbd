/*
 * (C) 2012 Steve O'Brien.  BSD licensed.
 * see http://www.opensource.org/licenses/BSD-2-Clause
 * and see LICENSE in the root of this project.
 */


package cc.obrien.lbd.layer;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import cc.obrien.lbd.Device;
import cc.obrien.lbd.util.FIFOCache;


/**
 * @author sobrien
 */
public abstract class Layer
{
	/** the device this is a participant of */
	public final Device device;

	
	/** can be written to? */
	private volatile boolean writable;
	
	
	/**
	 * can reads be cached for an indefinite amount of time (can we avoid asking the server each time)?
	 * Advisory only; the implementation can choose not to cache for whatever reason.
	 */
	public final boolean cacheEnabled;
	
	
	/** the size of the {@link #blockCache}, in 512-byte blocks */
	public static final int CACHE_SIZE_BLOCKS = 10240;  // 10k blocks -> 5MB cache
	
	
	/**
	 * block cache; block number &rarr; the most recent copy of the block
	 */
	public final FIFOCache<Long, byte[]> blockCache = new FIFOCache<Long, byte[]> (CACHE_SIZE_BLOCKS);


	/** blocks numbers, for dirty blocks (blocks that were written in the cache and not yet committed to permanent storage like disk / NBD server / etc.) */
	public final HashSet<Long> dirtyBlocks = new HashSet<Long> ();
	
	
	/**
	 * @param device device this belongs to
	 * @param writable writable?
	 * @param cacheEnabled
	 */
	protected Layer(Device device, boolean writable, boolean cacheEnabled)
	{
		this.device = device;
		this.writable = writable;
		this.cacheEnabled = cacheEnabled;
	}

	
	/**
	 * @return can be written to?
	 */
	public boolean isWritable()
	{
		return this.writable;
	}
	

	/**
	 * if device is writable, immediately set read-only, and flush any changes
	 * @throws IOException 
	 */
	public void setReadOnly() throws IOException
	{
		this.writable = false;
		this.commit();
	}
	
	
	/**
	 * commit any dirty blocks
	 * @throws IOException 
	 */
	public void commit() throws IOException
	{
		for(Long blockNumber : this.dirtyBlocks)
		{
			byte val[] = this.blockCache.find(blockNumber);
			this.commitBlock(blockNumber, 0, val);
		}
	}

	
	/**
	 * Write to this storage instance, optionally involving the cache layer.
	 * Assume all parameters are validated.
	 * Default implementation consults the cache first if possible, then tries {@link #commitBlock(long, int, byte[])}.
	 * Shouldn't synchronize; assume the {@link Device} calls like {@link Device#read(long, int, int, byte[])}, {@link Device#write(long, int, int, byte[])}, {@link Device#stop()}
	 * etc. calls are doing synchronization on the device level.
	 * @param block virtual startBlock
	 * @param arrayOffset specifies what startBlock into the contents array to read from into the storage
	 * @param contents data; should be {@code blockCount} bytes in size
	 * @return true if successfully written, else false (then lower layers will be tried)
	 * @throws IOException if error happened in this instance or during write to the backing storage, e.g. to files or network
	 */
	final public boolean writeBlock(long block, int arrayOffset, byte contents[]) throws IOException
	{
		if(this.cacheEnabled)
		{
			byte value[] = Arrays.copyOfRange(contents, arrayOffset, 512);
			this.blockCache.add(block, value);
			this.dirtyBlocks.add(block);
			return true;
		}
		else
		{
			return this.commitBlock(block, arrayOffset, contents);
		}
	}
	
	
	/**
	 * Write several blocks.
	 * The default implementation is to iteratively call {@link #writeBlock(long, int, byte[])}
	 * but this can be overridden to write multiple (e.g. for a network device, perhaps
	 * writing many blocks at a time will perform better).
	 * @param startingBlock
	 * @param blockCount
	 * @param arrayOffset
	 * @param contents
	 * @return true iff all blocks writes succeeded
	 * @throws IOException
	 */
	public boolean writeBlocks(long startingBlock, int blockCount, int arrayOffset, byte contents[]) throws IOException
	{
		for(int i=0; i<blockCount; i++)
		{
			int arrayBlockOffset = arrayOffset + (512 * i);
			long blockNumber = startingBlock + i;
			if(! this.writeBlock(blockNumber, arrayBlockOffset, contents))
				return false;
		}
		
		return true;
	}

	
	/**
	 * Write to this storage instance.
	 * Assume all parameters are validated.
	 * This is "below" the cache layer; this should always result in actual disk/NBD/etc. I/O operations.
	 * @param block virtual startBlock
	 * @param arrayOffset specifies what startBlock into the contents array to read from into the storage
	 * @param contents data; should be {@code blockCount} bytes in size
	 * @return true if successfully written, else false (then lower layers will be tried)
	 * @throws IOException if error happened in this instance or during write to the backing storage, e.g. to files or network
	 */
	abstract protected boolean commitBlock(long block, int arrayOffset, byte contents[]) throws IOException;

	
	/**
	 * Read from this storage instance, optionally involving the cache layer.
	 * Assume all parameters are validated.
	 * Default implementation consults the cache first if possible, then tries {@link #fetchBlock(long, int, byte[])}
	 * @param block virtual startBlock
	 * @param arrayOffset specifies what startBlock into the contents array to write to from the storage
	 * @param contents data; should be {@code blockCount} bytes in size
	 * @return true if successfully read, else false (then lower layers will be tried)
	 * @throws IOException if error happened in this instance or during read from the backing storage, e.g. from files or network
	 */
	final public boolean readBlock(long block, int arrayOffset, byte contents[]) throws IOException
	{
		if(this.cacheEnabled)
		{
			byte value[] = this.blockCache.find(block);
			if(value != null)
			{
				System.arraycopy(value, 0, contents, arrayOffset, value.length);
				return true;
			}
		}
		
		boolean result = this.fetchBlock(block, arrayOffset, contents);
		if(! result)
			return false;

		if(this.cacheEnabled)
		{
			byte value[] = Arrays.copyOfRange(contents, arrayOffset, 512);
			this.blockCache.add(block, value);
			this.dirtyBlocks.remove(block);
		}

		return true;
	}

	
	/**
	 * Read several blocks.
	 * The default implementation is to iteratively call {@link #readBlock(long, int, byte[])}
	 * but this can be overridden to read multiple (e.g. for a network device, perhaps
	 * reading many blocks at a time will perform better).
	 * @param startingBlock
	 * @param blockCount
	 * @param arrayOffset
	 * @param contents
	 * @return true iff all blocks reads succeeded
	 * @throws IOException
	 */
	public boolean readBlocks(long startingBlock, int blockCount, int arrayOffset, byte contents[]) throws IOException
	{
		for(int i=0; i<blockCount; i++)
		{
			int arrayBlockOffset = arrayOffset + (512 * i);
			long blockNumber = startingBlock + i;
			if(! this.readBlock(blockNumber, arrayBlockOffset, contents))
				return false;
		}
		
		return true;
	}

	
	/**
	 * Read from this storage instance.
	 * Assume all parameters are validated.
	 * This is "below" the cache layer; this should always result in actual disk/NBD/etc. I/O operations.
	 * @param block virtual startBlock
	 * @param arrayOffset specifies what startBlock into the contents array to write to from the storage
	 * @param contents data; should be {@code blockCount} bytes in size
	 * @return true if successfully read, else false (then lower layers will be tried)
	 * @throws IOException if error happened in this instance or during read from the backing storage, e.g. from files or network
	 */
	abstract protected boolean fetchBlock(long block, int arrayOffset, byte contents[]) throws IOException;
	
	
	/**
	 * Cleanups for this layer.
	 * Default implementation commits (if writable).
	 * Recommended that subclasses override this but also call {@code super.stop()}.
	 * Shouldn't synchronize; assume the {@link Device} calls like {@link Device#read(long, int, int, byte[])}, {@link Device#write(long, int, int, byte[])}, {@link Device#stop()}
	 * etc. calls are doing synchronization on the device level.
	 * @throws IOException if I/O problem occurred while closing, flushing, etc.
	 */
	public void stop() throws IOException
	{
		if(this.writable)
		{
			this.commit();
		}
	}
}
