package dev.nimbus.weather.data.model

/** Maps WMO weather interpretation codes (as used by Open-Meteo) to [Condition]. */
object WeatherCodes {
    fun fromWmo(code: Int?): Condition = when (code) {
        null -> Condition.CLOUDY
        0 -> Condition.CLEAR
        1 -> Condition.MOSTLY_CLEAR
        2 -> Condition.PARTLY_CLOUDY
        3 -> Condition.CLOUDY
        45, 48 -> Condition.FOG
        51, 53, 55 -> Condition.DRIZZLE
        56, 57, 66, 67 -> Condition.FREEZING_RAIN
        61, 63 -> Condition.RAIN
        65 -> Condition.HEAVY_RAIN
        71, 73, 77 -> Condition.SNOW
        75 -> Condition.HEAVY_SNOW
        80, 81 -> Condition.SHOWERS
        82 -> Condition.HEAVY_RAIN
        85 -> Condition.SNOW
        86 -> Condition.HEAVY_SNOW
        95, 96, 99 -> Condition.THUNDERSTORM
        else -> Condition.CLOUDY
    }

    /**
     * Derives a condition from raw parameters, used for sources that deliver no
     * weather code (e.g. DWD station data).
     */
    fun derive(
        cloudCover: Double?,
        precipitationMmPerHour: Double?,
        temperature: Double?,
        thunder: Boolean = false,
        fog: Boolean = false,
    ): Condition {
        val p = precipitationMmPerHour ?: 0.0
        val cold = (temperature ?: 10.0) <= 0.5
        val sleety = (temperature ?: 10.0) in 0.5..2.0
        if (thunder) return Condition.THUNDERSTORM
        if (p >= 0.1) {
            return when {
                cold && p >= 2.0 -> Condition.HEAVY_SNOW
                cold -> Condition.SNOW
                sleety -> Condition.SLEET
                p >= 4.0 -> Condition.HEAVY_RAIN
                p < 0.3 -> Condition.DRIZZLE
                else -> Condition.RAIN
            }
        }
        if (fog) return Condition.FOG
        val c = cloudCover ?: 50.0
        return when {
            c < 12.5 -> Condition.CLEAR
            c < 37.5 -> Condition.MOSTLY_CLEAR
            c < 75.0 -> Condition.PARTLY_CLOUDY
            else -> Condition.CLOUDY
        }
    }
}
