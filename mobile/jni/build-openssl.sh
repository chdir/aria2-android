#!/bin/bash -e
. ./setenv-generic.sh
pushd openssl
echo "$LDFLAGS" | grep  -q  "pie"  && export CFLAGS="-fPIC"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && CFLAGS="$CFLAGS -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && LDFLAGS="$LDFLAGS -march=armv7-a -Wl,--fix-cortex-a8"
./config -no-shared -no-comp -no-hw -no-engine --openssldir="$A2_ROOT"
make clean
make depend && make all && make install
popd
