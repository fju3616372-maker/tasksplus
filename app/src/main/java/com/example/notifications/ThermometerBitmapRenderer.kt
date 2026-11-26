package com.example.notifications

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.data.model.UrgencyLevelMeta

/**
 * Renders high-contrast, API 23+ compatible Bitmap artwork showing the 1 (Green) to 5 (Red)
 * thermometer-style urgency slide bar so the visual thermometer is directly visible inside
 * Android task notifications (BigPictureStyle + collapsed large icon badge).
 */
object ThermometerBitmapRenderer {

  private val scaleColors = intArrayOf(
    0xFF22C55E.toInt(), // 1 - Vibrant Green
    0xFF84CC16.toInt(), // 2 - Lime Green
    0xFFFACC15.toInt(), // 3 - Warm Yellow/Amber
    0xFFFB923C.toInt(), // 4 - Vivid Orange
    0xFFEF4444.toInt()  // 5 - Critical Red
  )

  private val scaleShortLabels = arrayOf("Calm", "Mild", "Mod", "High", "Max")

  /**
   * Landscape banner bitmap (960 x 300 px) showing a sleek dark card with the graduated 1..5
   * green-to-red thermometer slide bar, active mercury fill, thumb marker, and urgency pill.
   */
  fun renderNotificationPicture(
    urgencyLevel: Int,
    taskTitle: String = "",
    dueSummary: String = ""
  ): Bitmap {
    val level = UrgencyLevelMeta.clamp(urgencyLevel)
    val width = 960
    val height = 300
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val activeColor = scaleColors[level - 1]

    // Card background
    val cardRect = RectF(4f, 4f, width - 4f, height - 4f)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFF141824.toInt()
      style = Paint.Style.FILL
    }
    canvas.drawRoundRect(cardRect, 28f, 28f, bgPaint)

