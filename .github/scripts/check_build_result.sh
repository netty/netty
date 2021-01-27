#!/bin/bash
set -e

if [ "$#" -ne 1 ]; then
    echo "Expected build log as argument"
    exit 1
fi

if grep -q 'BUILD FAILURE' $1 ; then
    echo "Build failure detected, please inspect build log"
    exit 1
else
    echo "Build successful"
    exit 0
fi
