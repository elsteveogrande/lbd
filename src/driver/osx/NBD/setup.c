#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <netinet/in.h>
#include <stdio.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>


int main(int argc, char *argv[])
{
	int fd;
	int server_addr_size;
	struct sockaddr_storage server_addr;
	struct addrinfo hints;
	struct addrinfo *server_addr_info;
	int result;
	
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
	
	server_addr_size = server_addr_info->ai_addrlen;
	memset(&server_addr, 0, sizeof(struct sockaddr_storage));
	memcpy(&server_addr, server_addr_info->ai_addr, server_addr_size);

	printf("%d\n", server_addr_size); 
	for(int i=0; i<server_addr_size; i++)
		printf("%02x ", ((char *)(&(server_addr)))[i] & 0xff);
	printf("\n");
	
	close(fd);
	
	return 0;
}


