package com.lyihub.archiveassistant.ui.layout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutModeTest {

  @Test
  fun layoutMode_compact_neverShowsTwoPanes() {
    val info =
      WindowLayoutInfo(
        mode = LayoutMode.COMPACT,
        widthDp = androidx.compose.ui.unit.Dp(400f),
      )
    assertFalse(info.shouldShowTwoPanes(null))
    assertFalse(info.shouldShowTwoPanes("topic-1"))
  }

  @Test
  fun layoutMode_expanded_alwaysShowsTwoPanes() {
    val info =
      WindowLayoutInfo(
        mode = LayoutMode.EXPANDED,
        widthDp = androidx.compose.ui.unit.Dp(900f),
      )
    assertTrue(info.shouldShowTwoPanes(null))
    assertTrue(info.shouldShowTwoPanes("topic-1"))
  }

  @Test
  fun layoutMode_foldable_alwaysShowsTwoPanes() {
    val info =
      WindowLayoutInfo(
        mode = LayoutMode.FOLDABLE,
        widthDp = androidx.compose.ui.unit.Dp(900f),
        hingeBounds = listOf(HingeBounds(400, 0, 420, 2000)),
      )
    assertTrue(info.shouldShowTwoPanes(null))
    assertTrue(info.shouldShowTwoPanes("topic-1"))
  }

  @Test
  fun hingeBounds_intersectsHorizontal_correctly() {
    val hinge = HingeBounds(400, 500, 420, 1500)
    assertTrue(hinge.intersectsHorizontal(490, 100))
    assertFalse(hinge.intersectsHorizontal(200, 100))
    assertFalse(hinge.intersectsHorizontal(1600, 100))
  }

  @Test
  fun hingeBounds_intersectsVertical_correctly() {
    val hinge = HingeBounds(400, 500, 420, 1500)
    assertTrue(hinge.intersectsVertical(410, 20))
    assertFalse(hinge.intersectsVertical(200, 100))
    assertFalse(hinge.intersectsVertical(450, 100))
  }

  @Test
  fun hingeBoundsList_isSafeArea_correctly() {
    val hinges = listOf(HingeBounds(400, 500, 420, 1500))
    assertFalse(hinges.isSafeHorizontalArea(490, 100))
    assertTrue(hinges.isSafeHorizontalArea(200, 100))
    assertFalse(hinges.isSafeVerticalArea(410, 20))
    assertTrue(hinges.isSafeVerticalArea(200, 100))
  }
}
