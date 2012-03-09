/*
 * (C) 2012 Steve O'Brien.  BSD licensed.
 * see http://www.opensource.org/licenses/BSD-2-Clause
 * and see LICENSE in the root of this project.
 */


package cc.obrien.lbd.util;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.SocketException;


/**
 * general helpful stuff for NBD clients / servers
 * 
 * http://webcache.googleusercontent.com/search?q=cache:FC63wOVd1pMJ:code.activestate.com/recipes/577569-nbd-server-in-python/+&cd=1&hl=en&ct=clnk&gl=us
 * http://lists.canonical.org/pipermail/kragen-hacks/2004-May/000397.html
 * http://docs.python.org/library/struct.html
 * http://osdir.com/ml/linux.drivers.nbd.general/2008-09/msg00006.html
 * 
 * @author sobrien
 */
public class NBDUtil
{
	/** client &rarr; server request magic header */
	public static final int REQUEST_MAGIC = 0x25609513;
	
	
	/** server &rarr; client response packet header */
	public static final int RESPONSE_MAGIC = 0x67446698;
	
	
	/**
	 * upon initial connect, this packet is sent from server to client.
	 * @param blockCount the size of the virtual device, in blocks
	 * @param writable 
	 * @return the 152-byte packet
	 * @throws IOException potentially thrown by the byte array
	 */
	public static byte[] constructHello(long blockCount, boolean writable) throws IOException
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(152);
		DataOutputStream data = new DataOutputStream(bytes);
		data.write("NBDMAGIC".getBytes());
		data.write(new byte[] { 0x00, 0x00, 0x42, 0x02, -127, -122, 0x12, 0x53 });  //-127 == 0x81; -122 = 0x86
		data.writeLong(512L * ((long)blockCount));
		int flags = 
			1							// "has flags"
			| (writable ? 1<<1 : 0)		// readonly
		  ;
		data.writeInt(flags);
		for(int i=0; i<124; i++)  data.write(0);
		return bytes.toByteArray();
	}
	

	/**
	 * @param in
	 * @param writableExpected 
	 * @return block count of the NBD device as reported by the server (byte count >> 9)
	 * @throws IOException
	 * @throws IllegalArgumentException if writableExpected and remote device is not writable
	 */
	public static long readHello(DataInputStream in, boolean writableExpected) throws IOException
	{
		long helloMagic1 = in.readLong();
		if(helloMagic1 != 0x4E42444D41474943L)  // NBDMAGIC
			throw new RuntimeException(String.format("not NBDMAGIC: 0x%x", helloMagic1));

		long helloMagic2 = in.readLong();
		if(helloMagic2 != 0x0000420281861253L)
			throw new RuntimeException(String.format("magic2 %x != 0x0000420281861253", helloMagic2));
		
		long byteCount = in.readLong();
		if(byteCount % 512 != 0)
			throw new RuntimeException(String.format("size of NBD device %d not divisible by 512", byteCount));

		int flags = in.readInt();
		int unsupported = ~0x00000003;
		if( (flags & unsupported) != 0 )
		{
			throw new RuntimeException(String.format("cannot handle these flags: %08x", (flags & unsupported)));
		}

		if((flags & (1<<0)) != 0)
		{
			if((flags & (1<<1)) != 0)
			{
				// remote device is readonly
				if(writableExpected)
					throw new IllegalArgumentException("nbd device is read-only");
			}
		}
		
		byte junk[] = new byte[124];
		int result = IOUtil.ensureRead(in, junk);
		if(result != junk.length)
			throw new RuntimeException(String.format("expected %d bytes of junk, got %d", junk.length, result));
		
		return byteCount >> 9;  // convert byte count to block count (div 512)
	}

	
	/**
	 * client &rarr; server request packets, for read, write, or close commands
	 * @author sobrien
	 */
	public static class Request
	{
		/**
		 * request type
		 * @author sobrien
		 */
		public static enum Type
		{
			/** read request */
			READ(0),
			/** write request */
			WRITE(1),
			/** close session request */
			CLOSE(2),
			;

			/** numeric code; used when building request packets */
			public final int code;
			
			/**
			 * @param code numeric code (defined by protocol)
			 */
			private Type(int code)
			{
				this.code = code;
			}
			
			/**
			 * @param code
			 * @return type that has this code
			 * @throws IllegalArgumentException if invalid code
			 */
			public static Type forCode(int code)
			{
				switch(code)
				{
				case 0:
					return READ;
				case 1:
					return WRITE;
				case 2:
					return CLOSE;
				default:
					throw new IllegalArgumentException("type code " + code);
				}
			}
		}

		
		/** request type */
		public final Type type;
		
		
		/** handle (just a string identifier) */
		public final long handle;
		
		
		/** offset (byte offset) */
		public final long offset;
		
		
		/** length (bytes) */
		public final int length;
		
		
		/** payload bytes, if a {@link Type#WRITE} command, else null */
		public final byte[] bytes;
		
		
		/**
		 * @param type request type
		 * @param handle identifier
		 * @param offset byte offset
		 * @param length length in bytes
		 * @param bytes payload (can be null unless a {@link Type#WRITE})
		 */
		public Request(Type type, long handle, long offset, int length, byte bytes[])
		{
			this.type = type;
			this.handle = handle;
			this.offset = offset;
			this.length = length;

			// findbugs complains about storing an externally mutable object in this object's state; but for performance let this slide (don't require byte array copying)
			this.bytes = bytes;
		}
		
		
		/**
		 * parse request packet from input
		 * @param in input from client
		 * @return a request packet if a valid one was available in the input
		 * @throws IOException if network I/O error
		 * @throws AssertionError if junk in the input stream
		 */
		public static Request from(DataInputStream in) throws IOException
		{
			int magic = in.readInt();
			assert(magic == REQUEST_MAGIC);
			Type type = Type.forCode(in.readInt());
			long handle = in.readLong();
			long offset = in.readLong();
			int length = in.readInt();
			
			byte bytes[];
			if(type == Type.WRITE)
			{
				bytes = new byte[length];
				int result = IOUtil.ensureRead(in, bytes);
				if(result != length)
					throw new RuntimeException(
						String.format("incomplete read from network; got %d bytes, expected %d", result, length));
			}
			else
			{
				bytes = null;
			}
			
			return new Request(type, handle, offset, length, bytes);
		}
		
		
		/**
		 * transmit packet
		 * @param out output connection to remote server
		 * @throws IOException if network I/O problem
		 */
		public void write(DataOutputStream out) throws IOException
		{
			// self-nagling the response bytes; do a little byte-copying for the benefit of less network latency, in case TCP_NODELAY is set
			ByteArrayOutputStream buffer = new ByteArrayOutputStream(28 + (bytes == null ? 0 : bytes.length));
			DataOutputStream data = new DataOutputStream(buffer);
			
			data.writeInt(REQUEST_MAGIC);
			data.writeInt(type.code);
			data.writeLong(handle);
			data.writeLong(offset);
			data.writeInt(length);  // even if not a write packet TODO verify this!
			if(type == Type.WRITE)
				data.write(bytes);
			
			out.write(buffer.toByteArray());
			out.flush();

			data.flush();
		}
	}
	
	
	/**
	 * @author sobrien
	 *
	 */
	public static class Response
	{
		/** handle (string identifier); corresponds to the one from the client's request that this response is for */
		public final long handle;


		/** error code (0 is OK) */
		public final int error;

		
		/** bytes for the response, if the client requested a read */
		public final byte[] payload;

		
		/**
		 * @param error error code; 0 is OK
		 * @param handle the handle sent by the client
		 */
		public Response(long handle, int error)
		{
			this(handle, error, null);
		}
		
		
		/**
		 * @param handle the handle sent by the client
		 * @param error error code; 0 is OK
		 * @param payload bytes (only if this response was for a read command)
		 */
		public Response(long handle, int error, byte payload[])
		{
			this.handle = handle;
			this.error = error;
			// findbugs complains about storing an externally mutable object in this object's state; but for performance let this slide (don't require byte array copying)
			this.payload = payload;
		}
		
		
		@Override
		public String toString()
		{
			return String.format("[response handle=%016x error=%08x payload=%4d]", handle, error, (payload == null ? null : payload.length));
		}
		
		
		/**
		 * TODO allow asynchronous results coming back (e.g. request A, B, C, get B, C, A back)
		 * @param session remote server session
		 * @param handleExpected expected long identifier
		 * @param dataBytesExpected how many data byte should be coming back
		 * @return a response instance, possibly with payload (if this is in response to a read)
		 * @throws IOException if network I/O problems
		 */
		public static Response from(NBDSession session, long handleExpected, int dataBytesExpected) throws IOException
		{
			DataInputStream in = session.getDataIn();

			boolean retryAfterNextFail = true;
			while(true)
			{
				try
				{
					int magic = in.readInt();
					if(magic != RESPONSE_MAGIC)
						throw new RuntimeException("bad magic " + magic);
				
					int error = in.readInt();
				
					long handle = in.readLong();
					if(handle != handleExpected)
						throw new RuntimeException("got response for different request");
				
					byte payload[] = new byte[dataBytesExpected];
					if(error == 0 && dataBytesExpected > 0)
					{
						int result = IOUtil.ensureRead(in, payload);
						if(result < dataBytesExpected)
							throw new RuntimeException(String.format("couldn't write %d bytes; only received %d from client", dataBytesExpected, result));
					}
				
					Response ret = new Response(handleExpected, error, payload);
					return ret;
				}
				catch(SocketException e)
				{
					if(!retryAfterNextFail)
					{
						break;
					}
					
					if(! e.getMessage().contains("reset"))   // XXX need better way to detect connection reset (on an idle connection)
					{
						retryAfterNextFail = false;
					}
				}
			}
			
			// if here, we got no response; fail
			throw new RuntimeException("no response, failing");
		}
		

		/**
		 * send this response to the output stream
		 * @param out output stream to client
		 * @throws IOException if network I/O error
		 */
		public void write(DataOutputStream out) throws IOException
		{
			// self-nagling the response bytes; do a little byte-copying for the benefit of less network latency, in case TCP_NODELAY is set
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(16 + (payload == null ? 0 : payload.length));
			DataOutputStream data = new DataOutputStream(bytes);
			
			data.writeInt(RESPONSE_MAGIC);
			data.writeInt(0);
			data.writeLong(handle);
			if(payload != null)
				data.write(payload);
			
			out.write(bytes.toByteArray());
			out.flush();
		}
	}
}
