/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/theme/Contrast.kt
 * Text contrast on the animated sky: how dark cards and header must be, computed, not guessed.
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

package dev.nimbus.weather.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * WCAG 2 contrast on composited colours. The sky behind the cards changes with the weather
 * (white clouds, the sun, fog): the glass of the cards and the shade behind the header are made
 * just as dark as the brightest thing that can be behind them requires – see [Legibility].
 */
object Contrast {
    /** Relative luminance (WCAG) of an opaque sRGB colour. */
    fun luminance(c: Color): Double {
        fun ch(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    /** [top] (with its alpha) painted over the opaque [bottom], as the screen blends it. */
    fun over(top: Color, bottom: Color): Color {
        val a = top.alpha
        return Color(
            top.red * a + bottom.red * (1 - a), top.green * a + bottom.green * (1 - a), top.blue * a + bottom.blue * (1 - a), 1f,
        )
    }

    /** Contrast ratio of two opaque colours, 1 … 21. */
    fun ratio(a: Color, b: Color): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Contrast of [text] (with its alpha) on [background] (opaque). */
    fun textRatio(text: Color, background: Color): Double = ratio(over(text, background), background)

    /**
     * The least opacity of [shade] (its own alpha ignored), at least [min], that gives every text
     * colour of [needs] its ratio on [shade] over [behind]. 1 if even that is not enough.
     */
    fun requiredAlpha(shade: Color, behind: Color, needs: List<Pair<Color, Double>>, min: Float = 0f): Float {
        var a = min
        while (a < 1f) {
            val bg = over(shade.copy(alpha = a), behind)
            if (needs.all { (text, need) -> textRatio(text, bg) >= need }) return a
            a += 0.01f
        }
        return 1f
    }
}

/** What has to stay readable, and how well (WCAG: 4.5 for text, 3 for large text and labels). */
object Legibility {
    /** Text in the glass cards: values and captions 4.5:1, the small grey labels and axes 3:1. */
    val onCards = listOf(Color.White to 4.5, NimbusColors.Secondary to 4.5, NimbusColors.Tertiary to 3.0)

    /** The header straight on the sky: name, condition, station line 4.5:1; the large temperature is white. */
    val header = listOf(Color.White to 4.5, Color(0xE6FFFFFF) to 4.5, NimbusColors.Secondary to 4.5)

    /** The glass of the cards: dark blue; at least this opaque, more where the sky is bright. */
    val CardGlass = Color(0xFF0A1A33)
    const val CARD_MIN_ALPHA = 0.18f

    /** Card glass for a sky whose brightest part is [behind]. */
    fun cardFill(behind: Color): Color =
        CardGlass.copy(alpha = Contrast.requiredAlpha(CardGlass, behind, onCards, CARD_MIN_ALPHA))

    /** Shade behind the header (black, this opaque) for a sky whose brightest part is [behind]. */
    fun headerShade(behind: Color): Float = Contrast.requiredAlpha(Color.Black, behind, header)
}
