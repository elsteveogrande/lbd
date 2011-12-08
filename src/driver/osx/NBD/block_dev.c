#include <libkern/libkern.h>
#include <device.h>
#include <common.h>
#include <nbd_ioctl.h>
#include <sys/kpi_socket.h>
#include <netinet/in.h>
#include <sys/conf.h>
#include <sys/disk.h>
#include <block_dev.h>
#include "nbd_session.h"


extern device devices[];


int  dev_open(dev_t bsd_dev, int flags, int devtype, proc_t proc)
{
	int ret;
	int minor_number;
	
	minor_number = minor(bsd_dev);
	device *dev = &(devices[minor_number]);
	printf("nbd: dev_open %d (%08x) minor=%d\n", bsd_dev, bsd_dev, minor_number);

	ret = -1;

	// get exclusive lock while we check device's state
	printf("nbd: dev_open: minor %d: proc: %p: spinlock...\n", minor_number, proc);
	lck_spin_lock(dev->lock);
	
	// locked by us

	if(dev->opened_by && dev->opened_by != proc)
	{
		ret = EBUSY;
		goto unlock;
	}
	
	// successful
	
	dev->opened_by = proc;
	ret = 0;

unlock:
	lck_spin_unlock(dev->lock);
	printf("nbd: dev_open: minor %d: proc: %p: spinlock released\n", minor_number, proc);
	
out:
	printf("nbd: open: returning %d (%08x)\n", ret, ret);
	return ret;
}


int  dev_close(dev_t bsd_dev, int flags, int devtype, proc_t proc)
{
	int ret;
	int minor_number;
	
	minor_number = minor(bsd_dev);
	device *dev = &(devices[minor_number]);

	printf("nbd: dev_close %d (%08x) minor=%d dev=%p\n", bsd_dev, bsd_dev, minor(bsd_dev), dev);

	if(dev->opened_by != proc)
	{
		ret = EINVAL;
		goto out;
	}

	// can close; wipe out client-open state (keep the socket state)
	dev->opened_by = NULL;
	dev->client_block_size = BLOCK_SIZE;

out:
	return ret;
}


int  dev_size(dev_t bsd_dev)
{
	printf("nbd: dev_size minor=%d returning %d\n", minor(bsd_dev), BLOCK_SIZE);
	return BLOCK_SIZE;
}


void dev_strategy(buf_t bp)
{
	dev_t bsd_dev;
	long long byte_count;
	int minor_number;
	long long starting_block;
	long long starting_byte;
	int is_read;
	int is_write;
	device *dev;
	int ret;
	void * buffer;
	size_t buffer_size;
	
	bsd_dev = buf_device(bp);
	minor_number = minor(bsd_dev);
	dev = &(devices[minor_number]);
	
	byte_count = buf_count(bp);
	starting_block = buf_blkno(bp);
	starting_byte = starting_block * BLOCK_SIZE;
	is_read = (buf_flags(bp) & B_READ) ? 1 : 0;
	is_write = ! is_read;  // there's no B_WRITE flag

	buffer = (void *) buf_dataptr(bp);
	buffer_size = buf_size(bp);

	printf("nbd: strategy minor=%d read=%d write=%d start@ block=%lld offset=0x%016llx bytecount=%lld buffer=%p buffersize=%ld\n", minor_number, is_read, is_write, starting_block, starting_byte, byte_count, buffer, buffer_size);

	lck_spin_lock(dev->lock);

	if( ! (dev->socket && sock_isconnected(dev->socket)) )
	{
		ret = EIO;
		goto unlock;
	}
	
	if( ((long long) buffer_size) < byte_count )
	{
		ret = EIO;
		goto unlock;
	}
	
	ret = nbd_read(minor_number, dev->socket, buffer, starting_byte, byte_count);

unlock:
	lck_spin_unlock(dev->lock);

out:
	buf_seterror(bp, ret);
	buf_biodone(bp);
}


// XXX FIXME can't divide by block size; have to shift right this many bits.  How to get divdi3 linked into static binary?!
static int log_2(uint64_t x)
{
	int ret = 0;
	while(x > 0)
	{
		x = x >> 1;
		ret++;
	}
	
	return ret;
}


static void socket_event(socket_t socket, void *cookie, int waitf)
{
	int minor_number;
	device *dev;
	
	minor_number = (int) (long) cookie;
	dev = &(devices[minor_number]);
	
	lck_spin_lock(dev->lock);
	do
	{

		// an (unreliable) attempt to verify we are still talking about the same socket...
		if(dev->socket != socket)
		{
			break;
		}

		printf("nbd: device %d: socket event\n", minor_number);

		if(! sock_isconnected(socket))
		{
			printf("nbd: device %d: not connected\n", minor_number);
		}
	
	} while(0);
	lck_spin_unlock(dev->lock);
}


