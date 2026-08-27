package com.przunk.kratkoza.domain.daily

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Which Daily is the current one (project.md section 26).
 *
 * A new set appears at the same instant for everybody, and that instant is fixed in **UTC** rather
 * than to a wall clock anywhere. Two reasons, and the second is the one that settled it:
 *
 *  - a boundary tied to each device's local midnight would hand different players different puzzles
 *    at different times, and hand tomorrow's to anybody who moved their clock forward;
 *  - Play Games Services resets its daily leaderboards at a fixed UTC offset all year round. A
 *    changeover pinned to Warsaw wall-clock time agrees with that in winter and drifts an hour away
 *    in summer, which would file the first hour of a new puzzle's results under the previous day.
 *
 * The cost is that the local hour moves with daylight saving: 09:00 in Warsaw in summer, 08:00 in
 * winter. That was the deliberate trade — a ranking that lines up beats a tidy clock face.
 */
object DailySchedule {

    /** When a new set replaces the previous one, in UTC. */
    val CHANGEOVER_UTC: LocalTime = LocalTime.of(7, 0)

    /** Only for showing the changeover to the player in their own time. */
    val DISPLAY_ZONE: ZoneId = ZoneId.of("Europe/Warsaw")

    /**
     * The date whose Daily is current at [instant].
     *
     * Before the changeover the day belongs to the previous date: a minute before it, the puzzles on
     * offer are still yesterday's.
     */
    fun dailyDateAt(instant: Instant): LocalDate {
        val utc = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC)
        return if (utc.toLocalTime() < CHANGEOVER_UTC) {
            utc.toLocalDate().minusDays(1)
        } else {
            utc.toLocalDate()
        }
    }

    /** When the Daily for [date] appears. */
    fun startOf(date: LocalDate): Instant = date.atTime(CHANGEOVER_UTC).toInstant(ZoneOffset.UTC)

    /** When it is replaced — the start of the following day's set. */
    fun endOf(date: LocalDate): Instant = startOf(date.plusDays(1))

    /** How long [instant] still has on [date]'s set; zero once it is over. */
    fun remainingMillis(date: LocalDate, instant: Instant): Long =
        (endOf(date).toEpochMilli() - instant.toEpochMilli()).coerceAtLeast(0)

    /**
     * The changeover as a local time in [zone], for display only.
     *
     * Computed from a real instant rather than by adding an offset, so it tells the truth on both
     * sides of the daylight-saving switch instead of being right for half the year.
     */
    fun localChangeover(date: LocalDate, zone: ZoneId = DISPLAY_ZONE): LocalTime =
        ZonedDateTime.ofInstant(startOf(date), zone).toLocalTime()
}
