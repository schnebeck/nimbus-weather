/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/widget/WidgetFrames.kt
 * The size of the frame the launcher gives a widget, found out from which picture it shows.
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

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The search for a widget's frame. The widget offers pictures of several sizes; the system shows
 * the closest that fits the frame (the smallest where none fits) and the launcher fetches that
 * picture's pixel ([WidgetProbe]). So the frame is at least the size shown – and a picture larger
 * in both ways, which would have come closer, did not fit: in the way the shown one has already,
 * the other is too large ([overW], [overH]). The next pictures lie in steps between, the height
 * first, until the frame is known within [DONE] dp each way; then the widget is planned for it.
 *
 * [signature]: the sizes the launcher reports – when they change (the widget resized), the search
 * starts anew. [round]: the pictures last offered ([offered]), whose answer counts. At most
 * [BUDGET] rounds in [BUDGET_MS], so that a launcher whose frame keeps changing is not chased.
 */
data class FrameSearch(
    val signature: String,
    val round: Int = 0,
    val offered: List<WidgetSize> = emptyList(),
    /** The largest size known to fit. */
    val fit: WidgetSize? = null,
    /** The least width known not to fit; the least height. */
    val overW: Int? = null,
    val overH: Int? = null,
    val budgetFrom: Long = 0,
    val budgetUsed: Int = 0,
) {
    private val doneH get() = fit != null && overH != null && overH - fit.heightDp <= DONE
    private val doneW get() = fit != null && overW != null && overW - fit.widthDp <= DONE

    /** The frame known within [DONE] dp: the pictures are planned for [fit]. */
    val settled get() = doneH && doneW

    /**
     * The next round's pictures from [base] (those from the launcher's figures): none heard yet,
     * those and the [FLOOR]; searching, the size that fits and steps above it; else the size that
     * fits and those from the figures besides – one of them shown means the frame has changed.
     */
    fun next(base: List<WidgetSize>, now: Long): FrameSearch {
        val renewed = now - budgetFrom >= BUDGET_MS || now < budgetFrom
        val used = if (renewed) 0 else budgetUsed
        val searching = fit != null && !settled && used < BUDGET
        val sizes = when {
            fit == null -> base.take(MAX_BASE)
            searching -> listOf(fit) + probes(fit, offered + base)
            else -> listOf(fit) + base.filter { it != fit }.take(MAX_BASE - 1)
        } + FLOOR
        return copy(
            round = round + 1, offered = sizes.distinct(),
            budgetFrom = if (renewed) now else budgetFrom, budgetUsed = used + if (searching) 1 else 0,
        )
    }

    /**
     * The steps above [f] in the way not yet known: between it and the least known too large, or
     * up to the largest of [known] (the sizes offered and reported).
     */
    private fun probes(f: WidgetSize, known: List<WidgetSize>): List<WidgetSize> = if (!doneH) {
        steps(f.heightDp, overH, known.maxOfOrNull { it.heightDp } ?: 0).map { WidgetSize(f.widthDp, it) }
    } else {
        steps(f.widthDp, overW, known.maxOfOrNull { it.widthDp } ?: 0).map { WidgetSize(it, f.heightDp) }
    }

    /**
     * Just above [lo] first – a frame is mostly a little larger than a size that fits – then in
     * even steps up to [over], or where that is not known up to [largest], at least [EXPLORE] further.
     */
    private fun steps(lo: Int, over: Int?, largest: Int): List<Int> {
        val near = lo + DONE
        val top = over ?: maxOf(lo + EXPLORE, largest)
        val rest = (1 until PROBES).map { near + (top - near) * it / (if (over != null) PROBES else PROBES - 1) }
        return (listOf(near) + rest).filter { it > lo && (over == null || it < over) }.distinct()
    }

    /**
     * The picture of [chosen] shown in round [round]: what follows for the frame – null where
     * nothing (another round's answer, or nothing new).
     */
    fun heard(round: Int, chosen: WidgetSize): FrameSearch? {
        if (round != this.round || chosen !in offered) return null
        val f = fit
        val next = when {
            f == null || chosen == f -> copy(fit = chosen).bounded(chosen)
            chosen.widthDp >= f.widthDp && chosen.heightDp >= f.heightDp -> copy(fit = chosen).bounded(chosen)
            // smaller than what fitted: the frame has changed (turned, resized) – searched anew from here
            else -> copy(fit = chosen, overW = null, overH = null).bounded(chosen)
        }
        return next.takeIf { it != this }
    }

    /** The pictures larger than [c] both ways did not fit: too wide where as high, too high where as wide. */
    private fun bounded(c: WidgetSize): FrameSearch {
        var w = overW?.takeIf { it > c.widthDp }
        var h = overH?.takeIf { it > c.heightDp }
        for (o in offered) {
            if (o == c || o.widthDp < c.widthDp || o.heightDp < c.heightDp) continue
            if (o.widthDp == c.widthDp) h = minOf(h ?: o.heightDp, o.heightDp)
            else if (o.heightDp == c.heightDp) w = minOf(w ?: o.widthDp, o.widthDp)
        }
        return copy(overW = w, overH = h)
    }

    fun toJson(): String = JSONObject()
        .put("signature", signature).put("round", round)
        .put("offered", JSONArray(offered.map { "${it.widthDp}x${it.heightDp}" }))
        .put("fit", fit?.let { "${it.widthDp}x${it.heightDp}" })
        .put("overW", overW ?: -1).put("overH", overH ?: -1)
        .put("budgetFrom", budgetFrom).put("budgetUsed", budgetUsed)
        .toString()

    companion object {
        /** Known within this many dp each way: the pictures planned for it. */
        const val DONE = 2
        /** The steps offered in a round besides the size that fits and the floor. */
        const val PROBES = 4
        /** How far above a size that fits to look where nothing larger is known. */
        const val EXPLORE = 48
        /** Of those from the launcher's figures, at most this many (each picture carries its bitmaps). */
        const val MAX_BASE = 6
        const val BUDGET = 10
        const val BUDGET_MS = 10 * 60_000L
        /** A picture that fits any frame: shown, none of the others fits. */
        val FLOOR = WidgetSize(40, 40)

        fun size(text: String?): WidgetSize? = text?.split('x')?.takeIf { it.size == 2 }
            ?.let { (w, h) -> w.toIntOrNull()?.let { wi -> h.toIntOrNull()?.let { hi -> WidgetSize(wi, hi) } } }

        fun fromJson(text: String): FrameSearch? = runCatching {
            val o = JSONObject(text)
            val offered = o.getJSONArray("offered").let { a -> (0 until a.length()).mapNotNull { size(a.getString(it)) } }
            FrameSearch(
                signature = o.getString("signature"), round = o.getInt("round"), offered = offered,
                fit = size(o.optString("fit").takeIf { o.has("fit") && !o.isNull("fit") }),
                overW = o.getInt("overW").takeIf { it >= 0 }, overH = o.getInt("overH").takeIf { it >= 0 },
                budgetFrom = o.getLong("budgetFrom"), budgetUsed = o.getInt("budgetUsed"),
            )
        }.getOrNull()
    }
}

/** Each widget's [FrameSearch], kept across the app's restarts. */
class WidgetFrames(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("widget_frames", Context.MODE_PRIVATE)

    fun of(id: Int): FrameSearch? = prefs.getString(id.toString(), null)?.let(FrameSearch::fromJson)

    /** The pictures to offer widget [id] now: the search for the launcher's [signature] taken a round further. */
    fun next(id: Int, signature: String, base: List<WidgetSize>, now: Long = System.currentTimeMillis()): FrameSearch = synchronized(LOCK) {
        val was = of(id)?.takeIf { it.signature == signature } ?: FrameSearch(signature)
        was.next(base, now).also { prefs.edit().putString(id.toString(), it.toJson()).commit() }
    }

    /** Widget [id] shows its picture of [size] from [round]: the search, where that tells something new. */
    fun heard(id: Int, round: Int, size: WidgetSize): FrameSearch? = synchronized(LOCK) {
        of(id)?.heard(round, size)?.also { prefs.edit().putString(id.toString(), it.toJson()).commit() }
    }

    fun remove(id: Int) = synchronized(LOCK) { prefs.edit().remove(id.toString()).commit() }

    private companion object {
        val LOCK = Any()
    }
}
