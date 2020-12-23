#!/bin/bash

if grep -q 'LEAK:' $1 ; then
    echo "Leak detected, please inspect build log"
    exit 1
else
    echo "No Leak detected"
    exit 0
fi