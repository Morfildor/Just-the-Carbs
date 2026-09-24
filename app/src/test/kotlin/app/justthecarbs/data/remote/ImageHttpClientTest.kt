package app.justthecarbs.data.remote

import app.justthecarbs.BuildConfig
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Product photos have their own timeouts, derived from the app's one client.
 *
 * Measured 2026-09-23: Open Food Facts' image host took 8 to 34 s to complete a TLS handshake and
 * sometimes dropped the connection attempt, while the product API answered in 0.2 s. Photos went
 * through the product client, whose 15 s read timeout also bounds the handshake and whose 20 s call
 * timeout bounds the whole request, so 10 of 17 measured connections would have been abandoned and
 * the calculator kept showing the initials. Nothing waits for a photo, so a photo may take as long
 * as the host needs; a product lookup still gives up quickly.
 */
class ImageHttpClientTest {

    private lateinit var server: MockWebServer
    private val api = NetworkModule.okHttpClient()
    private val images = NetworkModule.imageHttpClient(api)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a photo outlasts the slowest handshake measured on the image host`() {
        // The TLS handshake runs under the read timeout, and the call timeout covers everything.
        assertTrue(images.readTimeoutMillis > SLOWEST_MEASURED_HANDSHAKE_MS)
        assertTrue(images.callTimeoutMillis > SLOWEST_MEASURED_HANDSHAKE_MS)
        assertTrue(images.connectTimeoutMillis > SLOWEST_MEASURED_CONNECT_MS)
    }

    @Test
    fun `a product lookup still gives up as quickly as before`() {
        assertEquals(10_000, api.connectTimeoutMillis)
        assertEquals(15_000, api.readTimeoutMillis)
        assertEquals(20_000, api.callTimeoutMillis)
    }

    @Test
    fun `a photo request still identifies the app`() {
        server.enqueue(MockResponse().setBody("jpeg"))

        images.newCall(Request.Builder().url(server.url("/images/products/1.400.jpg")).build()).execute().close()

        assertEquals(BuildConfig.OFF_USER_AGENT, server.takeRequest().getHeader("User-Agent"))
    }

    @Test
    fun `photos and product lookups share one connection pool`() {
        assertSame(api.connectionPool, images.connectionPool)
    }

    /**
     * A search page shows about eight rows. With OkHttp's default of five requests per host, the
     * rows past the fifth waited for a free connection to Open Food Facts' image host, which takes
     * seconds to open one (measured 2026-09-23: ten opened in parallel all finished in about the
     * time one took).
     */
    @Test
    fun `photos may use ten connections per host, product lookups keep the default`() {
        assertEquals(10, images.dispatcher.maxRequestsPerHost)
        assertEquals(5, api.dispatcher.maxRequestsPerHost)
        assertNotSame(api.dispatcher, images.dispatcher)
    }

    private companion object {
        /** The longest successful handshake measured against images.openfoodfacts.org, 2026-09-23. */
        const val SLOWEST_MEASURED_HANDSHAKE_MS = 34_000

        /** The longest TCP connect measured there: a SYN answered on its retransmission. */
        const val SLOWEST_MEASURED_CONNECT_MS = 15_000
    }
}
