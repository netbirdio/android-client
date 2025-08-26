#!/bin/bash
# Script to build NetBird mobile bindings using gomobile
# Usage: ./script.sh [version]
# - If a version is provided, it will be used.
# - If no version is provided:
#     * Uses the latest Git tag if available.
#     * Otherwise, defaults to "dev-<short-hash>".
# - When running in GitHub Actions, uses "ci-<short-hash>" instead of "dev-<short-hash>".

set -e

app_path=$(pwd)


get_version() {
  if [ -n "$1" ]; then
    echo "$1"
    return
  fi

  # Try to get an exact tag
  local tag=$(git describe --tags --exact-match 2>/dev/null || true)

  if [ -n "$tag" ]; then
    echo "$tag"
    return
  fi

  # Fallback to "<prefix>-<short-hash>"
  local short_hash=$(git rev-parse --short HEAD)

  if [ "$GITHUB_ACTIONS" == "true" ]; then
    local new_version="ci-$short_hash"
  else
    local new_version="dev-$short_hash"
  fi

  echo "$new_version"
}


cd netbird

# Get version using the function
version=$(get_version "$1")
echo "Using version: $version"

gomobile init

CGO_ENABLED=0 gomobile bind \
  -o $app_path/gomobile/netbird.aar \
  -javapkg=io.netbird.gomobile \
  -ldflags="-checklinkname=0 -X golang.zx2c4.com/wireguard/ipc.socketDirectory=/data/data/io.netbird.client/cache/wireguard -X github.com/netbirdio/netbird/version.version=$version" \
  $(pwd)/client/android

cd - > /dev/null
