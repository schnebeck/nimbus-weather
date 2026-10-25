/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/PageJump.kt
 * A place on the weather page a widget part leads to: the hours, today opened, the pollen, the head.
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

import dev.nimbus.weather.data.model.WeatherCard

/** Where on the weather page a tap on a widget part leads. */
enum class PageSection(
    /** The card shown there; null: the page's head. */
    val card: WeatherCard?,
    /** The key of the card's item on the page. */
    val key: String?,
) {
    HEAD(null, null),
    /** The temperature: the hours ahead. */
    HOURLY(WeatherCard.HOURLY, "hourly"),
    /** The precipitation: the days with today opened. */
    TODAY(WeatherCard.DAILY, "daily"),
    POLLEN(WeatherCard.POLLEN, "pollen"),
    ;

    companion object {
        fun of(name: String?): PageSection? = entries.firstOrNull { it.name == name }
    }
}

/**
 * The page of [placeId] is to be shown at [section] – once, then [MainViewModel][dev.nimbus.weather.ui.MainViewModel]
 * forgets it; [at] tells two taps on the same part apart.
 */
data class PageJump(val placeId: String, val section: PageSection, val at: Long = System.nanoTime())
