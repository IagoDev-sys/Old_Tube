package com.example.data.model

enum class QualityPreset(
    val label: String,
    val maxHeight: Int,
    val youtubeVqParam: String,
    val description: String
) {
    ECO_360P(
        label = "360p Eco",
        maxHeight = 360,
        youtubeVqParam = "medium",
        description = "Ideal para 1.5 GB RAM e Wi-Fi lento (~0.7 Mbps)"
    ),
    SD_480P(
        label = "480p SD",
        maxHeight = 480,
        youtubeVqParam = "large",
        description = "Equilíbrio entre nitidez e CPU no Galaxy Tab E"
    ),
    HD_720P(
        label = "720p HD",
        maxHeight = 720,
        youtubeVqParam = "hd720",
        description = "Limite máximo nativo da tela 1280×800 H.264"
    )
}

enum class PlaybackEngineMode(
    val label: String,
    val description: String
) {
    HYBRID_AUTO(
        label = "Híbrido (H.264 + Embed/Mobile)",
        description = "Tenta H.264 nativo; usa IFrame com fallback automático para Mobile Direto."
    ),
    NATIVE_H264_ONLY(
        label = "Nativo H.264 (MediaPlayer)",
        description = "Menor uso de RAM via MediaCodec OMX quando há ponte ativa."
    ),
    LITE_EMBED_ONLY(
        label = "Web Mobile / Embed Direto",
        description = "Máxima compatibilidade em todos os vídeos do YouTube."
    )
}

enum class ActivePlayerBackend {
    NATIVE_H264,
    IFRAME_EMBED,
    MOBILE_WEB
}

data class VideoItem(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String = "",
    val thumbnailUrl: String,
    val publishedAt: String = "",
    val durationText: String = "",
    val viewCountText: String = "",
    val description: String = "",
    val sourceTag: String = "Atom RSS",
    val isShort: Boolean = false
)

data class ResolvedStream(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val description: String,
    val directH264Url: String?,
    val qualityLabel: String,
    val codecLabel: String,
    val embedUrl: String,
    val mobileWatchUrl: String,
    val resolvedVia: String,
    val isShort: Boolean = false
)

data class OptimizationSettings(
    val qualityPreset: QualityPreset = QualityPreset.SD_480P,
    val engineMode: PlaybackEngineMode = PlaybackEngineMode.HYBRID_AUTO,
    val lowRamThumbnails: Boolean = true,
    val classicLightTheme: Boolean = true,
    val customBridgeUrl: String = "https://inv.nadeko.net"
)

data class DeviceTelemetry(
    val deviceModel: String,
    val manufacturer: String,
    val androidVersion: String,
    val apiLevel: Int,
    val supportedAbis: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val isLowRamDevice: Boolean,
    val hasHardwareAvc: Boolean,
    val hasHardwareVp9: Boolean,
    val hasHardwareAv1: Boolean,
    val avcDecoderNames: List<String>,
    val screenWidthDp: Int,
    val screenHeightDp: Int
)

data class BridgePingResult(
    val baseUrl: String,
    val reachable: Boolean,
    val latencyMs: Long,
    val note: String
)
