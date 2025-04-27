#!/bin/bash
# ----------------------------------------------------------------------------
# Copyright 2021 The Netty Project
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
set -e
# Adjust for different branch if needed
VERSION=4.2

if [ "$#" -ne 2 ]; then
    echo "Expected netty-website directory and tag"
    exit 1
fi

if [ ! -d "$1" ]; then
    echo "$1 is not a directory"
    exit 1
fi


BRANCH=$(git branch --show-current)
TAG="$2"
WEBSITE_API_DIR="$1"/"$VERSION"/api/
WEBSITE_XREF_DIR="$1"/"$VERSION"/xref/
API_DIR=target/api/apidocs/
XREF_DIR=target/reports/xref/

git checkout "$TAG"
JAVA_HOME=$JAVA_HOME ./mvnw -Paggregate clean package javadoc:aggregate jxr:aggregate -DskipTests=true

echo "Delete old javadocs and xref files"
rm -rf "$WEBSITE_API_DIR"/*
rm -rf "$WEBSITE_XREF_DIR"/*

echo "Copy javadocs and xref files"
cp -r "$API_DIR"/* "$WEBSITE_API_DIR"
cp -r "$XREF_DIR"/* "$WEBSITE_XREF_DIR"

git checkout "$BRANCH"
