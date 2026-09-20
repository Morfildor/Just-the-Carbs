package app.justthecarbs.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The share-intent rules, pinned where they can be reverted and noticed.
 *
 * Two of these are safety rules rather than conveniences: a share must not skip the welcome
 * carousel, and a share the app cannot use must not be silently swallowed. Both are one-line
 * changes away from being wrong, and neither is observable from a device test without reinstalling
 * the app between cases.
 */
class SharedImageRequestTest {

    private fun decide(
        action: String? = SharedImageRequest.ACTION_SEND,
        mimeType: String? = "image/jpeg",
        uri: String? = "content://media/external/images/1",
        hasSeenOnboarding: Boolean = true,
    ) = SharedImageRequest.from(action, mimeType, uri, hasSeenOnboarding)

    @Test
    fun `a shared jpeg asks the user what it contains`() {
        assertEquals(
            SharedImageRequest.Decision.Ask("content://media/external/images/1"),
            decide(),
        )
    }

    @Test
    fun `a wildcard image type is accepted`() {
        // Senders routinely declare `image/*` rather than a concrete subtype.
        assertEquals(SharedImageRequest.Decision.Ask("content://x"), decide(mimeType = "image/*", uri = "content://x"))
    }

    @Test
    fun `a subtype carrying parameters is still an image`() {
        assertEquals(SharedImageRequest.Decision.Ask("content://x"), decide(mimeType = "image/jpeg; charset=utf-8", uri = "content://x"))
    }

    @Test
    fun `an uppercase type is still an image`() {
        assertEquals(SharedImageRequest.Decision.Ask("content://x"), decide(mimeType = "IMAGE/PNG", uri = "content://x"))
    }

    @Test
    fun `a missing stream uri is invalid rather than a crash`() {
        assertEquals(SharedImageRequest.Decision.Invalid, decide(uri = null))
        assertEquals(SharedImageRequest.Decision.Invalid, decide(uri = "   "))
    }

    @Test
    fun `a non-image share is refused before anything is asked`() {
        // Refused here, not at staging: otherwise the user answers "barcode or label?" about a
        // PDF and only then learns there was never an image.
        assertEquals(SharedImageRequest.Decision.Invalid, decide(mimeType = "application/pdf"))
        assertEquals(SharedImageRequest.Decision.Invalid, decide(mimeType = "text/plain"))
        assertEquals(SharedImageRequest.Decision.Invalid, decide(mimeType = null))
    }

    @Test
    fun `a fully wildcard type is not treated as an image`() {
        assertEquals(SharedImageRequest.Decision.Invalid, decide(mimeType = "*/*"))
    }

    @Test
    fun `a bare image prefix with no subtype is not an image`() {
        assertEquals(SharedImageRequest.Decision.Invalid, decide(mimeType = "image/"))
    }

    @Test
    fun `an unrelated action is not a share`() {
        // The ordering rule: "is this ours?" precedes "is it usable?", so an ordinary launch or a
        // launcher shortcut can never surface a share error.
        assertEquals(SharedImageRequest.Decision.NotAShare, decide(action = null))
        assertEquals(SharedImageRequest.Decision.NotAShare, decide(action = "android.intent.action.MAIN"))
        assertEquals(
            SharedImageRequest.Decision.NotAShare,
            decide(action = StartupDestination.ACTION_SCAN_BARCODE),
        )
    }

    @Test
    fun `a malformed unrelated action is not a share even with no uri`() {
        assertEquals(SharedImageRequest.Decision.NotAShare, decide(action = "com.example.OTHER", uri = null, mimeType = null))
    }

    @Test
    fun `send multiple is not accepted`() {
        // Out of scope by decision, and refused rather than partially handled.
        assertEquals(
            SharedImageRequest.Decision.NotAShare,
            decide(action = "android.intent.action.SEND_MULTIPLE"),
        )
    }

    @Test
    fun `a share arriving before onboarding is held, never dropped and never acted on`() {
        // The rule. A shortcut arriving early is discarded because the user can tap it again; a
        // share cannot be re-sent without leaving the app, so it is retained for after the
        // carousel. What it must never be is `Ask` — that would put a chooser in front of someone
        // who has not yet been told what this app is.
        assertEquals(
            SharedImageRequest.Decision.Hold("content://media/external/images/1"),
            decide(hasSeenOnboarding = false),
        )
    }

    @Test
    fun `an unusable share before onboarding is invalid rather than held`() {
        // Validity is decided before onboarding, so nothing useless is carried across the carousel
        // to fail afterwards — which would show an error attributed to nothing the user just did.
        assertEquals(SharedImageRequest.Decision.Invalid, decide(mimeType = "text/plain", hasSeenOnboarding = false))
        assertEquals(SharedImageRequest.Decision.Invalid, decide(uri = null, hasSeenOnboarding = false))
    }

    @Test
    fun `an unrelated action before onboarding is still not a share`() {
        assertEquals(
            SharedImageRequest.Decision.NotAShare,
            decide(action = "android.intent.action.MAIN", hasSeenOnboarding = false),
        )
    }

    @Test
    fun `the resting delivery carries no uri and cannot open the chooser`() {
        assertEquals(null, SharedImageDelivery.NONE.uri)
        assertEquals(0L, SharedImageDelivery.NONE.id)
    }

    @Test
    fun `two shares of the same image are distinct deliveries`() {
        // Sharing the same picture twice is ordinary. Keyed on the URI alone the second would not
        // re-run the effect and would appear to do nothing — the defect measured on device for the
        // Barcode shortcut, recorded on StartupRequest.id.
        val first = SharedImageDelivery("content://same", 1L)
        val second = SharedImageDelivery("content://same", 2L)
        assert(first != second)
    }
}
