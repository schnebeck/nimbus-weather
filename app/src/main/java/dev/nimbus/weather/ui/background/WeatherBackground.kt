/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/background/WeatherBackground.kt
 * The animated weather background: sky, sun, moon, clouds, rain, snow, lightning.
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

package dev.nimbus.weather.ui.background

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.provider.Settings as SystemSettings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.ui.components.drawMoonPhase
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Deterministic pseudo random number in [0, 1) for particle [i]. */
internal fun rnd(i: Int, salt: Int): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777216f
}

private fun Double.fmod(m: Double): Double = this - floor(this / m) * m

/** Soft cumulus sprites rendered once and tinted/scaled while drawing. */
private object CloudSprites {
    private var cache: List<ImageBitmap>? = null

    fun get(): List<ImageBitmap> = cache ?: (0 until 4).map { make(it) }.also { cache = it }

    const val W = 512
    const val H = 320

    private fun make(seed: Int): ImageBitmap {
        val w = W
        val h = H
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = AndroidCanvas(bmp)
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
        val puffs = 10 + seed * 2
        for (i in 0 until puffs) {
            val fx = 0.2f + 0.6f * rnd(i, seed * 31 + 1)
            // Puffs in the middle are taller: gives a cumulus silhouette with a flatter base.
            val centerBias = 1f - kotlin.math.abs(fx - 0.5f) * 1.6f
            var r = (38f + 62f * rnd(i, seed * 31 + 2)) * (0.6f + 0.6f * centerBias)
            val cx = w * fx
            val cy = h * 0.66f - r * 0.5f * (0.4f + centerBias) + 10f * rnd(i, seed * 31 + 3)
            // Keep every gradient inside the bitmap, otherwise edges get cut off hard.
            r = minOf(r, cx - 2f, w - cx - 2f, cy - 2f, h - cy - 2f)
            if (r < 8f) continue
            paint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(0xF2FFFFFF.toInt(), 0xB3FFFFFF.toInt(), 0x00FFFFFF),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            c.drawCircle(cx, cy, r, paint)
        }
        return bmp.asImageBitmap()
    }
}

private class CloudSpec(val x: Float, val y: Float, val scale: Float, val speed: Float, val sprite: Int, val alpha: Float, val front: Boolean)

private fun cloudSpecs(condition: Condition): List<CloudSpec> {
    val count = when (condition) {
        Condition.CLEAR -> 0
        Condition.MOSTLY_CLEAR -> 3
        Condition.PARTLY_CLOUDY -> 6
        Condition.FOG -> 5
        Condition.CLOUDY -> 12
        Condition.DRIZZLE, Condition.SHOWERS -> 10
        Condition.SNOW, Condition.SLEET -> 10
        else -> 14
    }
    val spread = if (condition == Condition.MOSTLY_CLEAR || condition == Condition.PARTLY_CLOUDY) 0.34f else 0.5f
    return (0 until count).map { i ->
        val front = i % 3 == 2
        CloudSpec(
            x = rnd(i, 11),
            y = -0.04f + spread * rnd(i, 12) * (if (front) 1.1f else 0.85f),
            scale = (if (front) 1.3f else 0.85f) + 0.8f * rnd(i, 13),
            speed = (if (front) 9f else 5f) + 6f * rnd(i, 14),
            sprite = (rnd(i, 15) * 4).toInt().coerceIn(0, 3),
            alpha = (if (front) 0.95f else 0.75f) - 0.25f * rnd(i, 16),
            front = front,
        )
    }
}

private fun cloudColor(condition: Condition, night: Boolean): Color = if (!night) when (condition) {
    Condition.MOSTLY_CLEAR, Condition.PARTLY_CLOUDY -> Color(0xFFFFFFFF)
    Condition.CLOUDY, Condition.FOG -> Color(0xFFD5DCE4)
    Condition.SNOW, Condition.HEAVY_SNOW -> Color(0xFFE2E8EF)
    Condition.DRIZZLE, Condition.SHOWERS -> Color(0xFFA9B4C0)
    Condition.THUNDERSTORM -> Color(0xFF5C6470)
    else -> Color(0xFF8A96A3)
} else when (condition) {
    Condition.MOSTLY_CLEAR, Condition.PARTLY_CLOUDY -> Color(0xFF6A7690)
    Condition.CLOUDY, Condition.FOG, Condition.SNOW, Condition.HEAVY_SNOW -> Color(0xFF4E5868)
    Condition.THUNDERSTORM -> Color(0xFF2A2F38)
    else -> Color(0xFF3B4452)
}

