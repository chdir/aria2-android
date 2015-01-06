#!/bin/bash -e
. ./setenv-generic.sh
pushd openssl
echo "$LDFLAGS" | grep  -q  "pie"  && export CFLAGS="-fPIC"
./config -no-shared -no-comp -no-hw -no-engine --openssldir="$A2_ROOT"
make clean
make depend && make all && make install
popd
