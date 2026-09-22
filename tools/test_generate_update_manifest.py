import json
import subprocess
import sys
import tempfile
from pathlib import Path
import pytest

REPO_ROOT = Path(__file__).resolve().parent.parent
GENERATOR_SCRIPT = REPO_ROOT / "tools" / "generate_update_manifest.py"


def test_generator_canonical_apk_url():
    with tempfile.TemporaryDirectory() as td:
        tdp = Path(td)
        # Mock gradle file with versionName 0.004 and versionCode 4
        gradle_file = tdp / "build.gradle.kts"
        gradle_file.write_text(
            'versionCode = 4\nversionName = "0.004"\n',
            encoding="utf-8",
        )
        policy_file = tdp / "update-policy.json"
        policy_file.write_text(
            json.dumps({"minSupportedVersionCode": 1, "forceUpdate": False, "releaseNotes": []}),
            encoding="utf-8",
        )
        # Even if the local input file is named app-production-release.apk
        local_apk = tdp / "app-production-release.apk"
        local_apk.write_bytes(b"DUMMY_APK_CONTENT")
        output_json = tdp / "android-stable.json"

        cmd = [
            sys.executable,
            str(GENERATOR_SCRIPT),
            "--gradle", str(gradle_file),
            "--policy", str(policy_file),
            "--apk", str(local_apk),
            "--tag", "v0.004",
            "--output", str(output_json),
        ]
        res = subprocess.run(cmd, capture_output=True, text=True)
        assert res.returncode == 0, f"Generator failed: {res.stderr}"

        manifest = json.loads(output_json.read_text("utf-8"))
        apk_url = manifest["apk"]["url"]
        assert apk_url.endswith("RusMorph-0.004-production.apk"), (
            f"Expected URL ending with RusMorph-0.004-production.apk, got {apk_url}"
        )
        assert "app-production-release.apk" not in apk_url, (
            "Gradle internal APK name must not appear in the release URL"
        )
        assert manifest["versionCode"] == 4
        assert manifest["versionName"] == "0.004"
        assert manifest["apk"]["size"] == len(b"DUMMY_APK_CONTENT")


def test_generator_tag_mismatch_fails():
    with tempfile.TemporaryDirectory() as td:
        tdp = Path(td)
        gradle_file = tdp / "build.gradle.kts"
        gradle_file.write_text(
            'versionCode = 4\nversionName = "0.004"\n',
            encoding="utf-8",
        )
        local_apk = tdp / "dummy.apk"
        local_apk.write_bytes(b"DUMMY")
        output_json = tdp / "out.json"

        cmd = [
            sys.executable,
            str(GENERATOR_SCRIPT),
            "--gradle", str(gradle_file),
            "--apk", str(local_apk),
            "--tag", "v0.005",
            "--output", str(output_json),
        ]
        res = subprocess.run(cmd, capture_output=True, text=True)
        assert res.returncode != 0
        assert "must equal" in res.stderr
