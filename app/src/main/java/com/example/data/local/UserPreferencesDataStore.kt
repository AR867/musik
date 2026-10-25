package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppThemeMode
import com.example.data.model.RepeatModeState
import com.example.data.model.SubscriptionTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "music_form_prefs")

data class PersistedAppState(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val lastTrackId: String = "tr_neon_horizon",
    val lastPositionMs: Long = 0L,
    val lastQueueCsv: String = "",
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeState = RepeatModeState.OFF,
    val volume: Float = 0.85f,
    val waveDiversity: Int = 50,
    val excludedArtistsCsv: String = "",
    val excludedGenresCsv: String = "",
    val subscriptionTier: SubscriptionTier = SubscriptionTier.FREE,
    val simulateOfflineMode: Boolean = false
)

class UserPreferencesDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_TRACK_ID = stringPreferencesKey("last_track_id")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val LAST_QUEUE_CSV = stringPreferencesKey("last_queue_csv")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val VOLUME = floatPreferencesKey("volume")
        val WAVE_DIVERSITY = intPreferencesKey("wave_diversity")
        val EXCLUDED_ARTISTS_CSV = stringPreferencesKey("excluded_artists_csv")
        val EXCLUDED_GENRES_CSV = stringPreferencesKey("excluded_genres_csv")
        val SUBSCRIPTION_TIER = stringPreferencesKey("subscription_tier")
        val SIMULATE_OFFLINE = booleanPreferencesKey("simulate_offline")
    }

    val appStateFlow: Flow<PersistedAppState> = context.dataStore.data.map { prefs ->
        PersistedAppState(
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { AppThemeMode.valueOf(it) }.getOrNull()
            } ?: AppThemeMode.DARK,
            lastTrackId = prefs[Keys.LAST_TRACK_ID] ?: "tr_neon_horizon",
            lastPositionMs = prefs[Keys.LAST_POSITION_MS] ?: 0L,
            lastQueueCsv = prefs[Keys.LAST_QUEUE_CSV] ?: "",
            isShuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
            repeatMode = prefs[Keys.REPEAT_MODE]?.let {
                runCatching { RepeatModeState.valueOf(it) }.getOrNull()
            } ?: RepeatModeState.OFF,
            volume = prefs[Keys.VOLUME] ?: 0.85f,
            waveDiversity = prefs[Keys.WAVE_DIVERSITY] ?: 50,
            excludedArtistsCsv = prefs[Keys.EXCLUDED_ARTISTS_CSV] ?: "",
            excludedGenresCsv = prefs[Keys.EXCLUDED_GENRES_CSV] ?: "",
            subscriptionTier = prefs[Keys.SUBSCRIPTION_TIER]?.let {
                runCatching { SubscriptionTier.valueOf(it) }.getOrNull()
            } ?: SubscriptionTier.FREE,
            simulateOfflineMode = prefs[Keys.SIMULATE_OFFLINE] ?: false
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun savePlayerState(
        trackId: String,
        positionMs: Long,
        queueIds: List<String>,
        shuffle: Boolean,
        repeatMode: RepeatModeState,
        volume: Float
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_TRACK_ID] = trackId
            prefs[Keys.LAST_POSITION_MS] = positionMs.coerceAtLeast(0L)
            prefs[Keys.LAST_QUEUE_CSV] = queueIds.joinToString(",")
            prefs[Keys.SHUFFLE_ENABLED] = shuffle
            prefs[Keys.REPEAT_MODE] = repeatMode.name
            prefs[Keys.VOLUME] = volume.coerceIn(0f, 1f)
        }
    }

    suspend fun saveWaveSettings(
        diversity: Int,
        excludedArtists: Set<String>,
        excludedGenres: Set<String> = emptySet()
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WAVE_DIVERSITY] = diversity.coerceIn(0, 100)
            prefs[Keys.EXCLUDED_ARTISTS_CSV] = excludedArtists.joinToString(",")
            prefs[Keys.EXCLUDED_GENRES_CSV] = excludedGenres.joinToString(",")
        }
    }

    suspend fun setSimulateOffline(offline: Boolean) {
        context.dataStore.edit { it[Keys.SIMULATE_OFFLINE] = offline }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
