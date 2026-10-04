/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RecordsTest.kt
 * The model of what is current: each record goes out of date by itself at its time and tells
 * the controller; the position of "my location" takes its records with it; a confirmed position
 * does not make its data current – their arrival does.
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

import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.DataPart
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.data.repo.Freshness
import dev.nimbus.weather.data.repo.RecordKey
import dev.nimbus.weather.data.repo.RecordState.CURRENT
import dev.nimbus.weather.data.repo.RecordState.STALE
import dev.nimbus.weather.data.repo.Shelf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsTest {
    private val min = 60_000L
    private val start = 1_791_000_000_000L
    private val here = "current-location"
    private val berlin = "berlin"

    /** What went out of date and told the controller, in order. */
    private val due = mutableListOf<RecordKey>()

    private fun TestScope.shelf() = Shelf(backgroundScope, clock = { start + currentTime }, myLocationId = here, onDue = { due += it.key })

    private fun TestScope.data(id: String, partsAt: Map<DataPart, Long> = DataPart.entries.associateWith { start + currentTime }, stale: Set<DataPart> = emptySet()) =
        WeatherData(
            Place(id, id, latitude = 52.37, longitude = 9.73, isCurrentLocation = id == here), "UTC", 0,
            CurrentWeather(start, 15.0, 14.0, Condition.CLOUDY, true, 60.0, 8.0, 1020.0, 10.0, 20.0, 240.0, 10.0, 30000.0, 3.0, 0.0),
            emptyList(), emptyList(), sources = emptyList(), fetchedAt = start + currentTime, stale = stale, partsAt = partsAt,
        )

    /** A record goes out of date at the very moment its time is up – by itself, no clock read from outside. */
    @Test fun aRecordGoesOutOfDateAtItsTime() = runTest {
        val s = shelf()
        s.take(data(berlin))
        val forecast = s.part(berlin, DataPart.FORECAST)
        assertEquals(CURRENT, forecast.state)
        advanceTimeBy(Freshness.FORECAST_MS - 1); runCurrent()
        assertEquals(CURRENT, forecast.state)
        advanceTimeBy(1); runCurrent()
        assertEquals(STALE, forecast.state)
        assertEquals(RecordKey.Part(berlin, DataPart.FORECAST), due.first())
    }

    /** Each record for itself: the forecast after 10 minutes, the gauges after 15, the pollen after 3 hours – each its own event. */
    @Test fun eachRecordForItself() = runTest {
        val s = shelf()
        s.take(data(berlin))
        advanceTimeBy(10 * min); runCurrent()
        assertEquals(STALE, s.part(berlin, DataPart.FORECAST).state)
        assertEquals(CURRENT, s.part(berlin, DataPart.GAUGES).state)
        assertEquals(CURRENT, s.part(berlin, DataPart.POLLEN).state)
        advanceTimeBy(5 * min); runCurrent()
        assertEquals(STALE, s.part(berlin, DataPart.GAUGES).state)
        assertEquals(CURRENT, s.part(berlin, DataPart.POLLEN).state)
        advanceTimeBy(165 * min); runCurrent()
        assertEquals(STALE, s.part(berlin, DataPart.POLLEN).state)
        // each told once, in the order of their time
        val parts = due.filterIsInstance<RecordKey.Part>().map { it.part }
        assertEquals(DataPart.entries.size, parts.size)
        assertEquals(DataPart.POLLEN, parts.last())
    }

    /** The look-back keeps 15 minutes (the look-back of 01:00 was still shown at 09:28). */
    @Test fun theLookBackGoesOutOfDate() = runTest {
        val s = shelf()
        val r = s.lookBack(berlin)
        r.arrived(start)
        advanceTimeBy(15 * min - 1); runCurrent()
        assertEquals(CURRENT, r.state)
        advanceTimeBy(1); runCurrent()
        assertEquals(STALE, r.state)
        assertEquals(listOf<RecordKey>(RecordKey.LookBack(berlin)), due)
    }

    /** No new answer: out of date at once, and asked again after 2 minutes – not 10. */
    @Test fun aFailedPartIsAskedAgain() = runTest {
        val s = shelf()
        s.take(data(berlin, stale = setOf(DataPart.POLLEN)))
        val pollen = s.part(berlin, DataPart.POLLEN)
        assertEquals(STALE, pollen.state)
        pollen.stale(retryMs = Freshness.STALE_RETRY_MS)
        advanceTimeBy(Freshness.STALE_RETRY_MS - 1); runCurrent()
        assertEquals(emptyList<RecordKey>(), due)
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf<RecordKey>(RecordKey.Part(berlin, DataPart.POLLEN)), due)
    }

    /** Asked for anew (reload): the position out of date – and every record of "my location" with it; a saved place stays. */
    @Test fun theReloadOfMyLocationTakesItsRecordsWithIt() = runTest {
        val s = shelf()
        s.position.arrived(start)
        s.take(data(here)); s.take(data(berlin))
        s.lookBack(here).arrived(start)
        assertEquals(CURRENT, s.part(here, DataPart.FORECAST).state)
        s.position.stale()
        assertEquals(STALE, s.position.state)
        for (p in DataPart.entries) {
            assertEquals("$p of my location", STALE, s.part(here, p).state)
            assertEquals("$p of a saved place", CURRENT, s.part(berlin, p).state)
        }
        assertEquals(STALE, s.lookBack(here).state)
    }

    /** The position confirmed: its dot green – its data stay yellow until they have arrived anew. */
    @Test fun aConfirmedPositionDoesNotMakeItsDataCurrent() = runTest {
        val s = shelf()
        s.position.arrived(start)
        s.take(data(here))
        s.position.stale()
        s.position.arrived(start + currentTime)
        assertEquals(CURRENT, s.position.state)
        assertEquals(STALE, s.part(here, DataPart.FORECAST).state)
        s.take(data(here))
        assertEquals(CURRENT, s.part(here, DataPart.FORECAST).state)
    }

    /** Data taken while the position is not confirmed are not current, whatever their time. */
    @Test fun noCurrentDataWithoutAPosition() = runTest {
        val s = shelf()
        s.take(data(here))
        assertEquals(STALE, s.part(here, DataPart.FORECAST).state)
    }

    /** The position's time is up after 5 minutes: out of date, its data with it, the controller told to look for it. */
    @Test fun thePositionGoesOutOfDateWithItsData() = runTest {
        val s = shelf()
        s.position.arrived(start)
        s.take(data(here))
        advanceTimeBy(Freshness.LOCATION_MS); runCurrent()
        assertEquals(STALE, s.position.state)
        assertEquals(STALE, s.part(here, DataPart.POLLEN).state)
        assertEquals(RecordKey.Position, due.first())
    }

    /** Back from the background, where the device slept and no timer ran: what is past its time goes out of date at once. */
    @Test fun backFromTheBackground() = runTest {
        var clock = start
        val s = Shelf(backgroundScope, clock = { clock }, myLocationId = here, onDue = { due += it.key })
        s.take(data(berlin, partsAt = DataPart.entries.associateWith { start }))
        clock = start + 11 * min                       // the timers did not run
        assertEquals(CURRENT, s.part(berlin, DataPart.FORECAST).state)
        s.checkAll()
        assertEquals(STALE, s.part(berlin, DataPart.FORECAST).state)
        assertEquals(CURRENT, s.part(berlin, DataPart.GAUGES).state)
        assertEquals(RecordKey.Part(berlin, DataPart.FORECAST), due.single { it == RecordKey.Part(berlin, DataPart.FORECAST) })
    }

    /** Taken data: each part at its own time – one fetched long ago is out of date at once. */
    @Test fun eachPartAtItsOwnTime() = runTest {
        val s = shelf()
        val now = start + currentTime
        s.take(data(berlin, partsAt = DataPart.entries.associateWith { now } + (DataPart.POLLEN to now - 4 * 60 * min)))
        assertEquals(CURRENT, s.part(berlin, DataPart.FORECAST).state)
        assertEquals(STALE, s.part(berlin, DataPart.POLLEN).state)
        assertEquals(setOf(DataPart.POLLEN), s.due(berlin))
    }
}
