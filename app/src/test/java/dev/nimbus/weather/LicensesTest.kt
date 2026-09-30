/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/LicensesTest.kt
 * Tests for reflowing license texts on the licenses screen.
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

import dev.nimbus.weather.ui.settings.reflow
import org.junit.Assert.assertEquals
import org.junit.Test

class LicensesTest {
    @Test fun joinsWrappedLinesButKeepsParagraphs() {
        val text = "Redistribution and use are\npermitted provided that:\n\n   1. Redistributions of source code must\n      retain the notice.\r\n"
        assertEquals(
            "Redistribution and use are permitted provided that:\n\n1. Redistributions of source code must retain the notice.",
            reflow(text),
        )
    }

    @Test fun removesHtmlLineBreaks() {
        assertEquals("a b", reflow("a<br />\nb"))
    }
}
