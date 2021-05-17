#!/bin/bash
set -e
# Adjust for different branch if needed
VERSION=4.1

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
API_DIR=target/site/apidocs/
XREF_DIR=target/site/xref/

git checkout "$TAG"
JAVA_HOME=$JAVA8_HOME ./mvnw -Paggregate clean package javadoc:aggregate jxr:aggregate -DskipTests=true

echo "Delete old javadocs and xref files"
rm -rf "$WEBSITE_API_DIR"/*
rm -rf "$WEBSITE_XREF_DIR"/*

echo "Copy javadocs and xref files"
cp -r "$API_DIR"/* "$WEBSITE_API_DIR"
cp -r "$XREF_DIR"/* "$WEBSITE_XREF_DIR"
