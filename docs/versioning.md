# Android client build and versioning

## The two workflows

### `build-debug.yml` — the CI gate

Runs automatically on every pull request and on every push to `main`. It has
three jobs: `build-debug` produces the AAR and a debug APK/AAB, then
`unit-tests` and `instrumented-tests` download that AAR and run against it, the
latter on an emulator.

Its product is a pass/fail signal, plus the `netbird-aar` artifact that the two
test jobs consume. The debug APK it uploads is a convenience for humans; nothing
in CI reads it.

### `build-release.yml` — the signed builds

Produces every release-signed build, in one of two modes chosen by the trigger.

**Published release** — triggered by `release: published`, which fires for
pre-releases too. It builds the signed APK and AAB and attaches them to the
GitHub release. This is the source of anything that goes to the Play Store.

Release candidates run in this mode: they are ordinary GitHub pre-releases
tagged `vX.Y.Z-rc.N` (for example `v0.6.0-rc.1`, `v0.3.3-rc.2`).

**Snapshot** — triggered by a manual run (Actions → "build release" → "Run
workflow", or `gh workflow run build-release.yml --ref <branch>`). Produces a
release-signed build from an arbitrary untagged commit, uploaded as a 14-day
artifact rather than published. The version name anchors that commit to the last
release it descends from, so a build off the `v0.6.0-rc.1` line reads
`v0.6.0-rc.1-snapshot-4d10386`.

Use it when someone needs a real, installable build of work in progress and you
do not want a public pre-release for it. Every run consumes a version code, so
it is not something to run per pull request.

### Version code

Both modes feed the same Play Store listing, so they share one counter: the
version code is `github.run_number + 41` of `build-release.yml`. GitHub hands
that number to the run and it only ever grows, whichever branch, tag or trigger
started it.

- Do not rename `build-release.yml`: the run number restarts at 1 for a new
  file name, which would send version codes backwards.
- Re-running a run keeps its number. A build that already reached the Play Store
  has to be repeated as a new run, not re-run.

---

## Differences

| | `build-debug` | `build-release` (release) | `build-release` (snapshot) |
|---|---|---|---|
| Trigger | `pull_request`, `push` → `main` | `release: published` | `workflow_dispatch` |
| Build type | debug | release | release |
| **Version name** | `ci-<sha>` | the release tag verbatim (`v0.5.0`, `v0.6.0-rc.1`) | `<tag>-snapshot-<sha>` |
| **Version code** | `9999` (fallback from `version.properties`) | `run_number + 41` | `run_number + 41` |
| **Signing key** | default Android debug keystore | Play upload keystore (`gplay.keystore`) | Play upload keystore (`gplay.keystore`) |
| **Firebase Crashlytics + Analytics** | **absent** | present | present, absent under a custom application ID |
| Runs tests | yes (unit + instrumented) | no | no |
| Artifact | `debug-artifacts-<name>`, 3 days; `netbird-aar`, 1 day | attached to the GitHub release | `<tag>-snapshot-<sha>`, 14 days |
| Permissions | `contents: read` | `contents: write` | `contents: write` |

`<sha>` is the short commit hash of the `android-client` repository, not of the
submodule.

`<tag>` is the nearest release tag in the built commit's ancestry, as reported by
`git describe --tags --abbrev=0` — the closest tag walking back through the
commit graph, not the most recent tag by date. A branch that forked before a tag
was cut therefore reports the older tag, and merging `main` into it moves the
name forward.
