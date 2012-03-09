/*
 * (C) 2012 Steve O'Brien.  BSD licensed.
 * see http://www.opensource.org/licenses/BSD-2-Clause
 * and see LICENSE in the root of this project.
 */


package cc.obrien.lbd.layer;

import java.io.IOException;
import java.net.InetAddress;

import cc.obrien.lbd.Device;
import cc.obrien.lbd.util.NBDSession;


/**
 * receive/send data from/to an NBD source.
 * @author sobrien
 */
final public class NBD extends Layer
{
	/** remote NBD connection manager */
	private final NBDSession session;
	
	
	/**
	 * @param device device this belongs to
	 * @param writable writable?
	 * @param canCacheReadsIndefinitely ok to cache blocks that we already read?
	 * @param host NBD host
	 * @param port NBD port
	 * @param path (optional) the file on the NBD server, if the host:port specifies a directory
	 * @throws IOException if error occurred while trying to establish connection
	 */
	public NBD(Device device, boolean writable, boolean canCacheReadsIndefinitely, InetAddress host, int port, String path) throws IOException
	{
		super(device, writable, canCacheReadsIndefinitely);
		this.session = new NBDSession(host, port, writable);
		if(this.session.getBlockCount() != this.device.size)
			throw new IllegalArgumentException(String.format("NBD device block count of %d does not match virtual device block count of %d", this.session.getBlockCount(), this.device.size));
	}
	
	
	/**
	 * write this block to the NBD server
	 * XXX chatty; should allow for long runs of sectors to be written by way of {@link Layer#writeBlocks(long, int, int, byte[])}
	 */
	@Override
	public boolean commitBlock(long startBlock, int arrayOffset, byte[] contents) throws IOException
	{
		long deviceOffset = ((long) startBlock) * 512L;
		return this.session.writeBytes(deviceOffset, 512, arrayOffset, contents);
	}

	
	/**
	 * read the requested block from the NBD server and reply
	 * XXX chatty; should allow for long runs of sectors to be read by way of {@link Layer#readBlocks(long, int, int, byte[])}
	 */
	@Override
	public boolean fetchBlock(long startBlock, int arrayOffset, byte[] contents) throws IOException
	{
		long deviceOffset = startBlock * 512L;
		boolean result = this.session.readBytes(deviceOffset, 512, arrayOffset, contents);
		if(! result)
			return false;

		return true;
	}
	
	
	/**
	 * note: not synchronizing here, assume there's a lock at the device level
	 */
	@Override
	public void stop() throws IOException
	{
		super.stop();
		this.session.stop();
	}
}
