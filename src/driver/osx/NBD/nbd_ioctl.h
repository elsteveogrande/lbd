#ifndef _NBD_IOCTL_H
#define _NBD_IOCTL_H


#include <sys/ioccom.h>
#include <sys/ioctl.h>
#include <netinet/in.h>


typedef struct ioctl_connect_device
{
	// true size of the below "addr" member
	int addr_size;

	// size of a sockddr will vary, see addr_size for true size; can be sockaddr_in, sockaddr_in6, etc.
	// using a union of sockaddr with large byte array, to ensure consistent sizing
	// (the kernel-mode compile versus userland compile had 'sockaddr_storage' compile to different sizes...)
	union
	{
		struct sockaddr storage;
		char bytes[252];   // try to make this struct an even 256 bytes
	} server;
} ioctl_connect_device_t;


#define IOCTL_CONNECT_DEVICE            _IOW('C',  1, ioctl_connect_device_t)
#define IOCTL_CONNECTIVITY_CHECK		_IOR('C',  2, uint32_t)
#define IOCTL_TEARDOWN_DEVICE           _IOW('C',  3, uint32_t)

#endif
