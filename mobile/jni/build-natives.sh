#!/bin/bash -e

export A2_COMPILER=$1
export A2_ABI=$2
export A2_GCC=$3
export A2_ARCH=$4

export A2_TOOLCHAIN=$(realpath build/toolchain/$A2_ABI)

export A2_OPENSSL=$(realpath jni/openssl)
export A2_CARES=$(realpath jni/c-ares)

mkdir -p build/native-libs libs/$A2_ABI src/main/jniLibs/$A2_ABI

export A2_ROOT=$(realpath build/native-libs)

export A2_DEST=$(realpath src/main/jniLibs/$A2_ABI)

cd jni

(./build-openssl.sh)

(./build-c-ares.sh)

export PATH="$A2_TOOLCHAIN/bin:$PATH"

cd aria2

echo "$LDFLAGS" | grep  -q  "pie" && USE_PIE=true || USE_PIE=false

export CFLAGS="-pipe"
echo "$LDFLAGS" | grep  -q  "pie"  && { export CFLAGS="$CFLAGS -fpie -fPIE -Wl,--warn-shared-textrel -Wl,--fatal-warnings"; export A2_BIN="aria2_PIC"; } || export A2_BIN="aria2"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && CFLAGS="$CFLAGS -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && LDFLAGS="$LDFLAGS -march=armv7-a -Wl,--fix-cortex-a8"

# workaround for autofences behavior
sed -i "s/AM_GNU_GETTEXT_VERSION(\[0\.18])/AM_GNU_GETTEXT_VERSION([0.19])/g" configure.ac
autoreconf -fvi
#autopoint -f

sed -i '/\#undef EAI_ADDRFAMILY/a \#undef EAI_NODATA' src/getaddrinfo.h
./configure \
    --host=$A2_COMPILER \
    --enable-static --disable-shared \
    --disable-nls \
    --without-gnutls \
    --with-openssl --with-openssl-prefix="$A2_ROOT" \
    --without-sqlite3 \
    --without-libxml2 \
    --without-libexpat \
    --with-libcares --with-libcares-prefix="$A2_ROOT" \
    --with-libz --with-libz-prefix="$A2_TOOLCHAIN" \
    CXXFLAGS="-Os -g" \
    CFLAGS="-Os -g $CFLAGS" \
    CPPFLAGS="-DHAVE_GETTIMEOFDAY=1 -DHAVE_GETADDRINFO=1" \
    LDFLAGS="-L$A2_TOOLCHAIN/lib $LDFLAGS" \
    PKG_CONFIG_LIBDIR="$A2_ROOT/lib/pkgconfig" \
    ZLIB_LIBS="-lz" \
    ZLIB_CFLAGS="-I$A2_TOOLCHAIN/sysroot/usr/include"

make clean && make

if [ $USE_PIE -a $A2_ABI = "x86" ]; then
  # stripping everything has unwanted side-effect of adding text relocations to assembly routines
  "${A2_TOOLCHAIN}/bin/${A2_COMPILER}-strip" -g -o src/aria2c-stripped src/aria2c
else
  "${A2_TOOLCHAIN}/bin/${A2_COMPILER}-strip" -o src/aria2c-stripped src/aria2c
fi

install -D src/aria2c-stripped "$A2_DEST/$A2_BIN"
