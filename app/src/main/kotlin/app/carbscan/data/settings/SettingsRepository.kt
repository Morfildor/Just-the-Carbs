package app.carbscan.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.carbscan.domain.AppSettings
import app.carbscan.domain.ResultStyle
import app.carbscan.domain.ThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The four user preferences of §43. Local only — no account, no sync (§34). */
class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsStore.data
        // A corrupt or unreadable preference file must not stop the app from calculating. Falling
        // back to defaults keeps the core workflow alive (§36).
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            AppSettings(
                theme = prefs[THEME]?.toEnum(ThemeChoice.entries, ThemeChoice.SYSTEM)
                    ?: ThemeChoice.SYSTEM,
                resultStyle = prefs[RESULT_STYLE]?.toEnum(ResultStyle.entries, ResultStyle.WHOLE_WITH_DECIMAL)
                    ?: ResultStyle.WHOLE_WITH_DECIMAL,
                hapticsEnabled = prefs[HAPTICS] ?: true,
            )
        }

    suspend fun setTheme(choice: ThemeChoice) =
        context.settingsStore.edit { it[THEME] = choice.name }.let {}

    suspend fun setResultStyle(style: ResultStyle) =
        context.settingsStore.edit { it[RESULT_STYLE] = style.name }.let {}

    suspend fun setHapticsEnabled(enabled: Boolean) =
        context.settingsStore.edit { it[HAPTICS] = enabled }.let {}

    private fun <T : Enum<T>> String.toEnum(values: List<T>, fallback: T): T =
        values.firstOrNull { it.name == this } ?: fallback

    private fun emptyPreferences() = androidx.datastore.preferences.core.emptyPreferences()

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val RESULT_STYLE = stringPreferencesKey("result_style")
        val HAPTICS = booleanPreferencesKey("haptics")
    }
}
