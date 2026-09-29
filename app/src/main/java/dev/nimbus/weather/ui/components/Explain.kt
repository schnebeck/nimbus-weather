package dev.nimbus.weather.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R

/** Weather terms the app can explain. */
enum class Term(@StringRes val title: Int, @StringRes val body: Int) {
    PRECIP_PROBABILITY(R.string.term_precip_prob_title, R.string.term_precip_prob_body),
    FEELS_LIKE(R.string.term_feels_like_title, R.string.term_feels_like_body),
    DEW_POINT(R.string.term_dew_point_title, R.string.term_dew_point_body),
    UV_INDEX(R.string.term_uv_title, R.string.term_uv_body),
    WIND(R.string.term_wind_title, R.string.term_wind_body),
    VISIBILITY(R.string.term_visibility_title, R.string.term_visibility_body),
    PRESSURE(R.string.term_pressure_title, R.string.term_pressure_body),
    AIR_QUALITY(R.string.term_aqi_title, R.string.term_aqi_body),
    POLLEN(R.string.term_pollen_title, R.string.term_pollen_body),
    NOWCAST(R.string.term_nowcast_title, R.string.term_nowcast_body),
    RADAR(R.string.term_radar_title, R.string.term_radar_body),
    MODELS(R.string.term_models_title, R.string.term_models_body),
    COMMUNITY(R.string.term_community_title, R.string.term_community_body),
    STATION(R.string.term_station_title, R.string.term_station_body),
    ALERTS(R.string.term_alerts_title, R.string.term_alerts_body),
    MOON(R.string.term_moon_title, R.string.term_moon_body),
    HOURLY(R.string.term_hourly_title, R.string.term_hourly_body),
    DAILY(R.string.term_daily_title, R.string.term_daily_body),
}

/** Opens the explanation sheet for a term; provided by [ExplainHost]. */
val LocalExplain = staticCompositionLocalOf<(Term) -> Unit> { {} }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplainHost(content: @Composable () -> Unit) {
    var term by rememberSaveable { mutableStateOf<Term?>(null) }
    CompositionLocalProvider(LocalExplain provides { term = it }) {
        content()
    }
    val t = term ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { term = null },
        sheetState = sheetState,
        containerColor = Color(0xFF16233A),
        contentColor = Color.White,
        scrimColor = Color(0x99000000),
    ) {
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = navBottom + 24.dp),
        ) {
            Text(
                stringResource(t.title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                style = TextStyle(hyphens = Hyphens.Auto, lineBreak = LineBreak.Heading),
            )
            Spacer(Modifier.height(12.dp))
            ExplainBody(stringResource(t.body))
        }
    }
}

/** Renders paragraphs; lines starting with "• " get a hanging indent so wrapped lines align with the text. */
@Composable
private fun ExplainBody(body: String) {
    val style = TextStyle(
        fontSize = 16.sp, lineHeight = 23.sp, color = Color(0xE6FFFFFF),
        hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph,
    )
    body.split('\n').forEach { line ->
        when {
            line.isBlank() -> Spacer(Modifier.height(12.dp))
            line.startsWith("• ") -> Row(Modifier.padding(start = 4.dp, top = 2.dp)) {
                Text("•", style = style, modifier = Modifier.width(16.dp))
                Text(line.removePrefix("• "), style = style)
            }
            else -> Text(line, style = style)
        }
    }
}
