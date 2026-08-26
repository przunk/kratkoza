package com.przunk.kratkoza.domain.daily

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyScheduleTest {

    /** A Warsaw wall-clock moment, as an instant. */
    private fun warsaw(text: String) =
        LocalDateTime.parse(text).atZone(DailySchedule.ZONE).toInstant()

    @Test
    fun `the set changes at eight in the morning, not at midnight`() {
        assertEquals(LocalDate.parse("2026-08-25"), DailySchedule.dailyDateAt(warsaw("2026-08-26T07:59:59")))
        assertEquals(LocalDate.parse("2026-08-26"), DailySchedule.dailyDateAt(warsaw("2026-08-26T08:00:00")))
    }

    @Test
    fun `the small hours still belong to the previous day`() {
        // The case that catches a boundary written as midnight: somebody playing at 2am is on
        // yesterday's puzzles, and their result has to be filed against yesterday.
        assertEquals(LocalDate.parse("2026-08-25"), DailySchedule.dailyDateAt(warsaw("2026-08-26T00:00:00")))
        assertEquals(LocalDate.parse("2026-08-25"), DailySchedule.dailyDateAt(warsaw("2026-08-26T02:30:00")))
    }

    @Test
    fun `a full day is covered without gaps or overlaps`() {
        val date = LocalDate.parse("2026-08-26")
        val start = DailySchedule.startOf(date)
        val end = DailySchedule.endOf(date)

        assertEquals(date, DailySchedule.dailyDateAt(start))
        assertEquals(date, DailySchedule.dailyDateAt(end.minusMillis(1)))
        assertEquals(date.plusDays(1), DailySchedule.dailyDateAt(end))
    }

    @Test
    fun `the changeover stays at eight across the daylight-saving switch`() {
        // A fixed UTC offset would drift to 07:00 for half the year. Poland moves its clocks on the
        // last Sunday of March and October.
        for (day in listOf("2026-03-29", "2026-10-25", "2026-06-15", "2026-12-15")) {
            val date = LocalDate.parse(day)
            val startLocal = DailySchedule.startOf(date).atZone(DailySchedule.ZONE)

            assertEquals("changeover on $day", LocalTime.of(8, 0), startLocal.toLocalTime())
        }
    }

    @Test
    fun `a day is roughly twenty-four hours, and exactly so outside the switch`() {
        val ordinary = LocalDate.parse("2026-06-15")
        assertEquals(
            24 * 60 * 60 * 1000L,
            DailySchedule.endOf(ordinary).toEpochMilli() - DailySchedule.startOf(ordinary).toEpochMilli(),
        )

        // The spring day loses an hour; that is correct, not a bug to paper over.
        val springForward = LocalDate.parse("2026-03-28")
        val length = DailySchedule.endOf(springForward).toEpochMilli() -
            DailySchedule.startOf(springForward).toEpochMilli()
        assertEquals(23 * 60 * 60 * 1000L, length)
    }

    @Test
    fun `the countdown runs out exactly when the next set appears`() {
        val date = LocalDate.parse("2026-08-26")

        assertEquals(0L, DailySchedule.remainingMillis(date, DailySchedule.endOf(date)))
        assertEquals(1L, DailySchedule.remainingMillis(date, DailySchedule.endOf(date).minusMillis(1)))
        assertTrue(DailySchedule.remainingMillis(date, DailySchedule.startOf(date)) > 0)
    }

    @Test
    fun `a countdown asked for after the day is over is zero, never negative`() {
        val date = LocalDate.parse("2026-08-26")
        assertEquals(0L, DailySchedule.remainingMillis(date, warsaw("2026-09-01T12:00:00")))
    }
}
