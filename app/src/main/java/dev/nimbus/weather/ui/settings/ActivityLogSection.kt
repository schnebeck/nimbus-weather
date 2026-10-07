/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/settings/ActivityLogSection.kt
 * Settings: the activity log switched on and off, shared and cleared.
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

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.nimbus.weather.R
import dev.nimbus.weather.util.ActivityLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The activity log: on and off (it keeps its own switch – the HTTP client asks it on every call),
 * and while on, shared as a text file or cleared for a fresh measurement.
 */
@Composable
internal fun ActivityLogSection(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(ActivityLog.enabled) }
    val nothing = stringResource(R.string.activity_log_empty)
    val cleared = stringResource(R.string.activity_log_cleared)
    val chooser = stringResource(R.string.activity_log_share)
    Section(stringResource(R.string.settings_diagnostics)) {
        ToggleRow(stringResource(R.string.activity_log), stringResource(R.string.activity_log_desc), on) { v ->
            ActivityLog.setEnabled(v); on = v
        }
        if (on) {
            Spacer(Modifier.size(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { ActivityLog.clear() }
                        snackbar.showSnackbar(cleared)
                    }
                }) { Text(stringResource(R.string.activity_log_clear), fontSize = 14.sp) }
                TextButton(onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { ActivityLog.export(File(context.cacheDir, "shared/nimbus-activity.txt")) }
                        if (file == null) { snackbar.showSnackbar(nothing); return@launch }
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, chooser))
                    }
                }) { Text(chooser, fontSize = 14.sp) }
            }
        }
    }
}
