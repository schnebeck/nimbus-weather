package dev.nimbus.weather.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nimbus.weather.ui.components.ExplainHost
import dev.nimbus.weather.ui.main.MainScreen
import dev.nimbus.weather.ui.places.PlacesScreen
import dev.nimbus.weather.ui.radar.RadarScreen
import dev.nimbus.weather.ui.settings.SettingsScreen

@Composable
fun NimbusRoot(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.onLocationPermissionResult(result.values.any { it })
    }
    val requestLocation = {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    BackHandler(enabled = state.backStack.size > 1) { viewModel.back() }

    ExplainHost {
    Box(Modifier.fillMaxSize().background(Color(0xFF0E1726))) {
        if (!state.initialized) return@Box
        AnimatedContent(
            targetState = state.screen,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 1.02f))
            },
            contentKey = { it::class },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Main -> MainScreen(
                    state = state,
                    onSelect = viewModel::select,
                    onRefresh = viewModel::refresh,
                    onOpenRadar = { viewModel.navigate(Screen.Radar(it)) },
                    onOpenPlaces = { viewModel.navigate(Screen.Places) },
                    onRequestModels = viewModel::loadModels,
                    onRequestHistory = { viewModel.loadHistory(it) },
                    onRequestLocation = requestLocation,
                )
                Screen.Places -> PlacesScreen(
                    state = state,
                    search = viewModel::search,
                    onAdd = { viewModel.addPlace(it); viewModel.back() },
                    onRemove = viewModel::removePlace,
                    onOpen = { viewModel.select(it); viewModel.back() },
                    onSettings = { viewModel.navigate(Screen.Settings) },
                    onRequestLocation = requestLocation,
                    onBack = { viewModel.back() },
                )
                Screen.Settings -> SettingsScreen(
                    settings = state.settings,
                    onChange = viewModel::updateSettings,
                    onBack = { viewModel.back() },
                )
                is Screen.Radar -> {
                    val place = state.pages.firstOrNull { it.id == screen.placeId } ?: state.pages.firstOrNull()
                    RadarScreen(place = place, temperatureUnit = state.settings.temperatureUnit, onBack = { viewModel.back() })
                }
            }
        }
    }
    }
}
