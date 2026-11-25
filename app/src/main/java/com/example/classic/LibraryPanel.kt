package com.example.classic

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.example.R
import com.example.data.local.SavedVideoEntity
import com.example.data.model.OptimizationSettings
import com.example.data.model.VideoItem

class LibraryPanel(
    private val context: Context,
    private val onVideoClick: (VideoItem) -> Unit,
    private val onBookmarkToggle: (VideoItem) -> Unit,
    private val onClearHistory: () -> Unit
) {
    val root: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    private var showBookmarksTab = true
    private var lastBookmarks: List<SavedVideoEntity> = emptyList()
    private var lastHistory: List<SavedVideoEntity> = emptyList()
    private var lastBookmarkedIds: Set<String> = emptySet()
    private var lastSettings: OptimizationSettings = OptimizationSettings()

    private val headerTitle = TextView(context).apply {
        text = "Biblioteca Local"
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
    }
    private val clearHistoryBtn = Button(context).apply {
        text = "Limpar histórico"
        textSize = 12f
        setOnClickListener { onClearHistory() }
    }
    private val bookmarksTabBtn = TextView(context).apply {
        text = "Assistir mais tarde"
        contentDescription = "tab_bookmarks"
        gravity = Gravity.CENTER
        textSize = 13f
        minHeight = context.dp(48)
    }
    private val historyTabBtn = TextView(context).apply {
        text = "Histórico"
        contentDescription = "tab_history"
        gravity = Gravity.CENTER
        textSize = 13f
        minHeight = context.dp(48)
    }

    private val contentFrame = FrameLayout(context)
    private val listView = ListView(context).apply {
        dividerHeight = context.dp(8)
        val p = context.dp(10)
        setPadding(p, p, p, p)
        clipToPadding = false
    }
    private val emptyState = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(context.dp(24), context.dp(24), context.dp(24), context.dp(24))
    }
    private val emptyImage = ImageView(context).apply {
        setImageResource(R.drawable.img_empty_cinema)
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    private val emptyTitle = TextView(context).apply {
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
    }
    private val emptySubtitle = TextView(context).apply {
        textSize = 13f
        gravity = Gravity.CENTER
    }

    private val adapter = ClassicVideoAdapter(context, onVideoClick, onBookmarkToggle)

    init {
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(8))
        }
        topRow.addView(
            headerTitle,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        topRow.addView(
            clearHistoryBtn,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(40))
        )
        root.addView(topRow)

        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(context.dp(12), 0, context.dp(12), context.dp(8))
        }
        tabRow.addView(
            bookmarksTabBtn,
            LinearLayout.LayoutParams(0, context.dp(48), 1f).apply { rightMargin = context.dp(6) }
        )
        tabRow.addView(
            historyTabBtn,
            LinearLayout.LayoutParams(0, context.dp(48), 1f)
        )
        root.addView(tabRow)

        bookmarksTabBtn.setOnClickListener {
            showBookmarksTab = true
            renderInternal()
        }
        historyTabBtn.setOnClickListener {
            showBookmarksTab = false
            renderInternal()
        }

        emptyState.addView(
            emptyImage,
            LinearLayout.LayoutParams(context.dp(200), context.dp(116)).apply {
                bottomMargin = context.dp(12)
            }
        )
        emptyState.addView(emptyTitle)
        emptyState.addView(
            emptySubtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = context.dp(6) }
        )

        listView.adapter = adapter
        contentFrame.addView(
            listView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        contentFrame.addView(
            emptyState,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            contentFrame,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
    }

    fun render(
        bookmarks: List<SavedVideoEntity>,
        history: List<SavedVideoEntity>,
        bookmarkedIds: Set<String>,
        settings: OptimizationSettings
    ) {
        lastBookmarks = bookmarks
        lastHistory = history
        lastBookmarkedIds = bookmarkedIds
        lastSettings = settings
        renderInternal()
    }

    private fun renderInternal() {
        val palette = OldTubePalette.from(lastSettings.classicLightTheme)
        root.setBackgroundColor(palette.bg)

        headerTitle.setTextColor(palette.textPrimary)
        clearHistoryBtn.visibility = if (!showBookmarksTab && lastHistory.isNotEmpty()) View.VISIBLE else View.GONE
        clearHistoryBtn.setTextColor(palette.crimson)
        clearHistoryBtn.background = makeRoundedCardDrawable(
            palette.surface, palette.border, context.dp(4).toFloat()
        )

        styleTab(bookmarksTabBtn, showBookmarksTab, palette)
        styleTab(historyTabBtn, !showBookmarksTab, palette)

        val entities = if (showBookmarksTab) lastBookmarks else lastHistory
        if (entities.isEmpty()) {
            listView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            emptyTitle.setTextColor(palette.textPrimary)
            emptySubtitle.setTextColor(palette.textSecondary)
            if (showBookmarksTab) {
                emptyTitle.text = context.getString(R.string.empty_library_title)
                emptySubtitle.text = context.getString(R.string.empty_library_subtitle)
            } else {
                emptyTitle.text = "Histórico vazio"
                emptySubtitle.text = "Os vídeos assistidos localmente no aparelho aparecerão aqui."
            }
        } else {
            emptyState.visibility = View.GONE
            listView.visibility = View.VISIBLE
            val videos = entities.map {
                VideoItem(
                    id = it.videoId,
                    title = it.title,
                    channelName = it.channelName,
                    channelId = it.channelId,
                    thumbnailUrl = it.thumbnailUrl,
                    publishedAt = it.publishedAt,
                    durationText = it.durationText,
                    sourceTag = if (showBookmarksTab) "Salvo" else "Histórico"
                )
            }
            adapter.submitData(videos, lastBookmarkedIds, lastSettings.lowRamThumbnails, palette)
        }
    }

    private fun styleTab(view: TextView, active: Boolean, palette: OldTubePalette) {
        view.setTypeface(Typeface.DEFAULT, if (active) Typeface.BOLD else Typeface.NORMAL)
        view.setTextColor(if (active) Color.WHITE else palette.textPrimary)
        view.background = makeRoundedCardDrawable(
            if (active) palette.crimson else palette.surface,
            if (active) palette.crimsonDark else palette.border,
            context.dp(4).toFloat()
        )
    }
}
