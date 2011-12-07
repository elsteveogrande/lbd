#ifndef _DEVICE_H
#define _DEVICE_H

#include <sys/types.h>                       // (miscfs/devfs/devfs.h, ...)
#include <miscfs/devfs/devfs.h>
#include <sys/buf.h>
#include <sys/fcntl.h>
#include <sys/ioccom.h>
#include <sys/proc.h>
#include <sys/stat.h>
#include <sys/systm.h>
#include <sys/conf.h>
#include <libkern/libkern.h>
#include <common.h>
#include <sys/kpi_socket.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include "nbd_ioctl.h"


typedef struct device
{
	int minor:8;							// minor device number
	void * block_device_node;				// the devfs node
	lck_spin_t *lock;						// for mutex operations
	int client_block_size;					// block size requested by the process who opened this; by default, BLOCK_SIZE (in initialization, or on device close)

	int writable:1;							// can be written?  Or is it readonly (if 0)?
	long long size;							// determined size of the nbd device, in bytes
	socket_t socket;						// socket to server
	proc_t opened_by;						// the process who has this open, or 0.  Exclusive to this 

	int server_valid:1;						// 1 if server address here is valid; 0 if not populated
	ioctl_connect_device_t server_info;		// a copy of the connect ioctl structure; contains server addr and related info
} device;


kern_return_t device_global_start();

void device_global_stop();
void device_init(int minor);
void device_dealloc(int minor);
void device_teardown(int minor);
void device_wipe(int minor);


#endif
