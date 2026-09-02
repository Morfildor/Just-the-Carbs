package app.justthecarbs.ocr

import java.io.File
import java.util.zip.ZipFile

/**
 * Reads a finished archive back and confirms every entry is complete.
 *
 * ## The failure this exists for
 *
 * The first evidence bundle uploaded from the 2026-09-01 third phone session was **58 MB, truncated
 * mid-entry, and had no central directory**. The replacement, from the same captures, was 83 MB and
 * complete. Nothing in the app noticed: the archive was written straight to the shareable filename
 * and the share sheet opened on whatever bytes existed.
 *
 * A truncated archive is worse than a missing one. It looks like evidence, it is named like
 * evidence, and the person it is sent to discovers it is unreadable only after the phone session
 * they would have needed to repeat is over.
 *
 * ## What "complete" means here
 *
 * Every entry is decompressed in full and its bytes counted. That is deliberately stronger than
 * opening the file and listing entries:
 *
 * - **Opening** a [ZipFile] parses the central directory, which catches the missing-directory case
 *   but nothing else — the directory is written last, so a file that has one is *usually* complete.
 * - **Reading every entry** additionally verifies each deflate stream and its CRC, because
 *   `java.util.zip` throws on a CRC mismatch when a stream is read to its end. That is what catches
 *   an entry that was corrupted rather than cut short.
 *
 * Reading the whole archive costs a full decompression pass. That is acceptable precisely because
 * this runs only at export — an explicit, infrequent user action with no latency budget — and never
 * on the scan path.
 *
 * ## Debug-only, like everything it verifies
 *
 * Reachable only from [ScanEvidenceExport], which R8 removes from a release build entirely.
 */
internal object ZipIntegrity {

    /**
     * Whether [archive] is a readable zip whose every entry decompresses completely.
     *
     * False on any failure — a truncated file, a bad CRC, a missing central directory, an I/O error.
     * The caller has one response to all of them (refuse to share and say so), so distinguishing
     * them here would add a taxonomy nothing acts on.
     *
     * An archive with **no entries** is false too. An empty zip is well-formed, but as evidence it
     * is indistinguishable from a failure that produced nothing, and offering it to share would be
     * the same dead end as the truncated one.
     */
    fun isComplete(archive: File): Boolean = runCatching {
        ZipFile(archive).use { zip ->
            val entries = zip.entries().toList()
            if (entries.isEmpty()) return false

            val buffer = ByteArray(BUFFER_BYTES)
            entries.forEach { entry ->
                if (entry.isDirectory) return@forEach
                var read: Long = 0
                // Read to the end of every stream: `java.util.zip` verifies the CRC as the last
                // bytes are consumed, so stopping early would skip the check this exists for.
                zip.getInputStream(entry).use { stream ->
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        read += n
                    }
                }
                // A declared size that the stream could not deliver is a truncation the CRC check
                // would not necessarily reach. `size` is -1 when the archive does not declare one,
                // in which case there is nothing to compare and the CRC stands alone.
                if (entry.size >= 0 && read != entry.size) return false
            }
            true
        }
    }.getOrDefault(false)

    /** Read buffer. Large enough that a multi-megabyte PNG is not read in thousands of syscalls. */
    private const val BUFFER_BYTES = 64 * 1024
}
