package dev.nimbus.weather.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

/** What floats through the air when it is dry. */
enum class Ambient { NONE, BLOSSOMS, SEEDS, LEAVES, FIREFLIES, ICE_CRYSTALS }

private fun Double.wrap(m: Double): Double = this - floor(this / m) * m

/**
 * Leaf and petal sprites, rendered once. Drawing a small bitmap with a transform is far cheaper
 * than re-rasterising a vector path every frame at a new rotation and scale.
 */
internal class AmbientShapes {
    private val size = 96
    /** White leaf with a grey midrib; tinted per leaf with a multiplying colour filter. */
    val leaf: android.graphics.Bitmap = sprite { c, p ->
        val path = android.graphics.Path().apply {
            moveTo(0f, -1f)
            cubicTo(0.75f, -0.55f, 0.7f, 0.45f, 0f, 1f)
            cubicTo(-0.7f, 0.45f, -0.75f, -0.55f, 0f, -1f)
            close()
        }
        c.drawPath(path, p)
        p.color = 0xFF9A9A9A.toInt()
        p.strokeWidth = 0.05f
        p.style = android.graphics.Paint.Style.STROKE
        c.drawLine(0f, -0.85f, 0f, 1.1f, p)
    }
    val petal: android.graphics.Bitmap = sprite { c, p ->
        val path = android.graphics.Path().apply {
            moveTo(0f, -1f)
            cubicTo(0.9f, -0.8f, 0.8f, 0.7f, 0f, 1f)
            cubicTo(-0.8f, 0.7f, -0.9f, -0.8f, 0f, -1f)
            close()
        }
        c.drawPath(path, p)
    }
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
    private val filters = HashMap<Int, android.graphics.ColorFilter>()

    fun tint(argb: Int): android.graphics.ColorFilter =
        filters.getOrPut(argb) { android.graphics.LightingColorFilter(argb and 0xFFFFFF, 0) }

    private fun sprite(draw: (android.graphics.Canvas, android.graphics.Paint) -> Unit): android.graphics.Bitmap {
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.translate(size / 2f, size / 2f)
        c.scale(size / 2.3f, size / 2.3f)
        draw(c, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
        return bmp
    }

    /** Draws [bmp] centred at (x, y), rotated and scaled to [sx]×[sy] pixels. */
    fun draw(canvas: android.graphics.Canvas, bmp: android.graphics.Bitmap, x: Float, y: Float, rot: Float, sx: Float, sy: Float, argb: Int, alpha: Float) {
        paint.colorFilter = tint(argb)
        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rot)
        canvas.scale(sx / (bmp.width / 2.3f), sy / (bmp.height / 2.3f))
        canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, paint)
        canvas.restore()
    }
}

private val autumnEarly = listOf(Color(0xFF9DB94A), Color(0xFFE2B93B), Color(0xFFD98B2B), Color(0xFFB8C24E))
private val autumnPeak = listOf(Color(0xFFE0662A), Color(0xFFC0392B), Color(0xFFE5A93B), Color(0xFFB5651D), Color(0xFFD35400))
private val autumnLate = listOf(Color(0xFF8E5A2B), Color(0xFFA0522D), Color(0xFF6E4B2A), Color(0xFFB07A3E))
private val blossomColors = listOf(Color(0xFFFFFFFF), Color(0xFFFBD3E2), Color(0xFFF6B3CB), Color(0xFFFFE9F0))

/**
 * Draws wind-driven ambient particles. [wind] is the effective (gusting) wind -1..1,
 * [night] 0..1, [autumnProgress] 0 (early September) .. 1 (late November).
 */
