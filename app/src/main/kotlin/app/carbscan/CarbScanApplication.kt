package app.carbscan

import android.app.Application
import android.content.Context
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

class CarbScanApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
