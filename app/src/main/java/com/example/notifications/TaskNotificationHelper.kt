package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.RecurrenceType
import com.example.data.model.RemoteLockMode
import com.example.data.model.ScreenTimeLimitEntity
import com.example.data.model.TaskEntity
import com.example.data.model.TaskPriority
import com.example.data.model.UrgencyLevelMeta
import com.example.screentime.ScreenTimeEnforcementEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated notification and alarm helper class that uses [AlarmManager] to schedule and trigger
 * task reminders based on the task's priority and due date (`maxCompletionMillis`), ensuring the
 * system handles CPU wake-up events (`PARTIAL_WAKE_LOCK` + `RTC_WAKEUP` / `setAlarmClock`) and
 * optionally rings an audible alarm bell alongside priority notifications.
 */
class TaskNotificationHelper(private val context: Context) {

  private val appContext = context.applicationContext

  fun ensureNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

      val priorityChannel = NotificationChannel(
        CHANNEL_ID_PRIORITY,
        CHANNEL_NAME_PRIORITY,
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "High-priority repeating reminders for active tasks on lock screen and alert banner"
        enableVibration(true)
        enableLights(true)
        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        setShowBadge(true)
      }

      val alarmChannel = NotificationChannel(
        CHANNEL_ID_ALARM_BELL,
        CHANNEL_NAME_ALARM_BELL,
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Urgent task reminders with audible alarm bell wake-up"
        enableVibration(true)
        enableLights(true)
        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        setShowBadge(true)
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
          ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
        setSound(alarmUri, audioAttrs)
      }

