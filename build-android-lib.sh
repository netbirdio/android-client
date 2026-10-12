#!/bin/bash
# Script to build NetBird mobile bindings using gomobile
# Usage: ./script.sh [version]
#
# Version resolution (first match wins):
#   1. explicit argument               -> the argument ('v' prefix stripped)
#   2. local build (any HEAD)          -> dev-<sha>
#   3. CI, HEAD on a release tag       -> that tag, e.g. 0.79.0
#   4. CI, HEAD contains a release tag -> 0.79.0+<sha>
#   5. CI, no release tag contained    -> ci-<sha>
#
# The base tag is the highest stable release tag (vX.Y.Z, no pre-release) whose
# changes are all in HEAD of the netbird submodule. <sha> is the submodule
# commit. NetBird tags releases on release branches, so the tagged commit is
# often not an ancestor of main: its release-only commits are cherry-picks of
# main commits. A tag counts as contained when each commit it has on top of
# HEAD is in HEAD's history as an equivalent patch (git cherry) or under the
# same pull request number, since cherry-picks are sometimes adjusted and main
# commits sometimes renamed. A tag that fails the check is skipped for the next
# lower one, so a miss under-reports the version rather than over-reporting it.

set -euo pipefail

app_path=$(pwd)

# Stable release tags only ("v" + digits, no pre-release suffix): a pre-release
# base such as "0.75.0-rc.2" would land in SemVer pre-release position, which
# the management server compares differently from a plain release.
readonly RELEASE_TAG_MATCH='v[0-9]*'
readonly RELEASE_TAG_EXCLUDE='*-*'

# The NDK gomobile links against. CI reads this line to install the same one.
readonly NDK_VERSION=23.1.7779620

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

release_tags_newest_first() {
  local tag
  git tag -l "$RELEASE_TAG_MATCH" --sort=-v:refname | while read -r tag; do
    # shellcheck disable=SC2254
    case "$tag" in
      $RELEASE_TAG_EXCLUDE) ;;
      *) echo "$tag" ;;
    esac
  done
}

pr_number() {
  grep -oE '\(#[0-9]+\)' <<< "$1" | head -n 1 || true
}

# Prints the first commit of the tag that HEAD lacks, or nothing when HEAD has
# all of the tag's changes.
missing_tag_change() {
  local tag="$1" head_subjects="$2"
  local mark sha subject pr
  while read -r mark sha subject; do
    [ "$mark" = "-" ] && continue
    pr=$(pr_number "$subject")
    if [ -n "$pr" ] && grep -qF "$pr" <<< "$head_subjects"; then
      continue
    fi
    echo "$sha $subject"
    return
  done < <(git cherry -v HEAD "$tag")
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

  local head head_subjects tag missing
  head=$(git rev-parse HEAD)
  head_subjects=$(git log --format=%s HEAD)
  while read -r tag; do
    missing=$(missing_tag_change "$tag" "$head_subjects")
    if [ -n "$missing" ]; then
      echo "Skipping $tag: HEAD lacks $missing" >&2
      continue
    fi
    echo "Base release tag: $tag" >&2
    if [ "$(git rev-parse "$tag^{commit}")" = "$head" ]; then
      normalize_version "$tag"
    else
      echo "$(normalize_version "$tag")+$short_hash"
    fi
    return
  done < <(release_tags_newest_first)

  echo "WARNING: HEAD contains no release tag; using ci-$short_hash" >&2
  if [ "$(git rev-parse --is-shallow-repository)" = "true" ]; then
    echo "WARNING: the submodule is a shallow clone; the tag lookup needs full history" >&2
  fi
  echo "ci-$short_hash"
}

# gomobile bind shells out to gobind, and gobind is the tool that actually
# generates the Java bindings and the JNI glue. Its own suggestion for a missing
# gobind is `gomobile init`, which installs it from @latest — that would let the
# generator float even though the driver is pinned, changing the generated API
# without a commit here. So both tools are held to the revision go.mod names:
# the module version embedded in each binary (go version -m) is compared to
# that pin, and a missing or diverging tool is reinstalled at the pin.
#
# GOBIN is prepended to PATH so the binary this function verified or installed
# is the one `gomobile bind` (and its PATH lookup of gobind) actually runs,
# even when another copy sits earlier on the caller's PATH.
#
# Must run inside the submodule: the pinned version comes from its go.mod.
ensure_gomobile_tools() {
  local want
  want=$(go list -m -f '{{.Version}}' golang.org/x/mobile)

  local gobin
  gobin=$(go env GOBIN)
  [ -n "$gobin" ] || gobin="$(go env GOPATH)/bin"
  export PATH="$gobin:$PATH"

  local tool path have
  for tool in gomobile gobind; do
    have=""
    if path=$(command -v "$tool"); then
      # `|| true`: go version fails on binaries without build info, and set -e
      # would abort instead of letting the reinstall below repair the tool.
      have=$(go version -m "$path" 2>/dev/null \
        | awk '$1 == "mod" && $2 == "golang.org/x/mobile" {print $3}') || true
    fi
    if [ "$have" != "$want" ]; then
      echo "Installing $tool at the go.mod pin $want (found: ${have:-none})"
      go install "golang.org/x/mobile/cmd/$tool@$want"
    fi
  done
}

ensure_ndk() {
  : "${ANDROID_HOME:?ANDROID_HOME must point at the Android SDK}"
  export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$NDK_VERSION"
  if [ ! -d "$ANDROID_NDK_HOME" ]; then
    echo "NDK $NDK_VERSION not found under $ANDROID_HOME/ndk; install it with:" >&2
    echo "  sdkmanager --install \"ndk;$NDK_VERSION\"" >&2
    exit 1
  fi
}

cd netbird

# Get version using the function
version=$(get_version "${1:-}")
echo "Using version: $version"

ensure_ndk
ensure_gomobile_tools

# -androidapi must match minSdkVersion in build.gradle.kts. Without it gomobile
# defaults to API 16, which only NDK r23 and older still accept.
CGO_ENABLED=0 gomobile bind \
  -androidapi 26 \
  -o "$app_path/gomobile/netbird.aar" \
  -javapkg=io.netbird.gomobile \
  -ldflags="-linkmode=external -extldflags=-Wl,-z,max-page-size=16384 -checklinkname=0 -X golang.zx2c4.com/wireguard/ipc.socketDirectory=/data/data/io.netbird.client/cache/wireguard -X github.com/netbirdio/netbird/version.version=$version" \
  "$(pwd)/client/android"

cd - > /dev/null
