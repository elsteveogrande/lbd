/*
 * (C) 2012 Steve O'Brien.  BSD licensed.
 * see http://www.opensource.org/licenses/BSD-2-Clause
 * and see LICENSE in the root of this project.
 */


package cc.obrien.lbd.manager;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import cc.obrien.lbd.Device;
import cc.obrien.lbd.layer.ExpandableFile;
import cc.obrien.lbd.layer.Layer;


/**
 * @author sobrien
 */
final public class Manager extends Thread
{
	/** default listening port */
	public static final int DEFAULT_PORT = 6666;

	
	/** device this is managing */
	public final Device device;
	
	
	/** listen on this socket for connections */
	public final ServerSocket listenSocket;

	
	/**
	 * listen on default port
	 * @param device 
	 * @param listenSocket 
	 * @throws IOException 
	 */
	public Manager(Device device, ServerSocket listenSocket) throws IOException
	{
		this.device = device;
		this.listenSocket = listenSocket;
		this.setDaemon(true);
	}
		
	
	@Override
	public void run()
	{
		try
		{
			while(true)
			{
				Socket clientSocket = this.listenSocket.accept();
				clientSocket.setKeepAlive(true);
				clientSocket.setTcpNoDelay(true);
				new Handler(clientSocket).start();
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
	}
	
	
	/**
	 * client handler daemon
	 * @author sobrien
	 */
	public class Handler extends Thread
	{
		/** socket to client */
		private final Socket clientSocket;
		
		
		/** in */
		private final ObjectInputStream in;
		
		
		/** out */
		private final ObjectOutputStream out;

		
		/**
		 * @param clientSocket
		 * @throws IOException 
		 */
		public Handler(Socket clientSocket) throws IOException
		{
			super();
			this.setDaemon(true);
			this.clientSocket = clientSocket;
			this.out = new ObjectOutputStream(clientSocket.getOutputStream());
			this.out.flush();
			this.in = new ObjectInputStream(clientSocket.getInputStream());
			this.out.writeObject(new Response(0x1111222233334444L, true, "hello"));
			this.out.flush();
		}
		
		
		@Override
		public void run()
		{
			try
			{
				while(true)
				{
					Response response;
					Request request = (Request) in.readObject();
					switch(request.type)
					{
					case PING:
						response = new Response(request.serial, true);
						break;
				
					case SHUTDOWN:
						System.err.printf("stop %s: ", device.toString());
						device.stop();
						System.err.println();
						System.err.flush();
								
						response = new Response(request.serial, true);
						break;
						
					case DEVICE_INFO_STRINGS:
						LinkedList<String> result = new LinkedList<String> ();
						result.add(device.getInfoString());
						response = new Response(request.serial, true, device.getInfoString());
						break;
						
					case LIVE_ADD_LAYER:
						File expandableFile = new File((String) request.args.get(0));
						Layer expandableLayer = new ExpandableFile(expandableFile, device, true, false);
						device.addLayer(expandableLayer);
						response = new Response(request.serial, true);
						break;
						
					default:
						throw new RuntimeException("don't know request type " + request.type);
					}
					
					// serialize and send response; build array and send, don't stream directly from serializer
					// (don't want our un-nagled packets to incur too much overhead)
					ByteArrayOutputStream bytes = new ByteArrayOutputStream();
					out.writeObject(response);
					out.write(bytes.toByteArray());
				}
			}
			catch(EOFException e)
			{
				// natural termination of connection
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
				try { in.close(); }  catch(IOException e)  { e.printStackTrace(); /* but continue */ }
				try { out.close(); }  catch(IOException e)  { e.printStackTrace(); /* but continue */ }
				try { clientSocket.close(); }  catch(IOException e)  { e.printStackTrace(); /* but continue */ }
			}
		}
	}
}