// XXX need mutex when calling this!
int try_reconnect_async(int minor)
{
	device *dev;
	int ret;
	int result;
	
	dev = & (devices[minor]);
	
	// ditch old socket, if any
	if(dev->socket)
	{
		sock_close(dev->socket);
		dev->socket = 0;
	}

	ret = 0;

	// new socket
	ioctl_connect_device_t *server_info = &(dev->server_info);
	result = sock_socket(server_info->addr_family, server_info->addr_socktype, server_info->addr_protocol, NULL, (void*) (long) minor, &(dev->socket));
	if(result)
	{
		printf("nbd: ioctl_connect: during try_reconnect_async: %d\n", result);
		ret = result;
		goto out;
	}

	// try to connect (asynchronously; nonblocking call)
	result = sock_connect(dev->socket, &(server_info->server.addr), MSG_DONTWAIT);
	if(result != EINPROGRESS)
	{
		printf("nbd: ioctl_connect: during try_reconnect_async: %d\n", result);
		sock_close(dev->socket);
		dev->socket = 0;
		ret = result;
		goto out;
	}
	
out:
	return ret;
}


int  dev_ioctl_bdev(dev_t bsd_dev, u_long cmd, caddr_t data, int flags, proc_t proc)
{
	int ret;
	int result;
	int minor_number;
	ioctl_connect_device_t *ioctl_connect;
	struct sockaddr * server_sockaddr;
	socket_t socket;
	int i;
	nbd_hello_t hello;
	
	minor_number = minor(bsd_dev);
	
	device *dev = &(devices[minor_number]);

	ret = 0;

	switch(cmd)
	{
		
	case DKIOCGETBLOCKSIZE:  // uint32_t: block size

		lck_spin_lock(dev->lock);
		do
		{

			*(uint32_t *)data = dev->client_block_size;

		} while(0);
		lck_spin_unlock(dev->lock);
		break;
	
	
	case DKIOCSETBLOCKSIZE:

		lck_spin_lock(dev->lock);
		do
		{

			dev->client_block_size = *(uint32_t *) data;
			if(dev->client_block_size < 0 || dev->client_block_size > 512)
			{
				dev->client_block_size = 512;
			}

		} while(0);
		lck_spin_unlock(dev->lock);
		break;
	
	
	case DKIOCGETBLOCKCOUNT:  // uint64_t: block count

		lck_spin_lock(dev->lock);
		do
		{

			if(! dev->socket)
			{
				ret = ENXIO;
				break;
			}
			*(long long *)data = dev->size >> log_2((uint64_t)(dev->client_block_size));

		} while(0);
		lck_spin_unlock(dev->lock);

		break;
		
	
	case IOCTL_CONNECT_DEVICE:

		lck_spin_lock(dev->lock);
		do
		{
		
			ioctl_connect = (ioctl_connect_device_t *) data;
			server_sockaddr = (struct sockaddr *) &(ioctl_connect->server);

			// already connected?
			if(dev->socket)
			{
				ret = EBUSY;
				break;
			}

			memcpy(&(dev->server_info), ioctl_connect, sizeof(ioctl_connect_device_t));
			
			result = try_reconnect_async(minor_number);
			if(result != EINPROGRESS)
			{
				// if not EINPROGRESS, then this is an error
				ret = result;
			}
		
		} while(0);
		lck_spin_unlock(dev->lock);		
		break;


	case IOCTL_CONNECTIVITY_CHECK:  // uint32_t: connected boolean

		lck_spin_lock(dev->lock);
		do
		{

			socket = dev->socket;
			if(! socket)
			{
				ret = ENXIO;
				break;
			}

			*(int *)data = sock_isconnected(socket);
		
		} while(0);
		lck_spin_unlock(dev->lock);
		break;


	case IOCTL_READ_PARAMS:			// uint32_t: status bool

		lck_spin_lock(dev->lock);
		do
		{
			
			socket = dev->socket;
			if(! socket)
			{
				ret = ENXIO;
				break;
			}

			if(! sock_isconnected(socket))
			{
				ret = ENXIO;
				break;
			}
			
			ret = nbd_read_hello(minor_number, socket, &hello);
			if(ret || !hello.valid)
			{
				// disconnect and leave in invalid state until teardown
				sock_shutdown(socket, SHUT_RDWR);
				
				if(!ret)
				{
					ret = EIO;
				}
			}

			*(int *)data = (ret == 0);

		} while(0);
		lck_spin_unlock(dev->lock);
		break;
		
		
	case IOCTL_TEARDOWN_DEVICE:		// uint32_t: just say 1 to acknowledge
		printf("nbd: spinlock %d for teardown...\n", minor_number);

		lck_spin_lock(dev->lock);
		do
		{

			if(! dev->socket)
			{
				ret = EINVAL;
				break;
			}
						
			device_teardown(minor_number);
			*(int *)data = 1;

		} while(0);
		lck_spin_unlock(dev->lock);
		break;
	
	
	case -123123123:  // uint32_t: something

		lck_spin_lock(dev->lock);
		do
		{

			// meat of ioctl goes here

		} while(0);
		lck_spin_unlock(dev->lock);		
		break;
	
	
	default:
		printf("nbd: unknown dev_ioctl_bdev %d (%08x) minor=%d dev=%p cmd=%08lx data=%p flags=%d proc=%p\n", bsd_dev, bsd_dev, minor(bsd_dev), dev, cmd, data, flags, proc);
		ret = ENOTTY;
	}

	return ret;
}
