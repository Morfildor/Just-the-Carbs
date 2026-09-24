package app.justthecarbs.data.remote

import app.justthecarbs.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The app's only network client. Deliberately tiny — one host, one endpoint (§65).
 */
object NetworkModule {

    private val json = Json {
        // OFF returns hundreds of fields the app does not request or understand; unknown keys are
        // the normal case, not an error.
        ignoreUnknownKeys = true
        // A null where a number is expected becomes the property default rather than an exception.
        coerceInputValues = true
    }

    /**
     * OFF requires a User-Agent identifying the application and offering a contact route; requests
     * without one are blocked (verified 2026-08-13). It is built from branding.gradle.kts, so a
     * rebrand cannot leave a stale name here.
     */
    private val userAgent = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", BuildConfig.OFF_USER_AGENT)
                .build(),
        )
    }

    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(userAgent)
        // Short enough that a stalled lookup does not leave the user staring at a spinner in a
        // supermarket; §37 requires the wait to be cancellable and brief.
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Product photos: [shared] with timeouts a photo needs, keeping its User-Agent and connection
     * pool, and with its own dispatcher allowing [PHOTO_REQUESTS_PER_HOST] requests per host.
     *
     * Measured 2026-09-23: `images.openfoodfacts.org` took 8 to 34 s to complete a TLS handshake,
     * and sometimes a TCP connect was answered only on its retransmission at 15 s, while the
     * product API answered in 0.2 s. Under [okHttpClient]'s limits (the handshake runs under the
     * read timeout) 10 of 17 measured connections would have been abandoned, and the calculator
     * kept showing the initials. Nothing waits for a photo, and leaving the screen cancels its
     * request, so a photo may take as long as the host needs; a product lookup still gives up
     * quickly.
     *
     * The own dispatcher (2026-09-23): a search page shows about eight rows, and with OkHttp's
     * default of five requests per host the rows past the fifth waited for a free connection to a
     * host that takes seconds to open one. Ten opened in parallel all finished in about the time one
     * took. Product lookups keep the shared dispatcher and its default.
     */
    fun imageHttpClient(shared: OkHttpClient): OkHttpClient = shared.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = PHOTO_REQUESTS_PER_HOST })
        .build()

    private const val PHOTO_REQUESTS_PER_HOST = 10

    fun openFoodFactsApi(client: OkHttpClient = okHttpClient()): OpenFoodFactsApi = Retrofit.Builder()
        .baseUrl(OpenFoodFactsApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OpenFoodFactsApi::class.java)

    /**
     * Search-a-licious, the primary text-search provider.
     *
     * A second Retrofit instance because it is a second host, but built on the **same**
     * [OkHttpClient] — so it inherits the identifying User-Agent, the connection pool and, most
     * importantly here, the existing 10 s connect / 15 s read / 20 s call timeouts.
     *
     * Reusing those timeouts is deliberate. A primary that hangs would otherwise delay the fallback
     * by its own timeout plus the fallback's, and the call timeout is what bounds that: a failing
     * primary reaches the fallback within 20 s at worst rather than stacking two open-ended waits.
     */
    fun searchALiciousApi(client: OkHttpClient = okHttpClient()): SearchALiciousApi = Retrofit.Builder()
        .baseUrl(SearchALiciousApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SearchALiciousApi::class.java)
}
