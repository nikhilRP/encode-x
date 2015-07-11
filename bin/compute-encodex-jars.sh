#!/usr/bin/env bash
# Figure out where ENCODE-X is installed

ENCODEX_REPO="$(cd `dirname $0`/..; pwd)"
CLASSPATH=$("$ENCODEX_REPO"/bin/compute-encodex-classpath.sh)
ENCODEX_JARS=$(echo "$CLASSPATH" | tr ":" "," | cut -d "," -f 2- | rev | cut -d "," -f 2- | rev)

echo "$ENCODEX_JARS"
