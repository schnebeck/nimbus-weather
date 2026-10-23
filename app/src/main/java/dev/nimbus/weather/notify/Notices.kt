/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/notify/Notices.kt
 * What the notices tell and when: weather warnings, gauges reaching an alert level, precipitation
 * coming after hours without – each once, louder only when it gets worse.
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

package dev.nimbus.weather.notify

import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.GaugeInfo
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.model.WeatherAlert
import dev.nimbus.weather.ui.main.Insights
import kotlinx.serialization.Serializable

/**
 * What has been told, kept between the checks: the warnings by id with their level, the gauges by
 * id with the highest level told, the times of the precipitation notices.
 */
@Serializable
data class NoticeMemory(
    val warned: Map<String, Int> = emptyMap(),
    val gauges: Map<String, GaugeMemory> = emptyMap(),
    val rainTold: List<Long> = emptyList(),
)

/** A gauge told of: the highest level told; since when it is below the level told of (null: not). */
@Serializable
data class GaugeMemory(val stage: Int, val belowSince: Long? = null)

/** What a check asks to show or take away. [loud]: with the sound and vibration of its channel; else quietly updated. */
sealed interface NoticeStep {
    data class Warning(val alert: WeatherAlert, val loud: Boolean) : NoticeStep
    data class WarningGone(val id: String) : NoticeStep
    /** [below]: the gauge went back under the level told of. */
    data class Gauge(val gauge: GaugeInfo, val stage: Int, val below: Boolean, val loud: Boolean) : NoticeStep
    data class GaugeGone(val uuid: String) : NoticeStep
    /** [until]: when the precipitation ends by the radar (the notice goes then). */
    data class Rain(val notice: Insights.PrecipNotice, val until: Long) : NoticeStep
}

object Notices {
    private const val HOUR_MS = 3_600_000L
    private const val STEP_MS = 5 * 60_000L

    /** The level of a weather warning: 1 weather warning, 2 marked weather, 3 severe, 4 extreme. */
    fun level(a: WeatherAlert): Int = a.severity.ordinal + 1

    /** A states' flood warning ([WeatherAlert.source] "LHP"), else the DWD's. */
    fun isFlood(a: WeatherAlert): Boolean = a.source == "LHP"

    /**
     * The warnings from [minLevel] on ([floods]: the states' flood warnings too, whatever their
     * level): a new one is told once, the same one again only when its level rises; one no longer
     * there (over, lifted, or below the level now) is taken away. Returns the steps and the memory.
     */
    fun warnings(alerts: List<WeatherAlert>, minLevel: Int, floods: Boolean, told: Map<String, Int>): Pair<List<NoticeStep>, Map<String, Int>> {
        val shown = alerts.filter { if (isFlood(it)) floods else level(it) >= minLevel }
        val steps = mutableListOf<NoticeStep>()
        val now = mutableMapOf<String, Int>()
        for (a in shown) {
            val before = told[a.id]
            if (before == null || level(a) > before) steps += NoticeStep.Warning(a, loud = true)
            now[a.id] = maxOf(level(a), before ?: 0)
        }
        for (id in told.keys - now.keys) steps += NoticeStep.WarningGone(id)
        return steps to now
    }

    /** A gauge's alert level now: the state's level, else the flood portal's class; 0 none. */
    fun stage(g: GaugeInfo): Int = maxOf(g.alertStage ?: 0, (g.lhpClass ?: 0).coerceAtLeast(0))

    /** How long a gauge stays below its level before its notice goes – and a rise to the same level again is no news. */
    const val GAUGE_CALM_MS = 6 * HOUR_MS

    /**
     * The [gauges] reaching [minStage] or more: told once per level, louder at a higher one; back
     * below, quietly updated, and taken away after [GAUGE_CALM_MS] below – rising again within that
     * time to a level told already is no news. A gauge not in [gauges] (its source did not answer)
     * keeps what was told.
     */
    fun gauges(gauges: List<GaugeInfo>, minStage: Int, told: Map<String, GaugeMemory>, now: Long): Pair<List<NoticeStep>, Map<String, GaugeMemory>> {
        val steps = mutableListOf<NoticeStep>()
        val memory = told.toMutableMap()
        for (g in gauges.distinctBy { it.uuid }) {
            val s = stage(g)
            val m = memory[g.uuid]
            if (s >= minStage) {
                when {
                    m == null || s > m.stage -> { steps += NoticeStep.Gauge(g, s, below = false, loud = true); memory[g.uuid] = GaugeMemory(s) }
                    m.belowSince != null -> { steps += NoticeStep.Gauge(g, s, below = false, loud = false); memory[g.uuid] = m.copy(belowSince = null) }
                }
            } else if (m != null) {
                when {
                    m.belowSince == null -> { steps += NoticeStep.Gauge(g, m.stage, below = true, loud = false); memory[g.uuid] = m.copy(belowSince = now) }
                    now - m.belowSince >= GAUGE_CALM_MS -> { steps += NoticeStep.GaugeGone(g.uuid); memory.remove(g.uuid) }
                }
            }
        }
        return steps to memory
    }

    /** Hours without precipitation over the place before precipitation coming is news. */
    const val DRY_MS = 3 * HOUR_MS
    /** How far ahead precipitation is told of: the radar's nowcast, sure enough. */
    const val RAIN_AHEAD_MS = HOUR_MS
    /** At most this many precipitation notices a day. */
    const val RAIN_PER_DAY = 2

    /**
     * Precipitation coming after [DRY_MS] without – the change of the weather, not the weather: a
     * rainy day with breaks tells nothing, a dry afternoon with a shower in the evening tells once.
     *
     * - [steps]: the radar's measured 5-minute steps over the place (mm by their ends) of the last
     *   [DRY_MS]; none of them precipitation ([Insights.WET_RATE_MM_H]), and most of them there –
     *   missing steps are no proof of dry weather;
     * - not falling now ([current]); no precipitation notice within [DRY_MS], and fewer than
     *   [RAIN_PER_DAY] since [dayStart] ([told]);
     * - the radar's quarter hours ([minutely], [Insights.wet] – not only perhaps some drizzle) bring
     *   precipitation within [RAIN_AHEAD_MS];
     * - [quiet]: the phone is set to do not disturb (or its bedtime): nothing – by the time it is
     *   read, it is over.
     */
    fun rain(
        minutely: List<MinutelyPoint>, hourly: List<HourlyPoint>, current: Condition, steps: Map<Long, Double>,
        told: List<Long>, now: Long, dayStart: Long, quiet: Boolean,
    ): NoticeStep.Rain? {
        if (quiet || current.isPrecipitation) return null
        val recent = steps.filterKeys { it in now - DRY_MS..now }
        if (recent.size < (DRY_MS / STEP_MS) * 3 / 4) return null
        if (recent.values.any { it * (HOUR_MS / STEP_MS) >= Insights.WET_RATE_MM_H }) return null
        if (told.any { it > now - DRY_MS } || told.count { it >= dayStart } >= RAIN_PER_DAY) return null
        val notice = Insights.precipNotice(minutely, hourly, current, now, rainingNow = false, horizonMs = RAIN_AHEAD_MS) ?: return null
        if (notice.possible || notice.state !is Insights.Nowcast.StartsIn) return null
        // its end: the first dry quarter hour after the start, else an hour on
        val start = notice.at ?: now
        val end = Insights.nowcastPoints(minutely, now).firstOrNull { it.time > start && !Insights.wet(it) }?.time ?: (start + HOUR_MS)
        return NoticeStep.Rain(notice, end)
    }
}
