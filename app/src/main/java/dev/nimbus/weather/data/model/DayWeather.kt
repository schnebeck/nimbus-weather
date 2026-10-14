/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/model/DayWeather.kt
 * A day's weather in one line and one symbol, from its hours: what stands out, when, and the sky around it.
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

package dev.nimbus.weather.data.model

import java.time.Instant
import java.time.ZoneId

/**
 * A day's weather from its hours, told the way one tells it: one word where the day is all alike,
 * "mostly cloudy with showers" where something comes now and then, "rain in the morning, then
 * bright" where it keeps to a stretch of the day – named by the shortest [Term] that holds it –
 * and one symbol of all that matters (sky, precipitation, thunder, fog). The model's daily code
 * names the day's worst weather only: drizzle before dawn made a sunny day "drizzle".
 */
data class DayWeather(val phrase: Phrase, val symbol: DaySymbol) {

    /**
     * Named stretches of the day by the hours' starts, [from] until [to] (past midnight where [to]
     * is the smaller). A [span] says the weather filled it ("until noon"), the others that it came
     * within it ("in the afternoon").
     */
    enum class Term(val from: Int, val to: Int, val span: Boolean = false) {
        SMALL_HOURS(0, 3), EARLY(0, 5), MORNING(5, 9), EARLY_FORENOON(8, 10), FORENOON(8, 12), LATE_FORENOON(10, 12),
        NOON(12, 13), AFTERNOON(12, 18), LATE_AFTERNOON(16, 18), EARLY_EVENING(18, 20), EVENING(18, 21),
        LATE_EVENING(20, 22), NIGHT(21, 24), OVERNIGHT(21, 5), DAYTIME(8, 18, true), UNTIL_NOON(0, 12, true),
        FROM_NOON(12, 24, true);

        val hours: Set<Int> = if (from < to) (from until to).toSet() else ((from until 24) + (0 until to)).toSet()
    }

    /** How a stretch and the rest of the day go together. */
    enum class Form {
        /** "Rain in the morning, then bright" */
        FIRST,
        /** "Sunny, rain in the evening" */
        LATER,
        /** "Sunny by day, thundery with rain in the afternoon" */
        AFTERNOON,
        /** "Thunderstorms overnight, sunny by day" */
        OVERNIGHT,
        /** The dry stretch named: "sunny by day, rain otherwise" */
        OTHERWISE,
    }

    enum class Kind(val family: Family) {
        SUNNY(Family.FAIR), BRIGHT(Family.FAIR), CLOUDY(Family.DULL), OVERCAST(Family.DULL), FOG(Family.FOG),
        DRIZZLE(Family.WET), RAIN(Family.WET), STEADY_RAIN(Family.WET), SHOWERS(Family.WET),
        THUNDER(Family.THUNDER), SNOW(Family.WHITE), SLEET(Family.WHITE), FREEZING(Family.WHITE),
    }

    /** Kinds alike enough to be told as one: a day sunny, then bright is a fair day. */
    enum class Family { FAIR, DULL, FOG, WET, WHITE, THUNDER }

    /** A stretch's weather; [with]: what a thunderstorm brings ("thundery with rain"). */
    data class Spell(val kind: Kind, val with: Kind? = null)

    /** How the day is told (the words come from the resources). */
    sealed interface Phrase {
        /** "Sunny"; [mostly]: "mostly sunny", the sky not the same all day. */
        data class Whole(val kind: Kind, val mostly: Boolean = false) : Phrase
        /** "Sunny with some rain": [main] prevails ([mostly] as in [Whole]), [some] comes now and then. */
        data class With(val main: Kind, val some: Kind, val mostly: Boolean = false) : Phrase
        /** The [spell] in its [term], the [rest] of the day's sky, put together by [form]. */
        data class Apart(val form: Form, val term: Term, val spell: Spell, val rest: Kind) : Phrase
    }

