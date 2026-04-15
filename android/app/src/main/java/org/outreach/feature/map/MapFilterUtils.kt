package org.outreach.feature.map

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.outreach.core.model.HouseholdRecord

fun parseIsoDateOrNull(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(value, DateTimeFormatter.ISO_DATE) }.getOrNull()
}

fun formatBriefComment(value: String): String {
    return value
        .replace("_", " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}

/** Substring match on [HouseholdRecord.name] or [HouseholdRecord.streetAddress] (case-insensitive). Blank [query] matches all. */
fun householdMatchesTextSearch(household: HouseholdRecord, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return household.name.contains(q, ignoreCase = true) ||
        household.streetAddress.contains(q, ignoreCase = true)
}

fun filterHouseholdsForMap(
    data: List<HouseholdRecord>,
    visibleZipTabs: Set<String>,
    briefCommentMode: String,
    selectedBriefComments: Set<String>,
    startDate: LocalDate,
    endDate: LocalDate
): List<HouseholdRecord> {
    return data.filter { household ->
        val tabMatches =
            visibleZipTabs.isEmpty() || household.source.sheetName in visibleZipTabs
        val briefCommentMatches = when (briefCommentMode) {
            "pick_some" -> household.briefComment in selectedBriefComments
            else -> true
        }
        val visitationDate = parseIsoDateOrNull(household.lastVisited)
        val dateMatches = visitationDate == null ||
            (!visitationDate.isBefore(startDate) && !visitationDate.isAfter(endDate))
        tabMatches && briefCommentMatches && dateMatches
    }
}

fun applyOldestRecordsLimit(households: List<HouseholdRecord>, limit: Int?): List<HouseholdRecord> {
    val requested = limit ?: return households
    if (requested <= 0) return households
    return households
        .sortedWith(
            compareBy<HouseholdRecord>(
                { parseIsoDateOrNull(it.lastVisited)?.toEpochDay() ?: Long.MIN_VALUE },
                { it.id }
            )
        )
        .take(requested)
}
