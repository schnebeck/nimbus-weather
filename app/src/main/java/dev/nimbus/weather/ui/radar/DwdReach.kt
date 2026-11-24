/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/DwdReach.kt
 * The widest reach the DWD's radars have shown: where it is wider than now, one of them is out.
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

package dev.nimbus.weather.ui.radar

import java.io.File
import kotlin.math.floor

/**
 * Every mask of the DWD's reach ([DwdCoverage]) seen on this device, joined – on the same grid,
 * kept in [RadarComposites.dir] – starting from [REFERENCE], the reach of a time all its radars
 * measured (tools/dwd_reach_reference.py). Where it reached and the mask of now does not, a radar
 * is out of service: a hole of at least [OUT_CELLS] cells in the picture area – or, zoomed in, at
 * least [OUT_SHARE] of the cells it reaches there and [MIN_CELLS] – not a few at the edge.
 */
internal object DwdReach {
    private const val FILE = "dwd_reach.bin"

    /**
     * The full reach shipped with the app: the composite of 12 Nov 2026, 12:00 UTC – by its whole
     * path, the release build moves the classes to other packages.
     */
    const val REFERENCE = "/dev/nimbus/weather/ui/radar/dwd_reach_reference.bin"

    /** About 1000 km² – a radar out leaves a hole of 150 km around it, the edge wavers by a cell or two. */
    const val OUT_CELLS = 200

    /** Zoomed in on the hole the area has fewer cells: most of those the radars reach there missing. */
    const val OUT_SHARE = 0.4

    /** … and at least these – a view of a few cells tells nothing. */
    const val MIN_CELLS = 20

    @Volatile private var widest: BooleanArray? = null

    /** Where the widest reach starts on a new device: the reach shipped with the app; replaceable for tests. */
    @Volatile internal var shipped: () -> BooleanArray? = ::reference
    @Volatile private var now: BooleanArray? = null

    /** The mask of now ([mask]) joined to the widest, which is stored when it grew. */
    fun join(mask: BooleanArray) = synchronized(this) {
        now = mask
        val old = widest ?: start()
        val joined = joined(old, mask)
        widest = joined
        if (old == null || !joined.contentEquals(old)) write(joined)
    }

    /**
     * The widest reach at a new start: the stored one joined with the one shipped with the app – a
     * reach stored while a radar was out lacks its area.
     */
    private fun start(): BooleanArray? {
        val stored = read()
        val ref = shipped()
        if (stored == null) return ref
        if (ref == null) return stored
        return joined(stored, ref)
    }

    /** Whether a radar is out in the rectangle (degrees): a hole of [OUT_CELLS] cells or more. */
    fun outIn(west: Double, east: Double, south: Double, north: Double): Boolean {
        val w = widest ?: return false
        val n = now ?: return false
        val hole = holeCells(w, n, west, east, south, north)
        val reach = holeCells(w, BooleanArray(n.size), west, east, south, north)
        return hole >= OUT_CELLS || hole >= MIN_CELLS && hole >= reach * OUT_SHARE
    }

    /** [old] and [mask] joined: covered where either is. */
    fun joined(old: BooleanArray?, mask: BooleanArray): BooleanArray =
        if (old == null || old.size != mask.size) mask.copyOf() else BooleanArray(mask.size) { old[it] || mask[it] }

    /** The cells in the rectangle the [widest] reach covers and [now] does not. */
    fun holeCells(widest: BooleanArray, now: BooleanArray, west: Double, east: Double, south: Double, north: Double): Int {
        val step = (DwdCoverage.LON1 - DwdCoverage.LON0) / DwdCoverage.W
        val x0 = floor((west - DwdCoverage.LON0) / step).toInt().coerceIn(0, DwdCoverage.W - 1)
        val x1 = floor((east - DwdCoverage.LON0) / step).toInt().coerceIn(0, DwdCoverage.W - 1)
        val y0 = floor((DwdCoverage.LAT1 - north) / step).toInt().coerceIn(0, DwdCoverage.H - 1)
        val y1 = floor((DwdCoverage.LAT1 - south) / step).toInt().coerceIn(0, DwdCoverage.H - 1)
        var count = 0
        for (y in y0..y1) for (x in x0..x1) {
            val i = y * DwdCoverage.W + x
            if (widest[i] && !now[i]) count++
        }
        return count
    }

    private fun file(): File? = RadarComposites.dir?.let { File(it, FILE) }

    /** The stored mask ([MaskFile]). */
    private fun read(): BooleanArray? = MaskFile.read(file(), DwdCoverage.W * DwdCoverage.H)

    /** The reach shipped with the app. */
    internal fun reference(): BooleanArray? =
        runCatching {
            DwdReach::class.java.getResourceAsStream(REFERENCE)?.use { it.readBytes() }?.let { MaskFile.unpack(it, DwdCoverage.W * DwdCoverage.H) }
        }.getOrNull()

    private fun write(mask: BooleanArray) = MaskFile.write(file(), mask)

    /** For the debug log: what is known. */
    fun state(): String = "widest ${widest?.count { it }} cells, now ${now?.count { it }}"

    /** For tests: forgets what it has seen (the stored file stays). */
    internal fun reset() = synchronized(this) { widest = null; now = null }
}
