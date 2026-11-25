package com.example.classic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

data class OldTubePalette(
    val isLight: Boolean,
    val bg: Int,
    val surface: Int,
    val surfaceVariant: Int,
    val border: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val crimson: Int = Color.parseColor("#CC181E"),
    val crimsonDark: Int = Color.parseColor("#990000"),
    val blueInfo: Int = Color.parseColor("#167AC6")
) {
    companion object {
        fun from(classicLight: Boolean): OldTubePalette {
            return if (classicLight) {
                OldTubePalette(
                    isLight = true,
                    bg = Color.parseColor("#F1F1F1"),
                    surface = Color.parseColor("#FFFFFF"),
                    surfaceVariant = Color.parseColor("#EAEAEA"),
                    border = Color.parseColor("#D8D8D8"),
                    textPrimary = Color.parseColor("#222222"),
                    textSecondary = Color.parseColor("#666666")
                )
            } else {
                OldTubePalette(
                    isLight = false,
                    bg = Color.parseColor("#121212"),
                    surface = Color.parseColor("#1E1E1E"),
                    surfaceVariant = Color.parseColor("#2A2A2A"),
                    border = Color.parseColor("#353535"),
                    textPrimary = Color.parseColor("#F0F0F0"),
                    textSecondary = Color.parseColor("#AAAAAA")
                )
            }
        }
    }
}

fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density + 0.5f).toInt()

fun makeRoundedCardDrawable(
    fillColor: Int,
    borderColor: Int,
    radiusPx: Float,
    strokePx: Int = 1
): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusPx
        setColor(fillColor)
        if (strokePx > 0) setStroke(strokePx, borderColor)
    }
}

fun makeSelectableDrawable(
    normalColor: Int,
    pressedColor: Int,
    borderColor: Int,
    radiusPx: Float,
    strokePx: Int = 1
): StateListDrawable {
    val states = StateListDrawable()
    states.addState(
        intArrayOf(android.R.attr.state_pressed),
        makeRoundedCardDrawable(pressedColor, borderColor, radiusPx, strokePx)
    )
    states.addState(
        intArrayOf(),
        makeRoundedCardDrawable(normalColor, borderColor, radiusPx, strokePx)
    )
    return states
}

enum class ClassicIconType {
    PLAY_BADGE,
    HOME,
    SEARCH,
    LIBRARY,
    TUNE,
    LINK,
    REFRESH,
    ADD,
    BOOKMARK,
    BACK,
    FULLSCREEN
}

/**
 * Pure Canvas Drawable avoiding API 21+ XML <vector> crashes on Android 4.4 KitKat.
 */
