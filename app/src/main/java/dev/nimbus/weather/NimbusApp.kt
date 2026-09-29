package dev.nimbus.weather

import android.app.Application
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.USER_AGENT
import dev.nimbus.weather.data.repo.LocationProvider
import dev.nimbus.weather.data.repo.Store
import dev.nimbus.weather.data.repo.WeatherRepository
import dev.nimbus.weather.ui.radar.RadarCacheInterceptor
import dev.nimbus.weather.ui.radar.RadarTileInterceptor
import dev.nimbus.weather.ui.radar.RetryInterceptor
import okhttp3.Cache
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class NimbusApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        dev.nimbus.weather.ui.radar.WeatherGridStore.cacheDir = java.io.File(cacheDir, "grid")
        MapLibre.getInstance(this)
        HttpRequestUtil.setOkHttpClient(container.mapHttp)
        dev.nimbus.weather.data.repo.RefreshWorker.schedule(this)
    }
}

class AppContainer(app: Application) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cache(Cache(File(app.cacheDir, "http"), 20L * 1024 * 1024))
        .retryOnConnectionFailure(true)
        .build()

    /** Client used by MapLibre for map and radar tiles; recolors radar images on the fly. */
    val mapHttp: OkHttpClient = http.newBuilder()
        // Map tiles come from few hosts; OkHttp's default of 5 parallel requests per host is too low.
        .dispatcher(okhttp3.Dispatcher().apply { maxRequests = 32; maxRequestsPerHost = 10 })
        .cache(Cache(File(app.cacheDir, "maptiles"), 80L * 1024 * 1024))
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .addInterceptor(RadarTileInterceptor())
        .addInterceptor(RetryInterceptor(setOf("maps.dwd.de", "tilecache.rainviewer.com")))
        .addNetworkInterceptor(RadarCacheInterceptor())
        .build()

    val store = Store(app)
    val location = LocationProvider(app)
    val openMeteo = OpenMeteoSource(http)
    val history = dev.nimbus.weather.data.remote.HistorySource(http)
    val repository = WeatherRepository(openMeteo, BrightSkySource(http), CommunitySource(http), PollenSource(http))
}
