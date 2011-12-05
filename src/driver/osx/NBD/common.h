#ifndef _COMMON_H
#define _COMMON_H

#include <sys/types.h>
#include <kern/locks.h>


#define BLOCK_SIZE 512
#define DEVICE_COUNT 16


lck_spin_t * lock_alloc();
void lock_free(lck_spin_t *lock);


#endif
