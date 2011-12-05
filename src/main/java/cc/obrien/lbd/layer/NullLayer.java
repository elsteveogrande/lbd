package cc.obrien.lbd.layer;

import java.io.IOException;
import cc.obrien.lbd.Device;


/**
 * A layer (usually the bottommost layer in any virtual device layer stack)
 * that always results in unsuccessful writes and always results in successful reads (of null-bytes).
 * @author sobrien
 */
public final class NullLayer extends Layer
{
	/**
	 * @param device the device this belongs to
	 */
	public NullLayer(Device device)
	{
		super(device, false, false);
	}
	
	
	@Override
	public boolean commitBlock(long startBlock, int arrayOffset, byte[] contents) throws IOException
	{
		return false;
	}

	
	@Override
	public boolean fetchBlock(long startBlock, int arrayOffset, byte[] contents) throws IOException
	{
		for(int i=0; i<512; i++)
			contents[arrayOffset + i] = 0;
		
		return true;
	}
}
