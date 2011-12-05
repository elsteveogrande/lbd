package cc.obrien.lbd;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import cc.obrien.lbd.layer.Layer;
import cc.obrien.lbd.server.Server;


/**
 * A virtual device may be composed of several {@link Layer}s
 * and have one or more {@link Server}s.
 * @author sobrien
 */
public class VirtualDevice
{
	/** total virtual size: must be a multiple of 512 */
	public final long size;
	

	/** the layers in this device; tries to issue a read/write command to each layer in list order (so "bottom" layer is last) */
	private final LinkedList<Layer> layers = new LinkedList<Layer> ();

	
	/** endpoints; ways for clients to access data in this device; can have more than one. */
	private final List<Server> endpoints = new LinkedList<Server> ();
	
	
	/**
	 * @param size total virtual size: must be a multiple of 512
	 */
	public VirtualDevice(int size)
	{
		if(size % 512 > 0)
			throw new IllegalArgumentException("size must be a multiple of 512");
		
		this.size = size;
	}
	
	
	/**
	 * add a layer to this virtual device; gets added on top
	 * @param layer a layer
	 */
	public void addLayer(Layer layer)
	{
		synchronized(this)
		{
			this.layers.add(0, layer);
		}
	}
	
	
	/**
	 * @param server a server
	 */
	public void addServer(Server server)
	{
		synchronized(this)
		{
			this.endpoints.add(server);
		}
	}

	
	/**
	 * 
	 * @param startingBlock
	 * @param blockCount
	 * @param arrayOffset
	 * @param bytes
	 * @return whether some layer successfully handled this operation
	 * @throws IOException
	 */
	public boolean readBlocks(long startingBlock, int blockCount, int arrayOffset, byte bytes[]) throws IOException
	{
		if(startingBlock < 0)
			throw new IllegalArgumentException("negative start position");

		final long limit = startingBlock + blockCount;
		if((limit<<9) > size)
			throw new IllegalArgumentException("exceeds device size");

		if(bytes.length < arrayOffset + (blockCount<<9))
			throw new IllegalArgumentException("byte array too short");
		
		for(Layer layer : layers)
		{
			boolean result = layer.readBlocks(startingBlock, blockCount, arrayOffset, bytes);
			if(result)
				return true;
			// else retry with next layer down
		}
		
		// all layers failed (which is unusual; the NullLayer at the very bottom should have handled the read!)
		return false;
	}

	
	/**
	 * @param byteStart starting byte offset
	 * @param byteCount number of bytes to read
	 * @param arrayOffset offset into the bytes array
	 * @param bytes receives the requested data
	 * @return whether the write was successful in at least some layer
	 * @throws IOException if error occurred reading from this layer
	 * @throws IllegalArgumentException if byte array was too short
	 */
	public boolean read(long byteStart, int byteCount, int arrayOffset, byte bytes[]) throws IOException
	{
		if(byteStart < 0)
			throw new IllegalArgumentException("negative start position");

		final long limit = byteStart + byteCount;
		if(limit > size)
			throw new IllegalArgumentException("exceeds device size");

		if(bytes.length < arrayOffset + byteCount)
			throw new IllegalArgumentException("byte array too short");
	
		long offset = byteStart;
		
		while(offset < limit)
		{
			int remaining = (int) (limit - offset);
			long blockNumber = offset >> 9;
			int currentReadSize;
			
			if((offset & 0x1ff) != 0)
			{
				// unaligned starting address for read
				
				// read the single block that houses this starting address
				byte block[] = new byte[512];
				boolean result = this.readBlocks(blockNumber, 1, 0, block);
				if(! result)
					return false;
				
				// and write the partial result; copy some bytes from the block into the result array
				int indent = (int) (offset & 0x1ff);
				currentReadSize = Math.min(512 - indent, remaining);
				System.arraycopy(block, indent, bytes, arrayOffset, currentReadSize);
			}
			else
			{
				// is an aligned starting address

				// how much to read?  i.e. can read strides of blocks, or a partial?
				if(remaining < 512)
				{
					// read the single block that contains this
					byte block[] = new byte[512];
					boolean result = this.readBlocks(blockNumber, 1, 0, block);
					if(! result)
						return false;

					// and write the partial result; copy some bytes from the block into the result array
					currentReadSize = remaining;
					System.arraycopy(block, 0, bytes, arrayOffset, currentReadSize);
				}
				else
				{
					// the number of whole blocks to read; i.e. round down remaining byte count to the lower block
					currentReadSize = remaining & ~0x1ff;

					// do multi-block I/O (depends on layer supporting this; the layer might just do block-by-block I/O, after all, but do multi if supported)
					int blockCount = currentReadSize >> 9;
					boolean result = this.readBlocks(blockNumber, blockCount, arrayOffset, bytes);
					if(! result)
						return false;
				}
			}

			// advance source / destination pointers
			offset += currentReadSize;
			arrayOffset += currentReadSize;
		}
		
		return true;
	}

	
	/**
	 * @param startingBlock
	 * @param blockCount
	 * @param arrayOffset
	 * @param bytes
	 * @return whether the operation completed successfully
	 * @throws IOException
	 */
	public boolean writeBlocks(long startingBlock, int blockCount, int arrayOffset, byte bytes[]) throws IOException
	{
		if(startingBlock < 0)
			throw new IllegalArgumentException("negative start position");

		final long limit = startingBlock + blockCount;
		if((limit<<9) > size)
			throw new IllegalArgumentException("exceeds device size");

		if(bytes.length < arrayOffset + (blockCount<<9))
			throw new IllegalArgumentException("byte array too short");

		for(Layer layer : layers)
		{
			boolean result = layer.writeBlocks(startingBlock, blockCount, arrayOffset, bytes);
			if(result)
				return true;
			// else retry with next layer down
		}
		
		// all layers failed (which is unusual; the NullLayer at the very bottom should have handled the read!)
		return false;
	}

	
	/**
	 * @param byteStart starting position
	 * @param byteCount number of bytes to write
	 * @param arrayOffset offset into the bytes array
	 * @param bytes contains the data to write
	 * @return whether the write was successful in at least some layer
	 * @throws IOException if error occurred writing to some layer
	 * @throws IllegalArgumentException if byte array was too short
	 */
	public boolean write(long byteStart, int byteCount, int arrayOffset, byte bytes[]) throws IOException
	{
		if(byteStart < 0)
			throw new IllegalArgumentException("negative start position");

		final long limit = byteStart + byteCount;
		if(limit > size)
			throw new IllegalArgumentException("exceeds device size");

		if(bytes.length < arrayOffset + byteCount)
			throw new IllegalArgumentException("byte array too short");
	
		long offset = byteStart;
		
		while(offset < limit)
		{
			int remaining = (int) (limit - offset);
			long blockNumber = offset >> 9;
			int currentWriteSize;
			
			if((offset & 0x1ff) != 0)
			{
				// unaligned starting address for write

				boolean result;

				// read the single block that houses this starting address into a scratch byte array
				byte block[] = new byte[512];
				result = this.readBlocks(blockNumber, 1, 0, block);
				if(! result)
					return false;

				// copy some bytes from the caller's data into the scratch array
				int indent = (int) (offset & 0x1ff);
				currentWriteSize = Math.min(512 - indent, remaining);
				
				System.arraycopy(bytes, arrayOffset, block, indent, currentWriteSize);

				// write the complete block back
				result = this.writeBlocks(blockNumber, 1, 0, block);
				if(! result)
					return false;
			}
			else
			{
				// is an aligned starting address

				// how much to write?  i.e. can write strides of blocks, or a partial?
				if(remaining < 512)
				{
					boolean result;
					
					// read the single block that contains this
					byte block[] = new byte[512];
					result = this.readBlocks(blockNumber, 1, 0, block);
					if(! result)
						return false;

					// and write the partial result; copy some bytes from the block into the result array
					currentWriteSize = remaining;
					System.arraycopy(bytes, arrayOffset, block, 0, currentWriteSize);

					// write the complete block back
					result = this.writeBlocks(blockNumber, 1, 0, block);
					if(! result)
						return false;
				}
				else
				{
					// the number of whole blocks to read; i.e. round down remaining byte count to the lower block
					currentWriteSize = remaining & ~0x1ff;

					// do multi-block I/O (depends on layer supporting this; the layer might just do block-by-block I/O, after all, but do multi if supported)
					int blockCount = currentWriteSize >> 9;
					boolean result = this.writeBlocks(blockNumber, blockCount, arrayOffset, bytes);
					if(! result)
						return false;
				}
			}

			// advance source / destination pointers
			offset += currentWriteSize;
			arrayOffset += currentWriteSize;
		}
		
		return true;
	}
}
