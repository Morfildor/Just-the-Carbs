package app.justthecarbs.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The archive-completeness check, against the shapes a real truncated bundle takes.
 *
 * The first evidence bundle uploaded from the 2026-09-01 third phone session was 58 MB, cut off
 * mid-entry, with no central directory — and the app shared it without noticing. These pin that the
 * same file would now be refused.
 */
class ZipIntegrityTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A well-formed archive of [entries], as [ScanEvidenceExport] writes one. */
    private fun archive(vararg entries: Pair<String, ByteArray>): File {
        val file = temp.newFile("evidence-${entries.size}-${entries.hashCode()}.zip")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    /** Compressible bytes, so truncating the file genuinely cuts a deflate stream. */
    private fun payload(size: Int) = ByteArray(size) { (it % 251).toByte() }

    @Test
    fun `a complete archive passes`() {
        val file = archive(
            "20260901-225530-249/meta.txt" to payload(4_000),
            "20260901-225530-249/passA.png" to payload(300_000),
        )
        assertTrue(ZipIntegrity.isComplete(file))
    }

    @Test
    fun `an archive cut off mid-entry is refused`() {
        val file = archive("capture/passA.png" to payload(400_000))
        val whole = file.readBytes()
        // 58 of 83 MB is what the device produced; the proportion is what matters, not the size.
        val truncated = temp.newFile("truncated.zip")
        truncated.writeBytes(whole.copyOf((whole.size * 0.7).toInt()))

        assertFalse("a truncated archive must not be shared", ZipIntegrity.isComplete(truncated))
    }

    @Test
    fun `an archive missing its central directory is refused`() {
        val file = archive("capture/meta.txt" to payload(2_000))
        val whole = file.readBytes()
        // The central directory is written last; dropping the tail removes it while leaving the
        // entry's own local header and data intact. That is the exact shape the device produced.
        val headless = temp.newFile("headless.zip")
        headless.writeBytes(whole.copyOf(whole.size - 64))

        assertFalse(ZipIntegrity.isComplete(headless))
    }

    @Test
    fun `an archive whose entry data was corrupted is refused`() {
        val file = archive("capture/passA.png" to payload(200_000))
        val bytes = file.readBytes()
        // Flip a run of bytes in the middle of the deflate stream, leaving every header intact, so
        // only reading the entry to its end and checking the CRC can catch it. This is the case an
        // "can I open the zip and list its entries?" check would pass.
        for (i in 500 until 900) bytes[i] = (bytes[i].toInt() xor 0xFF).toByte()
        val corrupted = temp.newFile("corrupted.zip")
        corrupted.writeBytes(bytes)

        assertFalse(ZipIntegrity.isComplete(corrupted))
    }

    @Test
    fun `an empty archive is refused`() {
        // Well-formed, and useless as evidence — indistinguishable from a failure that produced
        // nothing, so sharing it would be the same dead end as sharing a truncated one.
        assertFalse(ZipIntegrity.isComplete(archive()))
    }

    @Test
    fun `a file that is not a zip at all is refused`() {
        val notAZip = temp.newFile("notazip.zip")
        notAZip.writeBytes(payload(10_000))
        assertFalse(ZipIntegrity.isComplete(notAZip))
    }

    @Test
    fun `a missing file is refused rather than throwing`() {
        assertFalse(ZipIntegrity.isComplete(File(temp.root, "absent.zip")))
    }

    @Test
    fun `an archive of several captures passes as a whole`() {
        val file = archive(
            "20260901-225530-249/meta.txt" to payload(900),
            "20260901-225530-249/capture.jpg" to payload(250_000),
            "20260901-225617-066/meta.txt" to payload(900),
            "20260901-225617-066/passA.png" to payload(500_000),
        )
        assertTrue(ZipIntegrity.isComplete(file))
    }
}
