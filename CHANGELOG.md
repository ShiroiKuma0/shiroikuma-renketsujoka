# Changelog

This file carries **both histories**: 白い熊 連結浄化's releases first, then upstream URLCheck's own
release notes below. Upstream keeps no `CHANGELOG.md` of its own — its notes live one file per
versionCode in `fastlane/metadata/android/en-US/changelogs/`, and `tools/gen-changelog.py` folds
them in. Our entries are never mixed into theirs.

Fork versions read `<upstream>+<base date>.<HH-MM UTC>.g<sha8>+<build>`: the middle group pins the
upstream commit the build sits on, and moves only on a sync. The installed `versionCode` is
`<upstream code> * 10000 + <build>`, independent of the pin.

## 白い熊 連結浄化 3.5+2026-07-25.15-05.g03a11762+021 — 2026-08-12

Still built on upstream **3.5** (versionCode 47), at the same upstream commit `03a11762` — upstream
has not pushed since, so the pin is unchanged and only the build counter moved. Everything here is
the delta since `+014`; builds 015–020 were not published.

### Dialogs

- **Every dialog is built through one house builder.** `SkDialog` is a drop-in subclass of
  `AlertDialog.Builder`, and all 25 construction sites across 17 files now go through it — so a
  dialog added later, by this fork or by upstream on a rebase, comes out black-and-yellow by
  construction rather than by remembering to style it. It works two ways at once: a
  `ContextThemeWrapper` carrying the house theme, **and** a show-listener that paints the window and
  walks the decor whatever the skin does with theme attributes.
- Dialog action buttons stay borderless — the view walk gained a flat-button mode, or three pills
  would line up along the bottom. Their colour is set after show, since they do not exist before it.
- `ProgressDialog` is its own class and never passes through a builder, so it styles itself
  directly: after show, on a post, and again on every `setMessage`, because it rewrites its message
  continuously and the message view is created lazily.
- The Export / Import result dialog no longer draws its own frame — the window now carries the
  border, and the inner one doubled it.

### UI page

- **Switch geometry is settable**: thumb size, track width and track height are three new attributes
  under Buttons → Switches, bringing the list from 25 to **28**, and they export, import and reset
  with everything else. They were the last hardcoded sizes in the fork — 20dp and 38×22dp, sitting
  in the restyle where nothing could reach them.
- The switch stroke follows the border attribute exactly, floor removed: every size slider in this
  fork reaches 0, and 0 here is a legitimate choice — a switch drawn by its thumb alone.
- The page styles any toggle handed to a row itself, so the new sliders preview live. The page
  rebuilds on every change, and those views are created *after* the lifecycle walk has run — without
  this they would have kept the previous geometry until the screen was left and re-entered.
- The preview panel gained a switch, so the three sliders show their effect in the same panel as
  everything else rather than only on the master toggle above.

### Fixes & behaviour

- **Version comparison uses `versionCode`, not the parsed version name.** `isVersionNewer` pulled
  every integer out of the version string, which this fork's name breaks outright:
  `3.5+2026-07-25.15-05.g03a11762+014` yields `[3, 5, 2026, 7, 25, 15, 5, 3, 11762, 14]`, where the
  `3` and `11762` come from the commit SHA and sit *ahead* of the build counter — two builds would
  be ordered by hex. `isVersionCodeNewer` compares the integer Android itself orders by.
- The backup now writes a `versionCode` file alongside the readable name. A backup written before
  that field existed is treated as **not** newer rather than guessed at: a wrong "this backup is
  from a newer version" warning is worse than none.
