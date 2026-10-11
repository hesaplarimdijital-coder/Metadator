# Continuous integration

Two workflows in `.github/workflows`, and one shared setup in `.github/actions/setup-build`. A
third builds the APK of a branch kept outside the repository's own CI.

## CI (`ci.yml`)

Runs on every pull request, on every push to `master` and `v2`, in a merge queue, and by hand.

| Job | Command | Keeps |
|---|---|---|
| Format | `./gradlew spotlessCheck` | |
| Unit tests | `./gradlew test --continue` | The test reports, when a test fails |
| Lint | `./gradlew lint --continue` | The lint reports |
| Debug build | `./gradlew :app:assembleFossDebug :app:assemblePlaystoreDebug` | The FOSS debug APK |
| CI | Fails unless the four above passed | |

The jobs run in parallel, so a formatting error is reported in a couple of minutes and does not
hide a failing test.

**Require the `CI` job in branch protection, not the four others.** It keeps its name when jobs are
added or renamed, and a job that was skipped or cancelled does not count as passed.

### Why it is built this way

- **No Android SDK step.** The runner image ships the SDK with its licenses accepted, and the
  Android Gradle Plugin downloads the platform and build tools the build asks for. A setup action
  that drives `sdkmanager` itself is one more thing that breaks when the SDK's packages change.
- **The runner image is pinned** (`ubuntu-24.04`, not `ubuntu-latest`), because the build relies on
  the SDK that image ships. Moving to a newer image is a pull request that shows whether it works,
  not something that happens to every branch on a day GitHub picks. Nothing updates this pin:
  change it by hand in both workflows.
- **Actions are pinned to a commit**, with the version in a comment. A tag can be moved to other
  code; a commit cannot. Dependabot (`.github/dependabot.yml`) opens the pull requests that move
  the pins, and one a week for the Gradle dependencies.
- **Only `master` and `v2` write the Gradle cache.** A pull request reads the cache of its base
  branch. Its own cache would be readable by nobody else and would push the useful ones out of the
  repository's quota.
- **A new push to a pull request cancels its previous run.** Runs on `master` and `v2` are left to
  finish, so every commit there has a result.
- **Nothing needs a secret**, so a pull request from a fork gets the same checks.

## Release (`release.yml`)

Started by hand from the Actions tab, on the branch or tag to release. It runs the CI workflow
first, then builds `:app:assembleFossRelease` and `:app:assemblePlaystoreRelease` and keeps the
APKs as the `metadator-release` artifact.

| Secret | What |
|---|---|
| `SIGNING_KEY_STORE_BASE64` | The keystore, base64-encoded (`base64 -w0 metadator_keystore.jks`) |
| `SIGNING_STORE_PASSWORD` | The keystore's password |
| `SIGNING_KEY_ALIAS` | The key's alias |
| `SIGNING_KEY_PASSWORD` | The key's password |
| `GOOGLE_SERVICES_JSON_BASE64` | Optional. `app/google-services.json`, base64-encoded |

`scripts/set-release-secrets.ps1` sets them all from the keystore and its password, with the GitHub
CLI. It checks the password with `keytool` first and reads the alias from the keystore:

```powershell
./scripts/set-release-secrets.ps1 C:\keys\metadator_keystore.jks 'the password'
```

`-GoogleServicesJson app\google-services.json` adds the optional secret, `-KeyPassword` is for a
key whose password is not the keystore's, and `-PrintOnly` prints the values instead of setting
them.

The workflow stops before building when a signing secret is missing, and fails when an APK comes
out unsigned: without a keystore Gradle builds unsigned APKs and reports success. Without
`GOOGLE_SERVICES_JSON_BASE64` it warns and builds the playstore APK without Crashlytics, as a fork
would.

## Metamatik's APK (`metamatik-apk.yml`)

Metamatik, the app in its Studio look, is kept on the `master` branch of a Metadator fork and was
built on the `metadator-one` branch of a repository that is not a fork, where neither workflow
above runs. On every push to either branch it runs the unit tests of the plain Kotlin modules and
builds `:app:assembleFossRelease -Pmetadator.fork=one`:

