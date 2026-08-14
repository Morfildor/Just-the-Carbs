package app.carbscan

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import app.carbscan.data.ProductRepository
import app.carbscan.data.local.CarbScanDatabase
import app.carbscan.data.local.RoomMealDataSource
import app.carbscan.data.local.RoomPortionUnitDataSource
import app.carbscan.data.local.RoomPortionUsageDataSource
import app.carbscan.data.local.RoomProductDataSource
import app.carbscan.data.remote.NetworkModule
import app.carbscan.data.remote.OpenFoodFactsDataSource
import app.carbscan.data.settings.SettingsRepository

/**
 * Manual dependency container.
 *
 * A DI framework would be more machinery than this app has dependencies (§65). Everything is
 * `lazy`, so nothing — database, HTTP client — is constructed during `Application.onCreate`, which
 * keeps cold start fast (§6, §62).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database by lazy { CarbScanDatabase.build(appContext) }

    val localProducts by lazy { RoomProductDataSource(database.productDao()) }
    val localPortionUnits by lazy { RoomPortionUnitDataSource(database.portionUnitDao()) }
    val localMeal by lazy { RoomMealDataSource(database.mealItemDao()) }
    val localPortionUsage by lazy { RoomPortionUsageDataSource(database.portionUsageDao()) }

    /**
     * The app's single OkHttp client instance (§15/§65) — Retrofit and Coil both build on this
     * exact object, not on two separately-constructed clients with matching config, so they
     * actually share timeouts, connection pool and the identifying User-Agent rather than merely
     * looking alike.
     */
    val okHttpClient by lazy { NetworkModule.okHttpClient() }

    /**
     * One Open Food Facts instance serving both roles — it is both the product source and the
     * search source, and constructing it twice would open two paths to the same host.
     */
    private val openFoodFacts by lazy {
        OpenFoodFactsDataSource(
            NetworkModule.openFoodFactsApi(okHttpClient),
            preferredLanguage = {
                appContext.resources.configuration.locales[0].toLanguageTag()
            },
        )
    }

    val productRepository by lazy {
        ProductRepository(
            local = localProducts,
            remote = openFoodFacts,
            portionUnits = localPortionUnits,
            meal = localMeal,
            portionUsage = localPortionUsage,
            searchSource = openFoodFacts,
        )
    }

    val settingsRepository by lazy { SettingsRepository(appContext) }
}

class CarbScanApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * Image loading for product thumbnails (§31).
     *
     * Shares the app's single OkHttp client instance (`container.okHttpClient`), not a second
     * client built from matching config — the two are not the same thing: only a genuinely shared
     * instance also shares the connection pool, so images inherit the same timeouts and the same
     * identifying User-Agent as every other request rather than opening a second, look-alike stack.
     *
     * Images are a nicety and nothing waits for them: the calculator renders and computes with no
     * regard for whether a thumbnail has arrived, and the app is fully usable with none at all.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.okHttpClient }))
            }
            .build()
}
