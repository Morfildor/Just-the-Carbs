package app.justthecarbs.data.remote

import app.justthecarbs.BuildConfig
import kotlinx.serialization.json.Json
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
