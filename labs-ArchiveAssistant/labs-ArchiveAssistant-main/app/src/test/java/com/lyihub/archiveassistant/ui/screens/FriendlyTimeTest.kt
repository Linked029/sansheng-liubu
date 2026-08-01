package com.lyihub.archiveassistant.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class FriendlyTimeTest {
  private val fixedNow = 1_720_000_000_000L

  @Test
  fun friendlyTime_future_returnsFuture() {
    assertEquals("未来", friendlyTime(fixedNow + 60_000, fixedNow))
  }

  @Test
  fun friendlyTime_justNow_returnsJustNow() {
    assertEquals("刚刚", friendlyTime(fixedNow - 30_000, fixedNow))
  }

  @Test
  fun friendlyTime_minutesAgo_returnsMinutes() {
    assertEquals("五分钟前", friendlyTime(fixedNow - 5 * 60_000, fixedNow))
  }

  @Test
  fun friendlyTime_hoursAgo_returnsHours() {
    assertEquals("三小时前", friendlyTime(fixedNow - 3 * 3_600_000, fixedNow))
  }

  @Test
  fun friendlyTime_weekAgo_returnsWeekForRecentDays() {
    assertEquals("一周前", friendlyTime(fixedNow - 2 * 86_400_000, fixedNow))
    assertEquals("一周前", friendlyTime(fixedNow - 10L * 86_400_000, fixedNow))
  }

  @Test
  fun friendlyTime_daysAgo_returnsDaysAfterTwoWeeks() {
    assertEquals("十五天前", friendlyTime(fixedNow - 15L * 86_400_000, fixedNow))
    assertEquals("二十九天前", friendlyTime(fixedNow - 29L * 86_400_000, fixedNow))
  }

  @Test
  fun friendlyTime_longAgo_returnsLongAgoForOverOneMonth() {
    assertEquals("很久以前", friendlyTime(fixedNow - 31L * 86_400_000, fixedNow))
  }
}
