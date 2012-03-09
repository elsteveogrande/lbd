/*
 * (C) 2012 Steve O'Brien.  BSD licensed.
 * see http://www.opensource.org/licenses/BSD-2-Clause
 * and see LICENSE in the root of this project.
 */


package cc.obrien.lbd.layer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

import cc.obrien.lbd.Device;


/**
 * <p>
 * Implements a sparse file layer; like flat files but starts out small and expands, instead of having to be
 * the exact size of the layer.  Reads from missing portions are zeroes; writes clobber existing data, or are
 * appended to the file if that block is not yet present in the sparse file.
 * </p>
 * 
 * <p>
 * This has a multi-level index table system (like CPU page tables).
 * Each node in the tree contains 64 references to either other nodes (if at level 0 through 4)
 * or a list of block locations (level 5).  This means (2**(6 * 6)) = (2**36) sectors are addressable,
 * which means (2**45) == 32TB are possible here.
 * </p>
 * 
 * @author sobrien
 */
public final class ExpandableFile extends FileStorage
{
	/**
	 * magic value expected at bytes 504 thru 511 (big endian) in the expandable file,
	 * to pass validation (empty files are also valid).
	 * This is OR'ed with {@link #FILE_FORMAT_VERSION} to produce the real magic bytes written to the file.
	 */
	public static final long FILE_MAGIC = 0x4c42445801010100L;   // LBDX(01 01 01)N where N is the file format version number

	
	/** we only write version 1 files */
	public static final int FILE_FORMAT_VERSION = 1;
		
	
	/**
	 * @param file filesystem file
	 * @param device device this belongs to
	 * @param writable writable?
	 * @param cacheEnabled
	 * @throws IOException if file could not be accessed
	 */
	public ExpandableFile(File file, Device device, boolean writable, boolean cacheEnabled) throws IOException
	{
		super(file, device, writable, cacheEnabled);
		
		// check magic (if empty file, then ok; skip)
		if(this.file.length() > 0)
		{
			if(file.length() % 512 != 0)
				throw new IllegalArgumentException("file length not a multiple of 512");

			if(this.file.length() < 512)
				throw new IllegalArgumentException("bad file size: " + file.length());
			
			this.file.seek(512 - 8);
			long magic = this.file.readLong();
			if((magic & ~0xff) != (FILE_MAGIC & ~0xff))
				throw new IllegalArgumentException("bad file magic");
			
			int version = (int)(magic & 0xff);
			if(version == 1)
			{
				if(device.size >= 0x1F8000000000L)		// last entry (#63) of the 64-entry root table is reserved, which limits the size to 32.0TB * (63.0/64.0) = 31.5TB
					throw new IllegalArgumentException("virtual device too big; 31.5TB limit");
			}
			else
			{
				throw new IllegalArgumentException("only version 1 expandable files supported");
			}
		}
		
		this.lockFile();
	}

	
	/**
	 * @param block
	 * @param arrayOffset
	 * @param contents
	 * @return whether this layer could handle the request; false if the read was in a "sparse" area
	 * @throws IOException
	 */
	@Override
	public boolean fetchBlock(long block, int arrayOffset, byte[] contents) throws IOException
	{
		long fileOffset = this.getFileOffsetForBlock(block, false);
		if(fileOffset == 0)
		{
			// unmapped block; let the next layer try to handle it
			return false;
		}
		else
		{
			// read from this block
			file.seek(fileOffset);
			int result = file.read(contents, arrayOffset, 512);
			if(result != 512)
				throw new RuntimeException("failed to read 512 bytes at " + fileOffset);
			return true;
		}
	}
	

