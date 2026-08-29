package app.justthecarbs.data.remote

import android.util.Log
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.SearchProviderLog

/**
 * Logcat diagnostics for the search provider chain — **debug builds only**.
 *
 * Answers the one question the migration cannot be validated without: *did Search-a-licious answer
 * this query, or did the legacy fallback?* A successful fallback is deliberately invisible on
 * screen, which is the right UX and precisely what makes it unverifiable by looking.
 *
 * Read it during device testing with:
 *
 * ```
 * adb logcat -s JtcSearch
 * ```
 *
 * **No query text is logged, ever** — only a stage, a provider tag and a result count. That follows
 * the app's existing practice of keeping user-entered text out of diagnostics, and it means turning
 * these on cannot turn search into a keystroke recorder. It is wired only under `BuildConfig.DEBUG`
 * in `AppContainer`, so release builds hold [SearchProviderLog.None] and R8 folds the calls away.
 */
internal object LogcatSearchProviderLog : SearchProviderLog {

    private const val TAG = "JtcSearch"

    override fun onPrimaryStarted() = log("primary  start     (POST /search)")
    override fun onPrimarySucceeded(hitCount: Int) = log("primary  OK        hits=$hitCount")
    override fun onPrimaryNoMatches() = log("primary  no-matches (no fallback: this is an answer)")
    override fun onPrimaryFailed(error: LookupError) = log("primary  FAILED    $error")

    override fun onPrimaryHitsUnusable(rawHitCount: Int, reportedCount: Int?) =
        log("primary  UNUSABLE  raw=$rawHitCount reported=${reportedCount ?: "-"} -> MALFORMED")
    override fun onFallbackStarted() = log("fallback start     (legacy OFF, governed)")
    override fun onFallbackSucceeded(hitCount: Int) = log("fallback OK        hits=$hitCount")
    override fun onFallbackNoMatches() = log("fallback no-matches")
    override fun onFallbackFailed(error: LookupError) = log("fallback FAILED    $error")

    private fun log(message: String) {
        Log.d(TAG, message)
    }
}
