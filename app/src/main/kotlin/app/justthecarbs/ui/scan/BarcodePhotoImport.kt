package app.justthecarbs.ui.scan

import app.justthecarbs.domain.ImportedBarcodeSelection
import java.util.concurrent.atomic.AtomicLong

/**
 * What the barcode scanner is doing about a chosen photograph, as one value.
 *
 * Held as a single state rather than as separate `reading` / `failed` / `choices` booleans so the
 * impossible combinations — reading *and* showing a choice, failed *and* reading — are
 * unconstructible rather than merely unreached. The live camera is paused for exactly the states
 * that are not [Idle], which is what stops a camera detection and a photo result racing each other
 * into the same one-shot navigation.
 */
internal sealed interface BarcodePhotoImportState {
    /** Nothing imported; the live camera owns the screen. */
    data object Idle : BarcodePhotoImportState

    /** A photograph is being copied and recognised. */
    data object Reading : BarcodePhotoImportState

    /** Recognised, and nothing usable was in it. */
    data object NoBarcode : BarcodePhotoImportState

    /**
     * Several distinct valid codes. The user picks; the app must not.
     *
     * @see ImportedBarcodeSelection
     */
    data class Choosing(val barcodes: List<String>) : BarcodePhotoImportState
}

/**
 * Newest-wins identity for an import, and the single question a finished import must answer before
 * it may touch anything.
 *
 * ## Why this is not simply an `AtomicLong` inline in the composable
 *
 * It is one, plus one rule — and the rule is the one this whole feature's staleness guarantee rests
 * on: **a result may become state only if its generation is still the current one.** This repo's
 * own notes record twice shipping exactly this kind of rule as a local `val` inside a composable
 * that binds a camera, where no JVM test can reach it and reverting it fails nothing. The label
 * scanner satisfies the same requirement through `CaptureEvidenceCoordinator`, which the barcode
 * scanner has no equivalent of; this is that coordinator's one relevant rule, on its own.
 *
 * Cancellation is deliberately *not* the guarantee. A staging copy that ignores its cancellation
 * poll, or an ML Kit read already in flight, still completes — so the decisive check is this one,
 * applied at the single point a result would become visible. Cancellation is the optimisation.
 */
internal class ImportGeneration {
    private val current = AtomicLong(0L)

    /** Starts a new import, invalidating every one before it. Returns the new generation. */
    fun begin(): Long = current.incrementAndGet()

    /** Whether [generation] is still the import the screen is waiting for. */
    fun isCurrent(generation: Long): Boolean = current.get() == generation

    /**
     * Invalidates whatever is in flight without starting anything.
     *
     * Used when the user leaves the sheet, dismisses a failure, or closes the screen: an import
     * abandoned that way must not land afterwards, and there is no successor to attribute it to.
     */
    fun invalidate() {
        current.incrementAndGet()
    }
}
