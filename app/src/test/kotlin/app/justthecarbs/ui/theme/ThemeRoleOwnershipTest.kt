package app.justthecarbs.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards theme roles and scanner semantics that would otherwise fall back to Material defaults. */
class ThemeRoleOwnershipTest {

    private val theme = java.io.File(
        "src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt",
    ).readText()

    @Test
    fun `material roles consumed by current components are owned in both schemes`() {
        listOf(
            "onTertiary",
            "inverseSurface",
            "inverseOnSurface",
            "surfaceBright",
            "surfaceDim",
            "errorContainer",
            "onErrorContainer",
            "outline",
            "outlineVariant",
        ).forEach { role ->
            val declarations = Regex("""(?m)^\s*$role\s*=""").findAll(theme).count()
            assertEquals("$role must be explicit in both LightColors and DarkColors", 2, declarations)
        }
    }

    @Test
    fun `dynamic material color remains absent`() {
        assertFalse(theme.contains("dynamicLightColorScheme"))
        assertFalse(theme.contains("dynamicDarkColorScheme"))
    }

    @Test
    fun `scanner overlay colors are semantic and no scanner screen owns a raw hex`() {
        assertEquals(2, Regex("""scanner\s*=\s*ImageScannerColors""").findAll(theme).count())

        listOf(
            "AssistedReadingScreen.kt",
            "CropConfirmationScreen.kt",
            "VerificationScreen.kt",
            "LabelScannerScreen.kt",
        ).forEach { name ->
            val source = java.io.File("src/main/kotlin/app/justthecarbs/ui/scan/$name").readText()
            assertFalse("$name contains a raw scanner color", source.contains("Color(0x"))
        }

        assertTrue(theme.contains("val scanner: ScannerColors"))
        assertTrue(theme.contains("val darkEdge: Color"))
        assertTrue(theme.contains("val lightEdge: Color"))
    }

    @Test
    fun `welcome carousel uses explicit semantic foreground pairs`() {
        val welcome = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/onboarding/WelcomeCarouselScreen.kt",
        ).readText()
        assertTrue(welcome.contains("MaterialTheme.colorScheme.onPrimary"))
        assertTrue(welcome.contains("MaterialTheme.extendedColors.onResult"))
        assertFalse(welcome.contains("Color.White"))
        assertFalse(
            "semantic foreground/background pairs must switch atomically",
            welcome.contains("animateColorAsState"),
        )
    }

    @Test
    fun `accent fill content has an explicit foreground in both schemes`() {
        assertEquals(1, Regex("""onAccent\s*=\s*WarmWhite""").findAll(theme).count())
        assertEquals(1, Regex("""onAccent\s*=\s*Night""").findAll(theme).count())

        listOf(
            "src/main/kotlin/app/justthecarbs/ui/home/HomeScreen.kt",
            "src/main/kotlin/app/justthecarbs/ui/onboarding/TutorialPreview.kt",
        ).forEach { path ->
            assertTrue(java.io.File(path).readText().contains("MaterialTheme.extendedColors.onAccent"))
        }
    }

    @Test
    fun `extended visual roles are explicit in both schemes`() {
        assertEquals(1, Regex("""onResult\s*=\s*WarmWhite""").findAll(theme).count())
        assertEquals(1, Regex("""onResult\s*=\s*Night""").findAll(theme).count())
        assertEquals(1, Regex("""accentBackdropAlpha\s*=\s*0\.06f""").findAll(theme).count())
        assertEquals(1, Regex("""accentBackdropAlpha\s*=\s*0\.18f""").findAll(theme).count())

        val backdrop = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/components/AccentBackdrop.kt",
        ).readText()
        assertTrue(backdrop.contains("MaterialTheme.extendedColors.accentBackdropAlpha"))
    }

    @Test
    fun `page ground is painted before every decorative backdrop`() {
        listOf(
            "home/HomeScreen.kt",
            "manual/ManualEntryScreen.kt",
            "meal/MealScreen.kt",
            "search/SearchScreen.kt",
            "settings/SettingsScreen.kt",
        ).forEach { relativePath ->
            val source = java.io.File("src/main/kotlin/app/justthecarbs/ui/$relativePath")
                .readText()
                .replace(Regex("""\s+"""), " ")
            assertTrue(
                "$relativePath must paint the opaque page ground below AccentBackdrop",
                source.contains(
                    "Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { AccentBackdrop(",
                ),
            )
        }

        val product = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt",
        ).readText().replace(Regex("""\s+"""), " ")
        assertTrue(
            "ProductScreen must paint the opaque page ground below its decorative circle",
            product.contains(
                "Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { // Doc's decorative",
            ),
        )
    }
}
