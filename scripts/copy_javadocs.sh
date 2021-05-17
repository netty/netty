#!/bin/bash
set -e

if [ "$#" -ne 1 ]; then
    echo "Expected netty-website directory"
    exit 1
fi

if [ ! -d "$1" ]; then
    echo "$1 is not a directory"
    exit 1
fi


BRANCH=$(git branch --show-current)
WEBSITE_API_DIR="$1"/"$BRANCH"/api/
WEBSITE_XREF_DIR="$1"/"$BRANCH"/xref/
API_DIR=all/target/api/
XREF_DIR=all/target/xref/

if [ ! -d "$API_DIR" ]; then
    echo "$API_DIR not exists, didn't run the release process yet?"
    exit 1
fi

if [ ! -d "$XREF_DIR" ]; then
    echo "$XREF_DIR not exists, didn't run the release process yet?"
    exit 1
fi

echo "Delete old javadocs and xref files"
rm -rf "$WEBSITE_API_DIR"/*
rm -rf "$WEBSITE_XREF_DIR"/*

echo "Copy javadocs and xref files"
cp -r "$API_DIR"/* "$WEBSITE_API_DIR"
cp -r "$XREF_DIR"/* "$WEBSITE_XREF_DIR"
