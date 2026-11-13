/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/widget/WidgetTint.kt
 * The widget's background colour and the writing on it, light or dark by what lies behind.
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

package dev.nimbus.weather.widget

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import dev.nimbus.weather.R

/**
 * The layout's writing comes in the system's colours, light by day and dark by night. A chosen
 * colour, or a background so transparent that the wallpaper shows through, can call for the other
 * ink: then every piece of writing is coloured here. Only colours the layout gives a view are
 * replaced – the views are listed by the colour they have there.
 */
internal object WidgetTint {
    /** Views written in the text colour (TextViews and the pin's icon). */
    val TEXT = listOf(
        R.id.widget_date, R.id.widget_place, R.id.widget_clock, R.id.widget_temp, R.id.widget_max, R.id.widget_min,
        R.id.widget_pollen_dots, R.id.widget_rain_text,
        R.id.widget_side_0_name, R.id.widget_side_1_name, R.id.widget_side_2_name, R.id.widget_side_3_name,
        R.id.widget_side_4_name, R.id.widget_side_5_name,
        R.id.widget_pollen_0_name, R.id.widget_pollen_1_name, R.id.widget_pollen_2_name, R.id.widget_pollen_3_name,
        R.id.widget_pollen_4_name, R.id.widget_pollen_5_name, R.id.widget_pollen_6_name, R.id.widget_pollen_7_name,
        R.id.widget_pollen_8_name, R.id.widget_pollen_9_name, R.id.widget_pollen_10_name, R.id.widget_pollen_11_name,
        R.id.widget_pollen_12_name, R.id.widget_pollen_13_name, R.id.widget_pollen_14_name, R.id.widget_pollen_15_name,
        R.id.widget_pollen_16_name, R.id.widget_pollen_17_name, R.id.widget_pollen_18_name, R.id.widget_pollen_19_name,
        R.id.widget_pollen_20_name, R.id.widget_pollen_21_name, R.id.widget_pollen_22_name, R.id.widget_pollen_23_name,
        R.id.widget_pollen_24_name, R.id.widget_pollen_25_name, R.id.widget_pollen_26_name,
    )
    val TEXT_ICONS = listOf(R.id.widget_pin)
    val DIM = listOf(
        R.id.widget_week, R.id.widget_alarm_beside_text, R.id.widget_alarm, R.id.widget_side_quiet,
        R.id.widget_as_of_side, R.id.widget_as_of_stack, R.id.widget_as_of,
    )
    val DIM_ICONS = listOf(R.id.widget_alarm_beside_icon, R.id.widget_alarm_icon, R.id.widget_refresh)
    val CHANCE = listOf(R.id.widget_chance)

    /** The wallpaper's colour behind the widget (null: unknown); replaceable for tests. */
    @Volatile var wallpaper: (Context) -> Int? = ::wallpaperColor

    /**
     * [config]'s background colour on [v], the writing in the ink that stands out on what lies
     * behind it. Returns the ink now in use – for what the widget draws itself, like the chart's
     * labels – or null where the layout's own colours stay.
     */
    fun apply(context: Context, v: RemoteViews, config: WidgetConfig): WidgetInk? {
        val wall = runCatching { wallpaper(context) }.getOrNull()
        val chosen = config.background
        if (chosen != null) {
            val bg = chosen or WidgetInk.OPAQUE
            v.setInt(R.id.widget_bg, "setColorFilter", bg)
            return WidgetInk.of(WidgetInk.behind(bg, config.opacity, wall)).also { paint(v, it) }
        }
        // the system's colour: the layout's ink fits it, unless the wallpaper shows through
        val day = WidgetInk.of(WidgetInk.behind(systemBackground(context, night = false), config.opacity, wall))
        val night = WidgetInk.of(WidgetInk.behind(systemBackground(context, night = true), config.opacity, wall))
        if (day.text == WidgetInk.ON_LIGHT.text && night.text == WidgetInk.ON_DARK.text) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) paint(v, day, night)
        else paint(v, if (isNight(context)) night else day)
        return if (isNight(context)) night else day
    }

    private fun paint(v: RemoteViews, ink: WidgetInk) {
        TEXT.forEach { v.setTextColor(it, ink.text) }
        TEXT_ICONS.forEach { v.setInt(it, "setColorFilter", ink.text) }
        DIM.forEach { v.setTextColor(it, ink.dim) }
        DIM_ICONS.forEach { v.setInt(it, "setColorFilter", ink.dim) }
        CHANCE.forEach { v.setTextColor(it, ink.chance) }
    }

    /** By day and by night each its ink: the launcher switches between them without a new picture. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun paint(v: RemoteViews, day: WidgetInk, night: WidgetInk) {
        TEXT.forEach { v.setColorInt(it, "setTextColor", day.text, night.text) }
        TEXT_ICONS.forEach { v.setColorInt(it, "setColorFilter", day.text, night.text) }
        DIM.forEach { v.setColorInt(it, "setTextColor", day.dim, night.dim) }
        DIM_ICONS.forEach { v.setColorInt(it, "setColorFilter", day.dim, night.dim) }
        CHANCE.forEach { v.setColorInt(it, "setTextColor", day.chance, night.chance) }
    }

    private fun isNight(context: Context) =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /** The widget's own background colour by day or by night. */
    fun systemBackground(context: Context, night: Boolean): Int {
        val conf = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
        return context.createConfigurationContext(conf).getColor(R.color.widget_bg)
    }

    /** A light and a dark stand-in where the wallpaper's main colour says otherwise than its hint for writing. */
    private const val LIGHT_WALL = 0xFFDDDDDD.toInt()
    private const val DARK_WALL = 0xFF333333.toInt()

    /**
     * The wallpaper's main colour; from Android 12 on corrected by its hint whether dark writing
     * reads on it (the main colour of a picture can be dark where its middle is bright).
     */
    private fun wallpaperColor(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return null
        val main = colors.primaryColor.toArgb() or WidgetInk.OPAQUE
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return main
        val darkText = colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
        return when {
            darkText && !WidgetInk.lightBehind(main) -> LIGHT_WALL
            !darkText && WidgetInk.lightBehind(main) -> DARK_WALL
            else -> main
        }
    }
}
