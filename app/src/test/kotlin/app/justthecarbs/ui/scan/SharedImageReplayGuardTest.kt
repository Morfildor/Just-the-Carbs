package app.justthecarbs.ui.scan

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A share is not a picker result, and the two replay guards must not be conflated.**
 *
 * This pins a defect that was *measured on a device*, not imagined, and that every JVM and
 * instrumented test in this repository was blind to.
 *
 * ## What happened
 *
 * The share was first wired to reuse `consumedPhotoToken` — the guard that stops
 * `rememberLauncherForActivityResult` re-delivering its last result to a recreated composition.
 * It looked like the same question. It is not.
 *
 * A keyed `LaunchedEffect` restarts when its composable leaves and re-enters composition, and a
 * navigation transition alone is enough to cause that. On the barcode scanner the import effect
 * therefore ran twice for a single delivery: the first run set the screen to *Reading*, wrote the
 * consumed token and was then cancelled; the restarted run — the only one still alive — found the
 * token its own cancelled predecessor had written and refused itself as `ALREADY_CONSUMED`. The
 * screen sat on *"Reading the photo you chose…"* forever, with no way out but Back.
 *
 * The token guard could not have caught it, because the token guard is what *caused* it.
 *
 * ## The rule
 *
 * A share passes `lastConsumedToken = null`, and its own once-per-share protection is a separate
 * piece of state (`deliveredShare` / `importedShare`) plus the Activity consuming the share the
 * moment the chooser is answered. A picker result keeps the token guard unchanged.
 *
 * Structural, following the convergence tests in this package: the property is about which
 * argument reaches a call, and no behavioural JVM test can reach a composable that binds a camera.
 */
class SharedImageReplayGuardTest {

    private val scanner = sourceFile("app/src/main/kotlin/app/justthecarbs/ui/scan/ScannerScreen.kt")
    private val labelScanner =
        sourceFile("app/src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt")

    private fun codeOf(source: String): String = source.lineSequence()
        .map { it.substringBefore("//") }
        .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
        .joinToString("\n")

    private fun sourceFile(path: String): String {
        val candidates = listOf(File(path), File("../$path"), File(path.removePrefix("app/")))
        val found = candidates.firstOrNull { it.isFile }
        requireNotNull(found) { "could not locate $path from ${File(".").absolutePath}" }
        return found.readText()
    }

    @Test
    fun `the barcode import exempts a share from the picker replay token`() {
        // Reverting this to a bare `consumedPhotoToken` reproduces the stuck-on-Reading defect.
        assertTrue(
            "the barcode import must not apply the picker's replay token to a share",
            codeOf(scanner).contains(Regex("""lastConsumedToken\s*=\s*if \(isShare\) null else consumedPhotoToken""")),
        )
    }

    @Test
    fun `the label import exempts a share from the picker replay token`() {
        assertTrue(
            "the label import must not apply the picker's replay token to a share",
            codeOf(labelScanner).contains(Regex("""lastConsumedToken\s*=\s*if \(fromShare\) null else consumedPhotoToken""")),
        )
    }

    @Test
    fun `each screen still guards a share against being delivered twice`() {
        // Exempting the share from the token guard removes one protection, so the replacement has
        // to exist: a share is delivered once per screen, compared by value so a genuinely
        // different second share still imports.
        assertTrue(
            "the barcode screen must track the share it has delivered",
            codeOf(scanner).contains(Regex("""var deliveredShare by rememberSaveable""")),
        )
        assertTrue(
            "the label screen must track the share it has imported",
            codeOf(labelScanner).contains(Regex("""var importedShare by rememberSaveable""")),
        )
    }

    @Test
    fun `the share guards survive recreation`() {
        // `remember` would be discarded by exactly the recreation a rotation causes, which is when
        // a re-delivery would otherwise happen — so these must be saveable, not merely remembered.
        for ((name, code) in listOf("barcode" to codeOf(scanner), "label" to codeOf(labelScanner))) {
            // Captures whatever remember-flavoured call actually holds it, so a plain `remember`
            // is reported as itself rather than silently matched as a prefix of the saveable one.
            val guard = Regex("""var (?:deliveredShare|importedShare) by (\w+)""").find(code)
            requireNotNull(guard) { "$name screen has no share guard at all" }
            assertEquals(
                "$name screen's share guard must survive recreation",
                "rememberSaveable",
                guard.groupValues[1],
            )
        }
    }

    @Test
    fun `the picker path keeps its own replay guard untouched`() {
        // The exemption is for shares only. A picker result must still be compared against the
        // consumed token, or the original defect this guard exists for comes back.
        // Written from `ImportedImageSource.token` since the ownership correction: the URI string
        // for a picked photo, the cache path for a share. Both are per-delivery and both are
        // compared by value, so the guard is the same guard — only its input is now typed.
        assertTrue(
            "the barcode screen must still write the consumed token for picker results",
            codeOf(scanner).contains(Regex("""consumedPhotoToken = resolvedSource\.token""")),
        )
        assertTrue(
            "the label screen must still write the consumed token",
            codeOf(labelScanner).contains(Regex("""consumedPhotoToken = source\.token""")),
        )
    }
}
