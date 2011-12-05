#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <netinet/in.h>
#include <stdio.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>
#include "nbd_ioctl.h"


int main(int argc, char *argv[])
{
	int fd;
	int server_addr_size;
	struct sockaddr *server_addr;
	struct addrinfo hints;
	struct addrinfo *server_addr_info;
	int result;
	ioctl_connect_device_t request;
	
	// open device for ioctl
	fd = open(argv[1], O_RDWR);
	if(fd == -1)
	{
		perror("could not open");
		return 1;
	}
	
	// resolve host and port for sockaddr
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = PF_UNSPEC;  // ip v4 or v6
    hints.ai_socktype = SOCK_STREAM;
	result = getaddrinfo(argv[2], argv[3], &hints, &server_addr_info);
	if(result)
	{
		perror("error on lookup");
		return 2;
	}

	// build request and send it
	memset(&request, 0, sizeof(ioctl_connect_device_t));
	request.addr_size = server_addr_info->ai_addrlen;
	server_addr = server_addr_info->ai_addr;
	memcpy(&(request.server), server_addr, request.addr_size);
	request.addr_family = server_addr_info->ai_family;
	request.addr_socktype = server_addr_info->ai_socktype;
	request.addr_protocol = server_addr_info->ai_protocol;

	fprintf(stderr, "sending ioctl %08lx\n", IOCTL_CONNECT_DEVICE);
	result = ioctl(fd, IOCTL_CONNECT_DEVICE, &request);
	if(result)
	{
		perror("ioctl");
		return 3;
	}

	close(fd);
	
	return 0;
}


