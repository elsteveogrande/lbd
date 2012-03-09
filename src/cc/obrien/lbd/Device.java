/*
 * (C) 2012 Steve O'Brien.  BSD licensed.
 * see http://www.opensource.org/licenses/BSD-2-Clause
 * and see LICENSE in the root of this project.
 */


package cc.obrien.lbd;

import java.io.IOException;
import java.util.LinkedList;
import cc.obrien.lbd.layer.ExpandableFile;
import cc.obrien.lbd.layer.Layer;
import cc.obrien.lbd.layer.NullLayer;
import cc.obrien.lbd.manager.Manager;
import cc.obrien.lbd.server.Server;


/**
 * A virtual device may be composed of several {@link Layer}s
 * and have one or more {@link Server}s.
 * @author sobrien
 */
public class Device
{
	/** total virtual size, in 512-byte blocks */
	public final long size;
	

	/** the layers in this device; tries to issue a read/write command to each layer in list order (so "bottom" layer is last) */
	private final LinkedList<Layer> layers = new LinkedList<Layer> ();

	
	/** server endpoint; a ways for clients to access data in this device */
	private volatile Server server = null;

	
	/** the manager daemon */
	private volatile Manager manager = null;
	
	
	/**
	 * @return server
	 */
	public Server getServer()
	{
		return server;
	}


	/**
	 * @param server
	 */
	public void setServer(Server server)
	{
		this.server = server;
	}


	/**
	 * @return manager
	 */
	public Manager getManager()
	{
		return manager;
	}


	/**
	 * @param manager
	 */
	public void setManager(Manager manager)
	{
		this.manager = manager;
	}


