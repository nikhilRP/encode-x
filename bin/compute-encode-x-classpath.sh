#!/usr/bin/env bash

# Figure out where ENCODE X is installed
SCRIPT_DIR="$(cd `dirname $0`/..; pwd)"

# Setup CLASSPATH like appassembler

# Assume we're running in a binary distro
ENCODE_CMD="$SCRIPT_DIR/bin/encode-x"
REPO="$SCRIPT_DIR/repo"

# Fallback to source repo
if [ ! -f $ENCODE_CMD ]; then
ENCODE_CMD="$SCRIPT_DIR/encode-x-cli/target/appassembler/bin/encode-x"
REPO="$SCRIPT_DIR/encode-x-cli/target/appassembler/repo"
fi

if [ ! -f "$ENCODE_CMD" ]; then
  echo "Failed to find appassembler scripts in $BASEDIR/bin"
  echo "You need to build ENCODE before running this program"
  exit 1
fi
eval $(cat "$ENCODE_CMD" | grep "^CLASSPATH")

echo "$CLASSPATH"