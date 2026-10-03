/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/settings/LicensesScreen.kt
 * Open-source licenses: Nimbus itself, every library in the app and the data attributions.
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

import dev.nimbus.weather.ui.components.statusBarsStable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withJson
import dev.nimbus.weather.BuildConfig
import dev.nimbus.weather.R
import dev.nimbus.weather.ui.theme.NimbusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Nimbus' own entry in the generated list (app/aboutlibraries/libraries/nimbus.json). */
private const val NIMBUS_ID = "dev.nimbus.weather:nimbus"
private const val SOURCE_URL = "https://github.com/schnebeck/nimbus-weather"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val navBottom = dev.nimbus.weather.ui.components.navBarBottom()
    // The list is generated at build time (AboutLibraries) and read once from the raw resource.
    val libs by produceState<Libs?>(null) {
        value = withContext(Dispatchers.IO) { Libs.Builder().withJson(context, R.raw.aboutlibraries).build() }
    }
    var shown by remember { mutableStateOf<Library?>(null) }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1424), Color(0xFF111D33))))
            .windowInsetsPadding(WindowInsets.statusBarsStable),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.close), tint = Color.White) }
            Text(stringResource(R.string.licenses_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        val all = libs?.libraries
        if (all == null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
            return@Column
        }
        val nimbus = all.firstOrNull { it.uniqueId == NIMBUS_ID }
        val others = all.filter { it.uniqueId != NIMBUS_ID }.sortedBy { it.name.lowercase() }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { AppCard(onShowLicense = { nimbus?.let { shown = it } }) }
            item {
                Block(stringResource(R.string.licenses_libraries, others.size)) {
                    others.forEachIndexed { i, lib ->
                        if (i > 0) Spacer(Modifier.fillMaxWidth().padding(vertical = 2.dp).size(1.dp).background(Color(0x14FFFFFF)))
                        LibraryRow(lib) { shown = lib }
                    }
                }
            }
            item {
                Block(stringResource(R.string.licenses_data)) {
                    Text(stringResource(R.string.licenses_data_text), fontSize = 13.sp, color = NimbusColors.Secondary, lineHeight = 18.sp)
                }
            }
        }
    }

    shown?.let { lib ->
        ModalBottomSheet(
            onDismissRequest = { shown = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF16233A),
            contentColor = Color.White,
        ) { LicenseSheet(lib) }
    }
}

@Composable
private fun AppCard(onShowLicense: () -> Unit) {
    val uri = LocalUriHandler.current
    Block(null) {
        Text("Nimbus", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), fontSize = 13.sp, color = NimbusColors.Tertiary)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.licenses_app_text), fontSize = 14.sp, color = NimbusColors.Secondary, lineHeight = 19.sp)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.licenses_credits), fontSize = 13.sp, color = NimbusColors.Tertiary, lineHeight = 18.sp)
        Spacer(Modifier.size(4.dp))
        LinkRow(stringResource(R.string.licenses_source)) { uri.openUri(SOURCE_URL) }
        LinkRow(stringResource(R.string.licenses_show_text)) { onShowLicense() }
    }
}

@Composable
private fun LibraryRow(lib: Library, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(lib.name, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f, fill = false), maxLines = 2)
                lib.artifactVersion?.let { Text("  $it", fontSize = 12.sp, color = NimbusColors.Tertiary, maxLines = 1) }
            }
            val by = lib.organization?.name ?: lib.developers.firstNotNullOfOrNull { it.name }
            val licenses = lib.licenses.joinToString(" · ") { it.spdxId ?: it.name }
            Text(listOfNotNull(by, licenses.ifEmpty { null }).joinToString(" · "), fontSize = 12.sp, color = NimbusColors.Secondary, maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = NimbusColors.Tertiary)
    }
}

@Composable
private fun LicenseSheet(lib: Library) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
        Text(lib.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        val by = listOfNotNull(lib.artifactVersion, lib.organization?.name ?: lib.developers.firstNotNullOfOrNull { it.name })
        if (by.isNotEmpty()) Text(by.joinToString(" · "), fontSize = 13.sp, color = NimbusColors.Tertiary)
        lib.description?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.size(8.dp))
            Text(it, fontSize = 14.sp, color = NimbusColors.Secondary, lineHeight = 19.sp)
        }
        (lib.website ?: lib.scm?.url)?.let { url ->
            Spacer(Modifier.size(4.dp))
            LinkRow(url.removePrefix("https://").removePrefix("http://")) { uri.openUri(url) }
        }
        lib.licenses.forEach { license ->
            Spacer(Modifier.size(16.dp))
            Text(license.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            val text = license.licenseContent?.let { reflow(it) }
            if (!text.isNullOrEmpty()) {
                Spacer(Modifier.size(6.dp))
                Text(text, fontSize = 12.sp, color = NimbusColors.Secondary, lineHeight = 17.sp)
            } else {
                license.url?.let { url -> LinkRow(url.removePrefix("https://").removePrefix("http://")) { uri.openUri(url) } }
            }
        }
    }
}

/**
 * License files are often wrapped at a fixed width; on a phone that gives ragged lines. Lines
 * within a paragraph are joined, paragraphs (blank lines) are kept.
 */
internal fun reflow(text: String): String =
    text.replace("\r", "").replace("<br />", "").trim()
        .split(Regex("\n\\s*\n"))
        .joinToString("\n\n") { p -> p.lines().joinToString(" ") { it.trim() }.replace(Regex(" {2,}"), " ") }

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Text(
        text, fontSize = 14.sp, color = Color(0xFF9CC8FF), textDecoration = TextDecoration.Underline,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
    )
}

@Composable
private fun Block(title: String?, content: @Composable () -> Unit) {
    Column {
        if (title != null) {
            Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NimbusColors.Tertiary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x1AFFFFFF)).padding(12.dp)) { content() }
    }
}
