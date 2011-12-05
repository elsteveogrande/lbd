package cc.obrien.lbd.server;

import cc.obrien.lbd.Device;


/**
 * A way for clients to access data on the virtual device.
 * Handles concerns like I/O with the outside world.
 * Must consider thread-safety (a VirtualDevice is intended to be thread-safe).
 * 
 * <p>
 * Subclasses must implement the {@link Runnable#run()} method
 * and in their constructors remember to {@link #start()} themselves.
 * </p>
 * 
 * @author sobrien
 */
public abstract class Server extends Thread
{
	/** the device this serves for */
	protected final Device device;

	
	/**
	 * initializes self as a daemon thread;
	 * subclass's constructor does the rest
	 * @param device the device this endpoint serves for
	 */
	protected Server(Device device)
	{
		this.device = device;
		this.setDaemon(true);
	}
	
	
	@Override
	public String toString()
	{
		return this.getClass().getSimpleName();
	}
	
	
	/**
	 * signal to the endpoint that it should stop.
	 */
	abstract public void stopServer();
}
