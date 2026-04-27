package org.outreach.testing

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue

/**
 * Asserts the vertical center of the node tagged [tagA] is above the center of [tagB] (portrait order).
 */
fun ComposeTestRule.assertCenterYIsAbove(
    tagA: String,
    tagB: String,
    useUnmergedTree: Boolean = false
) {
    val a = onNodeWithTag(tagA, useUnmergedTree).getUnclippedBoundsInRoot()
    val b = onNodeWithTag(tagB, useUnmergedTree).getUnclippedBoundsInRoot()
    val cya = ((a.top + a.bottom) / 2f).value
    val cyb = ((b.top + b.bottom) / 2f).value
    assertTrue(
        "Layout order: expect centerY($tagA)=$cya < centerY($tagB)=$cyb (top/reading order).",
        cya < cyb
    )
}

/** Asserts the horizontal center of [tagA] is to the left of [tagB] (e.g. icon rows). */
fun ComposeTestRule.assertCenterXIsToTheLeft(
    tagA: String,
    tagB: String,
    useUnmergedTree: Boolean = false
) {
    val a = onNodeWithTag(tagA, useUnmergedTree).getUnclippedBoundsInRoot()
    val b = onNodeWithTag(tagB, useUnmergedTree).getUnclippedBoundsInRoot()
    val cxa = ((a.left + a.right) / 2f).value
    val cxb = ((b.left + b.right) / 2f).value
    assertTrue(
        "Layout order: expect centerX($tagA)=$cxa < centerX($tagB)=$cxb (LTR).",
        cxa < cxb
    )
}
