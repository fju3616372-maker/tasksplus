package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.audio.VoiceContractManager
import com.example.data.local.PreferencesRepository
import com.example.data.local.TaskDatabase
import com.example.data.model.RecurrenceType
import com.example.data.remote.CloudSyncRepository
import com.example.screentime.EarnedScreenTimeCalculator
import com.example.widget.TasksWidgetUpdater
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TaskNotificationReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return

    if (action == TaskNotificationHelper.ACTION_STOP_ALARM) {
      AlarmBellPlayer.stopAlarmBell(context)
      TimerAlarmSoundManager.stopAlarm(context)
      VoiceContractManager.stopPlayback()
      return
    }

    // Handle timer broadcasts immediately and synchronously before onReceive returns so the
    // Time's Up alert notification is guaranteed to post even under strict background limits.
    when (action) {
      TaskNotificationHelper.ACTION_TIMER_EXPIRED,
      TaskNotificationHelper.ACTION_TIMER_PAUSE,
      TaskNotificationHelper.ACTION_TIMER_RESUME,
      TaskNotificationHelper.ACTION_TIMER_ADD_5M,
      TaskNotificationHelper.ACTION_TIMER_STOP -> {
        val timerWakeLock = TaskNotificationHelper.acquireWakeUpLock(context, timeoutMs = 15_000L)
        try {
          when (action) {
            TaskNotificationHelper.ACTION_TIMER_EXPIRED -> TaskTimerManager.onTimerExpired(context)
            TaskNotificationHelper.ACTION_TIMER_PAUSE -> TaskTimerManager.pauseTimer(context)
            TaskNotificationHelper.ACTION_TIMER_RESUME -> TaskTimerManager.resumeTimer(context)
            TaskNotificationHelper.ACTION_TIMER_ADD_5M -> TaskTimerManager.addMinutes(context, 5)
            TaskNotificationHelper.ACTION_TIMER_STOP -> TaskTimerManager.resetTimer(context)
          }
        } finally {
          TaskNotificationHelper.releaseWakeUpLock(timerWakeLock)
        }
        return
      }
    }

    val wakeLock = TaskNotificationHelper.acquireWakeUpLock(context)
    val pendingResult = goAsync()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    scope.launch {
      try {
        val helper = TaskNotificationHelper(context)
        val db = TaskDatabase.getInstance(context)
        val dao = db.taskDao()
        val prefs = PreferencesRepository(context).preferencesFlow.first()

        when (action) {
          Intent.ACTION_BOOT_COMPLETED -> {
            val allActive = dao.getAllTasksOnce().filter { !it.isCompleted }
            allActive.forEach { task ->
              helper.scheduleTaskReminder(
                task = task,
                notificationsEnabled = prefs.notificationsEnabled
              )
            }
            val lockLimits = db.screenTimeDao().getAllLimitsOnce()
            lockLimits.forEach { limit ->
              if (limit.isEnabled && limit.parsedLockMode == com.example.data.model.RemoteLockMode.LOCK_AT_TIME) {
                helper.scheduleRemoteAppLockAlarm(limit)
              }
            }
            com.example.screentime.GroupCloudBackgroundSyncEngine.ensureStarted(context)
            com.example.screentime.ScreenTimeEnforcementEngine.ensureBackgroundMonitorRunning(context)
            TaskTimerManager.restoreAfterBoot(context)
            TasksWidgetUpdater.updateAllWidgets(context)
          }

          TaskNotificationHelper.ACTION_ENFORCE_APP_LOCK -> {
            val lockPkg = intent.getStringExtra(TaskNotificationHelper.EXTRA_LOCK_PACKAGE) ?: return@launch
            val limit = db.screenTimeDao().getLimitByPackage(lockPkg) ?: return@launch
            if (limit.isEnabled) {
              helper.showRemoteAppLockNotification(
                limit = limit,
                isScheduledPreview = false,
                playBeep = limit.playBeepOnLock
              )
              com.example.screentime.ScreenTimeEnforcementEngine.syncUsageAndEnforceForeground(context)
            }
          }

          TaskNotificationHelper.ACTION_REMIND_TASK -> {
            val taskId = intent.getStringExtra(TaskNotificationHelper.EXTRA_TASK_ID) ?: return@launch
            val task = dao.getTaskById(taskId) ?: return@launch
            if (!task.isCompleted && prefs.notificationsEnabled) {
              val hasVoiceContract = VoiceContractManager.hasValidRecording(task.voiceContractPath)
              helper.showPriorityTaskNotification(
                task = task,
                triggerAlarmBell = task.ringAlarmBell && !hasVoiceContract
              )
              if (hasVoiceContract) {
                VoiceContractManager.playContractFile(
                  context = context,
                  filePath = task.voiceContractPath,
                  isReminderAlarm = true
                )
              }
              if (task.reminderIntervalMinutes > 0 || task.maxCompletionMillis > System.currentTimeMillis()) {
                helper.scheduleTaskReminder(
                  task = task,
                  notificationsEnabled = true
                )
              }
            }
          }

          TaskNotificationHelper.ACTION_SNOOZE_TASK -> {
            AlarmBellPlayer.stopAlarmBell(context)
            VoiceContractManager.stopPlayback()
            val taskId = intent.getStringExtra(TaskNotificationHelper.EXTRA_TASK_ID) ?: return@launch
            val task = dao.getTaskById(taskId)
            helper.cancelTaskReminder(taskId)
            if (task != null && !task.isCompleted) {
              helper.scheduleTaskReminder(
                task = task,
                notificationsEnabled = prefs.notificationsEnabled,
                customDelayMinutes = 10
              )
            }
          }

          TaskNotificationHelper.ACTION_COMPLETE_TASK -> {
            AlarmBellPlayer.stopAlarmBell(context)
            VoiceContractManager.stopPlayback()
            val taskId = intent.getStringExtra(TaskNotificationHelper.EXTRA_TASK_ID) ?: return@launch
            helper.cancelTaskReminder(taskId)
            val task = dao.getTaskById(taskId) ?: return@launch
            if (task.isCompleted) return@launch

            val now = System.currentTimeMillis()
            val cloudRepo = if (Firebase.auth.currentUser != null) CloudSyncRepository(context) else null

            if (task.parsedRecurrenceType != RecurrenceType.NONE) {
              val completedSnapshot = task.copy(
                id = "done_${UUID.randomUUID().toString().replace("-", "_")}",
                isCompleted = true,
                completedAtMillis = now,
                recurrenceType = RecurrenceType.NONE.name,
                updatedAtMillis = now
              )
              val (nextStart, nextMax) = task.computeNextOccurrenceMillis(now)
              val rescheduledTask = task.copy(
                scheduledAtMillis = nextStart,
                maxCompletionMillis = nextMax,
                isCompleted = false,
                completedAtMillis = 0L,
                updatedAtMillis = now
              )
              dao.upsertTask(completedSnapshot)
              dao.upsertTask(rescheduledTask)
              helper.scheduleTaskReminder(
                task = rescheduledTask,
                notificationsEnabled = prefs.notificationsEnabled
              )
              cloudRepo?.upsertCloudTask(completedSnapshot)
              cloudRepo?.upsertCloudTask(rescheduledTask)
            } else {
              val completedTask = task.copy(
                isCompleted = true,
                completedAtMillis = now,
                updatedAtMillis = now
              )
              dao.upsertTask(completedTask)
              cloudRepo?.upsertCloudTask(completedTask)
            }
            if (prefs.earnYourScrollEnabled) {
              val earned = EarnedScreenTimeCalculator.minutesEarnedForUrgency(
                urgencyLevel = task.resolvedUrgencyLevel,
                baseMinutes = prefs.earnMinutesBase
              )
              PreferencesRepository(context).mintEarnedScreenTimeMinutes(earned)
            }
            val remainingCount = dao.getActiveTasksOnce().size
            TaskTimerManager.onTaskCompleted(context, task.id, remainingCount)
            TasksWidgetUpdater.updateAllWidgets(context)
          }
        }
      } finally {
        try {
          if (wakeLock?.isHeld == true) {
            wakeLock.release()
          }
        } catch (_: Exception) {
        }
        pendingResult.finish()
      }
    }
  }
}
