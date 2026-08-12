# Changelog

This file carries **both histories**: 白い熊 連結浄化's releases first, then upstream URLCheck's own
release notes below. Upstream keeps no `CHANGELOG.md` of its own — its notes live one file per
versionCode in `fastlane/metadata/android/en-US/changelogs/`, and `tools/gen-changelog.py` folds
them in. Our entries are never mixed into theirs.

Fork versions read `<upstream>+<base date>.<HH-MM UTC>.g<sha8>+<build>`: the middle group pins the
upstream commit the build sits on, and moves only on a sync. The installed `versionCode` is
`<upstream code> * 10000 + <build>`, independent of the pin.

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
