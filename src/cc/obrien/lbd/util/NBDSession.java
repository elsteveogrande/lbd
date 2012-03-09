/*
 * (C) 2012 Steve O'Brien.  BSD licensed.
 * see http://www.opensource.org/licenses/BSD-2-Clause
 * and see LICENSE in the root of this project.
 */


package cc.obrien.lbd.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import cc.obrien.lbd.util.NBDUtil.Request;
import cc.obrien.lbd.util.NBDUtil.Response;


/**
 * connection to a remove NBD server
 * @author sobrien
 */
public final class NBDSession
{
	/** remote host */
	public final InetAddress host;
	
	
	/** remote port */
	public final int port;
	
	
	/** TCP connection */
	private volatile Socket socket;
	
	
	/** TCP input */
	private volatile DataInputStream input;
	
	
	/** TCP output */
	private volatile DataOutputStream output;

	
	/** size of device as reported by server */
	private volatile Long blockCount = null;
	
	
	/** writable NBD device desired */
	private volatile boolean writableExpected;
	
	
	/**
	 * @param host NBD host
	 * @param port NBD TCP port
	 * @param writableExpected 
	 * @throws IOException if error occurred while trying to establish connection
	 * @throws IllegalArgumentException if writableExpected and not writable
	 */
	public NBDSession(InetAddress host, int port, boolean writableExpected) throws IOException
	{
		this.host = host;
		this.port = port;
		this.writableExpected = writableExpected;
		
		this.negotiate();
	}
	
	
	/**
	 * clean up for shut down
	 * @throws IOException 
	 */
	public void stop() throws IOException
	{
		this.output.flush();
		this.socket.close();
	}

	
	/**
	 * connect and negotiate
	 * @throws IOException
	 * @throws IllegalArgumentException if writableExpected and device is not writable
	 */
	synchronized protected void negotiate() throws IOException
	{
		if(this.input != null)
			try { this.input.close(); }  catch(IOException f)  { }
		
		if(this.output != null)
			try { this.output.close(); }  catch(IOException f)  { }
		
		if(this.socket != null)
			try { this.socket.close(); }  catch(IOException f)  { }
		
		this.socket = new Socket(host, port);
		this.socket.setTcpNoDelay(true);
		this.socket.setKeepAlive(true);

		this.input = new DataInputStream(socket.getInputStream());
		this.output = new DataOutputStream(socket.getOutputStream());

		long thisBlockCount = NBDUtil.readHello(input, writableExpected);
		if(this.blockCount != null)
		{
			if((long)this.blockCount != thisBlockCount)
				throw new RuntimeException(String.format("device size changed during renegotiation!  Was %d, now %d", this.blockCount, thisBlockCount));
		}
		else
		{
			this.blockCount = thisBlockCount;
		}
	}
	
	
	/**
	 * @return connection's data input stream
	 */
	public DataInputStream getDataIn()
	{
		return this.input;
	}
	
	
	/**
	 * @return connection's data output stream
	 */
	public DataOutputStream getDataOut()
	{
		return this.output;
	}
	

	/**
	 * @return number of blocks in remote NBD device, as reported by the most recent (re-)negotiation
	 */
	public long getBlockCount()
	{
		return this.blockCount;
	}
	
	
	/**
	 * note: synchronized for threadsafety; avoid trouble with command/response packets to/from the server
	 * @param offset position to write to
	 * @param byteCount number of bytes to write
	 * @param arrayOffset starting index in this array
	 * @param bytes an array of at least (arrayOffset + (512 * count)) bytes
	 * @return successful
	 * @throws IOException if network I/O problem
	 */
	synchronized public boolean writeBytes(long offset, int byteCount, int arrayOffset, byte bytes[]) throws IOException
	{
		long handle = (long) ((Long.MAX_VALUE) * Math.random());
		Request request;
		request = new Request(Request.Type.WRITE, handle, offset, byteCount, bytes);

		Response response;
		request.write(output);
		response = Response.from(this, handle, byteCount);

		return response.error == 0;
	}
	
	
	/**
	 * note: synchronized for threadsafety; avoid trouble with command/response packets to/from the server
	 * @param offset position to read from
	 * @param byteCount number of bytes to write
	 * @param arrayOffset starting index in this array
	 * @param bytes an array of at least (arrayOffset + (512 * count)) bytes
	 * @return successful
	 * @throws IOException if network I/O problem
	 */
	synchronized public boolean readBytes(long offset, int byteCount, int arrayOffset, byte bytes[]) throws IOException
	{
		long handle = (long) ((Long.MAX_VALUE) * Math.random());

		Request request = new Request(Request.Type.READ, handle, offset, byteCount, null);
		Response response;
		
		request.write(output);
		response = Response.from(this, handle, byteCount);
		System.arraycopy(response.payload, 0, bytes, arrayOffset, byteCount);
		
		return response.error == 0;
	}
}
