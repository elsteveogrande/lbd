package cc.obrien.lbd.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;
import cc.obrien.lbd.Device;
import cc.obrien.lbd.util.NBDUtil;
import cc.obrien.lbd.util.NBDUtil.Request;
import cc.obrien.lbd.util.NBDUtil.Response;


/**
 * NBD server daemon.
 * @author sobrien
 */
final public class NBDServer extends Server
{
	/** default nbd server port */
	public static final int DEFAULT_PORT = 7777;
	
	
	/** port we're listening on */
	public final int port;
	
	
	/** socket to listen for connections on */
	private final ServerSocket listeningSocket;
	

	/**
	 * indicates whether main listening thread is running; when it becomes false,
	 * the main daemon thread will try to gracefully shut down the handler threads
	 */
	private volatile boolean running = true;


	/** all the handlers */
	private final Set<NBDHandler> handlers = new HashSet<NBDHandler> ();


	/** blocking network calls (accept, recv) timeout after this many ms.  Not as an error, it's just in the polling loop so we can see whether the thread should be terminated; see run() method in NBDEndpoint and NBDHandler */
	private static final int TIMEOUT_MS = 1500;
	
	
	/**
	 * @param device the device this endpoint serves for
	 * @param socket socket to listen on
	 * @throws IOException if couldn't bind to this local port
	 */
	public NBDServer(Device device, ServerSocket socket) throws IOException
	{
		super(device);
		
		this.listeningSocket = socket;
		listeningSocket.setSoTimeout(TIMEOUT_MS);

		this.port = socket.getLocalPort();
	}
	
	
	@Override
	public String toString()
	{
		return String.format("%s (port %d)", this.getClass().getSimpleName(), this.listeningSocket.getLocalPort());
	}
	

	@Override
	public void stopServer()
	{
		this.running = false;
	}

	
	/**
	 * @return true iff server is running
	 * @see #stopServer()
	 */
	public boolean isRunning()
	{
		return this.running;
	}
	

	@Override
	public void run()
	{
		try
		{
			// run forever
			while(running)
			{
				Socket clientSocket;
				try
				{
					clientSocket = listeningSocket.accept();
					clientSocket.setTcpNoDelay(true);
					clientSocket.setKeepAlive(true);
				}
				catch(SocketTimeoutException e)
				{
					// we timed out waiting for a client; this is ok, just restart the loop
					// (we need this timeout because we want to be able to re-check the while condition)
					// XXX better way to do this besides basically polling for clients?
					continue;
				}
				
				NBDHandler handler = new NBDHandler(clientSocket);
				addHandler(handler);
				handler.start();
			}
		}
		catch(RuntimeException e)
		{
			throw e;
		}
		catch(Exception e)
		{
			throw new RuntimeException(e);
		}
		finally
		{
			try { this.listeningSocket.close(); }  catch(IOException e) { e.printStackTrace(); /* but continue */ };
		}
	}
	

	/**
	 * add to set
	 * @param handler handler
	 */
	private void addHandler(NBDHandler handler)
	{
		synchronized(handlers)
		{
			handlers.add(handler);
		}
	}

	
	/**
	 * remove from set
	 * @param handler handler
	 */
	private void removeHandler(NBDHandler handler)
	{
		synchronized(handlers)
		{
			handlers.remove(handler);
		}
	}

	
	/**
	 * A handler thread.
	 * Is a daemon thread; in case the listening daemon thread crashes,
	 * the other handler daemon threads can continue to run.
	 * Exits upon error or when session complete.
	 * @author sobrien
	 */
	final private class NBDHandler extends Thread
	{
		/** connection to client */
		private final Socket socket;
		
		
		/** input from client */
		private final DataInputStream input;
		
		
		/** output to client */
		private final DataOutputStream output;
		
		
		/**
		 * @param socket connection to client
		 * @throws IOException if couldn't get I/O streams
		 */
		public NBDHandler(Socket socket) throws IOException
		{
			this.socket = socket;
			this.input = new DataInputStream(socket.getInputStream());
			this.output = new DataOutputStream(socket.getOutputStream());
			this.setDaemon(true);
			
			socket.setSoTimeout(TIMEOUT_MS);
		}
		
		
		@Override
		public void run()
		{
			try
			{
				this.output.write(NBDUtil.constructHello(device.size, device.isWritable()));
				this.output.flush();
				
				while(NBDServer.this.isRunning())
				{
					try
					{
						Request request = NBDUtil.Request.from(this.input);
						switch(request.type)
						{
							case READ:
							{
								if(request.length == 0)  throw new IllegalArgumentException("zero length read");
								if(request.offset % 512 != 0 || request.length % 512 != 0)  throw new IllegalArgumentException(String.format("unaligned read"));
								byte bytes[] = new byte[request.length];
								boolean success = device.read((int)(request.offset / 512L), request.length / 512, 0, bytes);
								Response response;
								if(success)
									response = new Response(request.handle, 0, bytes);
								else
									response = new Response(request.handle, 1);
								response.write(output);
								break;
							}
							
							case WRITE:
							{
								if(request.length == 0)  throw new IllegalArgumentException("zero length write");
								if(request.offset % 512 != 0 || request.length % 512 != 0)  throw new IllegalArgumentException(String.format("unaligned write"));
								boolean success = device.write((int)(request.offset / 512L), request.length / 512, 0, request.bytes);
								new Response(request.handle, success ? 0 : 1).write(output);
								break;
							}
							
							case CLOSE:
							{
								return;
							}
						}
					}
					catch(EOFException e)  { /* premature end of socket; clean up */ this.input.close(); this.output.close(); }
					catch(SocketTimeoutException e)  { continue; }
				}
			}
			catch(RuntimeException e)
			{
				throw e;
			}
			catch(Exception e)
			{
				throw new RuntimeException(e);
			}
			finally
			{
				// safe cleanups
				NBDServer.this.removeHandler(this);

				// unsafe cleanups
				try
				{
					this.socket.close();
				} catch(IOException e)  { e.printStackTrace(); /* but continue without throwing this */ }
			}
		}
	}
}