private data class PrecipSpec(val count: Int, val lengthDp: Float, val speed: Float, val widthDp: Float, val alpha: Float)

private fun rainSpec(c: Condition): PrecipSpec? = when (c) {
    Condition.DRIZZLE -> PrecipSpec(110, 9f, 0.75f, 1f, 0.35f)
    Condition.SHOWERS -> PrecipSpec(170, 20f, 1.25f, 1.3f, 0.42f)
    Condition.RAIN, Condition.FREEZING_RAIN -> PrecipSpec(220, 22f, 1.35f, 1.3f, 0.45f)
    Condition.SLEET -> PrecipSpec(120, 14f, 1.1f, 1.2f, 0.4f)
    Condition.HEAVY_RAIN -> PrecipSpec(380, 30f, 1.7f, 1.5f, 0.5f)
    Condition.THUNDERSTORM -> PrecipSpec(300, 28f, 1.6f, 1.4f, 0.48f)
    else -> null
}

private fun snowCount(c: Condition): Int = when (c) {
    Condition.SNOW -> 150
    Condition.HEAVY_SNOW -> 280
    Condition.SLEET -> 60
    else -> 0
}

/** Time of the last touch; the sky animation slows down when the app is left open unused. */
object UserActivity {
    @Volatile var lastInteraction = System.currentTimeMillis()
        private set

    fun touch() { lastInteraction = System.currentTimeMillis() }
}

private const val IDLE_AFTER_MS = 60_000L

/** Battery saver switched on in the system: no animation (Android's own guideline). */
@Composable
private fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val pm = remember { context.getSystemService(android.os.PowerManager::class.java) }
    var on by remember { mutableStateOf(pm?.isPowerSaveMode == true) }
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) { on = pm?.isPowerSaveMode == true }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return on
}

/**
 * Full-screen animated sky for the given [scene]. All animation is computed from a single
 * frame clock inside the draw phase, so no recomposition happens per frame.
 */
