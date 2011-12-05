package cc.obrien.lbd.util;

import java.util.HashMap;
import java.util.LinkedList;


/**
 * Contains a fixed-size list of key-value mappings and determines eviction order.
 * When an item is used, it's marked as such, and when it comes up for eviction, goes to the end of the queue.
 * See here for a description of the structure: http://en.wikipedia.org/wiki/Page_replacement_algorithm#Second-chance
 * But note that we cheat a little; see {@link #used}
 * @author sobrien
 * @param <K> any kind of key
 * @param <V> the item being cached
 */
public class FIFOCache<K, V>
{
	/** contents actually stored here */
	private final HashMap<K, V> contents = new HashMap<K, V> ();

	
	/** the FIFO of victim keys (who may be spared; see {@link #used}) */
	private final HashMap<K, Boolean> used = new HashMap<K, Boolean> ();

	
	/** fifo */
	private final LinkedList<K> queue = new LinkedList<K> ();

	
	/** max size we'll allow the contents map to get */
	public final int maxSize;

	
	/**
	 * @param maxSize maximum size this can grow to before evicting old stuff
	 */
	public FIFOCache(int maxSize)
	{
		this.maxSize = maxSize;
	}
	

	/**
	 * @return size of this collection
	 */
	synchronized public int size()
	{
		return this.used.size();
	}
	
	
	/**
	 * debugging
	 * @param c
	 */
	private static void boop(char c)
	{
		System.err.print(c);
		System.err.flush();
	}
	
	
	/**
	 * find the next victim and remove it
	 */
	synchronized private void evict()
	{
		if(this.queue.size() < maxSize)
		{
			// called when not really empty!  return.  Just for sanity.
			return;
		}

		K victim = null;
		while(victim == null)
		{
			victim = this.queue.pop();
			if(Boolean.TRUE.equals(this.used.get(victim)))
			{
				this.used.remove(victim);
				this.queue.add(victim);
				victim = null;
			}
		}

		boop('X');
		this.contents.remove(victim);
	}
	
	
	/**
	 * add to cache
	 * @param key
	 * @param value
	 */
	synchronized public void add(K key, V value)
	{
		if(! this.contents.containsKey(key))
		{
			if(this.size() >= maxSize)
				this.evict();
			
			this.queue.add(key);
			boop('A');
		}
		else
		{
			boop('a');
		}
		
		this.contents.put(key, value);
	}
	
	
	/**
	 * @param key checked against keys using {@link Object#equals(Object)}
	 * @return the value stored under key, or <code>null</code> if not found
	 */
	synchronized public V find(K key)
	{
		V ret = this.contents.get(key);
		if(ret != null)
		{
			boop('h');
			this.used.put(key, true);
		}
		else
		{
			boop('M');
		}
		
		return ret;
	}
}
