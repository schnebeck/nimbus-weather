/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/places/ModelSheet.kt
 * A place's forecast model: first whether it follows the app's setting or has a model of its own,
 * then – only for its own – which.
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

package dev.nimbus.weather.ui.places

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.ForecastModel
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.ui.settings.ModelChoice
import dev.nimbus.weather.ui.settings.ModelChoices

/** A model's short name as the choices call it ("Automatisch", "MET Nordic" …). */
@Composable
fun choiceName(m: ForecastModel): String =
    if (m == ForecastModel.BEST_MATCH) stringResource(R.string.model_auto_short) else dev.nimbus.weather.ui.main.modelName(m)

/**
 * "Standard" stood among the models as one of them – it is another kind of choice: whether the
 * place follows the app's setting ([setting], changing with it) or has a model of its own. The
 * models only open for its own; switching to it alone changes nothing (the one in force is
 * marked), a model tapped fixes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelSheet(
    place: Place, setting: ForecastModel,
    /** A copy just made ("⧉"): it is there for a model of its own – the models open at once. */
    copy: Boolean,
    onChoose: (ForecastModel?) -> Unit, onDismiss: () -> Unit,
) {
    var own by rememberSaveable { mutableStateOf(copy || place.model != null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF16233A), contentColor = Color.White, scrimColor = Color(0x99000000),
    ) {
        val navBottom = dev.nimbus.weather.ui.components.navBarBottom()
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 24.dp, bottom = navBottom + 24.dp).testTag("model-sheet"),
        ) {
            Text(
                stringResource(R.string.places_model_title, place.name), Modifier.padding(start = 8.dp, bottom = 8.dp),
                fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            )
            ModelChoice(
                !own, stringResource(R.string.places_model_app), stringResource(R.string.places_model_app_desc, choiceName(setting)),
            ) { own = false; onChoose(null) }
            ModelChoice(own, stringResource(R.string.places_model_own), stringResource(R.string.places_model_own_desc, place.name)) { own = true }
            AnimatedVisibility(own) {
                Column(Modifier.padding(start = 24.dp).testTag("own-models")) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color.White.copy(alpha = 0.15f))
                    val inForce = place.model ?: setting
                    ModelChoices.forEach { (m, title, desc) ->
                        ModelChoice(inForce == m, stringResource(title), stringResource(desc)) { onChoose(m) }
                    }
                }
            }
        }
    }
}
