# CLAUDE.md — guide for Claude Code in this repo

**shiroikuma-renketsujoka** — 白い熊's fork of [URLCheck](https://github.com/TrianguloY/URLCheck),
the intermediary that catches every opened link so it can be inspected, cleaned and rewritten before
it reaches a browser (plain Java, no AndroidX, `minSdk 19`). Renamed to `shiroikuma.renketsujoka` /
**白い熊 連結浄化** so it installs side by side with upstream.

This repo (`ShiroiKuma0/shiroikuma-renketsujoka`) is a real GitHub fork of `TrianguloY/URLCheck`.
`master` mirrors the upstream branch tip; our work lives on `custom`.

## Read this first

Before any work, read **`.claude/skills/build-apk/SKILL.md`** (canonical build + delivery) and
**`.claude/skills/upstream-new-version/SKILL.md`** (upstream sync + rebase, with the mandatory
proceed-gated upstream-changes table). Publishing a release uses the **global** `/publish-version`
skill — this repo keeps no local copy.

## Fork workflow — READ THIS FIRST

### Git remotes & branches

- `origin` → `git@github.com:ShiroiKuma0/shiroikuma-renketsujoka` (push here).
- `upstream` → `https://github.com/TrianguloY/URLCheck` (fetch only).
- `master` — mirrors `upstream/master`, fast-forward only. No fork work here.
- `custom` — all our work, and the GitHub default branch so the repo page lands on the fork.

**Upstream tracking: the bleeding branch tip, not release tags** (白い熊, 2026-08-12). URLCheck tags
releases (`v3.5` = versionCode 47) but keeps committing to `master` in between, and we sync on those
commits. The consequence that matters: upstream's `versionCode` stands still between releases, so
**`BUILD_NUMBER` never resets** — see *Versioning* below.

### Our customizations (install identity + build)

| What | Value | Where |
| --- | --- | --- |
| applicationId | `shiroikuma.renketsujoka` | `app/build.gradle` → `defaultConfig` |
| namespace (R/BuildConfig pkg) | `com.trianguloy.urlchecker` (**never rename**) | `app/build.gradle` |
| App label | `白い熊 連結浄化` | `app_name` in `app/src/main/res/values/strings.xml` |
| App icon | black-yellow traced line-art (yellow `#FFFF00` on black) | `design/shiroikuma-renketsujoka-icon.svg` → `mipmap-*/ic_launcher.png`, `drawable/ic_launcher_foreground.xml`, `values/ic_launcher_background.xml` |
| Version tail | `versionName = "<upstream>+<pin>+NNN"`, `versionCode = <upstream code>*10000+N` | `app/build.gradle` fork blocks |
| APK naming | `shiroikuma-renketsujoka_<version>.apk` | `app/build.gradle` → `outputFileName`, `buildFork` |
| Signing | gitignored `keystore.properties` → `~/.android-keystores/shiroikuma-renketsujoka.jks` (alias `renketsujoka`) | `app/build.gradle` fork shim feeding upstream's own signing block |
| De-branding | our name + our GitHub links everywhere user-visible | `values*/strings.xml`, `activities/AboutActivity.java`, `res/layout/activity_about.xml` |
| 白い熊 連結浄化 UI page | 28 attributes, kxkb grammar, live preview | `activities/ShiroikumaUiActivity.java`, `shiroikuma/UiPage.java`, `shiroikuma/ShiroikumaUi.java` |
| App-wide look | colour/typeface/weight over every screen, master-switchable | `shiroikuma/ShiroikumaApp.java` |
| Export / Import + 保存復元 | category ZIP, SAF directory, token-gated automation | `activities/ExportImportActivity.java`, `shiroikuma/Backups.java`, `shiroikuma/StateExport*.java` |

### Versioning & APK naming

- **Upstream tracking: `git`** — `custom` is rebased onto every upstream commit, so the fork
  versionName pins the upstream base:
  `<upstream>+<base date>.<HH-MM>.g<sha8>+<BUILD_NUMBER, 3 digits>`.
  See the global **`git-versioning`** skill. Upstream's `3.5` has stood still since July, so the
  pin is the only thing in the version that says whether we are behind upstream.
- Upstream's `versionCode` / `versionName` literals stay in `app/build.gradle` **untouched**, so a
  rebase carries new upstream values in by itself. Never hand-edit them.
- `BUILD_NUMBER` in `gradle.properties` is our per-build increment; `buildFork` bumps it.
- `versionName = "<upstream>+<base date>.<HH-MM>.g<sha8>+<build>"` → `3.5+2026-07-25.15-05.g03a11762+014`.
  The pin is the merge-base of `HEAD` and `master` — the upstream commit our patches sit on, not
  our own HEAD and not master's tip — with that commit's committer date in UTC. It therefore moves
  only on an upstream sync.
