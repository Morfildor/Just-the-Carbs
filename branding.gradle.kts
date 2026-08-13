/**
 * CENTRALIZED BRANDING (brief §5).
 *
 * Everything that must change before publication under a different name lives here and ONLY here.
 * Source code must never hardcode the app name, application id or namespace.
 *
 * To rebrand:
 *   1. Change the values below.
 *   2. Replace the launcher icon vectors in app/src/main/res/drawable/.
 *   3. Change app_name in app/src/main/res/values/strings.xml (+ values-nl).
 * No other file needs editing.
 */
object Branding {
    /** Working name. Must not contain another company's trademark (brief §5, §51). */
    const val APP_NAME = "CarbScan"

    /** Play Store application id. Immutable once published — choose carefully. */
    const val APPLICATION_ID = "app.carbscan"

    /** Kotlin/Java namespace. */
    const val NAMESPACE = "app.carbscan"

    /** User-Agent sent to Open Food Facts. OFF requires an identifying UA (verified 2026-08-13). */
    const val OFF_USER_AGENT_APP = "CarbScan"

    /** Contact placeholder — OWNER MUST REPLACE before publication. */
    const val CONTACT_EMAIL = "REPLACE_ME@example.com"

    const val VERSION_CODE = 1
    const val VERSION_NAME = "1.0.0"
}
