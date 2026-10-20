/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/LauncherIconTest.kt
 * The launcher icon: sun, cloud and rain on turquoise; the cloud's shadow lies within the cloud, as an
 * even band along its underside.
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

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class LauncherIconTest {
    /** 4 px per unit of the icon's 108 viewport. */
    private val size = 432

    private fun render(vararg ids: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        for (id in ids) ContextCompat.getDrawable(RuntimeEnvironment.getApplication(), id)!!.apply { setBounds(0, 0, size, size) }.draw(c)
        return bmp
    }

    private fun Int.alpha() = (this ushr 24) and 255
    private fun Int.isShadow() = alpha() == 255 && abs(((this shr 16) and 255) - 0xD6) < 6 && abs(((this shr 8) and 255) - 0xF2) < 6 && abs((this and 255) - 0xF0) < 6

    /** Nothing of the foreground lies outside the silhouette (the monochrome icon): no shadow sticking out of the cloud. */
    @Test fun theShadowStaysWithinTheCloud() {
        val fg = render(R.drawable.ic_launcher_foreground)
        val mono = render(R.drawable.ic_launcher_monochrome)
        val outside = (0 until size).flatMap { y -> (0 until size).map { x -> x to y } }
            .filter { (x, y) -> fg.getPixel(x, y).alpha() > 128 && mono.getPixel(x, y).alpha() == 0 }
        assertTrue("foreground outside the silhouette at ${outside.take(10)}", outside.isEmpty())
        render(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground).captureRoboImage("src/test/screenshots/launcher_icon.png")
    }

    /** The shadow along the flat underside is 4 units high, at its left, middle and right alike. */
    @Test fun theShadowIsAnEvenBand() {
        val fg = render(R.drawable.ic_launcher_foreground)
        for (u in listOf(45, 59, 73)) {
            val x = u * 4 + 2
            val rows = (0 until size).count { y -> fg.getPixel(x, y).isShadow() }
            assertEquals("shadow rows at x = $u", 16.0, rows.toDouble(), 2.0)
        }
        // up the bumps on both sides: the shadow follows them (a thin crescent left and right)
        assertTrue((0 until size).any { y -> fg.getPixel(32 * 4, y).isShadow() })
        assertTrue((0 until size).any { y -> fg.getPixel(85 * 4, y).isShadow() })
    }

    private fun hue(c: Int): Float = FloatArray(3).also { android.graphics.Color.colorToHSV(c, it) }[0]

    /** Turquoise all over, no steel blue or violet: the hue of every background pixel lies between green and blue. */
    @Test fun turquoiseBehind() {
        val bg = render(R.drawable.ic_launcher_background)
        for (y in 0 until size step 8) for (x in 0 until size step 8) {
            val h = hue(bg.getPixel(x, y))
            assertTrue("hue $h at ($x, $y)", h in 170f..190f)
        }
        // lightest at the upper left, where the sun stands
        val light = { c: Int -> ((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255) }
        assertTrue(light(bg.getPixel(22 * 4, 22 * 4)) > light(bg.getPixel(86 * 4, 86 * 4)) + 150)
    }

    /** Sun, cloud and rain: a yellow sun at the upper left, the white cloud, three separate drops below it. */
    @Test fun sunCloudAndRain() {
        val fg = render(R.drawable.ic_launcher_foreground)
        fun at(u: Float, v: Float) = fg.getPixel((u * 4).toInt(), (v * 4).toInt())
        val sun = at(32f, 33f)
        assertTrue("sun ${Integer.toHexString(sun)}", sun.alpha() == 255 && hue(sun) in 35f..55f)
        assertEquals(0xFFFFFFFF.toInt(), at(58f, 52f))
        for (x in listOf(45f, 56f, 67f)) {
            assertEquals("drop at $x", 0xFFFFFFFF.toInt(), at(x, if (x == 56f) 81f else 77.5f))
            assertTrue("gap above the drop at $x", at(x, 69.5f).alpha() == 0)
        }
        // between the drops: no rain
        assertTrue(at(50.5f, 78f).alpha() == 0 && at(61.5f, 78f).alpha() == 0)
    }
}
