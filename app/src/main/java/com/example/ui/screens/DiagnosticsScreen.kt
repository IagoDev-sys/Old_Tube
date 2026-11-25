package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BridgePingResult
import com.example.data.model.DeviceTelemetry
import com.example.data.model.OptimizationSettings
import com.example.data.model.PlaybackEngineMode
import com.example.data.model.QualityPreset
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryAmber

@Composable
fun DiagnosticsScreen(
    telemetry: DeviceTelemetry,
    settings: OptimizationSettings,
    pingResults: List<BridgePingResult>,
    isPinging: Boolean,
    onRefreshTelemetry: () -> Unit,
    onQualitySelected: (QualityPreset) -> Unit,
    onEngineSelected: (PlaybackEngineMode) -> Unit,
    onLowRamThumbnailsChanged: (Boolean) -> Unit,
    onClassicLightThemeChanged: (Boolean) -> Unit,
    onSaveBridgeUrl: (String) -> Unit,
    onRunBridgePing: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bridgeInput by remember(settings.customBridgeUrl) {
        mutableStateOf(settings.customBridgeUrl)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Modo Tab E & Hardware",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "100% local sem login, visual retrô e limites de memória",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRefreshTelemetry,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_telemetry_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Atualizar telemetria"
                    )
                }
            }
        }

        // 1. Hardware Telemetry Card
        item {
            SectionCard(title = "Telemetria Real do Dispositivo", icon = Icons.Rounded.Memory) {
                Text(
                    text = "${telemetry.manufacturer} ${telemetry.deviceModel} • Android ${telemetry.androidVersion} (API ${telemetry.apiLevel})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ABIs: ${telemetry.supportedAbis} • Tela: ${telemetry.screenWidthDp}×${telemetry.screenHeightDp} dp",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                val usedMb = (telemetry.totalRamMb - telemetry.availableRamMb).coerceAtLeast(0L)
                val ramRatio = if (telemetry.totalRamMb > 0) {
                    (usedMb.toFloat() / telemetry.totalRamMb.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Memória RAM em uso", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${telemetry.availableRamMb} MB livres de ${telemetry.totalRamMb} MB",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { ramRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (ramRatio > 0.85f) TelemetryAmber else MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CodecBadge("H.264 HW", telemetry.hasHardwareAvc, Modifier.weight(1f))
                    CodecBadge("VP9 HW", telemetry.hasHardwareVp9, Modifier.weight(1f))
                    CodecBadge("AV1 HW", telemetry.hasHardwareAv1, Modifier.weight(1f))
                }
            }
        }

        // 2. Classic 2013 Appearance & Memory Toggles
        item {
            SectionCard(title = "Visual Clássico & Economia de RAM", icon = Icons.Rounded.Speed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Visual Clássico 2013 (Claro)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Desative para usar o tema Holo TV Escuro",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.classicLightTheme,
                        onCheckedChange = onClassicLightThemeChanged,
                        modifier = Modifier.testTag("switch_classic_light")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Miniaturas Eco (320×180)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Reduz consumo de memória de texturas em ~75%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.lowRamThumbnails,
                        onCheckedChange = onLowRamThumbnailsChanged,
                        modifier = Modifier.testTag("switch_low_ram_thumbs")
                    )
                }
            }
        }

        // 3. Resolution Cap & Playback Engine
        item {
            SectionCard(title = "Qualidade & Motor de Vídeo", icon = Icons.Rounded.Speed) {
                QualityPreset.entries.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onQualitySelected(preset) }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.qualityPreset == preset,
                            onClick = { onQualitySelected(preset) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = preset.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = preset.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                PlaybackEngineMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onEngineSelected(mode) }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.engineMode == mode,
                            onClick = { onEngineSelected(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = mode.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 4. Bridge Config & Latency Tester
        item {
            SectionCard(title = "Ponte H.264 (Piped / Invidious)", icon = Icons.Rounded.NetworkCheck) {
                OutlinedTextField(
                    value = bridgeInput,
                    onValueChange = { bridgeInput = it },
                    label = { Text("URL da instância HTTPS") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bridge_url_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onSaveBridgeUrl(bridgeInput) },
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.testTag("save_bridge_button")
                    ) {
                        Text("Salvar")
                    }
                    OutlinedButton(
                        onClick = onRunBridgePing,
                        enabled = !isPinging,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.testTag("test_bridges_button")
                    ) {
                        if (isPinging) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Testar latência")
                    }
                }
                if (pingResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    pingResults.forEach { res ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (res.reachable) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (res.reachable) SignalEmerald else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = res.baseUrl.removePrefix("https://"),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Text(
                                text = res.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (res.reachable) SignalEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 5. Android 4.4 / Tab E note
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .border(1.dp, TelemetryAmber.copy(alpha = 0.45f), RoundedCornerShape(4.dp)),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = TelemetryAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Compatibilidade Android 7.0+ (minSdk 24)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "O login Google foi removido para evitar bloqueios em dispositivos antigos: inscrições, favoritos e histórico ficam salvos localmente em SQLite (Room). Este build em Jetpack Compose garante 100% de compatibilidade a partir do Android 7.0 (API 24); o Android 4.4 KitKat não suporta Jetpack Compose, Google Play Services atuais nem WebView/TLS modernos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun CodecBadge(label: String, supported: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.border(
            1.dp,
            if (supported) SignalEmerald.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
            RoundedCornerShape(4.dp)
        ),
        shape = RoundedCornerShape(4.dp),
        color = if (supported) SignalEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = if (supported) "Hardware" else "Sem HW",
                style = MaterialTheme.typography.labelSmall,
                color = if (supported) SignalEmerald else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
