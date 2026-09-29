package dev.nimbus.weather.ui.main

import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.WeatherData
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.ui.unit.sp

/**
 * Short text forecast for the next hours, derived from the hourly model data. Kept free of
 * Android types; [OutlookCard] turns it into localised sentences.
 */
data class Outlook(
    val now: Condition,
    /** Next change to a different kind of weather within the window. */
    val change: Pair<Condition, Long>?,
    val temperature: Temp,
    val precipitation: Precip,
    /** Strongest gust in km/h if it is notable (≥ 50 km/h). */
    val gust: Double?,
    /** Outlook for tomorrow, only in the afternoon and evening. */
    val tomorrow: DailyPoint?,
) {
    sealed interface Temp {
        data class Rise(val to: Double, val at: Long) : Temp
        data class Fall(val to: Double, val at: Long) : Temp
        data class Steady(val around: Double) : Temp
    }

    sealed interface Precip {
        data object Dry : Precip
        /** Chance ≥ 60 % or a measurable amount expected. */
        data class Likely(val condition: Condition, val from: Long, val chance: Int?) : Precip
        /** Chance 30–59 %. */
        data class Possible(val condition: Condition, val at: Long, val chance: Int) : Precip
        data class Ongoing(val endsAt: Long?) : Precip
    }

    companion object {
        const val WINDOW_HOURS = 12

        fun from(data: WeatherData, now: Long, isAfternoon: Boolean): Outlook {
            val hours = Insights.upcomingHours(data, now, WINDOW_HOURS + 1).drop(1)
            val current = data.current.condition
            val change = Insights.nextChange(listOf(HourlyPoint(now, data.current.temperature, condition = current, isDay = data.current.isDay)) + hours, current)
                .takeIf { it.time != null }?.let { it.condition to it.time!! }
            val today = data.daily.lastOrNull { it.date <= now }
            val tomorrow = data.daily.firstOrNull { it.date > now }
            return Outlook(
                now = current,
                change = change,
                temperature = temperature(data.current.temperature, hours),
                precipitation = precipitation(current, hours, data.minutely, now),
                gust = hours.mapNotNull { it.windGust }.maxOrNull()?.takeIf { it >= 50 },
                tomorrow = if (isAfternoon && today != null) tomorrow else null,
            )
        }

        fun temperature(now: Double, hours: List<HourlyPoint>): Temp {
            if (hours.isEmpty()) return Temp.Steady(now)
            val max = hours.maxBy { it.temperature }
            val min = hours.minBy { it.temperature }
            // Report whichever extreme comes first and differs noticeably from now.
            val rise = max.temperature - now
            val fall = now - min.temperature
            return when {
                rise >= 2 && (fall < 2 || max.time <= min.time) -> Temp.Rise(max.temperature, max.time)
                fall >= 2 -> Temp.Fall(min.temperature, min.time)
                else -> Temp.Steady(now)
            }
        }

        fun precipitation(current: Condition, hours: List<HourlyPoint>, minutely: List<dev.nimbus.weather.data.model.MinutelyPoint>, now: Long): Precip {
            if (current.isPrecipitation) {
                // End of the current precipitation: 15-minute nowcast first, then hourly data.
                val nowcast = Insights.nowcast(Insights.nowcastPoints(minutely, now), now, rainingNow = true)
                if (nowcast is Insights.Nowcast.StopsIn) return Precip.Ongoing(now + nowcast.minutes * 60_000L)
                val end = hours.firstOrNull { (it.precipitation ?: 0.0) < 0.1 && !it.condition.isPrecipitation }
                return Precip.Ongoing(end?.time)
            }
            val likely = hours.firstOrNull { (it.precipitationProbability ?: 0.0) >= 60 || (it.precipitation ?: 0.0) >= 0.3 }
            if (likely != null) {
                return Precip.Likely(precipCondition(likely), likely.time, likely.precipitationProbability?.let { Insights.chanceLabel(it) })
            }
            val possible = hours.filter { (it.precipitationProbability ?: 0.0) >= 30 }.maxByOrNull { it.precipitationProbability ?: 0.0 }
            if (possible != null) {
                return Precip.Possible(precipCondition(possible), possible.time, Insights.chanceLabel(possible.precipitationProbability) ?: 30)
            }
            return Precip.Dry
        }

        /** The hour's own precipitation type, or rain/snow by temperature if the symbol shows none. */
        private fun precipCondition(h: HourlyPoint): Condition = when {
            h.condition.isPrecipitation -> h.condition
            h.temperature <= 1.0 -> Condition.SNOW
            else -> Condition.SHOWERS
        }
    }
}

// ---------------------------------------------------------------------------------------

