/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/PageArrange.kt
 * Tidying the weather page where it is: arranging a card among the others, and the note at its
 * end offering the views.
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

package dev.nimbus.weather.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.PageView
import dev.nimbus.weather.data.model.WeatherCard
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.LocalCardFill
import dev.nimbus.weather.ui.components.ReorderableColumn
import dev.nimbus.weather.ui.theme.CardLabelStyle
import dev.nimbus.weather.ui.theme.NimbusColors

/**
 * Arranging [chosen] among the [cards] on the page: every card down to its title line, so the
 * whole page fits on the screen; the chosen one stands out and follows the finger. Let go, the new
 * order goes to [onDone]; "Cancel" or a tap on another card leaves it as it was ([onCancel]).
 */
@Composable
internal fun ArrangeList(cards: List<WeatherCard>, chosen: WeatherCard, hint: Int, onDone: (List<WeatherCard>) -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("arrange")) {
        // on the card's glass: readable on any sky
        Row(
            Modifier.padding(bottom = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LocalCardFill.current).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(hint), Modifier.weight(1f).padding(start = 4.dp), fontSize = 14.sp, color = Color.White)
            Text(
                stringResource(R.string.arrange_cancel),
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onCancel).padding(horizontal = 12.dp, vertical = 10.dp),
                fontSize = 14.sp, color = Color(0xFF9CC8FF), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
        }
        ReorderableColumn(
            cards, key = { it }, onMove = onDone, gap = 6.dp, immediate = true,
            moveUp = stringResource(R.string.move_up), moveDown = stringResource(R.string.move_down),
        ) { card, dragging, handle ->
            val mine = card == chosen
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (mine) Color(0xFF2A4166) else LocalCardFill.current)
                    .then(if (mine) Modifier.border(1.dp, Color(0xFF9CC8FF), RoundedCornerShape(14.dp)) else Modifier)
                    // the chosen card moves at once; a tap on another ends arranging
                    .then(if (mine) handle else Modifier.clickable(onClick = onCancel))
                    .padding(horizontal = 14.dp)
                    .testTag("arrange-${card.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(dev.nimbus.weather.ui.settings.cardIcon(card), null, tint = if (mine) Color.White else NimbusColors.Tertiary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(dev.nimbus.weather.ui.settings.cardLabel(card)).uppercase(), Modifier.weight(1f),
                    style = CardLabelStyle.copy(color = if (mine) Color.White else NimbusColors.Tertiary), maxLines = 1,
                )
                if (mine) Icon(Icons.Rounded.DragHandle, stringResource(R.string.places_drag), tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/**
 * At the end of the weather page until answered – by everyone, those who used the app before
 * too: whoever got here has seen every card. The views to choose from ([PageView]), or [onKeep]
 * the page as it is; either answer ends it for good.
 */
@Composable
internal fun ViewHint(current: PageView?, onChoose: (PageView) -> Unit, onKeep: () -> Unit) {
    GlassCard(title = null) {
        Text(stringResource(R.string.view_hint_title), fontSize = 16.sp, color = Color.White)
        Spacer(Modifier.size(2.dp))
        Text(stringResource(R.string.view_hint_text), fontSize = 13.sp, color = NimbusColors.Secondary, lineHeight = 18.sp)
        Spacer(Modifier.size(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PageView.entries.forEach { v ->
                val on = v == current
                Text(
                    stringResource(dev.nimbus.weather.ui.settings.viewLabel(v)),
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (on) Color(0x40FFFFFF) else Color.Transparent)
                        .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(10.dp))
                        .clickable { onChoose(v) }.padding(vertical = 10.dp),
                    fontSize = 14.sp, color = if (on) Color.White else NimbusColors.Secondary, maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.view_hint_keep),
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(10.dp))
                .clickable(onClick = onKeep).padding(vertical = 10.dp),
            fontSize = 14.sp, color = NimbusColors.Secondary, maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
