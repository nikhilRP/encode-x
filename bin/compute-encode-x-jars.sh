#!/usr/bin/env bash
# Figure out where ENCODE-X is installed

ENCODE_X_REPO="$(cd `dirname $0`/..; pwd)"
CLASSPATH=$("$ENCODE_X_REPO"/bin/compute-encode-x-classpath.sh)
ENCODE_X_JARS=$(echo "$CLASSPATH" | tr ":" "," | cut -d "," -f 2- | rev | cut -d "," -f 2- | rev)

echo "$ENCODE_X_JARS"