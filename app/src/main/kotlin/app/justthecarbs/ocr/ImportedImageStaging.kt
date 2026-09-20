package app.justthecarbs.ocr

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Turns a picked image into the **same cache file a camera capture produces**, so both inputs reach
 * [LabelAnalyzer.analyzeStillRetaining] as one kind of thing.
 *
 * ## Why staging, rather than teaching the pipeline about URIs
 *
 * The still path is file-shaped from end to end, and not incidentally: [StillImageLoader] decodes a
 * path and reads its EXIF tag from the same path; `LabelAnalyzer.runStill` reads `file.length()` for
 * the evidence record, falls back to `InputImage.fromFilePath` when the decode fails, and hands
 * ownership of the very same file to `ScanEvidenceRecorder.consumeCaptureAsync`, which **renames**
 * it into the evidence folder. Threading a `Uri` alongside the `File` through `StillRequest` would
 * fork every one of those five things into two cases, inside the class this app's safety rules run
 * in — and a fork there is exactly what the brief forbids, because "one pipeline" would then be a
 * claim about two code paths that happen to agree rather than a property of the code.
 *
 * Staging converges the two inputs **before** the analyzer instead. Once a picked image is a JPEG in
 * `cacheDir`, the analyzer cannot tell it from a shutter press — there is no branch to get wrong,
 * no second parser to drift, and every downstream guarantee (EXIF correction, whole-frame
 * recognition, deletion, evidence ownership) is inherited rather than reimplemented.
 *
 * ## What is deliberately NOT done here
 *
 * - **No decoding.** A `Bitmap` is never created, so a 50 MP phone photo costs a byte copy rather
 *   than ~200 MB of ARGB_8888. The single decode that does happen is [StillImageLoader]'s, the same
 *   one a capture pays for, at the same point in the pipeline.
 * - **No re-encoding and no EXIF rewriting.** The bytes are copied verbatim, so the orientation tag
 *   arrives intact and [StillImageLoader] applies it exactly as it does for a capture. Re-encoding
 *   would strip that tag and silently deliver sideways images, which is the one failure the parser's
 *   geometry stages cannot recover from.
 * - **No pre-crop.** For the same reason `StillImageLoader` no longer crops: cutting to an apparent
 *   table before recognition cost both canaries.
 *
 * Deliberately Android-free (streams, not `ContentResolver`) so the copy's own rules — the size
 * ceiling, the truncation check, the failure vocabulary — are plain-JVM testable without Robolectric,
 * which this codebase does not use. The caller supplies the stream.
 */
internal object ImportedImageStaging {

    /**
     * The largest image accepted, in bytes.
     *
     * This is a **staging** bound, not a quality judgement: the file is copied, never decoded here,
     * so the cost being bounded is cache space and copy time. It is generous on purpose — a 200 MP
     * phone photograph lands well inside it, and refusing a real photograph the user chose would be
     * a worse failure than a slow one. What it stops is a multi-gigabyte video or archive selected
     * through a document picker that does not honour the image filter, which would otherwise fill
     * the cache partition before anything noticed it was not a photograph.
     */
    const val MAX_BYTES: Long = 96L * 1024 * 1024

    /** Why a staging attempt produced no file. Each maps to copy the user can act on. */
    enum class Failure {
        /** The provider gave no stream at all — a revoked grant, or a URI that no longer resolves. */
        UNREADABLE,

        /** The stream ended immediately. An empty file is not a photograph. */
        EMPTY,

        /** Larger than [MAX_BYTES]. */
        TOO_LARGE,

        /** The copy began and did not complete — no space, or the provider died mid-read. */
        INCOMPLETE,
    }

    sealed interface Result {
        /** [bytes] is what actually landed on disk, for the evidence record and for diagnostics. */
        data class Staged(val bytes: Long) : Result
        data class Failed(val reason: Failure) : Result
    }

    /**
     * Copies [source] into [destination], bounded and verified.
     *
     * The caller owns both streams and must close them; this function does not, because on Android
     * the input comes from a `ContentResolver` whose stream is `use`-scoped by the caller together
     * with the URI grant it depends on. Closing one half of that here would split the lifetime of a
     * resource across two files.
     *
     * [cancelled] is polled between chunks so a picker result abandoned mid-copy (the screen closed,
     * a newer photo chosen) stops promptly rather than copying a hundred megabytes nobody is waiting
     * for. A cancelled copy reports [Failure.INCOMPLETE] and the caller deletes the partial file —
     * it is never distinguished as a success, because a partial JPEG decodes to a torn image and a
     * torn image is precisely the input that produces a confident-wrong reading.
     */
    fun copy(
        source: InputStream,
        destination: OutputStream,
        cancelled: () -> Boolean = { false },
    ): Result {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L

        while (true) {
            if (cancelled()) return Result.Failed(Failure.INCOMPLETE)

            val read = try {
                source.read(buffer)
            } catch (_: IOException) {
                // Distinguished from UNREADABLE: the stream opened and then failed, which is a
                // truncated copy on disk rather than a URI that never resolved.
                return Result.Failed(if (total == 0L) Failure.UNREADABLE else Failure.INCOMPLETE)
            }
            if (read < 0) break

            total += read
            // Checked against the running total BEFORE writing, so the ceiling bounds what reaches
            // the disk rather than merely what is reported afterwards.
            if (total > MAX_BYTES) return Result.Failed(Failure.TOO_LARGE)

            try {
                destination.write(buffer, 0, read)
            } catch (_: IOException) {
                return Result.Failed(Failure.INCOMPLETE)
            }
        }

        try {
            destination.flush()
        } catch (_: IOException) {
            return Result.Failed(Failure.INCOMPLETE)
        }

        // An empty result is a failure, not an empty success. `BitmapFactory` returns null for a
        // zero-byte file and the analyzer would report that as an ordinary `NotFound` — telling the
        // user their photograph has no nutrition table in it, when in fact nothing was ever read.
        if (total == 0L) return Result.Failed(Failure.EMPTY)

        return Result.Staged(total)
    }
}