- **A release build**, since the APK is for trying the app, and a debug one is slower and brings
  LeakCanary along. It is signed with a key kept in the branch
  (`.github/metadator-dex-debug.keystore`, the well-known debug passwords): it protects nothing,
  and exists so that each build installs over the one before it.
- **`-Pmetadator.fork=<name>`** gives the release an application id and a version name of its own
  (`com.bobbyesp.metadator.<name>`, and `-<name>` after the version), so that it installs next to
  the app from its store instead of being refused for its signature.
- **The APK is published as `metamatik.apk`, an asset of the `metamatik-apk` release**, with a
  `build-report.txt` of the errors, the warnings and the end of each step's output. A release
  asset can be downloaded with the repository's token; an artifact and the log of a run cannot.
- **Where `ci.yml` does not run** (`metadator-one`), a job after the APK does what it does
  besides the unit tests: `spotlessCheck`, `lint` and the debug builds of both flavors, and
  publishes what they said, lint's findings errors first, as `checks-report.txt`. So master in
  the fork, where `ci.yml` runs, keeps passing.
- **A fork runs no workflow until it is allowed to.** GitHub turns off the workflows a repository
  was forked with: the fork's Actions tab has the button that turns them on, after which this one
  and `ci.yml` run on every push to `master`, and this one can be started by hand.

### The APK is then used (`.github/smoke`)

A second job installs the published APK on an emulator and uses it through `adb`, as somebody at a
desk would: nothing of the app is replaced or instrumented, so what is tried is what is installed
on a phone. See [testing.md](testing.md#on-an-emulator-by-a-script) for what it tries.

- `make_audio.sh` makes the songs with FFmpeg: four tracks of one album, each a different answer
  for the lossless check (a genuine FLAC, one made from an MP3, an MP3, one upsampled to 96 kHz),
  and two of another album for the SD card.
- `start_emulator.sh` boots an emulator with no window, on Android 17 as the app targets: a
  display of 1920 x 1080 at the density of a monitor, a keyboard with real keys, and an SD card of
  512 MB. It drives `sdkmanager` itself, for the one thing the runner does not ship: a system image
  (the one with 4 KB pages when there is one, else the one with 16 KB pages, which the app's native
  library is aligned for). Android 17 says it has booted before its system server is done
  starting, so the device counts as ready only once the package service, storage and the media
  store answer and the system server keeps its pid. GL direct memory and the shared slots it needs
  are turned on: the image's graphics mapper reads the screen back only by DMA, which the emulator
  offers with both and not by default there, and without it the mapper aborts, taking Surface
  Flinger and every screenshot with it.
- `smoke.py` is the user. It reads the screen as the accessibility tree has it
  (`uiautomator dump`), presses keys and taps (`input`), plugs in a mouse the system believes in
  (`uinput`) and steers it by where the input system says its pointer is (`dumpsys input`), changes the display under the
  app (`wm`, `cmd uimode`), puts songs on the SD card behind the media store's back (`su`, or
  `adb push` when there is none), and compares each song the app wrote with what it was before,
  with FFprobe. Every step leaves a picture of the screen. When the emulator has no SD card, it
  asks the system for one made out of a file (`sm set-virtual-disk`).
- **It is a report, not a gate.** `smoke-report.txt` and `smoke.zip` (the pictures, the screens as
  text, the device's log) are published next to the APK. A step that needs the network, or the
  made-up mouse, is reported and does not fail the run.
- **`[smoke-only]` in a commit message** skips the build: the APK already published is tried
  again. For a push that only changes the scripts. A push that changes neither the app nor the
  scripts (the docs) says `[skip ci]`, which GitHub reads as not to run at all; a push that runs
  stops the run before it.

## Changing a workflow

- A workflow runs as it is in the branch that triggered it, so a change is tested by its own pull
  request. Dependabot and scheduled runs only read the default branch.
- Add a job to `needs` of the `CI` job, or it will not block a merge.
