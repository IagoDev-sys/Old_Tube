package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.OptimizationSettings
import com.example.data.model.PlaybackEngineMode
import com.example.data.model.QualityPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "oldtube_prefs")

class OptimizationPreferences(private val context: Context) {

    private object Keys {
        val QUALITY = stringPreferencesKey("quality_preset")
        val ENGINE = stringPreferencesKey("engine_mode")
        val LOW_RAM_THUMBS = booleanPreferencesKey("low_ram_thumbs")
        val CLASSIC_LIGHT = booleanPreferencesKey("classic_light_theme")
        val BRIDGE_URL = stringPreferencesKey("custom_bridge_url")
    }

    val settingsFlow: Flow<OptimizationSettings> = context.dataStore.data.map { prefs ->
        val quality = prefs[Keys.QUALITY]
            ?.let { runCatching { QualityPreset.valueOf(it) }.getOrNull() }
            ?: QualityPreset.SD_480P

        val engine = prefs[Keys.ENGINE]
            ?.let { runCatching { PlaybackEngineMode.valueOf(it) }.getOrNull() }
            ?: PlaybackEngineMode.HYBRID_AUTO

        OptimizationSettings(
            qualityPreset = quality,
            engineMode = engine,
            lowRamThumbnails = prefs[Keys.LOW_RAM_THUMBS] ?: true,
            classicLightTheme = prefs[Keys.CLASSIC_LIGHT] ?: true,
            customBridgeUrl = prefs[Keys.BRIDGE_URL] ?: "https://inv.nadeko.net"
        )
    }

    suspend fun updateQuality(preset: QualityPreset) {
        context.dataStore.edit { it[Keys.QUALITY] = preset.name }
    }

    suspend fun updateEngine(mode: PlaybackEngineMode) {
        context.dataStore.edit { it[Keys.ENGINE] = mode.name }
    }

    suspend fun updateLowRamThumbnails(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOW_RAM_THUMBS] = enabled }
    }

    suspend fun updateClassicLightTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLASSIC_LIGHT] = enabled }
    }

    suspend fun updateCustomBridgeUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        if (normalized.startsWith("https://")) {
            context.dataStore.edit { it[Keys.BRIDGE_URL] = normalized }
        }
    }
}
