#!/usr/bin/env python3
"""Verify sa-demo-*.csv match Outreach sheet schema (see Storage.kt validateRequiredHeaders)."""

from __future__ import annotations

import csv
import sys
from pathlib import Path


def canonical_header(raw: str) -> str | None:
    n = " ".join(raw.strip().lower().split())
    return {
        "brief comments": "brief comments",
        "brief comment": "brief comments",
        "last visited": "last visited",
        "last visit": "last visited",
        "name": "name",
        "street address": "street address",
        "neighborhood": "neighborhood",
        "notes": "notes",
    }.get(n)


REQUIRED = frozenset(
    {"brief comments", "last visited", "name", "street address", "neighborhood", "notes"}
)


def main() -> int:
    base = Path(__file__).resolve().parent
    pairs = [("78209", base / "sa-demo-78209.csv"), ("78212", base / "sa-demo-78212.csv")]
    for zip_code, path in pairs:
        with path.open(newline="", encoding="utf-8") as f:
            rows = list(csv.reader(f))
        headers = rows[0]
        seen = {canonical_header(h) for h in headers}
        seen.discard(None)
        if not REQUIRED <= seen:
            print(f"FAIL {path.name}: missing {sorted(REQUIRED - seen)}", file=sys.stderr)
            return 1
        if len(rows) != 6:
            print(f"FAIL {path.name}: expected 6 lines (header + 5 rows), got {len(rows)}", file=sys.stderr)
            return 1
        for lineno, row in enumerate(rows[1:], start=2):
            if len(row) < 5:
                print(f"FAIL {path.name}:{lineno}: expected ≥5 columns", file=sys.stderr)
                return 1
            name, addr = row[2].strip(), row[3]
            if not name:
                print(f"FAIL {path.name}:{lineno}: empty Name", file=sys.stderr)
                return 1
            compact = addr.replace(" ", "")
            if zip_code not in compact or "TX" not in compact:
                print(f"FAIL {path.name}:{lineno}: Street Address must include TX and {zip_code}", file=sys.stderr)
                return 1

    keys = base / "sa-demo-keys.csv"
    with keys.open(newline="", encoding="utf-8") as f:
        kr = list(csv.reader(f))
    if not kr or kr[0][0].strip().lower() != "name":
        print("FAIL sa-demo-keys.csv: row 1 must be a Name header", file=sys.stderr)
        return 1
    if len(kr) < 2:
        print("FAIL sa-demo-keys.csv: need at least one data row", file=sys.stderr)
        return 1

    print("OK: sample CSVs match Outreach schema.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
