import type { HouseholdRecord, SpreadsheetRowInput, VisitOutcome } from "./household";

const COMMENT_NORMALIZATION_RULES: Array<[RegExp, VisitOutcome]> = [
  [/^not\s*home$/i, "not_home"],
  [/^left\s*mess?a?g?e?$/i, "left_message"],
  [/^receptive$/i, "receptive"],
  [/^do\s*not\s*visit$/i, "do_not_visit"],
  [/^moved$/i, "moved"],
  [/^dawat\s*saath$/i, "dawat_saath"],
];

function normalizeText(value: string | undefined): string {
  return (value ?? "").trim();
}

function normalizeOptionalText(value: string | undefined): string | null {
  const normalized = normalizeText(value);
  return normalized.length > 0 ? normalized : null;
}

export function normalizeBriefComment(value: string | undefined): VisitOutcome | string {
  const normalized = normalizeText(value);
  if (!normalized) {
    return "other";
  }

  for (const [pattern, outcome] of COMMENT_NORMALIZATION_RULES) {
    if (pattern.test(normalized)) {
      return outcome;
    }
  }

  return normalized;
}

export function parseLastVisited(value: string | undefined): string | null {
  const normalized = normalizeText(value);
  if (!normalized) {
    return null;
  }

  const fullDateMatch = normalized.match(/^(\d{1,2})\/(\d{1,2})\/(\d{2}|\d{4})$/);
  if (fullDateMatch) {
    const [, monthPart, dayPart, yearPart] = fullDateMatch;
    const month = Number(monthPart);
    const day = Number(dayPart);
    const year = yearPart.length === 2 ? 2000 + Number(yearPart) : Number(yearPart);

    const parsed = new Date(Date.UTC(year, month - 1, day));
    if (
      parsed.getUTCFullYear() === year &&
      parsed.getUTCMonth() + 1 === month &&
      parsed.getUTCDate() === day
    ) {
      return parsed.toISOString().slice(0, 10);
    }
  }

  return null;
}

export function createHouseholdId(input: {
  name: string;
  streetAddress: string;
  neighborhood: string;
}): string {
  const base = `${input.name}|${input.streetAddress}|${input.neighborhood}`
    .toLowerCase()
    .replace(/[^a-z0-9|]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/(^-|-$)/g, "");

  return `household_${base || "unknown"}`;
}

export function parseSpreadsheetRow(
  row: SpreadsheetRowInput,
  context: { sheetName: string; rowNumber: number }
): HouseholdRecord {
  const name = normalizeText(row.name);
  const streetAddress = normalizeText(row.streetAddress);
  const neighborhood = normalizeText(row.neighborhood);

  return {
    id: createHouseholdId({ name, streetAddress, neighborhood }),
    name,
    streetAddress,
    neighborhood,
    briefComment: normalizeBriefComment(row.briefComments),
    lastVisited: parseLastVisited(row.lastVisited),
    notes: normalizeOptionalText(row.notes),
    source: {
      sheetName: context.sheetName,
      rowNumber: context.rowNumber,
    },
    raw: {
      briefComments: row.briefComments,
      lastVisited: row.lastVisited,
      name: row.name,
      streetAddress: row.streetAddress,
      neighborhood: row.neighborhood,
      notes: row.notes,
    },
  };
}