    // Subtle glowing accent border matching active level
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 3f
      shader = LinearGradient(
        0f, 0f, width.toFloat(), height.toFloat(),
        intArrayOf(scaleColors[0], activeColor, scaleColors[level - 1]),
        floatArrayOf(0f, 0.6f, 1f),
        Shader.TileMode.CLAMP
      )
      alpha = 170
    }
    canvas.drawRoundRect(cardRect, 28f, 28f, borderPaint)

    // Top-left subtitle label
    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFFB8C2D8.toInt()
      textSize = 24f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      letterSpacing = 0.08f
    }
    canvas.drawText("URGENCY THERMOMETER", 44f, 56f, headerPaint)

    // Top-right active level badge
    val badgeText = "LEVEL $level / 5 • ${UrgencyLevelMeta.shortLabel(level).uppercase()}"
    val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = if (level == 3) 0xFF1E1B07.toInt() else Color.WHITE
      textSize = 23f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val badgeTextWidth = badgeTextPaint.measureText(badgeText)
    val badgePadH = 22f
    val badgeRight = width - 40f
    val badgeLeft = badgeRight - badgeTextWidth - badgePadH * 2f
    val badgeRect = RectF(badgeLeft, 24f, badgeRight, 70f)
    val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = activeColor
      style = Paint.Style.FILL
    }
    canvas.drawRoundRect(badgeRect, 23f, 23f, badgeBgPaint)
    val badgeBaseline = badgeRect.centerY() - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
    canvas.drawText(badgeText, badgeLeft + badgePadH, badgeBaseline, badgeTextPaint)

    // Clean horizontal capsule slide bar geometry (no left bulb)
    val tubeCy = 148f
    val tubeStartX = 52f
    val tubeEndX = width - 52f
    val tubeHeight = 38f
    val tubeTop = tubeCy - tubeHeight / 2f
    val tubeBottom = tubeCy + tubeHeight / 2f
    val glassPad = 7f

    // Outer glass shell
    val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFF232A3E.toInt()
      style = Paint.Style.FILL
    }
    val outerTubeRect = RectF(
      tubeStartX - glassPad,
      tubeTop - glassPad,
      tubeEndX + glassPad,
      tubeBottom + glassPad
    )
    val outerRadius = (tubeHeight + glassPad * 2f) / 2f
    canvas.drawRoundRect(outerTubeRect, outerRadius, outerRadius, glassPaint)

    // Outer glass rim stroke
    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFF3A4563.toInt()
      style = Paint.Style.STROKE
      strokeWidth = 2.5f
    }
    canvas.drawRoundRect(outerTubeRect, outerRadius, outerRadius, rimPaint)

    // Inner dark cavity for unfilled scale
    val innerCavityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFF0E121D.toInt()
      style = Paint.Style.FILL
    }
    val innerTubeRect = RectF(tubeStartX, tubeTop, tubeEndX, tubeBottom)
    canvas.drawRoundRect(innerTubeRect, tubeHeight / 2f, tubeHeight / 2f, innerCavityPaint)

    // Evenly spaced step centers along the tube for levels 1..5
    val trackPad = 56f
    val scaleStartX = tubeStartX + trackPad
    val scaleEndX = tubeEndX - trackPad
    val stepSpan = (scaleEndX - scaleStartX) / 4f
    fun stepX(step: Int): Float = scaleStartX + (step - 1) * stepSpan

    val fillRightX = stepX(level)

    // Gradient mercury shader across full thermometer scale (1 green -> 5 red)
    val mercuryStartX = tubeStartX + 4f
    val mercuryGradient = LinearGradient(
      mercuryStartX,
      tubeCy,
      scaleEndX,
      tubeCy,
      scaleColors,
      floatArrayOf(0f, 0.28f, 0.52f, 0.76f, 1f),
      Shader.TileMode.CLAMP
    )

    val mercuryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = mercuryGradient
      style = Paint.Style.FILL
    }

    // Draw filled mercury bar from left tube edge to active level
    val mercuryBarRect = RectF(mercuryStartX, tubeTop + 4f, fillRightX, tubeBottom - 4f)
    canvas.drawRoundRect(mercuryBarRect, (tubeHeight - 8f) / 2f, (tubeHeight - 8f) / 2f, mercuryPaint)

    // Glass specular highlight streak on top of tube
    val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0x44FFFFFF
      style = Paint.Style.FILL
    }
    val shineRect = RectF(tubeStartX + 18f, tubeTop + 4f, tubeEndX - 18f, tubeTop + 10f)
    canvas.drawRoundRect(shineRect, 3f, 3f, shinePaint)

    // Draw tick divider notches on tube for 1..5
    val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0x88FFFFFF.toInt()
      strokeWidth = 3f
      strokeCap = Paint.Cap.ROUND
    }
    for (step in 1..5) {
      val sx = stepX(step)
      canvas.drawLine(sx, tubeTop + 5f, sx, tubeBottom - 5f, tickPaint)
    }

    // Active glowing slider thumb knob at fillRightX
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = activeColor
      alpha = 110
      style = Paint.Style.FILL
    }
    canvas.drawCircle(fillRightX, tubeCy, 32f, glowPaint)

    val outerThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      style = Paint.Style.FILL
    }
    canvas.drawCircle(fillRightX, tubeCy, 22f, outerThumbPaint)

    val innerThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = activeColor
      style = Paint.Style.FILL
    }
    canvas.drawCircle(fillRightX, tubeCy, 14f, innerThumbPaint)

    // Bottom step numbers (1..5) & labels aligned directly below each tick
    for (step in 1..5) {
      val sx = stepX(step)
      val isSelected = step == level
      val stepColor = scaleColors[step - 1]

      if (isSelected) {
        val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = stepColor
          style = Paint.Style.FILL
        }
        canvas.drawCircle(sx, 232f, 22f, pillBg)
      } else {
        val dimRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = stepColor
          alpha = 70
          style = Paint.Style.FILL
        }
        canvas.drawCircle(sx, 232f, 17f, dimRing)
      }

      val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = when {
          isSelected && step == 3 -> 0xFF1E1B07.toInt()
          isSelected -> Color.WHITE
          else -> 0xFFE2E8F0.toInt()
        }
        textSize = if (isSelected) 24f else 20f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
      val numBaseline = 232f - (numPaint.descent() + numPaint.ascent()) / 2f
      canvas.drawText("$step", sx, numBaseline, numPaint)

      val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isSelected) Color.WHITE else 0xFF94A3B8.toInt()
        textSize = if (isSelected) 20f else 18f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(
          Typeface.DEFAULT,
          if (isSelected) Typeface.BOLD else Typeface.NORMAL
        )
      }
      canvas.drawText(scaleShortLabels[step - 1], sx, 276f, lblPaint)
    }

    return bitmap
  }

  /**
   * Compact 128 x 128 px square large icon displaying a sleek capsule gauge bar and the urgency
   * number on the green-to-red spectrum, visible even when the notification is collapsed.
   */
  fun renderLargeIconBitmap(urgencyLevel: Int): Bitmap {
    val level = UrgencyLevelMeta.clamp(urgencyLevel)
    val size = 128
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val activeColor = scaleColors[level - 1]

    // Rounded square background card
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFF141824.toInt()
      style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(2f, 2f, size - 2f, size - 2f), 28f, 28f, bgPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = activeColor
      style = Paint.Style.STROKE
      strokeWidth = 5f
    }
    canvas.drawRoundRect(RectF(4f, 4f, size - 4f, size - 4f), 26f, 26f, borderPaint)

    // Sleek vertical capsule gauge on the left side (no circular bulb)
    val gaugeX = 34f
    val gaugeTop = 22f
    val gaugeBottom = 106f

    val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFF2A3248.toInt()
      style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(gaugeX - 10f, gaugeTop, gaugeX + 10f, gaugeBottom), 10f, 10f, glassPaint)

    val fillRatio = level / 5f
    val fillTop = (gaugeBottom - 4f) - ((gaugeBottom - 4f) - (gaugeTop + 4f)) * fillRatio
    val mercuryGradient = LinearGradient(
      gaugeX,
      gaugeBottom,
      gaugeX,
      gaugeTop,
      intArrayOf(scaleColors[0], activeColor),
      null,
      Shader.TileMode.CLAMP
    )
    val mercuryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = mercuryGradient
      style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(gaugeX - 6f, fillTop, gaugeX + 6f, gaugeBottom - 4f), 6f, 6f, mercuryPaint)

    // Large numeric level on right half: e.g. "4" over "/5"
    val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = activeColor
      textSize = 52f
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("$level", 86f, 70f, numPaint)

    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFFCBD5E1.toInt()
      textSize = 22f
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("/5", 86f, 100f, subPaint)

    return bitmap
  }
}
