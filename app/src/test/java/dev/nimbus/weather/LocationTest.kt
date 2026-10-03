/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/LocationTest.kt
 * "My location" follows the phone: an older position is not taken for the current one, the GPS
 * joins when the network gives nothing, and a page whose position is not current says so.
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

import dev.nimbus.weather.data.model.DataPart
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.ui.Screen
import dev.nimbus.weather.ui.UiState
import dev.nimbus.weather.ui.locationWanted
import dev.nimbus.weather.data.repo.Freshness
import dev.nimbus.weather.data.repo.Locate
import dev.nimbus.weather.ui.components.CardStatus
import dev.nimbus.weather.ui.main.LocationMark
import dev.nimbus.weather.ui.main.cardStatus
import dev.nimbus.weather.ui.main.pageStale
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTest {
    private val min = 60_000L
    private val now = 1_791_000_000_000L
    private val hannover = Locate.Found("Hannover", now - 20 * min)

    /** On an EDGE network the cell lookup never answers. */
    private val edge: suspend () -> String? = { awaitCancellation() }

    /**
     * The drive from Hannover to Bad Harzburg: the system still knows Hannover from 20 minutes
     * ago, the network stays silent – the GPS finds Bad Harzburg after 25 s (no help from the net).
     */
    @Test fun theOldPlaceIsNotTakenForTheCurrentOne() = runTest {
        val found = Locate.best(hannover, now, listOf(edge), gps = { delay(25_000); "Bad Harzburg" }) { now + currentTime }
        assertEquals("Bad Harzburg", found?.value)
        assertEquals(now + 30_000, found?.at)          // the GPS joined after 5 s
    }

    @Test fun theGpsJoinsAtOnceWithoutNetworkLocation() = runTest {
        val found = Locate.best(null, now, listOf(suspend { null }), gps = { delay(10_000); "Goslar" }) { now + currentTime }
        assertEquals(now + 10_000, found?.at)
    }

    /** A GPS request ends without a position (the platform gives up after 30 s): it is asked again. */
    @Test fun theGpsIsAskedAgain() = runTest {
        var asked = 0
        val found = Locate.best(hannover, now, emptyList(), gps = { asked++; delay(20_000); if (asked < 2) null else "Bad Harzburg" }) { now + currentTime }
        assertEquals("Bad Harzburg", found?.value)
    }

    @Test fun aRecentPositionIsTakenAsItIs() = runTest {
        val recent = Locate.Found("Hannover", now - 1 * min)
        assertEquals(recent, Locate.best(recent, now, listOf(edge), gps = { "never asked" }) { now + currentTime })
        assertEquals(0L, currentTime)
    }

    /** Nothing new at all (in a cellar): the old position comes back with its old time – shown as not current. */
    @Test fun withoutAnyPositionTheOldOneStaysOld() = runTest {
        val found = Locate.best(hannover, now, listOf(edge), gps = { awaitCancellation() }) { now + currentTime }
        assertEquals(hannover, found)
        assertEquals(Locate.TIMEOUT_MS, currentTime)
        assertFalse(Freshness.locationCurrent(found!!.at, now + currentTime))
    }

    @Test fun thePositionExpires() {
        assertTrue(Freshness.locationCurrent(now - 4 * min, now))
        assertFalse(Freshness.locationCurrent(now - 5 * min, now))
        // looked for again when expired – after a search without result not every minute
        assertTrue(Freshness.locationDue(now - 6 * min, triedAt = 0L, now))
        assertFalse(Freshness.locationDue(now - 6 * min, triedAt = now - 1 * min, now))
        assertTrue(Freshness.locationDue(now - 6 * min, triedAt = now - 2 * min, now))
    }

    /** Indoors without network location every search ends empty: the pause grows – 2, 5, 10 minutes – instead of the GPS running half the time. */
    @Test fun searchesWithoutResultWaitLonger() {
        val old = now - 30 * min
        assertTrue(Freshness.locationDue(old, triedAt = now - 2 * min, now, misses = 1))
        assertFalse(Freshness.locationDue(old, triedAt = now - 4 * min, now, misses = 2))
        assertTrue(Freshness.locationDue(old, triedAt = now - 5 * min, now, misses = 2))
        assertFalse(Freshness.locationDue(old, triedAt = now - 9 * min, now, misses = 3))
        assertFalse(Freshness.locationDue(old, triedAt = now - 9 * min, now, misses = 7))
        assertTrue(Freshness.locationDue(old, triedAt = now - 10 * min, now, misses = 7))
    }

    /** Fresh data of Hannover in Bad Harzburg are not current: the cards of "my location" turn yellow. */
    @Test fun thePlaceIsPartOfWhatIsCurrent() {
        val notHere = LocationMark(current = false, searching = true, off = false)
        assertEquals(CardStatus.STALE, cardStatus("hourly", pageStale(emptySet(), notHere)))
        assertEquals(CardStatus.STALE, cardStatus("pollen", pageStale(emptySet(), notHere)))
        val here = notHere.copy(current = true, searching = false)
        assertEquals(CardStatus.FRESH, cardStatus("hourly", pageStale(emptySet(), here)))
        assertEquals(CardStatus.STALE, cardStatus("pollen", pageStale(setOf(DataPart.POLLEN), here)))
        // a saved place has no position to confirm
        assertEquals(CardStatus.FRESH, cardStatus("hourly", pageStale(emptySet(), null)))
    }

    /**
     * The position is looked for only while "my location" is seen: its page chosen, the list of
     * places open (it stands there with its values) or the places beside the weather – looking at
     * Berlin the GPS stays off.
     */
    @Test fun theGpsOnlyForMyLocation() {
        val here = Place("current-location", "Hannover", latitude = 52.37, longitude = 9.73, isCurrentLocation = true)
        val berlin = Place("berlin", "Berlin", latitude = 52.52, longitude = 13.40)
        val st = UiState(currentPlace = here, savedPlaces = listOf(berlin))
        assertTrue(locationWanted(st, sidebar = false))                                   // the first page
        assertTrue(locationWanted(st.copy(selectedPlaceId = here.id), sidebar = false))
        val atBerlin = st.copy(selectedPlaceId = berlin.id)
        assertFalse(locationWanted(atBerlin, sidebar = false))
        assertTrue(locationWanted(atBerlin.copy(backStack = listOf(Screen.Main, Screen.Places)), sidebar = false))
        assertTrue(locationWanted(atBerlin, sidebar = true))
        // without "my location" nothing to look for (the first position is asked for on its own)
        assertFalse(locationWanted(UiState(savedPlaces = listOf(berlin)), sidebar = true))
    }
}
