#include <sys/types.h>
#include <string.h>
#include "nbd_session.h"


errno_t nbd_read_hello(int minor, socket_t socket, nbd_hello_t *hello)
{
	int result;
	int ret;
	mbuf_t buffer = 0;
	size_t size;
	char expect_magic[] = "NBDMAGIC";
	char *ptr;
	
	printf("nbd: trying to read hello %d\n", minor);

	hello->valid = 0;  // until proven otherwise below
	
	ret = 0;
	
	size = 152;  // hello packet size
	result = sock_receivembuf(socket, NULL, &buffer, 0, &size);
	if(result)
	{
		ret = result;
		goto free_buffers;
	}

	ptr = mbuf_datastart(buffer) + mbuf_leadingspace(buffer);
	if(memcmp(ptr, expect_magic, 8))
	{
		printf("nbd: nbd_read_hello: bad magic\n");
		ret = 1;
		goto free_buffers;
	}

	hello->valid = 1;

free_buffers:
	if(buffer)
	{
//		mbuf_freem_list(buffer);
	}
	
out:
	return ret;
}


errno_t nbd_read(int minor, socket_t socket, unsigned char *buffer, int64_t offset, int64_t length)
{
	int i;
	
	for(i=0; i<length; i++)
	{
		buffer[i] = (offset + i) & 0xff;
	}
	
	return 0;
}

