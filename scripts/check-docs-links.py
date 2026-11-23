#!/usr/bin/env python3
"""Fail when a relative Markdown link points to a missing file or heading.

Checks every tracked or new (not ignored) *.md file. External links (http:, mailto:...)
are skipped; anchors follow GitHub's heading slugs. Run from anywhere:
    python3 scripts/check-docs-links.py
"""
import os
import re
import subprocess
import sys

ROOT = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True).stdout.strip()
os.chdir(ROOT)

FILES = [
    f for f in subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "*.md"],
        capture_output=True, text=True, check=True,
    ).stdout.split("\n")
    if f and os.path.exists(f)
]


def slug(heading):
    return re.sub(r"[^\w\- ]", "", heading.strip().lower(), flags=re.U).replace(" ", "-")


_anchors = {}


def anchors(path):
    if path not in _anchors:
        found, seen, fence = set(), {}, False
        with open(path, encoding="utf-8", errors="ignore") as handle:
            for line in handle:
                if line.lstrip().startswith("```"):
                    fence = not fence
                    continue
                match = None if fence else re.match(r"^#{1,6} (.*)$", line.rstrip("\n"))
                if match:
                    base = slug(match.group(1))
                    count = seen.get(base, 0)
                    seen[base] = count + 1
                    found.add(base if count == 0 else f"{base}-{count}")
        _anchors[path] = found
    return _anchors[path]


broken = []
for source in FILES:
    with open(source, encoding="utf-8", errors="ignore") as handle:
        text = re.sub(r"```.*?```", "", handle.read(), flags=re.S)
    for match in re.finditer(r"\]\(([^)\s]+)\)", text):
        url = match.group(1)
        if re.match(r"^[a-z][a-z0-9+.-]*:", url):
            continue
        path, _, anchor = url.partition("#")
        target = os.path.normpath(os.path.join(os.path.dirname(source), path)) if path else source
        if not os.path.exists(target):
            broken.append(f"{source}: missing file {url}")
        elif anchor and target.endswith(".md") and anchor not in anchors(target):
            broken.append(f"{source}: missing heading {url}")

for line in broken:
    print(line)
print(f"Checked {len(FILES)} Markdown files: {len(broken)} broken links.")
sys.exit(1 if broken else 0)