@Composable
fun WeatherBackground(scene: SkyScene, animate: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val systemAnimations = remember {
        SystemSettings.Global.getFloat(context.contentResolver, SystemSettings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val powerSave = rememberPowerSaveMode()
    val running = animate && systemAnimations && !powerSave
    val clock = remember { mutableDoubleStateOf(12.0) }
    // Fast particles (rain, snow, lightning) need a smooth picture; drifting clouds, stars and
    // leaves look the same at 30 fps and cost half (or, on 120 Hz screens, a quarter) of the GPU
    // work. Nobody touching the phone for a while: 15 fps.
    val fast = rainSpec(scene.condition) != null || snowCount(scene.condition) > 0
    LaunchedEffect(running, fast) {
        if (!running) return@LaunchedEffect
        val base = clock.doubleValue
        val start = withFrameNanos { it }
        while (true) {
            val idle = System.currentTimeMillis() - UserActivity.lastInteraction > IDLE_AFTER_MS
            val interval = when {
                idle -> 1000L / 15
                fast -> 1000L / 60
                else -> 1000L / 30
            }
            // Sleep until shortly before the next wanted frame; the frame clock aligns it to vsync.
            if (interval > 17) kotlinx.coroutines.delay(interval - 12)
            withFrameNanos { clock.doubleValue = base + (it - start) / 1_000_000_000.0 }
        }
    }

    val colors = scene.skyColors
    // Luminance of the upper sky and of what the clouds add – bright scenes get a header scrim.
    val brightness = scene.brightness
    val scrim = ((brightness - 0.22f) * 0.9f).coerceIn(0f, 0.28f)
    val c0 by animateColorAsState(colors[0], tween(900), label = "sky0")
    val c1 by animateColorAsState(colors[1], tween(900), label = "sky1")
    val c2 by animateColorAsState(colors[2], tween(900), label = "sky2")
    val night by animateFloatAsState(1f - scene.daylight, tween(900), label = "night")
    val condition = scene.condition
    val clouds = remember(condition) { cloudSpecs(condition) }
    val cloudTint = cloudColor(condition, scene.isNight)
    val rain = rainSpec(condition)
    val snow = snowCount(condition)
    val moonPhase = scene.moonPhase
    val moonUp = scene.moonUp
    val southern = scene.southern
    val sunVisible = condition in setOf(Condition.CLEAR, Condition.MOSTLY_CLEAR, Condition.PARTLY_CLOUDY)
    val starAlpha = (1f - scene.cloudiness * 1.1f).coerceIn(0f, 1f)
    val wind = scene.wind
    val gustiness = scene.gustiness
    val air = remember { WindTravel() }
    val ambient = scene.ambient
    val pollen = scene.visiblePollen

    Spacer(
        // Own graphics layer: the per-frame invalidation re-records only the sky,
        // not the cards and texts that are drawn on top of it.
        modifier.fillMaxSize().graphicsLayer().drawWithCache {
            val w = size.width
            val h = size.height
            val dp = density
            val sprites = CloudSprites.get()
            val skyBrush = Brush.verticalGradient(0f to c0, 0.55f to c1, 1f to c2)
            // Stars in four twinkle groups, drawn as points.
            val starGroups = (0 until 4).map { g ->
                (0 until 45).map { i ->
                    val idx = g * 1000 + i
                    Offset(rnd(idx, 1) * w, rnd(idx, 2) * rnd(idx, 3) * h * 0.75f)
                }
            }
            // Top right, clear of the centred city name and the big temperature.
            // Below the top bar (menu / radar button), right of the big temperature.
            val sunCenter = Offset(w * 0.87f, h * 0.165f)
            val sunR = 28f * dp
            val moonR = 22f * dp
            val moonCenter = Offset(w * 0.87f, h * 0.165f)
            val ambientShapes = AmbientShapes()
            val particles = ParticleBuffers()
            val boltPath = Path()

            onDrawBehind {
                val t = clock.doubleValue
                // Gusts: a slowly varying, occasionally peaking multiplier on top of the mean wind.
                val gustWave = (0.5 + 0.5 * sin(t * 0.9) * sin(t * 0.37 + 1.3)).toFloat()
                val gust = 1f + gustiness * 1.6f * gustWave * gustWave
                val effWind = (wind * gust).coerceIn(-1.4f, 1.4f)
                air.advance(t, effWind)
                drawRect(skyBrush)

                // --- stars & moon (night) ---
                if (night > 0.02f) {
                    if (starAlpha > 0f) {
                        starGroups.forEachIndexed { g, pts ->
                            val tw = 0.55f + 0.45f * sin((t * (0.7 + g * 0.35) + g * 1.7)).toFloat()
                            drawPoints(
                                pts, PointMode.Points, Color.White.copy(alpha = night * starAlpha * tw * (0.55f + 0.12f * g)),
                                strokeWidth = (1.1f + 0.45f * g) * dp, cap = StrokeCap.Round,
                            )
                        }
                        drawShootingStar(t, w, h, dp, night * starAlpha)
                    }
                    if (sunVisible && moonUp) {
                        // Glow scales with the lit fraction: bright at full moon, faint for a thin crescent.
                        val lit = (1f - kotlin.math.cos(2f * PI.toFloat() * moonPhase)) / 2f
                        drawCircle(
                            Brush.radialGradient(listOf(Color(0x40DDE6FF), Color.Transparent), moonCenter, moonR * 4f),
                            moonR * 4f, moonCenter, alpha = night * (0.25f + 0.75f * lit),
                        )
                        drawMoonPhase(moonCenter, moonR, moonPhase, southern, alpha = night, darkSide = Color(0x1AFFFFFF))
                    }
                }

                // --- sun (day) ---
                if (sunVisible && night < 0.98f) {
                    val dayA = 1f - night
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0x66FFF3C4), Color(0x22FFE9A8), Color.Transparent), sunCenter, w * 0.55f),
                        w * 0.55f, sunCenter, alpha = dayA,
                    )
                    rotate((t * 3.0 % 360).toFloat(), sunCenter) {
                        for (i in 0 until 12) {
                            val a = (i / 12f) * 2f * PI.toFloat()
                            val len = sunR * (3.2f + 0.8f * sin(t * 0.6 + i).toFloat())
                            val from = sunCenter + Offset(kotlin.math.cos(a), sin(a)) * (sunR * 1.25f)
                            val to = sunCenter + Offset(kotlin.math.cos(a), sin(a)) * len
                            drawLine(
                                Brush.linearGradient(listOf(Color(0xFFFFF3C4).copy(alpha = 0.16f * dayA), Color.Transparent), from, to),
                                from, to, strokeWidth = 7f * dp, cap = StrokeCap.Round,
                            )
                        }
                    }
                    drawCircle(Color(0xFFFFF8E1), sunR, sunCenter, alpha = dayA)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0x00FFF3C4)), sunCenter, sunR * 1.6f), sunR * 1.6f, sunCenter, alpha = 0.6f * dayA)
                }

                // --- overcast base layer ---
                if (scene.cloudiness >= 0.85f) {
                    drawRect(
                        Brush.verticalGradient(0f to cloudTint.copy(alpha = 0.55f), 0.45f to cloudTint.copy(alpha = 0.12f), 0.7f to Color.Transparent),
                    )
                }

                // --- clouds (back layer first) ---
                val filter = ColorFilter.tint(cloudTint, BlendMode.Modulate)
                for (pass in 0..1) {
                    for (cs in clouds) {
                        if (cs.front != (pass == 1)) continue
                        val cw = w * 0.9f * cs.scale
                        val ch = cw * CloudSprites.H / CloudSprites.W
                        val dir = if (wind >= 0f) 1f else -1f
                        val speed = cs.speed * dp * (0.4f + 2.6f * kotlin.math.abs(wind))
                        val travel = w + cw
                        val x = ((cs.x * travel + dir * t * speed).fmod(travel.toDouble())).toFloat() - cw
                        drawImage(
                            sprites[cs.sprite],
                            srcOffset = IntOffset.Zero, srcSize = IntSize(CloudSprites.W, CloudSprites.H),
                            dstOffset = IntOffset(x.toInt(), (cs.y * h).toInt()),
                            dstSize = IntSize(cw.toInt(), ch.toInt()),
                            alpha = cs.alpha, colorFilter = filter,
                        )
                    }
                }

                // --- seasonal particles & pollen (dry weather only) ---
                if (ambient != Ambient.NONE || pollen > 0f) {
                    drawAmbient(ambient, pollen, t, w, h, dp, wind, night, scene.autumnProgress, ambientShapes, air)
                }

                // --- lightning ---
                if (condition == Condition.THUNDERSTORM) drawLightning(t, w, h, dp, boltPath)

                // --- fog ---
                if (condition == Condition.FOG) {
                    for (i in 0 until 4) {
                        val bw = w * 2.6f
                        val bh = h * 0.32f
                        val x = ((i * 0.37 * bw + t * (6 + i * 3) * dp).fmod(bw.toDouble())).toFloat() - bw * 0.5f
                        drawImage(
                            sprites[i % 4], IntOffset.Zero, IntSize(CloudSprites.W, CloudSprites.H),
                            IntOffset(x.toInt() - (bw * 0.5f).toInt(), (h * (0.28f + i * 0.16f)).toInt()),
                            IntSize(bw.toInt(), bh.toInt()), alpha = 0.42f,
                            colorFilter = ColorFilter.tint(if (night > 0.5f) Color(0xFF6B7482) else Color(0xFFE6EAEE), BlendMode.Modulate),
                        )
                    }
                }

                // --- rain ---
                if (rain != null) drawRain(rain, t, w, h, dp, effWind, particles)

                // --- snow ---
                if (snow > 0) drawSnow(snow, t, w, h, dp, effWind, particles)

                // --- readability: darken the header area on bright skies (snow, fog, overcast day) ---
                if (scrim > 0f) {
                    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = scrim), 0.42f to Color.Black.copy(alpha = scrim * 0.55f), 0.75f to Color.Transparent))
                }
            }
        },
    )
}

