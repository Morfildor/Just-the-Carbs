package app.carbscan

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import app.carbscan.data.ProductRepository
import app.carbscan.data.local.CarbScanDatabase
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

    val productRepository by lazy {
        ProductRepository(
            local = localProducts,
            remote = OpenFoodFactsDataSource(NetworkModule.openFoodFactsApi()),
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
     * Shares the app's single OkHttp client, so product images inherit the same timeouts and the
     * same identifying User-Agent rather than opening a second, differently-configured stack.
     *
     * Images are a nicety and nothing waits for them: the calculator renders and computes with no
     * regard for whether a thumbnail has arrived, and the app is fully usable with none at all.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { NetworkModule.okHttpClient() }))
            }
            .build()
}
