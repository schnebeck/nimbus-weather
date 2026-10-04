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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.ui.theme.CardLabelStyle
import dev.nimbus.weather.ui.theme.NimbusColors

val CardShape = RoundedCornerShape(18.dp)
private val CardRadius = 18.dp

/**
 * Where the scrolling content of a page ends at the top (in root coordinates, px): the line
 * below the collapsed header. Cards that scroll up to it keep their title at the line, the
 * content slides away under it and the card gets shorter, with its round top edge intact;
 * nothing is cut through mid-text. Null: no such line (settings, places …).
 */
val LocalPinLine = androidx.compose.runtime.staticCompositionLocalOf<(() -> Float)?> { null }

/** How far a card ([top], [height] in root px, title block [titleH]) has slid under the line [pin]: 0 … height − titleH. */
fun cardOverlap(pin: Float, top: Float, height: Float, titleH: Float): Float =
    (pin - top).coerceIn(0f, maxOf(0f, height - titleH))

/** Frosted translucent card, the building block of all weather modules. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    tint: Color = LocalCardFill.current,
    onClick: (() -> Unit)? = null,
    contentPadding: Boolean = true,
    info: Term? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pin = LocalPinLine.current
    // Read only while drawing: scrolling never recomposes the cards
    val top = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val titleH = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    fun overlap(height: Float) = if (pin == null) 0f else cardOverlap(pin(), top.floatValue, height, titleH.floatValue)
    val cardH = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    fun shape(scope: androidx.compose.ui.graphics.drawscope.DrawScope) = with(scope) {
        val r = androidx.compose.ui.geometry.CornerRadius(CardRadius.toPx())
        androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, overlap(size.height), size.width, size.height, r))
        }
    }
    Column(
        modifier
            .then(
                if (pin == null) Modifier else Modifier.onGloballyPositioned { c ->
                    top.floatValue = c.positionInRoot().y
                    cardH.floatValue = c.size.height.toFloat()
                },
            )
            // Everything – glass, content, ripple – inside the card's shape, which starts at the
            // line once the card slides under it (shorter, still round on top)
            .drawWithContent {
                val path = shape(this)
                clipPath(path) {
                    drawPath(path, tint)
                    this@drawWithContent.drawContent()
                }
                drawPath(path, NimbusColors.CardBorder, style = androidx.compose.ui.graphics.drawscope.Stroke(0.6.dp.toPx()))
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (title != null) {
            // The title stays at the line while the card slides under it
            Column(
                Modifier
                    .onGloballyPositioned { titleH.floatValue = it.size.height.toFloat() }
                    .zIndex(1f)
                    .graphicsLayer { translationY = overlap(cardH.floatValue) },
            ) {
                CardHeader(title, icon, Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 0.dp), info)
                if (contentPadding) HairlineDivider(Modifier.padding(horizontal = 14.dp))
            }
        }
        // a card without a title shows its status dot in the top corner
        val status = LocalCardStatus.current
        if (title == null && status != null) {
            Box(Modifier.fillMaxWidth().height(0.dp).zIndex(1f)) {
                StatusDot(status, Modifier.align(Alignment.TopEnd).offset(x = (-10).dp, y = 10.dp))
            }
        }
        Column(
            (if (contentPadding) Modifier.padding(horizontal = 14.dp, vertical = 10.dp) else Modifier)
                // the content disappears under the title (or the card's top edge) – never above it
                .drawWithContent {
                    val o = overlap(cardH.floatValue)
                    if (o <= 0f) { drawContent(); return@drawWithContent }
                    // this column starts below the title block; the visible part starts at o + titleH
                    val padTop = if (contentPadding) 10.dp.toPx() else 0f
                    // only from above: the charts reach into the card's side padding (bleed), their
                    // axis labels must not be cut off at the sides (the card's shape clips there)
                    clipRect(left = -size.width, top = o - padTop, right = 2 * size.width, bottom = 2 * size.height) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            content()
        }
    }
}

/**
 * The glass of the cards for the sky behind them ([dev.nimbus.weather.ui.background.SkyScene.cardFill]):
 * as dark as the brightest thing back there (white clouds, the sun, fog) requires for the text to
 * keep its contrast.
 */
val LocalCardFill = androidx.compose.runtime.compositionLocalOf { NimbusColors.CardFill }

/** The header on the sky: the dark halo behind its letters and the pill behind its small line. */
data class HeaderStyle(val halo: Float = dev.nimbus.weather.ui.theme.Legibility.HALO_MIN, val pill: Color = NimbusColors.CardFill) {
    /** Text shadow without offset: a soft dark halo right behind the letters. */
    val shadow: androidx.compose.ui.graphics.Shadow
        get() = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = halo), androidx.compose.ui.geometry.Offset.Zero, 14f)
}

val LocalHeaderStyle = androidx.compose.runtime.compositionLocalOf { HeaderStyle() }

@Composable
fun CardHeader(title: String, icon: ImageVector?, modifier: Modifier = Modifier, info: Term? = null) {
    val status = LocalCardStatus.current
    Row(modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = NimbusColors.Tertiary, modifier = Modifier.size(14.dp))
        // Long titles (e.g. German compounds) shrink instead of being cut off.
        androidx.compose.foundation.text.BasicText(
            title.uppercase(),
            modifier = Modifier.weight(1f),
            style = CardLabelStyle.copy(color = NimbusColors.Tertiary),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                minFontSize = 7.sp, maxFontSize = CardLabelStyle.fontSize, stepSize = 0.5.sp,
            ),
        )
        if (status != null) StatusDot(status)
        if (info != null) InfoButton(info)
    }
}

/** How current a card's data is: [FRESH] just loaded (light green), [STALE] older values – still loading or the source failed (yellow). */
enum class CardStatus { FRESH, STALE }

/** The status of the cards below (the weather page sets it per card; elsewhere none: no dot). */
val LocalCardStatus = androidx.compose.runtime.compositionLocalOf<CardStatus?> { null }

private val FreshDot = Color(0xFF9BE59B)
private val StaleDot = Color(0xFFFFD54F)

/** The small status dot beside a card's info button. */
@Composable
fun StatusDot(status: CardStatus, modifier: Modifier = Modifier) {
    val label = androidx.compose.ui.res.stringResource(
        if (status == CardStatus.FRESH) dev.nimbus.weather.R.string.card_status_fresh else dev.nimbus.weather.R.string.card_status_stale,
    )
    Box(
        modifier.requiredSize(8.dp).background(if (status == CardStatus.FRESH) FreshDot else StaleDot, androidx.compose.foundation.shape.CircleShape)
            .semantics { contentDescription = label },
    )
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