@androidx.compose.runtime.Composable
fun OutlookCard(data: WeatherData, now: Long) {
    val s = LocalSettings.current
    val tf = LocalTimeFormat.current
    val afternoon = tf.zoned(now).hour >= 15
    val o = androidx.compose.runtime.remember(data, now / 600_000) { Outlook.from(data, now, afternoon) }
    @androidx.compose.runtime.Composable
    fun phrase(c: Condition, isDay: Boolean = true) = androidx.compose.ui.res.stringResource(outlookPhrase(c, isDay))
    @androidx.compose.runtime.Composable
    fun str(id: Int, vararg args: Any) = androidx.compose.ui.res.stringResource(id, *args)
    val temp = { v: Double -> dev.nimbus.weather.util.Units.temp(v, s.temperatureUnit) }

    val sentences = buildList {
        val nowPhrase = phrase(o.now, data.current.isDay)
        add(
            o.change?.let { (c, t) -> str(dev.nimbus.weather.R.string.outlook_change, nowPhrase, phrase(c, true), tf.time(t)) }
                ?: str(dev.nimbus.weather.R.string.outlook_steady, nowPhrase),
        )
        add(
            when (val t = o.temperature) {
                is Outlook.Temp.Rise -> str(dev.nimbus.weather.R.string.outlook_temp_rise, temp(t.to), tf.time(t.at))
                is Outlook.Temp.Fall -> str(dev.nimbus.weather.R.string.outlook_temp_fall, temp(t.to), tf.time(t.at))
                is Outlook.Temp.Steady -> str(dev.nimbus.weather.R.string.outlook_temp_steady, temp(t.around))
            },
        )
        add(
            when (val p = o.precipitation) {
                Outlook.Precip.Dry -> str(dev.nimbus.weather.R.string.outlook_dry)
                is Outlook.Precip.Likely -> if (p.chance != null) str(dev.nimbus.weather.R.string.outlook_precip_likely, phrase(p.condition), tf.time(p.from), p.chance)
                else str(dev.nimbus.weather.R.string.outlook_precip_likely_nochance, phrase(p.condition), tf.time(p.from))
                is Outlook.Precip.Possible -> str(dev.nimbus.weather.R.string.outlook_precip_possible, phrase(p.condition), tf.time(p.at), p.chance)
                is Outlook.Precip.Ongoing -> p.endsAt?.let { str(dev.nimbus.weather.R.string.outlook_precip_ends, tf.time(it)) }
                    ?: str(dev.nimbus.weather.R.string.outlook_precip_continues)
            },
        )
        o.gust?.let {
            add(str(dev.nimbus.weather.R.string.summary_gusts,
                dev.nimbus.weather.util.Units.windNumber(it, s.windUnit) + dev.nimbus.weather.util.NBSP +
                    androidx.compose.ui.res.stringResource(dev.nimbus.weather.util.Texts.windUnit(s.windUnit))))
        }
        o.tomorrow?.let { d ->
            add(str(dev.nimbus.weather.R.string.outlook_tomorrow, phrase(d.condition), temp(d.tempMin), temp(d.tempMax)))
        }
    }.map { sentence -> sentence.replaceFirstChar { it.uppercase() } }

    dev.nimbus.weather.ui.components.GlassCard(
        title = androidx.compose.ui.res.stringResource(dev.nimbus.weather.R.string.outlook_title),
        icon = androidx.compose.material.icons.Icons.Outlined.Schedule,
        info = dev.nimbus.weather.ui.components.Term.OUTLOOK,
    ) {
        androidx.compose.material3.Text(
            sentences.joinToString(" "),
            fontSize = 16.sp, lineHeight = 22.sp, color = androidx.compose.ui.graphics.Color.White,
            style = androidx.compose.ui.text.TextStyle(hyphens = androidx.compose.ui.text.style.Hyphens.Auto),
        )
    }
}

/** Condition as used inside a sentence ("cloudy", "mostly sunny", "rain"). */
fun outlookPhrase(c: Condition, isDay: Boolean): Int = when (c) {
    Condition.CLEAR -> if (isDay) dev.nimbus.weather.R.string.ph_sunny else dev.nimbus.weather.R.string.ph_clear
    Condition.MOSTLY_CLEAR -> if (isDay) dev.nimbus.weather.R.string.ph_mostly_sunny else dev.nimbus.weather.R.string.ph_mostly_clear
    Condition.PARTLY_CLOUDY -> dev.nimbus.weather.R.string.ph_partly_cloudy
    Condition.CLOUDY -> dev.nimbus.weather.R.string.ph_cloudy
    Condition.FOG -> dev.nimbus.weather.R.string.ph_fog
    Condition.DRIZZLE -> dev.nimbus.weather.R.string.ph_drizzle
    Condition.RAIN -> dev.nimbus.weather.R.string.ph_rain
    Condition.HEAVY_RAIN -> dev.nimbus.weather.R.string.ph_heavy_rain
    Condition.FREEZING_RAIN -> dev.nimbus.weather.R.string.ph_freezing_rain
    Condition.SLEET -> dev.nimbus.weather.R.string.ph_sleet
    Condition.SNOW -> dev.nimbus.weather.R.string.ph_snow
    Condition.HEAVY_SNOW -> dev.nimbus.weather.R.string.ph_heavy_snow
    Condition.SHOWERS -> dev.nimbus.weather.R.string.ph_showers
    Condition.THUNDERSTORM -> dev.nimbus.weather.R.string.ph_thunderstorm
}
