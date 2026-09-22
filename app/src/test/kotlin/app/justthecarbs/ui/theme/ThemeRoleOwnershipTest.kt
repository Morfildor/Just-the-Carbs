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

        val home = java.io.File("src/main/kotlin/app/justthecarbs/ui/home/HomeScreen.kt").readText()
        assertTrue(home.contains("MaterialTheme.colorScheme.onPrimary"))
        assertFalse("Home actions are solid task tiles, not ornamental gradients", home.contains("Brush.linearGradient"))

        val tutorial = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/onboarding/TutorialPreview.kt",
        ).readText()
        assertTrue(tutorial.contains("MaterialTheme.extendedColors.onAccent"))
    }

    @Test
    fun `extended visual roles are explicit in both schemes`() {
        assertEquals(1, Regex("""onResult\s*=\s*WarmWhite""").findAll(theme).count())
        assertEquals(1, Regex("""onResult\s*=\s*Night""").findAll(theme).count())
        assertEquals(1, Regex("""accentBackdropAlpha\s*=\s*0\.16f""").findAll(theme).count())
        assertEquals(1, Regex("""accentBackdropAlpha\s*=\s*0\.26f""").findAll(theme).count())

        val backdrop = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/components/AccentBackdrop.kt",
        ).readText()
        assertTrue(backdrop.contains("MaterialTheme.extendedColors.accentBackdropAlpha"))
    }

    @Test
    fun `the decorative backdrop lives on Home only, over a painted page ground`() {
        // The backdrop moved to Home alone in the 2026-09-22 visual pass (report P1-6): on every
        // other screen it sat BEHIND the top bar's trailing controls -- measured collisions with
        // Product's star and overflow, the open overflow menu, and Meal's `Clear meal` -- and
        // decoration may not share a level with a control. Those screens identify themselves with
        // JtcTopBar's DestinationMarker instead.
        //
        // This test keeps its original purpose, which was never "every screen has a backdrop": it
        // is that a backdrop is never drawn onto an unpainted ground, where it would composite
        // against whatever happened to be behind the window. That claim is now checked on the one
        // screen that still has one, and the other five are checked for its ABSENCE -- so a future
        // change that reintroduces a backdrop on a secondary screen fails here rather than
        // silently restoring the collision.
        val home = java.io.File("src/main/kotlin/app/justthecarbs/ui/home/HomeScreen.kt")
            .readText()
            .replace(Regex("""\s+"""), " ")
        assertTrue(
            "HomeScreen must paint the opaque page ground below AccentBackdrop",
            home.contains(
                "Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { AccentBackdrop(",
            ),
        )

        listOf(
            "manual/ManualEntryScreen.kt",
            "meal/MealScreen.kt",
            "product/ProductScreen.kt",
            "search/SearchScreen.kt",
            "settings/SettingsScreen.kt",
        ).forEach { relativePath ->
            val source = java.io.File("src/main/kotlin/app/justthecarbs/ui/$relativePath").readText()
            assertTrue(
                "$relativePath must not draw the backdrop motif -- it belongs to Home alone, " +
                    "because on a screen with trailing top-bar controls it collides with them",
                !source.contains("AccentBackdrop("),
            )
        }
    }
}
