package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.UrgencyLevelMeta
import kotlin.math.roundToInt

private val thermometerSpectrumColors = listOf(
  Color(0xFF22A049), // 1 - Green
  Color(0xFF7CB342), // 2 - Lime Green
  Color(0xFFF9A825), // 3 - Warm Amber
  Color(0xFFEF6C00), // 4 - Orange
  Color(0xFFD32F2F)  // 5 - Red
)

/**
 * Interactive Thermometer-style urgency slide bar ranging from 1 (Green) to 5 (Red).
 * Replaces discrete priority chips with a continuous, tactile thermometer tube + bulb +
 * clickable graduation steps 1..5.
 */
@Composable
fun UrgencyThermometerSlider(
  urgencyLevel: Int,
  onUrgencyLevelChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
  title: String = "Urgency Thermometer"
) {
  val clampedLevel = UrgencyLevelMeta.clamp(urgencyLevel)
  val haptics = LocalHapticFeedback.current

  val targetColor = Color(UrgencyLevelMeta.colorHex(clampedLevel))
  val animatedActiveColor by animateColorAsState(
    targetValue = targetColor,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "thermometer_active_color"
  )
  val animatedProgress by animateFloatAsState(
    targetValue = (clampedLevel - 1) / 4f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioNoBouncy,
      stiffness = Spring.StiffnessMediumLow
    ),
    label = "thermometer_mercury_progress"
  )

  val density = LocalDensity.current
  val trackEndpointMarginPx = with(density) { 18.dp.toPx() }
  var canvasWidthPx by remember { mutableFloatStateOf(0f) }

  fun mapOffsetToLevel(xPx: Float, widthPx: Float): Int {
    if (widthPx <= 0f) return clampedLevel
    val startMargin = trackEndpointMarginPx.coerceAtMost(widthPx * 0.25f)
    val endMargin = (widthPx - startMargin).coerceAtLeast(startMargin + 1f)
    val usableSpan = (endMargin - startMargin).coerceAtLeast(1f)
    val fraction = ((xPx - startMargin) / usableSpan).coerceIn(0f, 1f)
    return (1 + (fraction * 4f).roundToInt()).coerceIn(1, 5)
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    modifier = modifier
      .fillMaxWidth()
      .testTag("urgency_thermometer_slider")
      .semantics {
        contentDescription = "Urgency thermometer set to level $clampedLevel of 5, ${UrgencyLevelMeta.shortLabel(clampedLevel)}"
      }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Header row with title and dynamic thermometer status pill
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Thermostat,
            contentDescription = null,
            tint = animatedActiveColor,
            modifier = Modifier.size(20.dp)
          )
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Surface(
          shape = RoundedCornerShape(999.dp),
          color = animatedActiveColor.copy(alpha = 0.16f)
        ) {
          Text(
            text = "${clampedLevel}/5 • ${UrgencyLevelMeta.shortLabel(clampedLevel)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = animatedActiveColor,
            modifier = Modifier
              .padding(horizontal = 10.dp, vertical = 4.dp)
              .testTag("urgency_thermometer_badge_text")
          )
        }
      }

      // Interactive Thermometer Capsule Slide Track (1 Green -> 5 Red)
      val trackShellColor = MaterialTheme.colorScheme.surfaceContainerHighest
      val trackInnerColor = MaterialTheme.colorScheme.surfaceContainerLow
      val tickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
      val rimColor = MaterialTheme.colorScheme.outlineVariant

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .onSizeChanged { size ->
            canvasWidthPx = size.width.toFloat()
          }
          .pointerInput(Unit) {
            detectTapGestures { offset ->
              val nextLevel = mapOffsetToLevel(offset.x, canvasWidthPx)
              if (nextLevel != clampedLevel) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              }
              onUrgencyLevelChange(nextLevel)
            }
          }
          .pointerInput(Unit) {
            detectHorizontalDragGestures { change, _ ->
              change.consume()
              val nextLevel = mapOffsetToLevel(change.position.x, canvasWidthPx)
              if (nextLevel != clampedLevel) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onUrgencyLevelChange(nextLevel)
              }
            }
          }
      ) {
        Canvas(
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
        ) {
          val w = size.width
          val cy = size.height / 2f

          val glassPad = 4.dp.toPx()
          val tubeHeight = 20.dp.toPx()
          val tubeTop = cy - tubeHeight / 2f
          val tubeStartX = 8.dp.toPx()
          val tubeEndX = w - 8.dp.toPx()
          val tubeWidth = (tubeEndX - tubeStartX).coerceAtLeast(1f)

          val barInset = 2.5.dp.toPx()
          val mercuryStartX = tubeStartX + barInset
          val mercuryMaxEndX = tubeEndX - barInset
          val mercuryBarH = (tubeHeight - barInset * 2f).coerceAtLeast(1f)
          val mercuryHalfH = mercuryBarH / 2f

          val scaleStartX = mercuryStartX + mercuryHalfH
          val scaleEndX = (mercuryMaxEndX - mercuryHalfH).coerceAtLeast(scaleStartX + 1f)
          val scaleSpan = (scaleEndX - scaleStartX).coerceAtLeast(1f)
          val activeThumbX = scaleStartX + scaleSpan * animatedProgress

          val outerCorner = (tubeHeight + glassPad * 2f) / 2f

          // Outer glass capsule casing
          drawRoundRect(
            color = trackShellColor,
            topLeft = Offset(tubeStartX - glassPad, tubeTop - glassPad),
            size = Size(tubeWidth + glassPad * 2f, tubeHeight + glassPad * 2f),
            cornerRadius = CornerRadius(outerCorner, outerCorner)
          )

          // Subtle outline ring around outer glass capsule
          drawRoundRect(
            color = rimColor.copy(alpha = 0.45f),
            topLeft = Offset(tubeStartX - glassPad, tubeTop - glassPad),
            size = Size(tubeWidth + glassPad * 2f, tubeHeight + glassPad * 2f),
            cornerRadius = CornerRadius(outerCorner, outerCorner),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
          )

          // Unfilled dark glass stem background
          drawRoundRect(
            color = trackInnerColor,
            topLeft = Offset(tubeStartX, tubeTop),
            size = Size(tubeWidth, tubeHeight),
            cornerRadius = CornerRadius(tubeHeight / 2f, tubeHeight / 2f)
          )

          // Gradient mercury fill across green (1) -> red (5), flush to the right end at 5/5
          val mercuryEndX = (activeThumbX + mercuryHalfH).coerceIn(mercuryStartX + mercuryBarH, mercuryMaxEndX)
          val mercuryBrush = Brush.horizontalGradient(
            colors = thermometerSpectrumColors,
            startX = mercuryStartX,
            endX = mercuryMaxEndX
          )
          drawRoundRect(
            brush = mercuryBrush,
            topLeft = Offset(mercuryStartX, tubeTop + barInset),
            size = Size((mercuryEndX - mercuryStartX).coerceAtLeast(mercuryBarH), mercuryBarH),
            cornerRadius = CornerRadius(mercuryHalfH, mercuryHalfH)
          )

          // Interior tick graduations (2..4) along the straight track span
          for (step in 2..4) {
            val sx = scaleStartX + scaleSpan * ((step - 1) / 4f)
            drawLine(
              color = if (step <= clampedLevel) Color.White.copy(alpha = 0.72f) else tickColor,
              start = Offset(sx, tubeTop + 3.5.dp.toPx()),
              end = Offset(sx, tubeTop + tubeHeight - 3.5.dp.toPx()),
              strokeWidth = 2.dp.toPx(),
              cap = StrokeCap.Round
            )
          }

          // Glowing draggable thumb indicator
          drawCircle(
            color = animatedActiveColor.copy(alpha = 0.28f),
            radius = 18.dp.toPx(),
            center = Offset(activeThumbX, cy)
          )
          drawCircle(
            color = Color.White,
            radius = 12.dp.toPx(),
            center = Offset(activeThumbX, cy)
          )
          drawCircle(
            color = animatedActiveColor,
            radius = 7.5.dp.toPx(),
            center = Offset(activeThumbX, cy)
          )
        }
      }

      // Clickable numbered scale buttons 1..5 (Green -> Red) aligned cleanly
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        for (step in 1..5) {
          val isSelected = step == clampedLevel
          val stepColor = Color(UrgencyLevelMeta.colorHex(step))
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
              .weight(1f)
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(14.dp))
              .background(
                if (isSelected) stepColor.copy(alpha = 0.16f) else Color.Transparent
              )
              .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) stepColor else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
              )
              .clickable {
                if (step != clampedLevel) {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onUrgencyLevelChange(step)
              }
              .padding(vertical = 6.dp)
              .testTag("urgency_level_step_$step")
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isSelected) stepColor else stepColor.copy(alpha = 0.22f))
            ) {
              Text(
                text = "$step",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else stepColor
              )
            }
            Text(
              text = UrgencyLevelMeta.shortLabel(step),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) stepColor else MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1
            )
          }
        }
      }
    }
  }
}

