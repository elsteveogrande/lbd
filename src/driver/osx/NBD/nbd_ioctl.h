#ifndef _NBD_IOCTL_H
#define _NBD_IOCTL_H


#include <sys/kpi_socket.h>
#include <sys/ioccom.h>
#include <sys/ioctl.h>
#include <netinet/in.h>


typedef struct ioctl_connect_device
{
	// device
	int minor_number;

	// true size of the below "addr" member
	int addr_size;

	// size of this will vary, see addr_size for true size; can be sockaddr_in, sockaddr_in6, etc.
	struct sockaddr server;
} ioctl_connect_device_t;


#define IOCTL_CONNECT_DEVICE            _IOW('C',  1, ioctl_connect_device_t)
#define IOCTL_CONNECTIVITY_CHECK		_IOR('C',  2, uint32_t)
#define IOCTL_TEARDOWN_DEVICE           _IOW('C',  3, uint32_t)

#endif
