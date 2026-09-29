package dev.nimbus.weather.util

import kotlin.math.abs

/**
 * Light phases of a day from the sun's altitude, named as people know them rather than by the
 * astronomical twilight definitions (civil −6°, nautical −12°, astronomical −18°):
 * - Day: above +6°
 * - Golden hour (morning/evening glow): −4° … +6°
 * - Blue hour: −8° … −4°
 * - Night: below −8°
 */
object SunPhases {
    enum class Phase { DAY, GOLDEN, BLUE, NIGHT }

    const val DAY_FROM = 6.0
    const val GOLDEN_FROM = -4.0
    const val BLUE_FROM = -8.0

    fun phaseOf(altitude: Double): Phase = when {
        altitude > DAY_FROM -> Phase.DAY
        altitude >= GOLDEN_FROM -> Phase.GOLDEN
        altitude >= BLUE_FROM -> Phase.BLUE
        else -> Phase.NIGHT
    }

    data class Span(val phase: Phase, val start: Long, val end: Long, val morning: Boolean)

    /**
     * Contiguous phase intervals of a sampled altitude curve (time to degrees, ascending). Phase
     * changes are interpolated linearly between the samples; spans before the highest point of
     * the curve count as morning.
     */
    fun spans(curve: List<Pair<Long, Double>>): List<Span> {
        if (curve.isEmpty()) return emptyList()
        val noon = curve.maxBy { it.second }.first
        val out = mutableListOf<Span>()
        var phase = phaseOf(curve.first().second)
        var start = curve.first().first
        for (i in 1 until curve.size) {
            val (t0, a0) = curve[i - 1]
            val (t1, a1) = curve[i]
            val p = phaseOf(a1)
            if (p == phase) continue
            val threshold = listOf(DAY_FROM, GOLDEN_FROM, BLUE_FROM).filter { it in minOf(a0, a1)..maxOf(a0, a1) }
                .minByOrNull { abs(it - a0) } ?: a1
            val t = if (a1 == a0) t1 else t0 + ((threshold - a0) / (a1 - a0) * (t1 - t0)).toLong()
            out += Span(phase, start, t, (start + t) / 2 < noon)
            phase = p
            start = t
        }
        val end = curve.last().first
        out += Span(phase, start, end, (start + end) / 2 < noon)
        return out
    }

    /** Highest possible sun altitude at this latitude (midsummer noon), used for a fixed scale. */
    fun maxAltitude(latitude: Double): Double = minOf(90.0, 90.0 - abs(latitude) + 23.44)
}
