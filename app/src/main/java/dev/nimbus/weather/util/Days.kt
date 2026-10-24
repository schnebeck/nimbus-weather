/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/util/Days.kt
 * The days of the clock at a place: from midnight to midnight, 23, 24 or 25 hours long.
 *
 *   Copyright (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 *   Produced by Thorsten Schnebeck - the idea, the decisions, the testing.
 *   Written by Anthropic Claude Opus 5.5 - AI generated content.
 *
 *   Free software under the GNU General Public License, version 3 or later.
 *   There is no warranty, to the extent permitted by law. The full text is in
 *   LICENSES/GPL-3.0-or-later.txt.
 *
 * SPDX-FileCopyrightText: (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 * SPDX-FileContributor: Anthropic Claude Opus 5.5 (AI generated content)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.nimbus.weather.util

import java.time.Instant
import java.time.ZoneId

/**
 * A day of the clock is not always 24 hours: the day the clocks go back has 25, the day they go
 * forward 23. Its end is the next midnight, its hours are named by the clock – a day counted in
 * fixed 24 hours loses its last hour or takes the next day's first.
 */
object Days {
    private const val HOUR = 3_600_000L

    /** The midnight that starts the day [t] lies in, at [zone]. */
    fun start(t: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(t).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /** The end of the day that starts at [start]: the next midnight – 23, 24 or 25 hours on. */
    fun end(start: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(start).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** [hour] o'clock of the day that starts at [start] (its noon: 13 hours in when the clocks went back). */
    fun at(start: Long, hour: Int, zone: ZoneId): Long =
        Instant.ofEpochMilli(start).atZone(zone).toLocalDate().atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    /**
     * The full hours from [from] (a full hour) to before [to] that the clock names a multiple of
     * [every]: 00, 03 … 21 – on the day the clocks go back the 03 an hour later than 3 hours in.
     */
    fun clockHours(from: Long, to: Long, every: Int, zone: ZoneId): List<Long> =
        generateSequence(from) { it + HOUR }.takeWhile { it < to }
            .filter { Instant.ofEpochMilli(it).atZone(zone).hour % every == 0 }.toList()
}
