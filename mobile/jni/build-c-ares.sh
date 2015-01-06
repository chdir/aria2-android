#!/bin/bash -e
. ./setenv-simple.sh
pushd c-ares
declare -rx VERSION=1.9.0
declare -rx PACKAGE_VERSION=1.9.0
echo "$LDFLAGS" | grep  -q  "pie"  && export CFLAGS="-fPIC"
./configure --host=$A2_COMPILER --disable-shared --prefix="$A2_ROOT"
make clean
make && make install
sed -i 's/Version: -/Version: 1.9.0/' "$A2_ROOT/lib/pkgconfig/libcares.pc"
popd
