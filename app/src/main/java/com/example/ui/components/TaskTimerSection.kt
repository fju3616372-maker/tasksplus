package com.example.ui.components

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.R
import com.example.data.model.TaskEntity
import com.example.notifications.TaskNotificationHelper
import com.example.notifications.TaskReminderManager
import com.example.notifications.TaskTimerManager
import com.example.notifications.TaskTimerState
import com.example.notifications.TaskTimerStatus
import com.example.sensors.FocusAuraSensorState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val CompletedAccentColor = Color(0xFF2E9E5B)
private val WarningAccentColor = Color(0xFFE6911A)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskTimerSection(
  timerState: TaskTimerState,
  activeTasks: List<TaskEntity>,
  onStartTimer: (Long, List<TaskEntity>) -> Unit,
  onPauseTimer: () -> Unit,
  onResumeTimer: () -> Unit,
  onAddFiveMinutes: () -> Unit,
  onResetTimer: () -> Unit,
  onCheckExpired: () -> Unit,
  onCompleteTask: (TaskEntity) -> Unit,
  onUpdateSettings: (Int, Boolean, Boolean) -> Unit,
  focusAuraState: FocusAuraSensorState? = null,
  onToggleFocusAura: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

  LaunchedEffect(timerState.status, timerState.endAtEpochMillis) {
    nowMillis = System.currentTimeMillis()
    while (timerState.status == TaskTimerStatus.RUNNING) {
      delay(500L)
      nowMillis = System.currentTimeMillis()
      if (timerState.endAtEpochMillis > 0L && nowMillis >= timerState.endAtEpochMillis) {
        onCheckExpired()
        break
      }
    }
  }

  var customMinutesText by rememberSaveable(timerState.defaultDurationMinutes) {
    mutableStateOf(timerState.defaultDurationMinutes.toString())
  }
  var customSecondsText by rememberSaveable { mutableStateOf("0") }
  var selectedTaskIdsCsv by rememberSaveable { mutableStateOf("") }

  val selectedTaskIdSet = remember(selectedTaskIdsCsv, activeTasks) {
    val activeIdSet = activeTasks.map { it.id }.toSet()
    selectedTaskIdsCsv.split("|")
      .map { it.trim() }
      .filter { it.isNotEmpty() && it in activeIdSet }
      .toSet()
  }

  val isEditingConfig = timerState.status == TaskTimerStatus.IDLE ||
    timerState.status == TaskTimerStatus.COMPLETED ||
    timerState.status == TaskTimerStatus.EXPIRED

  val configuredDurationMillis = remember(customMinutesText, customSecondsText) {
    val mins = (customMinutesText.toLongOrNull() ?: 0L).coerceIn(0L, 720L)
    val secs = (customSecondsText.toLongOrNull() ?: 0L).coerceIn(0L, 59L)
    (mins * 60_000L + secs * 1_000L).coerceIn(
      TaskTimerManager.MIN_DURATION_MILLIS,
      TaskTimerManager.MAX_DURATION_MILLIS
    )
  }

  val displayedRemainingMs = if (timerState.status == TaskTimerStatus.IDLE) {
    configuredDurationMillis
  } else {
    timerState.remainingMillisAt(nowMillis)
  }
  val progressFraction = if (timerState.status == TaskTimerStatus.IDLE) {
    1f
  } else {
    timerState.progressFractionAt(nowMillis)
  }

  val ringColor = when {
    timerState.status == TaskTimerStatus.COMPLETED -> CompletedAccentColor
    timerState.status == TaskTimerStatus.EXPIRED -> MaterialTheme.colorScheme.error
    timerState.status == TaskTimerStatus.RUNNING && progressFraction <= 0.12f -> MaterialTheme.colorScheme.error
    timerState.status == TaskTimerStatus.RUNNING && progressFraction <= 0.30f -> WarningAccentColor
    else -> MaterialTheme.colorScheme.primary
  }
  val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

  val endTimeText = remember(timerState.endAtEpochMillis, timerState.status) {
    if (timerState.status == TaskTimerStatus.RUNNING && timerState.endAtEpochMillis > 0L) {
      val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
      "Ends ${fmt.format(Date(timerState.endAtEpochMillis))}"
    } else {
      null
    }
  }

  val context = LocalContext.current
  var hasNotifPermission by remember {
    mutableStateOf(
      TaskReminderManager.hasNotificationPermission(context) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    )
  }
  var canExactAlarms by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.canScheduleExactAlarms() ?: true
      } else {
        true
      }
    )
  }
  var testAlertFeedback by remember { mutableStateOf<String?>(null) }

  val notifPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasNotifPermission = granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    if (hasNotifPermission && timerState.isActiveSession) {
      TaskNotificationHelper(context).showTaskTimerNotification(timerState)
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("task_timer_section_container"),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    if (focusAuraState != null && onToggleFocusAura != null) {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
          containerColor = if (focusAuraState.isFocusSessionActive) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
          }
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Sensors,
            contentDescription = stringResource(R.string.focus_aura_title),
            tint = MaterialTheme.colorScheme.primary
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.focus_aura_title),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = if (focusAuraState.isFocusSessionActive) {
                "Face-down Focus Aura ACTIVE • apps shielded"
              } else if (focusAuraState.isArmed) {
                "Armed — place phone face-down to auto-start timer"
              } else {
                "Flip phone face-down to auto-start & shield distracting apps"
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = focusAuraState.isArmed,
            onCheckedChange = { onToggleFocusAura() },
            enabled = focusAuraState.hasAccelerometer,
            modifier = Modifier.testTag("timer_focus_aura_switch")
          )
        }
      }
    }

    if (!hasNotifPermission) {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("timer_notification_permission_card")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Icon(
            imageVector = Icons.Default.NotificationsActive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(24.dp)
          )
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Text(
              text = "Enable Time's Up Alerts",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
              text = "Allow notifications so Tasks+ alerts you the moment the timer finishes.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
            )
          }
          Button(
            onClick = {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !TaskReminderManager.hasNotificationPermission(context)
              ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              } else {
                try {
                  val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                  }
                  context.startActivity(intent)
                } catch (_: Exception) {
                }
              }
            },
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("timer_grant_notification_btn")
          ) {
            Text("Allow")
          }
        }
      }
    }

    // 1. Hero Circular Countdown Card
    Card(
      shape = RoundedCornerShape(26.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
      border = BorderStroke(
        width = 1.dp,
        color = ringColor.copy(alpha = 0.25f)
      ),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("task_timer_hero_card")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          val statusLabel = when (timerState.status) {
            TaskTimerStatus.RUNNING -> "Live Countdown"
            TaskTimerStatus.PAUSED -> "Paused"
            TaskTimerStatus.EXPIRED -> "Time's Up!"
            TaskTimerStatus.COMPLETED -> "Sprint Complete!"
            TaskTimerStatus.IDLE -> "Ready to Start"
          }
          Surface(
            color = ringColor.copy(alpha = 0.16f),
            shape = RoundedCornerShape(50)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = if (timerState.status == TaskTimerStatus.COMPLETED) {
                  Icons.Default.CheckCircle
                } else {
                  Icons.Default.Timer
                },
                contentDescription = stringResource(R.string.cd_task_timer),
                tint = ringColor,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = ringColor
              )
            }
          }

          if (endTimeText != null) {
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = RoundedCornerShape(50)
            ) {
              Text(
                text = endTimeText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
              )
            }
          }
        }

        // Circular Countdown Dial
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(220.dp)
            .testTag("task_timer_dial")
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 14.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)

            drawArc(
              color = trackColor,
              startAngle = -90f,
              sweepAngle = 360f,
              useCenter = false,
              topLeft = topLeft,
              size = arcSize,
              style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            drawArc(
              color = ringColor,
              startAngle = -90f,
              sweepAngle = 360f * progressFraction,
              useCenter = false,
              topLeft = topLeft,
              size = arcSize,
              style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
          }

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
          ) {
            Text(
              text = TaskTimerManager.formatCountdown(displayedRemainingMs),
              style = MaterialTheme.typography.displaySmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.testTag("task_timer_countdown_text")
            )
            val targetCaption = when (timerState.status) {
              TaskTimerStatus.IDLE -> {
                if (selectedTaskIdSet.isEmpty()) {
                  "All Active Tasks"
                } else if (selectedTaskIdSet.size == 1) {
                  activeTasks.firstOrNull { it.id in selectedTaskIdSet }?.title ?: "1 Selected Task"
                } else {
                  "${selectedTaskIdSet.size} Selected Tasks"
                }
              }

              TaskTimerStatus.COMPLETED -> "Time saved!"
              TaskTimerStatus.EXPIRED -> "Finish ${timerState.effectiveLabel()}"
              else -> timerState.effectiveLabel()
            }
            Text(
              text = targetCaption,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.Medium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              textAlign = TextAlign.Center
            )
            if (timerState.status != TaskTimerStatus.IDLE && timerState.totalTargetCount > 1) {
              Text(
                text = "${timerState.completedTargetCount} / ${timerState.totalTargetCount} done",
                style = MaterialTheme.typography.labelMedium,
                color = ringColor,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        // Primary Action Controls
        when (timerState.status) {
          TaskTimerStatus.RUNNING -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = onPauseTimer,
                modifier = Modifier
                  .weight(1.2f)
                  .heightIn(min = 48.dp)
                  .testTag("timer_pause_button")
              ) {
                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pause")
              }
              FilledTonalButton(
                onClick = onAddFiveMinutes,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                  .weight(0.8f)
                  .heightIn(min = 48.dp)
                  .testTag("timer_add5_button")
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("5m")
              }
              OutlinedButton(
                onClick = onResetTimer,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                  .weight(0.8f)
                  .heightIn(min = 48.dp)
                  .testTag("timer_reset_button")
              ) {
                Text("Stop")
              }
            }
          }

          TaskTimerStatus.PAUSED -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = onResumeTimer,
                modifier = Modifier
                  .weight(1.2f)
                  .heightIn(min = 48.dp)
                  .testTag("timer_resume_button")
              ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Resume")
              }
              FilledTonalButton(
                onClick = onAddFiveMinutes,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                  .weight(0.8f)
                  .heightIn(min = 48.dp)
                  .testTag("timer_add5_button")
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("5m")
              }
              OutlinedButton(
                onClick = onResetTimer,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                  .weight(0.8f)
                  .heightIn(min = 48.dp)
                  .testTag("timer_reset_button")
              ) {
                Text("Reset")
              }
            }
          }

          TaskTimerStatus.EXPIRED -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = onAddFiveMinutes,
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                  .weight(1.2f)
                  .heightIn(min = 48.dp)
                  .testTag("timer_add5_button")
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add +5 Min")
              }
              OutlinedButton(
                onClick = onResetTimer,
                modifier = Modifier
                  .weight(0.8f)
                  .heightIn(min = 48.dp)
                  .testTag("timer_reset_button")
              ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset")
              }
            }
          }

          TaskTimerStatus.IDLE, TaskTimerStatus.COMPLETED -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = {
                  val chosenTasks = activeTasks.filter { it.id in selectedTaskIdSet }
                  onStartTimer(configuredDurationMillis, chosenTasks)
                },
                modifier = Modifier
                  .weight(1.3f)
                  .heightIn(min = 48.dp)
                  .testTag("timer_start_button")
              ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  if (timerState.status == TaskTimerStatus.COMPLETED) "Start New Sprint"
                  else "Start Countdown"
                )
              }
              if (timerState.status == TaskTimerStatus.COMPLETED) {
                OutlinedButton(
                  onClick = onResetTimer,
                  modifier = Modifier
                    .weight(0.7f)
                    .heightIn(min = 48.dp)
                    .testTag("timer_reset_button")
                ) {
                  Text("Clear")
                }
              }
            }
          }
        }
      }
    }

    // 2. Duration Picker Card (visible when configuring a new sprint)
    if (isEditingConfig) {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Alarm,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "Given Time Duration",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            FilterChip(
              selected = (customMinutesText.isBlank() || customMinutesText == "0") && customSecondsText == "30",
              onClick = {
                customMinutesText = "0"
                customSecondsText = "30"
              },
              label = { Text("30s") },
              modifier = Modifier.testTag("timer_preset_30s")
            )
            listOf(1, 5, 10, 15, 25, 45, 60).forEach { mins ->
              val isSelected = customMinutesText == mins.toString() &&
                (customSecondsText.isBlank() || customSecondsText == "0")
              FilterChip(
                selected = isSelected,
                onClick = {
                  customMinutesText = mins.toString()
                  customSecondsText = "0"
                },
                label = { Text("${mins}m") },
                modifier = Modifier.testTag("timer_preset_${mins}m")
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedTextField(
              value = customMinutesText,
              onValueChange = { input ->
                customMinutesText = input.filter { it.isDigit() }.take(3)
              },
              label = { Text("Minutes") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("timer_custom_minutes_input")
            )
            OutlinedTextField(
              value = customSecondsText,
              onValueChange = { input ->
                customSecondsText = input.filter { it.isDigit() }.take(2)
              },
              label = { Text("Seconds") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("timer_custom_seconds_input")
            )
          }
        }
      }
    }

    // 3. Target Tasks Checklist Card (Select before start OR check off during countdown)
    Card(
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        val headerTitle = if (isEditingConfig) {
          "Tasks to Complete Before Time Ends"
        } else {
          "Active Timer Tasks"
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = headerTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          if (isEditingConfig && selectedTaskIdSet.isNotEmpty()) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(50)
            ) {
              Text(
                text = "${selectedTaskIdSet.size} picked",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
          }
        }

        if (activeTasks.isEmpty()) {
          Text(
            text = "No active tasks in To Do right now. You can still run a general countdown sprint!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else if (isEditingConfig) {
          FilterChip(
            selected = selectedTaskIdSet.isEmpty(),
            onClick = { selectedTaskIdsCsv = "" },
            label = { Text("All Active Tasks (${activeTasks.size})") },
            leadingIcon = if (selectedTaskIdSet.isEmpty()) {
              { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
            modifier = Modifier.testTag("timer_select_all_tasks_chip")
          )

          activeTasks.forEach { task ->
            val picked = task.id in selectedTaskIdSet
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = if (picked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
              } else {
                MaterialTheme.colorScheme.surface
              },
              border = BorderStroke(
                width = 1.dp,
                color = if (picked) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                }
              ),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                  val mutable = selectedTaskIdSet.toMutableSet()
                  if (!mutable.add(task.id)) mutable.remove(task.id)
                  selectedTaskIdsCsv = mutable.joinToString("|")
                }
                .testTag("timer_pick_task_${task.id}")
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Icon(
                  imageVector = if (picked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                  contentDescription = if (picked) "Selected task" else "Unselected task",
                  tint = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  if (task.subtext.isNotBlank()) {
                    Text(
                      text = task.subtext,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
              }
            }
          }
        } else {
          val liveTargetTasks = remember(activeTasks, timerState.targetTaskIds) {
            if (timerState.targetTaskIds.isNotEmpty()) {
              activeTasks.filter { it.id in timerState.targetTaskIds }
            } else {
              activeTasks
            }
          }
          if (liveTargetTasks.isEmpty()) {
            Text(
              text = "All target tasks for this sprint are finished!",
              style = MaterialTheme.typography.bodyMedium,
              color = CompletedAccentColor,
              fontWeight = FontWeight.SemiBold
            )
          } else {
            liveTargetTasks.forEach { task ->
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = task.title,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    if (task.subtext.isNotBlank()) {
                      Text(
                        text = task.subtext,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                  }
                  FilledTonalButton(
                    onClick = { onCompleteTask(task) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                      .heightIn(min = 48.dp)
                      .testTag("timer_complete_task_${task.id}")
                  ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Done")
                  }
                }
              }
            }
          }
        }
      }
    }

    // 4. Timer Preferences & Live Visibility Card
    Card(
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          text = "Timer Settings & Visibility",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.NotificationsActive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Text(
            text = "Shows a live countdown in your notification shade and posts a high-priority Time's Up alert notification with sound when time expires.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Widgets,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Text(
            text = "Visible live in the Small & 4x4 Tasks+ widgets and the dedicated Countdown Timer widget.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Text(
          text = "Default Sprint Length",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf(10, 15, 25, 30, 45, 60).forEach { mins ->
            FilterChip(
              selected = timerState.defaultDurationMinutes == mins,
              onClick = {
                customMinutesText = mins.toString()
                customSecondsText = "0"
                onUpdateSettings(
                  mins,
                  timerState.ringSoundOnExpire,
                  timerState.autoCompleteWhenTasksDone
                )
              },
              label = { Text("${mins}m") },
              modifier = Modifier.testTag("timer_default_duration_${mins}m")
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Alert Sound on Time's Up",
              style = MaterialTheme.typography.titleSmall
            )
            Text(
              text = "Play a chime and vibrate when the countdown finishes",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = timerState.ringSoundOnExpire,
            onCheckedChange = { enabled ->
              onUpdateSettings(
                timerState.defaultDurationMinutes,
                enabled,
                timerState.autoCompleteWhenTasksDone
              )
            },
            modifier = Modifier.testTag("timer_sound_switch")
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Auto-Finish When Tasks Completed",
              style = MaterialTheme.typography.titleSmall
            )
            Text(
              text = "Stop the timer early and save remaining time once target tasks are checked off",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = timerState.autoCompleteWhenTasksDone,
            onCheckedChange = { enabled ->
              onUpdateSettings(
                timerState.defaultDurationMinutes,
                timerState.ringSoundOnExpire,
                enabled
              )
            },
            modifier = Modifier.testTag("timer_autocomplete_switch")
          )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          FilledTonalButton(
            onClick = {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !TaskReminderManager.hasNotificationPermission(context)
              ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                testAlertFeedback = "Please grant notification permission first."
              } else {
                val sent = TaskNotificationHelper(context).sendTestTimerExpiredNotification(
                  ringSound = timerState.ringSoundOnExpire
                )
                testAlertFeedback = if (sent) {
                  "Time's Up alert notification sent!"
                } else {
                  "Notifications are disabled in system settings."
                }
              }
            },
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("timer_test_expired_notification_btn")
          ) {
            Icon(
              imageVector = Icons.Default.NotificationsActive,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Test Time's Up Alert")
          }

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canExactAlarms) {
            OutlinedButton(
              onClick = {
                try {
                  val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                  }
                  context.startActivity(intent)
                } catch (_: Exception) {
                }
                canExactAlarms = (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
                  ?.canScheduleExactAlarms() ?: true
              },
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("timer_allow_exact_alarm_btn")
            ) {
              Text("Exact Alarm")
            }
          }
        }

        if (testAlertFeedback != null) {
          Text(
            text = testAlertFeedback!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
          )
        }
      }
    }
  }
}