	/**
	 * @param block
	 * @param arrayOffset
	 * @param contents
	 * @return always true
	 * @throws IOException
	 */
	@Override
	public boolean commitBlock(long block, int arrayOffset, byte[] contents) throws IOException
	{
		long fileOffset = this.getFileOffsetForBlock(block, true);

		if(fileOffset == 0)
			return false;
		
		file.seek(fileOffset);
		file.write(contents, arrayOffset, 512);
		return true;
	}

	
	/**
	 *     4         3         2         1         0
	 * 432109876543210987654321098765432109876543210
	 * AAAAAABBBBBBCCCCCCDDDDDDEEEEEEFFFFFFooooooooo
	 * A - F are L0-L5 indexes; o = startBlock into block.
	 * supports up to 45-bit addresses (32TB)
	 * @param blockNumber virtual starting block
	 * @param allocateIfNotFound if block for this virtual region not yet present in this expandable file, build it
	 * @return the startBlock into the file for that block (block-aligned); or zero if not found and <code>(! allocateIfNotFound)</code>
	 * @throws IOException if error occurred while reading/writing file
	 */
	private long getFileOffsetForBlock(long blockNumber, boolean allocateIfNotFound) throws IOException
	{
		Table table = new Table(0);  // root table at location 0
		long ret = 0;
		for(int level=0; level<6; level++)
		{
			int shift = (6 - level - 1) * 6;
			int index = (int) ((blockNumber >> shift) & 0x3f);

			if(level < 5)
			{
				table = table.getTableEntry(index, allocateIfNotFound);
				if(table == null)
				{
					ret = 0;
					break;
				}
			}
			else
			{
				ret = table.getEntry(index, allocateIfNotFound);
			}
		}

		return ret;
	}
	
	
	/**
	 * extend this file by one block
	 * @return the location of this new block (== the size of the file at the time of performing this operation)
	 * @throws IOException
	 */
	protected long extend() throws IOException
	{
		synchronized(file)
		{
			long fileOffset = file.length();
			file.setLength(fileOffset + 512);
			return fileOffset;
		}
	}
	
	
	/**
	 * a table that refers to other tables, or to sector locations.
	 * Has 64 entries.  (Not written as a constant; it's a pretty much hard-set number (512 byte sector size / 8 bytes per long)
	 * @author sobrien
	 */
	private class Table
	{
		/** permanent startBlock of this table in the file; if not yet written, then 0 */
		public final long fileOffset;
		
		/** the 64 entries: pointers to sub-tables or block locations */
		private final long entries[] = new long[64];		// these can be sector startBlocks (if level 5) or startBlocks to sub-tables (if level 0 through 4)

	
		/**
		 * load table from file
		 * @param fileOffset
		 * @throws IOException
		 */
		public Table(long fileOffset) throws IOException
		{
			this.fileOffset = fileOffset;
			
			byte block[] = new byte[512];
			
			synchronized(file)
			{
				if(file.length() >= (fileOffset + 512))
				{
					file.seek(fileOffset);
					int result = file.read(block);
					if(result != block.length)
						throw new RuntimeException("incomplete read at " + fileOffset);
				}
				else
				{
					for(int i=0; i<512; i++)  block[i] = 0;
					this.save();
				}
			}

			DataInputStream data = new DataInputStream(new ByteArrayInputStream(block));
			for(int i=0; i<64; i++)
				entries[i] = data.readLong();
		}
		
		
		/**
		 * write entries to the file
		 * @throws IOException
		 */
		private void save() throws IOException
		{
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
			DataOutputStream data = new DataOutputStream(bytes);

			if(this.fileOffset == 0)
			{
				// it's the root table
				entries[63] = FILE_MAGIC | FILE_FORMAT_VERSION;
			}
			
			for(int i=0; i<64; i++)
				data.writeLong(this.entries[i]);

			synchronized(file)
			{
				file.seek(fileOffset);
				file.write(bytes.toByteArray());
			}
		}
		
		
		/**
		 * @param i
		 * @param allocateIfNotFound
		 * @return entry at this location.  If not found, a new one is created and returned, if allocate flag is true (else null)
		 * @throws IOException
		 */
		public Table getTableEntry(int i, boolean allocateIfNotFound) throws IOException
		{
			long fileOffset = this.getEntry(i, allocateIfNotFound);
			if(fileOffset != 0)
				return new Table(fileOffset);

			return null;
		}
		
		
		/**
		 * @param i
		 * @param allocateIfNotFound
		 * @return long entry at this position
		 * @throws IOException
		 */
		public long getEntry(int i, boolean allocateIfNotFound) throws IOException
		{
			long fileOffset = this.entries[i];
			if(fileOffset != 0)
				return fileOffset;
			
			// 0 means subtable not declared at this location (startBlock 0 is invalid; it belongs to the root L0 table).
			if(allocateIfNotFound)
			{
				synchronized(file)
				{
					fileOffset = extend();
					this.entries[i] = fileOffset;
					this.save();
				}
			}
			
			return fileOffset;
		}
	}
}