    companion object {
        private const val WET_MM = 0.1
        /** Less in all is nothing to tell of. */
        private const val SOME_MM = 0.2
        /** Rain this heavy (mm per wet hour) over most of the hours is steady rain. */
        private const val STEADY_MM_H = 1.0
        /** This much sunshine on a day of rain or snow is worth telling. */
        private const val SUN_GLIMPSE_MIN = 90.0
        /** From this day's amount on the symbol shows more drops, from [MUCH_MM] the most. */
        private const val MORE_MM = 2.0
        private const val MUCH_MM = 10.0
        /** Less per wet hour is drizzle. */
        private const val DRIZZLE_MM_H = 0.3
        /** The share of a stretch a term has to hold (and a span to be filled with). */
        private const val HOLDS = 0.7
        /** What fills this share of the day is the day's weather. */
        private const val WHOLE = 0.75

        /** Fewer hours tell too little of a day. */
        private const val ENOUGH_HOURS = 12

        /** [hours]: the day's hours (stamped at their end, after its midnight up to the next) in [zone]; null: too few. */
        fun of(hours: List<HourlyPoint>, zone: ZoneId): DayWeather? {
            if (hours.size < ENOUGH_HOURS) return null
            val start = hours.associateWith { Instant.ofEpochMilli(it.time - 3_600_000L).atZone(zone).hour }
            return DayWeather(phrase(hours) { start.getValue(it) }, symbol(hours))
        }

        /** Minutes of sunshine of an hour; without them, as many as the sky is clear of cloud. */
        private fun sun(h: HourlyPoint) = h.sunshine ?: h.cloudCover?.let { 60.0 * (1 - it / 100.0) }

        private fun wet(hours: List<HourlyPoint>) = hours.filter { (it.precipitation ?: 0.0) >= WET_MM }

        /** What stands out, by its hours: thunder, else precipitation, else fog; none: empty. */
        private fun event(hours: List<HourlyPoint>): List<HourlyPoint> {
            hours.filter { it.condition == Condition.THUNDERSTORM }.let { if (it.isNotEmpty()) return it }
            wet(hours).let { if (it.sumOf { h -> h.precipitation ?: 0.0 } >= SOME_MM) return it }
            hours.filter { it.condition == Condition.FOG }.let { if (it.size >= 2) return it }
            return emptyList()
        }

        /** The character of [hours]; null where nothing falls and no daylight tells the sky. */
        internal fun kind(hours: List<HourlyPoint>): Kind? {
            if (hours.isEmpty()) return null
            if (hours.any { it.condition == Condition.THUNDERSTORM }) return Kind.THUNDER
            val wet = wet(hours)
            val wetSum = wet.sumOf { it.precipitation ?: 0.0 }
            if (wet.isNotEmpty() && wetSum >= SOME_MM) {
                return fall(wet) ?: when {
                    wetSum / wet.size < DRIZZLE_MM_H -> Kind.DRIZZLE
                    wet.size * 2 < hours.size || wet.count { it.condition == Condition.SHOWERS } * 2 > wet.size -> Kind.SHOWERS
                    wet.size * 4 >= hours.size * 3 && wetSum / wet.size >= STEADY_MM_H -> Kind.STEADY_RAIN
                    else -> Kind.RAIN
                }
            }
            if (hours.count { it.condition == Condition.FOG } * 2 > hours.size) return Kind.FOG
            return sky(hours)
        }

        /** The sky by the share of possible sunshine in the daylight hours. */
        private fun sky(hours: List<HourlyPoint>): Kind? {
            val sun = hours.filter { it.isDay }.mapNotNull { sun(it) }
            if (sun.isEmpty()) return null
            val share = sun.sum() / (60.0 * sun.size)
            return when {
                share >= 0.75 -> Kind.SUNNY
                share >= 0.5 -> Kind.BRIGHT
                share >= 0.2 -> Kind.CLOUDY
                else -> Kind.OVERCAST
            }
        }

        /** Snow, sleet or freezing rain where most of the wet hours bring them; null: rain. */
        private fun fall(wet: List<HourlyPoint>): Kind? {
            fun most(vararg c: Condition) = wet.count { it.condition in c } * 2 > wet.size
            return when {
                most(Condition.SNOW, Condition.HEAVY_SNOW) -> Kind.SNOW
                most(Condition.SLEET) -> Kind.SLEET
                most(Condition.FREEZING_RAIN) -> Kind.FREEZING
                else -> null
            }
        }

        private fun spell(hours: List<HourlyPoint>): Spell {
            val kind = kind(hours) ?: Kind.OVERCAST
            val wet = wet(hours)
            val brings = kind == Kind.THUNDER && wet.sumOf { it.precipitation ?: 0.0 } >= SOME_MM
            return Spell(kind, if (brings) fall(wet) ?: Kind.RAIN else null)
        }

        /**
         * The night's hours count half: little of the day is spent seeing them. What stands out over
         * most of the day is the day's word; kept to a stretch, the stretch is named – or, where it
         * is the dry daylight between wet ends, that one; else it comes now and then.
         */
        internal fun phrase(hours: List<HourlyPoint>, hourOf: (HourlyPoint) -> Int): Phrase {
            val light = hours.filter { it.isDay }
            val sky = sky(light) ?: Kind.OVERCAST
            val event = event(hours).toSet()
            if (event.isEmpty()) return Phrase.Whole(sky, mostly(sky, light))
            fun weight(s: Set<Int>) = s.sumOf { if (it < 5 || it >= 21) 0.5 else 1.0 }
            val at = event.map(hourOf).toSet()
            val all = hours.map(hourOf).toSet()
            if (weight(at) >= WHOLE * weight(all) || light.isNotEmpty() && light.count { it in event } >= WHOLE * light.size) {
                val kind = kind(hours) ?: sky
                val falls = kind.family == Family.WET || kind.family == Family.WHITE
                val sun = light.sumOf { sun(it) ?: 0.0 }
                return if (falls && sun >= SUN_GLIMPSE_MIN) Phrase.With(kind, Kind.BRIGHT) else Phrase.Whole(kind, mostly(kind, light))
            }
            // the shortest term that holds the stretch; a span also filled by it, as a whole or in its daylight
            val daylight = light.map(hourOf).toSet()
            fun filled(stretch: Set<Int>, t: Term): Boolean {
                val lit = t.hours intersect daylight
                return weight(stretch intersect t.hours) >= HOLDS * weight(t.hours intersect all) ||
                    lit.isNotEmpty() && (stretch intersect lit).size >= HOLDS * lit.size
            }
            fun term(stretch: Set<Int>) = Term.entries.filter { t ->
                weight(stretch intersect t.hours) >= HOLDS * weight(stretch) && (!t.span || filled(stretch, t))
            }.minWithOrNull(compareBy<Term> { it.hours.size }.thenByDescending { weight(stretch intersect it.hours) })
            val rest = sky(light.filter { it !in event }) ?: sky
            term(at)?.let { t ->
                val clearBefore = light.count { it !in event && hourOf(it) < at.min() }
                val form = when {
                    t == Term.OVERNIGHT -> Form.OVERNIGHT
                    t.from < t.to && t.to <= 12 && clearBefore <= 1 -> Form.FIRST
                    t.from >= 12 && t.to <= 18 -> Form.AFTERNOON
                    else -> Form.LATER
                }
                return Phrase.Apart(form, t, spell(event.toList()), rest)
            }
            // the dry daylight between wet ends
            val dry = light.filter { it !in event }.map(hourOf).toSet()
            if (dry.isNotEmpty() && dry.max() - dry.min() + 1 == dry.size && at.any { it < dry.min() } && at.any { it > dry.max() }) {
                term(dry)?.let { return Phrase.Apart(Form.OTHERWISE, it, spell(event.toList()), rest) }
            }
            val some = kind(event.toList()) ?: Kind.SHOWERS
            return Phrase.With(sky, some, mostly(sky, light))
        }

        /** A sky of [kind] over the day while a quarter of its daylight hours or more are another sky. */
        private fun mostly(kind: Kind, light: List<HourlyPoint>): Boolean {
            if (kind.family != Family.FAIR && kind.family != Family.DULL) return false
            val skies = light.mapNotNull { sky(listOf(it)) }
            return skies.isNotEmpty() && skies.count { it != kind } * 4 >= skies.size
        }

        /**
         * The symbol: the daylight sky, the day's precipitation, thunder, fog. The drops count the
         * day's amount – not the hours it is spread over, nor its chance: a day sure to bring a
         * millimetre is a day of little rain.
         */
        internal fun symbol(hours: List<HourlyPoint>): DaySymbol {
            val light = hours.filter { it.isDay }
            val sun = light.mapNotNull { sun(it) }
            val share = if (sun.isEmpty()) 0.0 else sun.sum() / (60.0 * sun.size)
            val sky = when {
                share >= 0.75 -> DaySymbol.Sky.SUNNY
                share >= 0.5 -> DaySymbol.Sky.BRIGHT
                share >= 0.2 || sun.sum() >= SUN_GLIMPSE_MIN -> DaySymbol.Sky.CLOUDY
                else -> DaySymbol.Sky.OVERCAST
            }
            val wet = wet(hours)
            val wetSum = wet.sumOf { it.precipitation ?: 0.0 }
            val fall = if (wetSum < SOME_MM) null else when (fall(wet)) {
                Kind.SNOW -> DaySymbol.Fall.SNOW
                Kind.SLEET -> DaySymbol.Fall.SLEET
                Kind.FREEZING -> DaySymbol.Fall.FREEZING
                else -> if (wetSum / wet.size < DRIZZLE_MM_H) DaySymbol.Fall.DRIZZLE else DaySymbol.Fall.RAIN
            }
            val amount = when {
                fall == null -> 0
                wetSum >= MUCH_MM -> 3
                wetSum >= MORE_MM -> 2
                else -> 1
            }
            val thunder = hours.any { it.condition == Condition.THUNDERSTORM }
            val fog = light.count { it.condition == Condition.FOG } >= 3 || hours.count { it.condition == Condition.FOG } * 2 > hours.size
            return DaySymbol(sky, fall, amount, thunder, fog && !thunder)
        }
    }
}

/** What the day's symbol shows: the daytime [sky], what fell ([amount] 1 a little … 3 much), thunder, fog. */
data class DaySymbol(val sky: Sky, val fall: Fall?, val amount: Int, val thunder: Boolean, val fog: Boolean) {
    enum class Sky { SUNNY, BRIGHT, CLOUDY, OVERCAST }
    enum class Fall { DRIZZLE, RAIN, SNOW, SLEET, FREEZING }
}
