package dev.nimbus.weather.ui.places

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.ui.LocationStatus
import dev.nimbus.weather.ui.PlaceState
import dev.nimbus.weather.ui.UiState
import dev.nimbus.weather.ui.background.SkyScene
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.TimeFormat
import dev.nimbus.weather.util.Units
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    state: UiState,
    search: suspend (String) -> List<Place>,
    onAdd: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onRequestLocation: () -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = null; return@LaunchedEffect }
        delay(300)
        results = search(query.trim())
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1424), Color(0xFF111D33))))
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.pages.isNotEmpty()) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.close), tint = Color.White) }
            } else Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.weather), Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings), tint = Color.White) }
        }
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) } }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0x33FFFFFF), unfocusedContainerColor = Color(0x26FFFFFF),
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedPlaceholderColor = NimbusColors.Tertiary, unfocusedPlaceholderColor = NimbusColors.Tertiary,
                focusedLeadingIconColor = NimbusColors.Secondary, unfocusedLeadingIconColor = NimbusColors.Secondary,
                focusedTrailingIconColor = NimbusColors.Secondary, unfocusedTrailingIconColor = NimbusColors.Secondary,
                cursorColor = Color.White,
            ),
        )

        val res = results
        if (res != null) {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = navBottom + 16.dp)) {
                if (res.isEmpty()) item { Text(stringResource(R.string.no_results), Modifier.padding(12.dp), color = NimbusColors.Secondary) }
                items(res, key = { it.id }) { p ->
                    val already = state.savedPlaces.any { it.id == p.id }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { if (already) onOpen(p.id) else onAdd(p) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 17.sp, color = Color.White)
                            if (p.subtitle.isNotEmpty()) Text(p.subtitle, fontSize = 13.sp, color = NimbusColors.Secondary)
                        }
                        if (!already) Icon(Icons.Rounded.Add, stringResource(R.string.add), tint = Color.White)
                    }
                }
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBottom + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.currentPlace == null && state.locationStatus != LocationStatus.LOADING) {
                item(key = "loc") {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x1FFFFFFF))
                            .clickable(onClick = onRequestLocation).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.LocationOn, null, tint = Color.White)
                        Spacer(Modifier.size(10.dp))
                        Text(stringResource(R.string.use_my_location), color = Color.White, fontSize = 16.sp)
                    }
                }
            }
            items(state.pages, key = { it.id }) { place ->
                val card = @Composable { PlaceCard(place, state.states[place.id], state.settings) { onOpen(place.id) } }
                if (place.isCurrentLocation) card() else {
                    val dismiss = rememberSwipeToDismissBoxState(
                        confirmValueChange = { v -> if (v == SwipeToDismissBoxValue.EndToStart) { onRemove(place); true } else false },
                    )
                    SwipeToDismissBox(
                        state = dismiss,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color(0xFFE5484D)).padding(end = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = Color.White) }
                        },
                    ) { card() }
                }
            }
        }
    }
}

@Composable
private fun PlaceCard(place: Place, st: PlaceState?, settings: dev.nimbus.weather.data.model.Settings, onClick: () -> Unit) {
    val context = LocalContext.current
    val s = st?.data
    val colors = s?.let { SkyScene.from(it).skyColors } ?: listOf(Color(0xFF2A3B57), Color(0xFF34486A), Color(0xFF3E5579))
    val tf = remember(s?.timezone) { s?.let { TimeFormat(it.timezone, DateFormat.is24HourFormat(context)) } }
    Row(
        Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (place.isCurrentLocation) stringResource(R.string.my_location) else place.name,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (place.isCurrentLocation) Icon(Icons.Rounded.LocationOn, null, tint = Color.White, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                }
                Text(
                    if (place.isCurrentLocation) place.name else (tf?.time(System.currentTimeMillis()) ?: place.subtitle),
                    fontSize = 14.sp, color = Color.White, maxLines = 1,
                )
            }
            s?.let { Text(stringResource(Texts.condition(it.current.condition, it.current.isDay)), fontSize = 14.sp, color = Color.White, maxLines = 1) }
        }
        Column(Modifier.fillMaxSize().weight(0.6f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
            Text(s?.let { Units.temp(it.current.temperature, settings.temperatureUnit) } ?: "–", fontSize = 44.sp, fontWeight = FontWeight.Light, color = Color.White)
            val today = s?.daily?.lastOrNull { it.date <= System.currentTimeMillis() }
            if (today != null) {
                Text(
                    stringResource(R.string.high_low, Units.temp(today.tempMax, settings.temperatureUnit), Units.temp(today.tempMin, settings.temperatureUnit)),
                    fontSize = 14.sp, color = Color.White,
                )
            }
        }
    }
}
