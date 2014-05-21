#!/bin/bash -e
EXAMPLE_MAP=(
  'spdy-server:io.netty.example.spdy.server.SpdyServer'
  'spdy-client:io.netty.example.spdy.client.SpdyClient'
)

EXAMPLE=''
EXAMPLE_CLASS=''
EXAMPLE_ARGS='-D_'
I=0

while [[ $# -gt 0 ]]; do
  ARG="$1"
  shift
  if [[ "$ARG" =~ (^-.+) ]]; then
    EXAMPLE_ARGS="$EXAMPLE_ARGS $ARG"
  else
    EXAMPLE="$ARG"
    for E in "${EXAMPLE_MAP[@]}"; do
      KEY="${E%%:*}"
      VAL="${E##*:}"
      if [[ "$EXAMPLE" == "$KEY" ]]; then
        EXAMPLE_CLASS="$VAL"
        break
      fi
    done
    break
  fi
done

if [[ -z "$EXAMPLE" ]] || [[ -z "$EXAMPLE_CLASS" ]] || [[ $# -ne 0 ]]; then
  echo "  Usage: $0 [-D<name>[=<value>] ...] <example-name>" >&2
  echo "Example: $0 -Dport=8443 -Dssl http-server" >&2
  echo "         $0 -Dhost=127.0.0.1 -Dport=8009 echo-client" >&2
  echo >&2
  echo "Available examples:" >&2
  echo >&2
  for E in "${EXAMPLE_MAP[@]}"; do
    echo "  ${E%%:*}"
  done | sort >&2
  echo >&2
  exit 1
fi

cd "`dirname "$0"`"/example
echo "[INFO] Running: $EXAMPLE ($EXAMPLE_CLASS $EXAMPLE_ARGS)"
exec mvn -nsu compile exec:exec -DargLine.example="$EXAMPLE_ARGS" -DexampleClass="$EXAMPLE_CLASS"

