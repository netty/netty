#!/bin/sh
# ----------------------------------------------------------------------------
# Copyright 2026 The Netty Project
#
# The Netty Project licenses this file to you under the Apache License,
# version 2.0 (the "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at:
#
#   https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------
# Verify a built netty-codec-native-quic Linux artifact under the libc of THIS container.
#
# Run it on Alpine to prove the glibc-built jar really works on musl, and on a glibc image as the
# control -- anything that fails on Alpine but also fails here means the harness is broken, not the
# library.
#
# The important part is that it does not stop at loading. musl defers a relocation it cannot
# resolve rather than failing the dlopen, so a library with undefined glibc-only symbols loads
# happily and dies at the first call. quiche carries a Rust standard library whose file and socket
# paths reach exactly those symbols, so only a real handshake decides the outcome.
#
# usage: verify.sh <dir-containing-the-netty-jars>
# env:   MUSL_VARIANT         label for the log, e.g. bare / gcompat / glibc-control
#        MUSL_DROP_ELFTOOLS   1 to apk del the ELF tools before the runtime checks
# exit:  0 all checks passed / 1 at least one failed / 2 usage or setup error
set -u

IN="${1:-}"
if [ -z "$IN" ] || [ ! -d "$IN" ]; then
  echo "usage: $0 <dir-containing-the-netty-jars>" >&2
  exit 2
fi
VARIANT="${MUSL_VARIANT:-unknown}"
HERE=$(dirname "$0")

case "$(uname -m)" in
  aarch64) CLS=linux-aarch_64 ;;
  x86_64)  CLS=linux-x86_64 ;;
  *) echo "$0: unsupported architecture $(uname -m)" >&2; exit 2 ;;
esac

