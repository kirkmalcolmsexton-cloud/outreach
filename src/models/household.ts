export const VISIT_OUTCOMES = [
  "not_home",
  "left_message",
  "receptive",
  "do_not_visit",
  "moved",
  "dawat_saath",
  "other",
] as const;

export type VisitOutcome = (typeof VISIT_OUTCOMES)[number];

export interface SourceMetadata {
  sheetName: string;
  rowNumber: number;
}

export interface RawHouseholdRow {
  briefComments?: string;
  lastVisited?: string;
  name?: string;
  streetAddress?: string;
  neighborhood?: string;
  notes?: string;
}

export interface HouseholdRecord {
  id: string;
  name: string;
  streetAddress: string;
  neighborhood: string;
  briefComment: VisitOutcome | string;
  lastVisited: string | null;
  notes: string | null;
  source: SourceMetadata;
  raw: RawHouseholdRow;
}

export interface SpreadsheetRowInput {
  briefComments?: string;
  lastVisited?: string;
  name?: string;
  streetAddress?: string;
  neighborhood?: string;
  notes?: string;
}
