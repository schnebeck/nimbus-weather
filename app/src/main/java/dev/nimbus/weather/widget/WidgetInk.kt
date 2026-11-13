/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/widget/WidgetInk.kt
 * The widget's text dark or light: by what lies behind it, the background over the wallpaper.
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

package dev.nimbus.weather.widget

import kotlin.math.pow

/**
 * The colours of the widget's writing: [text], its dimmer second line [dim] (week, alarm, the
 * time of the data) and the [chance] of precipitation. All colours are opaque ARGB.
 */
data class WidgetInk(val text: Int, val dim: Int, val chance: Int) {
    companion object {
        /** Dark writing, for a light background (the widget's own day colours). */
        val ON_LIGHT = WidgetInk(0xFF1B1B1F.toInt(), 0xFF5E5D66.toInt(), 0xFF2F6FD0.toInt())

        /** Light writing, for a dark background (the widget's own night colours). */
        val ON_DARK = WidgetInk(0xFFE6E4EB.toInt(), 0xFFB1AFB9.toInt(), 0xFF8FD3FF.toInt())

        /** The contrast writing needs to read well (and black or white reaches on any colour). */
        private const val TEXT_CONTRAST = 4.5

        /** Below this contrast the dimmer lines and the chance's blue are hard to read: they are written as the text then. */
        private const val SECOND_CONTRAST = 3.0

        /**
         * The writing for [behind]: dark or light, whichever stands out more; the dimmer lines and
         * the chance in their own colours where those still read on it. On a middle colour (a
         * strong blue, a teal) neither ink reaches [TEXT_CONTRAST]: then all of it in black or white.
         */
        fun of(behind: Int): WidgetInk {
            val ink = if (lightBehind(behind)) ON_LIGHT else ON_DARK
            if (contrast(ink.text, behind) < TEXT_CONTRAST) {
                val pure = if (contrast(BLACK, behind) >= contrast(WHITE, behind)) BLACK else WHITE
                return WidgetInk(pure, pure, pure)
            }
            fun second(c: Int) = if (contrast(c, behind) >= SECOND_CONTRAST) c else ink.text
            return WidgetInk(ink.text, second(ink.dim), second(ink.chance))
        }

        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()

        /** Whether dark writing stands out more on [behind] than light writing. */
        fun lightBehind(behind: Int): Boolean = contrast(ON_LIGHT.text, behind) >= contrast(ON_DARK.text, behind)

        /**
         * What lies behind the writing: the [background] colour at [opacity] over the [wallpaper];
         * the wallpaper unknown (null): the background alone.
         */
        fun behind(background: Int, opacity: Float, wallpaper: Int?): Int =
            if (wallpaper == null) background or OPAQUE else over(background, opacity.coerceIn(0f, 1f), wallpaper)

        /** [top] at [alpha] over [bottom], channel by channel. */
        fun over(top: Int, alpha: Float, bottom: Int): Int {
            fun ch(c: Int, s: Int) = (c ushr s) and 0xFF
            fun mix(s: Int) = (ch(top, s) * alpha + ch(bottom, s) * (1 - alpha) + 0.5f).toInt().coerceIn(0, 255)
            return OPAQUE or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }

        /** The relative luminance of [c] (0 black … 1 white), as for contrast ratios. */
        fun luminance(c: Int): Double {
            fun lin(v: Int): Double = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
            return 0.2126 * lin((c ushr 16) and 0xFF) + 0.7152 * lin((c ushr 8) and 0xFF) + 0.0722 * lin(c and 0xFF)
        }

        /** The contrast ratio of [a] and [b] (1 … 21). */
        fun contrast(a: Int, b: Int): Double {
            val la = luminance(a); val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        const val OPAQUE = 0xFF shl 24
    }
}
