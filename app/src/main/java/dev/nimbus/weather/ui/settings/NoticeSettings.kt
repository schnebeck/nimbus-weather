/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/settings/NoticeSettings.kt
 * The settings of the notices: on (the permission asked for then), each kind, the levels.
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

package dev.nimbus.weather.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.notify.NoticePoster
import dev.nimbus.weather.ui.theme.NimbusColors

/**
 * The notices: off until switched on – the system's permission is asked for then; each kind on
 * its own. The level of the weather warnings holds for the warnings card too, so it stands here
 * whether the notices are on or not. [placeName]: the place they are about (shown last).
 */
@Composable
internal fun NoticeSettings(settings: Settings, placeName: String?, onChange: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) onChange { it.copy(notices = true) }
    }
    Section(stringResource(R.string.settings_notices)) {
        ToggleRow(stringResource(R.string.settings_notices_on), stringResource(R.string.settings_notices_on_desc), settings.notices) { on ->
            when {
                !on -> onChange { it.copy(notices = false) }
                NoticePoster.allowed(context) -> { denied = false; onChange { it.copy(notices = true) } }
                Build.VERSION.SDK_INT >= 33 && !denied -> ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> denied = true
            }
        }
        // switched off for the app in the system: there it is allowed again
        if (denied || settings.notices && !NoticePoster.allowed(context)) {
            Text(
                stringResource(R.string.settings_notices_denied),
                Modifier.padding(vertical = 4.dp).clickable {
                    runCatching {
                        context.startActivity(
                            Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                fontSize = 13.sp, color = Color(0xFFFFD27A), lineHeight = 18.sp,
            )
        }
        if (settings.notices) {
            if (placeName != null) Text(stringResource(R.string.settings_notices_place, placeName), fontSize = 13.sp, color = NimbusColors.Secondary)
            Spacer(Modifier.size(8.dp))
            ToggleRow(stringResource(R.string.settings_notices_warnings), stringResource(R.string.settings_notices_warnings_desc), settings.noticeWarnings) { v ->
                onChange { it.copy(noticeWarnings = v) }
            }
            ToggleRow(stringResource(R.string.settings_notices_gauges), stringResource(R.string.settings_notices_gauges_desc), settings.noticeGauges) { v ->
                onChange { it.copy(noticeGauges = v) }
            }
            if (settings.noticeGauges) {
                Label(stringResource(R.string.settings_gauge_stage))
                Segmented(listOf("1", "2", "3"), (settings.gaugeStage - 1).coerceIn(0, 2)) { i -> onChange { it.copy(gaugeStage = i + 1) } }
                Spacer(Modifier.size(8.dp))
            }
            ToggleRow(stringResource(R.string.settings_notices_floods), stringResource(R.string.settings_notices_floods_desc), settings.noticeFloods) { v ->
                onChange { it.copy(noticeFloods = v) }
            }
            ToggleRow(stringResource(R.string.settings_notices_rain), stringResource(R.string.settings_notices_rain_desc), settings.noticeRain) { v ->
                onChange { it.copy(noticeRain = v) }
            }
        }
        Spacer(Modifier.size(8.dp))
        Label(stringResource(R.string.settings_warning_level))
        Segmented(
            listOf(R.string.warning_level_1, R.string.warning_level_2, R.string.warning_level_3, R.string.warning_level_4).map { stringResource(it) },
            (settings.warningLevel - 1).coerceIn(0, 3),
        ) { i -> onChange { it.copy(warningLevel = i + 1) } }
        Spacer(Modifier.size(10.dp))
        Text(stringResource(R.string.settings_notices_disclaimer), fontSize = 12.sp, color = NimbusColors.Tertiary, lineHeight = 16.sp)
    }
}