	/** whether the device is running / enabled; initially true, set to false when {@link #stop()} called */
	private volatile boolean running = true;
	
	
	/**
	 * @param size total virtual size, in 512-byte blocks
	 */
	public Device(long size)
	{
		if(size < 0)
			throw new IllegalArgumentException("negative block count");
		
		this.size = size;
		
		// a fail-safe readable bottom layer.  Reads result in null-bytes; writes fail.
		this.layers.add(new NullLayer(this));

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.err.println("-- shutdown");
				try { Device.this.stop(); }  catch(IOException e)  { e.printStackTrace(); }
			}
		});
	}
	
	
	/**
	 * @return whether this is running
	 */
	public boolean isRunning()
	{
		return this.running;
	}
	
	
	/**
	 * add a layer to this virtual device; gets added on top.
	 * Note that if there was a layer which was writable, it is forced
	 * to be read-only and flushed before adding this layer.
	 * (This special case is to support live-adding of new top layers.)
	 * @param layer a layer
	 * @throws IOException if during this special case new-top-layer addition, the previous writable top layer could not be committed (see {@link Layer#commit()})
	 */
	synchronized public void addLayer(Layer layer) throws IOException
	{
		// former top element
		if(! this.layers.isEmpty())
		{
			Layer oldTop = this.layers.getFirst();
			if(oldTop.isWritable())
			{
				oldTop.setReadOnly();
			}
		}
		
		// add to the top
		this.layers.add(0, layer);
	}
	
		
	/**
	 * @return true iff topmost layer is writable
	 */
	synchronized public boolean isWritable()
	{
		return this.layers.getFirst().isWritable();
	}
	
	
	/**
	 * @return spit out info about this device
	 */
	synchronized public String getInfoString()
	{
		StringBuilder ret = new StringBuilder();
		
		ret.append(String.format("device size: %d blocks (%d bytes / %f GB)\n", this.size, (((long)size)<<9), ((double)(((long)size)<<9)) / ((double)(1<<30))));
		ret.append(String.format("server type: %s\n", this.server));
		ret.append(String.format("management port: %d\n", this.manager.listenSocket.getLocalPort()));
		ret.append(String.format("layers:\n"));
		for(int i=0; i<layers.size(); i++)
		{
			Layer layer = layers.get(i);
			
			String param = null;
			if(layer instanceof ExpandableFile)
			{
				param = ((ExpandableFile)layer).fileObject.getAbsolutePath();
			}

			ret.append(String.format("   %2d: type=%18s, writable=%5s, param=%s\n", layers.size()-i-1, layer.getClass().getSimpleName(), layer.isWritable(), param));
		}
		ret.append('\n');
		return ret.toString();
	}

	
	/**
	 * dump info string to stderr
	 * @see #getInfoString()
	 */
	public void dumpInfo()
	{
		System.err.print(this.getInfoString());
		System.err.flush();
	}

	
	/**
	 * @param startBlock starting block number
	 * @param blockCount number of blocks to read
	 * @param arrayOffset startBlock into the bytes array to write these data into
	 * @param bytes receives the requested blocks in order; array size (blockCount)*512
	 * @return whether the write was successful in at least some layer
	 * @throws IOException if error occurred reading from this layer
	 */
	synchronized public boolean read(long startBlock, int blockCount, int arrayOffset, byte bytes[]) throws IOException
	{
		if(! this.isRunning())
			throw new IllegalStateException("not running");

		if( ! (bytes.length >= arrayOffset + (blockCount * 512)) )
			throw new IllegalArgumentException("insufficient bytes");

		if(startBlock < 0)
			return false;
		if(blockCount < 0)
			return false;
		if(blockCount >= (1<<22))	// max 2**22-1 blocks at a time (a little under 2GB)
			return false;
		if(startBlock+blockCount > size)
			return false;

		for(long i=0; i<blockCount; i++)
		{
			long block = startBlock + i;

			boolean readSuccess = false;
			for(Layer layer : layers)
			{
				boolean result = layer.readBlock(block, (int)(arrayOffset + (i * 512)), bytes);
				if(result)
				{
					// the read was a success; move on to next block
					readSuccess = true;
					break;
				}

				// else unhandled read error
				continue;
			}
			
			if(! readSuccess)
			{
				// couldn't read a block; abort
				return false;
			}
		}
		
		// all blocks completed
		return true;
	}
	
	
	/**
	 * @param startBlock starting block number
	 * @param blockCount number of blocks to write
	 * @param arrayOffset startBlock into the bytes array to read these data into
	 * @param bytes the blocks to write, in order; array size (blockCount)*512
	 * @return whether the write was successful in at least some layer
	 * @throws IOException if error occurred writing to this layer
	 */
	synchronized public boolean write(long startBlock, int blockCount, int arrayOffset, byte bytes[]) throws IOException
	{
		if(! this.isRunning())
			throw new IllegalStateException("not running");
		
		if( ! (bytes.length >= arrayOffset + (blockCount * 512L)) )
			throw new IllegalArgumentException("insufficient bytes");

		if(startBlock < 0)
			return false;
		if(blockCount < 0)
			return false;
		if(blockCount >= (1<<22))	// max 2**22-1 blocks at a time (a little under 2GB)
			return false;
		if(startBlock+blockCount > size)
			return false;
		
		for(long i=0; i<blockCount; i++)
		{
			long block = startBlock + i;
			
			for(Layer layer : layers)
			{
				if(! layer.isWritable())
					continue;

				boolean result = layer.writeBlock(block, (int)(arrayOffset + (i * 512)), bytes);
				if(result)
				{
					// the write was a success; move on to next block
					break;
				}
				else
				{
					// unhandled write error; give up
					return false;
				}
			}
		}
		
		// all blocks completed
		return true;
	}
	
	
	/**
	 * stop the device; disable all activity, shut down server, flush all unwritten blocks in the cache
	 * @throws IOException 
	 */
	synchronized public void stop() throws IOException
	{
		// de-initialization steps; do these BEFORE stopping the server
		// (because the server may be the only thread running, keeping the JVM alive, depending on how this is being run)
		
		// disable
		this.running = false;

		// flush blocks
		this.layers.getFirst().commit();

		// layer-specific shutdown procedures
		for(Layer layer : this.layers)
		{
			layer.stop();
		}
		
		// stop the server
		this.server.stopServer();

		// wait for the server thread to die
		while(true)
		{
			try
			{
				this.server.join();
				break;
			}
			catch(InterruptedException e) { }
		}
	}
}
