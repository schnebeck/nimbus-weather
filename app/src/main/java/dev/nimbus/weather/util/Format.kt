package dev.nimbus.weather.util

import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.PrecipitationUnit
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.data.model.WindUnit
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Narrow no-break space (U+202F): typographically correct between number and unit, never wraps. */
const val NBSP = "\u202F"

/** Unit conversion and formatting that does not need Android resources. */
object Units {
    fun temperature(celsius: Double, unit: TemperatureUnit): Double =
        if (unit == TemperatureUnit.FAHRENHEIT) celsius * 9.0 / 5.0 + 32.0 else celsius

    /** "12°" — rounds half away from zero and avoids "-0°". */
    fun temp(celsius: Double?, unit: TemperatureUnit): String {
        if (celsius == null) return "–"
        val v = Math.round(temperature(celsius, unit)).toInt()
        return "$v°"
    }

    fun windValue(kmh: Double, unit: WindUnit): Double = when (unit) {
        WindUnit.KMH -> kmh
        WindUnit.MS -> kmh / 3.6
        WindUnit.MPH -> kmh / 1.609344
        WindUnit.KNOTS -> kmh / 1.852
        WindUnit.BEAUFORT -> beaufort(kmh).toDouble()
    }

    fun beaufort(kmh: Double): Int {
        val limits = doubleArrayOf(1.0, 6.0, 12.0, 20.0, 29.0, 39.0, 50.0, 62.0, 75.0, 89.0, 103.0, 118.0)
        val idx = limits.indexOfFirst { kmh < it }
        return if (idx == -1) 12 else idx
    }

    fun windNumber(kmh: Double?, unit: WindUnit): String {
        if (kmh == null) return "–"
        val v = windValue(kmh, unit)
        return if (unit == WindUnit.MS && v < 10) String.format(Locale.getDefault(), "%.1f", v) else v.roundToInt().toString()
    }

    fun precipitationValue(mm: Double, unit: PrecipitationUnit): Double =
        if (unit == PrecipitationUnit.INCH) mm / 25.4 else mm

    fun precipitationNumber(mm: Double?, unit: PrecipitationUnit): String {
        if (mm == null) return "–"
        val v = precipitationValue(mm, unit)
        return when {
            unit == PrecipitationUnit.INCH -> String.format(Locale.getDefault(), "%.2f", v)
            v == 0.0 -> "0"
            v < 10 -> String.format(Locale.getDefault(), "%.1f", v)
            else -> v.roundToInt().toString()
        }
    }

    /** 0..7 index for N, NE, E, SE, S, SW, W, NW. */
    fun compassIndex(degrees: Double): Int = (((degrees % 360 + 360) % 360 + 22.5) / 45.0).toInt() % 8

    fun visibilityKm(meters: Double?): String {
        if (meters == null) return "–"
        val km = meters / 1000.0
        return if (km < 10) String.format(Locale.getDefault(), "%.1f\u202Fkm", km) else "${km.roundToInt()}${NBSP}km"
    }

    fun oneDecimal(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)
}

/** Time formatting in the time zone of the displayed place. */
class TimeFormat(timezone: String, private val use24h: Boolean, private val locale: Locale = Locale.getDefault()) {
    val zone: ZoneId = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
    private val hourFmt = DateTimeFormatter.ofPattern(if (use24h) "HH" else "h a", locale)
    private val timeFmt = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", locale)

    fun zoned(epochMillis: Long): ZonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
    // "5:00 AM" must never wrap between the time and AM/PM.
    fun hour(epochMillis: Long): String = hourFmt.format(zoned(epochMillis)).replace(' ', '\u202F')
    fun time(epochMillis: Long): String = timeFmt.format(zoned(epochMillis)).replace(' ', '\u202F')
    fun weekdayShort(epochMillis: Long): String =
        zoned(epochMillis).dayOfWeek.getDisplayName(TextStyle.SHORT, locale).trimEnd('.')
    fun dayMonth(epochMillis: Long): String = DateTimeFormatter
        .ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
        .format(zoned(epochMillis)).replace(' ', '\u00A0')
    fun weekdayLong(epochMillis: Long): String = zoned(epochMillis).dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    fun isSameDay(a: Long, b: Long): Boolean = zoned(a).toLocalDate() == zoned(b).toLocalDate()
}

object Texts {
    fun condition(c: Condition, isDay: Boolean): Int = when (c) {
        Condition.CLEAR -> if (isDay) R.string.cond_sunny else R.string.cond_clear
        Condition.MOSTLY_CLEAR -> if (isDay) R.string.cond_mostly_sunny else R.string.cond_mostly_clear
        Condition.PARTLY_CLOUDY -> R.string.cond_partly_cloudy
        Condition.CLOUDY -> R.string.cond_cloudy
        Condition.FOG -> R.string.cond_fog
        Condition.DRIZZLE -> R.string.cond_drizzle
        Condition.RAIN -> R.string.cond_rain
        Condition.HEAVY_RAIN -> R.string.cond_heavy_rain
        Condition.FREEZING_RAIN -> R.string.cond_freezing_rain
        Condition.SLEET -> R.string.cond_sleet
        Condition.SNOW -> R.string.cond_snow
        Condition.HEAVY_SNOW -> R.string.cond_heavy_snow
        Condition.SHOWERS -> R.string.cond_showers
        Condition.THUNDERSTORM -> R.string.cond_thunderstorm
    }

    val compass = intArrayOf(
        R.string.dir_n, R.string.dir_ne, R.string.dir_e, R.string.dir_se,
        R.string.dir_s, R.string.dir_sw, R.string.dir_w, R.string.dir_nw,
    )

    fun windUnit(u: WindUnit): Int = when (u) {
        WindUnit.KMH -> R.string.unit_kmh
        WindUnit.MS -> R.string.unit_ms
        WindUnit.MPH -> R.string.unit_mph
        WindUnit.KNOTS -> R.string.unit_kn
        WindUnit.BEAUFORT -> R.string.unit_bft
    }

    fun precipUnit(u: PrecipitationUnit): Int = if (u == PrecipitationUnit.INCH) R.string.unit_inch else R.string.unit_mm

    fun uvLevel(uv: Double): Int = when {
        uv < 3 -> R.string.uv_low
        uv < 6 -> R.string.uv_moderate
        uv < 8 -> R.string.uv_high
        uv < 11 -> R.string.uv_very_high
        else -> R.string.uv_extreme
    }

    fun aqiLevel(aqi: Double): Int = when {
        aqi <= 20 -> R.string.aqi_good
        aqi <= 40 -> R.string.aqi_fair
        aqi <= 60 -> R.string.aqi_moderate
        aqi <= 80 -> R.string.aqi_poor
        aqi <= 100 -> R.string.aqi_very_poor
        else -> R.string.aqi_extremely_poor
    }
}

fun Settings.effectiveTemp(c: Double?) = Units.temp(c, temperatureUnit)
