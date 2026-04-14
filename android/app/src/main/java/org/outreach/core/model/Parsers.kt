package org.outreach.core.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val fullDateRegex = Regex("""^(\d{1,2})\/(\d{1,2})\/(\d{2}|\d{4})$""")
private val normalizationRules = listOf(
    Regex("^not\\s*home$", RegexOption.IGNORE_CASE) to VisitOutcome.NOT_HOME,
    Regex("^left\\s*mess?a?g?e?$", RegexOption.IGNORE_CASE) to VisitOutcome.LEFT_MESSAGE,
    Regex("^receptive$", RegexOption.IGNORE_CASE) to VisitOutcome.RECEPTIVE,
    Regex("^do\\s*not\\s*visit$", RegexOption.IGNORE_CASE) to VisitOutcome.DO_NOT_VISIT,
    Regex("^moved$", RegexOption.IGNORE_CASE) to VisitOutcome.MOVED,
    Regex("^dawat\\s*saath$", RegexOption.IGNORE_CASE) to VisitOutcome.DAWAT_SAATH
)

fun normalizeText(value: String?): String = value?.trim().orEmpty()

fun normalizeOptionalText(value: String?): String? = normalizeText(value).ifBlank { null }

fun normalizeBriefComment(value: String?): String {
    val normalized = normalizeText(value)
    if (normalized.isBlank()) return VisitOutcome.OTHER.name.lowercase(Locale.US)
    val mapped = normalizationRules.firstOrNull { (pattern, _) -> pattern.matches(normalized) }?.second
    return mapped?.name?.lowercase(Locale.US) ?: normalized
}

fun parseLastVisited(value: String?): String? {
    val normalized = normalizeText(value)
    if (normalized.isBlank()) return null
    val match = fullDateRegex.matchEntire(normalized) ?: return null
    val month = match.groupValues[1].toInt()
    val day = match.groupValues[2].toInt()
    val rawYear = match.groupValues[3]
    val year = if (rawYear.length == 2) 2000 + rawYear.toInt() else rawYear.toInt()
    return try {
        LocalDate.of(year, month, day).format(DateTimeFormatter.ISO_DATE)
    } catch (_: DateTimeParseException) {
        null
    }
}

fun createHouseholdId(name: String, streetAddress: String, neighborhood: String): String {
    val base = "$name|$streetAddress|$neighborhood"
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9|]+"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')
    return "household_${if (base.isBlank()) "unknown" else base}"
}

fun parseSpreadsheetRow(
    row: SpreadsheetRowInput,
    source: SourceMetadata
): HouseholdRecord {
    val name = normalizeText(row.name)
    val streetAddress = normalizeText(row.streetAddress)
    val neighborhood = normalizeText(row.neighborhood)
    return HouseholdRecord(
        id = createHouseholdId(name, streetAddress, neighborhood),
        name = name,
        streetAddress = streetAddress,
        neighborhood = neighborhood,
        briefComment = normalizeBriefComment(row.briefComments),
        lastVisited = parseLastVisited(row.lastVisited),
        notes = normalizeOptionalText(row.notes),
        source = source,
        raw = RawHouseholdRow(
            briefComments = row.briefComments,
            lastVisited = row.lastVisited,
            name = row.name,
            streetAddress = row.streetAddress,
            neighborhood = row.neighborhood,
            notes = row.notes
        )
    )
}
