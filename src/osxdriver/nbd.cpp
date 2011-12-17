#import <IOKit/storage/IOBlockStorageDevice.h>
#import <IOKit/IOLib.h>
#import "nbd.h"


OSDefineMetaClassAndStructors(cc_obrien_nbd_NBDBlockDevice, IOBlockStorageDevice)

	
bool cc_obrien_nbd_NBDBlockDevice::init(OSDictionary *properties)
{
	return false;
}


bool cc_obrien_nbd_NBDBlockDevice::start(IOService *provider)
{
	return false;
}


void cc_obrien_nbd_NBDBlockDevice::free()
{	
}


IOReturn cc_obrien_nbd_NBDBlockDevice::doEjectMedia()
{
}


IOReturn cc_obrien_nbd_NBDBlockDevice::doFormatMedia(UInt64)
{
}


UInt32 cc_obrien_nbd_NBDBlockDevice::doGetFormatCapacities(UInt64 *, UInt32) const
{
}


IOReturn cc_obrien_nbd_NBDBlockDevice::doLockUnlockMedia(bool)
{
}


IOReturn cc_obrien_nbd_NBDBlockDevice::doSynchronizeCache()
{
}


char * cc_obrien_nbd_NBDBlockDevice::getVendorString()
{
}


char * cc_obrien_nbd_NBDBlockDevice::getProductString()
{
}


char * cc_obrien_nbd_NBDBlockDevice::getRevisionString()
{
}


char * cc_obrien_nbd_NBDBlockDevice::getAdditionalDeviceInfoString()
{
}


IOReturn cc_obrien_nbd_NBDBlockDevice::reportBlockSize(UInt64 *)
{
}


IOReturn cc_obrien_nbd_NBDBlockDevice::reportEjectability(bool *)
{
}


IOReturn cc_obrien_nbd_NBDBlockDevice::reportLockability(bool *)
{
}


IOReturn cc_obrien_nbd_NBDBlockDevice::reportMaxValidBlock(UInt64 *)
{
}


IOReturn cc_obrien_nbd_NBDBlockDevice::reportMediaState(bool *, bool *)
{
}


IOReturn cc_obrien_nbd_NBDBlockDevice::reportPollRequirements(bool *, bool *)
{
}


IOReturn cc_obrien_nbd_NBDBlockDevice::reportRemovability(bool *)
{
}


IOReturn cc_obrien_nbd_NBDBlockDevice::reportWriteProtection(bool *)
{
}


IOReturn cc_obrien_nbd_NBDBlockDevice::getWriteCacheState(bool*)
{
}


IOReturn cc_obrien_nbd_NBDBlockDevice::setWriteCacheState(bool)
{
}


IOReturn cc_obrien_nbd_NBDBlockDevice::doAsyncReadWrite(IOMemoryDescriptor*, UInt64, UInt64, IOStorageAttributes*, IOStorageCompletion*)
{
}