      val lockBeepChannel = NotificationChannel(
        CHANNEL_ID_LOCK_BEEP,
        CHANNEL_NAME_LOCK_BEEP,
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Remote parent/manager app lock alerts with notification beep"
        enableVibration(true)
        enableLights(true)
        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        setShowBadge(true)
        val notifUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_NOTIFICATION)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
        setSound(notifUri, audioAttrs)
      }

      val lockSilentChannel = NotificationChannel(
        CHANNEL_ID_LOCK_SILENT,
        CHANNEL_NAME_LOCK_SILENT,
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Silent remote app lock and task gate notifications"
        enableVibration(false)
        enableLights(true)
        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        setSound(null, null)
        setShowBadge(true)
      }

      val timerOngoingChannel = NotificationChannel(
        CHANNEL_ID_TIMER_ONGOING,
        CHANNEL_NAME_TIMER_ONGOING,
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Live countdown timer for completing active tasks"
        enableVibration(false)
        setSound(null, null)
        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        setShowBadge(false)
      }

      val timerAlertChannel = NotificationChannel(
        CHANNEL_ID_TIMER_ALERT,
        CHANNEL_NAME_TIMER_ALERT,
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "High-priority alerts when a task countdown timer finishes or reaches time up"
        enableVibration(true)
        vibrationPattern = longArrayOf(0L, 400L, 200L, 400L, 200L, 600L)
        enableLights(true)
        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        setShowBadge(true)
        val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
          ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val audioAttrs = AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
        setSound(alertUri, audioAttrs)
      }

      manager.createNotificationChannel(priorityChannel)
      manager.createNotificationChannel(alarmChannel)
      manager.createNotificationChannel(lockBeepChannel)
      manager.createNotificationChannel(lockSilentChannel)
      manager.createNotificationChannel(timerOngoingChannel)
      manager.createNotificationChannel(timerAlertChannel)
    }
  }

  fun hasNotificationPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  /**
   * Computes the next reminder trigger time in epoch milliseconds based on the task's priority,
   * configured reminder interval, and proximity to its due date (`maxCompletionMillis`).
   */
  fun computeNextTriggerTimeMillis(
    task: TaskEntity,
    now: Long = System.currentTimeMillis(),
    customDelayMinutes: Int? = null
  ): Long {
    if (customDelayMinutes != null) {
      return now + customDelayMinutes.coerceAtLeast(1) * 60_000L
    }

    // If the task is scheduled in the future, wake up at its scheduled start time first
    if (task.scheduledAtMillis > now + 30_000L) {
      return task.scheduledAtMillis
    }

    val baseMinutes = if (task.reminderIntervalMinutes > 0) {
      task.reminderIntervalMinutes
    } else {
      // Even if repeating interval is off, schedule for the due date if still in the future
      return if (task.maxCompletionMillis > now + 15_000L) {
        task.maxCompletionMillis
      } else {
        now + 30 * 60_000L
      }
    }

    // Scale interval based on 1..5 urgency thermometer and proximity to due date
    val timeUntilDueMs = task.maxCompletionMillis - now
    val urgency = task.resolvedUrgencyLevel
    val priorityMultiplier = when {
      urgency >= 5 -> if (timeUntilDueMs in 1..1_800_000L) 0.5f else 0.75f
      urgency == 4 -> if (timeUntilDueMs in 1..1_800_000L) 0.75f else 1.0f
      else -> 1.0f
    }

    val adjustedIntervalMs = (baseMinutes * 60_000L * priorityMultiplier)
      .toLong()
      .coerceAtLeast(60_000L)

    val candidateTrigger = now + adjustedIntervalMs
    // If the due date arrives sooner than the next interval tick, wake up right at the due date!
    return if (task.maxCompletionMillis in (now + 30_000L) until candidateTrigger) {
      task.maxCompletionMillis
    } else {
      candidateTrigger
    }
  }

  /**
   * Schedules both the repeating/priority interval wake-up alarm and the due-date deadline wake-up
   * alarm using [AlarmManager].
   */
  fun scheduleTaskReminder(
    task: TaskEntity,
    notificationsEnabled: Boolean = true,
    customDelayMinutes: Int? = null,
    fireInitialImmediately: Boolean = false
  ) {
    ensureNotificationChannels()
    if (!notificationsEnabled || task.isCompleted || (task.reminderIntervalMinutes <= 0 && !task.ringAlarmBell)) {
      cancelTaskReminder(task.id)
      return
    }

    val now = System.currentTimeMillis()
    if (fireInitialImmediately && task.scheduledAtMillis <= now + 60_000L) {
      showPriorityTaskNotification(task, triggerAlarmBell = task.ringAlarmBell)
    }

    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val triggerAtMillis = computeNextTriggerTimeMillis(task, now, customDelayMinutes)
    val remindPendingIntent = buildRemindPendingIntent(task.id, isDeadlineAlarm = false)

    scheduleWakeUpAlarm(
      alarmManager = alarmManager,
      triggerAtMillis = triggerAtMillis,
      pendingIntent = remindPendingIntent,
      useAlarmClockMode = task.ringAlarmBell || task.resolvedUrgencyLevel >= 5,
      taskId = task.id
    )

    // Also schedule an exact wake-up alarm at the task's due date (`maxCompletionMillis`) if in the future
    if (task.maxCompletionMillis > now + 45_000L && kotlin.math.abs(task.maxCompletionMillis - triggerAtMillis) > 30_000L) {
      val deadlinePendingIntent = buildRemindPendingIntent(task.id, isDeadlineAlarm = true)
      scheduleWakeUpAlarm(
        alarmManager = alarmManager,
        triggerAtMillis = task.maxCompletionMillis,
        pendingIntent = deadlinePendingIntent,
        useAlarmClockMode = task.ringAlarmBell || task.resolvedUrgencyLevel >= 5,
        taskId = task.id
      )
    }
  }

  private fun scheduleWakeUpAlarm(
    alarmManager: AlarmManager,
    triggerAtMillis: Long,
    pendingIntent: PendingIntent,
    useAlarmClockMode: Boolean,
    taskId: String
  ) {
    try {
      val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager.canScheduleExactAlarms()
      } else {
        true
      }

      if (useAlarmClockMode && canExact) {
        val showIntent = Intent(appContext, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
          putExtra(EXTRA_TASK_ID, taskId)
        }
        val showPendingIntent = PendingIntent.getActivity(
          appContext,
          (taskId + "_clock_ui").hashCode(),
          showIntent,
          pendingIntentFlags()
        )
        val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
        alarmManager.setAlarmClock(clockInfo, pendingIntent)
        return
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (canExact) {
          alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
          )
        } else {
          alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
          )
        }
      } else {
        alarmManager.setExact(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          pendingIntent
        )
      }
    } catch (_: Exception) {
      try {
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
      } catch (_: Exception) {
      }
    }
  }

  fun cancelTaskReminder(taskId: String) {
    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(buildRemindPendingIntent(taskId, isDeadlineAlarm = false))
    alarmManager.cancel(buildRemindPendingIntent(taskId, isDeadlineAlarm = true))
    AlarmBellPlayer.stopAlarmBell(appContext)
    NotificationManagerCompat.from(appContext).cancel(taskId.hashCode())
  }

  /**
   * Schedules an exact [AlarmManager] wake-up event for an app lock assigned at an exact clock time.
   */
  fun scheduleRemoteAppLockAlarm(limit: ScreenTimeLimitEntity) {
    ensureNotificationChannels()
    val pkg = limit.packageName
    if (pkg.isBlank() || !limit.isEnabled || limit.parsedLockMode != RemoteLockMode.LOCK_AT_TIME || limit.lockAtMillis <= 0L) {
      cancelRemoteAppLockAlarm(pkg)
      return
    }
    val now = System.currentTimeMillis()
    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pendingIntent = buildEnforceAppLockPendingIntent(pkg)
    if (limit.lockAtMillis <= now + 2_000L) {
      // Trigger immediately via alarm/notification
      scheduleWakeUpAlarm(
        alarmManager = alarmManager,
        triggerAtMillis = now + 500L,
        pendingIntent = pendingIntent,
        useAlarmClockMode = true,
        taskId = "lock_$pkg"
      )
      return
    }
    scheduleWakeUpAlarm(
      alarmManager = alarmManager,
      triggerAtMillis = limit.lockAtMillis,
      pendingIntent = pendingIntent,
      useAlarmClockMode = true,
      taskId = "lock_$pkg"
    )
  }

  fun cancelRemoteAppLockAlarm(packageName: String) {
    if (packageName.isBlank()) return
    try {
      val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      alarmManager.cancel(buildEnforceAppLockPendingIntent(packageName))
      NotificationManagerCompat.from(appContext).cancel(("lock_notif_$packageName").hashCode())
    } catch (_: Exception) {
    }
  }

  /**
   * Sends an instant notification to the child's device about an app lock or scheduled lock time,
   * with step-by-step instructions to complete a task before continuing and an optional short beep.
   */
  fun showRemoteAppLockNotification(
    limit: ScreenTimeLimitEntity,
    isScheduledPreview: Boolean = false,
    playBeep: Boolean = limit.playBeepOnLock
  ): Boolean {
    ensureNotificationChannels()
    if (!hasNotificationPermission()) return false

    if (playBeep) {
      AlarmBellPlayer.playShortNotificationBeep(appContext)
    }

    val openIntent = Intent(appContext, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      if (limit.isGroupEnforced && (limit.requiredGroupTaskId.isNotBlank() || limit.requiredGroupTaskTitle.isNotBlank())) {
        putExtra(ScreenTimeEnforcementEngine.EXTRA_OPEN_GROUP_TAB, true)
      } else {
        putExtra(ScreenTimeEnforcementEngine.EXTRA_OPEN_SCREEN_TIME_TAB, true)
      }
    }
    val openPendingIntent = PendingIntent.getActivity(
      appContext,
      ("open_lock_${limit.packageName}").hashCode(),
      openIntent,
      pendingIntentFlags()
    )

    val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val enforcer = limit.enforcedByName.ifBlank { "Group Manager" }
    val title = when {
      isScheduledPreview && limit.parsedLockMode == RemoteLockMode.LOCK_AT_TIME && limit.lockAtMillis > 0L ->
        "${limit.appName} Lock Scheduled (${fmt.format(Date(limit.lockAtMillis))})"
      limit.isLimitReachedAt(System.currentTimeMillis()) ->
        "${limit.appName} Locked by $enforcer"
      else ->
        "App Limit Set: ${limit.appName}"
    }

    val body = buildString {
      if (limit.requiredGroupTaskTitle.isNotBlank()) {
        append("Complete task \"${limit.requiredGroupTaskTitle}\" to continue using ${limit.appName}.")
      } else if (limit.parsedLockMode == RemoteLockMode.LOCK_AT_TIME && limit.lockAtMillis > 0L && isScheduledPreview) {
        append("${limit.appName} will lock automatically at ${fmt.format(Date(limit.lockAtMillis))}.")
      } else if (limit.parsedLockMode == RemoteLockMode.USAGE_LIMIT) {
        append("Allowed screen time: ${limit.limitMinutes}m (${limit.parsedPeriod.label}).")
      } else {
        append("Access to ${limit.appName} is restricted by $enforcer.")
      }
      if (limit.taskInstructions.isNotBlank()) {
        append("\nInstructions: ${limit.taskInstructions}")
      }
    }

    val channelId = if (playBeep) CHANNEL_ID_LOCK_BEEP else CHANNEL_ID_LOCK_SILENT
    val builder = NotificationCompat.Builder(appContext, channelId)
      .setSmallIcon(R.drawable.ic_stat_tasks_plus)
      .setContentTitle(title)
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setAutoCancel(true)
      .setContentIntent(openPendingIntent)

    if (!playBeep) {
      builder.setSilent(true)
    }

    return try {
      NotificationManagerCompat.from(appContext).notify(("lock_notif_${limit.packageName}").hashCode(), builder.build())
      true
    } catch (_: SecurityException) {
      false
    }
  }

  fun showPriorityTaskNotification(
    task: TaskEntity,
    triggerAlarmBell: Boolean = task.ringAlarmBell
  ): Boolean {
    ensureNotificationChannels()
    if (!hasNotificationPermission()) return false

    if (triggerAlarmBell) {
      AlarmBellPlayer.startAlarmBell(appContext)
    }

    val openIntent = Intent(appContext, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_TASK_ID, task.id)
    }
    val openPendingIntent = PendingIntent.getActivity(
      appContext,
      task.id.hashCode(),
      openIntent,
      pendingIntentFlags()
    )

    val completeIntent = Intent(appContext, TaskNotificationReceiver::class.java).apply {
      action = ACTION_COMPLETE_TASK
      putExtra(EXTRA_TASK_ID, task.id)
    }
    val completePendingIntent = PendingIntent.getBroadcast(
      appContext,
      (task.id + "_done").hashCode(),
      completeIntent,
      pendingIntentFlags()
    )

    val snoozeIntent = Intent(appContext, TaskNotificationReceiver::class.java).apply {
      action = ACTION_SNOOZE_TASK
      putExtra(EXTRA_TASK_ID, task.id)
    }
    val snoozePendingIntent = PendingIntent.getBroadcast(
      appContext,
      (task.id + "_snooze").hashCode(),
      snoozeIntent,
      pendingIntentFlags()
    )

    val stopAlarmIntent = Intent(appContext, TaskNotificationReceiver::class.java).apply {
      action = ACTION_STOP_ALARM
      putExtra(EXTRA_TASK_ID, task.id)
    }
    val stopAlarmPendingIntent = PendingIntent.getBroadcast(
      appContext,
      (task.id + "_stop_alarm").hashCode(),
      stopAlarmIntent,
      pendingIntentFlags()
    )

    val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val now = System.currentTimeMillis()
    val isOverdue = task.maxCompletionMillis < now
    val dueText = if (isOverdue) {
      "OVERDUE (${fmt.format(Date(task.maxCompletionMillis))})"
    } else {
      "Due ${fmt.format(Date(task.maxCompletionMillis))}"
    }
    val intervalBadge = if (task.reminderIntervalMinutes > 0) {
      "Every ${formatIntervalShort(task.reminderIntervalMinutes)}"
    } else {
      "Due Alert"
    }
    val urgencyLevel = task.resolvedUrgencyLevel
    val priorityBadge = "Urgency ${UrgencyLevelMeta.fullLabel(urgencyLevel)}"
    val recurrenceBadge = if (task.parsedRecurrenceType != RecurrenceType.NONE) {
      " • ${task.parsedRecurrenceType.label}"
    } else {
      ""
    }
    val contentBody = buildString {
      if (task.subtext.isNotBlank()) {
        append(task.subtext)
        append(" • ")
      }
      append("$dueText • $priorityBadge ($intervalBadge$recurrenceBadge)")
    }

    val channelId = if (triggerAlarmBell) CHANNEL_ID_ALARM_BELL else CHANNEL_ID_PRIORITY
    val titlePrefix = when {
      triggerAlarmBell -> "🔔 "
      urgencyLevel >= 5 -> "⚡ "
      else -> ""
    }

    val thermometerPicture = ThermometerBitmapRenderer.renderNotificationPicture(
      urgencyLevel = urgencyLevel,
      taskTitle = task.title,
      dueSummary = dueText
    )
    val largeUrgencyIcon = ThermometerBitmapRenderer.renderLargeIconBitmap(urgencyLevel)

    val builder = NotificationCompat.Builder(appContext, channelId)
      .setSmallIcon(R.drawable.ic_stat_tasks_plus)
      .setLargeIcon(largeUrgencyIcon)
      .setContentTitle("$titlePrefix${task.title}")
      .setContentText(contentBody)
      .setColor(UrgencyLevelMeta.colorHex(urgencyLevel).toInt())
      .setStyle(
        NotificationCompat.BigPictureStyle()
          .bigPicture(thermometerPicture)
          .bigLargeIcon(null as Bitmap?)
          .setBigContentTitle("$titlePrefix${task.title}")
          .setSummaryText(contentBody)
      )
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(
        if (triggerAlarmBell) NotificationCompat.CATEGORY_ALARM
        else NotificationCompat.CATEGORY_REMINDER
      )
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setAutoCancel(false)
      .setContentIntent(openPendingIntent)
      .addAction(
        android.R.drawable.checkbox_on_background,
        "Mark Finished",
        completePendingIntent
      )
      .addAction(
        android.R.drawable.ic_popup_reminder,
        "Remind +10m",
        snoozePendingIntent
      )

    if (triggerAlarmBell) {
      builder.addAction(
        android.R.drawable.ic_lock_silent_mode_off,
        "Stop Alarm Bell",
        stopAlarmPendingIntent
      )
      builder.setFullScreenIntent(openPendingIntent, true)
    }

    return try {
      NotificationManagerCompat.from(appContext).notify(task.id.hashCode(), builder.build())
      true
    } catch (_: SecurityException) {
      false
    }
  }

  /**
   * Displays or updates the Task Countdown Timer notification with a native live countdown
   * chronometer (API 24+) and interactive Pause / Resume / +5m / Mark Done / Stop actions.
   */
  fun showTaskTimerNotification(state: TaskTimerState): Boolean {
    ensureNotificationChannels()
    if (state.status == TaskTimerStatus.IDLE) {
      cancelTaskTimerNotification()
      return true
    }
    if (!hasNotificationPermission()) return false

    val openIntent = Intent(appContext, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_OPEN_TASK_TIMER, true)
    }
    val openPendingIntent = PendingIntent.getActivity(
      appContext,
      88425,
      openIntent,
      pendingIntentFlags()
    )

    val pausePi = buildTimerActionPendingIntent(ACTION_TIMER_PAUSE, 88426)
    val resumePi = buildTimerActionPendingIntent(ACTION_TIMER_RESUME, 88427)
    val add5mPi = buildTimerActionPendingIntent(ACTION_TIMER_ADD_5M, 88428)
    val stopPi = buildTimerActionPendingIntent(ACTION_TIMER_STOP, 88429)

    val singleTargetTaskId = state.targetTaskIds.singleOrNull()
    val completeSinglePi = singleTargetTaskId?.let { taskId ->
      val completeIntent = Intent(appContext, TaskNotificationReceiver::class.java).apply {
        action = ACTION_COMPLETE_TASK
        putExtra(EXTRA_TASK_ID, taskId)
      }
      PendingIntent.getBroadcast(
        appContext,
        88430,
        completeIntent,
        pendingIntentFlags()
      )
    }

    val label = state.effectiveLabel()
    val progressSuffix = if (state.totalTargetCount > 1) {
      " • ${state.completedTargetCount}/${state.totalTargetCount} done"
    } else if (state.targetTaskIds.isEmpty() && state.completedTargetCount > 0) {
      " • ${state.completedTargetCount} done"
    } else {
      ""
    }
    val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

    val builder = when (state.status) {
      TaskTimerStatus.RUNNING -> {
        val endTimeText = if (state.endAtEpochMillis > 0L) {
          "Ends ${timeFmt.format(Date(state.endAtEpochMillis))}"
        } else {
          TaskTimerManager.formatCountdown(state.remainingMillisAt())
        }
        val body = "$endTimeText$progressSuffix • Finish before time runs out"
        NotificationCompat.Builder(appContext, CHANNEL_ID_TIMER_ONGOING)
          .setSmallIcon(R.drawable.ic_stat_tasks_plus)
          .setContentTitle("Task Timer • $label")
          .setContentText(body)
          .setWhen(state.endAtEpochMillis)
          .setShowWhen(true)
          .setUsesChronometer(true)
          .setChronometerCountDown(true)
          .setOngoing(true)
          .setOnlyAlertOnce(true)
          .setSilent(true)
          .setPriority(NotificationCompat.PRIORITY_LOW)
          .setCategory(NotificationCompat.CATEGORY_PROGRESS)
          .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
          .setContentIntent(openPendingIntent)
          .addAction(android.R.drawable.ic_media_pause, "Pause", pausePi)
          .addAction(android.R.drawable.ic_input_add, "+5 min", add5mPi)
          .apply {
            if (completeSinglePi != null) {
              addAction(android.R.drawable.checkbox_on_background, "Mark Done", completeSinglePi)
            } else {
              addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
            }
          }
      }

      TaskTimerStatus.PAUSED -> {
        val leftText = TaskTimerManager.formatCountdown(state.remainingMillisWhenPaused)
        NotificationCompat.Builder(appContext, CHANNEL_ID_TIMER_ONGOING)
          .setSmallIcon(R.drawable.ic_stat_tasks_plus)
          .setContentTitle("Timer Paused • $leftText left")
          .setContentText("$label$progressSuffix")
          .setOngoing(false)
          .setOnlyAlertOnce(true)
          .setSilent(true)
          .setPriority(NotificationCompat.PRIORITY_LOW)
          .setCategory(NotificationCompat.CATEGORY_PROGRESS)
          .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
          .setContentIntent(openPendingIntent)
          .addAction(android.R.drawable.ic_media_play, "Resume", resumePi)
          .addAction(android.R.drawable.ic_input_add, "+5 min", add5mPi)
          .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
      }

      TaskTimerStatus.EXPIRED -> {
        val body = "Time is up for $label$progressSuffix. Add +5 min or finish your task."
        NotificationCompat.Builder(appContext, CHANNEL_ID_TIMER_ALERT)
          .setSmallIcon(R.drawable.ic_stat_tasks_plus)
          .setContentTitle("⏰ Time's Up! • $label")
          .setContentText(body)
          .setStyle(NotificationCompat.BigTextStyle().bigText(body))
          .setWhen(System.currentTimeMillis())
          .setShowWhen(true)
          .setPriority(NotificationCompat.PRIORITY_MAX)
          .setCategory(NotificationCompat.CATEGORY_ALARM)
          .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
          .setOngoing(false)
          .setAutoCancel(true)
          .setContentIntent(openPendingIntent)
          .addAction(android.R.drawable.ic_input_add, "+5 min", add5mPi)
          .apply {
            if (completeSinglePi != null) {
              addAction(android.R.drawable.checkbox_on_background, "Mark Done", completeSinglePi)
            }
            if (state.ringSoundOnExpire) {
              setDefaults(NotificationCompat.DEFAULT_ALL)
              setVibrate(longArrayOf(0L, 400L, 200L, 400L, 200L, 600L))
              setFullScreenIntent(openPendingIntent, true)
            } else {
              setSilent(true)
            }
          }
          .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", stopPi)
      }

      TaskTimerStatus.COMPLETED -> {
        val savedText = TaskTimerManager.formatCountdown(state.remainingMillisWhenPaused)
        val body = "Completed $label with $savedText remaining!"
        NotificationCompat.Builder(appContext, CHANNEL_ID_TIMER_ALERT)
          .setSmallIcon(R.drawable.ic_stat_tasks_plus)
          .setContentTitle("Sprint Complete!")
          .setContentText(body)
          .setStyle(NotificationCompat.BigTextStyle().bigText(body))
          .setWhen(System.currentTimeMillis())
          .setShowWhen(true)
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .setCategory(NotificationCompat.CATEGORY_STATUS)
          .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
          .setAutoCancel(true)
          .setContentIntent(openPendingIntent)
          .apply {
            if (state.ringSoundOnExpire) {
              setDefaults(NotificationCompat.DEFAULT_ALL)
            } else {
              setSilent(true)
            }
          }
      }

      TaskTimerStatus.IDLE -> return true
    }

    return try {
      val manager = NotificationManagerCompat.from(appContext)
      if (state.status == TaskTimerStatus.EXPIRED || state.status == TaskTimerStatus.COMPLETED) {
        // Cancel the silent ongoing timer tray notification and post a fresh high-priority alert
        // on a dedicated ID so Android always delivers the heads-up banner, sound, and vibration.
        manager.cancel(NOTIFICATION_ID_TASK_TIMER)
        manager.notify(NOTIFICATION_ID_TASK_TIMER_ALERT, builder.build())
      } else {
        // Clear any previous Time's Up / Completed alert when a live countdown is running/paused.
        manager.cancel(NOTIFICATION_ID_TASK_TIMER_ALERT)
        manager.notify(NOTIFICATION_ID_TASK_TIMER, builder.build())
      }
      true
    } catch (_: SecurityException) {
      false
    }
  }

  fun cancelTaskTimerNotification() {
    try {
      val manager = NotificationManagerCompat.from(appContext)
      manager.cancel(NOTIFICATION_ID_TASK_TIMER)
      manager.cancel(NOTIFICATION_ID_TASK_TIMER_ALERT)
    } catch (_: Exception) {
    }
  }

  /**
   * Sends a sample high-priority Time's Up notification on [CHANNEL_ID_TIMER_ALERT] so the user
   * can immediately verify sound, vibration, and heads-up banner delivery on their device.
   */
  fun sendTestTimerExpiredNotification(ringSound: Boolean = true): Boolean {
    ensureNotificationChannels()
    if (!hasNotificationPermission()) return false
    if (ringSound) {
      AlarmBellPlayer.playShortNotificationBeep(appContext)
    }
    val sampleExpired = TaskTimerState(
      status = TaskTimerStatus.EXPIRED,
      durationMillis = 60_000L,
      remainingMillisWhenPaused = 0L,
      targetTaskTitle = "Test Countdown",
      ringSoundOnExpire = ringSound
    )
    return showTaskTimerNotification(sampleExpired)
  }

  private fun buildTimerActionPendingIntent(actionName: String, requestCode: Int): PendingIntent {
    val intent = Intent(appContext, TaskNotificationReceiver::class.java).apply {
      action = actionName
    }
    return PendingIntent.getBroadcast(
      appContext,
      requestCode,
      intent,
      pendingIntentFlags()
    )
  }

  private fun buildRemindPendingIntent(taskId: String, isDeadlineAlarm: Boolean): PendingIntent {
    val intent = Intent(appContext, TaskNotificationReceiver::class.java).apply {
      action = ACTION_REMIND_TASK
      putExtra(EXTRA_TASK_ID, taskId)
      putExtra(EXTRA_IS_DEADLINE_ALARM, isDeadlineAlarm)
    }
    val reqCode = if (isDeadlineAlarm) (taskId + "_deadline").hashCode() else taskId.hashCode()
    return PendingIntent.getBroadcast(
      appContext,
      reqCode,
      intent,
      pendingIntentFlags()
    )
  }

  private fun buildEnforceAppLockPendingIntent(packageName: String): PendingIntent {
    val intent = Intent(appContext, TaskNotificationReceiver::class.java).apply {
      action = ACTION_ENFORCE_APP_LOCK
      putExtra(EXTRA_LOCK_PACKAGE, packageName)
    }
    return PendingIntent.getBroadcast(
      appContext,
      ("enforce_lock_$packageName").hashCode(),
      intent,
      pendingIntentFlags()
    )
  }

  private fun pendingIntentFlags(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }
  }

  companion object {
    const val CHANNEL_ID_PRIORITY = "tasks_plus_priority_alerts_v2"
    const val CHANNEL_NAME_PRIORITY = "Priority Task Reminders"
    const val CHANNEL_ID_ALARM_BELL = "tasks_plus_alarm_bell_v2"
    const val CHANNEL_NAME_ALARM_BELL = "Task Alarm Bell Reminders"
    const val CHANNEL_ID_LOCK_BEEP = "tasks_plus_app_lock_beep_v1"
    const val CHANNEL_NAME_LOCK_BEEP = "Remote App Lock Alerts (Beep)"
    const val CHANNEL_ID_LOCK_SILENT = "tasks_plus_app_lock_silent_v1"
    const val CHANNEL_NAME_LOCK_SILENT = "Remote App Lock Alerts (Silent)"
    const val CHANNEL_ID_TIMER_ONGOING = "tasks_plus_timer_ongoing_v1"
    const val CHANNEL_NAME_TIMER_ONGOING = "Task Countdown Timer (Live)"
    const val CHANNEL_ID_TIMER_ALERT = "tasks_plus_timer_alert_v2"
    const val CHANNEL_NAME_TIMER_ALERT = "Task Countdown Timer Alerts"

    const val NOTIFICATION_ID_TASK_TIMER = 88421
    const val NOTIFICATION_ID_TASK_TIMER_ALERT = 88422

    const val ACTION_REMIND_TASK = "com.example.tasksplus.ACTION_REMIND_TASK"
    const val ACTION_COMPLETE_TASK = "com.example.tasksplus.ACTION_COMPLETE_TASK"
    const val ACTION_SNOOZE_TASK = "com.example.tasksplus.ACTION_SNOOZE_TASK"
    const val ACTION_STOP_ALARM = "com.example.tasksplus.ACTION_STOP_ALARM"
    const val ACTION_ENFORCE_APP_LOCK = "com.example.tasksplus.ACTION_ENFORCE_APP_LOCK"
    const val ACTION_TIMER_EXPIRED = "com.example.tasksplus.ACTION_TIMER_EXPIRED"
    const val ACTION_TIMER_PAUSE = "com.example.tasksplus.ACTION_TIMER_PAUSE"
    const val ACTION_TIMER_RESUME = "com.example.tasksplus.ACTION_TIMER_RESUME"
    const val ACTION_TIMER_ADD_5M = "com.example.tasksplus.ACTION_TIMER_ADD_5M"
    const val ACTION_TIMER_STOP = "com.example.tasksplus.ACTION_TIMER_STOP"

    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_IS_DEADLINE_ALARM = "extra_is_deadline_alarm"
    const val EXTRA_LOCK_PACKAGE = "extra_lock_package"
    const val EXTRA_OPEN_TASK_TIMER = "extra_open_task_timer"

    /**
     * Acquires a temporary CPU wake lock (`PARTIAL_WAKE_LOCK`) so wake-up reminder events
     * complete reliably even when the device screen is off.
     */
    fun acquireWakeUpLock(context: Context, timeoutMs: Long = 30_000L): PowerManager.WakeLock? {
      return try {
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(
          PowerManager.PARTIAL_WAKE_LOCK,
          "TasksPlus:ReminderWakeLock"
        )
        wakeLock?.setReferenceCounted(false)
        wakeLock?.acquire(timeoutMs)
        wakeLock
      } catch (_: Exception) {
        null
      }
    }

    fun releaseWakeUpLock(wakeLock: PowerManager.WakeLock?) {
      try {
        if (wakeLock?.isHeld == true) {
          wakeLock.release()
        }
      } catch (_: Exception) {
      }
    }

    fun ensureNotificationChannels(context: Context) {
      TaskNotificationHelper(context).ensureNotificationChannels()
    }

    fun scheduleTaskReminder(
      context: Context,
      task: TaskEntity,
      notificationsEnabled: Boolean = true,
      customDelayMinutes: Int? = null,
      fireInitialImmediately: Boolean = false
    ) {
      TaskNotificationHelper(context).scheduleTaskReminder(
        task = task,
        notificationsEnabled = notificationsEnabled,
        customDelayMinutes = customDelayMinutes,
        fireInitialImmediately = fireInitialImmediately
      )
    }

    fun cancelTaskReminder(context: Context, taskId: String) {
      TaskNotificationHelper(context).cancelTaskReminder(taskId)
    }

    fun formatIntervalShort(minutes: Int): String {
      return when {
        minutes <= 0 -> "Off"
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
      }
    }
  }
}

