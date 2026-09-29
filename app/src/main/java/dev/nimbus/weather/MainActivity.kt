package dev.nimbus.weather

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.ui.Demo
import dev.nimbus.weather.ui.MainViewModel
import dev.nimbus.weather.ui.background.Season
import dev.nimbus.weather.ui.NimbusRoot
import dev.nimbus.weather.ui.theme.NimbusTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        applyDemoExtras()
        setContent {
            NimbusTheme {
                NimbusRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyDemoExtras()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    /**
     * Test hook for visual QA: `adb shell am start -n dev.nimbus.weather/.MainActivity
     *   --es demo_condition RAIN --ez demo_night true --es demo_season AUTUMN --es demo_wind 0.8
     *   --es demo_pollen 0.6` forces a background scene.
     */
    private fun applyDemoExtras() {
        val i = intent ?: return
        val cond = i.getStringExtra("demo_condition")?.let { runCatching { Condition.valueOf(it) }.getOrNull() }
        val season = i.getStringExtra("demo_season")?.let { runCatching { Season.valueOf(it) }.getOrNull() }
        val wind = i.getStringExtra("demo_wind")?.toFloatOrNull()
        val pollen = i.getStringExtra("demo_pollen")?.toFloatOrNull()
        val demo = if (cond != null || season != null || wind != null || pollen != null) {
            Demo(cond, i.getBooleanExtra("demo_night", false), season, wind, pollen)
        } else null
        viewModel.setDemo(demo, i.getStringExtra("demo_screen"))
        dev.nimbus.weather.ui.radar.RadarPalette.demoTempOffset = i.getStringExtra("demo_radar_temp")?.toFloatOrNull() ?: 0f
    }
}
