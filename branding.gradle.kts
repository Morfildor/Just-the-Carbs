/**
 * CENTRALIZED BRANDING (brief §5).
 *
 * Everything that must change before publication under a different name lives here and ONLY here.
 * Source code must never hardcode the app name, application id or namespace — it reads them from
 * `BuildConfig`, which is generated from these values.
 *
 * These are Gradle `extra` properties rather than a Kotlin `object`, because an `object` declared
 * in an applied script is not visible to the script that applies it: the previous version of this
 * file looked centralized but nothing actually read it.
 *
 * To rebrand:
 *   1. Change the values below.
 *   2. Replace the launcher icon vectors in app/src/main/res/drawable/.
 * Nothing else needs editing — app name, application id, namespace, and the Open Food Facts
 * User-Agent all derive from here.
 */

/** Working name. Must not contain another company's trademark (brief §5, §51). */
extra["brandAppName"] = "CarbScan"

/** Play Store application id. Immutable once published — choose carefully. */
extra["brandApplicationId"] = "app.carbscan"

/** Kotlin/Java namespace. */
extra["brandNamespace"] = "app.carbscan"

/** Contact placeholder — OWNER MUST REPLACE before publication (also used in the OFF User-Agent). */
extra["brandContactEmail"] = "albinogorillassupport@gmail.com"

extra["brandVersionCode"] = 1
extra["brandVersionName"] = "1.0.0"
