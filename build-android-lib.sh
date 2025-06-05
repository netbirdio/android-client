#!/bin/bash
# Script to build NetBird mobile bindings using gomobile
# Usage: ./script.sh [version]
# If no version is provided, "development" is used as default
set -e

# Set version from the first argument or use "development" as default
version=${1:-development}
app_path=$(pwd)

cd netbird
gomobile init

CGO_ENABLED=0 gomobile bind \
  -o $app_path/gomobile/netbird.aar \
  -javapkg=io.netbird.gomobile \
  -ldflags="-X golang.zx2c4.com/wireguard/ipc.socketDirectory=/data/data/io.netbird.client/cache/wireguard -X github.com/netbirdio/netbird/version.version=$version" \
  $(pwd)/client/android

cd - > /dev/null
