GPP=g++ -arch x86_64 -arch i386 -fmessage-length=0 -nostdinc -fno-builtin -Wno-trigraphs -fno-exceptions -fno-rtti -fcheck-new -force_cpusubtype_ALL -msoft-float -O0 -fno-common -mkernel -finline -fno-keep-inline-functions -Wmissing-prototypes -Wreturn-type -Wunused-variable -Wshorten-64-to-32 -DDEBUG=1 -DKERNEL -DKERNEL_PRIVATE -DDRIVER_PRIVATE -DAPPLE -DNeXT -isysroot /Developer/SDKs/MacOSX10.7.sdk -fapple-kext -fasm-blocks -mmacosx-version-min=10.5 -gdwarf-2 -I/System/Library/Frameworks/Kernel.framework/PrivateHeaders -I/Developer/SDKs/MacOSX10.7.sdk/System/Library/Frameworks/Kernel.framework/Headers -I.
LD=g++ -arch x86_64 -arch i386 -isysroot /Developer/SDKs/MacOSX10.7.sdk -mmacosx-version-min=10.7 -lcpp_kext -Xlinker -kext -nostdlib -lkmodc++ -lkmod -lcc_kext

all: kext

nbd: nbd.o
	$(LD) -o nbd nbd.o

nbd.o: nbd.cpp
	$(GPP) -c -o nbd.o nbd.cpp

kext: nbd
	sudo rm -rf NBD.kext
	mkdir -p NBD.kext/Contents/MacOS
	cp nbd NBD.kext/Contents/MacOS
	cp Info.plist NBD.kext/Contents
	sudo chown -R root:wheel NBD.kext
	sudo chmod -R 755 NBD.kext

clean:
	rm -f nbd *.o setup teardown
	sync  # in case of crash during unload
	echo `sudo kextunload -q NBD.kext 2>&1`
	sudo rm -rf NBD.kext

test: clean kext setup teardown
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

teardown: teardown.c nbd_ioctl.h
	gcc -std=c99 -O0 -g -o teardown teardown.c
