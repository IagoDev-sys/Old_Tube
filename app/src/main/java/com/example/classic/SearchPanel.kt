package com.example.classic

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.example.data.model.OptimizationSettings
import com.example.data.model.VideoItem

class SearchPanel(
    private val context: Context,
    private val onSubmitSearch: (String) -> Unit,
    private val onVideoClick: (VideoItem) -> Unit,
    private val onBookmarkToggle: (VideoItem) -> Unit
) {
    val root: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    private val searchInput = EditText(context).apply {
        hint = "Pesquisar no OLD Tube ou colar link..."
        textSize = 14f
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
    }
    private val searchBtn = Button(context).apply {
        text = "Buscar"
        textSize = 13f
    }
    private val quickRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(context.dp(10), context.dp(4), context.dp(10), context.dp(8))
    }

    private val contentFrame = FrameLayout(context)
    private val listView = ListView(context).apply {
        dividerHeight = context.dp(8)
        val p = context.dp(10)
        setPadding(p, p, p, p)
        clipToPadding = false
    }
    private val loadingView = ProgressBar(context).apply { isIndeterminate = true }
    private val statusText = TextView(context).apply {
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(context.dp(24), context.dp(24), context.dp(24), context.dp(24))
    }

    private val adapter = ClassicVideoAdapter(context, onVideoClick, onBookmarkToggle)

    init {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(10), context.dp(10), context.dp(10), context.dp(6))
        }
        bar.addView(
            searchInput,
            LinearLayout.LayoutParams(0, context.dp(48), 1f).apply {
                rightMargin = context.dp(8)
            }
        )
        bar.addView(
            searchBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                context.dp(48)
            )
        )
        root.addView(bar)

        val quickScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(quickRow)
        }
        root.addView(quickScroll)

        listView.adapter = adapter
        contentFrame.addView(
            listView,
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
            statusText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            contentFrame,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        searchBtn.setOnClickListener { onSubmitSearch(searchInput.text.toString()) }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                onSubmitSearch(searchInput.text.toString())
                true
            } else {
                false
            }
        }
    }

    fun render(
        query: String,
        searchState: SearchUiState,
        bookmarkedIds: Set<String>,
        settings: OptimizationSettings
    ) {
        val palette = OldTubePalette.from(settings.classicLightTheme)
        root.setBackgroundColor(palette.bg)

        if (searchInput.text.toString() != query && !searchInput.hasFocus()) {
            searchInput.setText(query)
        }
        searchInput.setTextColor(palette.textPrimary)
        searchInput.setHintTextColor(palette.textSecondary)
        searchInput.background = makeRoundedCardDrawable(
            palette.surface, palette.border, context.dp(4).toFloat()
        )
        searchBtn.setTextColor(Color.WHITE)
        searchBtn.background = makeRoundedCardDrawable(
            palette.crimson, palette.crimsonDark, context.dp(4).toFloat()
        )

        quickRow.removeAllViews()
        val suggestions = listOf("Ciência", "Hardware antigo", "Shorts", "Música retrô", "Documentários")
        for (term in suggestions) {
            val chip = TextView(context).apply {
                text = term
                textSize = 12f
                setTextColor(palette.textPrimary)
                setTypeface(typeface, Typeface.BOLD)
                background = makeRoundedCardDrawable(
                    palette.surface, palette.border, context.dp(4).toFloat()
                )
                setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = context.dp(6) }
                setOnClickListener {
                    searchInput.setText(term)
                    onSubmitSearch(term)
                }
            }
            quickRow.addView(chip)
        }

        when (searchState) {
            is SearchUiState.Idle -> {
                loadingView.visibility = View.GONE
                listView.visibility = View.GONE
                statusText.visibility = View.VISIBLE
                statusText.setTextColor(palette.textSecondary)
                statusText.text = "Busque vídeos do YouTube ou cole diretamente qualquer link (watch, shorts ou youtu.be)."
            }
            is SearchUiState.Loading -> {
                loadingView.visibility = View.VISIBLE
                listView.visibility = View.GONE
                statusText.visibility = View.GONE
            }
            is SearchUiState.Error -> {
                loadingView.visibility = View.GONE
                listView.visibility = View.GONE
                statusText.visibility = View.VISIBLE
                statusText.setTextColor(palette.textPrimary)
                statusText.text = searchState.message
            }
            is SearchUiState.Success -> {
                loadingView.visibility = View.GONE
                statusText.visibility = View.GONE
                listView.visibility = View.VISIBLE
                adapter.submitData(
                    searchState.videos,
                    bookmarkedIds,
                    settings.lowRamThumbnails,
                    palette
                )
            }
        }
    }
}
