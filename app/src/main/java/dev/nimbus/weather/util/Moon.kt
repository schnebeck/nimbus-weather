package dev.nimbus.weather.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Moon position, phase and rise/set times – a port of the moon part of SunCalc
 * (Vladimir Agafonkin, BSD-2-Clause, https://github.com/mourner/suncalc), which is based on
 * the formulas of "Astronomy Answers" (aa.quae.nl). Accuracy: a few minutes for rise/set,
 * which is plenty for a weather app.
 */
object Moon {
    private const val RAD = PI / 180
    private const val DAY_MS = 86_400_000.0
    private const val J1970 = 2440588.0
    private const val J2000 = 2451545.0
    private const val E = RAD * 23.4397
    const val SYNODIC_MONTH_DAYS = 29.530588853

    data class Illumination(
        /** Illuminated fraction 0..1. */
        val fraction: Double,
        /** 0 = new moon, 0.25 first quarter, 0.5 full moon, 0.75 last quarter. */
        val phase: Double,
    )

    data class Times(val rise: Long?, val set: Long?, val alwaysUp: Boolean, val alwaysDown: Boolean)

    enum class Phase { NEW, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS, FULL, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT }

    private fun toDays(ms: Long) = ms / DAY_MS - 0.5 + J1970 - J2000
    private fun rightAscension(l: Double, b: Double) = atan2(sin(l) * cos(E) - tan(b) * sin(E), cos(l))
    private fun declination(l: Double, b: Double) = asin(sin(b) * cos(E) + cos(b) * sin(E) * sin(l))
    private fun altitude(h: Double, phi: Double, dec: Double) = asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(h))
    private fun siderealTime(d: Double, lw: Double) = RAD * (280.16 + 360.9856235 * d) - lw
    private fun astroRefraction(h0: Double): Double {
        val h = if (h0 < 0) 0.0 else h0
        return 0.0002967 / tan(h + 0.00312536 / (h + 0.08901179))
    }

    private class Coords(val ra: Double, val dec: Double, val dist: Double)

    private fun sunCoords(d: Double): Coords {
        val m = RAD * (357.5291 + 0.98560028 * d)
        val c = RAD * (1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m))
        val l = m + c + RAD * 102.9372 + PI
        return Coords(rightAscension(l, 0.0), declination(l, 0.0), 149598000.0)
    }

    private fun moonCoords(d: Double): Coords {
        val lm = RAD * (218.316 + 13.176396 * d)
        val m = RAD * (134.963 + 13.064993 * d)
        val f = RAD * (93.272 + 13.229350 * d)
        val l = lm + RAD * 6.289 * sin(m)
        val b = RAD * 5.128 * sin(f)
        val dt = 385001 - 20905 * cos(m)
        return Coords(rightAscension(l, b), declination(l, b), dt)
    }

    /** Altitude of the moon above the horizon in radians (with refraction). */
    fun altitude(timeMs: Long, lat: Double, lon: Double): Double {
        val lw = RAD * -lon
        val phi = RAD * lat
        val d = toDays(timeMs)
        val c = moonCoords(d)
        val h = siderealTime(d, lw) - c.ra
        val alt = altitude(h, phi, c.dec)
        return alt + astroRefraction(alt)
    }

    /** Altitude of the sun's centre above the horizon in degrees (geometric, without refraction). */
    fun sunAltitude(timeMs: Long, lat: Double, lon: Double): Double {
        val d = toDays(timeMs)
        val c = sunCoords(d)
        val h = siderealTime(d, RAD * -lon) - c.ra
        return altitude(h, RAD * lat, c.dec) / RAD
    }

    /**
     * Sunrise and sunset (upper limb with refraction, −0.833°) of the day starting at
     * [dayStartMs], found by scanning in 5-minute steps; null when the sun does not cross.
     */
    fun sunTimes(dayStartMs: Long, lat: Double, lon: Double): Pair<Long?, Long?> {
        val step = 5 * 60_000L
        var rise: Long? = null
        var set: Long? = null
        var prev = sunAltitude(dayStartMs, lat, lon) + 0.833
        for (i in 1..288) {
            val t = dayStartMs + i * step
            val cur = sunAltitude(t, lat, lon) + 0.833
            if (prev < 0 && cur >= 0 && rise == null) rise = t - step + (step * (-prev / (cur - prev))).toLong()
            if (prev >= 0 && cur < 0 && set == null) set = t - step + (step * (prev / (prev - cur))).toLong()
            prev = cur
        }
        return rise to set
    }

    fun illumination(timeMs: Long): Illumination {
        val d = toDays(timeMs)
        val s = sunCoords(d)
        val m = moonCoords(d)
        val phi = acos(sin(s.dec) * sin(m.dec) + cos(s.dec) * cos(m.dec) * cos(s.ra - m.ra))
        val inc = atan2(s.dist * sin(phi), m.dist - s.dist * cos(phi))
        val angle = atan2(cos(s.dec) * sin(s.ra - m.ra), sin(s.dec) * cos(m.dec) - cos(s.dec) * sin(m.dec) * cos(s.ra - m.ra))
        return Illumination(
            fraction = (1 + cos(inc)) / 2,
            phase = 0.5 + 0.5 * inc * (if (angle < 0) -1 else 1) / PI,
        )
    }

    fun phaseOf(phase: Double): Phase {
        val p = ((phase % 1.0) + 1.0) % 1.0
        val tol = 0.5 / SYNODIC_MONTH_DAYS          // ± half a day counts as the exact phase
        return when {
            p < tol || p > 1 - tol -> Phase.NEW
            abs(p - 0.25) < tol -> Phase.FIRST_QUARTER
            abs(p - 0.5) < tol -> Phase.FULL
            abs(p - 0.75) < tol -> Phase.LAST_QUARTER
            p < 0.25 -> Phase.WAXING_CRESCENT
            p < 0.5 -> Phase.WAXING_GIBBOUS
            p < 0.75 -> Phase.WANING_GIBBOUS
            else -> Phase.WANING_CRESCENT
        }
    }

    /** Moonrise and moonset within the 24 hours starting at [dayStartMs] (local midnight). */
    fun times(dayStartMs: Long, lat: Double, lon: Double): Times {
        val hc = 0.133 * RAD
        fun alt(hours: Double) = altitude(dayStartMs + (hours * 3_600_000).toLong(), lat, lon) - hc
        var h0 = alt(0.0)
        var rise: Double? = null
        var set: Double? = null
        var ye = 0.0
        var i = 1
        while (i <= 24) {
            val h1 = alt(i.toDouble())
            val h2 = alt(i + 1.0)
            val a = (h0 + h2) / 2 - h1
            val b = (h2 - h0) / 2
            val xe = -b / (2 * a)
            ye = (a * xe + b) * xe + h1
            val d = b * b - 4 * a * h1
            var roots = 0
            var x1 = 0.0
            var x2 = 0.0
            if (d >= 0) {
                val dx = sqrt(d) / (abs(a) * 2)
                x1 = xe - dx
                x2 = xe + dx
                if (abs(x1) <= 1) roots++
                if (abs(x2) <= 1) roots++
                if (x1 < -1) x1 = x2
            }
            if (roots == 1) {
                if (h0 < 0) rise = i + x1 else set = i + x1
            } else if (roots == 2) {
                rise = i + if (ye < 0) x2 else x1
                set = i + if (ye < 0) x1 else x2
            }
            if (rise != null && set != null) break
            h0 = h2
            i += 2
        }
        fun ms(h: Double?) = h?.let { dayStartMs + (it * 3_600_000).toLong() }
        return Times(
            rise = ms(rise), set = ms(set),
            alwaysUp = rise == null && set == null && ye > 0,
            alwaysDown = rise == null && set == null && ye <= 0,
        )
    }

    // ---- Phase times after Jean Meeus, "Astronomical Algorithms", chapter 49 (accuracy ~minutes) ----

    private fun sinD(deg: Double) = sin(deg * RAD)

    /** Time (epoch ms) of the new moon (fractional part 0.0) or full moon (0.5) with lunation number [k]. */
    fun meeusPhaseTime(k: Double): Long {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val t4 = t3 * t
        val jde = 2451550.09766 + 29.530588861 * k + 0.00015437 * t2 - 0.000000150 * t3 + 0.00000000073 * t4
        val e = 1 - 0.002516 * t - 0.0000074 * t2
        val m = 2.5534 + 29.10535670 * k - 0.0000014 * t2 - 0.00000011 * t3
        val mp = 201.5643 + 385.81693528 * k + 0.0107582 * t2 + 0.00001238 * t3 - 0.000000058 * t4
        val f = 160.7108 + 390.67050284 * k - 0.0016118 * t2 - 0.00000227 * t3 + 0.000000011 * t4
        val om = 124.7746 - 1.56375588 * k + 0.0020672 * t2 + 0.00000215 * t3
        val frac = k - kotlin.math.floor(k)
        val c = when {
            abs(frac - 0.5) < 0.01 -> // full moon
                -0.40614 * sinD(mp) + 0.17302 * e * sinD(m) + 0.01614 * sinD(2 * mp) + 0.01043 * sinD(2 * f) +
                    0.00734 * e * sinD(mp - m) - 0.00515 * e * sinD(mp + m) + 0.00209 * e * e * sinD(2 * m) -
                    0.00111 * sinD(mp - 2 * f) - 0.00057 * sinD(mp + 2 * f) + 0.00056 * e * sinD(2 * mp + m) -
                    0.00042 * sinD(3 * mp) + 0.00042 * e * sinD(m + 2 * f) + 0.00038 * e * sinD(m - 2 * f) -
                    0.00024 * e * sinD(2 * mp - m) - 0.00017 * sinD(om)
            frac < 0.01 || frac > 0.99 -> // new moon
                -0.40720 * sinD(mp) + 0.17241 * e * sinD(m) + 0.01608 * sinD(2 * mp) + 0.01039 * sinD(2 * f) +
                    0.00739 * e * sinD(mp - m) - 0.00514 * e * sinD(mp + m) + 0.00208 * e * e * sinD(2 * m) -
                    0.00111 * sinD(mp - 2 * f) - 0.00057 * sinD(mp + 2 * f) + 0.00056 * e * sinD(2 * mp + m) -
                    0.00042 * sinD(3 * mp) + 0.00042 * e * sinD(m + 2 * f) + 0.00038 * e * sinD(m - 2 * f) -
                    0.00024 * e * sinD(2 * mp - m) - 0.00017 * sinD(om)
            else -> { // first (0.25) or last (0.75) quarter
                val q = -0.62801 * sinD(mp) + 0.17172 * e * sinD(m) - 0.01183 * e * sinD(mp + m) + 0.00862 * sinD(2 * mp) +
                    0.00804 * sinD(2 * f) + 0.00454 * e * sinD(mp - m) + 0.00204 * e * e * sinD(2 * m) -
                    0.00180 * sinD(mp - 2 * f) - 0.00070 * sinD(mp + 2 * f) - 0.00040 * sinD(3 * mp) -
                    0.00034 * e * sinD(2 * mp - m) + 0.00032 * e * sinD(m + 2 * f) + 0.00032 * e * sinD(m - 2 * f) -
                    0.00028 * e * e * sinD(mp + 2 * m) + 0.00027 * e * sinD(2 * mp + m) - 0.00017 * sinD(om)
                val w = 0.00306 - 0.00038 * e * cos(m * RAD) + 0.00026 * cos(mp * RAD) - 0.00002 * cos((mp - m) * RAD) +
                    0.00002 * cos((mp + m) * RAD) + 0.00002 * cos(2 * f * RAD)
                if (frac < 0.5) q + w else q - w
            }
        }
        return ((jde + c - 2440587.5) * DAY_MS).toLong()
    }

    private fun lunationNear(ms: Long): Double =
        kotlin.math.floor((ms / DAY_MS / 365.2425 + 1970.0 - 2000.0) * 12.3685)

    /** Time of the next full moon after [fromMs]. */
    fun nextFullMoon(fromMs: Long): Long {
        var k = lunationNear(fromMs) - 2 + 0.5
        while (true) {
            val t = meeusPhaseTime(k)
            if (t > fromMs) return t
            k += 1.0
        }
    }

    /**
     * Phase 0..1 and illuminated fraction at [ms], anchored to the Meeus new and full moon times
     * (more precise than the low-precision lunar coordinates above).
     */
    fun preciseIllumination(ms: Long): Illumination {
        var k = lunationNear(ms) - 2
        var lastNew = meeusPhaseTime(k)
        while (true) {
            val next = meeusPhaseTime(k + 1)
            if (next > ms) break
            k += 1
            lastNew = next
        }
        // Interpolate between the four principal phases of this lunation.
        val anchors = listOf(0.0, 0.25, 0.5, 0.75, 1.0).map { it to meeusPhaseTime(k + it) }
        val seg = anchors.zipWithNext().first { (_, b) -> ms <= b.second || b.first == 1.0 }
        val (a, b) = seg
        val phase = a.first + 0.25 * (ms - a.second) / (b.second - a.second).toDouble()
        return Illumination((1 - cos(2 * PI * phase)) / 2, phase)
    }
}
