#!/bin/bash
# Script to build NetBird mobile bindings using gomobile
# Usage: ./script.sh [version]
# - If a version is provided, it will be used (with leading 'v' stripped if present).
# - If no version is provided:
#     * Uses the latest Git tag if available (with leading 'v' stripped if present).
#     * Otherwise, defaults to "dev-<short-hash>".
# - When running in GitHub Actions, uses "ci-<short-hash>" instead of "dev-<short-hash>".

set -euo pipefail

app_path=$(pwd)

# Normalize semantic versions to drop a leading 'v' (e.g., v1.2.3 -> 1.2.3).
# Only strips if the string starts with 'v' followed by a digit, so it won't affect
# dev/ci strings or other non-semver values.
normalize_version() {
  local ver="$1"
  if [[ "$ver" =~ ^v[0-9] ]]; then
    ver="${ver#v}"
  fi
  echo "$ver"
}

get_version() {
  if [ -n "${1:-}" ]; then
    normalize_version "$1"
    return
  fi

  # Try to get an exact tag
  local tag
  tag=$(git describe --tags --exact-match 2>/dev/null || true)

  if [ -n "$tag" ]; then
    normalize_version "$tag"
    return
  fi

  # Fallback to "<prefix>-<short-hash>"
  local short_hash
  short_hash=$(git rev-parse --short HEAD)

  local new_version
  if [ "${GITHUB_ACTIONS:-}" = "true" ]; then
    new_version="ci-$short_hash"
  else
    new_version="dev-$short_hash"
  fi

  echo "$new_version"
}

cd netbird

# Get version using the function
version=$(get_version "${1:-}")
echo "Using version: $version"

gomobile init

CGO_ENABLED=0 gomobile bind \
  -o "$app_path/gomobile/netbird.aar" \
  -javapkg=io.netbird.gomobile \
  -ldflags="-checklinkname=0 -X golang.zx2c4.com/wireguard/ipc.socketDirectory=/data/data/io.netbird.client/cache/wireguard -X github.com/netbirdio/netbird/version.version=$version" \
  "$(pwd)/client/android"

cd - > /dev/null
