#!/bin/bash

BIN_DIR="$(dirname "$0")/target"

BINARIES=("native-image-http-server" "native-image-quic-server" "native-image-quic-client")

PIDS=()
FAIL=0

for BIN in "${BINARIES[@]}"; do
  if [[ ! -x "${BIN_DIR}/${BIN}" ]]; then
    echo "Error: ${BIN} not found or not executable in ${BIN_DIR}"
    exit 1
  fi
done

for BIN in "${BINARIES[@]}"; do
  echo "Running ${BIN}..."
  "${BIN_DIR}/${BIN}" &
  sleep 1
done

for i in "${!PIDS[@]}"; do
  PID="${PIDS[$i]}"
  BIN="${BINARIES[$i]}"
  if wait "$PID"; then
    echo "$BIN completed successfully."
  else
    echo "Error: $BIN (PID $PID) exited with non-zero status."
    FAIL=1
  fi
done

echo "All binaries executed successfully."
exit $FAIL
