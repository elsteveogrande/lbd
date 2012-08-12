/*
 * (C) 2012 Steve O'Brien.  BSD licensed.
 * see http://www.opensource.org/licenses/BSD-2-Clause
 * and see LICENSE in the root of this project.
 */


package cc.obrien.lbd;

import gnu.getopt.Getopt;
import java.io.*;
import java.net.*;
import java.util.LinkedList;
import cc.obrien.lbd.layer.*;
import cc.obrien.lbd.manager.Manager;
import cc.obrien.lbd.manager.Request;
import cc.obrien.lbd.server.*;


/**
 * Command-line utility to build a {@link Device} and start a {@link Server} daemon.
 * Currently the virtual device parameters, such as size and layers, are just hardcoded in {@link #main(String...)}.
 * Should use a config file or some sort of getopt convention so all parameters come from the command line (my preference)
 * @author sobrien
 */
@SuppressWarnings("all")
public final class LBD
{
	/** all instantiated devices that are running; used by {@link Request.Type#SHUTDOWN} */
	public static final LinkedList<Device> runningDevices = new LinkedList<Device> ();
	
	
	@SuppressWarnings("javadoc")
	private static final class LayerArg
	{
		public static enum Type  { EXPANDABLE_FILE, FLAT_FILE, NBD };
		public final Type type;
		public final boolean writable;
		public final boolean cacheEnabled;
		public final String spec;
		public LayerArg(Type type, boolean writable, boolean cacheEnabled, String spec) {
			this.type = type;
			this.writable = writable;
			this.cacheEnabled = cacheEnabled;
			this.spec = spec;
		}
	}
	
	
	/**
	 * @param args pased into {@link Getopt}; see {@link #shortOptions}
	 * @throws Exception any exception that can occur is thrown: I/O, etc.
	 */
	public static void main(String... args) throws Exception
	{
		System.err.printf("LBD version %d.%d.%d\n", Version.MAJOR, Version.MINOR, Version.PATCH);

		Long deviceBlockCount = null;
		InetSocketAddress serverBindOn = new InetSocketAddress(NBDServer.DEFAULT_PORT);
		InetSocketAddress managerBindOn = new InetSocketAddress(InetAddress.getByName("localhost"), Manager.DEFAULT_PORT);
		LinkedList<LayerArg> layerArgs = new LinkedList<LayerArg> ();

		String parts[];
		InetAddress bindAddress;
		Integer bindPort;

		Getopt getOpt = new Getopt("LBD", args, "hb:s:l:a:e:E:f:F:n:N:X:");
		int opt;
		while((opt = getOpt.getopt()) != -1)
		{
			switch(opt)
			{
			case 'b':
				deviceBlockCount = Long.parseLong(getOpt.getOptarg());
				break;

			case 's':
				Character suffix = null;
				String arg = getOpt.getOptarg().toLowerCase();
				if(! arg.matches("^[0-9]+[kmgt]?$"))
					throw new IllegalArgumentException("bad format for -s");
				suffix = arg.charAt(arg.length() - 1);
				long numPart;
				long multiplier = 1L;
				if(Character.isLetter(suffix))
				{
					switch(suffix)
					{
					case 'k':
						multiplier = 1L<<10;
						break;
					case 'm':
						multiplier = 1L<<20;
						break;
					case 'g':
						multiplier = 1L<<30;
						break;
					case 't':
						multiplier = 1L<<40;
						break;
					default:
						// doesn't reach here
						break;
					}
				
					numPart = Long.parseLong(arg.substring(0, arg.length()-1));
				}
				else
				{
					numPart = Long.parseLong(arg);
				}

				long byteCount = numPart * multiplier;
				if(byteCount % 512 != 0)
					throw new IllegalArgumentException("device size must be a multiple of 512");

				deviceBlockCount = byteCount >> 9;
				
				break;
				
			case 'l':
				parts = getOpt.getOptarg().split(":");
				bindAddress = null;
				bindPort = null;
				if(parts.length == 1)
				{
					bindPort = Integer.parseInt(parts[0]);
					serverBindOn = new InetSocketAddress(bindPort);
				}
				else if(parts.length == 2)
				{
					bindAddress = InetAddress.getByName(parts[0]);
					bindPort = Integer.parseInt(parts[1]);
					serverBindOn = new InetSocketAddress(bindAddress, bindPort);
				}
				else
				{
					throw new IllegalArgumentException("bad format for -l");
				}
				break;
			
			case 'a':
				parts = getOpt.getOptarg().split(":");
				bindAddress = null;
				bindPort = null;
				if(parts.length == 1)
				{
					bindPort = Integer.parseInt(parts[0]);
					managerBindOn = new InetSocketAddress(bindPort);
				}
				else if(parts.length == 2)
				{
					bindAddress = InetAddress.getByName(parts[0]);
					bindPort = Integer.parseInt(parts[1]);
					managerBindOn = new InetSocketAddress(bindAddress, bindPort);
				}
				else
				{
					throw new IllegalArgumentException("bad format for -a");
				}
				break;
			
			case 'e':
				layerArgs.add(new LayerArg(LayerArg.Type.EXPANDABLE_FILE, false, true, getOpt.getOptarg()));
				break;
				
			case 'E':
				layerArgs.add(new LayerArg(LayerArg.Type.EXPANDABLE_FILE, true, false,getOpt.getOptarg()));
				break;
				
			case 'f':
				layerArgs.add(new LayerArg(LayerArg.Type.FLAT_FILE, false, false, getOpt.getOptarg()));
				break;
				
			case 'F':
				layerArgs.add(new LayerArg(LayerArg.Type.FLAT_FILE, true, false, getOpt.getOptarg()));
				break;
				
			case 'n':
				layerArgs.add(new LayerArg(LayerArg.Type.NBD, false, true, getOpt.getOptarg()));
				break;
				
			case 'N':
				layerArgs.add(new LayerArg(LayerArg.Type.NBD, true, false, getOpt.getOptarg()));
				break;
				
			case 'X':
				layerArgs.add(new LayerArg(LayerArg.Type.NBD, true, true, getOpt.getOptarg()));
				break;
				
			case '?':
			case 'h':
				System.err.println();
				System.err.println("general options:");
				System.err.println("    -h                this help");
				System.err.println("    -b blockcount     (-b or -s required) the size of the virtual device,");
				System.err.println("                      in blocks (1 block = 512 bytes)");
				System.err.println("    -s bytecount      (-b or -s required) the size of the virtual device,");
				System.err.println("                      in bytes; can use suffix like K, M, G, T");
				System.err.println();
				System.err.println("server options:");
				System.err.println("    -l [ip:]port      the TCP ip/port to listen on for NBD clients");
				System.err.println("                      (optional; default is 0.0.0.0:" + NBDServer.DEFAULT_PORT + ")");
				System.err.println("    -a [ip:]port      the TCP ip/port to listen on for manager commands");
				System.err.println("                      (optional; default is localhost:" + Manager.DEFAULT_PORT + ")");
				System.err.println();
				System.err.println("to specify layers: (at least one is required)");
				System.err.println("    -e filename       readonly expandable file");
				System.err.println("    -E filename       writable expandable file");
				System.err.println("    -f filename       readonly flat file (file size must == device size)");
				System.err.println("    -F filename       writable flat file (file size must == device size)");
				System.err.println("    -n ip:port        readonly remote NBD device (read cache enabled)");
				System.err.println("    -N ip:port        writable remote NBD host (caching disabled)");
				System.err.println("    -X ip:port        writable remote NBD host w/ assumed exclusive access");
				System.err.println("                      (since exclusive access assumed, cache is enabled)");
				System.err.println();
				return;
			}
		}
		
		if(layerArgs.size() == 0)
			throw new RuntimeException("no layers specified; see -h for help");

		if(deviceBlockCount == null)
			throw new RuntimeException("no device size specified; see -h for help");
		
		// writable layer check: only the topmost layer may be writable
		for(LayerArg arg : layerArgs)
		{
			// top can be writable or read-only, either is ok
			if(arg == layerArgs.getLast())
				continue;
			
			// but layers underneath should not be writable
			if(arg.writable)
				throw new IllegalArgumentException("only topmost layer may be writable");
		}

		// the device
		Device device = new Device(deviceBlockCount);

		// NBD server daemon
		ServerSocket serverSocket = new ServerSocket();
		serverSocket.bind(serverBindOn);
		Server server = new NBDServer(device, serverSocket);
		device.setServer(server);
		
		// management server daemon
		ServerSocket managerSocket = new ServerSocket();
		managerSocket.bind(managerBindOn);
		Manager manager = new Manager(device, managerSocket);
		device.setManager(manager);

		for(LayerArg arg : layerArgs)
		{
			Layer layer;

			switch(arg.type)
			{
			case FLAT_FILE:
				layer = new FlatFile(new File(arg.spec), device, arg.writable, arg.cacheEnabled);
				break;
			
			case EXPANDABLE_FILE:
				layer = new ExpandableFile(new File(arg.spec), device, arg.writable, arg.cacheEnabled);
				break;
				
			case NBD:
				parts = arg.spec.split(":");
				if(parts.length != 2)
					throw new IllegalArgumentException("for NBD layer, format is hostnameorIP:portnumber");
				InetAddress host = InetAddress.getByName(parts[0]);
				int port = Integer.parseInt(parts[1]);
				layer = new NBD(device, arg.writable, arg.cacheEnabled, host, port, null);  // no path support yet
				break;
			
			default:
				throw new RuntimeException(String.format("sorry, can't handle %s yet", arg.type));
			}
			
			device.addLayer(layer);
		}
		
		// device complete
		
		// start up services
		server.start();
		manager.start();
		
		// dump info to stdout
		device.dumpInfo();

		// run until NBD server termination
		server.join();
	}
}
