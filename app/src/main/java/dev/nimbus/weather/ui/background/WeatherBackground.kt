/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/background/WeatherBackground.kt
 * The animated weather background: the sky with sun and moon and the frame clock – clouds and
 * particles drawn on it.
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.ui.components.drawMoonPhase
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Deterministic pseudo random number in [0, 1) for particle [i]. */
internal fun rnd(i: Int, salt: Int): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777216f
}

internal fun Double.fmod(m: Double): Double = this - floor(this / m) * m

/** Time of the last touch; the sky animation slows down when the app is left open unused. */
object UserActivity {
    @Volatile var lastInteraction = System.currentTimeMillis()
        private set

    fun touch() { lastInteraction = System.currentTimeMillis() }
}

private const val IDLE_AFTER_MS = 60_000L

/** Battery saver switched on in the system: no animation (Android's own guideline). */
/**
 * Whether the system's animations are on (the animator duration scale above 0) – followed while
 * shown: switched off in the developer settings or by accessibility, the sky stands at once.
 */
@Composable
private fun rememberSystemAnimations(): Boolean {
    val context = LocalContext.current
    fun read() = SystemSettings.Global.getFloat(context.contentResolver, SystemSettings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var on by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { on = read() }
        }
        context.contentResolver.registerContentObserver(SystemSettings.Global.getUriFor(SystemSettings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return on
}

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
 * frame clock inside the draw phase, so no recomposition happens per frame. [animate]: the page
 * is the one shown (else its sky stands, as it is, until it is). [motion]: moving pictures are
 * wanted at all – off in the settings, by the system's animations or its battery saver the sky is
 * still: without rain, snow, lightning, shooting stars, leaves and pollen, which only make sense
 * moving (a heavy shower frozen in the air looked like a fault).
 */
@Composable
fun WeatherBackground(scene: SkyScene, animate: Boolean, modifier: Modifier = Modifier, motion: Boolean = true) {
    val systemAnimations = rememberSystemAnimations()
    val powerSave = rememberPowerSaveMode()
    val moving = motion && systemAnimations && !powerSave
    val running = animate && moving
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
    val sunVisible = SkyScene.sunVisible(condition)
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
                        if (moving) drawShootingStar(t, w, h, dp, night * starAlpha)
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
                if (moving && (ambient != Ambient.NONE || pollen > 0f)) {
                    drawAmbient(ambient, pollen, t, w, h, dp, wind, night, scene.autumnProgress, ambientShapes, air)
                }

                // --- lightning ---
                if (moving && condition == Condition.THUNDERSTORM) drawLightning(t, w, h, dp, boltPath)

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
                if (moving && rain != null) drawRain(rain, t, w, h, dp, effWind, particles)

                // --- snow ---
                if (moving && snow > 0) drawSnow(snow, t, w, h, dp, effWind, particles)

                // --- readability: darken the header area on bright skies (snow, fog, overcast day) ---
                if (scrim > 0f) {
                    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = scrim), 0.42f to Color.Black.copy(alpha = scrim * 0.55f), 0.75f to Color.Transparent))
                }
            }
        },
    )
}
