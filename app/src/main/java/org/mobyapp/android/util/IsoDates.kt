package org.mobyapp.android.util

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** ISO 8601 dates, which the API requires to include a UTC offset. */
object IsoDates {

    /** Formats a date with its offset, such as "2026-10-27T18:30:00-06:00". */
    fun format(date: ZonedDateTime): String =
        date.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    /** Parses a date from the API into the given time zone. */
    fun parse(value: String, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime? =
        runCatching { OffsetDateTime.parse(value).atZoneSameInstant(zone) }.getOrNull()

    /** Formats a date from the API for display, in the device's time zone. */
    fun display(value: String, zone: ZoneId = ZoneId.systemDefault()): String =
        parse(value, zone)?.let { display(it) } ?: value

    fun display(date: ZonedDateTime): String =
        date.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
}
