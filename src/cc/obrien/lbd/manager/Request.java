package cc.obrien.lbd.manager;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import cc.obrien.lbd.Device;
import cc.obrien.lbd.LBD;


/**
 * 
 * @author sobrien
 */
public class Request implements Serializable
{
	/** for serialization */
	private static final long serialVersionUID = 7863162327056931061L;


	/**
	 * @author sobrien
	 */
	public static enum Type
	{
		/** server check (no-op) */
		PING,

		/** terminate all running devices; see {@link LBD#runningDevices} */
		SHUTDOWN,

		/** get each running device's {@link Device#toString()} */
		DEVICE_INFO_STRINGS,
		
		/** add a layer to a running device; currently only writable expandable files supported */
		LIVE_ADD_LAYER,
		
		;
	}
	
	
	/** request type */
	public final Type type;

	
	/** serial (to identify the request) */
	public final long serial;
	
	
	/** args in this request, if any */
	public final List<Object> args;
	

	/**
	 * @param type
	 * @param serial 
	 * @param args
	 */
	public Request(Type type, long serial, List<Object> args)
	{
		this.type = type;
		this.serial = serial;
		this.args = Collections.unmodifiableList(args);
	}
	

	/**
	 * @param type
	 * @param serial
	 * @param args
	 */
	public Request(Type type, long serial, Object... args)
	{
		this(type, serial, Arrays.asList(args));
	}
	
	
	@Override
	public String toString()
	{
		return String.format("[request type=%s serial=%016x args=%s]", type, serial, args);
	}
}
