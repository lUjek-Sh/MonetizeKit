package ua.lujek.monetizekit.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.monetizeKitFrequencyCapDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "monetizekit_fullscreen_frequency_caps",
)

/** Persists only placement-owned impression timestamps; no ad, device, or user identifiers are stored. */
internal class FullscreenFrequencyCapStore(context: Context) {
    private val dataStore = context.applicationContext.monetizeKitFrequencyCapDataStore

    suspend fun shownAt(placementId: PlacementId): List<Long> =
        dataStore.data.first()[placementId.preferenceKey()]
            ?.split(',')
            ?.mapNotNull(String::toLongOrNull)
            .orEmpty()

    suspend fun recordShow(
        placementId: PlacementId,
        frequencyCap: FullscreenFrequencyCap,
        shownAtEpochMillis: Long,
    ) {
        dataStore.edit { preferences ->
            val history = preferences[placementId.preferenceKey()]
                ?.split(',')
                ?.mapNotNull(String::toLongOrNull)
                .orEmpty()
            preferences[placementId.preferenceKey()] = frequencyCap
                .historyAfterShow(history, shownAtEpochMillis)
                .joinToString(separator = ",")
        }
    }
}

private fun PlacementId.preferenceKey(): Preferences.Key<String> =
    stringPreferencesKey("fullscreen_frequency_cap:${value}")
