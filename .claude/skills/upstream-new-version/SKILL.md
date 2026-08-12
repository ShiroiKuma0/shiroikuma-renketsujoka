---
name: upstream-new-version
description: Rebase the shiroikuma-renketsujoka fork onto new upstream work from TrianguloY/URLCheck. Use when 白い熊 says a new upstream version is out, asks to update/sync to upstream, check for a new URLCheck version, bump to the latest URLCheck, or rebase custom onto the latest upstream — then build the new +1.
---

# Sync shiroikuma-renketsujoka onto new upstream URLCheck work

This fork tracks [TrianguloY/URLCheck](https://github.com/TrianguloY/URLCheck). `master` mirrors
`upstream/master` (fast-forward only); `custom` carries our patches and is rebased onto it.

> **Never `git push` or `git commit` unprompted, and never `adb install`.** After the rebase and
> build you stop and let 白い熊 test; you push only when they explicitly say **"Push"**.

## Branch / remote model

| Branch | Role | Update mode |
| --- | --- | --- |
| `master` | Mirrors `upstream/master`. No fork work here. | fast-forward only |
| `custom` | Our patches; the working/dev branch, and the GitHub default branch. | rebased onto `master` each sync |

`origin` = `git@github.com:ShiroiKuma0/shiroikuma-renketsujoka` (push). `upstream` =
`https://github.com/TrianguloY/URLCheck` (fetch only).

**Upstream tracking: the bleeding branch tip, not release tags** (白い熊, 2026-08-12). We sync
whenever commits land on `upstream/master`, not only when a `vX.Y` tag appears.

## Steps

### 1. Fetch and find out what actually changed

```bash
git fetch upstream --tags
git log --oneline master..upstream/master
git diff --stat master..upstream/master
```

Read the new upstream release notes too — upstream keeps no `CHANGELOG.md`, its notes live one file
per versionCode in `fastlane/metadata/android/en-US/changelogs/`:

```bash
git diff master..upstream/master -- fastlane/metadata/android/en-US/changelogs/
git show upstream/master:app/build.gradle | grep -E 'versionCode|versionName'
```

### 2. Present the upstream changes as a table, and STOP for a proceed (mandatory)

**白い熊's standing request: before touching a single conflict, give them a tabular, descriptive
summary of what upstream introduced, and wait for an explicit go-ahead.** No rebase, no branch
movement, no build until they say proceed.

The table is descriptive, not a commit dump — say what each change *does* and what it means for our
patches:

| Upstream change | What it does | Touches our patches? |
| --- | --- | --- |
| e.g. New "Referrer" module | Adds a module showing the app a link came from | No |
| e.g. Reworked settings activity | Moves preferences into a fragment | **Yes** — our 白い熊 連結浄化 UI page hangs off it |
| e.g. versionCode 47 → 48 | Upstream released 3.6 | Version literals only; ours derive |

Close with the version move (`3.5` / 47 → whatever is new), the commit count, and any file we patch
that upstream also touched — then ask whether to proceed.

### 3. Advance `master` (mirror; no fork work lives here)

```bash
git checkout master
git merge --ff-only upstream/master
```

### 4. Rebase `custom`

```bash
git checkout custom
git rebase master
```

Resolve conflicts so **all** our customizations survive (table below). Reconcile, don't drop — if
upstream restructured a file we patch, port our change onto the new structure rather than forcing
the old diff. **If the conflicts are significant, stop and plan with 白い熊** before continuing.

The upstream `versionCode` / `versionName` literals in `app/build.gradle` flow in automatically —
keep **upstream's** values for those two. Our fork block derives from them; never hand-edit.

### 5. Leave the build tail running — do NOT reset it

`BUILD_NUMBER` keeps counting **upward across the sync**, always. Because `master` tracks the
bleeding branch tip, most syncs land on commits where upstream's `versionCode` has not moved — so
resetting the counter would send `versionCode` backwards (470030 installed, 470001 offered) and the
installer would refuse the update. `buildFork` enforces this: `LAST_BUILT_VERSION_CODE` in
`gradle.properties` records the highest code ever built and the task fails rather than produce one
at or below it. If it fires, raise `BUILD_NUMBER` past the last built tail.

### 6. Verify our customizations survived the rebase

| What | Expected | Where |
| --- | --- | --- |
| Installed app id | `shiroikuma.renketsujoka` | `app/build.gradle` → `defaultConfig.applicationId` |
| Code namespace | `com.trianguloy.urlchecker` (**never rename**) | `app/build.gradle` → `namespace` |
| App label | `白い熊 連結浄化` | `app_name` in `app/src/main/res/values/strings.xml` |
| Fork version block | `forkVersionName` / `forkVersionCode` + the override after `defaultConfig` | `app/build.gradle` |
| Signing shim | `keystore.properties` → `ext.RELEASE_*` block at the top | `app/build.gradle` |
| APK naming + task | `outputFileName` rebrand + the `buildFork` task | `app/build.gradle` |
| Build tail | `BUILD_NUMBER`, `LAST_BUILT_VERSION_CODE` | `gradle.properties` |
| Black-yellow icon | yellow `#FFFF00` line-art on `#000000` | `design/shiroikuma-renketsujoka-icon.svg`, `mipmap-*/ic_launcher.png`, `drawable/ic_launcher_foreground.xml`, `values/ic_launcher_background.xml` |
| De-branding | our name + our GitHub links everywhere user-visible | `values*/strings.xml`, the About/Help screens |
| 白い熊 連結浄化 UI page | present and reachable from Settings | see `CLAUDE.md` |
| Committed agent files | `CLAUDE.md`, `.claude/` un-ignored; only `.claude/settings.local.json` out | `.gitignore` |

Conflict-prone files: `app/build.gradle`, `gradle.properties`, `app/src/main/res/values/strings.xml`,
`.gitignore`, and the About/settings sources we patched. Upstream also carries ~35 translated
`values-*/strings.xml` — de-branding strings live in those too.

Sanity check the script still evaluates:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk \
  ./gradlew :app:tasks --console=plain | head
```

### 7. Merge the changelog

Write the new fork entry at the top of `CHANGELOG.md`, then fold in whatever upstream release notes
arrived with the rebase:

```bash
python3 tools/gen-changelog.py
```

The script rewrites only the *Upstream releases* section from the fastlane files and preserves our
hand-written fork history above it.

### 8. Build the new `+1`

Via the **build-apk** skill (`./gradlew buildFork`), then deliver via the global **/after-build**
skill (no transfer prompt).

### 9. Stop

Let 白い熊 test. Commit/push only on their explicit **"Push"**. `custom` was rebased, so it needs
`git push --force-with-lease origin custom`; `master` is a plain fast-forward.

## Hard rules

- Never `adb install` / `adb uninstall` — 白い熊 installs manually from `/sdcard/tmp/`.
- Never commit/push unprompted; wait for "Push".
- Never rename the `com.trianguloy.urlchecker` code namespace — only `applicationId` differs.
- No Claude attribution in commits (see `CLAUDE.md`).
