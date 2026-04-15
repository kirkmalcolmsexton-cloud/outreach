package org.outreach.feature.map

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.outreach.core.model.HouseholdRecord
import org.outreach.core.model.RawHouseholdRow
import org.outreach.core.model.SourceMetadata

class MapFilterUtilsTest {
    @Test
    fun parseIsoDateOrNull_handlesNullBlankAndInvalid() {
        assertNull(parseIsoDateOrNull(null))
        assertNull(parseIsoDateOrNull(" "))
        assertNull(parseIsoDateOrNull("2024/01/10"))
    }

    @Test
    fun formatBriefComment_normalizesUnderscoresAndCasing() {
        assertEquals("Do Not Visit", formatBriefComment("do_not_visit"))
        assertEquals("Left Message", formatBriefComment(" left   message "))
    }

    @Test
    fun householdMatchesTextSearch_matchesNameAndAddress_caseInsensitive() {
        val household = sample(
            id = "1",
            name = "Fatima Khan",
            streetAddress = "123 Main Street"
        )

        assertTrue(householdMatchesTextSearch(household, "fatima"))
        assertTrue(householdMatchesTextSearch(household, "MAIN"))
        assertTrue(householdMatchesTextSearch(household, "   "))
    }

    @Test
    fun filterHouseholdsForMap_appliesTabCommentAndDateFilters() {
        val inRange = sample(
            id = "in-range",
            sheetName = "60657",
            briefComment = "other",
            lastVisited = "2024-01-15"
        )
        val wrongTab = sample(
            id = "wrong-tab",
            sheetName = "60618",
            briefComment = "other",
            lastVisited = "2024-01-15"
        )
        val wrongComment = sample(
            id = "wrong-comment",
            sheetName = "60657",
            briefComment = "do_not_visit",
            lastVisited = "2024-01-15"
        )
        val outOfRange = sample(
            id = "out-of-range",
            sheetName = "60657",
            briefComment = "other",
            lastVisited = "2022-10-01"
        )
        val noDate = sample(
            id = "no-date",
            sheetName = "60657",
            briefComment = "other",
            lastVisited = null
        )

        val filtered = filterHouseholdsForMap(
            data = listOf(inRange, wrongTab, wrongComment, outOfRange, noDate),
            visibleZipTabs = setOf("60657"),
            briefCommentMode = "pick_some",
            selectedBriefComments = setOf("other"),
            startDate = LocalDate.parse("2024-01-01"),
            endDate = LocalDate.parse("2024-12-31")
        )

        assertEquals(listOf("in-range", "no-date"), filtered.map { it.id })
    }

    @Test
    fun applyOldestRecordsLimit_sortsByVisitDateThenId_andRespectsLimit() {
        val noDate = sample(id = "c", lastVisited = null)
        val older = sample(id = "b", lastVisited = "2020-01-01")
        val newer = sample(id = "a", lastVisited = "2023-01-01")

        val limited = applyOldestRecordsLimit(listOf(newer, older, noDate), 2)

        assertEquals(listOf("c", "b"), limited.map { it.id })
    }

    private fun sample(
        id: String,
        name: String = id,
        streetAddress: String = "123 Test St",
        sheetName: String = "60657",
        briefComment: String = "other",
        lastVisited: String? = null
    ) = HouseholdRecord(
        id = id,
        name = name,
        streetAddress = streetAddress,
        neighborhood = "Lakeview",
        briefComment = briefComment,
        lastVisited = lastVisited,
        notes = null,
        source = SourceMetadata(sheetName = sheetName, rowNumber = 1),
        raw = RawHouseholdRow()
    )
}
