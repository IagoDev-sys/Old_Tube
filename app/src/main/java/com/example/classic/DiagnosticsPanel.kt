package com.example.classic

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.data.model.BridgePingResult
import com.example.data.model.DeviceTelemetry
import com.example.data.model.OptimizationSettings
import com.example.data.model.PlaybackEngineMode
import com.example.data.model.QualityPreset

class DiagnosticsPanel(
    private val context: Context,
    private val onRefreshTelemetry: () -> Unit,
    private val onQualitySelected: (QualityPreset) -> Unit,
    private val onEngineSelected: (PlaybackEngineMode) -> Unit,
    private val onLowRamChanged: (Boolean) -> Unit,
    private val onClassicThemeChanged: (Boolean) -> Unit,
    private val onAllowLanBridgeChanged: (Boolean) -> Unit,
    private val onSaveBridgeUrl: (String) -> Unit,
    private val onRunBridgePing: () -> Unit
) {
    val root = ScrollView(context)
    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val p = context.dp(14)
        setPadding(p, p, p, p)
    }

    private val telemetryCard = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
    }
    private val telemetryText = TextView(context).apply { textSize = 13f }
    private val refreshHwBtn = Button(context).apply {
        text = "Atualizar hardware"
        textSize = 12f
        setOnClickListener { onRefreshTelemetry() }
    }

    private val qualityGroup = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val engineGroup = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val lowRamCheck = CheckBox(context).apply {
        text = "Miniaturas Low-RAM (RGB_565 · metade da memória)"
    }
    private val lightThemeCheck = CheckBox(context).apply {
        text = "Tema Clássico Claro (Visual YouTube 2013)"
    }
    private val lanBridgeCheck = CheckBox(context).apply {
        text = "Permitir Ponte na Rede Local (http://192.168.x.x / 10.x.x.x)"
    }

    private val bridgeInput = EditText(context).apply {
        hint = "https://inv.nadeko.net ou http://192.168.0.10:8787"
        textSize = 13f
        isSingleLine = true
        setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
    }
    private val saveBridgeBtn = Button(context).apply { text = "Salvar Ponte" }
    private val pingBtn = Button(context).apply { text = "Testar instâncias e API Ponte" }
    private val pingResultsCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        root.addView(container)
        telemetryCard.addView(telemetryText)
        telemetryCard.addView(
            refreshHwBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                context.dp(40)
            ).apply { topMargin = context.dp(8) }
        )
    }

    fun render(
        telemetry: DeviceTelemetry,
        settings: OptimizationSettings,
        pingResults: List<BridgePingResult>,
        isPinging: Boolean
    ) {
        val palette = OldTubePalette.from(settings.classicLightTheme)
        root.setBackgroundColor(palette.bg)
        container.removeAllViews()

        container.addView(sectionHeader("Diagnóstico Android 4.4 / Hardware", palette))
        telemetryCard.background = makeRoundedCardDrawable(
            palette.surface, palette.border, context.dp(4).toFloat()
        )
        telemetryText.setTextColor(palette.textPrimary)
        telemetryText.text = buildString {
            appendLine("Aparelho: ${telemetry.manufacturer} ${telemetry.deviceModel}")
            appendLine("Sistema: Android ${telemetry.androidVersion} (API ${telemetry.apiLevel})")
            appendLine("ABIs: ${telemetry.supportedAbis}")
            appendLine("RAM: ${telemetry.availableRamMb} MB livre / ${telemetry.totalRamMb} MB total")
            appendLine("Tela: ${telemetry.screenWidthDp}×${telemetry.screenHeightDp} dp")
            appendLine("H.264 Hardware: ${if (telemetry.hasHardwareAvc) "Disponível (${telemetry.avcDecoderNames.joinToString()})" else "Indisponível"}")
            append("VP9 HW: ${if (telemetry.hasHardwareVp9) "Sim" else "Não"} · AV1 HW: ${if (telemetry.hasHardwareAv1) "Sim" else "Não"}")
        }
        refreshHwBtn.setTextColor(palette.textPrimary)
        refreshHwBtn.background = makeRoundedCardDrawable(
            palette.surfaceVariant, palette.border, context.dp(3).toFloat()
        )
        container.addView(telemetryCard)

        container.addView(sectionHeader("Qualidade máxima padrão", palette))
        qualityGroup.removeAllViews()
        for (preset in QualityPreset.entries) {
            qualityGroup.addView(
                createOptionRow(
                    title = preset.label,
                    subtitle = preset.description,
                    selected = settings.qualityPreset == preset,
                    palette = palette
                ) { onQualitySelected(preset) }
            )
        }
        container.addView(qualityGroup)

        container.addView(sectionHeader("Motor de reprodução", palette))
        engineGroup.removeAllViews()
        for (mode in PlaybackEngineMode.entries) {
            engineGroup.addView(
                createOptionRow(
                    title = mode.label,
                    subtitle = mode.description,
                    selected = settings.engineMode == mode,
                    palette = palette
                ) { onEngineSelected(mode) }
            )
        }
        container.addView(engineGroup)

        container.addView(sectionHeader("Memória e Aparência", palette))
        val togglesCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = makeRoundedCardDrawable(palette.surface, palette.border, context.dp(4).toFloat())
            setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
        }
        bindCheckbox(lowRamCheck, settings.lowRamThumbnails, palette, onLowRamChanged)
        bindCheckbox(lightThemeCheck, settings.classicLightTheme, palette, onClassicThemeChanged)
        bindCheckbox(lanBridgeCheck, settings.allowLanBridge, palette, onAllowLanBridgeChanged)
        togglesCard.addView(lowRamCheck)
        togglesCard.addView(lightThemeCheck)
        togglesCard.addView(lanBridgeCheck)
        container.addView(togglesCard)

        container.addView(sectionHeader("API Ponte Personalizada (Invidious / OLD Tube Bridge)", palette))
        val bridgeCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = makeRoundedCardDrawable(palette.surface, palette.border, context.dp(4).toFloat())
            setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
        }
        if (bridgeInput.text.toString() != settings.customBridgeUrl && !bridgeInput.hasFocus()) {
            bridgeInput.setText(settings.customBridgeUrl)
        }
        bridgeInput.setTextColor(palette.textPrimary)
        bridgeInput.setHintTextColor(palette.textSecondary)
        bridgeInput.background = makeRoundedCardDrawable(
            palette.bg, palette.border, context.dp(4).toFloat()
        )
        bridgeCard.addView(bridgeInput)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, context.dp(8), 0, 0)
        }
        saveBridgeBtn.setTextColor(Color.WHITE)
        saveBridgeBtn.background = makeRoundedCardDrawable(
            palette.crimson, palette.crimsonDark, context.dp(4).toFloat()
        )
        saveBridgeBtn.setOnClickListener { onSaveBridgeUrl(bridgeInput.text.toString()) }

        pingBtn.text = if (isPinging) "Testando..." else "Testar APIs"
        pingBtn.isEnabled = !isPinging
        pingBtn.setTextColor(palette.textPrimary)
        pingBtn.background = makeRoundedCardDrawable(
            palette.surfaceVariant, palette.border, context.dp(4).toFloat()
        )
        pingBtn.setOnClickListener { onRunBridgePing() }

        btnRow.addView(
            saveBridgeBtn,
            LinearLayout.LayoutParams(0, context.dp(44), 1f).apply { rightMargin = context.dp(8) }
        )
        btnRow.addView(
            pingBtn,
            LinearLayout.LayoutParams(0, context.dp(44), 1f)
        )
        bridgeCard.addView(btnRow)

        pingResultsCol.removeAllViews()
        for (res in pingResults) {
            val row = TextView(context).apply {
                text = "${if (res.reachable) "✓" else "✕"} ${res.baseUrl} — ${res.note}"
                textSize = 12f
                setTextColor(if (res.reachable) Color.parseColor("#2E7D32") else palette.crimson)
                setPadding(0, context.dp(6), 0, 0)
            }
            pingResultsCol.addView(row)
        }
        bridgeCard.addView(pingResultsCol)
        container.addView(bridgeCard)
    }

    private fun sectionHeader(text: String, palette: OldTubePalette): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.textPrimary)
            setPadding(0, context.dp(14), 0, context.dp(6))
        }
    }

    private fun createOptionRow(
        title: String,
        subtitle: String,
        selected: Boolean,
        palette: OldTubePalette,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = makeRoundedCardDrawable(
                if (selected) palette.surface else palette.surfaceVariant,
                if (selected) palette.crimson else palette.border,
                context.dp(4).toFloat(),
                if (selected) context.dp(2) else 1
            )
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = context.dp(6) }

            val t = TextView(context).apply {
                text = if (selected) "● $title" else "○ $title"
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (selected) palette.crimson else palette.textPrimary)
            }
            val sub = TextView(context).apply {
                text = subtitle
                textSize = 12f
                setTextColor(palette.textSecondary)
                setPadding(0, context.dp(2), 0, 0)
            }
            addView(t)
            addView(sub)
            setOnClickListener { onClick() }
        }
    }

    private fun bindCheckbox(
        cb: CheckBox,
        checked: Boolean,
        palette: OldTubePalette,
        onChange: (Boolean) -> Unit
    ) {
        cb.setOnCheckedChangeListener(null)
        cb.isChecked = checked
        cb.setTextColor(palette.textPrimary)
        cb.minHeight = context.dp(48)
        cb.setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
    }
}