internal fun DrawScope.drawAmbient(
    ambient: Ambient, pollen: Float, t: Double, w: Float, h: Float, dp: Float,
    wind: Float, night: Float, autumnProgress: Float, shapes: AmbientShapes,
) {
    val windAbs = abs(wind)
    val dir = if (wind >= 0f) 1f else -1f
    when (ambient) {
        Ambient.LEAVES -> {
            val count = (8 + 26 * windAbs).toInt()
            val palette = when {
                autumnProgress < 0.33f -> autumnEarly
                autumnProgress < 0.7f -> autumnPeak
                else -> autumnLate
            }
            val shade = 1f - 0.65f * night
            for (i in 0 until count) {
                val depth = rnd(i, 61)
                val size = (6f + 7f * depth) * dp
                val fall = (28f + 40f * depth + 30f * windAbs) * dp
                val drift = dir * (15f + 190f * windAbs) * (0.6f + 0.6f * depth) * dp
                val margin = size * 3
                val y = (rnd(i, 62) * (h + margin) + t * fall).wrap((h + margin).toDouble()).toFloat() - margin
                val sway = sin(t * (0.9 + rnd(i, 63)) + i * 1.7).toFloat() * (18f + 16f * depth) * dp
                val x = (rnd(i, 64) * (w + margin * 2) + t * drift + sway).wrap((w + margin * 2).toDouble()).toFloat() - margin
                val spin = (t * (40 + 160 * rnd(i, 65)) * (if (i % 2 == 0) 1 else -1) + 360 * rnd(i, 66)).toFloat()
                val flip = cos(t * (1.5 + 2.5 * rnd(i, 67)) + i).toFloat()          // fake 3D tumbling
                val base = palette[(rnd(i, 68) * palette.size).toInt().coerceIn(0, palette.size - 1)]
                val color = lerp(Color.Black, base, shade)
                drawIntoCanvas { c ->
                    shapes.draw(c.nativeCanvas, shapes.leaf, x, y, spin, size * (0.35f + 0.65f * abs(flip)), size, color.toArgb(), 0.92f)
                }
            }
        }
        Ambient.BLOSSOMS -> {
            val count = (12 + 22 * windAbs).toInt()
            for (i in 0 until count) {
                val depth = rnd(i, 71)
                val size = (3f + 3.5f * depth) * dp
                val fall = (14f + 22f * depth) * dp
                val drift = dir * (12f + 150f * windAbs) * (0.5f + 0.7f * depth) * dp
                val margin = 40f * dp
                val y = (rnd(i, 72) * (h + margin) + t * fall).wrap((h + margin).toDouble()).toFloat() - margin / 2
                val sway = sin(t * (1.1 + rnd(i, 73)) + i).toFloat() * 22f * dp
                val x = (rnd(i, 74) * (w + margin * 2) + t * drift + sway).wrap((w + margin * 2).toDouble()).toFloat() - margin
                val spin = (t * (60 + 120 * rnd(i, 75)) + 360 * rnd(i, 76)).toFloat()
                val flip = sin(t * (2.0 + 2.0 * rnd(i, 77)) + i).toFloat()
                drawIntoCanvas { c ->
                    shapes.draw(
                        c.nativeCanvas, shapes.petal, x, y, spin, size * (0.3f + 0.7f * abs(flip)), size * 0.8f,
                        blossomColors[i % blossomColors.size].toArgb(), 0.9f * (1f - 0.5f * night),
                    )
                }
            }
        }
        Ambient.SEEDS -> {
            val count = (6 + 10 * windAbs).toInt()
            for (i in 0 until count) {
                val depth = rnd(i, 81)
                val r = (5f + 5f * depth) * dp
                val drift = dir * (10f + 120f * windAbs) * (0.5f + 0.6f * depth) * dp
                val margin = 60f * dp
                val bob = sin(t * (0.4 + 0.3 * rnd(i, 82)) + i).toFloat() * 40f * dp
                val y = (rnd(i, 83) * h * 0.85f + t * 6f * dp * (rnd(i, 84) - 0.4f) + bob).toDouble().wrap(h.toDouble()).toFloat()
                val x = (rnd(i, 85) * (w + margin * 2) + t * drift).wrap((w + margin * 2).toDouble()).toFloat() - margin
                val a = 0.75f * (1f - 0.6f * night)
                val tilt = sin(t * 0.8 + i).toFloat() * 0.35f
                // stalk + seed
                val stalkEnd = Offset(x + sin(tilt) * r * 1.6f, y + cos(tilt) * r * 1.6f)
                drawLine(Color.White.copy(alpha = a * 0.7f), Offset(x, y), stalkEnd, 0.8f * dp)
                drawCircle(Color(0xFFD8CFB8).copy(alpha = a), 1.3f * dp, stalkEnd)
                // pappus filaments
                for (k in 0 until 9) {
                    val ang = -PI.toFloat() / 2 + tilt + (k - 4) * 0.33f
                    val end = Offset(x + cos(ang) * r, y + sin(ang) * r)
                    drawLine(Color.White.copy(alpha = a * 0.55f), Offset(x, y), end, 0.6f * dp, StrokeCap.Round)
                    drawCircle(Color.White.copy(alpha = a * 0.5f), 0.9f * dp, end)
                }
            }
        }
        Ambient.FIREFLIES -> {
            for (i in 0 until 22) {
                val cx = rnd(i, 91) * w
                val cy = h * (0.45f + 0.5f * rnd(i, 92))
                val x = cx + sin(t * (0.3 + 0.4 * rnd(i, 93)) + i).toFloat() * 40f * dp + t.toFloat() * wind * 8f * dp % w
                val y = cy + cos(t * (0.25 + 0.35 * rnd(i, 94)) + i * 2).toFloat() * 26f * dp
                val blink = sin(t * (0.8 + 1.2 * rnd(i, 95)) + i * 3).toFloat()
                val a = (blink * 1.6f - 0.4f).coerceIn(0f, 1f) * night
                if (a <= 0.01f) continue
                val p = Offset(((x % w) + w) % w, y)
                drawCircle(Brush.radialGradient(listOf(Color(0x99E6FF7A), Color.Transparent), p, 9f * dp), 9f * dp, p, alpha = a)
                drawCircle(Color(0xFFF6FFB0), 1.6f * dp, p, alpha = a)
            }
        }
        Ambient.ICE_CRYSTALS -> {
            for (i in 0 until 60) {
                val depth = rnd(i, 101)
                val fall = (4f + 10f * depth) * dp
                val y = (rnd(i, 102) * h + t * fall).toDouble().wrap(h.toDouble()).toFloat()
                val x = (rnd(i, 103) * w + t * wind * 30f * dp + sin(t * 0.5 + i).toFloat() * 10f * dp).toDouble().wrap(w.toDouble()).toFloat()
                val tw = sin(t * (1.5 + 3 * rnd(i, 104)) + i * 1.3).toFloat()
                val a = (tw * 1.4f - 0.3f).coerceIn(0f, 1f) * 0.9f
                if (a <= 0.02f) continue
                val r = (1.5f + 3f * depth) * dp
                val p = Offset(x, y)
                drawLine(Color.White.copy(alpha = a), p - Offset(r, 0f), p + Offset(r, 0f), 0.8f * dp, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = a), p - Offset(0f, r), p + Offset(0f, r), 0.8f * dp, StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = a), 0.9f * dp, p)
            }
        }
        Ambient.NONE -> Unit
    }

    // Pollen floats on top of the seasonal layer, amount from real pollen data.
    if (pollen > 0.02f) {
        val count = (10 + 70 * pollen).toInt()
        val color = Color(0xFFFFE9A6)
        for (i in 0 until count) {
            val depth = rnd(i, 111)
            val drift = dir * (6f + 90f * windAbs) * (0.4f + 0.8f * depth) * dp
            val wig = sin(t * (1.3 + rnd(i, 112) * 2) + i).toFloat() * 6f * dp
            val x = (rnd(i, 113) * w + t * drift + wig).toDouble().wrap(w.toDouble()).toFloat()
            val y = (rnd(i, 114) * h + t * (3f + 4f * depth) * dp + cos(t * 0.9 + i).toFloat() * 8f * dp).toDouble().wrap(h.toDouble()).toFloat()
            val a = (0.35f + 0.4f * depth) * (1f - 0.6f * night)
            drawCircle(color, (0.9f + 1.4f * depth) * dp, Offset(x, y), alpha = a)
        }
    }
}
