package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.GoogleTtsState
import com.example.audio.PcmRecordingCapture
import com.example.audio.PcmVoiceRecorder
import com.example.audio.VoiceContractManager
import com.example.audio.VoiceLockStrictness
import com.example.audio.VoiceprintStore
import com.example.viewmodel.VoiceAssistantUiState
import com.example.viewmodel.VoiceCommandPhase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceCommandSheet(
  voiceState: VoiceAssistantUiState,
  ttsState: GoogleTtsState,
  onRecordEnrollmentStep: (stepIndex: Int, capture: PcmRecordingCapture) -> Unit,
  onBeginReEnrollment: () -> Unit,
  onCancelReEnrollment: () -> Unit,
  onDeleteVoiceprint: () -> Unit,
  onChangeStrictness: (VoiceLockStrictness) -> Unit,
  onProcessVoiceCommand: (capture: PcmRecordingCapture, verifyOnlyMode: Boolean) -> Unit,
  onSpeakText: (String) -> Unit,
  onToggleTtsMute: (Boolean) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val recorder = remember(context) { PcmVoiceRecorder(context) }
  val store = remember(context) { VoiceprintStore(context) }
  val recorderState by recorder.state.collectAsStateWithLifecycle()

  var hasMicPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var activeEnrollRecordingStep by remember { mutableIntStateOf(-1) }
  var isCommandRecording by remember { mutableStateOf(false) }
  var verifyOnlyMode by remember { mutableStateOf(false) }
  var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }

  DisposableEffect(recorder) {
    onDispose {
      recorder.cancelRecording()
      VoiceContractManager.stopPlayback()
    }
  }

  val micPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasMicPermission = granted
    if (granted) {
      pendingPermissionAction?.invoke()
    } else {
      onSpeakText("Microphone permission is needed to verify your voice and listen to task commands.")
    }
    pendingPermissionAction = null
  }

  fun runWithMicPermission(action: () -> Unit) {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
    hasMicPermission = granted
    if (granted) {
      action()
    } else {
      pendingPermissionAction = action
      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  val showEnrollmentView = voiceState.enrolledProfile == null || voiceState.isReEnrolling

  ModalBottomSheet(
    onDismissRequest = {
      recorder.cancelRecording()
      onDismiss()
    },
    sheetState = sheetState
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Header + Google TTS live status bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(
                if (voiceState.enrolledProfile != null) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.tertiaryContainer
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (voiceState.enrolledProfile != null) Icons.Default.VerifiedUser else Icons.Default.RecordVoiceOver,
              contentDescription = null,
              tint = if (voiceState.enrolledProfile != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
          }
          Column {
            Text(
              text = stringResource(R.string.voice_command_title),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = if (ttsState.isSpeaking) "Google TTS speaking…" else ttsState.engineLabel,
              style = MaterialTheme.typography.labelMedium,
              color = if (ttsState.isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        IconButton(
          onClick = { onToggleTtsMute(!ttsState.isMuted) },
          modifier = Modifier
            .size(48.dp)
            .testTag("voice_tts_mute_button")
        ) {
          Icon(
            imageVector = if (ttsState.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            contentDescription = stringResource(R.string.cd_voice_tts_toggle),
            tint = if (ttsState.isMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
          )
        }
      }

      if (showEnrollmentView) {
        // ==================== VOICE ID 3-PHRASE ENROLLMENT STUDIO ====================
        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = "Create Your Private Voice ID",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Record 3 quick phrases in your normal speaking voice. Tasks+ builds an acoustic vocal profile (formants + pitch) and compares every future voice command against your enrolled recording so only YOU can add, complete, or remove tasks.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val capturedCount = voiceState.enrollmentSteps.count { it.isCaptured }
            LinearProgressIndicator(
              progress = { capturedCount / voiceState.enrollmentSteps.size.toFloat().coerceAtLeast(1f) },
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
            )
            Text(
              text = "$capturedCount of ${voiceState.enrollmentSteps.size} calibration recordings captured",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold
            )
          }
        }

        if (recorderState.isRecording && activeEnrollRecordingStep >= 0) {
          LiveVoiceWaveformStrip(
            bars = recorderState.recentBars,
            elapsedSeconds = recorderState.elapsedSeconds,
            maxSeconds = recorderState.maxDurationSeconds
          )
        }

        voiceState.enrollmentSteps.forEach { step ->
          val isThisStepRecording = recorderState.isRecording && activeEnrollRecordingStep == step.stepIndex
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (step.isCaptured) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
              } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
              }
            ),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = if (step.isCaptured) Icons.Default.CheckCircle else Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = if (step.isCaptured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                  )
                  Text(
                    text = "Phrase ${step.stepIndex + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                  )
                  if (step.isCaptured && step.pitchHz > 60.0) {
                    Surface(
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                      Text(
                        text = "~${step.pitchHz.roundToInt()} Hz",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                      )
                    }
                  }
                }
                TextButton(
                  onClick = { onSpeakText(step.promptPhrase) }
                ) {
                  Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Hear")
                }
              }

              Text(
                text = "\"${step.promptPhrase}\"",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = {
                    if (isThisStepRecording) {
                      activeEnrollRecordingStep = -1
                      val cap = recorder.stopAndCapture()
                      if (cap != null) {
                        onRecordEnrollmentStep(step.stepIndex, cap)
                      }
                    } else {
                      runWithMicPermission {
                        VoiceContractManager.stopPlayback()
                        activeEnrollRecordingStep = step.stepIndex
                        recorder.startRecording(
                          outputFile = store.enrollmentStepFile(step.stepIndex),
                          maxSeconds = 7,
                          onAutoCompleted = { autoCap ->
                            activeEnrollRecordingStep = -1
                            onRecordEnrollmentStep(step.stepIndex, autoCap)
                          }
                        )
                      }
                    }
                  },
                  enabled = !recorderState.isRecording || isThisStepRecording,
                  colors = if (isThisStepRecording) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                  } else {
                    ButtonDefaults.buttonColors()
                  },
                  modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("voice_enroll_record_step_${step.stepIndex}")
                ) {
                  Icon(
                    imageVector = if (isThisStepRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(
                    when {
                      isThisStepRecording -> "Stop (${recorderState.elapsedSeconds}s)"
                      step.isCaptured -> "Re-record"
                      else -> "Record Phrase"
                    }
                  )
                }

                if (step.isCaptured && step.wavPath.isNotBlank()) {
                  FilledTonalButton(
                    onClick = { VoiceContractManager.playContractFile(context, step.wavPath) },
                    modifier = Modifier.height(48.dp)
                  ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play recorded phrase")
                  }
                }
              }
            }
          }
        }

        if (voiceState.isReEnrolling && voiceState.enrolledProfile != null) {
          OutlinedButton(
            onClick = {
              recorder.cancelRecording()
              activeEnrollRecordingStep = -1
              onCancelReEnrollment()
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
          ) {
            Text("Cancel Re-enrollment")
          }
        }
      } else {
        // ==================== VOICE ID ACTIVE COMMAND CONSOLE ====================
        val profile = voiceState.enrolledProfile!!
        val enrolledDate = remember(profile.enrolledAtMillis) {
          SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()).format(Date(profile.enrolledAtMillis))
        }

        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Voice Lock Active • ${profile.ownerName}",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                val pitchStr = if (profile.voiceprint.pitchHz > 60.0) {
                  " • Baseline ~${profile.voiceprint.pitchHz.roundToInt()} Hz"
                } else ""
                Text(
                  text = "Enrolled $enrolledDate$pitchStr",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Row {
                IconButton(
                  onClick = onBeginReEnrollment,
                  modifier = Modifier
                    .size(44.dp)
                    .testTag("voice_reenroll_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Re-enroll Voice ID",
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
                IconButton(
                  onClick = onDeleteVoiceprint,
                  modifier = Modifier
                    .size(44.dp)
                    .testTag("voice_delete_profile_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_voice_delete_profile),
                    tint = MaterialTheme.colorScheme.error
                  )
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = "Lock Strictness:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              VoiceLockStrictness.entries.forEach { level ->
                FilterChip(
                  selected = profile.strictness == level,
                  onClick = { onChangeStrictness(level) },
                  label = { Text(level.label) },
                  modifier = Modifier.testTag("voice_strictness_${level.name.lowercase()}")
                )
              }
            }
          }
        }

        // Mode switcher: Task Commands vs Speaker Lock Test
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = !verifyOnlyMode,
            onClick = { verifyOnlyMode = false },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            modifier = Modifier.testTag("voice_mode_commands")
          ) {
            Text("Task Commands")
          }
          SegmentedButton(
            selected = verifyOnlyMode,
            onClick = { verifyOnlyMode = true },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            modifier = Modifier.testTag("voice_mode_test_lock")
          ) {
            Text("Test Voice Lock")
          }
        }

        // Expressive Interactive Mic Orb
        val pulseTransition = rememberInfiniteTransition(label = "mic_orb_pulse")
        val pulseScale by pulseTransition.animateFloat(
          initialValue = 1.0f,
          targetValue = if (isCommandRecording) 1.16f else 1.0f,
          animationSpec = infiniteRepeatable(
            animation = tween(620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
          ),
          label = "mic_pulse_scale"
        )

        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 4.dp)
          ) {
            if (isCommandRecording) {
              Box(
                modifier = Modifier
                  .size(104.dp)
                  .scale(pulseScale)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
              )
            }
            Box(
              modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                  when {
                    voiceState.phase == VoiceCommandPhase.VERIFYING -> MaterialTheme.colorScheme.secondaryContainer
                    isCommandRecording -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                  }
                )
                .clickable(enabled = voiceState.phase != VoiceCommandPhase.VERIFYING) {
                  if (isCommandRecording) {
                    isCommandRecording = false
                    val cap = recorder.stopAndCapture()
                    if (cap != null) {
                      onProcessVoiceCommand(cap, verifyOnlyMode)
                    }
                  } else {
                    runWithMicPermission {
                      VoiceContractManager.stopPlayback()
                      isCommandRecording = true
                      recorder.startRecording(
                        outputFile = store.commandTempFile(),
                        maxSeconds = 9,
                        onAutoCompleted = { autoCap ->
                          isCommandRecording = false
                          onProcessVoiceCommand(autoCap, verifyOnlyMode)
                        }
                      )
                    }
                  }
                }
                .testTag("voice_command_mic_orb"),
              contentAlignment = Alignment.Center
            ) {
              when {
                voiceState.phase == VoiceCommandPhase.VERIFYING -> {
                  CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                  )
                }
                isCommandRecording -> {
                  Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = stringResource(R.string.cd_voice_mic_action),
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(34.dp)
                  )
                }
                else -> {
                  Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringResource(R.string.cd_voice_mic_action),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp)
                  )
                }
              }
            }
          }

          Text(
            text = when {
              voiceState.phase == VoiceCommandPhase.VERIFYING ->
                "Verifying your Voice ID & parsing command…"
              isCommandRecording ->
                "Listening (${recorderState.elapsedSeconds}s) — tap stop when finished"
              verifyOnlyMode ->
                "Tap mic to test speaker identity (hand to someone else to verify blocking!)"
              else ->
                "Tap mic and say a task command"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
          )

          AnimatedVisibility(visible = isCommandRecording) {
            LiveVoiceWaveformStrip(
              bars = recorderState.recentBars,
              elapsedSeconds = recorderState.elapsedSeconds,
              maxSeconds = recorderState.maxDurationSeconds
            )
          }
        }

        // Verification & Command Outcome Card
        if (voiceState.phase == VoiceCommandPhase.EXECUTED || voiceState.phase == VoiceCommandPhase.REJECTED_SPEAKER) {
          val isRejected = voiceState.phase == VoiceCommandPhase.REJECTED_SPEAKER
          val ver = voiceState.lastVerification
          Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (isRejected) {
                MaterialTheme.colorScheme.errorContainer
              } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
              }
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag(if (isRejected) "voice_result_rejected_card" else "voice_result_verified_card")
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Icon(
                  imageVector = if (isRejected) Icons.Default.GppBad else Icons.Default.VerifiedUser,
                  contentDescription = null,
                  tint = if (isRejected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(26.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = if (isRejected) {
                      "Voice Not Recognized • Blocked"
                    } else {
                      "Owner Verified • ${ver?.fusedScorePercent ?: 0}% Match"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isRejected) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                  )
                  if (ver != null) {
                    Text(
                      text = "Acoustic DSP: ${ver.acousticScorePercent}%  •  Gemini Voice Match: ${ver.aiConfidencePercent}%",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (isRejected) {
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                      } else {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                      }
                    )
                  }
                }
                if (voiceState.lastSpokenReply.isNotBlank()) {
                  IconButton(
                    onClick = { onSpeakText(voiceState.lastSpokenReply) },
                    modifier = Modifier.size(40.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.VolumeUp,
                      contentDescription = stringResource(R.string.cd_voice_tts_replay)
                    )
                  }
                }
              }

              if (voiceState.lastTranscript.isNotBlank()) {
                Text(
                  text = "Heard: \"${voiceState.lastTranscript}\"",
                  style = MaterialTheme.typography.bodySmall,
                  fontStyle = FontStyle.Italic
                )
              }

              if (voiceState.lastActionSummary.isNotBlank()) {
                Text(
                  text = voiceState.lastActionSummary,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold
                )
              }

              if (ver != null && ver.reason.isNotBlank()) {
                Text(
                  text = ver.reason,
                  style = MaterialTheme.typography.bodySmall
                )
              }
            }
          }
        }

        // Example Voice Commands Strip
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Try saying (verified owner only):",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf(
              "\"Add task Buy groceries\"",
              "\"Mark Buy groceries as completed\"",
              "\"Remove task Buy groceries\"",
              "\"What are my tasks?\""
            ).forEach { example ->
              AssistChip(
                onClick = { onSpeakText("Try saying: ${example.replace("\"", "")}") },
                label = { Text(example, style = MaterialTheme.typography.labelSmall) }
              )
            }
          }
        }
      }

      // Error banner (from recorder or verification)
      val errText = voiceState.errorMessage ?: recorderState.errorMessage
      if (!errText.isNullOrBlank()) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = errText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun LiveVoiceWaveformStrip(
  bars: List<Float>,
  elapsedSeconds: Int,
  maxSeconds: Int
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier
          .weight(1f)
          .height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        bars.forEach { level ->
          val barHeight = (6f + level.coerceIn(0.05f, 1f) * 28f).dp
          Box(
            modifier = Modifier
              .weight(1f)
              .height(barHeight)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary)
          )
        }
      }
      Spacer(Modifier.width(12.dp))
      Text(
        text = "${elapsedSeconds}s / ${maxSeconds}s",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error
      )
    }
  }
}
