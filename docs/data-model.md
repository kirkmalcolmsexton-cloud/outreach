# Outreach Data Model

This model is derived from the spreadsheet tab shown in the provided screenshot.

## Spreadsheet Columns

- `Brief Comments`
- `Last Visited`
- `Name`
- `Street Address`
- `Neighborhood`
- `Notes`

## Domain Model

Primary entity: `HouseholdRecord`

- `id`: stable generated identifier from name + address + neighborhood
- `name`: normalized row `Name`
- `streetAddress`: normalized row `Street Address`
- `neighborhood`: normalized row `Neighborhood`
- `briefComment`: canonical visit outcome enum when recognized, else raw text
- `lastVisited`: ISO date (`YYYY-MM-DD`) when parseable with year present, else `null`
- `notes`: optional free text
- `source.sheetName`: source tab name
- `source.rowNumber`: original spreadsheet row number
- `raw`: original raw string values for traceability

## Visit Outcome Enum

Canonical values:

- `not_home`
- `left_message`
- `receptive`
- `do_not_visit`
- `moved`
- `dawat_saath`
- `other`

Rows that do not match known patterns are retained as original text in `briefComment`.

## Column Mapping

| Spreadsheet Column | Model Field | Rule |
| --- | --- | --- |
| Brief Comments | `briefComment` | Normalize variants to enum (`Not home`, `left message`, `Do not visit`, etc.); fallback to original text |
| Last Visited | `lastVisited` | Parse `M/D/YY` or `M/D/YYYY` to ISO date; values without a year become `null` |
| Name | `name` | Trim whitespace |
| Street Address | `streetAddress` | Trim whitespace and preserve source formatting |
| Neighborhood | `neighborhood` | Trim whitespace |
| Notes | `notes` | Trim whitespace; empty values become `null` |

## Notes on Data Quality

- Dates like `2/25` are preserved only in `raw.lastVisited` until year is supplied.
- Minor spelling variants in comments are normalized where safe.
- `notes` remains unstructured to avoid losing context from outreach observations.
