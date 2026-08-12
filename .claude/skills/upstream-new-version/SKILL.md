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

**Upstream tracking: `git`** — the bleeding branch tip, not release tags (白い熊, 2026-08-12). We
sync whenever commits land on `upstream/master`, not only when a `vX.Y` tag appears. Two
consequences run through this whole procedure: the version carries an upstream-base **pin** (global
`git-versioning` skill), and `BUILD_NUMBER` **never resets**.

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

And find out, before anything moves, which of **our** files upstream touched:

```bash
git diff --name-only master..upstream/master > /tmp/upstream-touched.txt
git diff --name-only master..custom | grep -Fxf /tmp/upstream-touched.txt
```

That last list is the conflict set. It is the most useful thing to have in hand at the gate below.

### 2. Present the upstream changes as a table, and STOP for a proceed (MANDATORY)

**白い熊's standing request: before touching a single conflict, give them a tabular, descriptive
summary of what upstream introduced, and wait for an explicit go-ahead.** No rebase, no branch
movement, no build until they say proceed.

The table is descriptive, not a commit dump — say what each change *does* and what it means for our
patches:

| Upstream change | What it does | Touches our patches? |
| --- | --- | --- |
| e.g. New "Referrer" module | Adds a module showing the app a link came from | No |
| e.g. Reworked settings activity | Moves preferences into a fragment | **Yes** — our UI page entry hangs off it |
| e.g. New AlertDialog in BackupActivity | A confirmation prompt | **Yes** — must be routed through `SkDialog` |
| e.g. versionCode 47 → 48 | Upstream released 3.6 | Version literals only; ours derive |

Close with the version move (`3.5` / 47 → whatever is new), the commit count, the conflict set from
step 1, and then ask whether to proceed.

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

Resolve conflicts so **all** our customizations survive. Reconcile, don't drop — if upstream
restructured a file we patch, port our change onto the new structure rather than forcing the old
diff. **If the conflicts are significant, stop and plan with 白い熊** before continuing.

The upstream `versionCode` / `versionName` literals in `app/build.gradle` flow in automatically —
keep **upstream's** values for those two. Our fork block derives from them; never hand-edit.

### 5. Leave the build tail running — do NOT reset it

`BUILD_NUMBER` keeps counting **upward across the sync**, always. Because `master` tracks the
bleeding branch tip, most syncs land on commits where upstream's `versionCode` has not moved — so
resetting the counter would send `versionCode` backwards (470030 installed, 470001 offered) and the
installer would refuse the update. `buildFork` enforces it: `LAST_BUILT_VERSION_CODE` in
`gradle.properties` records the highest code ever built and the task fails rather than produce one
at or below it. If it fires, raise `BUILD_NUMBER` past the last built tail.

**The version pin needs no edit.** It is derived from `git merge-base HEAD master`, which the rebase
moves by itself, so the next build picks up the new date and sha automatically. See the global
**`git-versioning`** skill.

### 6. Verify our customizations survived — the inventory

