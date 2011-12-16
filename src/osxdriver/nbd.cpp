#import <IOKit/storage/IOBlockStorageDevice.h>

class cc_obrien_lbd_LBDBlockDevice : public IOBlockStorageDevice
{
	OSDeclareDefaultStructors(cc_obrien_lbd_LBDBlockDevice)
	
public:
	virtual void free();
	virtual bool handleOpen(IOService *client, IOOptionBits options, void *access);
	virtual bool handleIsOpen(const IOService *client) const;
	virtual void handleClose(IOService *client, IOOptionBits options);
};
