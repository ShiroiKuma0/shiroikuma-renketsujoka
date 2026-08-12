# Changelog

Every 白い熊 連結浄化 build, newest first. A fork entry names what changed on our side; the
upstream release it is based on is recorded under *Upstream releases* below.

Versions read `<upstream version>+<build>` — e.g. `3.5+001` is our first build on upstream 3.5.
The installed `versionCode` is `<upstream code> * 10000 + <build>`, so 3.5+001 is 470001.

## 3.5+001

The first fork build.

- Fork of URLCheck, installable side by side: app id `shiroikuma.renketsujoka`, label
  白い熊 連結浄化. The code namespace stays `com.trianguloy.urlchecker` so rebases stay cheap.
- Black-yellow house icon: the chain-link mark traced as yellow line-art on black.
- Fork versioning: `versionName = <upstream>+<build padded to 3>`,
  `versionCode = <upstream code> * 10000 + <build>`, signed with our own keystore.
- 白い熊 連結浄化 UI settings page added (empty for now — contents to follow).
- New built-in pattern, enabled and automatic: **Telegram Instant View** unwraps
  `t.me/iv?url=<target>&rhash=<hash>` to the target. No catalog covers it and no
  redirect-follower can reach it — `t.me` answers 200 with an Instant View page, not a 3xx.

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
