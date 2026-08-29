package app.justthecarbs

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import app.justthecarbs.data.ProductRepository
import app.justthecarbs.data.local.JustTheCarbsDatabase
import app.justthecarbs.data.local.RoomMealDataSource
import app.justthecarbs.data.local.RoomPortionUnitDataSource
import app.justthecarbs.data.local.RoomPortionUsageDataSource
import app.justthecarbs.data.local.RoomProductDataSource
import app.justthecarbs.data.remote.LogcatSearchProviderLog
import app.justthecarbs.data.remote.NetworkModule
import app.justthecarbs.data.remote.OpenFoodFactsDataSource
import app.justthecarbs.data.remote.SearchALiciousDataSource
import app.justthecarbs.data.settings.SettingsRepository
import app.justthecarbs.domain.CachedProductSearch
import app.justthecarbs.domain.FallbackProductSearch
import app.justthecarbs.domain.GovernedProductSearch
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.domain.RemoteSearchGovernor
import app.justthecarbs.domain.SearchProviderLog

/**
 * Manual dependency container.
 *
 * A DI framework would be more machinery than this app has dependencies (§65). Everything is
 * `lazy`, so nothing — database, HTTP client — is constructed during `Application.onCreate`, which
 * keeps cold start fast (§6, §62).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database by lazy { JustTheCarbsDatabase.build(appContext) }

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
     * One Open Food Facts instance serving both roles — it is the canonical **product** source
     * (barcode lookup, the authoritative nutrition path) and the **legacy** search source, and
     * constructing it twice would open two paths to the same host.
     *
     * Its product role is untouched by the 2026-08-28 search migration: every selected search hit,
     * from either provider, still resolves through this object's `fetch`.
     */
    val legacySearchSource by lazy {
        OpenFoodFactsDataSource(
            NetworkModule.openFoodFactsApi(okHttpClient),
            preferredLanguage = {
                appContext.resources.configuration.locales[0].toLanguageTag()
            },
        )
    }

    /**
     * The **legacy** Open Food Facts search budget (§9) — 10 reads/min/IP on `cgi/search.pl`.
     *
     * Held here rather than inside `SearchViewModel` because there is more than one of those:
     * Home's inline search and the search screen are separate instances, so a per-ViewModel
     * cooldown would let the two most-likely-consecutive screens spend the same budget twice over.
     * One instance for the process is the whole point.
     *
     * Since 2026-08-28 it governs the **fallback only**, applied inside [GovernedProductSearch]
     * rather than in the ViewModel. It is still shared by both screens, so the legacy endpoint sees
     * the same single budget it always did.
     */
    val legacySearchGovernor by lazy { RemoteSearchGovernor() }

    /**
     * The primary provider's pacing.
     *
     * A separate instance with its own, far shorter interval — the two services have different
     * limits and one governor cannot express both. Shared across screens for the same reason the
     * legacy one is.
     */
    val primarySearchGovernor by lazy {
        RemoteSearchGovernor(minIntervalMs = RemoteSearchGovernor.PRIMARY_MIN_INTERVAL_MS)
    }

    /**
     * Chain diagnostics, shared by the provider chain and the primary source.
     *
     * One instance so both report to the same place: the primary's "matches arrived but none were
     * usable" event and the chain's "falling back" event are two halves of one story, and reading
     * them from separate logs would hide that the first caused the second.
     */
    private val searchProviderLog: SearchProviderLog =
        if (BuildConfig.DEBUG) LogcatSearchProviderLog else SearchProviderLog.None

    /** Search-a-licious — the primary text-search provider (see [SearchALiciousApi]). */
    private val searchALiciousSource by lazy {
        SearchALiciousDataSource(
            api = NetworkModule.searchALiciousApi(okHttpClient),
            log = searchProviderLog,
        )
    }

    /**
     * The primary, with a short-lived memory of its own successful answers.
     *
     * Wrapping the **primary alone** rather than the whole chain is deliberate. Inside the chain, a
     * cache hit is an ordinary [app.justthecarbs.domain.ProductSearchResult.Found] from the primary,
     * so the fallback is not consulted — structurally, not by a rule — and only successful
     * *primary* answers are ever stored, which keeps "what is in the cache" a statement about one
     * provider. Wrapping the chain instead would file a legacy answer under the primary's name and
     * make a cached result's provenance unanswerable.
     *
     * One instance for the process, so Home's inline search and the search screen share it; typing
     * the same term on both is exactly the case this saves a request on.
     */
    private val cachedPrimarySearchSource by lazy {
        CachedProductSearch(searchALiciousSource)
    }

    /**
     * The provider chain the whole app searches through.
     *
     * Search-a-licious first; the legacy governed endpoint only for the failures
     * [FallbackProductSearch] classifies as worth a second host. Exposed as a plain
     * [app.justthecarbs.domain.ProductSearchSource], so no screen or ViewModel knows there are two
     * providers — which is what keeps the migration reversible: pointing this at
     * [legacySearchSource] alone restores the previous behaviour exactly, with no other change.
     */
    val searchSource: ProductSearchSource by lazy {
        FallbackProductSearch(
            primary = cachedPrimarySearchSource,
            fallback = GovernedProductSearch(legacySearchSource, legacySearchGovernor),
            log = searchProviderLog,
        )
    }

    val productRepository by lazy {
        ProductRepository(
            local = localProducts,
            // The canonical product path. Deliberately NOT the search chain: a barcode lookup is
            // the app's authoritative nutrition source and must keep answering from Open Food
            // Facts' product API whatever the text-search provider is doing.
            remote = legacySearchSource,
            portionUnits = localPortionUnits,
            meal = localMeal,
            portionUsage = localPortionUsage,
            // The repository's own `search` goes through the same chain the screens use, so there
            // is exactly one text-search stack in the app.
            searchSource = searchSource,
        )
    }

    val settingsRepository by lazy { SettingsRepository(appContext) }
}

class JustTheCarbsApplication : Application(), SingletonImageLoader.Factory {

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
