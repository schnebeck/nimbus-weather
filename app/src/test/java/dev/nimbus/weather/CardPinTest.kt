/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/CardPinTest.kt
 * Cards sliding under the header: the title stays at the line until the card is used up.
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

import dev.nimbus.weather.ui.components.cardOverlap
import org.junit.Assert.assertEquals
import org.junit.Test

class CardPinTest {
    // the line at 300 px, a card of 500 px with a 100 px title block
    @Test fun belowTheLineNothingHappens() = assertEquals(0f, cardOverlap(300f, 320f, 500f, 100f), 0f)

    @Test fun slidingUnderTheTitleStaysAtTheLine() {
        // card top 200 px: 100 px under the line – the title moves down by 100, so it stays at 300
        assertEquals(100f, cardOverlap(300f, 200f, 500f, 100f), 0f)
    }

    @Test fun whenOnlyTheTitleIsLeftItGoesWithTheCard() {
        // card top −250: 550 under the line, but only 400 can be used up – the title leaves with the bottom
        assertEquals(400f, cardOverlap(300f, -250f, 500f, 100f), 0f)
    }
}
