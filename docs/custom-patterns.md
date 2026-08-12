# Custom patterns

The **Pattern checker** module matches the URL against regexes and either warns about it, suggests a
replacement, or applies one outright. This page is what the module's *user patterns* link points at.

**The authoritative format documentation lives in the app**, at the top of the Json editor: open
*Settings → Modules → Pattern checker → Edit*, then tap **Show info**. What follows is a working
reference and the patterns this fork ships with.

## Format

All patterns live in one JSON object keyed by pattern name:

```json
{
  "Pattern name": {
    "regex": "^https?://example\\.com/(.*)",
    "replacement": "https://example.org/$1",
    "decode": true,
    "automatic": true,
    "enabled": true
  }
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `regex` | string or list | A valid Java regex the URL must match, or the pattern is skipped. If a list, at least one must match. Omit and the full URL matches. |
| `excludeRegex` | string or list | If it matches, the pattern is skipped. |
| `replacement` | string or list | Runs `url.replaceAll(regex, replacement)` when applied. `$0` is the whole match, `$1`… the capture groups. If a list, a random element is used. |
| `encode` | boolean | URL-encode the URL before testing the regex. |
| `decode` | boolean | Decode the replaced URL afterwards. |
| `automatic` | boolean | Apply without waiting for the **Apply** button. |
| `enabled` | boolean | Set `false` to keep a pattern in the catalogue but inactive. |

Two things worth knowing:

- The regex is inside a JSON string, so every backslash is doubled — `\\.` for a literal dot,
  `\\?` for a literal question mark.
- **Once you edit the catalogue, new built-in patterns from app updates stop being added.** Keep
  your JSON somewhere outside the app; *Reset* is the only way back to the shipped defaults.

## Telegram Instant View

Telegram wraps outbound links as `t.me/iv?url=<target>&rhash=<hash>`, which no ClearURLs or
FastForward rule unwraps. This pattern strips the wrapper and the trailing hash:

```json
  "Telegram Instant View": {
    "regex": "^https?://t\\.me/iv\\?url=([^&]+)",
    "replacement": "$1",
    "decode": true,
    "automatic": true
  }
```

`[^&]+` stops at the first `&`, so `&rhash=…` is dropped while a target's own query — the `?v=…` of
a YouTube link, say — is kept. `decode` handles the percent-encoded form of the same wrapper.
