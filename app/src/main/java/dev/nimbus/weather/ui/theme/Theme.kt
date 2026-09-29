package dev.nimbus.weather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Scheme = darkColorScheme(
    primary = Color(0xFF8CC4FF),
    onPrimary = Color(0xFF002E5B),
    secondary = Color(0xFFB8C8DC),
    background = Color(0xFF0E1726),
    surface = Color(0xFF16233A),
    surfaceVariant = Color(0xFF22324D),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFC8D3E3),
    outline = Color(0x33FFFFFF),
)

private val Base = Typography()

val NimbusTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontSize = 96.sp, fontWeight = FontWeight.Thin, letterSpacing = (-2).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Normal),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Medium),
    bodyLarge = Base.bodyLarge,
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
)

object NimbusColors {
    val CardFill = Color(0x2E0A1A33)
    val CardFillStrong = Color(0x4D0A1A33)
    val CardBorder = Color(0x26FFFFFF)
    val Secondary = Color(0xB3FFFFFF)
    val Tertiary = Color(0x80FFFFFF)
    val Divider = Color(0x33FFFFFF)
}

val CardLabelStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)

@Composable
fun NimbusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = NimbusTypography, content = content)
}
