#!/bin/bash -e
declare -A EXAMPLE_MAP=(
  ['spdy-server']='io.netty.example.spdy.server.SpdyServer'
  ['spdy-client']='io.netty.example.spdy.client.SpdyClient'
)

EXAMPLE=''
EXAMPLE_CLASS=''
EXAMPLE_ARGS=''
I=0

while [[ $# -gt 0 ]]; do
  ARG="$1"
  shift
  if [[ "$ARG" =~ (^-.+) ]]; then
    if [[ -z "$EXAMPLE_ARGS" ]]; then
      EXAMPLE_ARGS="$ARG"
    else
      EXAMPLE_ARGS="$EXAMPLE_ARGS $ARG"
    fi
  else
    EXAMPLE="$ARG"
    EXAMPLE_CLASS="${EXAMPLE_MAP["$EXAMPLE"]}"
    break
  fi
done

if [[ -z "$EXAMPLE" ]] || [[ -z "$EXAMPLE_CLASS" ]] || [[ $# -ne 0 ]]; then
  echo "  Usage: $0 [-D<name>[=<value>] ...] <example-name>" >&2
  echo "Example: $0 -Dport=8443 -Dssl http-server" >&2
  echo >&2
  echo "Available examples:" >&2
  for E in "${!EXAMPLE_MAP[@]}"; do
    echo "  $E"
  done | sort >&2
  exit 1
fi

cd "`dirname "$0"`"/example
echo "[INFO] Running: $EXAMPLE ($EXAMPLE_CLASS $EXAMPLE_ARGS)"
exec mvn -nsu compile exec:exec -DargLine.example="$EXAMPLE_ARGS" -DexampleClass="$EXAMPLE_CLASS"