/** Reused arrays and paints so the particle systems allocate nothing per frame. */
internal class ParticleBuffers {
    val far = FloatArray(4 * 400)
    val near = FloatArray(4 * 400)
    val snow = Array(3) { FloatArray(2 * 300) }
    val linePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
    }
    val pointPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        color = android.graphics.Color.WHITE
    }
}

private fun DrawScope.drawRain(spec: PrecipSpec, t: Double, w: Float, h: Float, dp: Float, wind: Float, b: ParticleBuffers) {
    val slant = 0.06f + 0.5f * kotlin.math.abs(wind)
    val dirX = if (wind >= 0f) slant else -slant
    var nf = 0
    var nn = 0
    for (i in 0 until spec.count.coerceAtMost(400)) {
        val isNear = i % 3 == 0
        val len = spec.lengthDp * dp * (if (isNear) 1.25f else 0.8f) * (0.8f + 0.4f * rnd(i, 21))
        val v = h * spec.speed * (if (isNear) 1.15f else 0.8f) * (0.85f + 0.3f * rnd(i, 22))
        val total = h + len * 2
        val y = ((rnd(i, 23) * total + t * v).fmod(total.toDouble())).toFloat() - len
        // Drops start further left/right so that slanted rain covers the whole screen.
        val x0 = rnd(i, 24) * (w + h * slant) - (if (dirX > 0) h * slant else 0f)
        val x = x0 + y * dirX
        val arr = if (isNear) b.near else b.far
        val k = if (isNear) nn++ * 4 else nf++ * 4
        arr[k] = x; arr[k + 1] = y; arr[k + 2] = x + len * dirX; arr[k + 3] = y + len
    }
    val base = 0xD7E3F0
    drawIntoCanvas { c ->
        val p = b.linePaint
        p.color = ((spec.alpha * 0.6f * 255).toInt() shl 24) or base
        p.strokeWidth = spec.widthDp * dp * 0.8f
        c.nativeCanvas.drawLines(b.far, 0, nf * 4, p)
        p.color = ((spec.alpha * 255).toInt() shl 24) or base
        p.strokeWidth = spec.widthDp * dp * 1.2f
        c.nativeCanvas.drawLines(b.near, 0, nn * 4, p)
    }
}

