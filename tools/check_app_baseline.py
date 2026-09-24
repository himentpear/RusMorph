#!/usr/bin/env python3
"""Fail CI when the canonical Android identity or release path drifts."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"APP BASELINE VIOLATION: {message}")


def read(path: str) -> str:
    target = ROOT / path
    require(target.is_file(), f"missing {path}")
    return target.read_text(encoding="utf-8")


def main() -> None:
    build = read("app/build.gradle.kts")
    require(re.search(r'applicationId\s*=\s*"org\.namchieh\.rusmorph"', build) is not None, "production applicationId changed")
    version = re.search(r'versionName\s*=\s*"([^"]+)"', build)
    code = re.search(r'versionCode\s*=\s*(\d+)', build)
    require(version is not None and code is not None, "version fields missing")
    production = re.search(r'create\("production"\)\s*\{([^{}]*)\}', build, re.S)
    require(production is not None and "applicationIdSuffix" not in production.group(1), "production flavor has an applicationIdSuffix")
    require('applicationIdSuffix = ".preview"' in build, "local preview identity missing")

    baseline = json.loads(read("release/ui-baseline.json"))
    require(baseline.get("navigation") == ["home", "learning", "dictionary", "review", "profile"], "five-destination UI baseline changed")
    require((ROOT / "app/src/main/java/org/namchieh/rusmorph/ui/design/WerusTheme.kt").is_file(), "WerusTheme missing")
    navigation = read("app/src/main/java/org/namchieh/rusmorph/ui/navigation/BottomNavigation.kt")
    destinations = re.findall(r'^\s*(Home|Learning|Dictionary|Review|Profile|Tools)\("[^"]+",\s*"[^"]+",\s*Routes\.(\w+)\)', navigation, re.M)
    require(destinations == [(name, name) for name in ("Home", "Learning", "Dictionary", "Review", "Profile")], "top-level navigation changed")

    manifest = read("app/src/main/AndroidManifest.xml")
    require('android:icon="@mipmap/ic_launcher"' in manifest, "launcher icon reference changed")
    require('android:roundIcon="@mipmap/ic_launcher_round"' in manifest, "round launcher icon reference changed")
    for icon in ("ic_launcher.xml", "ic_launcher_round.xml"):
        require((ROOT / "app/src/main/res/mipmap-anydpi-v26" / icon).is_file(), f"missing {icon}")

    release = read(".github/workflows/android-release.yml")
    guard = release.find('git merge-base --is-ancestor "$GITHUB_SHA" origin/main')
    sign = release.find("Restore release keystore")
    require(guard >= 0 and sign > guard, "main ancestry guard missing or after signing")
    require("git fetch origin main" in release, "main ancestry fetch missing")

    contract = json.loads(read("deployment/production.json"))
    require(contract["applicationId"] == "org.namchieh.rusmorph", "deployment applicationId changed")
    require(contract["versionName"] == version.group(1) and contract["versionCode"] == int(code.group(1)), "deployment version differs from Gradle")
    require(contract["uiBaseline"] == baseline["theme"], "deployment UI baseline differs")
    require(contract["releaseBranch"] == "main" and contract["updateEndpoint"] == "/update/android/stable", "deployment release contract changed")
    print("APP BASELINE: VERIFIED")


if __name__ == "__main__":
    main()
