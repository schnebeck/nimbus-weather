package dev.nimbus.weather.ui.radar

import kotlin.math.floor

/**
 * Isolines of a temperature field with marching squares. The grid is first refined bilinearly
 * ([refine] × per cell) so the lines follow the smooth field instead of the coarse grid cells.
 * Returns line segments as (lon1, lat1, lon2, lat2) together with their level in °C.
 */
object Isolines {
    data class Segment(val level: Double, val lon1: Double, val lat1: Double, val lon2: Double, val lat2: Double)

    /** Lines at [offset] + k · [band]; e.g. band 1, offset 0.5 → 21.5, 22.5, … (the rounding borders). */
    fun compute(g: WeatherGrid, field: FloatArray, band: Double, refine: Int = 4, offset: Double = 0.0): List<Segment> {
        val rows = (g.rows - 1) * refine + 1
        val cols = (g.cols - 1) * refine + 1
        val step = g.step / refine
        val v = Array(rows) { r -> DoubleArray(cols) { c -> g.sample(field, g.lat0 + r * step, g.lon0 + c * step)?.toDouble() ?: Double.NaN } }
        val finite = v.flatMap { it.asIterable() }.filter { !it.isNaN() }
        if (finite.isEmpty()) return emptyList()
        val first = floor((finite.min() - offset) / band).toInt() + 1
        val last = kotlin.math.ceil((finite.max() - offset) / band).toInt() - 1     // no line exactly on the maximum
        val out = ArrayList<Segment>()
        for (k in first..last) {
            val level = offset + k * band
            for (r in 0 until rows - 1) for (c in 0 until cols - 1) {
                // corners: a = bottom-left, b = bottom-right, cc = top-right, d = top-left (lat grows with r)
                val a = v[r][c]; val b = v[r][c + 1]; val cc = v[r + 1][c + 1]; val d = v[r + 1][c]
                if (a.isNaN() || b.isNaN() || cc.isNaN() || d.isNaN()) continue
                val idx = (if (a >= level) 1 else 0) or (if (b >= level) 2 else 0) or (if (cc >= level) 4 else 0) or (if (d >= level) 8 else 0)
                if (idx == 0 || idx == 15) continue
                val lat0 = g.lat0 + r * step; val lon0 = g.lon0 + c * step
                fun t(x: Double, y: Double) = if (y == x) 0.5 else ((level - x) / (y - x)).coerceIn(0.0, 1.0)
                // edge points: bottom (a-b), right (b-cc), top (d-cc), left (a-d)
                val bottom = Pair(lon0 + t(a, b) * step, lat0)
                val right = Pair(lon0 + step, lat0 + t(b, cc) * step)
                val top = Pair(lon0 + t(d, cc) * step, lat0 + step)
                val left = Pair(lon0, lat0 + t(a, d) * step)
                fun seg(p: Pair<Double, Double>, q: Pair<Double, Double>) { out += Segment(level, p.first, p.second, q.first, q.second) }
                when (idx) {
                    1, 14 -> seg(left, bottom)
                    2, 13 -> seg(bottom, right)
                    3, 12 -> seg(left, right)
                    4, 11 -> seg(right, top)
                    6, 9 -> seg(bottom, top)
                    7, 8 -> seg(left, top)
                    5 -> { seg(left, top); seg(bottom, right) }       // saddles: resolved simply
                    10 -> { seg(left, bottom); seg(right, top) }
                }
            }
        }
        return out
    }
}
