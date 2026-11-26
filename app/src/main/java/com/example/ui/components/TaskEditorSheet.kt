package com.example.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.analytics.ProcrastinationRadarEngine
import com.example.analytics.ProcrastinationRadarReport
import com.example.data.model.RecurrenceType
import com.example.data.model.TaskCategory
import com.example.data.model.TaskCategoryCatalog
import com.example.data.model.TaskEntity
import com.example.data.model.TaskPriority
import com.example.notifications.TaskReminderManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TaskSheetMode {
  CREATE,
  EDIT,
  COPY_COMPLETED
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
  mode: TaskSheetMode,
  initialTask: TaskEntity?,
  defaultReminderMinutes: Int,
  categoriesEnabled: Boolean = true,
  availableCategories: List<TaskCategory> = TaskCategoryCatalog.premadeCategories,
  radarReport: ProcrastinationRadarReport? = null,
  onDismiss: () -> Unit,
  onSave: (
    existingId: String?,
    title: String,
    subtext: String,
    scheduledAtMillis: Long,
    maxCompletionMillis: Long,
    reminderIntervalMinutes: Int,
    priority: TaskPriority,
    ringAlarmBell: Boolean,
    recurrenceType: RecurrenceType,
    recurrenceInterval: Int,
    selectedDaysOfWeek: Set<Int>,
    urgencyLevel: Int,
    categoryId: String,
    voiceContractPath: String
  ) -> Unit
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val now = remember { System.currentTimeMillis() }

  var title by remember(initialTask, mode) {
    mutableStateOf(initialTask?.title.orEmpty())
  }
  var subtext by remember(initialTask, mode) {
    mutableStateOf(initialTask?.subtext.orEmpty())
  }

  val defaultStart = remember(initialTask, mode) {
    if (mode == TaskSheetMode.EDIT && initialTask != null) {
      initialTask.scheduledAtMillis
    } else {
      now
    }
  }
  val defaultMax = remember(initialTask, mode) {
    if (mode == TaskSheetMode.EDIT && initialTask != null) {
      initialTask.maxCompletionMillis
    } else if (mode == TaskSheetMode.COPY_COMPLETED && initialTask != null) {
      val prevDuration = (initialTask.maxCompletionMillis - initialTask.scheduledAtMillis)
        .coerceAtLeast(3_600_000L)
      now + prevDuration
    } else {
      now + 2 * 3_600_000L
    }
  }

  var scheduledAtMillis by remember(initialTask, mode) { mutableLongStateOf(defaultStart) }
  var maxCompletionMillis by remember(initialTask, mode) { mutableLongStateOf(defaultMax) }

  var reminderMinutes by remember(initialTask, mode) {
    mutableIntStateOf(initialTask?.reminderIntervalMinutes ?: defaultReminderMinutes)
  }
  var showCustomReminderInput by remember { mutableStateOf(false) }
  var customReminderText by remember {
    mutableStateOf((initialTask?.reminderIntervalMinutes ?: 45).toString())
  }

  var selectedUrgencyLevel by remember(initialTask, mode) {
    mutableIntStateOf(initialTask?.resolvedUrgencyLevel ?: TaskPriority.HIGH.defaultUrgencyLevel)
  }
  var selectedCategoryId by remember(initialTask, mode) {
    mutableStateOf(initialTask?.categoryId.orEmpty())
  }
  var ringAlarmBell by remember(initialTask, mode) {
    mutableStateOf(initialTask?.ringAlarmBell ?: false)
  }
  var voiceContractPath by remember(initialTask, mode) {
    mutableStateOf(if (mode == TaskSheetMode.EDIT) initialTask?.voiceContractPath.orEmpty() else "")
  }

  var recurrenceType by remember(initialTask, mode) {
    mutableStateOf(initialTask?.parsedRecurrenceType ?: RecurrenceType.NONE)
  }
  var recurrenceIntervalText by remember(initialTask, mode) {
    mutableStateOf((initialTask?.recurrenceInterval ?: 1).toString())
  }
  var selectedWeekDays by remember(initialTask, mode) {
    mutableStateOf(
      initialTask?.selectedDaysOfWeek?.takeIf { it.isNotEmpty() } ?: setOf(1, 3, 5)
    )
  }

  val dateFormatter = remember { SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()) }

  fun pickDateTime(initialMillis: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
      context,
      { _, year, month, dayOfMonth ->
        TimePickerDialog(
          context,
          { _, hourOfDay, minute ->
            val picked = Calendar.getInstance().apply {
              set(Calendar.YEAR, year)
              set(Calendar.MONTH, month)
              set(Calendar.DAY_OF_MONTH, dayOfMonth)
              set(Calendar.HOUR_OF_DAY, hourOfDay)
              set(Calendar.MINUTE, minute)
              set(Calendar.SECOND, 0)
            }
            onPicked(picked.timeInMillis)
          },
          cal.get(Calendar.HOUR_OF_DAY),
          cal.get(Calendar.MINUTE),
          false
        ).show()
      },
      cal.get(Calendar.YEAR),
      cal.get(Calendar.MONTH),
      cal.get(Calendar.DAY_OF_MONTH)
    ).show()
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Text(
        text = when (mode) {
          TaskSheetMode.CREATE -> "New Task"
          TaskSheetMode.EDIT -> "Edit Task"
          TaskSheetMode.COPY_COMPLETED -> "Copy Task to To Do"
        },
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface
      )

      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Task Name") },
        placeholder = { Text("e.g., Submit sprint report") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("task_title_input")
      )

      OutlinedTextField(
        value = subtext,
        onValueChange = { subtext = it },
        label = { Text("Brief Subtext") },
        placeholder = { Text("Short note or context") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("task_subtext_input")
      )

      // 1 (Green) to 5 (Red) Thermometer Urgency Slide Bar
      UrgencyThermometerSlider(
        urgencyLevel = selectedUrgencyLevel,
        onUrgencyLevelChange = { selectedUrgencyLevel = it },
        title = "Urgency Level"
      )

      // Optional Color-Coded Category Picker
      if (categoriesEnabled && availableCategories.isNotEmpty()) {
        TaskCategoryPickerCard(
          categories = availableCategories,
          selectedCategoryId = selectedCategoryId,
          onSelectCategoryId = { selectedCategoryId = it }
        )
      }

      // Optional Audible Alarm Bell Toggle
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = Icons.Default.Alarm,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp)
            )
            Column {
              Text(
                text = "Ring Alarm Bell (Optional)",
                style = MaterialTheme.typography.titleSmall
              )
              Text(
                text = "Rings alarm bell with wake-up notifications",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          Switch(
            checked = ringAlarmBell,
            onCheckedChange = { ringAlarmBell = it },
            modifier = Modifier.testTag("ring_alarm_bell_switch")
          )
        }
      }

      // Schedule Start / Future Time
      Text(
        text = "Schedule Time",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        val presets = listOf(
          "Now" to 0L,
          "+1 hr" to 3_600_000L,
          "+3 hr" to 3 * 3_600_000L,
          "Tomorrow" to 24 * 3_600_000L
        )
        presets.forEach { (label, offset) ->
          FilterChip(
            selected = kotlin.math.abs((scheduledAtMillis - now) - offset) < 120_000L,
            onClick = {
              val duration = (maxCompletionMillis - scheduledAtMillis).coerceAtLeast(1_800_000L)
              scheduledAtMillis = System.currentTimeMillis() + offset
              maxCompletionMillis = scheduledAtMillis + duration
            },
            label = { Text(label) }
          )
        }
        OutlinedButton(
          onClick = {
            pickDateTime(scheduledAtMillis) { picked ->
              scheduledAtMillis = picked
              if (maxCompletionMillis <= scheduledAtMillis) {
                maxCompletionMillis = scheduledAtMillis + 3_600_000L
              }
            }
          }
        ) {
          Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(dateFormatter.format(Date(scheduledAtMillis)))
        }
      }

      // Max Time Until Completion / Due Date
      Text(
        text = "Due Date (Max Time Until Completion)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        val durationPresets = listOf(
          "30m" to 30 * 60_000L,
          "1h" to 60 * 60_000L,
          "2h" to 120 * 60_000L,
          "4h" to 240 * 60_000L,
          "1d" to 24 * 3_600_000L
        )
        durationPresets.forEach { (label, durMs) ->
          val currentDur = maxCompletionMillis - scheduledAtMillis
          FilterChip(
            selected = kotlin.math.abs(currentDur - durMs) < 120_000L,
            onClick = { maxCompletionMillis = scheduledAtMillis + durMs },
            label = { Text(label) }
          )
        }
        OutlinedButton(
          onClick = {
            pickDateTime(maxCompletionMillis) { picked ->
              maxCompletionMillis = picked.coerceAtLeast(scheduledAtMillis + 300_000L)
            }
          }
        ) {
          Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(dateFormatter.format(Date(maxCompletionMillis)))
        }
      }

      // Priority Notification Interval
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Icon(
          imageVector = Icons.Default.NotificationsActive,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp)
        )
        Text(
          text = "Priority Notification Interval",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary
        )
      }
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        val intervalOptions = listOf(
          "Off" to 0,
          "10m" to 10,
          "30m" to 30,
          "1h" to 60,
          "2h" to 120
        )
        intervalOptions.forEach { (label, mins) ->
          FilterChip(
            selected = !showCustomReminderInput && reminderMinutes == mins,
            onClick = {
              showCustomReminderInput = false
              reminderMinutes = mins
            },
            label = { Text(label) },
            modifier = Modifier.testTag("interval_chip_$mins")
          )
        }
        FilterChip(
          selected = showCustomReminderInput || reminderMinutes !in listOf(0, 10, 30, 60, 120),
          onClick = { showCustomReminderInput = true },
          label = { Text("Custom") }
        )
      }

      if (showCustomReminderInput || reminderMinutes !in listOf(0, 10, 30, 60, 120)) {
        OutlinedTextField(
          value = customReminderText,
          onValueChange = {
            customReminderText = it.filter { ch -> ch.isDigit() }
            reminderMinutes = customReminderText.toIntOrNull()?.coerceIn(1, 10080) ?: 15
          },
          label = { Text("Repeat every N minutes") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        )
      }

      // Recurrence Schedule
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Autorenew,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp)
        )
        Text(
          text = "Recurring Schedule",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary
        )
      }
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        RecurrenceType.entries.forEach { type ->
          FilterChip(
            selected = recurrenceType == type,
            onClick = { recurrenceType = type },
            label = { Text(type.label) },
            modifier = Modifier.testTag("recurrence_chip_${type.name}")
          )
        }
      }

      if (recurrenceType == RecurrenceType.WEEKLY) {
        val dayLabels = listOf(
          1 to "Mon",
          2 to "Tue",
          3 to "Wed",
          4 to "Thu",
          5 to "Fri",
          6 to "Sat",
          7 to "Sun"
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          dayLabels.forEach { (dayNum, dayText) ->
            val isSelected = dayNum in selectedWeekDays
            FilterChip(
              selected = isSelected,
              onClick = {
                selectedWeekDays = if (isSelected && selectedWeekDays.size > 1) {
                  selectedWeekDays - dayNum
                } else {
                  selectedWeekDays + dayNum
                }
              },
              label = { Text(dayText) }
            )
          }
        }
      }

      if (recurrenceType in listOf(
          RecurrenceType.CUSTOM_DAYS,
          RecurrenceType.MONTHLY,
          RecurrenceType.ANNUALLY
        )
      ) {
        val unitLabel = when (recurrenceType) {
          RecurrenceType.CUSTOM_DAYS -> "days"
          RecurrenceType.MONTHLY -> "months"
          RecurrenceType.ANNUALLY -> "years"
          else -> "days"
        }
        OutlinedTextField(
          value = recurrenceIntervalText,
          onValueChange = { recurrenceIntervalText = it.filter { ch -> ch.isDigit() } },
          label = { Text("Repeat every N $unitLabel") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        )
      }

      val scheduleRisk = remember(scheduledAtMillis, radarReport) {
        radarReport?.let { ProcrastinationRadarEngine.evaluateScheduleRisk(scheduledAtMillis, it) }
      }
      if (scheduleRisk != null) {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Insights,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = scheduleRisk.warningTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
              )
            }
            Text(
              text = scheduleRisk.warningBody,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            FilledTonalButton(
              onClick = {
                val duration = (maxCompletionMillis - scheduledAtMillis).coerceAtLeast(1_800_000L)
                scheduledAtMillis = scheduleRisk.suggestedStartMillis
                maxCompletionMillis = scheduleRisk.suggestedStartMillis + duration
              },
              modifier = Modifier.testTag("radar_move_to_peak_button")
            ) {
              Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Move to Peak Slot (${scheduleRisk.suggestedWindow.label})")
            }
          }
        }
      }

      VoiceContractRecorderCard(
        voiceContractPath = voiceContractPath,
        onVoiceContractChanged = { voiceContractPath = it },
        compact = true
      )

      Spacer(modifier = Modifier.height(6.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
        ) {
          Text("Cancel")
        }
        Button(
          onClick = {
            if (title.isNotBlank()) {
              onSave(
                if (mode == TaskSheetMode.EDIT) initialTask?.id else null,
                title,
                subtext,
                scheduledAtMillis,
                maxCompletionMillis,
                reminderMinutes,
                TaskPriority.fromUrgencyLevel(selectedUrgencyLevel),
                ringAlarmBell,
                recurrenceType,
                recurrenceIntervalText.toIntOrNull()?.coerceIn(1, 365) ?: 1,
                selectedWeekDays,
                selectedUrgencyLevel,
                selectedCategoryId,
                voiceContractPath
              )
              onDismiss()
            }
          },
          enabled = title.isNotBlank(),
          modifier = Modifier
            .weight(2f)
            .height(50.dp)
            .testTag("save_task_button")
        ) {
          Icon(Icons.Default.Check, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = when (mode) {
              TaskSheetMode.CREATE -> "Add Task"
              TaskSheetMode.EDIT -> "Save Changes"
              TaskSheetMode.COPY_COMPLETED -> "Add to To Do"
            }
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskLongPressActionSheet(
  task: TaskEntity,
  onDismiss: () -> Unit,
  onEdit: () -> Unit,
  onComplete: () -> Unit,
  onTriggerAlertNow: () -> Unit,
  onStopAlarmBell: () -> Unit = {},
  onOpenFiveMinSpark: (() -> Unit)? = null,
  onOpenTransformationProof: (() -> Unit)? = null,
  onDelete: () -> Unit
) {
  val isBellRinging = remember { TaskReminderManager.isAlarmBellRinging() }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Text(
        text = task.title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface
      )
      if (task.subtext.isNotBlank()) {
        Text(
          text = task.subtext,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

      if (isBellRinging) {
        Button(
          onClick = {
            onDismiss()
            onStopAlarmBell()
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("long_press_stop_alarm_button")
        ) {
          Icon(Icons.Default.AlarmOff, contentDescription = null)
          Spacer(modifier = Modifier.width(10.dp))
          Text("Stop Alarm Bell")
        }
      }

      FilledTonalButton(
        onClick = {
          onDismiss()
          onEdit()
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("long_press_edit_button")
      ) {
        Icon(Icons.Default.Edit, contentDescription = null)
        Spacer(modifier = Modifier.width(10.dp))
        Text("Edit Task & Schedule")
      }

      FilledTonalButton(
        onClick = {
          onDismiss()
          onComplete()
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("long_press_complete_button")
      ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          if (task.parsedRecurrenceType != RecurrenceType.NONE)
            "Mark Done & Schedule Next"
          else
            "Mark as Completed"
        )
      }

      if (onOpenFiveMinSpark != null) {
        FilledTonalButton(
          onClick = {
            onDismiss()
            onOpenFiveMinSpark()
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("long_press_five_min_spark_button")
        ) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null)
          Spacer(modifier = Modifier.width(10.dp))
          Text("Break into 5-Min Spark (Gemini AI)")
        }
      }

      if (onOpenTransformationProof != null) {
        OutlinedButton(
          onClick = {
            onDismiss()
            onOpenTransformationProof()
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("long_press_before_after_proof_button")
        ) {
          Icon(Icons.Default.Compare, contentDescription = null)
          Spacer(modifier = Modifier.width(10.dp))
          Text("Before & After Photo Proof (Gemini AI)")
        }
      }

      OutlinedButton(
        onClick = {
          onDismiss()
          onTriggerAlertNow()
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
      ) {
        Icon(Icons.Default.Schedule, contentDescription = null)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          if (task.ringAlarmBell) "Send Priority Alert & Ring Bell Now"
          else "Send Priority Alert Banner Now"
        )
      }

      Button(
        onClick = {
          onDismiss()
          onDelete()
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("long_press_delete_button")
      ) {
        Icon(Icons.Default.Delete, contentDescription = null)
        Spacer(modifier = Modifier.width(10.dp))
        Text("Delete Task")
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}
