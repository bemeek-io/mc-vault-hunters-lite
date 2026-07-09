#!/usr/bin/env bash
# Packages the resource pack into a distributable zip and prints the SHA-1
# that server.properties wants. Run from anywhere.
set -euo pipefail
cd "$(dirname "$0")"

ZIP="../build/VaultHuntersLite-ResourcePack.zip"
mkdir -p ../build
rm -f "$ZIP"
zip -r -q -X "$ZIP" pack.mcmeta assets
echo "Built $(cd .. && pwd)/build/VaultHuntersLite-ResourcePack.zip"
shasum "$ZIP" | awk '{print "SHA-1: " $1}'
