package app.justthecarbs.ui

/**
 * What an incoming `ACTION_SEND` amounts to, decided as a value rather than as a branch inside
 * `MainActivity`.
 *
 * ## Why this is its own unit
 *
 * The same reasoning [StartupDestination] records, and the same reasoning
 * [app.justthecarbs.ocr.ImportedPhotoIntake] and
 * [app.justthecarbs.domain.ImportedBarcodeSelection] record before it: a rule that lives only
 * inside an Activity or a composable is a rule no JVM test can reach, and **a rule no test can
 * reach is a rule that can be silently reverted.** This repo has shipped that mistake twice.
 *
 * Two rules here are of exactly that kind:
 *
 * 1. **A share never skips onboarding.** Identical in spirit to [StartupDestination.from]'s
 *    shortcut gate, and differing in one decisive respect: a shortcut that arrives too early is
 *    *discarded* (the user can tap it again), whereas a share is the only copy of something the
 *    user sent from another app and cannot re-send without leaving. So it is **held**, not
 *    dropped — see [Decision.Hold].
 * 2. **The app never guesses what the image contains.** A screenshot of a barcode and a photo of
 *    a nutrition table are the same MIME type and the same pixels as far as this decision is
 *    concerned. Nothing here inspects content, and nothing downstream may either: the image goes
 *    to a chooser, and the user says which it is.
 *
 * ## What is deliberately not handled
 *
 * `ACTION_SEND_MULTIPLE` is not accepted (scope). A non-image MIME type is not accepted, and the
 * declared type is treated as a *filter* rather than a guarantee — exactly as the photo picker's
 * `ImageOnly` is — because staging checks the bytes regardless. A missing stream URI is an
 * [Decision.Invalid], not a crash: a malformed intent is ordinary input from an app this one does
 * not control.
 */
object SharedImageRequest {

    /** Android's own action and extra names, so nothing here depends on an Android import. */
    const val ACTION_SEND = "android.intent.action.SEND"

    /** What to do with an incoming intent. */
    sealed interface Decision {
        /**
         * A usable single image. [uri] is the sender's content URI, which carries only a temporary
         * read grant and is staged immediately rather than held.
         */
        data class Ask(val uri: String) : Decision

        /**
         * A usable image that arrived before onboarding was complete.
         *
         * Held rather than dropped: the user sent this from another app and has no way to re-send
         * it without leaving Just the Carbs, so discarding it would lose the thing they asked for.
         * The carousel shows first and this is replayed after it.
         */
        data class Hold(val uri: String) : Decision

        /**
         * The intent is a share this app should answer, and there is nothing usable in it — no
         * stream, or a type it cannot read. The user is told, rather than left on a screen that
         * silently ignored them.
         */
        data object Invalid : Decision

        /** Not a share at all: an ordinary launch, a shortcut, or an action this does not own. */
        data object NotAShare : Decision
    }

    /**
     * Decides what [action] carrying [mimeType] and [uri] means.
     *
     * [uri] is a string rather than a `Uri` so this stays Android-free and plain-JVM testable,
     * matching [StartupDestination] and every other decision object in this codebase. The caller
     * converts.
     *
     * The ordering is load-bearing. **"Is this even ours?" is answered before "is it usable?"**,
     * so an unrelated action can never produce [Decision.Invalid] and put an error in front of
     * someone who did not share anything — the same shape of rule as
     * `PackageBasisResolver`'s "is there a number at all?" preceding "what is it measured per?".
     *
     * Onboarding is checked **last**, after validity, so a malformed share arriving before the
     * carousel is discarded as malformed rather than held and then found to be useless later —
     * which would put an error on screen immediately after a first-run welcome, attributed to
     * nothing the user can remember doing.
     */
    fun from(
        action: String?,
        mimeType: String?,
        uri: String?,
        hasSeenOnboarding: Boolean,
    ): Decision {
        if (action != ACTION_SEND) return Decision.NotAShare
        if (!isImage(mimeType)) return Decision.Invalid
        if (uri.isNullOrBlank()) return Decision.Invalid
        return if (hasSeenOnboarding) Decision.Ask(uri) else Decision.Hold(uri)
    }

    /**
     * Whether [mimeType] names an image.
     *
     * A wildcard image type and any concrete `image/jpeg`-style subtype are accepted; a fully
     * wildcard type is not. Senders do use a bare wildcard, and accepting it would mean opening
     * the chooser for a PDF or a video
     * and only discovering at staging that there was never an image — after the user had answered
     * a question about what the picture contained. Refusing here states it once, immediately,
     * before asking anything.
     *
     * Case and parameters (`image/jpeg; charset=...`) are tolerated because senders produce both.
     */
    private fun isImage(mimeType: String?): Boolean {
        val type = mimeType?.substringBefore(';')?.trim()?.lowercase() ?: return false
        return type == "image/*" || (type.startsWith("image/") && type.length > "image/".length)
    }
}

/**
 * One share delivery: what was shared, plus which delivery it was.
 *
 * The [id] exists for [StartupRequest]'s reason, measured on this project's own device: sharing the
 * *same* image twice produces an identical [uri], so an effect keyed on the value alone would not
 * re-run and the second share would appear to do nothing. Each delivery gets a fresh id.
 *
 * [NONE] is the resting value and carries id 0, so an ordinary launch never opens the chooser.
 */
data class SharedImageDelivery(
    val uri: String? = null,
    val id: Long = 0L,
) {
    companion object {
        /** No share: an ordinary launch. */
        val NONE = SharedImageDelivery()
    }
}
