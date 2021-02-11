#!/bin/bash
set -e
if [ "$#" -lt 2 ]; then
    echo "Expected target directory and at least one local staging directory"
    exit 1
fi
TARGET=$1

for ((i=2; i<=$#; i++))
do
  DIR="${!i}"
  SUB_DIR=$(ls -d "${DIR}"/* | awk -F / '{print $NF}')

  if [ ! -d "${TARGET}/${SUB_DIR}" ]
  then
      mkdir -p "${TARGET}/${SUB_DIR}"
  fi
  cat "${DIR}"/"${SUB_DIR}"/.index >> "${TARGET}/${SUB_DIR}"/.index
  cp -r "${DIR}"/"${SUB_DIR}"/* "${TARGET}/${SUB_DIR}"/
done
