#!/usr/bin/env python3
"""Download OpenRussian dataset CSV files from GitHub."""

import sys
import urllib.request
from pathlib import Path

BASE_URL = "https://raw.githubusercontent.com/Badestrand/russian-dictionary/master/"
FILES = ["nouns.csv", "verbs.csv", "adjectives.csv", "others.csv"]


def download_all(target_dir: Path) -> None:
    target_dir.mkdir(parents=True, exist_ok=True)
    for filename in FILES:
        dest = target_dir / filename
        if dest.is_file() and dest.stat().st_size > 1000:
            print(f"File {filename} already exists ({dest.stat().st_size} bytes), skipping.")
            continue
        url = BASE_URL + filename
        print(f"Downloading {filename} from {url}...")
        try:
            req = urllib.request.Request(
                url,
                headers={"User-Agent": "RusMorph-Builder/1.0"}
            )
            with urllib.request.urlopen(req, timeout=60) as resp, open(dest, "wb") as out:
                total = 0
                while chunk := resp.read(65536):
                    out.write(chunk)
                    total += len(chunk)
            print(f"Downloaded {filename}: {total} bytes.")
        except Exception as exc:
            print(f"Failed to download {filename}: {exc}", file=sys.stderr)
            raise


if __name__ == "__main__":
    target = Path(__file__).resolve().parents[2] / "data-source" / "openrussian"
    download_all(target)
