package org.outreach.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsersTest {

    @Test
    fun `normalize brief comment maps known values`() {
        assertEquals("left_message", normalizeBriefComment("left message"))
        assertEquals("do_not_visit", normalizeBriefComment("Do not visit"))
    }

    @Test
    fun `parse last visited supports 2 or 4 digit year`() {
        assertEquals("2019-09-11", parseLastVisited("9/11/19"))
        assertEquals("2022-11-06", parseLastVisited("11/6/2022"))
    }

    @Test
    fun `parse last visited returns null when year absent`() {
        assertNull(parseLastVisited("2/25"))
    }
}
