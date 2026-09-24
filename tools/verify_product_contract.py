#!/usr/bin/env python3
"""Verify or intentionally regenerate the frozen WeRus UI/brand product contract."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASELINE_PATH = ROOT / "release/ui-baseline.json"
BRAND_PATH = ROOT / "release/brand-assets.json"

EXPECTED_NAVIGATION = ["home", "learning", "dictionary", "review", "profile"]
EXPECTED_LABELS = ["首页", "学习", "词典", "复习", "我的"]
BRAND_ASSETS = [
    "tools/werus_brand_icon_source.png",
    "app/src/main/res/drawable/ic_launcher.png",
    "app/src/main/res/drawable/ic_launcher_foreground.png",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
    "app/src/main/res/mipmap-mdpi/ic_launcher.png",
    "app/src/main/res/mipmap-mdpi/ic_launcher_round.png",
    "app/src/main/res/mipmap-hdpi/ic_launcher.png",
    "app/src/main/res/mipmap-hdpi/ic_launcher_round.png",
    "app/src/main/res/mipmap-xhdpi/ic_launcher.png",
    "app/src/main/res/mipmap-xhdpi/ic_launcher_round.png",
    "app/src/main/res/mipmap-xxhdpi/ic_launcher.png",
    "app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png",
    "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
    "app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png",
    "app/src/main/res/values/ic_launcher_background.xml",
]


def sha256(relative_path: str) -> str:
    data = (ROOT / relative_path).read_bytes()
    # Git may check out XML as CRLF on Windows and LF on CI. Preserve the
    # approved XML content hash across those equivalent line endings.
    if relative_path.endswith(".xml"):
        data = data.replace(b"\r\n", b"\n")
    return hashlib.sha256(data).hexdigest()


def generated_brand_manifest() -> dict[str, object]:
    assets = [{"path": path, "sha256": sha256(path)} for path in BRAND_ASSETS]
    return {
        "schema": 1,
        "launcherIcon": "werus-brand-v1",
        "launcherSource": assets[0],
        "assets": assets[1:],
    }


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def verify() -> None:
    baseline = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    if baseline != {
        "schema": 1,
        "navigation": EXPECTED_NAVIGATION,
        "theme": "werus-red-beige-v1",
        "launcherIcon": "werus-brand-v1",
        "developerTools": "hidden-by-default",
        "legacyToolsDestinationAllowed": False,
    }:
        fail("PRODUCT CONTRACT VIOLATION: release/ui-baseline.json changed from the approved baseline.")

    navigation = (ROOT / "app/src/main/java/org/namchieh/rusmorph/ui/navigation/BottomNavigation.kt").read_text(encoding="utf-8")
    entries = re.findall(r'^\s*(Home|Learning|Dictionary|Review|Profile|Tools)\("([^"]+)",\s*"[^"]+",\s*Routes\.(\w+)\)', navigation, re.M)
    routes = [route.lower() for _, _, route in entries]
    labels = [label for _, label, _ in entries]
    if any(name == "Tools" for name, _, _ in entries):
        fail("PRODUCT CONTRACT VIOLATION: Legacy Tools destination was restored as a primary navigation item.")
    if routes != EXPECTED_NAVIGATION or labels != EXPECTED_LABELS:
        fail(f"PRODUCT CONTRACT VIOLATION: primary navigation is {routes} / {labels}.")

    colors = (ROOT / "app/src/main/java/org/namchieh/rusmorph/ui/design/WerusColors.kt").read_text(encoding="utf-8")
    for token, value in {
        "Red": "A33A32", "RedDark": "702822", "RedSoft": "F0DCD8", "Paper": "FFFCF7",
        "Canvas": "F7F4EE", "Beige": "F0E5D1", "BeigeMuted": "F0ECE4", "Gold": "C19A5B",
        "GoldDark": "8B642F", "Ink": "292421", "InkMuted": "716861", "InkFaint": "8C827A",
    }.items():
        if not re.search(rf"val\s+{token}\s*=\s*Color\(0xFF{value}\)", colors):
            fail(f"PRODUCT CONTRACT VIOLATION: WerusColors.{token} is missing or changed.")

    strings = (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
    if '<string name="app_name">全员俄人WeRus</string>' not in strings:
        fail("BRANDING CONTRACT VIOLATION: launcher app_name is not 全员俄人WeRus.")

    manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    if 'android:icon="@mipmap/ic_launcher"' not in manifest or 'android:roundIcon="@mipmap/ic_launcher_round"' not in manifest:
        fail("BRAND CONTRACT VIOLATION: Android manifest launcher references changed.")

    expected_brand = generated_brand_manifest()
    actual_brand = json.loads(BRAND_PATH.read_text(encoding="utf-8"))
    if actual_brand != expected_brand:
        fail("BRAND CONTRACT VIOLATION: Launcher icon changed without updating approved brand baseline.")

    print("UI BASELINE: VERIFIED")
    print("NAVIGATION CONTRACT: VERIFIED")
    print("LAUNCHER ICON: VERIFIED")
    print("BRANDING: VERIFIED")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--update-brand-assets", action="store_true", help="Regenerate approved launcher hashes.")
    args = parser.parse_args()
    if args.update_brand_assets:
        BRAND_PATH.parent.mkdir(parents=True, exist_ok=True)
        BRAND_PATH.write_text(json.dumps(generated_brand_manifest(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Updated {BRAND_PATH.relative_to(ROOT)} from actual files.")
    verify()


if __name__ == "__main__":
    main()
