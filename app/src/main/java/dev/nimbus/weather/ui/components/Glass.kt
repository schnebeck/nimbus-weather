/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/components/Glass.kt
 * Translucent cards, card headers and dividers used on all pages.
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

package dev.nimbus.weather.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.ui.theme.CardLabelStyle
import dev.nimbus.weather.ui.theme.NimbusColors

val CardShape = RoundedCornerShape(18.dp)

/** Frosted translucent card, the building block of all weather modules. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    tint: Color = NimbusColors.CardFill,
    onClick: (() -> Unit)? = null,
    contentPadding: Boolean = true,
    info: Term? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CardShape)
            .background(tint)
            .border(0.6.dp, NimbusColors.CardBorder, CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (title != null) {
            CardHeader(title, icon, Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 0.dp), info)
            if (contentPadding) HairlineDivider(Modifier.padding(horizontal = 14.dp))
        }
        Column(if (contentPadding) Modifier.padding(horizontal = 14.dp, vertical = 10.dp) else Modifier) {
            content()
        }
    }
}

@Composable
fun CardHeader(title: String, icon: ImageVector?, modifier: Modifier = Modifier, info: Term? = null) {
    Row(modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = NimbusColors.Tertiary, modifier = Modifier.size(14.dp))
        // Long titles (e.g. German compounds) shrink instead of being cut off.
        androidx.compose.foundation.text.BasicText(
            title.uppercase(),
            modifier = Modifier.weight(1f),
            style = CardLabelStyle.copy(color = NimbusColors.Tertiary),
            maxLines = 1,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                minFontSize = 9.sp, maxFontSize = CardLabelStyle.fontSize, stepSize = 0.5.sp,
            ),
        )
        if (info != null) InfoButton(info)
    }
}

/** Small ⓘ button that opens the explanation of [term]. */
@Composable
fun InfoButton(term: Term, modifier: Modifier = Modifier) {
    val explain = LocalExplain.current
    val label = androidx.compose.ui.res.stringResource(dev.nimbus.weather.R.string.info_about, androidx.compose.ui.res.stringResource(term.title))
    Box(
        modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClickLabel = label) { explain(term) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(androidx.compose.material.icons.Icons.Outlined.Info, contentDescription = label, tint = NimbusColors.Tertiary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.6.dp).background(NimbusColors.Divider))
}
