GCC=gcc -arch x86_64 -arch i386 -Wall -fmessage-length=0 -nostdinc -fno-builtin -Wno-trigraphs -fno-exceptions -force_cpusubtype_ALL -msoft-float -O0 -fno-common -mkernel -finline -fno-keep-inline-functions -Wmissing-prototypes -Wreturn-type -Wunused-variable -Wshorten-64-to-32 -DDEBUG=1 -DKERNEL -DKERNEL_PRIVATE -DDRIVER_PRIVATE -DAPPLE -DNeXT -isysroot /Developer/SDKs/MacOSX10.7.sdk -fasm-blocks -mmacosx-version-min=10.5 -gdwarf-2 -I/System/Library/Frameworks/Kernel.framework/PrivateHeaders -I/Developer/SDKs/MacOSX10.6.sdk/System/Library/Frameworks/Kernel.framework/Headers -I.
GCCPP=$(GCC) -x c++ -fno-rtti -fcheck-new -fapple-kext
LD=$(CC) -arch x86_64 -arch i386 -isysroot /Developer/SDKs/MacOSX10.6.sdk -mmacosx-version-min=10.5 -lcpp_kext -Xlinker -kext -nostdlib -lkmodc++ -lkmod -lcc_kext

all: kext

nbd: nbd.o nbd_info.o
	$(LD) -o nbd nbd.o nbd_info.o /usr/lib/libkmodc++.a

nbd.o: nbd.cpp
	$(GCCPP) -c -o nbd.o nbd.cpp

nbd_info.o: nbd_info.c
	$(GCC) -c -o nbd_info.o nbd_info.c

kext: nbd
	sudo rm -rf nbd.kext
	mkdir -p nbd.kext/Contents/MacOS
	cp nbd nbd.kext/Contents/MacOS
	cp Info.plist nbd.kext/Contents
	sudo chown -R root:wheel nbd.kext
	sudo chmod -R 755 nbd.kext

clean:
	rm -f nbd *.o setup teardown
	sync  # in case of crash during unload
	echo `sudo kextunload -q nbd.kext 2>&1`
	sudo rm -rf nbd.kext

test: clean kext setup teardown
	sync  # in case of crash during load
	sleep .5
	sync
	sleep .5
	sync
	sleep .5
	sync
	sudo kextload -v nbd.kext

setup: setup.c nbd_ioctl.h
	gcc -std=c99 -O0 -g -o setup setup.c

teardown: teardown.c nbd_ioctl.h
	gcc -std=c99 -O0 -g -o teardown teardown.c
