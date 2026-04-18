package org.outreach.feature.visits

import org.junit.Assert.assertEquals
import org.junit.Test
import org.outreach.core.model.HouseholdRecord
import org.outreach.core.model.RawHouseholdRow
import org.outreach.core.model.SourceMetadata

class NextTargetSelectorTest {
    @Test
    fun choosesOldestVisitDate() {
        val older = sample("a", "2019-01-01")
        val newer = sample("b", "2022-01-01")
        val target = selectNextTarget(listOf(newer, older))
        assertEquals("a", target?.id)
    }

    private fun sample(id: String, lastVisited: String?) = HouseholdRecord(
        id = id,
        name = id,
        streetAddress = "x",
        neighborhood = "y",
        briefComment = "other",
        lastVisited = lastVisited,
        notes = null,
        source = SourceMetadata("60657", 2),
        raw = RawHouseholdRow()
    )
}
