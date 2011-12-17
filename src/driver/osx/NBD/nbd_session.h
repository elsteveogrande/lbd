
#ifndef _NBD_SESSION
#define _NBD_SESSION

#include <sys/types.h>
#include <sys/kpi_socket.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <sys/errno.h>


typedef struct nbd_hello
{
	int valid:1;
	int readonly:1;
	uint64_t size;
} nbd_hello_t;


void nbd_global_init();

errno_t nbd_read_hello(int minor, socket_t socket, nbd_hello_t *result);

errno_t nbd_read(int minor, socket_t socket, unsigned char *buffer, int64_t offset, int64_t length);

errno_t nbd_write(int minor, socket_t socket, unsigned char *buffer, int64_t offset, int64_t length);


#endif
