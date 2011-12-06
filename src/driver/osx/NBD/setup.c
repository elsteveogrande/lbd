#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <netinet/in.h>
#include <stdio.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <sys/time.h>
#include "nbd_ioctl.h"

int main(int argc, char *argv[])
{
	int ret;
	int fd;
	int server_addr_size;
	struct sockaddr *server_addr;
	struct addrinfo hints;
	struct addrinfo *server_addr_info;
	int result;
	ioctl_connect_device_t request;
	uint32_t conn_status;
	int success;
	struct timeval t0;
	struct timeval t1;
	struct timespec sleep_time;
	
	ret = 0;
	
	// open device for ioctl
	fd = open(argv[1], O_RDWR);
	if(fd == -1)
	{
		perror("could not open");
		ret = 1;
		goto out;
	}
	
	// resolve host and port for sockaddr
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = PF_UNSPEC;  // ip v4 or v6
    hints.ai_socktype = SOCK_STREAM;
	result = getaddrinfo(argv[2], argv[3], &hints, &server_addr_info);
	if(result)
	{
		fprintf(stderr, "looking up address: %s\n", gai_strerror(result));
		ret = 2;
		goto out_close;
	}

	// build request and send it
	memset(&request, 0, sizeof(ioctl_connect_device_t));
	request.addr_size = server_addr_info->ai_addrlen;
	server_addr = server_addr_info->ai_addr;
	memcpy(&(request.server), server_addr, request.addr_size);
	request.addr_family = server_addr_info->ai_family;
	request.addr_socktype = server_addr_info->ai_socktype;
	request.addr_protocol = server_addr_info->ai_protocol;

	fprintf(stderr, "sending ioctl IOCTL_CONNECT_DEVICE %08lx\n", IOCTL_CONNECT_DEVICE);
	result = ioctl(fd, IOCTL_CONNECT_DEVICE, &request);
	if(result)
	{
		perror("ioctl");
		ret = 3;
		goto out_close;
	}

	freeaddrinfo(server_addr_info);
	
	fprintf(stderr, "sending ioctl IOCTL_CONNECTIVITY_CHECK %08lx\n", IOCTL_CONNECTIVITY_CHECK);
	
	sleep_time.tv_sec = 0;
	sleep_time.tv_nsec = 1000000000 / 100;
	
	success = 0;
	gettimeofday(&t0, NULL);
	while(! success)
	{
		result = ioctl(fd, IOCTL_CONNECTIVITY_CHECK, &conn_status);
		if(result)
		{
			perror("ioctl");
			ret = 4;
			goto out_close;
		}

		if(conn_status)
		{
			success = 1;
			break;
		}

		gettimeofday(&t1, NULL);
		if( ( ((t1.tv_sec*1000000) + t1.tv_usec) - ((t0.tv_sec*1000000) + t0.tv_usec) ) > 5000000 )
		{
			break;
		}

		nanosleep(&sleep_time, NULL);
	}

	printf("connection status %d\n", success);

out_close:
	close(fd);
	
out:
	return ret;
}


