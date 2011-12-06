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
	int fd;
	uint32_t status;
	int result;
	int ret;
	
	ret = 0;
	
	// open device for ioctl
	fd = open(argv[1], O_RDWR);
	if(fd == -1)
	{
		perror("could not open");
		return 1;
	}
	
	result = ioctl(fd, IOCTL_TEARDOWN_DEVICE, &status);
	if(result)
	{
		perror("ioctl");
		ret = 1;
	}

	close(fd);
	
	return ret;
}


