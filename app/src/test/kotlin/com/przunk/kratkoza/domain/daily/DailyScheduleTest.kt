package com.przunk.kratkoza.domain.daily

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyScheduleTest {

    private fun utc(text: String): Instant =
        ZonedDateTime.parse(text + "Z", java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME)
            .toInstant()

    private val warsaw = ZoneId.of("Europe/Warsaw")

    @Test
    fun `the set changes at seven UTC, not at midnight`() {
        assertEquals(LocalDate.parse("2026-08-25"), DailySchedule.dailyDateAt(utc("2026-08-26T06:59:59")))
        assertEquals(LocalDate.parse("2026-08-26"), DailySchedule.dailyDateAt(utc("2026-08-26T07:00:00")))
    }

    @Test
    fun `the small hours still belong to the previous day`() {
        assertEquals(LocalDate.parse("2026-08-25"), DailySchedule.dailyDateAt(utc("2026-08-26T00:00:00")))
        assertEquals(LocalDate.parse("2026-08-25"), DailySchedule.dailyDateAt(utc("2026-08-26T02:30:00")))
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
    fun `every day is exactly twenty-four hours, daylight saving included`() {
        // The point of pinning the changeover to UTC. A wall-clock boundary makes the day around
        // the switch 23 or 25 hours long, and a leaderboard cut at a fixed instant would then
        // disagree with it about which puzzle a result belongs to.
        for (day in listOf("2026-03-28", "2026-03-29", "2026-10-24", "2026-10-25", "2026-06-15")) {
            val date = LocalDate.parse(day)
            assertEquals(
                "day length on $day",
                24 * 60 * 60 * 1000L,
                DailySchedule.endOf(date).toEpochMilli() - DailySchedule.startOf(date).toEpochMilli(),
            )
        }
    }

    @Test
    fun `the local hour of the changeover moves with daylight saving, and is reported as it moves`() {
        // The accepted cost of variant A: the clock face shifts, the instant does not. Anything
        // shown to the player has to be computed, never assumed.
        assertEquals(LocalTime.of(9, 0), DailySchedule.localChangeover(LocalDate.parse("2026-06-15"), warsaw))
        assertEquals(LocalTime.of(8, 0), DailySchedule.localChangeover(LocalDate.parse("2026-12-15"), warsaw))
    }

    @Test
    fun `the changeover is the same instant everywhere`() {
        val date = LocalDate.parse("2026-06-15")
        val start = DailySchedule.startOf(date)

        for (zone in listOf("Europe/Warsaw", "America/New_York", "Asia/Tokyo", "UTC")) {
            assertEquals(
                "a player in $zone must switch at the same moment",
                date,
                DailySchedule.dailyDateAt(start),
            )
        }
        assertEquals(LocalTime.of(7, 0), start.atZone(ZoneOffset.UTC).toLocalTime())
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
        assertEquals(0L, DailySchedule.remainingMillis(LocalDate.parse("2026-08-26"), utc("2026-09-01T12:00:00")))
    }
}