/**
 * Compact live countdown banner displayed at the top of the To-Do tab whenever a task timer
 * is actively running, paused, or expired. Tapping it jumps straight to Settings -> Task Timer.
 */
@Composable
fun ActiveTaskTimerBanner(
  timerState: TaskTimerState,
  onOpenTimerInSettings: () -> Unit,
  onPauseTimer: () -> Unit,
  onResumeTimer: () -> Unit,
  onAddFiveMinutes: () -> Unit,
  onCheckExpired: () -> Unit,
  modifier: Modifier = Modifier
) {
  if (!timerState.isActiveSession) return

  var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(timerState.status, timerState.endAtEpochMillis) {
    nowMillis = System.currentTimeMillis()
    while (timerState.status == TaskTimerStatus.RUNNING) {
      delay(500L)
      nowMillis = System.currentTimeMillis()
      if (timerState.endAtEpochMillis > 0L && nowMillis >= timerState.endAtEpochMillis) {
        onCheckExpired()
        break
      }
    }
  }

  val remainingMs = timerState.remainingMillisAt(nowMillis)
  val isExpired = timerState.status == TaskTimerStatus.EXPIRED
  val containerColor = if (isExpired) {
    MaterialTheme.colorScheme.errorContainer
  } else {
    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
  }
  val accentColor = if (isExpired) {
    MaterialTheme.colorScheme.error
  } else {
    MaterialTheme.colorScheme.primary
  }

  Card(
    onClick = onOpenTimerInSettings,
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = containerColor),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
    modifier = modifier
      .fillMaxWidth()
      .testTag("home_active_timer_banner")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(accentColor.copy(alpha = 0.16f))
      ) {
        Icon(
          imageVector = Icons.Default.Timer,
          contentDescription = stringResource(R.string.cd_task_timer),
          tint = accentColor,
          modifier = Modifier.size(22.dp)
        )
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = if (isExpired) "TIME'S UP" else TaskTimerManager.formatCountdown(remainingMs),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (isExpired) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
          )
          if (timerState.status == TaskTimerStatus.PAUSED) {
            Surface(
              color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
              shape = RoundedCornerShape(50)
            ) {
              Text(
                text = "PAUSED",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
        }
        Text(
          text = timerState.effectiveLabel(),
          style = MaterialTheme.typography.bodySmall,
          color = if (isExpired) {
            MaterialTheme.colorScheme.onErrorContainer
          } else {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
          },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      when (timerState.status) {
        TaskTimerStatus.RUNNING -> {
          FilledTonalIconButton(
            onClick = onPauseTimer,
            modifier = Modifier
              .size(48.dp)
              .testTag("home_timer_pause_btn")
          ) {
            Icon(Icons.Default.Pause, contentDescription = "Pause task timer")
          }
        }

        TaskTimerStatus.PAUSED -> {
          FilledTonalIconButton(
            onClick = onResumeTimer,
            modifier = Modifier
              .size(48.dp)
              .testTag("home_timer_resume_btn")
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Resume task timer")
          }
        }

        TaskTimerStatus.EXPIRED -> {
          FilledTonalButton(
            onClick = onAddFiveMinutes,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("home_timer_add5_btn")
          ) {
            Text("+5m")
          }
        }

        else -> Unit
      }
    }
  }
}
