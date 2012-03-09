/*
 * (C) 2012 Steve O'Brien.  BSD licensed.
 * see http://www.opensource.org/licenses/BSD-2-Clause
 * and see LICENSE in the root of this project.
 */


package cc.obrien.lbd.layer;

import java.io.File;
import java.io.IOException;

import cc.obrien.lbd.Device;


/**
 * A big flat file representing a block device's content.
 * The storage instance's span is the size of this file, in blocks.
 * File size must be a multiple of 512 (the block size);
 * @author sobrien
 *
 */
public final class FlatFile extends FileStorage
{
	/**
	 * @param file file to use for random access
	 * @param device device this belongs to
	 * @param writable whether this will be writable into
	 * @param canCacheReadsIndefinitely ok to cache blocks we've read?
	 * @throws IOException error occurred during opening of file
	 * @throws AssertionError if the file's size is not exactly the size of the device, in bytes (512 * {@link Device#size})
	 */
	public FlatFile(File file, Device device, boolean writable, boolean canCacheReadsIndefinitely) throws IOException
	{
		super(file, device, writable, canCacheReadsIndefinitely);

		long expectedLength = device.size * 512L;
		if(file.length() != expectedLength)
			throw new IllegalArgumentException(String.format("bad file length %d, expected %d", file.length(), expectedLength));
		
		this.lockFile();
	}
	
	
	@Override
	public boolean commitBlock(long startBlock, int arrayOffset, byte[] contents) throws IOException
	{
		synchronized(file)
		{
			file.seek(startBlock * 512L);
			file.write(contents, arrayOffset, 512);
			file.getFD().sync();
		}
		
		return true;
	}

	
	@Override
	public boolean fetchBlock(long startBlock, int arrayOffset, byte[] contents) throws IOException
	{
		int result;
		
		synchronized(file)
		{
			file.seek(startBlock * 512L);
			result = file.read(contents, arrayOffset, 512);
		}

		return result == 512;
	}
}