private fun DrawScope.drawSnow(count: Int, t: Double, w: Float, h: Float, dp: Float, wind: Float, b: ParticleBuffers) {
    // Three depth layers, each drawn with one drawPoints call.
    val n = IntArray(3)
    for (i in 0 until count.coerceAtMost(900)) {
        val layer = i % 3
        val depth = (layer + rnd(i, 31)) / 3f
        val r = (1.2f + 2.8f * depth) * dp
        val v = (22f + 55f * depth) * dp
        val total = h + r * 4
        val y = ((rnd(i, 32) * total + t * v).fmod(total.toDouble())).toFloat() - r * 2
        val sway = sin(t * (0.6 + rnd(i, 33)) + i).toFloat() * (8f + 14f * depth) * dp
        val drift = wind * y * (0.35f + 0.5f * (1f - depth))
        val x = ((rnd(i, 34) * w + sway + drift).toDouble().fmod(w.toDouble() + 20 * dp)).toFloat() - 10 * dp
        val arr = b.snow[layer]
        val k = n[layer]++ * 2
        if (k + 1 < arr.size) { arr[k] = x; arr[k + 1] = y } else n[layer]--
    }
    drawIntoCanvas { c ->
        for (layer in 0..2) {
            val depth = (layer + 0.5f) / 3f
            b.pointPaint.strokeWidth = 2 * (1.2f + 2.8f * depth) * dp
            b.pointPaint.alpha = ((0.45f + 0.45f * depth) * 255).toInt()
            c.nativeCanvas.drawPoints(b.snow[layer], 0, n[layer] * 2, b.pointPaint)
        }
    }
}

private fun DrawScope.drawLightning(t: Double, w: Float, h: Float, dp: Float, bolt: Path) {
    val cycle = 6.5
    val n = floor(t / cycle).toInt()
    val offset = 1.0 + rnd(n, 41) * 4.0
    val local = t - n * cycle - offset
    if (local < 0 || local > 0.7) return
    // Double flash with exponential decay.
    val f1 = if (local < 0.08) local / 0.08 else kotlin.math.exp(-(local - 0.08) * 14)
    val f2 = if (local in 0.14..0.7) 0.8 * kotlin.math.exp(-(local - 0.14) * 9) else 0.0
    val flash = max(f1, f2).toFloat().coerceIn(0f, 1f)
    drawRect(Color(0xFFE6ECFF), alpha = 0.32f * flash)
    if (local < 0.3 && rnd(n, 42) > 0.3f) {
        bolt.reset()
        var x = w * (0.2f + 0.6f * rnd(n, 43))
        var y = h * 0.08f
        bolt.moveTo(x, y)
        var k = 0
        while (y < h * (0.45f + 0.2f * rnd(n, 44))) {
            x += (rnd(n * 97 + k, 45) - 0.5f) * 46f * dp
            y += (14f + 22f * rnd(n * 97 + k, 46)) * dp
            bolt.lineTo(x, y)
            k++
        }
        val a = min(1f, flash * 1.4f)
        drawPath(bolt, Color(0x668FA8FF), alpha = a, style = Stroke(width = 7f * dp, cap = StrokeCap.Round))
        drawPath(bolt, Color.White, alpha = a, style = Stroke(width = 2.2f * dp, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawShootingStar(t: Double, w: Float, h: Float, dp: Float, alpha: Float) {
    val cycle = 17.0
    val n = floor(t / cycle).toInt()
    val local = t - n * cycle - rnd(n, 51) * 10
    if (local < 0 || local > 0.9) return
    val p = (local / 0.9).toFloat()
    val start = Offset(w * (0.1f + 0.5f * rnd(n, 52)), h * (0.05f + 0.2f * rnd(n, 53)))
    val dir = Offset(1f, 0.38f)
    val head = start + dir * (p * w * 0.45f)
    val tail = head - dir * (70f * dp)
    val a = alpha * sin(p * PI).toFloat()
    drawLine(
        Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = a)), tail, head),
        tail, head, strokeWidth = 1.6f * dp, cap = StrokeCap.Round,
    )
}
