#!/usr/bin/env python3
"""Patch the built app Info.plist with CLIENT_ID and REVERSED_CLIENT_ID from GoogleService-Info.plist.

Called from an Xcode Run Script phase so OAuth URL schemes match Firebase without committing
client IDs into the source Info.plist. No-op if inputs are missing or keys absent.
"""
from __future__ import annotations

import plistlib
import sys
from pathlib import Path


def main() -> None:
    if len(sys.argv) != 3:
        print(
            "usage: patch-app-info-google-oauth.py <GoogleService-Info.plist> <AppBundleInfo.plist>",
            file=sys.stderr,
        )
        sys.exit(2)
    service_path = Path(sys.argv[1])
    info_path = Path(sys.argv[2])
    if not service_path.is_file() or not info_path.is_file():
        sys.exit(0)
    with service_path.open("rb") as f:
        gs = plistlib.load(f)
    rev = gs.get("REVERSED_CLIENT_ID")
    cid = gs.get("CLIENT_ID")
    if not rev or not cid:
        print(
            "patch-app-info-google-oauth: GoogleService-Info missing REVERSED_CLIENT_ID or CLIENT_ID",
            file=sys.stderr,
        )
        sys.exit(0)
    with info_path.open("rb") as f:
        info = plistlib.load(f)
    info["GIDClientID"] = cid
    url_types = info.get("CFBundleURLTypes")
    if not isinstance(url_types, list):
        url_types = []
    replaced = False
    for ut in url_types:
        if isinstance(ut, dict) and ut.get("CFBundleURLName") == "google":
            ut["CFBundleURLSchemes"] = [rev]
            replaced = True
            break
    if not replaced:
        url_types.append(
            {
                "CFBundleTypeRole": "Editor",
                "CFBundleURLName": "google",
                "CFBundleURLSchemes": [rev],
            }
        )
    info["CFBundleURLTypes"] = url_types
    with info_path.open("wb") as f:
        plistlib.dump(info, f, fmt=plistlib.FMT_XML)


if __name__ == "__main__":
    main()
