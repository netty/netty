#!/bin/bash
set -e

RC_LIST=$(mvn org.sonatype.plugins:nexus-staging-maven-plugin:rc-list -DserverId=sonatype-nexus-staging -DnexusUrl=https://oss.sonatype.org | grep -A 2 "\[INFO\] ID                   State    Description")
STAGED=$(echo "$RC_LIST" | grep 'OPEN' | cut -f 2 -d ' ')
echo "$STAGED"
