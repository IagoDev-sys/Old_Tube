package com.example.data.telemetry

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.example.data.model.DeviceTelemetry

class DeviceTelemetryProvider(private val context: Context) {

    @Suppress("DEPRECATION")
    fun collect(): DeviceTelemetry {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val availableRamMb = memInfo.availMem / (1024L * 1024L)
        val isLowRam = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            am.isLowRamDevice || totalRamMb <= 2048L
        } else {
            totalRamMb <= 2048L
        }

        val codecs: List<MediaCodecInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList()
        } else {
            val count = MediaCodecList.getCodecCount()
            (0 until count).map { idx -> MediaCodecList.getCodecInfoAt(idx) }
        }

        val avcDecoders = mutableListOf<String>()
        var hwAvc = false
        var hwVp9 = false
        var hwAv1 = false

        for (info in codecs) {
            if (info.isEncoder) continue
            val isHardware = isHardwareAcceleratedCodec(info)
            for (type in info.supportedTypes) {
                when {
                    type.equals("video/avc", ignoreCase = true) -> {
                        if (isHardware) {
                            hwAvc = true
                            avcDecoders.add(info.name)
                        }
                    }
                    type.equals("video/x-vnd.on2.vp9", ignoreCase = true) && isHardware -> {
                        hwVp9 = true
                    }
                    type.equals("video/av01", ignoreCase = true) && isHardware -> {
                        hwAv1 = true
                    }
                }
            }
        }

        val abis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.joinToString(", ")
        } else {
            listOfNotNull(
                Build.CPU_ABI?.takeIf { it.isNotBlank() },
                Build.CPU_ABI2?.takeIf { it.isNotBlank() && it != "unknown" }
            ).joinToString(", ").ifBlank { "armeabi-v7a" }
        }

        val metrics = context.resources.displayMetrics
        val widthDp = (metrics.widthPixels / metrics.density).toInt()
        val heightDp = (metrics.heightPixels / metrics.density).toInt()

        return DeviceTelemetry(
            deviceModel = Build.MODEL ?: "Dispositivo",
            manufacturer = Build.MANUFACTURER ?: "Android",
            androidVersion = Build.VERSION.RELEASE ?: "?",
            apiLevel = Build.VERSION.SDK_INT,
            supportedAbis = abis,
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            isLowRamDevice = isLowRam,
            hasHardwareAvc = hwAvc,
            hasHardwareVp9 = hwVp9,
            hasHardwareAv1 = hwAv1,
            avcDecoderNames = avcDecoders.distinct().take(4),
            screenWidthDp = widthDp,
            screenHeightDp = heightDp
        )
    }

    private fun isHardwareAcceleratedCodec(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return info.isHardwareAccelerated
        }
        val name = info.name.lowercase()
        return !name.startsWith("omx.google.") &&
            !name.startsWith("c2.android.") &&
            !name.contains("sw")
    }
}
