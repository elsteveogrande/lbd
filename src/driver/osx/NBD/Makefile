CC=gcc -x c -g -mmacosx-version-min=10.6 -arch i386 -arch x86_64
INCLUDE=-I/System/Library/Frameworks/Kernel.Framework/Headers -I.
SYS=-isysroot /Developer/SDKs/MacOSX10.6.sdk
COMPILE_D=-DKERNEL -DDEBUG
COMPILE_F=-fno-builtin -fno-common -msoft-float -mlong-branch
COMPILE=$(CC) $(INCLUDE) $(COMPILE_D) $(COMPILE_F) -Wall
LINK=$(CC) $(SYS) -Xlinker -kext -nostdlib -r -lkmod -lcc_kext -lgcc -static
BOTH=$(CC) $(INCLUDE) $(COMPILE_D) $(COMPILE_F) $(SYS) -Xlinker -kext -nostdlib -r -lkmod -lcc_kext -lgcc -static

all: kext setup

nbd: nbd.c nbd.h device.c device.h common.c common.h nbd_ioctl.h block_dev.h block_dev.c
	$(BOTH) -o nbd nbd.c device.c common.c block_dev.c

kext: nbd
	sudo rm -rf NBD.kext
	mkdir -p NBD.kext/Contents/MacOS
	cp nbd NBD.kext/Contents/MacOS
	cp Info.plist NBD.kext/Contents
	sudo chown -R root:wheel NBD.kext
	sudo chmod -R 755 NBD.kext

clean:
	rm -f nbd *.o
	sync  # in case of crash during unload
	echo `sudo kextunload -q NBD.kext 2>&1`
	sudo rm -rf NBD.kext

test: clean kext
	sync  # in case of crash during load
	sleep .5
	sync
	sleep .5
	sync
	sleep .5
	sync
	sudo kextload -v NBD.kext

setup: setup.c nbd_ioctl.h
	gcc -std=c99 -O0 -g -o setup setup.c