# find returns paths relative to $IN, and both the unpack step and javac run from elsewhere.
abspath() {
  case "$1" in
    /*) printf '%s\n' "$1" ;;
    *)  printf '%s\n' "$PWD/${1#./}" ;;
  esac
}

# Discover rather than hardcode a path: the directory layout inside a downloaded artifact or a
# mounted ~/.m2 differs, and -sources/-javadoc/-tests jars are published alongside the real ones.
NATIVE_JAR=$(find "$IN" -name "netty-codec-native-quic-*-$CLS.jar" \
                        ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*-tests.jar' | head -1)
if [ -z "$NATIVE_JAR" ]; then
  echo "$0: no netty-codec-native-quic $CLS jar under $IN" >&2
  exit 2
fi
NATIVE_JAR=$(abspath "$NATIVE_JAR")
HERE=$(abspath "$HERE")

# Everything else the check needs -- common, buffer, transport, resolver, handler, codec-base,
# codec-classes-quic -- comes from the same tree. Taking the whole set rather than naming each one
# keeps this working when netty's module split changes again.
CLASSPATH=$(find "$IN" -name 'netty-*.jar' \
                       ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*-tests.jar' \
            | while read -r j; do abspath "$j"; done | tr '\n' ':')
# A trailing colon is an empty classpath entry, which java reads as the current directory. That is
# enough for netty's NativeLibraryLoader to find a second copy of the .so -- this script unpacks one
# -- and refuse to load either.
CLASSPATH=${CLASSPATH%:}
if [ -z "$CLASSPATH" ]; then
  echo "$0: no netty jars under $IN" >&2
  exit 2
fi

echo "=================================================================="
echo " arch     : $(uname -m)  ($CLS)"
echo " variant  : $VARIANT"
echo " libc     : $(apk info -v musl 2>/dev/null | head -1 || ldd --version 2>&1 | head -1)"
echo " compat   : $(apk info 2>/dev/null | grep -Ex 'gcompat|libc6-compat|libgcc|libstdc\+\+' | sort | tr '\n' ' ')"
echo " java     : $(java -version 2>&1 | head -1)"
echo " jar      : $(basename "$NATIVE_JAR")"
echo "=================================================================="

FAILURES=0
fail() { FAILURES=$((FAILURES + 1)); echo "   FAIL: $1"; }

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
(cd "$WORK" && jar xf "$NATIVE_JAR" META-INF/native) || { echo "$0: cannot unpack $NATIVE_JAR" >&2; exit 2; }
SO=$(find "$WORK/META-INF/native" -name '*.so' | head -1)
[ -n "$SO" ] || { echo "$0: no .so inside $NATIVE_JAR" >&2; exit 2; }

# ---------------------------------------------------------------- ldd: the loader's own verdict
# On Alpine `ldd` IS the musl loader, so this reports the real relocation errors with no JVM in the
# way. Authoritative for dynamic linking, and worth printing even when it succeeds.
echo "-- ldd"
LDD_OUT=$(ldd "$SO" 2>&1)
echo "$LDD_OUT" | sed 's/^/   /' | head -30
if echo "$LDD_OUT" | grep -qE 'Error loading shared library|Error relocating|not found'; then
  fail "ldd reports unresolved libraries or relocations"
fi

# ---------------------------------------------------------------- DT_NEEDED, resolved for real
# On the build host this could only be judged against a hardcoded allowlist. Here the loader is
# present, so ask it: resolution is read back out of the ldd output above rather than guessing at
# library paths, which differ between musl (/lib) and glibc multiarch (/lib/<triplet>).
echo "-- DT_NEEDED"
MUSL_LDSO="/lib/ld-musl-$(uname -m).so.1"
for lib in $(readelf -d "$SO" 2>/dev/null | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p'); do
  case "$lib" in
    # ldso/dynlink.c: static const char reserved[] = "c.pthread.rt.m.dl.util.xnet.";
    # musl satisfies these from itself and never looks at the filesystem, which is also why
    # installing gcompat does not help: it ships /lib/libc.so.6 -> libgcompat.so.0, but libc.so.6
    # is reserved, so that symlink is never read.
    libc.so*|libpthread.so*|librt.so*|libm.so*|libdl.so*|libutil.so*|libxnet.so*)
      echo "   ok        $lib (musl-reserved)" ;;
    *)
      resolved=$(echo "$LDD_OUT" | awk -v l="$lib" '$1 == l && $2 == "=>" { print $3; exit }')
      if [ -n "$resolved" ] && [ "$resolved" != "not" ]; then
        echo "   ok        $lib -> $resolved"
      else
        fail "$lib in DT_NEEDED is neither musl-reserved nor resolvable on this system"
      fi ;;
  esac
done

# ---------------------------------------------------------------- undefined vs really exported
# Reported, not fatal: a deferred relocation that nothing ever calls never aborts.
# netty-transport-native-epoll ships __xpg_strerror_r this way and runs fine on bare Alpine. The
# execution checks below are what actually decide the outcome -- but for this artifact the list is
# the first place to look when the handshake dies, because that is where musl_compat.c's coverage
# shows up or fails to.
echo "-- undefined GLOBAL symbols not exported by anything installed"
UND=$(mktemp); AVAIL=$(mktemp)
nm -D --undefined-only "$SO" 2>/dev/null | awk '$1 == "U" { print $2 }' | sed 's/@.*//' | sort -u > "$UND"
# The glibc control leg keeps its libraries under a multiarch triplet, so both spellings are
# listed; a path that does not exist is simply skipped.
for provider in "$MUSL_LDSO" /usr/lib/libgcc_s.so.1 /usr/lib/libstdc++.so.6 /lib/libgcompat.so.0 \
                /usr/lib/libc.so.6 "/lib/$(uname -m)-linux-gnu/libc.so.6" \
                "/lib/$(uname -m)-linux-gnu/libgcc_s.so.1" "/usr/lib/$(uname -m)-linux-gnu/libstdc++.so.6"; do
  [ -e "$provider" ] && nm -D --defined-only "$provider" 2>/dev/null | awk '{ print $NF }' | sed 's/@.*//'
done | sort -u > "$AVAIL"
MISSING=$(comm -23 "$UND" "$AVAIL")
if [ -z "$MISSING" ]; then
  echo "   none"
else
  echo "$MISSING" | sed 's/^/   WARN /'
fi
rm -f "$UND" "$AVAIL"

# ------------------------------------------------------- drop the ELF tools (and libgcc with them)
# Everything above needs binutils; nothing below does. binutils depends on libgcc, so on an image
# whose JDK does not itself require libgcc, installing it silently satisfies a libgcc_s.so.1
# DT_NEEDED and the bare leg stops testing what it claims to. Removing it here means the execution
# checks run against the package set a stock Alpine JDK image really has. Guarded on apk so a
# glibc-control image skips this untouched.
if [ "${MUSL_DROP_ELFTOOLS:-0}" = "1" ] && command -v apk >/dev/null 2>&1; then
  echo "-- dropping ELF tools so the runtime checks see no libgcc"
  apk del .elftools >/dev/null 2>&1 || echo "   WARN: could not remove the .elftools virtual package"
  echo "   compat now: $(apk info 2>/dev/null | grep -Ex 'gcompat|libc6-compat|libgcc|libstdc\+\+' | sort | tr '\n' ' ')"
  for lib in /usr/lib/libgcc_s.so.1 /usr/lib/libstdc++.so.6; do
    if [ -e "$lib" ]; then
      echo "   note: $lib is still present (the JDK itself depends on it)"
    else
      echo "   ok: $lib is absent"
    fi
  done
fi

# ---------------------------------------------------------------- execute
echo "-- load, initialize and handshake"
CLASSES="$WORK/classes"
mkdir -p "$CLASSES"
javac -nowarn -d "$CLASSES" -cp "$CLASSPATH" "$HERE/QuicMuslCheck.java" \
  || { echo "$0: cannot compile QuicMuslCheck" >&2; exit 2; }

# keytool rather than SelfSignedCertificate: that class needs either BouncyCastle on the classpath
# or --add-exports java.base/sun.security.x509, and a failure there would look like a musl failure.
KEYSTORE="$WORK/quic.p12"
KEYSTORE_PASSWORD=password
keytool -genkeypair -alias quic -keyalg RSA -keysize 2048 -validity 1 \
  -dname CN=localhost -storetype PKCS12 -keystore "$KEYSTORE" \
  -storepass "$KEYSTORE_PASSWORD" -keypass "$KEYSTORE_PASSWORD" -ext SAN=IP:127.0.0.1 >/dev/null 2>&1 \
  || { echo "$0: keytool could not create the server keystore" >&2; exit 2; }

# Keep core dumps and hs_err files out of the mounted work tree: a SIGSEGV here writes hundreds of
# megabytes, and in netty-tcnative that was the normal outcome on aarch64 before the fix.
ulimit -c 0 2>/dev/null || true
JVM_OPTS="-ea -Xcheck:jni -XX:-CreateCoredumpOnCrash -XX:ErrorFile=$WORK/hs_err_%p.log"

# A hard JVM crash prints no RESULT line. Synthesise a failure for it rather than letting a missing
# line read as success -- this is the single most important behaviour in this script.
run_level() {
    level="$1"
    out=$(java $JVM_OPTS -cp "$CLASSES:$CLASSPATH" QuicMuslCheck "$level" "$SO" "$KEYSTORE" "$KEYSTORE_PASSWORD" 2>&1)
    rc=$?
    if echo "$out" | grep -q '^RESULT'; then
        echo "$out" | grep '^RESULT' | sed 's/^/   /'
    else
        printf '   RESULT\t%s\tFAIL\tJVM died without a result line (exit=%s)\n' "$level" "$rc"
        echo "$out" | grep -E '^#  (SIGSEGV|SIGBUS|SIGILL)|^# C  \[' | sed 's/^/     crash| /'
    fi
    if [ "$rc" -ne 0 ]; then
        fail "level '$level' exited $rc"
    fi
}

run_level load
run_level init
run_level handshake

echo "------------------------------------------------------------------"
if [ "$FAILURES" -ne 0 ]; then
  echo "musl-verify: FAIL ($FAILURES check(s)) on $(uname -m)/$VARIANT"
  exit 1
fi
echo "musl-verify: PASS on $(uname -m)/$VARIANT"
