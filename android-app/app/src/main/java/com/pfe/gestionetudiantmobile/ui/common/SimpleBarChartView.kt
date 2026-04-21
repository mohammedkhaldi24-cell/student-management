package com.pfe.gestionetudiantmobile.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.pfe.gestionetudiantmobile.R
import kotlin.math.max

data class SimpleChartEntry(
    val label: String,
    val value: Double,
    val valueLabel: String,
    @ColorInt val color: Int,
    val detail: String = ""
)

class SimpleBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val entries = mutableListOf<SimpleChartEntry>()
    private var maxValue: Double = 100.0
    private var emptyLabel: String = "Aucune donnee"

    private val density = resources.displayMetrics.density
    private val rowHeight = 64f * density
    private val barHeight = 10f * density
    private val cornerRadius = 8f * density
    private val sidePadding = 2f * density
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.textPrimary)
        textSize = 13f * resources.displayMetrics.scaledDensity
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.textSecondary)
        textSize = 12f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.RIGHT
    }
    private val detailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.textMuted)
        textSize = 11f * resources.displayMetrics.scaledDensity
    }
    private val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.textSecondary)
        textSize = 13f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.surfaceVariant)
        style = Paint.Style.FILL
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rect = RectF()

    init {
        setWillNotDraw(false)
        minimumHeight = (112f * density).toInt()
    }

    fun setEntries(
        nextEntries: List<SimpleChartEntry>,
        maxValue: Double,
        emptyLabel: String = "Aucune donnee"
    ) {
        entries.clear()
        entries += nextEntries
        this.maxValue = max(1.0, maxValue)
        this.emptyLabel = emptyLabel
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = if (entries.isEmpty()) {
            minimumHeight
        } else {
            (entries.size * rowHeight + paddingTop + paddingBottom).toInt()
        }
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) {
            canvas.drawText(
                emptyLabel,
                width / 2f,
                height / 2f - ((emptyPaint.descent() + emptyPaint.ascent()) / 2f),
                emptyPaint
            )
            return
        }

        val left = paddingLeft + sidePadding
        val right = width - paddingRight - sidePadding
        val valueWidth = 72f * density
        val labelRight = right - valueWidth - (8f * density)
        val barWidth = (right - left).coerceAtLeast(1f)

        entries.forEachIndexed { index, entry ->
            val top = paddingTop + index * rowHeight
            val labelBaseline = top + 17f * density
            val detailBaseline = top + 56f * density
            val barTop = top + 28f * density
            val ratio = (entry.value / maxValue).coerceIn(0.0, 1.0).toFloat()
            val displayLabel = TextUtils.ellipsize(
                entry.label,
                labelPaint,
                (labelRight - left).coerceAtLeast(1f),
                TextUtils.TruncateAt.END
            ).toString()

            canvas.drawText(displayLabel, left, labelBaseline, labelPaint)
            canvas.drawText(entry.valueLabel, right, labelBaseline, valuePaint)

            rect.set(left, barTop, right, barTop + barHeight)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, trackPaint)
            if (ratio > 0f) {
                fillPaint.color = entry.color
                rect.set(left, barTop, left + (barWidth * ratio), barTop + barHeight)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
            }

            if (entry.detail.isNotBlank()) {
                val detail = TextUtils.ellipsize(
                    entry.detail,
                    detailPaint,
                    barWidth,
                    TextUtils.TruncateAt.END
                ).toString()
                canvas.drawText(detail, left, detailBaseline, detailPaint)
            }
        }
    }

}
