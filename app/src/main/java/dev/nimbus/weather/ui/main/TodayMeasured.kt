/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/TodayMeasured.kt
 * Today's station readings (DWD via Bright Sky) for the day charts: measured next to forecast.
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

package dev.nimbus.weather.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.nimbus.weather.R
import dev.nimbus.weather.data.remote.History
import dev.nimbus.weather.data.remote.HistoryHour
import dev.nimbus.weather.data.remote.Provenance
import java.time.LocalDate

/**
 * What was measured today, keyed by the hour's time stamp (sums cover the hour before it, like the
 * model values): precipitation in mm – by the radar over the place where it reaches, else the
 * station's gauge ([precipitationFrom]) –, pressure in hPa, and all readings of the hour in [hours].
 */
data class TodayMeasured(
    val precipitation: Map<Long, Double>,
    val pressure: Map<Long, Double>,
    val station: String?,
    val hours: Map<Long, HistoryHour.Measured> = emptyMap(),
    /** Station temperature every 10 minutes today (SYNOP), by time. */
    val fine: Map<Long, Double> = emptyMap(),
    /** Where today's precipitation was measured (null: nowhere). */
    val precipitationFrom: Provenance? = null,
    /** The hour (its end) of the last precipitation reading – of today or the day before; null: none. */
    val lastPrecipitationAt: Long? = null,
    /** The hour (its end) of the last sunshine reading; null: none. */
    val lastSunshineAt: Long? = null,
) {
    /**
     * Today's chart: the hours over show what was measured, the hours to come the forecast; the
     * readout puts measured and expected side by side ([MeteoPoint.compare]). A quantity without a
     * reading in an hour over: still on its way (after its last reading – the satellite's comes
     * 20 minutes, the station's hourly values 1–2 hours late): its forecast's frame until it
     * comes; missing between readings, or not measured at all: nothing drawn.
     */
    fun apply(p: MeteoPoint, now: Long): MeteoPoint {
        val expected = HourCompare(
            null, p.temperature, null, p.precipitation, p.precipitationChance, null, null, p.windSpeed, null, p.windGust,
            null, p.sunshine, feelsF = p.apparentTemperature, humidityF = p.humidity,
        )
        if (p.time > now) return p.copy(compare = expected)
        val m = hours[p.time]
        val t = m?.temperature
        return p.copy(
            temperature = t ?: p.temperature,
            apparentTemperature = if (t != null) p.apparentTemperature?.plus(t - p.temperature) else p.apparentTemperature,
            condition = m?.condition ?: p.condition,
            // what fell and how long the sun shone: measured – or, still to come, expected
            precipitation = m?.precipitation ?: p.precipitation.takeIf { pending(lastPrecipitationAt, p.time) },
            sunshine = m?.sunshineMinutes ?: p.sunshine.takeIf { pending(lastSunshineAt, p.time) },
            // the chance stays as it was forecast
            windSpeed = m?.windSpeed ?: p.windSpeed,
            windDirection = m?.windDirection ?: p.windDirection,
            windGust = m?.windGust ?: p.windGust,
            measured = m != null,
            precipMeasured = m?.precipitation != null,
            sunMeasured = m?.sunshineMinutes != null,
            compare = expected.copy(
                tempM = t, precipM = m?.precipitation, windM = m?.windSpeed, windDirM = m?.windDirection,
                gustM = m?.windGust, sunM = m?.sunshineMinutes,
            ),
        )
    }

    /** An hour after the last reading of a quantity measured at all ([last]): its reading is still to come. */
    private fun pending(last: Long?, time: Long) = last != null && time > last

    companion object {
        fun of(history: History?, today: LocalDate): TodayMeasured? {
            val day = history?.days?.firstOrNull { it.date == today } ?: return null
            val precip = day.hours.mapNotNull { h -> h.measured?.precipitation?.let { h.time to it } }.toMap()
            val pressure = day.hours.mapNotNull { h -> h.measured?.pressure?.let { h.time to it } }.toMap()
            val hours = day.hours.mapNotNull { h -> h.measured?.let { h.time to it } }.toMap()
            if (hours.isEmpty()) return null
            val start = today.atStartOfDay(history.zone).toInstant().toEpochMilli()
            val fine = history.fineMeasured.filterKeys { it in start - 3_600_000L..start + 24 * 3_600_000L }
            val from = day.hours.filter { it.measured?.precipitation != null }.map { it.measured!!.precipitationFrom }
            // the last readings, of today or the day before (just after midnight today has none yet)
            val read = history.allHours.filter { it.measured != null }
            return TodayMeasured(
                precip, pressure, history.stationName, hours, fine,
                precipitationFrom = if (Provenance.RADAR in from) Provenance.RADAR else from.firstOrNull(),
                lastPrecipitationAt = read.lastOrNull { it.measured?.precipitation != null }?.time,
                lastSunshineAt = read.lastOrNull { it.measured?.sunshineMinutes != null }?.time,
            )
        }
    }
}

/**
 * The temperature curve of a day in the forecast: the model every 15 minutes where it has such
 * steps, hourly elsewhere; today the station's readings (every 10 minutes, else hourly) up to the
 * last one, the forecast after it – no gap between them.
 */
fun dayCurve(
    hours: List<dev.nimbus.weather.data.model.HourlyPoint>, minutely: List<dev.nimbus.weather.data.model.MinutelyPoint>,
    start: Long, end: Long, measured: TodayMeasured?,
): List<CurvePoint> {
    val from = start - 3_600_000L
    val forecast = Curve.merge(
        minutely.filter { it.time in from..end && it.temperature != null }.map { CurvePoint(it.time, it.temperature!!, 15 * 60_000L) },
        hours.filter { it.time in from..end }.map { CurvePoint(it.time, it.temperature) },
    )
    if (measured == null) return forecast
    val readings = Curve.merge(
        measured.fine.map { (t, v) -> CurvePoint(t, v, 10 * 60_000L) },
        measured.hours.mapNotNull { (t, m) -> m.temperature?.let { CurvePoint(t, it) } },
    ).filter { it.time in from..end }
    // the forecast goes on from the last reading, no step at "now"
    return Curve.joined(readings, forecast)
}

/**
 * "Measured: …" – where the measured values of [hours] come from: the station ([station]), the
 * radar's precipitation and the satellite's sunshine over the place; null when nothing was measured.
 */
@Composable
fun measuredBy(hours: Collection<HistoryHour.Measured>, station: String?): String? {
    val parts = buildList {
        if (station != null && hours.any { it.temperature != null }) add(stringResource(R.string.measured_by_station, station))
        if (hours.any { it.precipitation != null && it.precipitationFrom == Provenance.RADAR }) add(stringResource(R.string.measured_by_radar))
        if (hours.any { it.sunshineMinutes != null && it.sunshineFrom == Provenance.SATELLITE }) add(stringResource(R.string.measured_by_satellite))
    }
    return if (parts.isEmpty()) null else stringResource(R.string.measured_by, parts.joinToString(" · "))
}

