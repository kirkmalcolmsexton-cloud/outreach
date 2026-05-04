#!/usr/bin/env python3
"""Populate an Outreach demo Google Sheet from docs/samples CSVs (OAuth required once).

Reads COMMA-separated samples:
  docs/samples/sa-demo-78209.csv  → tab 78209
  docs/samples/sa-demo-78212.csv  → tab 78212
  docs/samples/sa-demo-keys.csv   → tab keys

Usage:
  export OUTREACH_WEB_CLIENT_SECRET_JSON=/path/to/client_secret_....apps.googleusercontent.com.json
  ./populate_demo_spreadsheet.py SPREADSHEET_ID

First run opens a browser for Google sign-in (needs spreadsheets scope).
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import sys
from pathlib import Path

from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build

SCOPES = ["https://www.googleapis.com/auth/spreadsheets"]

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
SAMPLES = REPO_ROOT / "docs" / "samples"
TOKEN_PATH = SCRIPT_DIR / "token.populate_sheets.json"


def _find_default_web_client_secret() -> Path | None:
    docs = REPO_ROOT / "docs"
    if not docs.is_dir():
        return None
    for p in sorted(docs.glob("client_secret_*apps.googleusercontent.com.json")):
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
            if "web" in data and data["web"].get("client_secret"):
                return p
        except OSError:
            continue
    return None


def _load_csv_rows(path: Path) -> list[list[str]]:
    with path.open(newline="", encoding="utf-8") as f:
        return [row for row in csv.reader(f)]


def _credentials(client_secret: Path) -> Credentials:
    creds: Credentials | None = None
    if TOKEN_PATH.exists():
        creds = Credentials.from_authorized_user_file(str(TOKEN_PATH), SCOPES)
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            flow = InstalledAppFlow.from_client_secrets_file(str(client_secret), SCOPES)
            creds = flow.run_local_server(port=0)
        TOKEN_PATH.write_text(creds.to_json(), encoding="utf-8")
    return creds


def _ensure_tabs(service, spreadsheet_id: str, titles: list[str]) -> None:
    ss = (
        service.spreadsheets()
        .get(spreadsheetId=spreadsheet_id, fields="sheets(properties(sheetId,title))")
        .execute()
    )
    existing = {s["properties"]["title"] for s in ss.get("sheets", [])}
    requests_body = [
        {"addSheet": {"properties": {"title": t}}}
        for t in titles
        if t not in existing
    ]
    if requests_body:
        service.spreadsheets().batchUpdate(
            spreadsheetId=spreadsheet_id, body={"requests": requests_body}
        ).execute()


def main() -> int:
    parser = argparse.ArgumentParser(description="Populate Outreach demo sheet from CSV samples.")
    parser.add_argument(
        "spreadsheet_id",
        nargs="?",
        default=os.environ.get("OUTREACH_SPREADSHEET_ID", ""),
        help="Google Sheets spreadsheet id (from the URL)",
    )
    args = parser.parse_args()
    sid = args.spreadsheet_id.strip()
    if not sid:
        print("usage: populate_demo_spreadsheet.py SPREADSHEET_ID", file=sys.stderr)
        return 2

    secret_env = os.environ.get("OUTREACH_WEB_CLIENT_SECRET_JSON", "").strip()
    client_secret = Path(secret_env) if secret_env else None
    if client_secret is None or not client_secret.is_file():
        client_secret = _find_default_web_client_secret()
    if client_secret is None or not client_secret.is_file():
        print(
            "Set OUTREACH_WEB_CLIENT_SECRET_JSON to your OAuth **web** client JSON "
            "(download from Google Cloud Console; must include client_secret).",
            file=sys.stderr,
        )
        return 1

    for name in ("sa-demo-78209.csv", "sa-demo-78212.csv", "sa-demo-keys.csv"):
        if not (SAMPLES / name).is_file():
            print(f"Missing sample file: {SAMPLES / name}", file=sys.stderr)
            return 1

    creds = _credentials(client_secret)
    service = build("sheets", "v4", credentials=creds)

    _ensure_tabs(service, sid, ["78209", "78212", "keys"])

    def qtab(name: str) -> str:
        """A1 range prefix for sheet titles that are numeric."""
        return f"'{name}'" if name.isdigit() else name

    rows_09 = _load_csv_rows(SAMPLES / "sa-demo-78209.csv")
    rows_12 = _load_csv_rows(SAMPLES / "sa-demo-78212.csv")
    rows_keys = _load_csv_rows(SAMPLES / "sa-demo-keys.csv")

    data = [
        {"range": f"{qtab('78209')}!A1", "majorDimension": "ROWS", "values": rows_09},
        {"range": f"{qtab('78212')}!A1", "majorDimension": "ROWS", "values": rows_12},
        {"range": f"{qtab('keys')}!A1", "majorDimension": "ROWS", "values": rows_keys},
    ]

    service.spreadsheets().values().batchUpdate(
        spreadsheetId=sid,
        body={"valueInputOption": "USER_ENTERED", "data": data},
    ).execute()

    print(f"Updated spreadsheet {sid}: tabs 78209, 78212, keys (sample rows from {SAMPLES}).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
