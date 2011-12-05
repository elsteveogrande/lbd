#include <sys/types.h>
#include <sys/malloc.h>
#include <device.h>
#include <common.h>

/* used in creating device locks */
lck_grp_attr_t *lck_grp_attr;
lck_grp_t *lck_grp;
lck_attr_t *lck_attr;


// store array of device structures (not pointers)
device devices[DEVICE_COUNT];


lck_spin_t * lock_alloc()
{
	return lck_spin_alloc_init(lck_grp, lck_attr);
}


void lock_free(lck_spin_t *lock)
{
	lck_spin_free(lock, lck_grp);
}

