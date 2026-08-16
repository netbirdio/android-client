#!/bin/bash
# Script to build NetBird mobile bindings using gomobile
# Usage: ./script.sh [version]
#
# Version resolution (first match wins):
#   1. explicit argument            -> the argument ('v' prefix stripped)
#   2. local build (any HEAD)       -> dev-<sha>
#   3. CI, HEAD on a release tag    -> that tag, e.g. 0.77.0
#   4. CI, commits on top of a tag  -> 0.77.0+<sha>
#   5. CI, no reachable tag         -> ci-<sha>
#
# The base tag is the last stable release tag (vX.Y.Z, no pre-release) found
# walking back HEAD's ancestry in the netbird submodule — the last tag on this
# branch, not the newest tag in the repository. <sha> is the submodule commit.

set -euo pipefail

app_path=$(pwd)

# Stable release tags only ("v" + digits, no pre-release suffix): a pre-release
# base such as "0.75.0-rc.2" would land in SemVer pre-release position, which
# the management server compares differently from a plain release.
readonly RELEASE_TAG_MATCH='v[0-9]*'
readonly RELEASE_TAG_EXCLUDE='*-*'

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

describe_release_tag() {
  git describe --tags "$@" --match "$RELEASE_TAG_MATCH" --exclude "$RELEASE_TAG_EXCLUDE" 2>/dev/null || true
}

get_version() {
  if [ -n "${1:-}" ]; then
    normalize_version "$1"
    return
  fi

  local short_hash
  short_hash=$(git rev-parse --short HEAD)

  if [ "${GITHUB_ACTIONS:-}" != "true" ]; then
    echo "dev-$short_hash"
    return
  fi

  local tag
  tag=$(describe_release_tag --exact-match)
  if [ -n "$tag" ]; then
    normalize_version "$tag"
    return
  fi

  # Walks HEAD's ancestry, so this is the last release tag on this branch,
  # not the most recently created tag in the repository.
  tag=$(describe_release_tag --abbrev=0)
  if [ -n "$tag" ]; then
    echo "$(normalize_version "$tag")+$short_hash"
    return
  fi

  echo "WARNING: no release tag reachable from HEAD; using ci-$short_hash" >&2
  if [ "$(git rev-parse --is-shallow-repository)" = "true" ]; then
    echo "WARNING: the submodule is a shallow clone; the tag lookup needs full history" >&2
  fi
  echo "ci-$short_hash"
}

# gomobile bind shells out to gobind, and gobind is the tool that actually
# generates the Java bindings and the JNI glue. Its own suggestion for a missing
# gobind is `gomobile init`, which installs it from @latest — that would let the
# generator float even though the driver is pinned, changing the generated API
# without a commit here. So both are installed up front at the revision go.mod
# names, and this only checks that they are there.
#
# Must run inside the submodule: the version quoted below comes from its go.mod.
check_gomobile_tools() {
  local missing=""
  command -v gomobile >/dev/null || missing="gomobile"
  command -v gobind >/dev/null || missing="${missing:+$missing }gobind"
  # Explicit 0: a bare return would propagate the test's exit status and set -e
  # would abort the build on the success path.
  [ -n "$missing" ] || return 0

  echo "ERROR: not on PATH: $missing" >&2
  echo "Install both at the pinned revision, do not run 'gomobile init':" >&2
  local version
  version=$(go list -m -f '{{.Version}}' golang.org/x/mobile)
  echo "  go install golang.org/x/mobile/cmd/gomobile@$version" >&2
  echo "  go install golang.org/x/mobile/cmd/gobind@$version" >&2
  exit 1
}

cd netbird

# Get version using the function
version=$(get_version "${1:-}")
echo "Using version: $version"

check_gomobile_tools

CGO_ENABLED=0 gomobile bind \
  -o "$app_path/gomobile/netbird.aar" \
  -javapkg=io.netbird.gomobile \
  -ldflags="-linkmode=external -extldflags=-Wl,-z,max-page-size=16384 -checklinkname=0 -X golang.zx2c4.com/wireguard/ipc.socketDirectory=/data/data/io.netbird.client/cache/wireguard -X github.com/netbirdio/netbird/version.version=$version" \
  "$(pwd)/client/android"

cd - > /dev/null
