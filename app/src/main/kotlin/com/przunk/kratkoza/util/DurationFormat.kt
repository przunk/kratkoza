package com.przunk.kratkoza.util

import android.content.Context
import android.text.format.DateUtils
import com.przunk.kratkoza.R
import java.util.concurrent.TimeUnit

/**
 * Play time as `m:ss`, or `h:mm:ss` once it passes an hour.
 *
 * The pattern comes from string resources rather than from `String.format` in code, because the
 * separator and digit shapes are a locale's business (project.md section 4).
 */
fun formatDuration(context: Context, millis: Long): String {
    val total = millis.coerceAtLeast(0)
    val hours = TimeUnit.MILLISECONDS.toHours(total)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(total) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(total) % 60

    return if (hours > 0) {
        context.getString(R.string.duration_hours_minutes_seconds, hours, minutes, seconds)
    } else {
        context.getString(R.string.duration_minutes_seconds, minutes, seconds)
    }
}

/**
 * A date and time in the reader's own locale (project.md section 4).
 *
 * Uses the platform formatter rather than a pattern of our own: date order, the 12/24-hour clock and
 * the separators are the locale's decision, and no hand-written pattern gets that right everywhere.
 */
fun formatDateTime(context: Context, epochMillis: Long): String =
    DateUtils.formatDateTime(
        context,
        epochMillis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
