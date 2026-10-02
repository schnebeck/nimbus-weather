/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/ContrastTest.kt
 * Text stays readable on every sky: the cards' glass and the header shade against the brightest
 * thing that can be behind them (white clouds, the sun, fog), in every weather, day and night.
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

package dev.nimbus.weather

import androidx.compose.ui.graphics.Color
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.ui.background.SkyScene
import dev.nimbus.weather.ui.theme.Contrast
import dev.nimbus.weather.ui.theme.Legibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    /** Every weather at night, at dawn and dusk (glow), and by day. */
    private val skies = Condition.entries.flatMap { c ->
        listOf(SkyScene(c, 0f, 0f, 0f), SkyScene(c, 0.5f, 1f, 0f), SkyScene(c, 0.8f, 0.6f, 0f), SkyScene(c, 1f, 0f, 0f))
    }

    private fun failures(background: (SkyScene) -> Color, needs: List<Pair<Color, Double>>) = skies.flatMap { sky ->
        val bg = background(sky)
        needs.mapNotNull { (text, need) ->
            val r = Contrast.textRatio(text, bg)
            if (r < need) "${sky.condition} daylight ${sky.daylight} twilight ${sky.twilight}: text ${text.value.toString(16)} ${"%.2f".format(r)} < $need" else null
        }
    }

    @Test fun cardTextReadableOnEverySky() {
        // the card's glass over the brightest thing behind it
        val f = failures({ Contrast.over(it.cardFill, it.brightestBehind) }, Legibility.onCards)
        assertTrue(f.joinToString("\n"), f.isEmpty())
    }

    @Test fun headerTextReadableOnEverySky() {
        val f = failures({ Contrast.over(Color.Black.copy(alpha = it.headerShade), it.brightestBehindHeader) }, Legibility.header)
        assertTrue(f.joinToString("\n"), f.isEmpty())
    }

    @Test fun whiteCloudsAreTheWorstCaseOfAFairDay() {
        // the case of the screenshot: partly cloudy by day – white clouds behind the cards
        val sky = SkyScene(Condition.PARTLY_CLOUDY, 1f, 0f, 0f)
        assertEquals(1.0, Contrast.luminance(sky.brightestBehind), 0.01)
    }

    @Test fun theSunCountsForTheHeaderOnly() {
        val clear = SkyScene(Condition.CLEAR, 1f, 0f, 0f)
        assertTrue(Contrast.luminance(clear.brightestBehindHeader) > Contrast.luminance(clear.brightestBehind))
    }

    @Test fun darkSkiesKeepTheLightGlass() {
        // at night nothing needs a darker card or a header shade
        val night = SkyScene(Condition.CLEAR, 0f, 0f, 0f)
        assertEquals(Legibility.CARD_MIN_ALPHA, night.cardFill.alpha, 0.011f)
        assertEquals(0f, night.headerShade, 0.011f)
    }

    @Test fun contrastFormulaMatchesWcag() {
        assertEquals(21.0, Contrast.ratio(Color.White, Color.Black), 0.01)
        assertEquals(1.0, Contrast.ratio(Color.White, Color.White), 0.01)
        // 50 % white on black is mid grey (#808080: 5.32:1 against black)
        assertEquals(5.32, Contrast.textRatio(Color.White.copy(alpha = 0.5f), Color.Black), 0.05)
    }
}
