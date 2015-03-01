#!/bin/bash -e

. ./setenv-generic.sh

pushd openssl

echo "$LDFLAGS" | grep  -q  "pie"  && export CFLAGS="-fPIC"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && CFLAGS="$CFLAGS -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && LDFLAGS="$LDFLAGS -march=armv7-a -Wl,--fix-cortex-a8"

if [ "$_ANDROID_ARCH" == "arch-x86" ]; then
	./Configure android-x86 -no-shared -no-comp -no-hw -no-engine --cross-compile-prefix="$CROSS_COMPILE" --openssldir="$A2_ROOT"
elif [ "$_ANDROID_ARCH" == "arch-mips" ]; then
	./Configure android-mips -no-shared -no-comp -no-hw -no-engine --cross-compile-prefix="$CROSS_COMPILE" --openssldir="$A2_ROOT"
else
    ./Configure android-armv7 -no-shared -no-comp -no-hw -no-engine --cross-compile-prefix="$CROSS_COMPILE" --openssldir="$A2_ROOT"
fi

# -Wl,--fix-cortex-a8"

make clean
make depend && make all && make install
popd
