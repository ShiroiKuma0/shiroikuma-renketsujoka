# 白い熊 連結浄化

**連結浄化** — *link purification*. Every link you tap lands here first, gets its trackers,
wrappers and referral junk stripped, and only then opens in whatever app you choose.

<p align="center">
  <img src="design/shiroikuma-renketsujoka-icon.svg" width="128" alt="白い熊 連結浄化">
</p>

App id `shiroikuma.renketsujoka`, label **白い熊 連結浄化** — installs side by side with anything
else. Black-yellow throughout, signed with our own key.

## What it does

Set it as the default browser and it becomes the intermediary for every opened link: a dialog shows
what the URL actually is and lets you change it before it goes anywhere. Everything in that dialog is
a **module**, and you choose which ones run and in what order.

| Module | What it gives you |
| --- | --- |
| **Pattern checker** | Your own regex → replacement rules. The one that handles anything no catalogue covers — see [docs/custom-patterns.md](docs/custom-patterns.md). |
| **Url Cleaner** | The ClearURLs catalogue: tracking parameters and offline redirections. |
| **Unshortener** | Expands shortened links to their real destination. |
| **Uri parts** | Drop individual query parameters and path segments by hand. |
| **Hosts labeler** | Colour-codes hosts from a list you define, plus StevenBlack's. |
| **Automations** | Run module actions on matching links with no tap at all — see [docs/automations.md](docs/automations.md). |
| **Open** | Choose which app finally receives the cleaned URL. |

The case that motivated the fork ships enabled by default: Telegram's `t.me/iv?url=…&rhash=…`
wrapper, which no ClearURLs or FastForward catalogue unwraps and no redirect-follower can reach —
`t.me` answers 200 with an Instant View page rather than a 3xx. It is written up in
[docs/custom-patterns.md](docs/custom-patterns.md).

## Install

Grab the APK from [Releases](https://github.com/ShiroiKuma0/shiroikuma-renketsujoka/releases) and
install it. Then make it the default browser — *Settings → Apps → Default apps → Browser app* — so
links route through it rather than straight into whichever app claimed them.

## Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk
./gradlew buildFork --console=plain
```

`buildFork` builds the signed release, copies it to `~/tmp/` under the house filename, and bumps the
build counter. Signing reads a gitignored `keystore.properties`; without it the release build is
unsigned.

### Versioning

`versionName` is `<upstream version>+<build>` — `3.5+001` is the first build on upstream 3.5.
`versionCode` is `<upstream code> * 10000 + <build>`, so 3.5+001 installs as 470001. The build
counter never resets, because `master` tracks the upstream branch tip and its `versionCode` stands
still between releases.

## Fork layout

| Branch | Role |
| --- | --- |
| `master` | Mirrors upstream, fast-forward only. No fork work. |
| `custom` | Everything of ours. The default branch. |

Development notes, the sync procedure and the build conventions are in
[`CLAUDE.md`](CLAUDE.md) and [`.claude/skills/`](.claude/skills).

## Licence

Creative Commons Attribution 4.0 International — see [`LICENSE`](LICENSE).
