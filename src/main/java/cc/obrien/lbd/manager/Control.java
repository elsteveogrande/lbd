package cc.obrien.lbd.manager;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;


/**
 * control CLI app
 * @author sobrien
 */
public class Control
{
	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String... args) throws Exception
	{
		Socket managerSocket = new Socket("localhost", Integer.parseInt(args[0]));
		ObjectInputStream in = new ObjectInputStream(managerSocket.getInputStream());
		ObjectOutputStream out = new ObjectOutputStream(managerSocket.getOutputStream());

		Response hello = (Response) in.readObject();
		assert(hello.serial == 0x1111222233334444L);

		Request request;
		Response response;

		if(args[1].equals("add"))
		{
			
			request = new Request(Request.Type.LIVE_ADD_LAYER, 0, args[2]);
			out.writeObject(request);
			out.flush();
			
			response = (Response) in.readObject();
			assert(response.status);
			
			request = new Request(Request.Type.DEVICE_INFO_STRINGS, 0);
			out.writeObject(request);
			out.flush();
	
			response = (Response) in.readObject();
			assert(response.status);
			System.out.println(response.args.get(0));
		}
		else if(args[1].equals("stop"))
		{
			request = new Request(Request.Type.SHUTDOWN_ALL, 0);
			out.writeObject(request);
			out.flush();
			
			response = (Response) in.readObject();
			assert(response.status);
		}
	}
}