/**
 * Rings the device's audible Alarm Bell (`TYPE_ALARM` stream + vibration) when a task has the
 * optional Alarm Bell feature enabled. Compatible with Android 6.0 (API 23) through Android 15.
 */
object AlarmBellPlayer {
  @Volatile
  private var isCurrentlyRinging: Boolean = false
  private var activeRingtone: Ringtone? = null
  private val mainHandler = Handler(Looper.getMainLooper())
  private var autoStopRunnable: Runnable? = null

  fun isRinging(): Boolean = isCurrentlyRinging

  fun startAlarmBell(context: Context, durationMs: Long = 45_000L) {
    val appContext = context.applicationContext
    isCurrentlyRinging = true
    mainHandler.post {
      stopAlarmBellInternal(appContext)
      isCurrentlyRinging = true
      try {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
          ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
          ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val ringtone = RingtoneManager.getRingtone(appContext, alarmUri)
        if (ringtone != null) {
          val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
          ringtone.audioAttributes = attrs
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone.isLooping = true
          }
          ringtone.play()
          activeRingtone = ringtone
        }
      } catch (_: Exception) {
      }

      triggerAlarmVibration(appContext)

      val stopTask = Runnable { stopAlarmBellInternal(appContext) }
      autoStopRunnable = stopTask
      mainHandler.postDelayed(stopTask, durationMs)
    }
  }

  fun stopAlarmBell(context: Context) {
    isCurrentlyRinging = false
    val appContext = context.applicationContext
    mainHandler.post {
      stopAlarmBellInternal(appContext)
    }
  }

  fun playShortNotificationBeep(context: Context) {
    val appContext = context.applicationContext
    try {
      val vibrator = getVibrator(appContext)
      if (vibrator != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator.vibrate(VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
          @Suppress("DEPRECATION")
          vibrator.vibrate(180L)
        }
      }
    } catch (_: Exception) {
    }

    mainHandler.post {
      try {
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 360)
        mainHandler.postDelayed({
          try {
            tg.release()
          } catch (_: Exception) {
          }
        }, 480L)
      } catch (_: Exception) {
        try {
          val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
          val ringtone = RingtoneManager.getRingtone(appContext, uri)
          ringtone?.play()
        } catch (_: Exception) {
        }
      }
    }
  }

  private fun stopAlarmBellInternal(context: Context) {
    isCurrentlyRinging = false
    autoStopRunnable?.let { mainHandler.removeCallbacks(it) }
    autoStopRunnable = null
    try {
      activeRingtone?.stop()
    } catch (_: Exception) {
    }
    activeRingtone = null

    try {
      val vibrator = getVibrator(context)
      vibrator?.cancel()
    } catch (_: Exception) {
    }
  }

  private fun triggerAlarmVibration(context: Context) {
    try {
      val vibrator = getVibrator(context) ?: return
      val pattern = longArrayOf(0L, 500L, 300L, 500L, 300L, 700L)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
      } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(pattern, 0)
      }
    } catch (_: Exception) {
    }
  }

  private fun getVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      vm?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  }
}
