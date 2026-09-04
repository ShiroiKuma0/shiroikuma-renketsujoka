<div align="center">

<img src="design/icon.png" width="120" alt="白い熊 連結浄化 icon" />

# 白い熊 連結浄化

**Every link you tap lands here first — stripped of its trackers, wrappers and referral junk — and only then opens where you choose.**

A fork of [URLCheck](https://github.com/TrianguloY/URLCheck) with **major additions**: a fully settable black-yellow UI, user-authorable link rewriting shipped with the Telegram Instant View unwrapper, on-device link resolution that never asks a third party where a link goes, category-ZIP export/import over a settable directory, and backup automation that can put this app's data back on a wiped phone.

Installs **side-by-side** with anything else (app id `shiroikuma.renketsujoka`).

**📥 Latest release: [`3.5+2026-07-25.15-05.g03a11762+025`](https://github.com/ShiroiKuma0/shiroikuma-renketsujoka/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-renketsujoka/releases)

</div>

---

## 🔗 The wrapper nothing else unwraps

Telegram hands out links as `t.me/iv?url=<target>&rhash=<hash>`. No ClearURLs or FastForward rule touches it, and no redirect-follower can: `t.me` answers **200 with an Instant View page**, never a 3xx, so there is nothing to follow.

This fork ships the rule enabled and automatic, so the wrapper is gone before the dialog even draws:

```json
"Telegram Instant View": {
  "regex": "^https?://t\\.me/iv\\?url=([^&]+)",
  "replacement": "$1",
  "decode": true,
  "automatic": true
}
```

`[^&]+` stops at the first `&`, dropping `&rhash=` while keeping the target's own query — the `?v=` of a YouTube link survives. It also fills the `decode` example upstream had left as a TODO.

---

## 🔍 Where a link actually goes — worked out here, not by a web service

Upstream's Unshortener posts every link you open to `unshorten.me` and shows whatever comes back. That means a third party is handed the list of links you tap — the opposite of what this app is for — and it means their database errors land in your dialog: any link past 100 characters returns a raw `value too long for type character varying(100)`, which a mail-tracking link routinely is.

The **Link resolver** does the same job on the phone, in an order that matters:

1. **Unwrap offline first.** Most wrappers carry the destination inside the link — `?url=`, `?redirect_uri=`, an address parked in the path. Reading it out costs no request and, more to the point, never tells the tracker the link was opened.
2. **Then follow what is left**, one hop at a time, refusing to auto-follow so every hop stays visible.
3. **Then strip the tracking parameters** — last, because on a redirector `e=` and `m=` are often exactly what makes it answer.

With **Resolve automatically** on, the browser is only ever handed the cleaned destination: replacing the url re-runs the whole module pipeline, so Clear URL gets a second pass with its own catalogue.

---

## 🎨 白い熊 連結浄化 UI — the whole look, settable

A settings page in the kxkb grammar: big bold headings underlined only as wide as their own text, a hairline opening every group, an indent ladder that makes nesting instant to read, and deliberately tight rows.

**The page is drawn by the attributes it edits**, so it is its own live preview — move the indent slider and the page you are standing on re-indents. Each visual section also carries a bordered preview panel.

- **Colours** — 4 RGBA sliders over a live hex swatch, with one-click recent colours seeded from the house palette. Applies while you slide; Cancel reverts.
- **Fonts** — every family listed **in its own glyphs**, plus `.ttf`/`.otf` import. An imported font is copied into app storage on pick, so the choice survives the source file moving, and a file the loader rejects is refused rather than silently rendering as the system face.
- **Sizes** — font size, weight, corner roundness, border and separator thickness, row padding and indent, and the switch thumb and track, all sliders, all reaching **0**: a border, a separator, even a switch's track can genuinely vanish.
- **One master switch** turns the whole layer off and returns every screen to upstream's own styling.

Reach it from Settings, or by **long-pressing the Settings cog** on the main screen.

---

## 🖤 Black and yellow, everywhere

`#FFFF00` on `#000000`, carried across every screen by an Application-level restyle rather than a per-activity patch — so the link dialog is covered too, and there is nothing to re-apply on each upstream rebase.

Both launcher icons are traced house line-art. The link dialog wears a yellow border on black. Action bars, up arrows, overflow menus, switches, seek bars, spinner arrows, dividers, toasts and the app-chooser popup all follow. **Every dialog in the app is built through one house builder**, so a dialog added later — by this fork or by upstream on a rebase — comes out black-and-yellow by construction rather than by remembering to style it. A second, dimmer yellow (`#C8C800`) is used **only** for de-emphasis — summaries, hints, inactive control halves — so a screen stays scannable instead of becoming a wall of one colour.

---

## 💾 Export / Import

A settable backup directory (SAF, no storage permissions), the latest export queried every time the page opens, and seven categories to tick. The archive is the sister-app **category ZIP**: a `manifest.json` plus one `<id>.json` per category.

Written **atomically** through a `.part` renamed only once the archive is closed and complete, and deleted on any failure or cancel — so a killed export can never leave a truncated file that looks like the latest backup.

---

## 🤖 保存復元 automation — and a restore that survives a wipe

Implements **v2** of the sister-app contract, in two halves.

**The batch half.** 白い熊 自由作業盤 backs this app up headlessly as part of one run: `EXPORT_STATE`, `LIST_CATEGORIES` and `CANCEL_EXPORT`. The export runs in a foreground service rather than the receiver — a manifest receiver that overruns the broadcast window is an ANR and a kill mid-write. Replies are fresh broadcasts, never a binder, and progress reports real counts naming the category being written.

**The data door.** A `ContentProvider` 応用管理 can call to back this app's data up and put it back on a **clean phone**, where no setting has been configured and nothing has been pasted. It writes into a file descriptor the caller opens — never a path, so the archive can be encrypted and checksummed by the caller like any other file it owns, and this app needs no storage permission for it. `import` lives **only** here.

Automation is **on by default**, because a pasted secret cannot survive the wipe this feature exists to recover from. What protects the data door instead is knowing *who is calling*: an exact package name, the uid the kernel reports, and a **pinned signing certificate**. A token remains available for anyone who wants one — 「Use authorization token?」, off by default — and a token sent to an app that is not asking for one is quietly ignored rather than refused.

The token lives in **its own preferences file**, so it cannot travel inside a backup by construction rather than by rule.

---

## 🧭 Versioning that tells you something

This fork rebases onto **every upstream commit**, and upstream's `3.5` has stood still since July — so the version pins the upstream commit the build sits on:

```
3.5+2026-07-25.15-05.g03a11762+025
└┬─┘ └────────┬─────────────┘ └┬─┘
 │            │                └── our build counter
 │            └─── upstream base: committer date (UTC) + sha
 └─── upstream's own version
```

The pin moves **only on a sync**, so two builds sharing one pin were built on the same upstream base. `versionCode` stays `<upstream code> × 10000 + <build>`.

---

## Built on URLCheck

A fork of [URLCheck](https://github.com/TrianguloY/URLCheck) by TrianguloY — the app that made link interception a modular, inspectable thing rather than a black box, and did it in plain Java with no dependencies at all. This fork keeps that: **no libraries were added**, at `minSdk 19`, so the APK is still about 1 MB.

App id `shiroikuma.renketsujoka`, so it coexists with the official build. The code remains under [CC BY 4.0](LICENSE).

## Building

```bash
git clone git@github.com:ShiroiKuma0/shiroikuma-renketsujoka.git
cd shiroikuma-renketsujoka

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME="$HOME/android-sdk"

./gradlew buildFork          # signed release → ~/tmp, bumps the build counter
./gradlew :app:assembleDebug # fast, debug-signed
```

Signing reads a gitignored `keystore.properties`; without it the release build is unsigned.
Development notes and the upstream-sync procedure are in [`CLAUDE.md`](CLAUDE.md) and
[`.claude/skills/`](.claude/skills).
