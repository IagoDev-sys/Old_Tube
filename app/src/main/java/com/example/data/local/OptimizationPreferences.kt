package com.example.data.local

import android.content.Context
import com.example.data.model.OptimizationSettings
import com.example.data.model.PlaybackEngineMode
import com.example.data.model.QualityPreset
import com.example.data.remote.YouTubeLegacyNetwork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class OptimizationPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "oldtube_prefs",
        Context.MODE_PRIVATE
    )

    private val _settingsFlow = MutableStateFlow(readCurrent())
    val settingsFlow: Flow<OptimizationSettings> = _settingsFlow.asStateFlow()

    private fun readCurrent(): OptimizationSettings {
        val quality = prefs.getString(KEY_QUALITY, null)
            ?.let { runCatching { QualityPreset.valueOf(it) }.getOrNull() }
            ?: QualityPreset.SD_480P

        val engine = prefs.getString(KEY_ENGINE, null)
            ?.let { runCatching { PlaybackEngineMode.valueOf(it) }.getOrNull() }
            ?: PlaybackEngineMode.HYBRID_AUTO

        val allowLan = prefs.getBoolean(KEY_ALLOW_LAN_BRIDGE, false)
        val rawBridge = prefs.getString(KEY_BRIDGE_URL, "https://inv.nadeko.net") ?: "https://inv.nadeko.net"
        val validBridge = if (YouTubeLegacyNetwork.isSafeBridgeUrl(rawBridge) ||
            (allowLan && YouTubeLegacyNetwork.isSafeLocalLanBridgeUrl(rawBridge))
        ) {
            rawBridge
        } else {
            "https://inv.nadeko.net"
        }

        return OptimizationSettings(
            qualityPreset = quality,
            engineMode = engine,
            lowRamThumbnails = prefs.getBoolean(KEY_LOW_RAM_THUMBS, true),
            classicLightTheme = prefs.getBoolean(KEY_CLASSIC_LIGHT, true),
            customBridgeUrl = validBridge,
            allowLanBridge = allowLan
        )
    }

    fun updateQuality(preset: QualityPreset) {
        prefs.edit().putString(KEY_QUALITY, preset.name).apply()
        _settingsFlow.value = readCurrent()
    }

    fun updateEngine(mode: PlaybackEngineMode) {
        prefs.edit().putString(KEY_ENGINE, mode.name).apply()
        _settingsFlow.value = readCurrent()
    }

    fun updateLowRamThumbnails(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOW_RAM_THUMBS, enabled).apply()
        _settingsFlow.value = readCurrent()
    }

    fun updateClassicLightTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLASSIC_LIGHT, enabled).apply()
        _settingsFlow.value = readCurrent()
    }

    fun updateAllowLanBridge(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_LAN_BRIDGE, enabled).apply()
        _settingsFlow.value = readCurrent()
    }

    fun updateCustomBridgeUrl(url: String): Boolean {
        val normalized = url.trim().trimEnd('/')
        val allowLan = prefs.getBoolean(KEY_ALLOW_LAN_BRIDGE, false)
        val accepted = YouTubeLegacyNetwork.isSafeBridgeUrl(normalized) ||
            (allowLan && YouTubeLegacyNetwork.isSafeLocalLanBridgeUrl(normalized))
        if (accepted) {
            prefs.edit().putString(KEY_BRIDGE_URL, normalized).apply()
            _settingsFlow.value = readCurrent()
        }
        return accepted
    }

    private companion object {
        const val KEY_QUALITY = "quality_preset"
        const val KEY_ENGINE = "engine_mode"
        const val KEY_LOW_RAM_THUMBS = "low_ram_thumbs"
        const val KEY_CLASSIC_LIGHT = "classic_light_theme"
        const val KEY_BRIDGE_URL = "custom_bridge_url"
        const val KEY_ALLOW_LAN_BRIDGE = "allow_lan_bridge"
    }
}
