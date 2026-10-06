/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/PageCards.kt
 * The cards of the weather page: their list, status dots and popping in when their data arrive.
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

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.nimbus.weather.ui.components.CardStatus
import dev.nimbus.weather.ui.components.LocalCardStatus

/** One card of the weather page; [fullSpan] cards span all columns on a tablet. */
internal class PageItem(val key: String, val fullSpan: Boolean = false, val content: @Composable () -> Unit)

/**
 * In the columns of a tablet, a card between two wide ones (or a wide one and the end) would
 * stand alone in its row, the other column empty – e.g. "precipitation today" right above the
 * hourly row: it gets the full width.
 */
internal fun spanLonely(items: List<PageItem>): List<PageItem> =
    items.mapIndexed { i, it -> if (!it.fullSpan && lonely(items.map { p -> p.fullSpan }, i)) PageItem(it.key, true, it.content) else it }

/** Whether the narrow item [i] of a list ([wide]: which items span all columns) stands alone between wide ones. */
internal fun lonely(wide: List<Boolean>, i: Int): Boolean =
    !wide[i] && (i == 0 || wide[i - 1]) && (i == wide.lastIndex || wide[i + 1])

/**
 * Which cards of a page came later than the rest: the first list of a page is there; a key that
 * joins a later list arrived (its data came in, or the card was switched on) and is [fresh] for
 * [FRESH_MS] – long enough to pop in when it is drawn, never again when it scrolls back into view.
 */
internal class Arrivals(private val clock: () -> Long = System::currentTimeMillis) {
    private var known: Set<String>? = null
    private val since = HashMap<String, Long>()

    fun note(keys: List<String>) {
        val k = known
        if (k != null) for (key in keys) if (key !in k) since[key] = clock()
        known = keys.toSet()
    }

    fun fresh(key: String): Boolean = since[key]?.let { clock() - it < FRESH_MS } == true

    companion object { const val FRESH_MS = 2_000L }
}

/**
 * The data part a card of the page shows (its status dot follows it); null: no dot (the header
 * space, banners, the footer, the model comparison loaded on demand).
 */
internal fun cardParts(key: String): Set<dev.nimbus.weather.data.model.DataPart>? = when (key) {
    "header-space", "offline", "sources", "models" -> null
    "alerts" -> setOf(dev.nimbus.weather.data.model.DataPart.FORECAST, dev.nimbus.weather.data.model.DataPart.FLOOD)
    "aqi" -> setOf(dev.nimbus.weather.data.model.DataPart.AIR_QUALITY)
    "pollen" -> setOf(dev.nimbus.weather.data.model.DataPart.POLLEN)
    "community" -> setOf(dev.nimbus.weather.data.model.DataPart.COMMUNITY)
    "gauge" -> setOf(dev.nimbus.weather.data.model.DataPart.GAUGES)
    "bathing" -> setOf(dev.nimbus.weather.data.model.DataPart.BATHING)
    else -> setOf(dev.nimbus.weather.data.model.DataPart.FORECAST)
}

/** Status of the card [key] when [stale] parts still show older values. */
internal fun cardStatus(key: String, stale: Set<dev.nimbus.weather.data.model.DataPart>): CardStatus? =
    cardParts(key)?.let { parts -> if (parts.any { it in stale }) CardStatus.STALE else CardStatus.FRESH }

/**
 * One card of the page: popping in when it arrived, with its status dot – read from the records of
 * its parts: when one of them goes out of date or arrives, this card alone is drawn anew.
 */
@Composable
internal fun Card(item: PageItem, arrivals: Arrivals, placeId: String) {
    val shelf = LocalShelf.current
    val stale = cardParts(item.key)?.filterTo(mutableSetOf()) { shelf.stateOf(placeId, it) != dev.nimbus.weather.data.repo.RecordState.CURRENT }.orEmpty()
    // the protocol of the view (debug builds): each card drawn, with the parts it shows out of date
    if (dev.nimbus.weather.BuildConfig.DEBUG) android.util.Log.d("NimbusCard", "$placeId ${item.key} stale=$stale")
    PopIn(arrivals.fresh(item.key)) {
        CompositionLocalProvider(LocalCardStatus provides cardStatus(item.key, stale)) {
            Box(Modifier.testTag("card-${item.key}")) { item.content() }
        }
    }
}

/** The model of what is current ([dev.nimbus.weather.data.repo.Shelf]) – provided by the app's root. */
val LocalShelf = androidx.compose.runtime.staticCompositionLocalOf {
    dev.nimbus.weather.data.repo.Shelf(kotlinx.coroutines.MainScope())
}

/** Overshoots a little before it settles – the "pop". */
private val Pop = androidx.compose.animation.core.Easing { t -> val x = t - 1f; x * x * (2.70158f * x + 1.70158f) + 1f }

/**
 * A card that arrives: first its place opens in the list (the cards below slide down), then it
 * pops in from small to its size. [fresh] false: simply there (the same layout, so a card never
 * loses its state when the flag changes).
 */
@Composable
private fun PopIn(fresh: Boolean, content: @Composable () -> Unit) {
    val state = remember { androidx.compose.animation.core.MutableTransitionState(!fresh).apply { targetState = true } }
    androidx.compose.animation.AnimatedVisibility(
        state,
        enter = androidx.compose.animation.expandVertically(
            androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            expandFrom = Alignment.Top, clip = false,
        ) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(380, delayMillis = 240, easing = Pop), initialScale = 0.6f) +
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, delayMillis = 240)),
    ) { content() }
}
