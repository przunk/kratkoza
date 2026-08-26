package com.przunk.kratkoza.domain.daily

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Which Daily is the current one (project.md section 26).
 *
 * A new set appears at 08:00 Warsaw time, and that hour is the same for everybody — not each
 * player's local midnight. Every player has to be racing the same board on the same day for a
 * shared ranking to mean anything, and a boundary tied to the device's own clock would also hand
 * out tomorrow's puzzles to anyone who moved their clock forward.
 *
 * Warsaw wall-clock rather than a fixed UTC offset, so the changeover stays at 08:00 through the
 * daylight-saving switch instead of drifting to 07:00 for half the year.
 */
object DailySchedule {

    val ZONE: ZoneId = ZoneId.of("Europe/Warsaw")

    /** The moment a new set replaces the previous one. */
    val CHANGEOVER: LocalTime = LocalTime.of(8, 0)

    /**
     * The date whose Daily is current at [instant].
     *
     * Before the changeover the day belongs to the previous date: at 07:59 on the 26th the puzzles
     * on offer are still the 25th's.
     */
    fun dailyDateAt(instant: Instant): LocalDate {
        val local = ZonedDateTime.ofInstant(instant, ZONE)
        return if (local.toLocalTime() < CHANGEOVER) {
            local.toLocalDate().minusDays(1)
        } else {
            local.toLocalDate()
        }
    }

    /** When the Daily for [date] appears. */
    fun startOf(date: LocalDate): Instant = date.atTime(CHANGEOVER).atZone(ZONE).toInstant()

    /** When it is replaced — the start of the following day's set. */
    fun endOf(date: LocalDate): Instant = startOf(date.plusDays(1))

    /** How long [instant] still has on [date]'s set; zero once it is over. */
    fun remainingMillis(date: LocalDate, instant: Instant): Long =
        (endOf(date).toEpochMilli() - instant.toEpochMilli()).coerceAtLeast(0)
}