- **The "the app has been updated" notice is gone.** That module makes itself visible on nothing
  else, so removing the notice is removing the module — it is unregistered in `ModuleManager`. The
  class stays in the tree deliberately: the useful message is the converse ("a newer version is
  available"), and that is where it would be built.
- **The Google credit is purged.** "VirusTotal™ is a trademark of Google, Inc." is removed from the
  English strings, all 25 translations, and both screens that showed it — the VirusTotal module
  config and the About page. That was the app's only attribution to Google. What remains is not
  attribution and stays: the Chrome incognito intent extra (functional), the `googĺe.com` homograph
  example in the Pattern module's description (the attack being illustrated), and four "Copied from
  android.googlesource.com" comments in `RegexFix` — deleting a provenance comment does not purge a
  credit, it misstates where the code came from.
- **The sample test link points at `gnu.org`**, not Google. The main screen and the tutorial offer a
  link to test the dialog with; the new one keeps the shape that makes it useful — plain `http` so
  the https-upgrade pattern fires, a `ref=` for the referral stripper, a spare parameter and a
  fragment for the Uri parts module.
- **The Dutch homograph example shows a homograph again.** The Pattern module warns that non-ASCII
  characters can be used for phishing and illustrates it with `googĺe.com` vs `google.com`, the
  first `ĺ` being U+013A. The Dutch translation had lost the accent, so it read "phishing:
  google.com versus google.com" and demonstrated nothing at all. All 20 translations carrying the
  example were checked; `nl` was the only one where both sides were identical. Hebrew renders it as
  a garbled transliteration that does still differ, so the point survives and it was left alone
  rather than rewritten into a language that could not be checked.

### Repo & tooling

- The `upstream-new-version` skill now verifies the fork **as it actually is**. It was written at
  build +001 and still described a fork with an empty UI page, no theme, no dialogs of ours and no
  automation — a rebase could have passed its checks while dropping most of the fork. Its inventory
  now covers the palette and theme, our drawables, the app-wide restyle, the UI page and its
  pickers, Export / Import, the 保存復元 receiver and service, the Telegram built-in pattern, the
  unregistered changelog module, the `versionCode` comparison and the absent Google credit.
- The skill computes the **conflict set** before anything moves — the intersection of what upstream
  touched and what we patch — so the mandatory proceed gate is answered with facts, not a guess.
- New **regression greps** for the changes that are mechanical sweeps across many files: every
  dialog through `SkDialog`, every toast through `SkToast`, every vector filling with `@color/app`,
  no platform panel creeping back in, the Google credit staying gone. If upstream adds one more call
  of the same shape, git merges it cleanly and the fork silently loses ground — no conflict, no
  warning, just an unstyled dialog. Each grep was proved to still catch a real regression.

## 白い熊 連結浄化 3.5+2026-07-25.15-05.g03a11762+014 — 2026-08-12

The first release. Built on upstream **3.5** (versionCode 47), at upstream commit `03a11762`
(2026-07-25). Everything below is on top of stock URLCheck; later entries will be per-release deltas.

### Major features

- **Telegram Instant View unwrapper**, shipped as a built-in pattern, enabled and automatic:
  `t.me/iv?url=<target>&rhash=<hash>` collapses to the target. No ClearURLs or FastForward rule
  covers it, and following redirects cannot reach it — `t.me` answers 200 with an Instant View page
  rather than a 3xx. Also fills the `decode` example upstream had left as a TODO.
- **白い熊 連結浄化 UI page** — 25 settable attributes across Theme, Text, Headings, Borders & shape,
  Rows & spacing and Buttons, in the kxkb page grammar: 20sp bold headings underlined only as wide
  as their own text, a full-width hairline opening every group but the first, an indent ladder of
  36/72/108dp, and 5dp row padding. Heading size, underline, indent, separator and row padding are
  themselves sliders, and every size slider reaches 0.
- **The page is its own live preview** — it is drawn by the attributes it edits, so a change
  repaints the page you are standing on. Each visual section also carries a bordered preview panel.
- **Colour picker** — four A/R/G/B sliders over a live hex preview, with a one-click recent-colour
  row seeded from the house palette. Applies live while sliding; Cancel reverts to the opening
  colour, OK remembers it.
- **Font picker** — every available family rendered **in its own glyphs**, plus `.ttf`/`.otf` import
  through the document picker. An imported font is copied into app storage on pick so the choice
  survives the source moving, and a file the font loader rejects is refused rather than stored to
  render as the system face forever.
- **Export / Import** — a settable SAF backup directory, the latest export queried on open, seven
  tickable categories, and an Arcanechat-style pill row (Cancel alone left, Import and Export
  right). The archive is the sister-app category ZIP: `manifest.json` plus one `<id>.json` per
  category, named `shiroikuma-renketsujoka_<yyyy-MM-dd_HH-mm-ss>.zip`.
- **Atomic exports** — written to a `.part` renamed only once the archive is closed and complete,
  and deleted on any failure or cancel, so a killed export cannot leave a truncated file that reads
  as the latest backup.
- **保存復元 automation** — the sister-app contract: `EXPORT_STATE`, `LIST_CATEGORIES` and
  `CANCEL_EXPORT` on one exported receiver, all token-gated, default off. The export runs in a
  `dataSync` foreground service, never the receiver, so it cannot ANR mid-write. Replies are fresh
  broadcasts with `FLAG_INCLUDE_STOPPED_PACKAGES`, never a binder; exactly one terminal reply per
  request; progress reports real counts and names the category being written.

### UI & theming

- Black-yellow throughout: `#FFFF00` on `#000000`, with `#C8C800` reserved for de-emphasis only
  (summaries, hints, subtitles, inactive control halves).
- Both launcher icons traced as house line-art — the chain link, and the clipboard shortcut's
  clipboard-and-magnifier. Adaptive foregrounds carry the same paths as vector drawables, scaled
  into the safe zone; backgrounds black. `tools/gen-icons.sh` regenerates the density PNGs.
- App-wide restyle through an Application-level lifecycle hook rather than a per-activity patch, so
  the link dialog is covered and nothing has to be re-applied on an upstream rebase. Colour,
  typeface and weight only — never layout. The master switch makes it a genuine no-op.
- The link dialog gets a yellow border on black, painted in code — the theme's `windowBackground`
  is ignored on this device.
- Action bars painted black with yellow titles, up arrows and overflow, on every screen.
- Switches rebuilt rather than tinted: a filled yellow thumb when on, a traced one when off, over a
  black track with a yellow border.
- Overflow and app-chooser menus themed **and** span-tinted, since OEM skins honour popup theming
  inconsistently.
- Toasts replaced with a house toast — black, yellow text, yellow border — across all 28 call sites,
  falling back to a plain toast if the platform refuses the custom view.
- The split "open with" control styled by id, so its Button half, its ImageButton half and the
  backdrop under both are set together and no platform panel shows through.
- Long-pressing the Settings cog on the main screen opens the UI page directly.

### Fork identity & de-branding

- App id `shiroikuma.renketsujoka`, label 白い熊 連結浄化, installable side by side. The
  `com.trianguloy.urlchecker` code namespace is deliberately unchanged so rebases stay cheap.
- De-branded across 189 files: app name, the author logo shown in About and animated in the link
  dialog, every store/source/blog link, the backup filename prefix, the sample hosts group, the
  `-test` build label, and the app name the French and Chinese translations had hardcoded.
- `docs/custom-patterns.md` and `docs/automations.md` written to replace the in-app help links that
  pointed at upstream's wiki.

### Packaging

- Signed with our own keystore (PKCS12/RSA-4096, SHA384withRSA). Signing feeds upstream's existing
  `RELEASE_*` block from a gitignored `keystore.properties`, so its signing code is never edited and
  never conflicts on a rebase.
- `buildFork` builds the signed release, copies it to `~/tmp` under the house filename, bumps the
  counter and records `LAST_BUILT_VERSION_CODE` — refusing to build at or below the highest code
  ever shipped.
- Version pins the upstream base commit (see the header). `BUILD_NUMBER` never resets, because
  `master` tracks the bleeding upstream tip whose `versionCode` stands still between releases.
- **No dependencies added.** Upstream ships zero libraries at `minSdk 19` and this fork keeps it
  that way — the pickers are pure framework and SAF runs on `startActivityForResult`.

## Upstream releases

Upstream's own notes, taken from its fastlane release files — one per versionCode.

### 3.5 (versionCode 47)

- Improve: Allow token on Unshorten module
- Improve: Json editor (monospace and font size)
- Improve: Default patterns (Rickroll, twitter, eddrit, farside)
- Internal: Translations fixes and updates

### 3.4.1 (versionCode 46)

- Improve: Updated translations
- Fix: Report error instead of deleting extra json elements in the editor
- Fix: Avoid some errors when using http services
- Internal: Several updates and minor changes

### 3.4 (versionCode 45)

- Improve: Rewritten VirusTotal integration: use api v3 and show more details (vote third place)
- Improve: Allow app component and/or package in the open automation
- Improve: Allow list of strings on 'regex' and 'excludeRegex' for patterns
- Improve: $REFERRER$ for the webhook body
- Improve: Close url editText on enter
- New: excludeRegex on automations
- New: Toast automation sample
- Fix: bold host display not working with host-only urls
- Fix: non-clickable links on json editor

### 3.3.1 (versionCode 42)

- New: Allow multiple referrers on automations
- Fix: Automations other than the first not working
- Fix: Unescaped dots on example patterns

### 3.3 (versionCode 40)

- New: Configurable dialog width
- New: Automations: 'referrer' and 'stop'
- New: Open&replace text on long-click
- New: Register the app to open some common url intents (thanks ArtikusHG!)
- New: Sample pattern song.link (thanks shalva-an!)
- Improve: Automations: Allow multiple actions
- Improve: Automations and patterns: 'regex' is now optional
- Fix: Custom tabs not working when opening from some apps (like Google search)
- Remove: 'clear' automation (wasn't working)

### 3.2 (versionCode 39)

- Improve: enhanced JSON editor (vote second place)
- New: upper-case-in-domain default pattern (thanks Bakr-Ali!)
- Fix: 'rejected' urls hide the opened app

### 3.1 (versionCode 38)

- New module: Webhook. Thanks to anoop-b!
- New automations: clear and close
- Improve: Allow multiple regex on automations
- Improve: Tweaked style of history module
- Fix: Disable auto-updates after using the Input module

### 3.0 (versionCode 37)

- New: Automation (vote winner)
- Change: Text input edit mode is now a popup
- Fix: Uri parser should no longer decode twice
- Improve: Updated main screen icons

### 2.17 (versionCode 35)

- Improve: Input Text module display
- Improve: Hosts module will check for subdomains too
- Fix: Avoid crashes when decoding invalid text
- Fix: Avoid crashes with malformed queries
- Fix: Updated invalid lastUpdate of ClearUrl catalog
- Fix: Module enable switches was sometimes reset when the app was sent to the background
- Fix: Double decoding of url parts

### 2.16 (versionCode 34)

- Improve: Detect multiple activities for the same app
- Improve: Show first app icon
- Improve: Display applied patterns in order
- Debug: Include query results in the debug module

### 2.15 (versionCode 32)

- New: Animations (can be disabled)
- Fix: Better rejected detection. And allow to disable.
- Fix: Custom tabs should work in more situations (links not opening)
- Fix: Emojis and other non-standard characters on the Uri parts module
- Improvement: Better error message when a module can't be enabled

### 2.14 (versionCode 31)

- New: Backup/restore screen
- Removed support for Android 4.3 and below
- Updated internal versions

### 2.13.1 (versionCode 29)

- New: Custom logic to hide rejected urls. Hide referrer is now disabled by default.
- Fix: Third try to the 'open/share does nothing' bug.
- Fix: Status module no longer decodes the location.

### 2.13 (versionCode 28)

- New module: drawer. Hide modules under a toggleable.
- New module: changelog. Will notify if the app updates.
- Improvement: Limit number of log entries.
- Improvement: Missing 'hidden' status for Ctabs and private buttons.
- Improvement: Try to open more valid urls.

### 2.12 (versionCode 25)

- New: open Firefox in incognito
- New: enhance share & copy buttons
- New: encode and decode pattern parameters
- Improvement: show referrer on the debug module
- Improvement: keep expanded groups on Uri parts
- Improvement: long tap uri parts to copy to clipboard
- Fix: null optional groups on Android <=10
- Fix: extract links from shared text
- Fix: legacy shortcuts should work now
- Fix: minor crash on Unshorten module

### 2.11 (versionCode 23)

- New module: Edit Flags
- New module: Uri parts (substitute of Queries remover)
- New config: excludeRegex field on Pattern Checker module
- Change: modules are now hidden when unused
- Fix: opened apps weren't showing on recents
- Fix: standard shortcuts weren't available
- Fix: keyboard was obstructing the dialog
- Fix: status module wasn't working properly
- Translations: New and updates from symbuzzer and other from Weblate

### 2.10 (versionCode 22)

- New tutorial screen
- New module: Unshortener
- New features and improvements to status module
- New shortcut & tile to open clipboard links
- New chinese translation. Many thanks to Seviche CC!
- Visual fixes

### 2.9.1 (versionCode 21)

- New Italian translation. Thanks to dperruso!
- Fixed some uncommon errors when opening links

### 2.9 (versionCode 20)

- New module: Hosts labeler
- Hide module titles (can be toggled)
- New settings activity
- Setup as default browser
- Change theme
- Change locale
- Tweaked and cleaned styles and code

### 2.8 (versionCode 19)

- Renamed app from 'Url Checker' to 'URLCheck'
- New japanese translation. Thanks to 404potato!
- New Pattern Checker samples and improvements
- New setting to sort apps per-domain
- New setting to close after sharing
- New setting to hide the source app (referrer)
- Allow disabling all modules
- Improved About activity
- Internal tooling improvements

### 2.7 (versionCode 16)

- Improved Open module apps sorting
- Improved ClearURL catalog updater
- Added CTabs settings
- Some Android 13 improvements
- Fixed issues with module sorting
- Fixed some links not opening
- Fixes and tweaks to strings
- Fixes and tweaks to styles
- Several internal improvements

### 2.6 (versionCode 15)

- Colors!
- Improved Queries remover module
- Improved Patterns checker module
- New module: Log
- New Turkish translation. Thanks to Metezd!
- New Hebrew translation. Thanks to Nhman Mazuz!
- Minor tweaks and fixes

### 2.5 (versionCode 14)

- Advanced JSON editor for ClearURLs and Patterns
- The ClearURLs database can now be updated
- Added Spanish translation
- Styles and texts improvements

### 2.4 (versionCode 12)

- Added French translation. Thanks to Ilithy!
- Added Ukrainian translation. Thanks to Idris!
- Automatic dark/white theme

### 2.3 (versionCode 11)

- Allow reordering modules
- Clear urls module can be configured to auto-apply
- Remove queries module can remove individual queries
- Added European Portuguese translation. Thanks to Tiago Carmo!
- Some tweaks and fixes

### 2.2 (versionCode 10)

- New module 'Remove queries module': removes all queries from the url. Thanks to PabloOQ for the idea and original implementation!

### 2.1 (versionCode 9)

- Fixed and improved ClearUrl module
- Improved some texts and messages
- Tweaked style to be more compact

### 2.0 (versionCode 8)

- Style update
- Updated Clear Url catalog

### 1.1 (versionCode 5)

- New module 'Clear Url': Uses the Clear Url dictionary to clear urls from referral and other useless url parameters.
- New module 'History': Revert changes from any module.
- Replaced module 'Redirect' with 'Status': Fetch the status code of the page, still allows for redirection.
- Improved 'Pattern' module: Press 'Fix' to convert http to https
- New module 'Debug': For developers (display the intent uri and ctabs messages)
- UI cleanup.
