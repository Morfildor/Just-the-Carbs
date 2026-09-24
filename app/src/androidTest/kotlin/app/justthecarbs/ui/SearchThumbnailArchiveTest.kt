package app.justthecarbs.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.ui.components.SearchThumbnail
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.Collections

/**
 * A search row's photo: the S3 archive first, Open Food Facts' own address on any failure (2026-09-23).
 *
 * The image pipeline is replaced by a loader whose answers the test sets by URL: an archive photo
 * is drawn red, Open Food Facts' own picture green, and anything with "missing" in its address
 * fails. Which one is on screen is read from the tile's pixels, and every address the loader was
 * asked for is recorded in order, so "fell back once" and "never asked" are both observable.
 *
 * **Instrumented: needs a device or emulator.**
 */
@OptIn(DelicateCoilApi::class)
class SearchThumbnailArchiveTest {

    @get:Rule
    val compose = createComposeRule()

    private val requested: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Before
    fun controlTheImagePipeline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .memoryCache(null)
                .diskCache(null)
                .components { add(ControlledImages(requested)) }
                .build(),
        )
    }

    @After
    fun restoreTheImagePipeline() {
        SingletonImageLoader.reset()
    }

    @Test
    fun theArchivePhotoIsShownAndTheUsualAddressIsNeverRequested() {
        show(imageUrl = OFF_URL, archiveImageUrl = ARCHIVE_URL)

        awaitTile(RED)
        assertEquals(listOf(ARCHIVE_URL), requestedNow())
    }

    @Test
    fun aFailedArchivePhotoFallsBackToTheUsualAddressOnce() {
        show(imageUrl = OFF_URL, archiveImageUrl = MISSING_ARCHIVE_URL)

        awaitTile(GREEN)
        assertEquals(listOf(MISSING_ARCHIVE_URL, OFF_URL), requestedNow())
    }

    @Test
    fun withoutAnArchiveAddressTheUsualAddressIsLoaded() {
        show(imageUrl = OFF_URL, archiveImageUrl = null)

        awaitTile(GREEN)
        assertEquals(listOf(OFF_URL), requestedNow())
    }

    /** The archive rule is exact: a lookalike address is never contacted, not even to fail. */
    @Test
    fun anArchiveAddressOffTheAllowlistIsNeverRequested() {
        val lookalike = ARCHIVE_URL.replace("openfoodfacts-images.s3.eu-west-3", "openfoodfacts-images.s3.us-east-1")
        show(imageUrl = OFF_URL, archiveImageUrl = lookalike)

        awaitTile(GREEN)
        assertEquals(listOf(OFF_URL), requestedNow())
    }

    /** Both fail: the initials stay and nothing is retried in a loop. */
    @Test
    fun whenBothAddressesFailTheInitialsStayAndEachIsAskedOnce() {
        show(imageUrl = MISSING_OFF_URL, archiveImageUrl = MISSING_ARCHIVE_URL)

        compose.waitUntil(timeoutMillis = 5_000) { requestedNow().size >= 2 }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals(listOf(MISSING_ARCHIVE_URL, MISSING_OFF_URL), requestedNow())
        val pixel = centrePixel()
        assertEquals("the tile must show neither test photo", false, pixel.isColour(RED) || pixel.isColour(GREEN))
    }

    private fun show(imageUrl: String?, archiveImageUrl: String?) {
        compose.setContent {
            JustTheCarbsTheme {
                // Centred: the system-bar scrim paints over the top of a test window, so a tile
                // at the top-left would read as black whatever it draws.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.testTag(TILE_TAG)) {
                        SearchThumbnail(imageUrl = imageUrl, archiveImageUrl = archiveImageUrl, name = "Nutella")
                    }
                }
            }
        }
    }

    private fun requestedNow(): List<String> = synchronized(requested) { requested.toList() }

    private fun awaitTile(colour: Int) {
        compose.waitUntil(timeoutMillis = 5_000) { centrePixel().isColour(colour) }
    }

    private fun centrePixel(): Int {
        val bitmap = compose.onNodeWithTag(TILE_TAG).captureToImage().asAndroidBitmap()
        return bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    }

    private fun Int.isColour(colour: Int): Boolean =
        Math.abs(android.graphics.Color.red(this) - android.graphics.Color.red(colour)) < 30 &&
            Math.abs(android.graphics.Color.green(this) - android.graphics.Color.green(colour)) < 30 &&
            Math.abs(android.graphics.Color.blue(this) - android.graphics.Color.blue(colour)) < 30

    private class ControlledImages(private val requested: MutableList<String>) : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val url = chain.request.data.toString()
            requested += url
            if ("missing" in url || "/404." in url) {
                return ErrorResult(null, chain.request, IOException("controlled failure"))
            }
            val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(if (ARCHIVE_HOST in url) RED else GREEN)
            return SuccessResult(image = bitmap.asImage(), request = chain.request, dataSource = DataSource.MEMORY)
        }
    }

    private companion object {
        const val TILE_TAG = "search_thumbnail_archive_test_tile"
        const val ARCHIVE_HOST = "openfoodfacts-images.s3.eu-west-3.amazonaws.com"
        const val ARCHIVE_URL =
            "https://openfoodfacts-images.s3.eu-west-3.amazonaws.com/data/301/762/042/9484/149.400.jpg"
        const val MISSING_ARCHIVE_URL =
            "https://openfoodfacts-images.s3.eu-west-3.amazonaws.com/data/301/762/042/9484/404.400.jpg"
        const val OFF_URL =
            "https://images.openfoodfacts.org/images/products/301/762/042/9484/front_fr.409.200.jpg"
        const val MISSING_OFF_URL =
            "https://images.openfoodfacts.org/images/products/301/762/042/9484/missing_fr.409.200.jpg"
        val RED = android.graphics.Color.rgb(200, 40, 40)
        val GREEN = android.graphics.Color.rgb(40, 180, 60)
    }
}
