#!/usr/bin/env bash

# Figure out where ENCODEX is installed
SCRIPT_DIR="$(cd `dirname $0`/..; pwd)"

# Setup CLASSPATH like appassembler

# Assume we're running in a binary distro
ENCODEX_CMD="$SCRIPT_DIR/bin/encodex"
REPO="$SCRIPT_DIR/repo"

# Fallback to source repo
if [ ! -f $ENCODEX_CMD ]; then
ENCODEX_CMD="$SCRIPT_DIR/encodex-cli/target/appassembler/bin/encodex"
REPO="$SCRIPT_DIR/encodex-cli/target/appassembler/repo"
fi

if [ ! -f "$ENCODEX_CMD" ]; then
  echo "Failed to find appassembler scripts in $BASEDIR/bin"
  echo "You need to build ENCODEX before running this program"
  exit 1
fi
eval $(cat "$ENCODEX_CMD" | grep "^CLASSPATH")

echo "$CLASSPATH"
