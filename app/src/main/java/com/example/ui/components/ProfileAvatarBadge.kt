package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileAvatarBadge(
  fallbackInitial: String,
  modifier: Modifier = Modifier,
  size: Dp = 44.dp,
  showBorder: Boolean = true
) {
  val borderModifier = if (showBorder) {
    Modifier.border(
      width = 1.5.dp,
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
      shape = CircleShape
    )
  } else {
    Modifier
  }

  val cleanInitial = fallbackInitial.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.primaryContainer)
      .then(borderModifier)
  ) {
    Text(
      text = cleanInitial,
      fontSize = (size.value * 0.42f).coerceIn(11f, 28f).sp,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onPrimaryContainer
    )
  }
}
