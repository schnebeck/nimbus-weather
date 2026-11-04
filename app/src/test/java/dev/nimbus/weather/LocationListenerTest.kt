/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/LocationListenerTest.kt
 * Every location listener complete: up to Android 10 the system calls all four of its methods.
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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocationListenerTest {
    /**
     * A LocationListener written as a lambda has only onLocationChanged; up to Android 10 the
     * other three have no body of their own, and a provider's status changing ends the app. So
     * every one is an object with all four.
     */
    @Test fun everyLocationListenerHasAllFourMethods() {
        val src = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        val lambdas = src.filter { Regex("""[=(,]\s*(android\.location\.)?LocationListener\s*\{""").containsMatchIn(it.readText()) }.map { it.name }
        assertTrue("written as a lambda: $lambdas", lambdas.isEmpty())
        for (f in src) {
            val text = f.readText()
            Regex("""object\s*:\s*(android\.location\.)?LocationListener\s*\{""").findAll(text).forEach { m ->
                val body = text.substring(m.range.last, (m.range.last + 1500).coerceAtMost(text.length))
                for (method in listOf("onLocationChanged", "onProviderEnabled", "onProviderDisabled", "onStatusChanged")) {
                    assertTrue("${f.name}: $method", "override fun $method(" in body)
                }
            }
        }
    }
}
