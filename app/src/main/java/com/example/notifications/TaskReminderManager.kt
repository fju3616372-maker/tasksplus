package com.example.notifications

import android.content.Context
import com.example.data.model.TaskEntity
import com.example.data.model.TaskPriority

/**
 * Facade delegating to [TaskNotificationHelper] and [AlarmBellPlayer] for scheduling priority
 * & due-date task reminders with wake-up support and optional alarm bell ringing.
 */
object TaskReminderManager {
  const val CHANNEL_ID = TaskNotificationHelper.CHANNEL_ID_PRIORITY
  const val CHANNEL_NAME = TaskNotificationHelper.CHANNEL_NAME_PRIORITY
  const val ACTION_REMIND_TASK = TaskNotificationHelper.ACTION_REMIND_TASK
  const val ACTION_COMPLETE_TASK = TaskNotificationHelper.ACTION_COMPLETE_TASK
  const val ACTION_SNOOZE_TASK = TaskNotificationHelper.ACTION_SNOOZE_TASK
  const val ACTION_STOP_ALARM = TaskNotificationHelper.ACTION_STOP_ALARM
  const val EXTRA_TASK_ID = TaskNotificationHelper.EXTRA_TASK_ID

  fun ensureNotificationChannel(context: Context) {
    TaskNotificationHelper(context).ensureNotificationChannels()
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return TaskNotificationHelper(context).hasNotificationPermission()
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

  fun showPriorityTaskNotification(
    context: Context,
    task: TaskEntity,
    triggerAlarmBell: Boolean = task.ringAlarmBell,
    triggerAlarmBellIfEnabled: Boolean = task.ringAlarmBell
  ): Boolean {
    val shouldRing = triggerAlarmBell || (triggerAlarmBellIfEnabled && task.ringAlarmBell)
    return TaskNotificationHelper(context).showPriorityTaskNotification(
      task = task,
      triggerAlarmBell = shouldRing
    )
  }

  fun sendTestPriorityNotification(
    context: Context,
    withAlarmBell: Boolean = false
  ): Boolean {
    val helper = TaskNotificationHelper(context)
    helper.ensureNotificationChannels()
    if (!helper.hasNotificationPermission()) return false
    val testTask = TaskEntity(
      id = "priority_alert_verification",
      title = if (withAlarmBell) "Tasks+ Alarm Bell & Reminder Active" else "Tasks+ Priority Reminder Active",
      subtext = if (withAlarmBell) "Tap 'Stop Alarm Bell' on this banner to silence" else "Lock screen & alert banner verified",
      scheduledAtMillis = System.currentTimeMillis(),
      maxCompletionMillis = System.currentTimeMillis() + 1_800_000L,
      reminderIntervalMinutes = 10,
      priority = if (withAlarmBell) TaskPriority.CRITICAL.name else TaskPriority.HIGH.name,
      urgencyLevel = if (withAlarmBell) 5 else 4,
      ringAlarmBell = withAlarmBell
    )
    return helper.showPriorityTaskNotification(testTask, triggerAlarmBell = withAlarmBell)
  }

  fun startAlarmBell(context: Context, taskId: String = "manual_alarm") {
    AlarmBellPlayer.startAlarmBell(context)
  }

  fun stopAlarmBell(context: Context) {
    AlarmBellPlayer.stopAlarmBell(context)
  }

  fun stopRingingAlarmBell(context: Context) {
    AlarmBellPlayer.stopAlarmBell(context)
  }

  fun isAlarmBellRinging(): Boolean {
    return AlarmBellPlayer.isRinging()
  }

  fun formatIntervalShort(minutes: Int): String {
    return TaskNotificationHelper.formatIntervalShort(minutes)
  }
}
