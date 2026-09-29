package dev.nimbus.weather.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.Units

/**
 * Max/min temperature stacked like on a weather station display: labels in one column,
 * right-aligned values with unit in the other.
 */
@Composable
fun MaxMinStack(
    maxC: Double?, minC: Double?, unit: TemperatureUnit,
    modifier: Modifier = Modifier, fontSize: TextUnit = 18.sp, shadow: Shadow? = null,
) {
    val lineH = with(LocalDensity.current) { (fontSize * 1.3f).toDp() }
    val labelStyle = TextStyle(fontSize = fontSize * 0.62f, color = NimbusColors.Secondary, fontWeight = FontWeight.Medium, shadow = shadow)
    val valueStyle = TextStyle(fontSize = fontSize, color = Color.White, fontWeight = FontWeight.Medium, shadow = shadow)
    val rows = listOf(stringResource(R.string.max_label) to maxC, stringResource(R.string.min_label) to minC)
    Row(modifier) {
        Column {
            rows.forEach { (label, _) ->
                Box(Modifier.height(lineH), contentAlignment = Alignment.CenterStart) { Text(label.uppercase(), style = labelStyle) }
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            rows.forEach { (_, v) ->
                Box(Modifier.height(lineH), contentAlignment = Alignment.CenterEnd) { Text(Units.tempFull(v, unit), style = valueStyle) }
            }
        }
    }
}

/** Large temperature with the unit set smaller and raised, e.g. "20 °C". */
@Composable
fun BigTemperature(celsius: Double?, unit: TemperatureUnit, fontSize: TextUnit, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Thin, shadow: Shadow? = null) {
    val number = Units.tempNumber(celsius, unit)
    val size = if (number.length >= 3) fontSize * 0.82f else fontSize
    Row(modifier) {
        Text(number, style = TextStyle(fontSize = size, fontWeight = weight, color = Color.White, lineHeight = size * 1.04f, shadow = shadow))
        Text(
            Units.tempUnit(unit),
            style = TextStyle(fontSize = size * 0.36f, fontWeight = FontWeight.Light, color = Color.White, shadow = shadow),
            modifier = Modifier.padding(start = 2.dp, top = with(LocalDensity.current) { (size * 0.16f).toDp() }),
        )
    }
}
