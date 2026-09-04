package app.justthecarbs.ui

import app.justthecarbs.domain.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Whether the first real settings value has arrived from DataStore yet.
 *
 * A returning user's `hasSeenOnboarding = true` only exists once this resolves to [Ready] — before
 * that there is no settings value to trust, only the *shape* of one. Rendering a NavHost against a
 * synthetic default (as opposed to waiting for this) is exactly what let a returning user's start
 * destination be decided by [AppSettings]'s default constructor rather than by what was actually
 * stored.
 */
sealed interface StartupState {
    data object Loading : StartupState

    data class Ready(val settings: AppSettings) : StartupState
}

/** Maps a settings source to [StartupState]: [StartupState.Loading] until its first emission. */
fun Flow<AppSettings>.asStartupState(): Flow<StartupState> = map { StartupState.Ready(it) }
