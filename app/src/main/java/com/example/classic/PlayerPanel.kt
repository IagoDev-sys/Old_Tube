package com.example.classic

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.example.data.model.ActivePlayerBackend
import com.example.data.model.OptimizationSettings
import com.example.data.model.ResolvedStream
import com.example.data.model.VideoItem
import com.example.data.remote.ThumbnailCacheLoader

class PlayerPanel(
    private val context: Context,
    private val onBack: () -> Unit,
    private val onSetFullscreen: (Boolean) -> Unit,
    private val onSwitchBackend: (ActivePlayerBackend) -> Unit,
    private val onPositionChanged: (String, Long) -> Unit,
    private val onBookmarkToggle: (VideoItem) -> Unit,
    private val onToggleSubscribe: (String, String) -> Unit,
    private val onSelectRelated: (VideoItem) -> Unit,
    private val onRetryVideo: (String) -> Unit
) {
    val root: FrameLayout = FrameLayout(context)

    private var currentVideoId: String = ""
    private var useVerticalShortFrame: Boolean = false
    private var expandDescription: Boolean = false

    private val topBar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(6), context.dp(4), context.dp(6), context.dp(4))
    }
    private val backBtn = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = "Voltar"
        setOnClickListener { onBack() }
    }
    private val headerTitle = TextView(context).apply {
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val fullscreenBtn = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = "Tela cheia"
    }

    private val stageWrapper = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
    }
    private val playerStage = SafeLegacyPlayerStage(
        context = context,
        onFallbackToEmbed = { onSwitchBackend(ActivePlayerBackend.IFRAME_EMBED) },
        onFallbackToMobile = { onSwitchBackend(ActivePlayerBackend.MOBILE_WEB) },
        onPositionSaved = { pos ->
            if (currentVideoId.isNotBlank()) onPositionChanged(currentVideoId, pos)
        }
    )

    private val scrollBody = ScrollView(context)
    private val bodyColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(20))
    }

    private val statusOverlay = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(context.dp(24), context.dp(24), context.dp(24), context.dp(24))
    }
    private val normalLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    private val fullscreenContainer = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
    }

    init {
        topBar.addView(backBtn, LinearLayout.LayoutParams(context.dp(48), context.dp(48)))
        topBar.addView(
            headerTitle,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = context.dp(4)
            }
        )
        topBar.addView(fullscreenBtn, LinearLayout.LayoutParams(context.dp(48), context.dp(48)))

        stageWrapper.addView(
            playerStage,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        scrollBody.addView(bodyColumn)
        normalLayout.addView(topBar)
        normalLayout.addView(stageWrapper)
        normalLayout.addView(
            scrollBody,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        root.addView(
            normalLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            fullscreenContainer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            statusOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun render(
        playerState: PlayerUiState,
        relatedVideos: List<VideoItem>,
        bookmarkedIds: Set<String>,
        followedChannelIds: Set<String>,
        isFullscreen: Boolean,
        settings: OptimizationSettings
    ) {
        val palette = OldTubePalette.from(settings.classicLightTheme)
        root.setBackgroundColor(palette.bg)
        topBar.setBackgroundColor(palette.surface)
        backBtn.setImageDrawable(ClassicVectorIcon(ClassicIconType.BACK, palette.textPrimary, context.dp(22)))
        fullscreenBtn.setImageDrawable(
            ClassicVectorIcon(ClassicIconType.FULLSCREEN, palette.textPrimary, context.dp(20))
        )
        headerTitle.setTextColor(palette.textPrimary)

        when (playerState) {
            is PlayerUiState.Idle -> {
                playerStage.releaseMedia()
                normalLayout.visibility = View.GONE
                fullscreenContainer.visibility = View.GONE
                statusOverlay.visibility = View.VISIBLE
                showIdleState(palette)
            }
            is PlayerUiState.Loading -> {
                playerStage.releaseMedia()
                normalLayout.visibility = View.GONE
                fullscreenContainer.visibility = View.GONE
                statusOverlay.visibility = View.VISIBLE
                showLoadingState(playerState.title, palette)
            }
            is PlayerUiState.Error -> {
                playerStage.releaseMedia()
                normalLayout.visibility = View.GONE
                fullscreenContainer.visibility = View.GONE
                statusOverlay.visibility = View.VISIBLE
                showErrorState(playerState.videoId, playerState.message, palette)
            }
            is PlayerUiState.Ready -> {
                statusOverlay.visibility = View.GONE
                val stream = playerState.stream
                if (currentVideoId != stream.videoId) {
                    currentVideoId = stream.videoId
                    useVerticalShortFrame = stream.isShort
                    expandDescription = false
                }

                if (isFullscreen) {
                    normalLayout.visibility = View.GONE
                    fullscreenContainer.visibility = View.VISIBLE
                    moveStageTo(fullscreenContainer, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                } else {
                    fullscreenContainer.visibility = View.GONE
                    normalLayout.visibility = View.VISIBLE
                    val stageHeight = if (useVerticalShortFrame) context.dp(320) else context.dp(230)
                    stageWrapper.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        stageHeight
                    )
                    val stageWidth = if (useVerticalShortFrame) context.dp(186) else FrameLayout.LayoutParams.MATCH_PARENT
                    moveStageTo(stageWrapper, stageWidth, FrameLayout.LayoutParams.MATCH_PARENT)
                    fullscreenBtn.setOnClickListener { onSetFullscreen(true) }
                    headerTitle.text = stream.channelName
                    rebuildMetadata(
                        stream = stream,
                        activeBackend = playerState.activeBackend,
                        relatedVideos = relatedVideos,
                        isBookmarked = stream.videoId in bookmarkedIds,
                        isSubscribed = stream.channelId.isNotBlank() && stream.channelId in followedChannelIds,
                        settings = settings,
                        palette = palette
                    )
                }
                playerStage.bind(stream, playerState.activeBackend, playerState.resumePositionMs)
            }
        }
    }

    private fun moveStageTo(target: FrameLayout, width: Int, height: Int) {
        val currentParent = playerStage.parent as? FrameLayout
        if (currentParent != target) {
            currentParent?.removeView(playerStage)
            target.addView(playerStage, FrameLayout.LayoutParams(width, height, Gravity.CENTER))
        } else {
            playerStage.layoutParams = FrameLayout.LayoutParams(width, height, Gravity.CENTER)
        }
    }

    private fun rebuildMetadata(
        stream: ResolvedStream,
        activeBackend: ActivePlayerBackend,
        relatedVideos: List<VideoItem>,
        isBookmarked: Boolean,
        isSubscribed: Boolean,
        settings: OptimizationSettings,
        palette: OldTubePalette
    ) {
        bodyColumn.removeAllViews()

        val titleView = TextView(context).apply {
            text = stream.title
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.textPrimary)
        }
        bodyColumn.addView(titleView)

        val channelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(10), 0, context.dp(10))
        }
        val avatar = TextView(context).apply {
            text = stream.channelName.take(1).uppercase()
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = makeRoundedCardDrawable(palette.crimson, palette.crimson, context.dp(3).toFloat(), 0)
        }
        channelRow.addView(avatar, LinearLayout.LayoutParams(context.dp(36), context.dp(36)))

        val chCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(10), 0, context.dp(8), 0)
        }
        val chName = TextView(context).apply {
            text = stream.channelName
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.textPrimary)
        }
        val viaView = TextView(context).apply {
            text = "Via ${stream.resolvedVia} · ${stream.qualityLabel}"
            textSize = 11f
            setTextColor(palette.textSecondary)
        }
        chCol.addView(chName)
        chCol.addView(viaView)
        channelRow.addView(chCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (stream.channelId.isNotBlank()) {
            val subBtn = Button(context).apply {
                text = if (isSubscribed) "Inscrito ✓" else "Inscrever-se"
                textSize = 12f
                setTextColor(if (isSubscribed) palette.textPrimary else Color.WHITE)
                background = makeRoundedCardDrawable(
                    if (isSubscribed) palette.surfaceVariant else palette.crimson,
                    palette.border,
                    context.dp(3).toFloat()
                )
                setOnClickListener { onToggleSubscribe(stream.channelId, stream.channelName) }
            }
            channelRow.addView(subBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(40)))
        }
        bodyColumn.addView(channelRow)

        val engineLabel = TextView(context).apply {
            text = "Modo de Reprodução:"
            textSize = 12f
            setTextColor(palette.textSecondary)
            setPadding(0, context.dp(4), 0, context.dp(6))
        }
        bodyColumn.addView(engineLabel)

        val engineRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, context.dp(10))
        }
        val hasH264 = stream.directH264Url != null
        engineRow.addView(
            makeEngineChip(
                label = if (hasH264) "H.264 Nativo" else "H.264 Indisp.",
                selected = activeBackend == ActivePlayerBackend.NATIVE_H264,
                enabled = hasH264,
                contentDesc = "chip_native_engine",
                palette = palette
            ) { onSwitchBackend(ActivePlayerBackend.NATIVE_H264) }
        )
        engineRow.addView(
            makeEngineChip(
                label = "Embed Direto",
                selected = activeBackend == ActivePlayerBackend.IFRAME_EMBED,
                enabled = true,
                contentDesc = "chip_iframe_engine",
                palette = palette
            ) { onSwitchBackend(ActivePlayerBackend.IFRAME_EMBED) }
        )
        engineRow.addView(
            makeEngineChip(
                label = "Mobile Direto",
                selected = activeBackend == ActivePlayerBackend.MOBILE_WEB,
                enabled = true,
                contentDesc = "chip_mobile_engine",
                palette = palette
            ) { onSwitchBackend(ActivePlayerBackend.MOBILE_WEB) }
        )
        bodyColumn.addView(engineRow)

        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, context.dp(10))
        }
        val currentItem = VideoItem(
            id = stream.videoId,
            title = stream.title,
            channelName = stream.channelName,
            channelId = stream.channelId,
            thumbnailUrl = stream.thumbnailUrl,
            description = stream.description,
            sourceTag = stream.resolvedVia,
            isShort = stream.isShort
        )
        val saveBtn = Button(context).apply {
            text = if (isBookmarked) "Salvo ✓" else "Assistir mais tarde"
            textSize = 12f
            setTextColor(palette.textPrimary)
            background = makeRoundedCardDrawable(palette.surface, palette.border, context.dp(4).toFloat())
            setOnClickListener { onBookmarkToggle(currentItem) }
        }
        val extBtn = Button(context).apply {
            text = "Abrir no navegador"
            textSize = 12f
            setTextColor(palette.textPrimary)
            background = makeRoundedCardDrawable(palette.surface, palette.border, context.dp(4).toFloat())
            setOnClickListener { openExternalUrl(stream.videoId, stream.isShort) }
        }
        actionRow.addView(
            saveBtn,
            LinearLayout.LayoutParams(0, context.dp(44), 1f).apply { rightMargin = context.dp(8) }
        )
        actionRow.addView(
            extBtn,
            LinearLayout.LayoutParams(0, context.dp(44), 1f)
        )
        bodyColumn.addView(actionRow)

        val nextShort = relatedVideos
            .filter { it.id != stream.videoId }
            .let { others -> if (stream.isShort) others.firstOrNull { it.isShort } ?: others.firstOrNull() else null }

        if (stream.isShort || nextShort != null) {
            val shortRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, context.dp(10))
            }
            val aspectBtn = Button(context).apply {
                text = if (useVerticalShortFrame) "Tela 9:16 (Short)" else "Tela 16:9"
                contentDescription = "toggle_short_aspect_button"
                textSize = 12f
                setTextColor(palette.textPrimary)
                background = makeRoundedCardDrawable(palette.surface, palette.border, context.dp(4).toFloat())
                setOnClickListener {
                    useVerticalShortFrame = !useVerticalShortFrame
                    val stageHeight = if (useVerticalShortFrame) context.dp(320) else context.dp(230)
                    stageWrapper.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        stageHeight
                    )
                    val stageWidth = if (useVerticalShortFrame) context.dp(186) else FrameLayout.LayoutParams.MATCH_PARENT
                    moveStageTo(stageWrapper, stageWidth, FrameLayout.LayoutParams.MATCH_PARENT)
                    text = if (useVerticalShortFrame) "Tela 9:16 (Short)" else "Tela 16:9"
                }
            }
            shortRow.addView(
                aspectBtn,
                LinearLayout.LayoutParams(0, context.dp(44), 1f).apply { rightMargin = context.dp(8) }
            )
            if (nextShort != null) {
                val nextBtn = Button(context).apply {
                    text = "Próximo Short →"
                    contentDescription = "next_short_button"
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    background = makeRoundedCardDrawable(palette.crimson, palette.crimsonDark, context.dp(4).toFloat())
                    setOnClickListener { onSelectRelated(nextShort) }
                }
                shortRow.addView(nextBtn, LinearLayout.LayoutParams(0, context.dp(44), 1f))
            }
            bodyColumn.addView(shortRow)
        }

        if (stream.description.isNotBlank()) {
            val descBox = TextView(context).apply {
                text = stream.description
                textSize = 12f
                setTextColor(palette.textSecondary)
                maxLines = if (expandDescription) 40 else 3
                ellipsize = TextUtils.TruncateAt.END
                background = makeRoundedCardDrawable(palette.surface, palette.border, context.dp(4).toFloat())
                setPadding(context.dp(10), context.dp(10), context.dp(10), context.dp(10))
                setOnClickListener {
                    expandDescription = !expandDescription
                    maxLines = if (expandDescription) 40 else 3
                }
            }
            bodyColumn.addView(
                descBox,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = context.dp(12) }
            )
        }

        val upNext = relatedVideos.filter { it.id != stream.videoId }.take(10)
        if (upNext.isNotEmpty()) {
            val nextHeader = TextView(context).apply {
                text = "Próximos vídeos"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.textPrimary)
                setPadding(0, context.dp(4), 0, context.dp(8))
            }
            bodyColumn.addView(nextHeader)

            for (video in upNext) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    background = makeSelectableDrawable(
                        palette.surface, palette.surfaceVariant, palette.border, context.dp(4).toFloat()
                    )
                    setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = context.dp(6) }
                    setOnClickListener { onSelectRelated(video) }
                }
                val thumb = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(Color.BLACK)
                }
                ThumbnailCacheLoader.load(thumb, video.thumbnailUrl, settings.lowRamThumbnails)
                row.addView(thumb, LinearLayout.LayoutParams(context.dp(116), context.dp(66)))

                val col = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(context.dp(10), 0, 0, 0)
                }
                val t = TextView(context).apply {
                    text = video.title
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(palette.textPrimary)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                val ch = TextView(context).apply {
                    text = video.channelName
                    textSize = 11f
                    setTextColor(palette.textSecondary)
                }
                col.addView(t)
                col.addView(ch)
                row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                bodyColumn.addView(row)
            }
        }
    }

    private fun makeEngineChip(
        label: String,
        selected: Boolean,
        enabled: Boolean,
        contentDesc: String,
        palette: OldTubePalette,
        onClick: () -> Unit
    ): Button {
        return Button(context).apply {
            text = label
            textSize = 11f
            this.isEnabled = enabled
            contentDescription = contentDesc
            setTextColor(
                when {
                    !enabled -> palette.textSecondary
                    selected -> Color.WHITE
                    else -> palette.textPrimary
                }
            )
            background = makeRoundedCardDrawable(
                if (selected && enabled) palette.crimson else palette.surface,
                if (selected && enabled) palette.crimsonDark else palette.border,
                context.dp(4).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(0, context.dp(44), 1f).apply {
                rightMargin = context.dp(4)
            }
            setOnClickListener { if (enabled) onClick() }
        }
    }

    private fun showIdleState(palette: OldTubePalette) {
        statusOverlay.removeAllViews()
        val btn = Button(context).apply {
            text = "Voltar ao início"
            setTextColor(Color.WHITE)
            background = makeRoundedCardDrawable(palette.crimson, palette.crimsonDark, context.dp(4).toFloat())
            setOnClickListener { onBack() }
        }
        statusOverlay.addView(btn)
    }

    private fun showLoadingState(title: String, palette: OldTubePalette) {
        statusOverlay.removeAllViews()
        statusOverlay.addView(ProgressBar(context).apply { isIndeterminate = true })
        statusOverlay.addView(
            TextView(context).apply {
                text = "Preparando: $title"
                textSize = 14f
                setTextColor(palette.textPrimary)
                gravity = Gravity.CENTER
                setPadding(0, context.dp(12), 0, 0)
            }
        )
    }

    private fun showErrorState(videoId: String, message: String, palette: OldTubePalette) {
        statusOverlay.removeAllViews()
        statusOverlay.addView(
            TextView(context).apply {
                text = message
                textSize = 14f
                setTextColor(palette.textPrimary)
                gravity = Gravity.CENTER
            }
        )
        val retry = Button(context).apply {
            text = "Tentar novamente"
            setTextColor(Color.WHITE)
            background = makeRoundedCardDrawable(palette.crimson, palette.crimsonDark, context.dp(4).toFloat())
            setOnClickListener { onRetryVideo(videoId) }
        }
        statusOverlay.addView(
            retry,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(48)).apply {
                topMargin = context.dp(12)
            }
        )
    }

    private fun openExternalUrl(videoId: String, isShort: Boolean) {
        val url = if (isShort) {
            "https://www.youtube.com/shorts/$videoId"
        } else {
            "https://www.youtube.com/watch?v=$videoId"
        }
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun release() {
        playerStage.releaseMedia()
    }
}
