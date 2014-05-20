#!/bin/bash -e
cd "`dirname "$0"`"/example
if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <example-name>" >&2
  echo >&2
  echo "Available examples:" >&2
  grep -E '^      <id>[-a-z0-9]*</id>' pom.xml | sed -e 's#\(^.*<id>\|</id>.*$\)##g' | sed -e 's#^#  #' >&2
  exit 1
fi

EXAMPLE_NAME="$1"

echo "[INFO] Running: $EXAMPLE_NAME"
mvn -X -P "$EXAMPLE_NAME" compile exec:exec

