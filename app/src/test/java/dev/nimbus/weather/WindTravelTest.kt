package dev.nimbus.weather

import dev.nimbus.weather.ui.background.WindTravel
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class WindTravelTest {
    /** Same gust model as the background: mean wind × (1 + gustiness · 1.6 · wave²). */
    private fun effWind(t: Double, wind: Float, gustiness: Float): Float {
        val wave = (0.5 + 0.5 * sin(t * 0.9) * sin(t * 0.37 + 1.3)).toFloat()
        return wind * (1f + gustiness * 1.6f * wave * wave)
    }

    @Test
    fun `leaf drift stays continuous after ten minutes with gusts`() {
        val wind = 0.22f          // 13 km/h
        val gustiness = 0.175f    // gusts 20 km/h
        val air = WindTravel()
        var t = 0.0
        val frame = 1.0 / 60
        var lastNew = Double.NaN
        var lastOld = Double.NaN
        var maxNew = 0.0
        var maxOld = 0.0
        while (t < 610.0) {
            val w = effWind(t, wind, gustiness)
            air.advance(t, w)
            // x offset in dp for a mid-depth leaf, new: integrated travel; old: t × current speed
            val xNew = (12.0 * t + 150.0 * air.distance) * 0.9
            val xOld = t * (15.0 + 190.0 * abs(w)) * 0.9
            if (t > 600.0) {
                maxNew = maxOf(maxNew, abs(xNew - lastNew))
                maxOld = maxOf(maxOld, abs(xOld - lastOld))
            }
            lastNew = xNew; lastOld = xOld
            t += frame
        }
        // ~50 dp/s → < 1 dp per frame; the old formula jumped far more
        assertTrue("new max step $maxNew dp", maxNew < 2.0)
        assertTrue("old max step $maxOld dp (demonstrates the bug)", maxOld > 20.0)
    }

    @Test
    fun `no jump after the animation was paused`() {
        val air = WindTravel()
        air.advance(10.0, 0.5f)
        air.advance(10.1, 0.5f)
        val before = air.distance
        air.advance(500.0, 0.5f)          // resumed much later
        assertTrue(air.distance - before <= 0.05 + 1e-9)
    }
}
