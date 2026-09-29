package dev.nimbus.weather.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Draws the moon with its real phase. [phase]: 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter.
 * On the northern hemisphere the waxing moon is lit on the right; [southern] mirrors it.
 */
fun DrawScope.drawMoonPhase(center: Offset, r: Float, phase: Float, southern: Boolean, alpha: Float = 1f, darkSide: Color = Color(0x33FFFFFF)) {
    drawCircle(darkSide, r, center, alpha = alpha)
    val p = ((phase % 1f) + 1f) % 1f
    val c = cos(2 * PI * p).toFloat()
    val waxing = p < 0.5f
    val path = Path()
    val steps = 48
    // Limb (outer edge) of the lit side from top to bottom …
    for (i in 0..steps) {
        val y = -r + 2 * r * i / steps
        val limb = sqrt((r * r - y * y).coerceAtLeast(0f))
        val x = if (waxing) limb else -limb
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    // … and back along the terminator (an ellipse) from bottom to top.
    for (i in steps downTo 0) {
        val y = -r + 2 * r * i / steps
        val limb = sqrt((r * r - y * y).coerceAtLeast(0f))
        val x = if (waxing) limb * c else -limb * c
        path.lineTo(x, y)
    }
    path.close()
    val mirror = if (southern) -1f else 1f
    val brush = Brush.radialGradient(listOf(Color(0xFFFBF8EC), Color(0xFFE4DFCB)), Offset.Zero, r)
    drawContext.canvas.save()
    drawContext.canvas.translate(center.x, center.y)
    drawContext.canvas.scale(mirror, 1f)
    drawPath(path, brush, alpha = alpha)
    drawContext.canvas.restore()
}
