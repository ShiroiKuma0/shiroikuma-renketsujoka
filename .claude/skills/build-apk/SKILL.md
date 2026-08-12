---
name: build-apk
description: Build the signed release APK of shiroikuma-renketsujoka (the "白い熊 連結浄化" link cleaner — a fork of TrianguloY/URLCheck) with the `buildFork` Gradle task, then deliver it automatically via the global /after-build skill (adb push if a phone is connected, else scp to skhw — no prompt). Always build first without asking permission to build. Use whenever 白い熊 asks to build the app, build the APK, make a release build, or build and send to the phone.
---

# Build the 白い熊 連結浄化 release APK and deliver it

> **Never ask whether to build — just build.** When this skill applies (白い熊 asked to build, or
> you've made changes ready to test), run the build immediately. Do **not** ask "shall I build?".
> There is **no** transfer question either: after a successful build, deliver the APK automatically
> via the global **`/after-build`** skill — no prompts at all.

> **The push destination is ALWAYS `/sdcard/tmp/`.** Never `/sdcard/Download/`.

> **Never run `adb install` / `pm install` / `adb uninstall`.** 白い熊 installs the APK themselves
> from the phone's file manager.

> **Never `git commit` or `git push` on your own.** Building does not include committing. After the
> build 白い熊 tests it; only when they explicitly say **"Push"** do you commit and
> `git push origin custom`. Their "Push" means commit-and-push-to-the-fork — unrelated to `adb push`.

## Build environment (this machine)

The default `java` is JDK 11, which cannot run Gradle 9.x. The Android SDK is not on a default
env var. Export both:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/shiroikuma/android-sdk
```

Writes under `~/git/` are blocked by the command sandbox on this machine — run the build with
`dangerouslyDisableSandbox: true`.

## Steps

1. **Note the output filename / version.** Read the counter from `gradle.properties`:
   - `grep -E 'BUILD_NUMBER|LAST_BUILT_VERSION_CODE' gradle.properties`
   - The upstream version pair lives in `app/build.gradle` (`versionCode` / `versionName` literals):
     `grep -E 'versionCode|versionName' app/build.gradle`
   - The APK will be `shiroikuma-renketsujoka_<upstream versionName>+<BUILD_NUMBER padded to 3>.apk`,
     using the `BUILD_NUMBER` value **before** the build (`buildFork` bumps it afterward).
   - versionCode for that build = `<upstream versionCode> * 10000 + BUILD_NUMBER`.

2. **Build** (release, signed) — from the repo root:
   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk
   ./gradlew buildFork --console=plain < /dev/null
   ```
   - `buildFork` runs `assembleRelease`, copies the signed APK to `~/tmp/<apk name>`, bumps
     `BUILD_NUMBER` and records `LAST_BUILT_VERSION_CODE` in `gradle.properties`.
   - It prints `>>> <path>` and `>>> versionCode <n>` in cyan — use those to confirm the exact
     filename/code; confirm `BUILD SUCCESSFUL`.
   - A cold build downloads the Gradle 9.1 distro and the AGP deps and can exceed the foreground
     timeout — run it with `run_in_background` if so.
   - **Fast iteration:** `./gradlew :app:assembleDebug` is quicker (no R8), but the shippable build
     is `buildFork`. Upstream also defines `alpha` and `evaluation` build types with their own
     applicationId suffixes — those install alongside ours and are not what we ship.

3. **At the end of every build, deliver via `/after-build`** — no exceptions, no asking. As soon as
   `BUILD SUCCESSFUL` appears and the APK is in `~/tmp/`, invoke the global **`/after-build`**
   skill; it runs `/adb-check` unsandboxed, then `/adb-push` to `/sdcard/tmp/` if a phone is
   connected, otherwise `/scp` to `skhw:~/tmp/`, and announces what landed.

## Signing

Release signing is non-interactive. Upstream's own signing block reads `RELEASE_STORE_FILE`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` from project properties or
the environment; our fork block at the top of `app/build.gradle` feeds those four from a gitignored
`keystore.properties`. That indirection is deliberate — it means we never edit upstream's signing
code and it never conflicts on a rebase.

- Keystore: `~/.android-keystores/shiroikuma-renketsujoka.jks`, alias `renketsujoka`
  (PKCS12/RSA-4096, SHA384withRSA, 10000-day validity, created 2026-08-12).
- Passwords: `~/〇/[666] 私資料/[666][27] 暗号/android-keystores.org`; the `.jks` is mirrored to
  `~/〇/[666] 私資料/[666][27] 暗号/android-keystores/`.
- If `keystore.properties` is absent the build prints "No secrets provided" and the release APK is
  **unsigned** and won't install — restore the file rather than shipping it.

## Versioning (how the numbers are formed)

- The upstream pair (`versionCode 47` / `versionName "3.5"`) stays in `app/build.gradle` as
  untouched literals, so a rebase carries new upstream values in by itself. **Never hand-edit them.**
- `BUILD_NUMBER` in `gradle.properties` is our per-build increment, bumped by `buildFork`.
- Fork `versionName = "<upstream name>+<BUILD_NUMBER padded to 3>"` → `3.5+001`.
- Fork `versionCode = <upstream code> * 10000 + BUILD_NUMBER` → `470001`.
- **`BUILD_NUMBER` never resets.** `master` mirrors bleeding `upstream/master`, whose `versionCode`
  stands still between upstream releases, so a reset would send our code backwards and the installer
  would read the new build as a downgrade. `LAST_BUILT_VERSION_CODE` records the highest code ever
  shipped and `buildFork` fails rather than build at or below it.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` /
"Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line
of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
