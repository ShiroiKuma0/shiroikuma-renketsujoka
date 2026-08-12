# Automations

An automation runs a module action on a URL without you pressing anything — matched by regex, and
optionally by which app the link came from. This page is what the Automations screen's help link
points at.

**The authoritative format documentation lives in the app**, at the top of the automations Json
editor: open *Settings → Modules → Automations → Edit*, then tap **Show info**.

## Format

```json
{
  "Automation name": {
    "regex": "^https?://example\\.com/.*",
    "action": "unshort",
    "enabled": true
  }
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `regex` | string or list | The URL must match. Optional — omit and every URL matches. |
| `excludeRegex` | string or list | If it matches, the automation is skipped. |
| `action` | string or list | The module action to run. Multiple actions run in order. |
| `args` | object | Arguments for the action, when it takes any (e.g. `text` for `toast`). |
| `referrer` | string or list | Restrict the automation to links arriving from these apps. |
| `enabled` | boolean | Set `false` to keep it in the catalogue but inactive. |

The available actions are contributed by the modules themselves, so the list in the app's *Show
info* is the accurate one for the version you are running — it grows when a module gains an action.

As with patterns, the regex sits inside a JSON string: double every backslash. And once you edit the
catalogue, new built-in automations from app updates stop being added.
