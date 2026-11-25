package com.example.classic

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.data.model.VideoItem
import com.example.data.remote.ThumbnailCacheLoader

/**
 * ViewHolder-backed ListView/GridView adapter for classic 2013 YouTube video cards on Android 4.4.
 */
class ClassicVideoAdapter(
    private val context: Context,
    private val onVideoClick: (VideoItem) -> Unit,
    private val onBookmarkToggle: (VideoItem) -> Unit
) : BaseAdapter() {

    private var items: List<VideoItem> = emptyList()
    private var bookmarkedIds: Set<String> = emptySet()
    private var lowRamThumbs: Boolean = true
    private var palette: OldTubePalette = OldTubePalette.from(true)

    fun submitData(
        newItems: List<VideoItem>,
        newBookmarkedIds: Set<String>,
        lowRamThumbnails: Boolean,
        newPalette: OldTubePalette
    ) {
        items = newItems
        bookmarkedIds = newBookmarkedIds
        lowRamThumbs = lowRamThumbnails
        palette = newPalette
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): VideoItem = items[position]
    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: CardHolder
        val cardView: LinearLayout
        if (convertView == null) {
            holder = createHolder()
            cardView = holder.root
            cardView.tag = holder
        } else {
            cardView = convertView as LinearLayout
            holder = cardView.tag as CardHolder
        }

        val item = items[position]
        val isSaved = item.id in bookmarkedIds

        cardView.background = makeSelectableDrawable(
            normalColor = palette.surface,
            pressedColor = palette.surfaceVariant,
            borderColor = palette.border,
            radiusPx = context.dp(4).toFloat()
        )
        cardView.contentDescription = "video_card_${item.id}"

        ThumbnailCacheLoader.load(holder.thumb, item.thumbnailUrl, lowRamThumbs)

        if (item.isShort) {
            holder.badge.text = "SHORTS"
            holder.badge.background = makeRoundedCardDrawable(
                palette.crimson, palette.crimson, context.dp(2).toFloat(), 0
            )
            holder.badge.visibility = View.VISIBLE
        } else if (item.durationText.isNotBlank()) {
            holder.badge.text = item.durationText
            holder.badge.background = makeRoundedCardDrawable(
                Color.parseColor("#CC000000"), Color.TRANSPARENT, context.dp(2).toFloat(), 0
            )
            holder.badge.visibility = View.VISIBLE
        } else {
            holder.badge.visibility = View.GONE
        }

        holder.title.text = item.title
        holder.title.setTextColor(palette.textPrimary)

        val metaParts = listOfNotNull(
            item.channelName.takeIf { it.isNotBlank() },
            item.viewCountText.takeIf { it.isNotBlank() },
            item.publishedAt.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        holder.meta.text = metaParts
        holder.meta.setTextColor(palette.textSecondary)

        holder.sourceChip.text = item.sourceTag
        holder.sourceChip.setTextColor(palette.textSecondary)
        holder.sourceChip.background = makeRoundedCardDrawable(
            palette.surfaceVariant, palette.border, context.dp(3).toFloat()
        )

        holder.bookmarkBtn.setImageDrawable(
            ClassicVectorIcon(
                ClassicIconType.BOOKMARK,
                if (isSaved) palette.crimson else palette.textSecondary,
                context.dp(20)
            )
        )
        holder.bookmarkBtn.contentDescription =
            if (isSaved) "Remover dos salvos" else "Assistir mais tarde"

        cardView.setOnClickListener { onVideoClick(item) }
        holder.bookmarkBtn.setOnClickListener { onBookmarkToggle(item) }

        return cardView
    }

    private fun createHolder(): CardHolder {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = context.dp(8)
            setPadding(pad, pad, pad, pad)
        }

        val thumbFrame = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#181818"))
        }
        val thumb = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val thumbHeight = context.dp(156)
        thumbFrame.addView(
            thumb,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, thumbHeight)
        )

        val badge = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(context.dp(6), context.dp(2), context.dp(6), context.dp(2))
        }
        thumbFrame.addView(
            badge,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                setMargins(0, 0, context.dp(6), context.dp(6))
            }
        )
        root.addView(
            thumbFrame,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, thumbHeight)
        )

        val infoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(8), 0, 0)
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val title = TextView(context).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val meta = TextView(context).apply {
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, context.dp(3), 0, context.dp(4))
        }
        val sourceChip = TextView(context).apply {
            textSize = 11f
            setPadding(context.dp(6), context.dp(2), context.dp(6), context.dp(2))
        }
        textCol.addView(title)
        textCol.addView(meta)
        textCol.addView(
            sourceChip,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        infoRow.addView(
            textCol,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val minTouch = context.dp(48)
        val bookmarkBtn = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
        }
        infoRow.addView(bookmarkBtn, LinearLayout.LayoutParams(minTouch, minTouch))
        root.addView(infoRow)

        return CardHolder(root, thumb, badge, title, meta, sourceChip, bookmarkBtn)
    }

    private class CardHolder(
        val root: LinearLayout,
        val thumb: ImageView,
        val badge: TextView,
        val title: TextView,
        val meta: TextView,
        val sourceChip: TextView,
        val bookmarkBtn: ImageView
    )
}
