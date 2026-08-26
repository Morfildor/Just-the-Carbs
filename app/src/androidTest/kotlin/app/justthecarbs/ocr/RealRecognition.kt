package app.justthecarbs.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs the real bundled recognizer synchronously, for instrumented measurement suites.
 *
 * Shared so that several suites cannot drift into slightly different recognizer configurations and
 * then report incomparable numbers — which would quietly invalidate any before/after claim made
 * across them.
 */
object RealRecognition {

    fun recognise(bitmap: Bitmap, timeoutSeconds: Long = 90): Text {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val latch = CountDownLatch(1)
            var out: Text? = null
            var failure: Exception? = null
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { out = it; latch.countDown() }
                .addOnFailureListener { failure = it; latch.countDown() }
            check(latch.await(timeoutSeconds, TimeUnit.SECONDS)) { "recognition timed out" }
            failure?.let { throw it }
            return out!!
        } finally {
            recognizer.close()
        }
    }
}
