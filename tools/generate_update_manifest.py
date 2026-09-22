#!/usr/bin/env python3
"""Generate the stable Android update manifest from a signed production APK."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote


REPOSITORY = "himentpear/RusMorph"


def gradle_value(source: str, name: str) -> str:
    match = re.search(rf"\b{name}\s*=\s*(?:\"([^\"]+)\"|(\d+))", source)
    if not match:
        raise ValueError(f"Could not read {name} from app/build.gradle.kts")
    return match.group(1) or match.group(2)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--gradle", type=Path, default=Path("app/build.gradle.kts"))
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--policy", type=Path, default=Path("release/update-policy.json"))
    parser.add_argument("--tag", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    source = args.gradle.read_text(encoding="utf-8")
    version_name = gradle_value(source, "versionName")
    version_code = int(gradle_value(source, "versionCode"))
    expected_tag = f"v{version_name}"
    if version_code <= 0:
        raise ValueError("versionCode must be a positive integer")
    if args.tag != expected_tag:
        raise ValueError(f"Release tag {args.tag!r} must equal {expected_tag!r}")
    if not args.apk.is_file():
        raise FileNotFoundError(args.apk)

    policy = json.loads(args.policy.read_text(encoding="utf-8"))
    minimum = policy.get("minSupportedVersionCode")
    force_update = policy.get("forceUpdate")
    release_notes = policy.get("releaseNotes")
    if not isinstance(minimum, int) or isinstance(minimum, bool) or minimum <= 0:
        raise ValueError("minSupportedVersionCode must be a positive integer")
    if not isinstance(force_update, bool):
        raise ValueError("forceUpdate must be a boolean")
    if not isinstance(release_notes, list) or not all(isinstance(note, str) for note in release_notes):
        raise ValueError("releaseNotes must be an array of strings")

    encoded_tag = quote(args.tag, safe="")
    encoded_apk = quote(args.apk.name, safe="")
    manifest = {
        "platform": "android",
        "channel": "stable",
        "versionCode": version_code,
        "versionName": version_name,
        "minSupportedVersionCode": minimum,
        "forceUpdate": force_update,
        "publishedAt": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
        "title": f"RusMorph {version_name}",
        "releaseNotes": release_notes,
        "apk": {
            "url": f"https://github.com/{REPOSITORY}/releases/download/{encoded_tag}/{encoded_apk}",
            "sha256": sha256(args.apk),
            "size": args.apk.stat().st_size,
        },
        "releasePageUrl": f"https://github.com/{REPOSITORY}/releases/tag/{encoded_tag}",
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