/**
 * Compact inline thermometer pill for task cards showing level 1 (green) to 5 (red)
 * alongside a mini horizontal fill bar.
 */
@Composable
fun ThermometerUrgencyBadge(
  urgencyLevel: Int,
  modifier: Modifier = Modifier
) {
  val level = UrgencyLevelMeta.clamp(urgencyLevel)
  val activeColor = Color(UrgencyLevelMeta.colorHex(level))
  val containerColor = Color(UrgencyLevelMeta.containerColorHex(level))
  val contentColor = Color(UrgencyLevelMeta.onContainerColorHex(level))

  Surface(
    shape = RoundedCornerShape(10.dp),
    color = containerColor,
    modifier = modifier.testTag("task_thermometer_badge_$level")
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Thermostat,
        contentDescription = null,
        tint = activeColor,
        modifier = Modifier.size(14.dp)
      )
      // Mini 5-segment thermometer bar
      Canvas(
        modifier = Modifier
          .width(28.dp)
          .height(6.dp)
      ) {
        val segCount = 5
        val gap = 1.5.dp.toPx()
        val segW = (size.width - gap * (segCount - 1)) / segCount
        for (i in 1..segCount) {
          val x = (i - 1) * (segW + gap)
          val segColor = if (i <= level) {
            thermometerSpectrumColors[i - 1]
          } else {
            contentColor.copy(alpha = 0.16f)
          }
          drawRoundRect(
            color = segColor,
            topLeft = Offset(x, 0f),
            size = Size(segW, size.height),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
          )
        }
      }
      Text(
        text = UrgencyLevelMeta.fullLabel(level),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = contentColor
      )
    }
  }
}
