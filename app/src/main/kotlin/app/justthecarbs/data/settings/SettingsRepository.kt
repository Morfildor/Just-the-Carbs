package app.justthecarbs.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.ResultStyle
import app.justthecarbs.domain.ThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The four user preferences of §43. Local only — no account, no sync (§34). */
class SettingsRepository private constructor(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.settingsStore)

    val settings: Flow<AppSettings> = store.data
        // A corrupt or unreadable preference file must not stop the app from calculating. Falling
        // back to defaults keeps the core workflow alive (§36).
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            AppSettings(
                theme = prefs[THEME]?.toEnum(ThemeChoice.entries, ThemeChoice.LIGHT)
                    ?: ThemeChoice.LIGHT,
                resultStyle = prefs[RESULT_STYLE]?.toEnum(ResultStyle.entries, ResultStyle.DECIMAL_DOMINANT)
                    ?: ResultStyle.DECIMAL_DOMINANT,
                hapticsEnabled = prefs[HAPTICS] ?: true,
                hasSeenOnboarding = prefs[HAS_SEEN_ONBOARDING] ?: false,
                // Negative values are floored at zero rather than trusted. A corrupt counter should
                // read as "not yet counted" — which shows a new user the reminder — instead of
                // becoming a number that could silently suppress it.
                launchCount = (prefs[LAUNCH_COUNT] ?: 0).coerceAtLeast(0),
            )
        }

    suspend fun setTheme(choice: ThemeChoice) =
        store.edit { it[THEME] = choice.name }.let {}

    suspend fun setResultStyle(style: ResultStyle) =
        store.edit { it[RESULT_STYLE] = style.name }.let {}

    suspend fun setHapticsEnabled(enabled: Boolean) =
        store.edit { it[HAPTICS] = enabled }.let {}

    suspend fun setHasSeenOnboarding(seen: Boolean) =
        store.edit { it[HAS_SEEN_ONBOARDING] = seen }.let {}

    /**
     * Record one app launch, if the tutorial reminder still depends on the count.
     *
     * Read-and-increment inside a single `edit` block, which DataStore serialises — so two launches
     * racing (a rapid relaunch, a process restart) cannot both read the same value and write the
     * same number. The stop condition is re-checked *inside* the transaction against the stored
     * values rather than against whatever the caller last observed, so a stale snapshot cannot
     * resurrect a counter that has already finished.
     */
    suspend fun recordLaunch() = store.edit { prefs ->
        val seen = prefs[HAS_SEEN_ONBOARDING] ?: false
        val count = (prefs[LAUNCH_COUNT] ?: 0).coerceAtLeast(0)
        if (app.justthecarbs.domain.TutorialReminder.shouldCountLaunch(seen, count)) {
            prefs[LAUNCH_COUNT] = count + 1
        }
    }.let {}

    private fun <T : Enum<T>> String.toEnum(values: List<T>, fallback: T): T =
        values.firstOrNull { it.name == this } ?: fallback

    private fun emptyPreferences() = androidx.datastore.preferences.core.emptyPreferences()

    companion object {
        /** Test-only entry point so tests can inject a temp-file DataStore instead of a Context. */
        internal fun forTesting(store: DataStore<Preferences>) = SettingsRepository(store)

        private val THEME = stringPreferencesKey("theme")
        private val RESULT_STYLE = stringPreferencesKey("result_style")
        private val HAPTICS = booleanPreferencesKey("haptics")
        private val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        private val LAUNCH_COUNT = intPreferencesKey("launch_count")
    }
}
