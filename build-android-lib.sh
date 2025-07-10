#!/bin/bash
# Script to build NetBird mobile bindings using gomobile
# Usage: ./script.sh [version]
# If no version is provided, "development" is used as default
set -e

app_path=$(pwd)


get_version() {
  if [ -n "$1" ]; then
    echo "$1"
    return
  fi

  cd netbird

  # Try to get an exact tag
  local tag=$(git describe --tags --exact-match 2>/dev/null || true)

  if [ -n "$tag" ]; then
    cd - > /dev/null
    echo "$tag"
    return
  fi

  # Get the last tag
  local last_tag=$(git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0")

  # Parse and increment patch version
  if [[ $last_tag =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    local major=${BASH_REMATCH[1]}
    local minor=${BASH_REMATCH[2]}
    local patch=${BASH_REMATCH[3]}
    local new_patch=$((patch + 1))
    local short_hash=$(git rev-parse --short HEAD)
    local new_version="v$major.$minor.$new_patch-$short_hash"
  else
    # Fallback if tag format is not vX.Y.Z
    local short_hash=$(git rev-parse --short HEAD)
    local new_version="development-$short_hash"
  fi

  cd - > /dev/null
  echo "$new_version"
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