| What | Expected | Where |
| --- | --- | --- |
| Installed app id | `shiroikuma.renketsujoka` | `app/build.gradle` → `defaultConfig.applicationId` |
| Code namespace | `com.trianguloy.urlchecker` (**never rename**) | `app/build.gradle` → `namespace` |
| App label | `白い熊 連結浄化` | `app_name` in `values/untranslatable_strings.xml` |
| Fork version + pin block | `forkVersionName`/`forkVersionCode`, `upstreamPin`, `forkGit` via `providers.exec` | `app/build.gradle` |
| Signing shim | `keystore.properties` → `ext.RELEASE_*` at the top | `app/build.gradle` |
| APK naming + task | `outputFileName` rebrand + the `buildFork` task | `app/build.gradle` |
| Build tail | `BUILD_NUMBER`, `LAST_BUILT_VERSION_CODE` | `gradle.properties` |
| House palette | `app` = `#FFFF00` (every vector fills with it), `sk_*` colours | `values/colors.xml` |
| House theme | `ShiroikumaBase`/`Dialog`/`AlertDialog`/`ActionBar`/popup styles | `values/styles.xml` |
| Fork drawables | `sk_dialog_background`, `sk_divider`, `open_left/right/both` | `res/drawable/` |
| Black-yellow icons | both launchers, yellow line-art on black | `design/*.svg`, `mipmap-*/`, `drawable/ic_launcher_foreground.xml`, `drawable/clipboard_launcher_foreground.xml` |
| App-wide restyle | `ShiroikumaApp` registered as `android:name` | `AndroidManifest.xml`, `shiroikuma/ShiroikumaApp.java` |
| UI page | 28 attributes, kxkb grammar, live preview | `activities/ShiroikumaUiActivity.java`, `shiroikuma/{ShiroikumaUi,UiPage,Fonts,ColorPickerDialog,FontPickerDialog}.java` |
| Long-press entry | cog long-press → UI page | `MainActivity`, `@+id/btn_settings` in `activity_main.xml` |
| Export / Import | category ZIP, SAF directory, atomic `.part` | `activities/ExportImportActivity.java`, `shiroikuma/{Backups,BackupDirectory,ExportRunner}.java` |
| 保存復元 automation | receiver + service + token, declared in the manifest | `shiroikuma/{StateExportReceiver,StateExportService,AutomationAuth}.java` |
| Telegram pattern | built-in, enabled, `automatic` + `decode` | `modules/companions/PatternCatalog.java` |
| Changelog module OFF | its `modules.add(...)` stays commented out | `modules/ModuleManager.java` |
| versionCode comparison | `isVersionCodeNewer`, and the backup writes `versionCode` | `modules/companions/VersionManager.java`, `activities/BackupActivity.java` |
| De-branding | our name + our GitHub links everywhere user-visible | `values*/strings.xml`, `AboutActivity`, `activity_about.xml` |
| No Google credit | `mVT_tm` absent everywhere | `values*/strings.xml` |
| Committed agent files | `CLAUDE.md`, `.claude/` un-ignored; only `settings.local.json` out | `.gitignore` |

### 6b. Regression greps — the checks a rebase will NOT flag

Several of our changes are **mechanical sweeps across many files**. If upstream adds one more call
of the same shape, git merges it cleanly and the fork silently loses ground — no conflict, no
warning, just an unstyled dialog or a white toast. Run these every sync; each must print nothing:

```bash
# every dialog must be built through SkDialog (25 sites at the time of writing)
grep -rn "new AlertDialog.Builder\|new android.app.AlertDialog.Builder" app/src/main/java \
  --include=*.java | grep -v "SkDialog.java"

# every toast must go through SkToast
grep -rn "Toast.makeText" app/src/main/java --include=*.java | grep -v "SkToast.java"

# no vector may reintroduce a hardcoded white/teal fill — they all take @color/app
grep -rn 'fillColor="#[Ff][Ff][Ff]"\|fillColor="@android:color/white"\|#004D57' app/src/main/res

# no platform panel may creep back in behind our own (matches the ATTRIBUTE, so a comment
# mentioning btn_default does not trip it)
grep -rnE '(android:(drawable|background)=|<item android:drawable=)"@android:drawable/(btn_|dialog_)' \
  app/src/main/res/

# the Google credit must stay gone
grep -rn "mVT_tm" app/src/main
```

Then confirm the build script still evaluates:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk \
  ./gradlew :app:tasks --console=plain | head
```

### 7. Merge the changelog

Write the new fork entry at the top of `CHANGELOG.md` — a per-release delta, not a re-listing — then
fold in whatever upstream release notes arrived with the rebase:

```bash
python3 tools/gen-changelog.py
```

The script rewrites only the *Upstream releases* section from the fastlane files and preserves our
hand-written fork history above it.

### 8. Build the new `+1`

Via the **build-apk** skill (`./gradlew buildFork`), then deliver via the global **/after-build**
skill (no transfer prompt). Check the printed version: the pin's date and sha should have **moved**
to the new base, and the counter should have continued rather than reset.

### 9. Stop

Let 白い熊 test. Commit/push only on their explicit **"Push"**. `custom` was rebased, so it needs
`git push --force-with-lease origin custom`; `master` is a plain fast-forward.

## Hard rules

- Never `adb install` / `adb uninstall` — 白い熊 installs manually from `/sdcard/tmp/`.
- Never commit/push unprompted; wait for "Push".
- Never rename the `com.trianguloy.urlchecker` code namespace — only `applicationId` differs.
- Never hand-edit upstream's `versionCode`/`versionName` literals, and never reset `BUILD_NUMBER`.
- No Claude attribution in commits (see `CLAUDE.md`).
