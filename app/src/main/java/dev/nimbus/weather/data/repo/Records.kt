/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/Records.kt
 * The model of what is current: every record of data – each part of a place's weather, its
 * look-back, the position of "my location" – knows when it was fetched and how long it keeps,
 * and tells its card when it goes out of date; the position tells the records that depend on it.
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

package dev.nimbus.weather.data.repo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.nimbus.weather.data.model.DataPart
import dev.nimbus.weather.data.model.WeatherData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a record says about its data: current, or out of date (its card yellow). */
enum class RecordState { CURRENT, STALE }

/** The name of a record. */
sealed interface RecordKey {
    /** A part of a place's weather (forecast, pollen, gauges …). */
    data class Part(val placeId: String, val part: DataPart) : RecordKey
    /** The look-back of a place. */
    data class LookBack(val placeId: String) : RecordKey
    /** The position of "my location". */
    data object Position : RecordKey
}

/**
 * One record of the model. Its [state] is what its card shows – the card reads it and is drawn
 * anew when it changes, nothing else is. It changes on events only:
 * - [arrived]: new data – current, and the moment it goes out of date is set (its own timer);
 * - its time is up: out of date, and the controller is told ([Shelf.onDue]) to fetch it anew;
 * - [stale]: not current any more (asked for anew, a request running or failed) – and so are the
 *   records that depend on it ([dependents]: the data of "my location" on its position).
 * Used on the main thread.
 */
class DataRecord internal constructor(val key: RecordKey, private val lifeMs: Long, private val shelf: Shelf) {
    var state: RecordState by mutableStateOf(RecordState.STALE)
        private set

    /** When the data now shown were fetched (0: none or of unknown age). */
    var fetchedAt: Long = 0L
        private set

    internal val dependents = mutableSetOf<DataRecord>()
    private var timer: Job? = null

    /** New data, fetched at [at]: current until their life is over (then out of date, by themselves). */
    fun arrived(at: Long) {
        fetchedAt = at
        val left = at + lifeMs - shelf.clock()
        if (left <= 0) { expire(); return }
        state = RecordState.CURRENT
        after(left) { expire() }
    }

    /**
     * Not current any more: asked for anew, a request running, or failed – then [retryMs]: the
     * controller is told to ask again after this long. The records depending on it go with it.
     */
    fun stale(retryMs: Long? = null) {
        timer?.cancel()
        timer = null
        state = RecordState.STALE
        dependents.forEach { it.stale() }
        if (retryMs != null) after(retryMs) { shelf.onDue(this) }
    }

    /**
     * Compared with the clock: back from the background, where the device slept and no timer ran,
     * what is past its time goes out of date now.
     */
    fun check() {
        if (state == RecordState.CURRENT && shelf.clock() >= fetchedAt + lifeMs) expire()
    }

    private fun expire() {
        stale()
        shelf.onDue(this)
    }

    private fun after(ms: Long, then: () -> Unit) {
        timer?.cancel()
        timer = shelf.scope.launch {
            delay(ms)
            timer = null
            then()
        }
    }
}

/**
 * All records of the app. [onDue]: a record went out of date (its time was up, or a failed one is
 * to be asked again) – the controller fetches it anew. The data of [myLocationId] depend on the
 * [position]: when it is out of date, so are they.
 */
class Shelf(
    internal val scope: CoroutineScope,
    internal val clock: () -> Long = System::currentTimeMillis,
    private val myLocationId: String = LocationProvider.CURRENT_LOCATION_ID,
    internal val onDue: (DataRecord) -> Unit = {},
) {
    private val records = HashMap<RecordKey, DataRecord>()

    /** The position of "my location". */
    val position: DataRecord = DataRecord(RecordKey.Position, Freshness.LOCATION_MS, this).also { records[RecordKey.Position] = it }

    fun part(placeId: String, part: DataPart): DataRecord = record(RecordKey.Part(placeId, part))

    fun lookBack(placeId: String): DataRecord = record(RecordKey.LookBack(placeId))

    private fun record(key: RecordKey): DataRecord = records.getOrPut(key) {
        val (placeId, life) = when (key) {
            is RecordKey.Part -> key.placeId to Freshness.lifeMs(key.part)
            is RecordKey.LookBack -> key.placeId to Freshness.HISTORY_MS
            RecordKey.Position -> error("the position is made with the shelf")
        }
        DataRecord(key, life, this).also { r ->
            // the data of "my location" are as current as its position
            if (placeId == myLocationId) {
                position.dependents += r
                if (position.state == RecordState.STALE) r.stale()
            }
        }
    }

    /**
     * Takes the weather [data] of a place (stored, or a step of a load): each part fetched at its
     * time – the ones still on the way or without a new answer out of date.
     */
    fun take(data: WeatherData) {
        val myData = data.place.id == myLocationId
        for (p in DataPart.entries) {
            val r = part(data.place.id, p)
            when {
                p in data.stale -> if (r.state != RecordState.STALE) r.stale()
                // "my location" while its position is not confirmed: not current, whatever comes
                myData && position.state == RecordState.STALE -> r.stale()
                r.state != RecordState.CURRENT || r.fetchedAt != data.fetchedAt(p) -> r.arrived(data.fetchedAt(p))
            }
        }
    }

    /**
     * What the card of [part] of [placeId] shows – read in a card, the card alone is drawn anew
     * when it changes. No record yet (no data taken): out of date.
     */
    fun stateOf(placeId: String, part: DataPart): RecordState = records[RecordKey.Part(placeId, part)]?.state ?: RecordState.STALE

    /** What the look-back of [placeId] shows (see [stateOf]). */
    fun lookBackState(placeId: String): RecordState = records[RecordKey.LookBack(placeId)]?.state ?: RecordState.STALE

    /** The parts of [placeId]'s weather out of date: to be fetched anew. */
    fun due(placeId: String): Set<DataPart> =
        DataPart.entries.filterTo(mutableSetOf()) { part(placeId, it).state == RecordState.STALE }

    /** All records against the clock (see [DataRecord.check]). */
    fun checkAll() = records.values.toList().forEach { it.check() }

    /** A place given up: its records go. */
    fun forget(placeId: String) {
        records.keys.filter { (it is RecordKey.Part && it.placeId == placeId) || (it is RecordKey.LookBack && it.placeId == placeId) }
            .forEach { k -> records.remove(k)?.let { r -> r.stale(); position.dependents -= r } }
    }
}
