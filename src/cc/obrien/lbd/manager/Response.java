/*
 * (C) 2012 Steve O'Brien.  BSD licensed.
 * see http://www.opensource.org/licenses/BSD-2-Clause
 * and see LICENSE in the root of this project.
 */


package cc.obrien.lbd.manager;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * @author sobrien
 */
public final class Response implements Serializable
{
	/** serialization */
	private static final long serialVersionUID = -2526218966449437416L;

	
	/** request serial, repeated here */
	public final long serial;

	
	/** response status (true iff success) */
	public final boolean status;
	
	
	/** args, if any */
	public final List<Object> args;
	
	
	/**
	 * @param serial
	 * @param status
	 * @param args
	 */
	public Response(long serial, boolean status, List<Object> args)
	{
		this.serial = serial;
		this.status = status;
		this.args = Collections.unmodifiableList(args);
	}

	
	/**
	 * @param serial
	 * @param status
	 * @param args
	 */
	public Response(long serial, boolean status, Object... args)
	{
		this(serial, status, Arrays.asList(args));
	}
	
	
	@Override
	public String toString()
	{
		return String.format("[response serial=%016x status=%5s args=%s]", serial, status, args);
	}
}
