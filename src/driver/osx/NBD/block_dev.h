#ifndef _BLOCK_DEV_H
#define _BLOCK_DEV_H

int  dev_open(dev_t dev, int flags, int devtype, proc_t proc);
int  dev_close(dev_t dev, int flags, int devtype, proc_t proc);
int  dev_size(dev_t dev);    
void dev_strategy(buf_t bp);
int  dev_ioctl_bdev(dev_t dev, u_long cmd, caddr_t data, int flags, proc_t proc);

#endif
