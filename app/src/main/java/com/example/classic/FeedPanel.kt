package com.example.classic

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.data.local.ChannelEntity
import com.example.data.model.DeviceTelemetry
import com.example.data.model.OptimizationSettings
import com.example.data.model.VideoItem

enum class FeedMediaFilter {
    ALL, VIDEOS_ONLY, SHORTS_ONLY
}

class FeedPanel(
    private val context: Context,
    private val isWideTablet: Boolean,
    private val onSelectChannel: (String?) -> Unit,
    private val onRemoveChannel: (String) -> Unit,
    private val onRefresh: () -> Unit,
    private val onOpenAddChannel: () -> Unit,
    private val onVideoClick: (VideoItem) -> Unit,
    private val onBookmarkToggle: (VideoItem) -> Unit,
    private val onOpenDiagnostics: () -> Unit
) {
    val root: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    private val hardwareBanner = TextView(context).apply {
        textSize = 12f
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        setOnClickListener { onOpenDiagnostics() }
    }
    private val filterRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
    }
    private val channelRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(8), 0, context.dp(8), context.dp(6))
    }

    private val contentFrame = FrameLayout(context)
    private val gridView = GridView(context).apply {
        numColumns = if (isWideTablet) 2 else 1
        horizontalSpacing = context.dp(10)
        verticalSpacing = context.dp(10)
        val p = context.dp(10)
        setPadding(p, p, p, p)
        clipToPadding = false
    }
    private val loadingView = ProgressBar(context).apply { isIndeterminate = true }
    private val errorContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(context.dp(24), context.dp(24), context.dp(24), context.dp(24))
    }
    private val errorText = TextView(context).apply {
        textSize = 14f
        gravity = Gravity.CENTER
    }
    private val retryBtn = Button(context).apply {
        text = "Tentar novamente"
        setOnClickListener { onRefresh() }
    }

    private val adapter = ClassicVideoAdapter(context, onVideoClick, onBookmarkToggle)
    private var mediaFilter: FeedMediaFilter = FeedMediaFilter.ALL
    private var lastVideos: List<VideoItem> = emptyList()
    private var lastBookmarkedIds: Set<String> = emptySet()
    private var lastSettings: OptimizationSettings = OptimizationSettings()
    private var lastPalette: OldTubePalette = OldTubePalette.from(true)

    init {
        root.addView(
            hardwareBanner,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(context.dp(10), context.dp(8), context.dp(10), 0)
            }
        )

        val filterScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(filterRow)
        }
        root.addView(filterScroll)

        val channelScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(channelRow)
        }
        root.addView(channelScroll)

        gridView.adapter = adapter
        errorContainer.addView(errorText)
        errorContainer.addView(
            retryBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                context.dp(48)
            ).apply { topMargin = context.dp(12) }
        )

        contentFrame.addView(
            gridView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        contentFrame.addView(
            loadingView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        contentFrame.addView(
            errorContainer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            contentFrame,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    fun render(
        feedState: FeedUiState,
        channels: List<ChannelEntity>,
        selectedChannelId: String?,
        bookmarkedIds: Set<String>,
        settings: OptimizationSettings,
        telemetry: DeviceTelemetry
    ) {
        val palette = OldTubePalette.from(settings.classicLightTheme)
        lastPalette = palette
        lastBookmarkedIds = bookmarkedIds
        lastSettings = settings

        root.setBackgroundColor(palette.bg)

        val avcLabel = if (telemetry.hasHardwareAvc) "H.264 HW Ativo" else "Compatível WebView"
        hardwareBanner.text =
            "Modo Tab E · Android ${telemetry.androidVersion} (API ${telemetry.apiLevel}) · " +
                "${telemetry.availableRamMb}/${telemetry.totalRamMb} MB RAM · $avcLabel · ${settings.qualityPreset.label}"
        hardwareBanner.setTextColor(palette.textPrimary)
        hardwareBanner.background = makeRoundedCardDrawable(
            palette.surface, palette.border, context.dp(4).toFloat()
        )

        rebuildMediaFilters(palette)
        rebuildChannelRow(channels, selectedChannelId, palette)

        when (feedState) {
            is FeedUiState.Loading -> {
                loadingView.visibility = View.VISIBLE
                gridView.visibility = View.GONE
                errorContainer.visibility = View.GONE
            }
            is FeedUiState.Error -> {
                loadingView.visibility = View.GONE
                gridView.visibility = View.GONE
                errorContainer.visibility = View.VISIBLE
                errorText.text = feedState.message
                errorText.setTextColor(palette.textPrimary)
                retryBtn.setTextColor(Color.WHITE)
                retryBtn.background = makeRoundedCardDrawable(
                    palette.crimson, palette.crimsonDark, context.dp(3).toFloat()
                )
            }
            is FeedUiState.Success -> {
                lastVideos = feedState.videos
                loadingView.visibility = View.GONE
                errorContainer.visibility = View.GONE
                gridView.visibility = View.VISIBLE
                applyMediaFilter()
            }
        }
    }

    private fun applyMediaFilter() {
        val filtered = when (mediaFilter) {
            FeedMediaFilter.ALL -> lastVideos
            FeedMediaFilter.VIDEOS_ONLY -> lastVideos.filter { !it.isShort }
            FeedMediaFilter.SHORTS_ONLY -> lastVideos.filter { it.isShort }
        }
        adapter.submitData(filtered, lastBookmarkedIds, lastSettings.lowRamThumbnails, lastPalette)
    }

    private fun rebuildMediaFilters(palette: OldTubePalette) {
        filterRow.removeAllViews()
        val chips = listOf(
            "Tudo" to FeedMediaFilter.ALL,
            "Vídeos" to FeedMediaFilter.VIDEOS_ONLY,
            "Shorts" to FeedMediaFilter.SHORTS_ONLY
        )
        for ((label, type) in chips) {
            val active = mediaFilter == type
            val chip = createChip(label, active, palette) {
                mediaFilter = type
                rebuildMediaFilters(palette)
                applyMediaFilter()
            }
            chip.contentDescription = when (type) {
                FeedMediaFilter.ALL -> "filter_all_media"
                FeedMediaFilter.VIDEOS_ONLY -> "filter_videos_only"
                FeedMediaFilter.SHORTS_ONLY -> "filter_shorts_only"
            }
            filterRow.addView(chip)
        }
        filterRow.addView(
            createChip("+ Canal", false, palette) { onOpenAddChannel() }
        )
        filterRow.addView(
            createChip("Atualizar", false, palette) { onRefresh() }
        )
    }

    private fun rebuildChannelRow(
        channels: List<ChannelEntity>,
        selectedChannelId: String?,
        palette: OldTubePalette
    ) {
        channelRow.removeAllViews()
        channelRow.addView(
            createChip("Todos os canais", selectedChannelId == null, palette) {
                onSelectChannel(null)
            }
        )
        for (channel in channels) {
            val active = selectedChannelId == channel.channelId
            val chip = createChip(channel.name, active, palette) {
                onSelectChannel(channel.channelId)
            }
            chip.setOnLongClickListener {
                onRemoveChannel(channel.channelId)
                true
            }
            channelRow.addView(chip)
        }
    }

    private fun createChip(
        label: String,
        selected: Boolean,
        palette: OldTubePalette,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = 13f
            if (selected) setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (selected) Color.WHITE else palette.textPrimary)
            background = makeRoundedCardDrawable(
                if (selected) palette.crimson else palette.surface,
                if (selected) palette.crimsonDark else palette.border,
                context.dp(4).toFloat()
            )
            gravity = Gravity.CENTER
            minHeight = context.dp(40)
            setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = context.dp(6) }
            setOnClickListener { onClick() }
        }
    }
}
