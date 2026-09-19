#!/usr/bin/env python3
"""Non-mutating API smoke test for local, staging, or production gateways."""
from __future__ import annotations

import json
import os
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


base_url = os.environ.get("API_BASE_URL", "http://127.0.0.1:8787").rstrip("/")
live = os.environ.get("RUSMORPH_LIVE_API_TEST") == "1"


def check_health() -> bool:
    try:
        with urlopen(Request(f"{base_url}/health", headers={"Accept": "application/json"}), timeout=10) as response:
            body = json.loads(response.read())
            headers = {name.lower(): value for name, value in response.headers.items()}
            missing = [name for name in ("content-security-policy", "referrer-policy", "x-content-type-options", "x-frame-options") if name not in headers]
            if response.status != 200 or body.get("status") not in {"ok", "degraded"} or missing:
                print(f"FAIL health status={response.status} missing_headers={','.join(missing) or '-'}")
                return False
            print(f"PASS health ({body.get('status')})")
            return True
    except (HTTPError, URLError, TimeoutError, json.JSONDecodeError) as error:
        print(f"FAIL health {error}")
        return False


if __name__ == "__main__":
    print(f"API base: {base_url}")
    ok = check_health()
    print("SKIP mutating/paid routes (set RUSMORPH_LIVE_API_TEST=1 only in a dedicated test environment)" if not live else "SKIP live mutations: no production-safe fixture identity configured")
    sys.exit(0 if ok else 1)
