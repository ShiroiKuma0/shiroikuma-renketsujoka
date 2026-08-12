#!/usr/bin/env python3
"""Regenerate the upstream half of CHANGELOG.md from upstream's fastlane release notes.

Upstream (TrianguloY/URLCheck) keeps no CHANGELOG.md — its release notes live one file per
versionCode in fastlane/metadata/android/en-US/changelogs/. This script turns those into the
"Upstream releases" section of our CHANGELOG.md, newest first.

Everything above the UPSTREAM_MARKER is OUR hand-written fork history and is preserved
verbatim; only the section below it is rewritten. So the release flow is:

  1. write the new fork entry by hand at the top of CHANGELOG.md
  2. run this script to fold in whatever upstream release notes arrived with the rebase

Usage:  python3 tools/gen-changelog.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
CHANGELOG = REPO / "CHANGELOG.md"
CHANGELOGS_DIR = REPO / "fastlane/metadata/android/en-US/changelogs"

UPSTREAM_MARKER = "## Upstream releases"

HEADER = """# Changelog

Every 白い熊 連結浄化 build, newest first. A fork entry names what changed on our side; the
upstream release it is based on is recorded under *Upstream releases* below.

Versions read `<upstream version>+<build>` — e.g. `3.5+001` is our first build on upstream 3.5.
The installed `versionCode` is `<upstream code> * 10000 + <build>`, so 3.5+001 is 470001.

"""


def read_entries() -> list[tuple[int, str, list[str]]]:
    """Return (versionCode, versionName, bullet lines) per upstream release, newest first."""
    entries = []
    for path in CHANGELOGS_DIR.glob("*.txt"):
        if not path.stem.isdigit():
            continue
        code = int(path.stem)
        lines = [ln.rstrip() for ln in path.read_text(encoding="utf-8").splitlines()]
        lines = [ln for ln in lines if ln.strip()]
        if not lines:
            continue
        # First line is "V 3.5" (occasionally just the bullets, for the oldest files).
        head = lines[0].strip()
        m = re.fullmatch(r"[Vv]\s*(.+)", head)
        if m:
            name, bullets = m.group(1).strip(), lines[1:]
        else:
            name, bullets = "", lines
        entries.append((code, name, bullets))
    entries.sort(key=lambda e: e[0], reverse=True)
    return entries


def render_upstream(entries) -> str:
    out = [UPSTREAM_MARKER, ""]
    out.append("Upstream's own notes, taken from its fastlane release files — one per versionCode.")
    out.append("")
    for code, name, bullets in entries:
        title = f"### {name} (versionCode {code})" if name else f"### versionCode {code}"
        out.append(title)
        out.append("")
        for b in bullets:
            b = b.strip()
            out.append(b if b.startswith("-") else f"- {b}")
        out.append("")
    return "\n".join(out).rstrip() + "\n"


def main() -> int:
    if not CHANGELOGS_DIR.is_dir():
        print(f"no upstream changelog dir at {CHANGELOGS_DIR}", file=sys.stderr)
        return 1

    entries = read_entries()
    upstream = render_upstream(entries)

    if CHANGELOG.exists():
        existing = CHANGELOG.read_text(encoding="utf-8")
        head = existing.split(UPSTREAM_MARKER)[0].rstrip() + "\n\n"
    else:
        head = HEADER

    CHANGELOG.write_text(head + upstream, encoding="utf-8")
    print(f"wrote {CHANGELOG.relative_to(REPO)} — {len(entries)} upstream releases")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
