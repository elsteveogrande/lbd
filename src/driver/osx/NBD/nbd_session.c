#include <sys/types.h>
#include <string.h>
#include "nbd_session.h"


/**
 * @return errno
 */
static int receive(socket_t socket, char *buffer, int length)
{
	mbuf_t mbuf;
	size_t size;
	int result;
	int ret;
	buffer;
	int total;
	int partial_size;
	mbuf_t tmp_buf_packet;
	mbuf_t tmp_buf_chain;
	char *ptr;
	
	ret = 0;
	total = 0;
	while(total < length)
	{
		mbuf = 0;
		size = length - total;
		result = sock_receivembuf(socket, NULL, &mbuf, MSG_WAITALL, &size);
		if(result)
		{
			ret = result;
			break;
		}

		tmp_buf_packet = mbuf;
		while(tmp_buf_packet)
		{
			tmp_buf_chain = tmp_buf_packet;
			while(tmp_buf_chain)
			{
				ptr = (char *)  ( ((long)mbuf_datastart(tmp_buf_chain)) + ((long)mbuf_leadingspace(tmp_buf_chain)) );
				memcpy(&(buffer[total]), ptr, mbuf_len(tmp_buf_chain));
				total += mbuf_len(tmp_buf_chain);
				tmp_buf_chain = (mbuf_t) mbuf_next(tmp_buf_chain);
			}
			
			tmp_buf_packet = mbuf_nextpkt(tmp_buf_packet);
		}
	
		mbuf_freem_list(mbuf);
	}
	
	return ret;
}


errno_t nbd_read_hello(int minor, socket_t socket, nbd_hello_t *hello)
{
	int result;
	int ret;
	char expect_magic[] = "NBDMAGIC";
	char buffer[152];
	
	printf("nbd: trying to read hello %d\n", minor);

	hello->valid = 0;  // until proven otherwise below
	
	ret = 0;
	
	result = receive(socket, buffer, 152);
	if(result)
	{
		ret = result;
		goto out;
	}

	if(memcmp(buffer, expect_magic, 8))
	{
		printf("nbd: nbd_read_hello: bad magic\n");
		ret = EILSEQ;
		goto out;
	}

	hello->valid = 1;

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

