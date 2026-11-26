package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Accessible 48dp task completion control featuring a subtle radial ripple pulse,
 * spring-animated filled circle, and progressive stroke-drawn checkmark.
 */
@Composable
fun TaskCompletionCheckButton(
  isChecked: Boolean,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  accentColor: Color = MaterialTheme.colorScheme.primary,
  checkmarkColor: Color = MaterialTheme.colorScheme.onPrimary
) {
  val interactionSource = remember { MutableInteractionSource() }

  val fillProgress = remember { Animatable(if (isChecked) 1f else 0f) }
  val checkProgress = remember { Animatable(if (isChecked) 1f else 0f) }
  val rippleProgress = remember { Animatable(0f) }
  val circleScale = remember { Animatable(1f) }

  LaunchedEffect(isChecked) {
    if (isChecked) {
      rippleProgress.snapTo(0f)
      checkProgress.snapTo(0f)

      launch {
        rippleProgress.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
      }
      launch {
        fillProgress.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
        )
      }
      launch {
        circleScale.animateTo(
          targetValue = 1.14f,
          animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
        )
        circleScale.animateTo(
          targetValue = 1f,
          animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
          )
        )
      }
      launch {
        checkProgress.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 220, delayMillis = 55, easing = FastOutSlowInEasing)
        )
      }
    } else {
      fillProgress.snapTo(0f)
      checkProgress.snapTo(0f)
      rippleProgress.snapTo(0f)
      circleScale.snapTo(1f)
    }
  }

  val fullCheckPath = remember { Path() }
  val partialCheckPath = remember { Path() }
  val pathMeasure = remember { PathMeasure() }

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .size(48.dp)
      .semantics {
        this.contentDescription = contentDescription
        this.stateDescription = if (isChecked) "Completed" else "Not completed"
      }
      .clickable(
        interactionSource = interactionSource,
        indication = ripple(
          bounded = false,
          radius = 24.dp,
          color = accentColor
        ),
        role = Role.Checkbox,
        enabled = !isChecked,
        onClick = onClick
      )
  ) {
    Canvas(modifier = Modifier.size(42.dp)) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val baseRadius = 12.dp.toPx()
      val strokeWidth = 2.dp.toPx()

      // 1. Subtle expanding radial ripple ring on completion
      val rp = rippleProgress.value
      if (rp > 0f && rp < 1f) {
        val rippleRadius = baseRadius * (0.95f + 0.72f * rp)
        val rippleAlpha = (1f - rp) * 0.36f
        drawCircle(
          color = accentColor.copy(alpha = rippleAlpha),
          radius = rippleRadius,
          center = center,
          style = Stroke(width = (2.6.dp.toPx() * (1f - 0.55f * rp)).coerceAtLeast(1f))
        )
        drawCircle(
          color = accentColor.copy(alpha = rippleAlpha * 0.28f),
          radius = rippleRadius,
          center = center
        )
      }

      // 2. Circle outline + animated fill with gentle spring bounce
      scale(scale = circleScale.value, pivot = center) {
        val fill = fillProgress.value
        if (fill > 0f) {
          drawCircle(
            color = accentColor.copy(alpha = fill),
            radius = baseRadius * (0.72f + 0.28f * fill),
            center = center
          )
        }
        drawCircle(
          color = accentColor,
          radius = baseRadius,
          center = center,
          style = Stroke(width = strokeWidth)
        )

        // 3. Progressive stroke-drawn checkmark
        val cp = checkProgress.value
        if (cp > 0f) {
          fullCheckPath.reset()
          val startX = center.x - baseRadius * 0.42f
          val startY = center.y + baseRadius * 0.02f
          val midX = center.x - baseRadius * 0.10f
          val midY = center.y + baseRadius * 0.34f
          val endX = center.x + baseRadius * 0.45f
          val endY = center.y - baseRadius * 0.28f

          fullCheckPath.moveTo(startX, startY)
          fullCheckPath.lineTo(midX, midY)
          fullCheckPath.lineTo(endX, endY)

          pathMeasure.setPath(fullCheckPath, false)
          partialCheckPath.reset()
          pathMeasure.getSegment(
            startDistance = 0f,
            stopDistance = pathMeasure.length * cp,
            destination = partialCheckPath,
            startWithMoveTo = true
          )

          drawPath(
            path = partialCheckPath,
            color = checkmarkColor,
            style = Stroke(
              width = 2.3.dp.toPx(),
              cap = StrokeCap.Round,
              join = StrokeJoin.Round
            )
          )
        }
      }
    }
  }
}
