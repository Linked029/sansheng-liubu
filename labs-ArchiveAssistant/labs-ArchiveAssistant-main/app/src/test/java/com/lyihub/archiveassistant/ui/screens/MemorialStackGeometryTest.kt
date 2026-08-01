package com.lyihub.archiveassistant.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class MemorialStackGeometryTest {
  private val dp: (Float) -> Float = { it * 2f }

  @Test
  fun pose_withoutShift_usesCurrentLevelGeometry() {
    val pose = MemorialStackGeometry.pose(level = 2, shiftProgress = 0f, dp = dp)

    assertEquals(-14f, pose.offsetX, 0.001f)
    assertEquals(40f, pose.offsetY, 0.001f)
    assertEquals(0.972f, pose.scale, 0.001f)
    assertEquals(1.35f, pose.rotation, 0.001f)
  }

  @Test
  fun pose_withFullShift_usesPreviousLevelGeometry() {
    val pose = MemorialStackGeometry.pose(level = 2, shiftProgress = 1f, dp = dp)

    assertEquals(16f, pose.offsetX, 0.001f)
    assertEquals(27f, pose.offsetY, 0.001f)
    assertEquals(0.986f, pose.scale, 0.001f)
    assertEquals(-1.1f, pose.rotation, 0.001f)
  }

  @Test
  fun pose_clampsShiftProgress() {
    val below = MemorialStackGeometry.pose(level = 1, shiftProgress = -1f, dp = dp)
    val above = MemorialStackGeometry.pose(level = 1, shiftProgress = 2f, dp = dp)

    assertEquals(16f, below.offsetX, 0.001f)
    assertEquals(27f, below.offsetY, 0.001f)
    assertEquals(0f, above.offsetX, 0.001f)
    assertEquals(0f, above.offsetY, 0.001f)
  }

  @Test
  fun pose_halfShiftInterpolatesBetweenLevels() {
    val pose = MemorialStackGeometry.pose(level = 1, shiftProgress = 0.5f, dp = dp)

    assertEquals(8f, pose.offsetX, 0.001f)
    assertEquals(13.5f, pose.offsetY, 0.001f)
    assertEquals(0.993f, pose.scale, 0.001f)
    assertEquals(-0.55f, pose.rotation, 0.001f)
  }
}
