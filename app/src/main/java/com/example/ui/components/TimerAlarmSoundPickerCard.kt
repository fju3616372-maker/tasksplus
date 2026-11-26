package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notifications.AlarmSoundOption
import com.example.notifications.TimerAlarmSoundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Interactive Alarm Sound Library & Zero-Permission Custom Audio Upload picker card
 * displayed inside Settings -> Task Timer.
 */
@Composable
fun TimerAlarmSoundPickerCard(
  selectedSoundId: String,
  selectedSoundName: String,
  onSelectSound: (AlarmSoundOption) -> Unit,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var customAlarms by remember {
    mutableStateOf(TimerAlarmSoundManager.getCustomAlarms(context))
  }
  val previewingSoundId by TimerAlarmSoundManager.previewingSoundIdFlow.collectAsStateWithLifecycle()

  DisposableEffect(Unit) {
    onDispose {
      TimerAlarmSoundManager.stopPreview()
    }
  }

  // Zero-permission Storage Access Framework picker for audio files (Play Policy compliant)
  val audioPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      val result = withContext(Dispatchers.IO) {
        TimerAlarmSoundManager.importCustomAlarmFromUri(context, uri)
      }
      result.onSuccess { uploaded ->
        customAlarms = TimerAlarmSoundManager.getCustomAlarms(context)
        onSelectSound(uploaded)
        onShowMessage("Uploaded & selected alarm: ${uploaded.name}")
      }.onFailure { err ->
        onShowMessage(err.localizedMessage ?: "Could not upload audio file.")
      }
    }
  }

  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ),
    modifier = modifier
      .fillMaxWidth()
      .testTag("timer_alarm_library_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = Icons.Default.NotificationsActive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
          )
          Text(
            text = "Alarm Sound",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(50)
        ) {
          Text(
            text = selectedSoundName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
          )
        }
      }

      Text(
        text = "Choose from the built-in alarm library or upload your own MP3, WAV, M4A, or OGG audio file.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      FilledTonalButton(
        onClick = {
          audioPickerLauncher.launch(arrayOf("audio/*"))
        },
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .testTag("timer_upload_alarm_button")
      ) {
        Icon(
          imageVector = Icons.Default.UploadFile,
          contentDescription = null,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Upload Custom Alarm Audio",
          fontWeight = FontWeight.SemiBold
        )
      }

      if (customAlarms.isNotEmpty()) {
        Text(
          text = "Your Uploaded Alarms",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary
        )
        customAlarms.forEach { sound ->
          AlarmOptionRow(
            sound = sound,
            isSelected = sound.id == selectedSoundId,
            isPreviewing = previewingSoundId == sound.id,
            onSelect = { onSelectSound(sound) },
            onTogglePreview = { TimerAlarmSoundManager.togglePreviewSound(context, sound) },
            onDeleteCustom = {
              TimerAlarmSoundManager.deleteCustomAlarm(context, sound.id)
              customAlarms = TimerAlarmSoundManager.getCustomAlarms(context)
              if (selectedSoundId == sound.id) {
                onSelectSound(TimerAlarmSoundManager.BUILT_IN_LIBRARY.first())
              }
              onShowMessage("Removed custom alarm \"${sound.name}\"")
            }
          )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
      }

      Text(
        text = "In-App Alarm Library (${TimerAlarmSoundManager.BUILT_IN_LIBRARY.size})",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      TimerAlarmSoundManager.BUILT_IN_LIBRARY.forEach { sound ->
        AlarmOptionRow(
          sound = sound,
          isSelected = sound.id == selectedSoundId,
          isPreviewing = previewingSoundId == sound.id,
          onSelect = { onSelectSound(sound) },
          onTogglePreview = { TimerAlarmSoundManager.togglePreviewSound(context, sound) },
          onDeleteCustom = null
        )
      }
    }
  }
}

@Composable
private fun AlarmOptionRow(
  sound: AlarmSoundOption,
  isSelected: Boolean,
  isPreviewing: Boolean,
  onSelect: () -> Unit,
  onTogglePreview: () -> Unit,
  onDeleteCustom: (() -> Unit)?
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = if (isSelected) {
      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
      MaterialTheme.colorScheme.surface
    },
    border = BorderStroke(
      width = if (isSelected) 1.5.dp else 1.dp,
      color = if (isSelected) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
      }
    ),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onSelect)
      .testTag("alarm_sound_option_${sound.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(
        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
        contentDescription = if (isSelected) "Selected alarm" else "Select alarm",
        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp)
      )

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = if (sound.isCustom) Icons.Default.AudioFile else Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = sound.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = sound.category,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
            )
          }
        }
        Text(
          text = sound.subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      FilledTonalIconButton(
        onClick = onTogglePreview,
        modifier = Modifier
          .size(48.dp)
          .testTag("alarm_preview_${sound.id}")
      ) {
        Icon(
          imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
          contentDescription = if (isPreviewing) "Stop preview for ${sound.name}" else "Preview ${sound.name}"
        )
      }

      if (onDeleteCustom != null) {
        IconButton(
          onClick = onDeleteCustom,
          modifier = Modifier
            .size(48.dp)
            .testTag("alarm_delete_${sound.id}")
        ) {
          Icon(
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = "Delete custom alarm ${sound.name}",
            tint = MaterialTheme.colorScheme.error
          )
        }
      }
    }
  }
}
