#include <sys/types.h>                       // (miscfs/devfs/devfs.h, ...)
#include <miscfs/devfs/devfs.h>
#include <sys/buf.h>
#include <sys/fcntl.h>
#include <sys/ioccom.h>
#include <sys/proc.h>
#include <sys/stat.h>
#include <sys/systm.h>
#include <sys/kpi_socket.h>
#include <netinet/in.h>
#include <device.h>
#include <block_dev.h>


extern lck_grp_attr_t *lck_grp_attr;
extern lck_grp_t *lck_grp;
extern lck_attr_t *lck_attr;

extern device devices[];

int bdevsw_slot;
void * control_node;


struct bdevsw bsd_block_dev_functions =
{
    /* d_open     */ dev_open,
    /* d_close    */ dev_close,
    /* d_strategy */ dev_strategy,
    /* d_ioctl    */ dev_ioctl_bdev,
    /* d_dump     */ eno_dump,
    /* d_psize    */ dev_size,
    /* d_type     */ D_DISK
};


kern_return_t device_global_start()
{
	bdevsw_slot = bdevsw_add(-1, &bsd_block_dev_functions);
	if(bdevsw_slot == -1)
	{
		printf("nbd: couldn't get a free bdevsw slot\n");
		return KERN_FAILURE;
	}
	else
	{
		printf("nbd: got slot %d\n", bdevsw_slot);
	}
	
	lck_grp_attr = lck_grp_attr_alloc_init();
	lck_grp = lck_grp_alloc_init("nbdlockgroup", lck_grp_attr);
	return KERN_SUCCESS;
}


void device_global_stop()
{
	lck_attr_free(lck_attr);
	lck_grp_free(lck_grp);
	lck_grp_attr_free(lck_grp_attr);
	
	bdevsw_remove(bdevsw_slot, &bsd_block_dev_functions);
}


void device_init(int minor)
{
	devices[minor].client_block_size = BLOCK_SIZE;
	devices[minor].minor = minor;
	devices[minor].lock = lck_spin_alloc_init(lck_grp, lck_attr);
	
	devices[minor].block_device_node = devfs_make_node(
		makedev(bdevsw_slot, minor),				// major/minor
		DEVFS_BLOCK,								// device type
		UID_ROOT,									// owner user
		GID_OPERATOR,								// owner group
		0640,										// perms
		"nbd%d",									// name format
		minor										// args ...
	  );
}


void device_cleanup(int minor)
{
	if(devices[minor].block_device_node)
		devfs_remove(devices[minor].block_device_node);

	if(devices[minor].lock)
		lck_spin_free(devices[minor].lock, lck_grp);
}


static int device_socket_write(int minor_number, size_t size, void *buffer)
{
	struct iovec msg_data;
	struct msghdr msg;
	int result;
	size_t written;
	
	msg_data.iov_base = buffer;
	msg_data.iov_len = size;
	msg.msg_name = 0;
	msg.msg_namelen = 0;
	msg.msg_iov = &msg_data;
	msg.msg_iovlen = 1;
	msg.msg_controllen = 0;
	msg.msg_flags = 0;
	result = sock_send(devices[minor_number].socket, &msg, 0, &written);
	return result;
}
