package cc.obrien.lbd.util;

import java.io.DataInputStream;
import java.io.IOException;


/**
 * Useful I/O functions
 * @author sobrien
 */
public final class IOUtil
{
	/**
	 * @param in
	 * @param buffer
	 * @param offset
	 * @param length
	 * @return the number of bytes read until EOF
	 * @throws IOException
	 */
	public static int ensureRead(DataInputStream in, byte buffer[], int offset, int length) throws IOException
	{
		int counter = 0;
		while(counter < length)
		{
			int remaining = length - counter;
			int result = in.read(buffer, offset + counter, remaining);
			if(result < 0)
				break;
			counter += result;
		}
		
		return counter;
	}
	
	
	/**
	 * @param in
	 * @param buffer
	 * @return result of ensureRead(in, buffer, 0, buffer.length)
	 * @throws IOException
	 * @see #ensureRead(DataInputStream, byte[], int, int)
	 */
	public static int ensureRead(DataInputStream in, byte buffer[]) throws IOException
	{
		return ensureRead(in, buffer, 0, buffer.length);
	}
}