class ClassicVectorIcon(
    private val type: ClassicIconType,
    private val tintColor: Int,
    private val sizePx: Int
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tintColor
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tintColor
        style = Paint.Style.STROKE
        strokeWidth = (sizePx * 0.1f).coerceAtLeast(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun getIntrinsicWidth(): Int = sizePx
    override fun getIntrinsicHeight(): Int = sizePx

    override fun draw(canvas: Canvas) {
        val w = bounds.width().takeIf { it > 0 }?.toFloat() ?: sizePx.toFloat()
        val h = bounds.height().takeIf { it > 0 }?.toFloat() ?: sizePx.toFloat()
        val cx = bounds.left + w / 2f
        val cy = bounds.top + h / 2f
        val u = minOf(w, h)

        when (type) {
            ClassicIconType.PLAY_BADGE -> {
                val tri = Path().apply {
                    moveTo(cx - u * 0.18f, cy - u * 0.24f)
                    lineTo(cx + u * 0.26f, cy)
                    lineTo(cx - u * 0.18f, cy + u * 0.24f)
                    close()
                }
                canvas.drawPath(tri, paint)
            }
            ClassicIconType.HOME -> {
                val p = Path().apply {
                    moveTo(cx, cy - u * 0.34f)
                    lineTo(cx + u * 0.36f, cy - u * 0.02f)
                    lineTo(cx + u * 0.24f, cy - u * 0.02f)
                    lineTo(cx + u * 0.24f, cy + u * 0.32f)
                    lineTo(cx + u * 0.07f, cy + u * 0.32f)
                    lineTo(cx + u * 0.07f, cy + u * 0.10f)
                    lineTo(cx - u * 0.07f, cy + u * 0.10f)
                    lineTo(cx - u * 0.07f, cy + u * 0.32f)
                    lineTo(cx - u * 0.24f, cy + u * 0.32f)
                    lineTo(cx - u * 0.24f, cy - u * 0.02f)
                    lineTo(cx - u * 0.36f, cy - u * 0.02f)
                    close()
                }
                canvas.drawPath(p, paint)
            }
            ClassicIconType.SEARCH -> {
                val r = u * 0.22f
                canvas.drawCircle(cx - u * 0.07f, cy - u * 0.07f, r, strokePaint)
                canvas.drawLine(
                    cx + u * 0.09f, cy + u * 0.09f,
                    cx + u * 0.30f, cy + u * 0.30f,
                    strokePaint
                )
            }
            ClassicIconType.LIBRARY -> {
                canvas.drawRect(
                    cx - u * 0.30f, cy - u * 0.16f,
                    cx + u * 0.30f, cy + u * 0.28f,
                    strokePaint
                )
                canvas.drawLine(cx - u * 0.22f, cy - u * 0.28f, cx + u * 0.22f, cy - u * 0.28f, strokePaint)
                val tri = Path().apply {
                    moveTo(cx - u * 0.08f, cy - u * 0.04f)
                    lineTo(cx + u * 0.12f, cy + u * 0.06f)
                    lineTo(cx - u * 0.08f, cy + u * 0.16f)
                    close()
                }
                canvas.drawPath(tri, paint)
            }
            ClassicIconType.TUNE -> {
                for (row in listOf(-0.22f, 0f, 0.22f)) {
                    val y = cy + u * row
                    canvas.drawLine(cx - u * 0.30f, y, cx + u * 0.30f, y, strokePaint)
                    val knobX = cx + u * (if (row == 0f) 0.12f else -0.10f)
                    canvas.drawCircle(knobX, y, u * 0.07f, paint)
                }
            }
            ClassicIconType.LINK -> {
                canvas.drawCircle(cx - u * 0.12f, cy, u * 0.14f, strokePaint)
                canvas.drawCircle(cx + u * 0.12f, cy, u * 0.14f, strokePaint)
            }
            ClassicIconType.REFRESH -> {
                val r = u * 0.25f
                canvas.drawArc(
                    cx - r, cy - r, cx + r, cy + r,
                    30f, 290f, false, strokePaint
                )
            }
            ClassicIconType.ADD -> {
                canvas.drawLine(cx - u * 0.26f, cy, cx + u * 0.26f, cy, strokePaint)
                canvas.drawLine(cx, cy - u * 0.26f, cx, cy + u * 0.26f, strokePaint)
            }
            ClassicIconType.BOOKMARK -> {
                val p = Path().apply {
                    moveTo(cx - u * 0.20f, cy - u * 0.30f)
                    lineTo(cx + u * 0.20f, cy - u * 0.30f)
                    lineTo(cx + u * 0.20f, cy + u * 0.30f)
                    lineTo(cx, cy + u * 0.14f)
                    lineTo(cx - u * 0.20f, cy + u * 0.30f)
                    close()
                }
                canvas.drawPath(p, paint)
            }
            ClassicIconType.BACK -> {
                canvas.drawLine(cx - u * 0.25f, cy, cx + u * 0.28f, cy, strokePaint)
                canvas.drawLine(cx - u * 0.25f, cy, cx - u * 0.02f, cy - u * 0.22f, strokePaint)
                canvas.drawLine(cx - u * 0.25f, cy, cx - u * 0.02f, cy + u * 0.22f, strokePaint)
            }
            ClassicIconType.FULLSCREEN -> {
                val d = u * 0.28f
                val k = u * 0.12f
                canvas.drawLine(cx - d, cy - d, cx - d + k, cy - d, strokePaint)
                canvas.drawLine(cx - d, cy - d, cx - d, cy - d + k, strokePaint)
                canvas.drawLine(cx + d, cy - d, cx + d - k, cy - d, strokePaint)
                canvas.drawLine(cx + d, cy - d, cx + d, cy - d + k, strokePaint)
                canvas.drawLine(cx - d, cy + d, cx - d + k, cy + d, strokePaint)
                canvas.drawLine(cx - d, cy + d, cx - d, cy + d - k, strokePaint)
                canvas.drawLine(cx + d, cy + d, cx + d - k, cy + d, strokePaint)
                canvas.drawLine(cx + d, cy + d, cx + d, cy + d - k, strokePaint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        strokePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
