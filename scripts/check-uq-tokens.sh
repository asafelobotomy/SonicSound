#!/usr/bin/env bash
# check-uq-tokens.sh <pinned-version> <path/to/uq-tokens.js>
# Drift check for a vendored uq-tokens.js: compares the `uq-tokens X.Y.Z` header in the file
# with the version the repo pinned. Copy it into a consuming repo as scripts/check-uq-tokens.sh.
# Exit 0 on a match, 1 on a mismatch or a missing header/file, 2 on bad usage.
set -u
if [ $# -ne 2 ] || ! [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "usage: check-uq-tokens.sh <pinned-version X.Y.Z> <path/to/uq-tokens.js>" >&2
    exit 2
fi
pinned=$1 file=$2
if [ ! -r "$file" ]; then
    echo "check-uq-tokens: cannot read $file" >&2
    exit 1
fi
found=$(head -n 5 "$file" | grep -m1 -oE 'uq-tokens [0-9]+\.[0-9]+\.[0-9]+' | cut -d' ' -f2)
if [ -z "$found" ]; then
    echo "check-uq-tokens: no 'uq-tokens X.Y.Z' header in $file" >&2
    exit 1
fi
if [ "$found" != "$pinned" ]; then
    echo "check-uq-tokens: $file is uq-tokens $found, pinned $pinned" >&2
    exit 1
fi
echo "check-uq-tokens: $file is uq-tokens $found"
