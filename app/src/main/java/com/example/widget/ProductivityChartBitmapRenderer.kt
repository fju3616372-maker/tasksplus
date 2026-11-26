package com.example.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.data.model.DailyProductivitySummary
import kotlin.math.ceil

/**
 * Renders a crisp Recharts-style Completed vs. Pending grouped bar chart onto an Android [Bitmap]
 * for the optional Home Screen Daily Productivity Widget. 100% Android 6.0 (API 23) safe.
 */
object ProductivityChartBitmapRenderer {

  private const val COLOR_COMPLETED = 0xFF10B981.toInt() // Emerald green
  private const val COLOR_PENDING = 0xFFF59E0B.toInt()   // Amber gold
  private const val DEFAULT_COLOR_GRID = 0x44C4B5FD
  private const val DEFAULT_COLOR_BASELINE = 0x77C4B5FD
  private const val DEFAULT_COLOR_AXIS_TEXT = 0xFFDCD3FF.toInt()
  private const val DEFAULT_COLOR_TODAY_TEXT = 0xFFFFFFFF.toInt()

  fun renderChartBitmap(
    summary: DailyProductivitySummary,
    widthPx: Int = 720,
    heightPx: Int = 300,
    gridColor: Int = DEFAULT_COLOR_GRID,
    baselineColor: Int = DEFAULT_COLOR_BASELINE,
    axisTextColor: Int = DEFAULT_COLOR_AXIS_TEXT,
    todayTextColor: Int = DEFAULT_COLOR_TODAY_TEXT
  ): Bitmap {
    val safeWidth = widthPx.coerceAtLeast(320)
    val safeHeight = heightPx.coerceAtLeast(160)
    val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.TRANSPARENT)

    val padLeft = safeWidth * 0.085f
    val padRight = safeWidth * 0.035f
    val padTop = safeHeight * 0.10f
    val padBottom = safeHeight * 0.20f

    val plotLeft = padLeft
    val plotRight = safeWidth - padRight
    val plotTop = padTop
    val plotBottom = safeHeight - padBottom
    val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
    val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

    val yMax = summary.maxBarValue.let { raw ->
      val safe = raw.coerceAtLeast(2)
      if (safe <= 4) 4 else ceil(safe / 2.0).toInt() * 2
    }

    val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = gridColor
      strokeWidth = 2f
      style = Paint.Style.STROKE
      pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    val baseLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = baselineColor
      strokeWidth = 2.5f
      style = Paint.Style.STROKE
    }
    val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = axisTextColor
      textSize = safeHeight * 0.085f
      textAlign = Paint.Align.RIGHT
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val xLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = axisTextColor
      textSize = safeHeight * 0.085f
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    // Draw Y-axis ticks & horizontal dashed CartesianGrid
    val tickValues = listOf(yMax, yMax / 2, 0)
    tickValues.forEachIndexed { idx, tickVal ->
      val y = when (idx) {
        0 -> plotTop
        1 -> plotTop + plotHeight / 2f
        else -> plotBottom
      }
      canvas.drawLine(plotLeft, y, plotRight, y, if (idx == 2) baseLinePaint else gridPaint)
      canvas.drawText(
        tickVal.toString(),
        plotLeft - 12f,
        y + axisLabelPaint.textSize * 0.35f,
        axisLabelPaint
      )
    }

    val completedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = COLOR_COMPLETED
      style = Paint.Style.FILL
    }
    val pendingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = COLOR_PENDING
      style = Paint.Style.FILL
    }

    val days = summary.days
    if (days.isNotEmpty()) {
      val slotWidth = plotWidth / days.size.toFloat()
      val barGap = (slotWidth * 0.08f).coerceIn(4f, 10f)
      val barWidth = (slotWidth * 0.30f).coerceIn(10f, 34f)
      val cornerRadius = 7f

      days.forEachIndexed { idx, point ->
        val centerX = plotLeft + slotWidth * idx + slotWidth / 2f
        val leftBarLeft = centerX - barGap / 2f - barWidth
        val rightBarLeft = centerX + barGap / 2f

        if (point.completedCount > 0) {
          val h = ((point.completedCount.toFloat() / yMax.toFloat()) * plotHeight)
            .coerceAtLeast(8f)
          val rect = RectF(leftBarLeft, plotBottom - h, leftBarLeft + barWidth, plotBottom)
          canvas.drawRoundRect(rect, cornerRadius, cornerRadius, completedPaint)
        }

        if (point.pendingCount > 0) {
          val h = ((point.pendingCount.toFloat() / yMax.toFloat()) * plotHeight)
            .coerceAtLeast(8f)
          val rect = RectF(rightBarLeft, plotBottom - h, rightBarLeft + barWidth, plotBottom)
          canvas.drawRoundRect(rect, cornerRadius, cornerRadius, pendingPaint)
        }

        xLabelPaint.color = if (point.isToday) todayTextColor else axisTextColor
        xLabelPaint.typeface = if (point.isToday) {
          Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        } else {
          Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(
          point.shortLabel,
          centerX,
          plotBottom + safeHeight * 0.13f,
          xLabelPaint
        )
      }
    }

    return bitmap
  }
}
