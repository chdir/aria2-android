#!/bin/bash -e

. ./setenv-generic.sh

pushd openssl

export CFLAGS="-pipe"
echo "$LDFLAGS" | grep  -q  "pie"  && export CFLAGS="$CFLAGS -fPIC"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && CFLAGS="$CFLAGS -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && LDFLAGS="$LDFLAGS -march=armv7-a -Wl,--fix-cortex-a8"

if [ "$_ANDROID_ARCH" == "arch-x86" ]; then
    # disable CAST cipher, which seems to house a couple of stray text relocations
	./Configure android-x86 -no-cast -no-comp -no-hw -no-engine --cross-compile-prefix="$CROSS_COMPILE" --openssldir="$A2_ROOT" $CFLAGS
elif [ "$_ANDROID_ARCH" == "arch-mips" ]; then
	./Configure android-mips -no-shared -no-comp -no-hw -no-engine --cross-compile-prefix="$CROSS_COMPILE" --openssldir="$A2_ROOT" $CFLAGS
else
    ./Configure android-armv7 -no-shared -no-comp -no-hw -no-engine --cross-compile-prefix="$CROSS_COMPILE" --openssldir="$A2_ROOT" $CFLAGS
fi

make clean
make depend && make all && make install
popd
