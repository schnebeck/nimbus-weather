/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/DayWeatherText.kt
 * A day's weather as one line: its phrase put into words.
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.DayWeather
import dev.nimbus.weather.data.model.DayWeather.Family
import dev.nimbus.weather.data.model.DayWeather.Kind
import dev.nimbus.weather.data.model.DayWeather.Form
import dev.nimbus.weather.data.model.DayWeather.Phrase
import dev.nimbus.weather.data.model.DayWeather.Spell
import dev.nimbus.weather.data.model.DayWeather.Term
import java.util.Locale

/**
 * "Sunny", "Mostly cloudy with showers", "Rain in the morning, then bright": the word lists follow
 * [Kind] and [Term] in their order; a stretch's weather is told by its noun where something falls
 * or the fog lies ("in the morning rain"), by its adjective where it is the sky or the rest of
 * the day ("otherwise rainy"); a thunderstorm with what it brings ("thundery with rain");
 * "mostly" where the sky changes over the day, the other sky left unsaid.
 */
@Composable
fun dayWeatherText(
    weather: DayWeather,
    /** A sentence of its own (its first letter capital); false: within one, the words as they are. */
    sentence: Boolean = true,
): String {
    val adjective = stringArrayResource(R.array.day_kind_adjective)
    val noun = stringArrayResource(R.array.day_kind_noun)
    val some = stringArrayResource(R.array.day_kind_some)
    val terms = stringArrayResource(R.array.day_term)
    val mostly = stringResource(R.string.day_mostly)
    val with = stringResource(R.string.day_with)
    fun sky(k: Kind, most: Boolean) = if (most) mostly.format(adjective[k.ordinal]) else adjective[k.ordinal]
    fun told(s: Spell, otherwise: Boolean) = when {
        s.with != null -> with.format(adjective[s.kind.ordinal], noun[s.with.ordinal])
        otherwise || s.kind.family == Family.FAIR || s.kind.family == Family.DULL -> adjective[s.kind.ordinal]
        else -> noun[s.kind.ordinal]
    }
    val text = when (val p = weather.phrase) {
        is Phrase.Whole -> sky(p.kind, p.mostly)
        is Phrase.With -> with.format(sky(p.main, p.mostly), some[p.some.ordinal])
        is Phrase.Apart -> stringResource(
            when (p.form) {
                Form.FIRST -> R.string.day_first
                Form.LATER -> R.string.day_later
                Form.AFTERNOON -> R.string.day_afternoon
                Form.OVERNIGHT -> R.string.day_overnight
                Form.OTHERWISE -> R.string.day_otherwise
            },
            terms[p.term.ordinal], told(p.spell, p.form == Form.OTHERWISE), adjective[p.rest.ordinal],
        )
    }
    return if (sentence) text.replaceFirstChar { it.titlecase(Locale.getDefault()) } else text
}
