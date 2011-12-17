#import <IOKit/storage/IOBlockStorageDevice.h>
#import <IOKit/IOLib.h>


#define super IOBlockStorageDevice


class cc_obrien_nbd_NBDBlockDevice : public IOBlockStorageDevice
{
	OSDeclareDefaultStructors(cc_obrien_nbd_NBDBlockDevice)
	
public:
	
	virtual bool init(OSDictionary *properties = 0);
	virtual bool start(IOService *provider);
	virtual void stop(IOService *provider);
	virtual void free();
	virtual IOService * probe(IOService *provider, SInt32 *score);
	virtual bool handleOpen(IOService *client, IOOptionBits options, void *access);
	virtual bool handleIsOpen(const IOService *client) const;
	virtual void handleClose(IOService *client, IOOptionBits options);
	virtual IOReturn doEjectMedia();
	virtual IOReturn doFormatMedia(UInt64);
	virtual UInt32 doGetFormatCapacities(UInt64 *, UInt32) const;
	virtual IOReturn doLockUnlockMedia(bool);
	virtual IOReturn doSynchronizeCache();
	virtual char * getVendorString();
	virtual char * getProductString();
	virtual char * getRevisionString();
	virtual char * getAdditionalDeviceInfoString();
	virtual IOReturn reportBlockSize(UInt64 *);
	virtual IOReturn reportEjectability(bool *);
	virtual IOReturn reportLockability(bool *);
	virtual IOReturn reportMaxValidBlock(UInt64 *);
	virtual IOReturn reportMediaState(bool *, bool *);
	virtual IOReturn reportPollRequirements(bool *, bool *);
	virtual IOReturn reportRemovability(bool *);
	virtual IOReturn reportWriteProtection(bool *);
	virtual IOReturn getWriteCacheState(bool*);
	virtual IOReturn setWriteCacheState(bool);
	virtual IOReturn doAsyncReadWrite(IOMemoryDescriptor*, UInt64, UInt64, IOStorageAttributes*, IOStorageCompletion*);
};