- `versionCode = <upstream code> * 10000 + BUILD_NUMBER` → `470001`.
- **`BUILD_NUMBER` never resets.** Most syncs land where upstream's `versionCode` has not moved, so a
  reset would send our code backwards and read as a downgrade. `LAST_BUILT_VERSION_CODE` records the
  highest code ever shipped; `buildFork` fails rather than build at or below it.
- APK: `~/tmp/shiroikuma-renketsujoka_<versionName>.apk`. No ABI suffix — no native libs.

### Build commands

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk

# Our build: signed release → ~/tmp + bump BUILD_NUMBER (use this)
./gradlew buildFork --console=plain < /dev/null

# Release APK only (no copy / no bump)
./gradlew :app:assembleRelease

# Fast iteration, debug-signed, no R8
./gradlew :app:assembleDebug
```

Writes under `~/git/` are blocked by the command sandbox on this machine — run builds and git
commands with `dangerouslyDisableSandbox: true`.

### Toolchain

- JDK **21** (the default `java` is 11 and cannot run Gradle 9.x), Gradle **9.1**, AGP via the
  wrapper, `compileSdk`/`targetSdk` 36, `minSdk` 19, Java source/target 17.
- Groovy `build.gradle` (not `.kts`) — upstream never migrated. No AndroidX, no Kotlin, no
  dependencies beyond `libs/*.jar`.

## Architecture (upstream URLCheck)

The app is one dialog over a URL, assembled from **modules**. Everything the user sees when a link
opens is a module rendered into that dialog, in a user-defined order.

- `activities/` — `MainActivity` (the URL dialog), `SettingsActivity`, `ModulesActivity`,
  `AboutActivity`, `TutorialActivity`, `BackupActivity`, `AutomationActivity`, `JsonEditorActivity`.
- `modules/list/` — one class per module: `PatternModule`, `ClearUrlModule`, `UnshortenModule`,
  `UriPartsModule`, `HostsModule`, `OpenModule`, `LogModule`, `StatusModule`, `WebhookModule`, …
  Each pairs a `…Config` (its settings UI, inflated from `res/layout/config_*.xml`) with a
  `…Dialog` (its row in the URL dialog).
- `modules/companions/` — the catalogs behind the modules (`PatternCatalog`, `ClearUrlCatalog`, …),
  each editable through `JsonEditorActivity` — that is the "Json editor" screen the user reaches
  from a module's config.
- `url/` — `UrlData` / `UrlDataUpdater`: the URL being inspected and the pipeline of changes modules
  propose to it. A module *suggests* a replacement; `automatic` patterns apply without a tap.
- `utilities/` — `GenericPref` (the preference wrapper every module uses), `AndroidUtils`, HTTP
  helpers, the JSON editor plumbing.

Upstream generates two things at build time from the ~35 translated `values-*/strings.xml`: a
`LOCALES` `BuildConfig` field and an `all_translators` string resource. That code is at the bottom
of `app/build.gradle` — leave it alone.

## Changelog

Upstream keeps **no** `CHANGELOG.md`; its release notes live one file per versionCode in
`fastlane/metadata/android/en-US/changelogs/`. Ours is generated:

```bash
python3 tools/gen-changelog.py
```

The script rewrites only the *Upstream releases* section from those files and preserves our
hand-written fork history above it. Write the new fork entry by hand first, then run it.

## Hard rules

- **Never `adb install` / `pm install` / `adb uninstall`.** 白い熊 installs from `/sdcard/tmp/`
  themselves. `adb push` to `/sdcard/tmp/` only, via `/after-build`.
- **Never `git commit` or `git push` unprompted.** Build, deliver, stop. Only 白い熊's explicit
  **"Push"** authorizes commit-and-push.
- **Never rename the `com.trianguloy.urlchecker` code namespace** — only `applicationId` differs.
  Renaming would make every rebase a mass-conflict.
- **Never hand-edit the upstream `versionCode`/`versionName` literals**, and never reset
  `BUILD_NUMBER`.
- Disconnect wireless adb at the end of every delivery batch (global rule, `~/.claude/CLAUDE.md`).
- Keep our changes a **small, legible layer** on top of upstream — prefer rebasing over merging so
  the customization set stays easy to audit and replay.

## Commit convention — no Claude attribution

Never add a `Co-Authored-By: Claude …` or "Generated with Claude Code" trailer to commit messages or
PR bodies; end the message at the last line of the body. This overrides the harness default.
(Global rule: `~/.claude/CLAUDE.md`.)
