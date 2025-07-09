#!/bin/bash
# Script to build NetBird mobile bindings using gomobile
# Usage: ./script.sh [version]
# If no version is provided, "development" is used as default
set -e

app_path=$(pwd)


get_version() {
  # If user passed a version, use it
  if [ -n "$1" ]; then
    echo "$1"
    return
  fi

  # No version passed, try to detect a Git tag
  cd netbird
  local tag=$(git describe --tags --exact-match 2>/dev/null || true)
  cd - > /dev/null

  # Use tag if found, otherwise default to "development"
  if [ -n "$tag" ]; then
    echo "$tag"
  else
    echo "development"
  fi
}

# Get version using the function
version=$(get_version "$1")

echo "Using version: $version"


cd netbird
gomobile init

CGO_ENABLED=0 gomobile bind \
  -o $app_path/gomobile/netbird.aar \
  -javapkg=io.netbird.gomobile \
  -ldflags="-checklinkname=0 -X golang.zx2c4.com/wireguard/ipc.socketDirectory=/data/data/io.netbird.client/cache/wireguard -X github.com/netbirdio/netbird/version.version=$version" \
  $(pwd)/client/android

cd - > /dev/null
