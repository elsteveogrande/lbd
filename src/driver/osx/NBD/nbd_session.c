#include <sys/types.h>
#include <string.h>
#include <sys/malloc.h>
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


typedef struct fake_block
{
	long long block_number;
	unsigned char data[512];
} fake_block_t;
	

#define FAKE_SECTORS 1048576
fake_block_t fake_blocks[FAKE_SECTORS];


void nbd_global_init()
{
	int i;
	
	for(i=0; i<FAKE_SECTORS; i++)
	{
		fake_blocks[i].block_number = -1;
	}
}


void * fake_get_block(long long block_number)
{
	int i;
	void *mem;
	
	for(i=0; i<FAKE_SECTORS; i++)
	{
		if(fake_blocks[i].block_number == -1)
		{
			fake_blocks[i].block_number = block_number;
		}
		else
		{
			if(fake_blocks[i].block_number == block_number)
			{
				return &(fake_blocks[i].data);
			}
		}
	}
	
	if(i == FAKE_SECTORS)
	{
		return -1;
	}
	
	return 0;
}


static errno_t fake_read(unsigned char *buffer, int64_t offset, int64_t length)
{
	int64_t p;
	void *ptr;
	
	p = 0;
	while(p < length)
	{
		ptr = fake_get_block((offset + p) >> 9);
		if(ptr == -1)
		{
			return ENOMEM;
		}

		memcpy(buffer + p, ptr, 512);
		p += 512;
	}
	
	return 0;
}


static errno_t fake_write(unsigned char *buffer, int64_t offset, int64_t length)
{
	int64_t p;
	void *ptr;
	int i;
	
	p = 0;
	while(p < length)
	{
		ptr = fake_get_block((offset + p) >> 9);
		if(ptr == -1)
		{
			return ENOMEM;
		}
		
		memcpy(ptr, buffer + p, 512);
		p += 512;
	}

	
	return 0;
}


errno_t nbd_read(int minor, socket_t socket, unsigned char *buffer, int64_t offset, int64_t length)
{
	return fake_read(buffer, offset, length);
}


errno_t nbd_write(int minor, socket_t socket, unsigned char *buffer, int64_t offset, int64_t length)
{
	return fake_write(buffer, offset, length);
}
