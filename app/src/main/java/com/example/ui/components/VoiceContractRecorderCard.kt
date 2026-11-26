package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.VoiceContractManager

@Composable
fun VoiceContractRecorderCard(
  voiceContractPath: String,
  onVoiceContractChanged: (String) -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false
) {
  val context = LocalContext.current
  val recorderState by VoiceContractManager.uiState.collectAsStateWithLifecycle()
  var permissionDeniedNote by remember { mutableStateOf(false) }

  val hasRecording = remember(voiceContractPath, recorderState.isRecording) {
    VoiceContractManager.hasValidRecording(voiceContractPath)
  }
  val isPlayingThis = recorderState.isPlaying && recorderState.activePlayingPath == voiceContractPath

  val startRecAction = {
    permissionDeniedNote = false
    if (voiceContractPath.isNotBlank()) {
      VoiceContractManager.deleteContractFile(voiceContractPath)
    }
    VoiceContractManager.startRecording(context) { autoSavedPath ->
      onVoiceContractChanged(autoSavedPath)
    }
  }

  val micPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      startRecAction()
    } else {
      permissionDeniedNote = true
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      if (VoiceContractManager.uiState.value.isRecording) {
        val saved = VoiceContractManager.stopRecording()
        if (!saved.isNullOrBlank()) {
          onVoiceContractChanged(saved)
        }
      }
    }
  }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    )
  ) {
    Column(
      modifier = Modifier.padding(if (compact) 14.dp else 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.RecordVoiceOver,
            contentDescription = stringResource(R.string.cd_voice_record),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.voice_contract_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = if (hasRecording) {
              "Recorded! Your own voice plays when this task reminder fires."
            } else {
              "Record a 15s note to future-you explaining why this task matters."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (recorderState.isRecording) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
          )
          // Live hardware amplitude waveform bars
          Row(
            modifier = Modifier
              .weight(1f)
              .height(26.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            val weights = listOf(0.45f, 0.75f, 1.0f, 0.85f, 0.6f, 0.95f, 0.7f, 0.5f, 0.8f, 0.65f)
            weights.forEach { factor ->
              val barHeight by animateFloatAsState(
                targetValue = (6f + (recorderState.amplitudeLevel * factor * 20f)).coerceIn(4f, 26f),
                label = "mic_bar"
              )
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(barHeight.dp)
                  .clip(RoundedCornerShape(4.dp))
                  .background(MaterialTheme.colorScheme.error)
              )
            }
          }
          Text(
            text = "${recorderState.elapsedSeconds}s / ${recorderState.maxDurationSeconds}s",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (recorderState.isRecording) {
          Button(
            onClick = {
              val saved = VoiceContractManager.stopRecording()
              if (!saved.isNullOrBlank()) {
                onVoiceContractChanged(saved)
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("voice_contract_stop_button")
          ) {
            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Stop & Save")
          }
        } else {
          FilledTonalButton(
            onClick = {
              val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
              ) == PackageManager.PERMISSION_GRANTED
              if (granted) {
                startRecAction()
              } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              }
            },
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("voice_contract_record_button")
          ) {
            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (hasRecording) "Re-record" else "Record Voice (15s)")
          }
        }

        if (hasRecording && !recorderState.isRecording) {
          FilledTonalIconButton(
            onClick = {
              if (isPlayingThis) {
                VoiceContractManager.stopPlayback()
              } else {
                VoiceContractManager.playContractFile(context, voiceContractPath, isReminderAlarm = false)
              }
            },
            modifier = Modifier
              .size(48.dp)
              .testTag("voice_contract_play_button")
          ) {
            Icon(
              imageVector = if (isPlayingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
              contentDescription = stringResource(R.string.cd_voice_play)
            )
          }

          IconButton(
            onClick = {
              VoiceContractManager.deleteContractFile(voiceContractPath)
              onVoiceContractChanged("")
            },
            modifier = Modifier
              .size(48.dp)
              .testTag("voice_contract_delete_button")
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = stringResource(R.string.cd_voice_delete),
              tint = MaterialTheme.colorScheme.error
            )
          }
        }
      }

      if (permissionDeniedNote) {
        Text(
          text = "Microphone permission is needed to record your future-self voice note.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error
        )
      }
      recorderState.errorMessage?.let { err ->
        Text(
          text = err,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error
        )
      }
    }
  }
}
