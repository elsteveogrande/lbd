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


void device_dealloc(int minor)
{
	if(devices[minor].socket)
		sock_close(devices[minor].socket);  // note: this generates a call to "socket_event"
	
	if(devices[minor].block_device_node)
		devfs_remove(devices[minor].block_device_node);

	if(devices[minor].lock)
		lck_spin_free(devices[minor].lock, lck_grp);
}


// NOTE: do while we have the spinlock
void device_teardown(int minor)
{
	printf("nbd: teardown %d\n", minor);
	
	if(devices[minor].socket)
	{
		//sock_close(devices[minor].socket);
	}
	
	device_wipe(minor);
}


// NOTE: do while we have the spinlock
void device_wipe(int minor)
{	
	// zero out / reinitialize the appropriate fields.  Do not wipe the following: minor, lock
	devices[minor].writable = 0;
	devices[minor].size = 0;
	devices[minor].socket = 0;
	devices[minor].opened_by = 0;
	devices[minor].client_block_size = BLOCK_SIZE;
	memset( &(devices[minor].server), 0, sizeof(struct sockaddr_storage) );
}
