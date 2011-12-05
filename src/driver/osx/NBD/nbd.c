#include <libkern/libkern.h>
#include <mach/mach_types.h>
#include <kern/locks.h>
#include <sys/kpi_socket.h>
#include <sys/types.h>

#include <nbd.h>
#include <device.h>
#include <common.h>

extern device devices[];


kern_return_t driver_start(kmod_info_t *ki, void *d)
{
	kern_return_t result;
	
	result = device_global_start();
	if(result)
	{
		printf("nbd: device_global_init: %d (%08x)\n", result, result);
		return result;
	}
	
	bzero(devices, DEVICE_COUNT * sizeof(device));
	
	result = start_bsd_devices();
	if(result)
	{
		printf("nbd: start_bsd_devices: %d (%08x)\n", result, result);
		return result;
	}

	printf("nbd: started\n");
	return KERN_SUCCESS;
}


kern_return_t driver_stop(kmod_info_t *ki, void *d)
{
	printf("nbd: unloading...\n");
	stop_bsd_devices();
	device_global_stop();
	printf("nbd: ... successfully unloaded\n");
	return KERN_SUCCESS;
}


int start_bsd_devices()
{
	int i;
	
	for(i=0; i<DEVICE_COUNT; i++)
	{
		device_init(i);
	}

	return KERN_SUCCESS;
}


void stop_bsd_devices()
{
	int i;

	for(i=0; i<DEVICE_COUNT; i++)
	{
		device_cleanup(i);
	}
}


KMOD_EXPLICIT_DECL(cc.obrien.nbd, "0.8.0", driver_start, driver_stop)
__private_extern__ kmod_start_func_t *_realmain = driver_start;
__private_extern__ kmod_stop_func_t *_antimain = driver_stop;
__private_extern__ int _kext_apple_cc = __APPLE_CC__;
