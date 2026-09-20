package app.justthecarbs.ocr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * The staging copy's own rules: what reaches disk, what is refused, and how a refusal is named.
 *
 * Every case here is a failure mode of a real picked image — a revoked grant, a zero-byte provider
 * entry, a video selected through a document picker, a copy interrupted mid-read. The reason each is
 * distinguished rather than collapsed into one boolean is that the screen says something different
 * for each, and "we could not read that photo" is the only honest thing to say when nothing was read
 * — as opposed to `NotFound`, which claims the photograph was read and had no table in it.
 */
class ImportedImageStagingTest {

    private fun stage(
        bytes: ByteArray,
        cancelled: () -> Boolean = { false },
    ): Pair<ImportedImageStaging.Result, ByteArray> {
        val out = ByteArrayOutputStream()
        val result = ImportedImageStaging.copy(ByteArrayInputStream(bytes), out, cancelled)
        return result to out.toByteArray()
    }

    @Test
    fun `a photograph is copied byte for byte`() {
        // Byte-identical matters beyond tidiness: the EXIF orientation tag lives in these bytes, and
        // StillImageLoader reads it from the staged file. Any re-encode would drop it and deliver a
        // sideways image to the parser's geometry stages.
        val source = ByteArray(200_000) { (it % 251).toByte() }

        val (result, written) = stage(source)

        assertEquals(ImportedImageStaging.Result.Staged(200_000L), result)
        assertArrayEquals("the staged bytes must be the source bytes", source, written)
    }

    @Test
    fun `an empty stream is a failure, not an empty success`() {
        val (result, written) = stage(ByteArray(0))

        assertEquals(
            ImportedImageStaging.Result.Failed(ImportedImageStaging.Failure.EMPTY),
            result,
        )
        assertEquals(0, written.size)
    }

    @Test
    fun `an image larger than the ceiling is refused`() {
        // One byte over, so this pins the boundary rather than some comfortable margin inside it.
        val source = ByteArray((ImportedImageStaging.MAX_BYTES + 1).toInt())

        val (result, _) = stage(source)

        assertEquals(
            ImportedImageStaging.Result.Failed(ImportedImageStaging.Failure.TOO_LARGE),
            result,
        )
    }

    @Test
    fun `an image exactly at the ceiling is accepted`() {
        val source = ByteArray(ImportedImageStaging.MAX_BYTES.toInt())

        val (result, _) = stage(source)

        assertEquals(ImportedImageStaging.Result.Staged(ImportedImageStaging.MAX_BYTES), result)
    }

    @Test
    fun `a stream that fails before yielding anything is unreadable`() {
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("revoked")
            override fun read(b: ByteArray): Int = throw IOException("revoked")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("revoked")
        }

        val result = ImportedImageStaging.copy(failing, ByteArrayOutputStream())

        assertEquals(
            "a grant that never resolved is UNREADABLE, not a truncated copy",
            ImportedImageStaging.Result.Failed(ImportedImageStaging.Failure.UNREADABLE),
            result,
        )
    }

    @Test
    fun `a stream that dies mid copy reports an incomplete copy, never a success`() {
        // The dangerous case: a partial JPEG decodes to a torn image, and a torn image is exactly
        // the input that produces a confidently wrong reading. It must never be handed on as if it
        // were a whole photograph.
        val failing = object : InputStream() {
            private var served = 0
            override fun read(): Int = throw IOException("died")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (served >= 8192) throw IOException("died")
                served += 4096
                return 4096
            }
        }

        val result = ImportedImageStaging.copy(failing, ByteArrayOutputStream())

        assertEquals(
            ImportedImageStaging.Result.Failed(ImportedImageStaging.Failure.INCOMPLETE),
            result,
        )
    }

    @Test
    fun `a cancelled copy stops promptly and is never reported as staged`() {
        val source = ByteArray(4 * 1024 * 1024)
        var polls = 0

        val (result, written) = stage(source, cancelled = { polls++ >= 2 })

        assertEquals(
            ImportedImageStaging.Result.Failed(ImportedImageStaging.Failure.INCOMPLETE),
            result,
        )
        assertTrue(
            "cancellation must stop the copy, not merely relabel its result — " +
                "${written.size} bytes of ${source.size} were written",
            written.size < source.size,
        )
    }
}
