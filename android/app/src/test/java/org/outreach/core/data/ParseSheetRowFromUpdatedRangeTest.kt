package org.outreach.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseSheetRowFromUpdatedRangeTest {
    @Test
    fun parsesSimpleRange() {
        assertEquals(10, parseSheetRowFromUpdatedRange("Sheet1!A10:F10"))
    }

    @Test
    fun parsesQuotedTabName() {
        assertEquals(2, parseSheetRowFromUpdatedRange("'My Tab'!B2:E2"))
    }

    @Test
    fun invalidReturnsNull() {
        assertNull(parseSheetRowFromUpdatedRange(""))
    }
}
