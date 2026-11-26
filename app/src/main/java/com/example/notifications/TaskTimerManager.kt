package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.MainActivity
import com.example.data.model.TaskEntity
import com.example.widget.TasksWidgetUpdater
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TaskTimerStatus {
  IDLE,
  RUNNING,
  PAUSED,
  COMPLETED,
  EXPIRED;

  companion object {
    fun fromName(raw: String?): TaskTimerStatus {
      return entries.firstOrNull { it.name == raw } ?: IDLE
    }
  }
}

data class TaskTimerState(
  val status: TaskTimerStatus = TaskTimerStatus.IDLE,
  val durationMillis: Long = 25 * 60_000L,
  val remainingMillisWhenPaused: Long = 25 * 60_000L,
  val startedAtEpochMillis: Long = 0L,
  val endAtEpochMillis: Long = 0L,
  val targetTaskIds: List<String> = emptyList(),
  val targetTaskTitle: String = "",
  val totalTargetCount: Int = 0,
  val completedTargetCount: Int = 0,
  val defaultDurationMinutes: Int = 25,
  val ringSoundOnExpire: Boolean = true,
  val autoCompleteWhenTasksDone: Boolean = true
) {
  val isActiveSession: Boolean
    get() = status == TaskTimerStatus.RUNNING ||
      status == TaskTimerStatus.PAUSED ||
      status == TaskTimerStatus.EXPIRED

  fun remainingMillisAt(nowEpochMillis: Long = System.currentTimeMillis()): Long {
    return when (status) {
      TaskTimerStatus.RUNNING -> (endAtEpochMillis - nowEpochMillis).coerceAtLeast(0L)
      TaskTimerStatus.PAUSED -> remainingMillisWhenPaused.coerceAtLeast(0L)
      TaskTimerStatus.IDLE -> durationMillis.coerceAtLeast(0L)
      TaskTimerStatus.COMPLETED -> remainingMillisWhenPaused.coerceAtLeast(0L)
      TaskTimerStatus.EXPIRED -> 0L
    }
  }

  fun progressFractionAt(nowEpochMillis: Long = System.currentTimeMillis()): Float {
    val total = durationMillis.coerceAtLeast(1_000L)
    val remaining = remainingMillisAt(nowEpochMillis)
    return (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
  }

  fun effectiveLabel(): String {
    return targetTaskTitle.ifBlank { "All Active Tasks" }
  }
}

/**
 * Single source of truth for the in-app Task Countdown Timer, synchronized across Compose UI,
 * ongoing & alert notifications, home-screen widgets, and exact [AlarmManager] wake-up triggers.
 */
object TaskTimerManager {
  private const val PREFS_NAME = "tasks_plus_timer_state_v1"

  private const val KEY_STATUS = "status"
  private const val KEY_DURATION_MS = "duration_ms"
  private const val KEY_REMAINING_PAUSED_MS = "remaining_paused_ms"
  private const val KEY_STARTED_AT_MS = "started_at_ms"
  private const val KEY_END_AT_MS = "end_at_ms"
  private const val KEY_TARGET_TASK_IDS = "target_task_ids"
  private const val KEY_TARGET_TASK_TITLE = "target_task_title"
  private const val KEY_TOTAL_TARGET_COUNT = "total_target_count"
  private const val KEY_COMPLETED_TARGET_COUNT = "completed_target_count"
  private const val KEY_DEFAULT_DURATION_MIN = "default_duration_min"
  private const val KEY_RING_SOUND = "ring_sound"
  private const val KEY_AUTO_COMPLETE = "auto_complete"

  const val MIN_DURATION_MILLIS = 5_000L
  const val MAX_DURATION_MILLIS = 12 * 60 * 60_000L

  private val _stateFlow = MutableStateFlow(TaskTimerState())
  @Volatile
  private var isLoaded = false
  private val mainHandler = Handler(Looper.getMainLooper())
  private var inProcessExpireRunnable: Runnable? = null

  fun stateFlow(context: Context): StateFlow<TaskTimerState> {
    val appContext = context.applicationContext
    ensureLoaded(appContext)
    syncInProcessSchedule(appContext, _stateFlow.value)
    return _stateFlow.asStateFlow()
  }

  fun syncOnAppForeground(context: Context) {
    val appContext = context.applicationContext
    ensureLoaded(appContext)
    val current = _stateFlow.value
    if (current.status == TaskTimerStatus.RUNNING) {
      if (current.endAtEpochMillis > 0L && System.currentTimeMillis() >= current.endAtEpochMillis) {
        onTimerExpired(appContext)
      } else {
        scheduleInProcessExpiration(appContext, current.endAtEpochMillis)
        scheduleExpirationAlarm(appContext, current.endAtEpochMillis)
      }
    }
  }

  fun getCurrentState(context: Context): TaskTimerState {
    val appContext = context.applicationContext
    ensureLoaded(appContext)
    val current = _stateFlow.value
    if (current.status == TaskTimerStatus.RUNNING &&
      current.endAtEpochMillis > 0L &&
      System.currentTimeMillis() >= current.endAtEpochMillis
    ) {
      return markExpiredInternal(appContext, triggerAlert = true)
    }
    return current
  }

  private fun ensureLoaded(appContext: Context) {
    if (isLoaded) return
    synchronized(this) {
      if (isLoaded) return
      _stateFlow.value = readFromPrefs(appContext)
      isLoaded = true
    }
  }

  private fun readFromPrefs(appContext: Context): TaskTimerState {
    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val defaultMin = prefs.getInt(KEY_DEFAULT_DURATION_MIN, 25).coerceIn(1, 720)
    val defaultMs = defaultMin * 60_000L
    val rawIds = prefs.getString(KEY_TARGET_TASK_IDS, "").orEmpty()
    val taskIds = rawIds.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    return TaskTimerState(
      status = TaskTimerStatus.fromName(prefs.getString(KEY_STATUS, TaskTimerStatus.IDLE.name)),
      durationMillis = prefs.getLong(KEY_DURATION_MS, defaultMs).coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS),
      remainingMillisWhenPaused = prefs.getLong(KEY_REMAINING_PAUSED_MS, defaultMs).coerceAtLeast(0L),
      startedAtEpochMillis = prefs.getLong(KEY_STARTED_AT_MS, 0L),
      endAtEpochMillis = prefs.getLong(KEY_END_AT_MS, 0L),
      targetTaskIds = taskIds,
      targetTaskTitle = prefs.getString(KEY_TARGET_TASK_TITLE, "").orEmpty(),
      totalTargetCount = prefs.getInt(KEY_TOTAL_TARGET_COUNT, 0).coerceAtLeast(0),
      completedTargetCount = prefs.getInt(KEY_COMPLETED_TARGET_COUNT, 0).coerceAtLeast(0),
      defaultDurationMinutes = defaultMin,
      ringSoundOnExpire = prefs.getBoolean(KEY_RING_SOUND, true),
      autoCompleteWhenTasksDone = prefs.getBoolean(KEY_AUTO_COMPLETE, true)
    )
  }

  private fun persistState(appContext: Context, state: TaskTimerState) {
    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
      .putString(KEY_STATUS, state.status.name)
      .putLong(KEY_DURATION_MS, state.durationMillis)
      .putLong(KEY_REMAINING_PAUSED_MS, state.remainingMillisWhenPaused)
      .putLong(KEY_STARTED_AT_MS, state.startedAtEpochMillis)
      .putLong(KEY_END_AT_MS, state.endAtEpochMillis)
      .putString(KEY_TARGET_TASK_IDS, state.targetTaskIds.joinToString("|"))
      .putString(KEY_TARGET_TASK_TITLE, state.targetTaskTitle)
      .putInt(KEY_TOTAL_TARGET_COUNT, state.totalTargetCount)
      .putInt(KEY_COMPLETED_TARGET_COUNT, state.completedTargetCount)
      .putInt(KEY_DEFAULT_DURATION_MIN, state.defaultDurationMinutes)
      .putBoolean(KEY_RING_SOUND, state.ringSoundOnExpire)
      .putBoolean(KEY_AUTO_COMPLETE, state.autoCompleteWhenTasksDone)
      .commit()
    isLoaded = true
    _stateFlow.value = state
  }

  fun startTimer(
    context: Context,
    durationMillis: Long,
    selectedTasks: List<TaskEntity>
  ): TaskTimerState {
    val appContext = context.applicationContext
    val current = getCurrentState(appContext)
    val clampedDuration = durationMillis.coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS)
    val now = System.currentTimeMillis()
    val endAt = now + clampedDuration

    val cleanIds = selectedTasks.map { it.id }.distinct()
    val title = when {
      selectedTasks.size == 1 -> selectedTasks.first().title.trim().ifBlank { "Task Sprint" }
      selectedTasks.size > 1 -> "${selectedTasks.size} Selected Tasks"
      else -> "All Active Tasks"
    }

    val updated = current.copy(
      status = TaskTimerStatus.RUNNING,
      durationMillis = clampedDuration,
      remainingMillisWhenPaused = clampedDuration,
      startedAtEpochMillis = now,
      endAtEpochMillis = endAt,
      targetTaskIds = cleanIds,
      targetTaskTitle = title,
      totalTargetCount = cleanIds.size,
      completedTargetCount = 0
    )
    AlarmBellPlayer.stopAlarmBell(appContext)
    persistState(appContext, updated)
    scheduleExpirationAlarm(appContext, endAt)
    scheduleInProcessExpiration(appContext, endAt)
    TaskNotificationHelper(appContext).showTaskTimerNotification(updated)
    TasksWidgetUpdater.updateAllWidgets(appContext)
    return updated
  }

  fun startQuickDefaultTimer(context: Context, activeTasks: List<TaskEntity>): TaskTimerState {
    val appContext = context.applicationContext
    val current = getCurrentState(appContext)
    val durationMs = current.defaultDurationMinutes.coerceIn(1, 720) * 60_000L
    val targetList = activeTasks.take(1)
    return startTimer(appContext, durationMs, targetList)
  }

  fun pauseTimer(context: Context): TaskTimerState {
    val appContext = context.applicationContext
    val current = getCurrentState(appContext)
    if (current.status != TaskTimerStatus.RUNNING) return current

    val remaining = current.remainingMillisAt()
    if (remaining <= 0L) {
      return onTimerExpired(appContext)
    }

    val updated = current.copy(
      status = TaskTimerStatus.PAUSED,
      remainingMillisWhenPaused = remaining,
      endAtEpochMillis = 0L
    )
    cancelExpirationAlarm(appContext)
    cancelInProcessExpiration()
    persistState(appContext, updated)
    TaskNotificationHelper(appContext).showTaskTimerNotification(updated)
    TasksWidgetUpdater.updateAllWidgets(appContext)
    return updated
  }

  fun resumeTimer(context: Context): TaskTimerState {
    val appContext = context.applicationContext
    val current = getCurrentState(appContext)
    if (current.status != TaskTimerStatus.PAUSED) return current

    val remaining = current.remainingMillisWhenPaused.coerceAtLeast(MIN_DURATION_MILLIS)
    val now = System.currentTimeMillis()
    val endAt = now + remaining
    val updated = current.copy(
      status = TaskTimerStatus.RUNNING,
      endAtEpochMillis = endAt
    )
    AlarmBellPlayer.stopAlarmBell(appContext)
    persistState(appContext, updated)
    scheduleExpirationAlarm(appContext, endAt)
    scheduleInProcessExpiration(appContext, endAt)
    TaskNotificationHelper(appContext).showTaskTimerNotification(updated)
    TasksWidgetUpdater.updateAllWidgets(appContext)
    return updated
  }

  fun addMinutes(context: Context, extraMinutes: Int = 5): TaskTimerState {
    val appContext = context.applicationContext
    val current = getCurrentState(appContext)
    val addMs = extraMinutes.coerceIn(1, 120) * 60_000L
    val now = System.currentTimeMillis()

    val updated = when (current.status) {
      TaskTimerStatus.RUNNING -> {
        val newRemaining = (current.remainingMillisAt(now) + addMs).coerceAtMost(MAX_DURATION_MILLIS)
        val newDuration = maxOf(current.durationMillis, newRemaining)
        current.copy(
          durationMillis = newDuration,
          endAtEpochMillis = now + newRemaining
        )
      }

      TaskTimerStatus.PAUSED -> {
        val newRemaining = (current.remainingMillisWhenPaused + addMs).coerceAtMost(MAX_DURATION_MILLIS)
        val newDuration = maxOf(current.durationMillis, newRemaining)
        current.copy(
          durationMillis = newDuration,
          remainingMillisWhenPaused = newRemaining
        )
      }

      TaskTimerStatus.EXPIRED -> {
        AlarmBellPlayer.stopAlarmBell(appContext)
        current.copy(
          status = TaskTimerStatus.RUNNING,
          durationMillis = maxOf(current.durationMillis, addMs),
          remainingMillisWhenPaused = addMs,
          endAtEpochMillis = now + addMs
        )
      }

      else -> return current
    }

    persistState(appContext, updated)
    if (updated.status == TaskTimerStatus.RUNNING) {
      scheduleExpirationAlarm(appContext, updated.endAtEpochMillis)
      scheduleInProcessExpiration(appContext, updated.endAtEpochMillis)
    }
    TaskNotificationHelper(appContext).showTaskTimerNotification(updated)
    TasksWidgetUpdater.updateAllWidgets(appContext)
    return updated
  }

  fun resetTimer(context: Context): TaskTimerState {
    val appContext = context.applicationContext
    val current = getCurrentState(appContext)
    val defaultMs = current.defaultDurationMinutes.coerceIn(1, 720) * 60_000L
    val updated = current.copy(
      status = TaskTimerStatus.IDLE,
      durationMillis = defaultMs,
      remainingMillisWhenPaused = defaultMs,
      startedAtEpochMillis = 0L,
      endAtEpochMillis = 0L,
      targetTaskIds = emptyList(),
      targetTaskTitle = "",
      totalTargetCount = 0,
      completedTargetCount = 0
    )
    AlarmBellPlayer.stopAlarmBell(appContext)
    cancelExpirationAlarm(appContext)
    cancelInProcessExpiration()
    persistState(appContext, updated)
    TaskNotificationHelper(appContext).cancelTaskTimerNotification()
    TasksWidgetUpdater.updateAllWidgets(appContext)
    return updated
  }

  fun updateSettings(
    context: Context,
    defaultDurationMinutes: Int,
    ringSoundOnExpire: Boolean,
    autoCompleteWhenTasksDone: Boolean
  ): TaskTimerState {
    val appContext = context.applicationContext
    val current = getCurrentState(appContext)
    val cleanMinutes = defaultDurationMinutes.coerceIn(1, 720)
    val newDefaultMs = cleanMinutes * 60_000L
    val updated = current.copy(
      defaultDurationMinutes = cleanMinutes,
      durationMillis = if (current.status == TaskTimerStatus.IDLE) newDefaultMs else current.durationMillis,
      remainingMillisWhenPaused = if (current.status == TaskTimerStatus.IDLE) newDefaultMs else current.remainingMillisWhenPaused,
      ringSoundOnExpire = ringSoundOnExpire,
      autoCompleteWhenTasksDone = autoCompleteWhenTasksDone
    )
    persistState(appContext, updated)
    TasksWidgetUpdater.updateAllWidgets(appContext)
    return updated
  }

  fun onTaskCompleted(
    context: Context,
    completedTaskId: String,
    remainingActiveTaskCount: Int
  ): TaskTimerState {
    val appContext = context.applicationContext
    val current = getCurrentState(appContext)
    if (current.status != TaskTimerStatus.RUNNING && current.status != TaskTimerStatus.PAUSED) {
      return current
    }

    val now = System.currentTimeMillis()
    val remainingMs = current.remainingMillisAt(now)

    val updated = if (current.targetTaskIds.isNotEmpty()) {
      if (completedTaskId !in current.targetTaskIds) return current
      val remainingTargets = current.targetTaskIds.filterNot { it == completedTaskId }
      val newCompleted = (current.completedTargetCount + 1).coerceAtMost(current.totalTargetCount.coerceAtLeast(1))
      if (remainingTargets.isEmpty() && current.autoCompleteWhenTasksDone) {
        current.copy(
          status = TaskTimerStatus.COMPLETED,
          remainingMillisWhenPaused = remainingMs,
          endAtEpochMillis = 0L,
          targetTaskIds = emptyList(),
          completedTargetCount = newCompleted
        )
      } else {
        current.copy(
          targetTaskIds = remainingTargets,
          completedTargetCount = newCompleted
        )
      }
    } else {
      val newCompleted = current.completedTargetCount + 1
      if (remainingActiveTaskCount <= 0 && current.autoCompleteWhenTasksDone) {
        current.copy(
          status = TaskTimerStatus.COMPLETED,
          remainingMillisWhenPaused = remainingMs,
          endAtEpochMillis = 0L,
          completedTargetCount = newCompleted
        )
      } else {
        current.copy(completedTargetCount = newCompleted)
      }
    }

    persistState(appContext, updated)
    if (updated.status == TaskTimerStatus.COMPLETED) {
      cancelExpirationAlarm(appContext)
      cancelInProcessExpiration()
      if (updated.ringSoundOnExpire) {
        AlarmBellPlayer.playShortNotificationBeep(appContext)
      }
    }
    TaskNotificationHelper(appContext).showTaskTimerNotification(updated)
    TasksWidgetUpdater.updateAllWidgets(appContext)
    return updated
  }

  fun onTimerExpired(context: Context): TaskTimerState {
    return markExpiredInternal(context.applicationContext, triggerAlert = true)
  }

  private fun markExpiredInternal(appContext: Context, triggerAlert: Boolean): TaskTimerState {
    val updated = synchronized(this) {
      ensureLoaded(appContext)
      val current = _stateFlow.value
      if (current.status != TaskTimerStatus.RUNNING) return current

      val nextState = current.copy(
        status = TaskTimerStatus.EXPIRED,
        remainingMillisWhenPaused = 0L,
        endAtEpochMillis = 0L
      )
      cancelExpirationAlarm(appContext)
      cancelInProcessExpiration()
      persistState(appContext, nextState)
      nextState
    }

    if (triggerAlert && updated.ringSoundOnExpire) {
      AlarmBellPlayer.playShortNotificationBeep(appContext)
    }
    TaskNotificationHelper(appContext).showTaskTimerNotification(updated)
    TasksWidgetUpdater.updateAllWidgets(appContext)
    return updated
  }

  fun restoreAfterBoot(context: Context) {
    val appContext = context.applicationContext
    ensureLoaded(appContext)
    val state = _stateFlow.value
    when (state.status) {
      TaskTimerStatus.RUNNING -> {
        val now = System.currentTimeMillis()
        if (state.endAtEpochMillis > now + 1_000L) {
          scheduleExpirationAlarm(appContext, state.endAtEpochMillis)
          scheduleInProcessExpiration(appContext, state.endAtEpochMillis)
          TaskNotificationHelper(appContext).showTaskTimerNotification(state)
        } else {
          onTimerExpired(appContext)
        }
      }

      TaskTimerStatus.PAUSED -> {
        TaskNotificationHelper(appContext).showTaskTimerNotification(state)
      }

      else -> Unit
    }
    TasksWidgetUpdater.updateAllWidgets(appContext)
  }

  private fun syncInProcessSchedule(appContext: Context, state: TaskTimerState) {
    if (state.status == TaskTimerStatus.RUNNING && state.endAtEpochMillis > 0L) {
      val now = System.currentTimeMillis()
      if (now >= state.endAtEpochMillis) {
        onTimerExpired(appContext)
      } else {
        scheduleInProcessExpiration(appContext, state.endAtEpochMillis)
      }
    }
  }

  private fun scheduleInProcessExpiration(appContext: Context, triggerAtMillis: Long) {
    cancelInProcessExpiration()
    val delayMs = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
    val runnable = Runnable {
      onTimerExpired(appContext)
    }
    inProcessExpireRunnable = runnable
    mainHandler.postDelayed(runnable, delayMs)
  }

  private fun cancelInProcessExpiration() {
    inProcessExpireRunnable?.let { mainHandler.removeCallbacks(it) }
    inProcessExpireRunnable = null
  }

  private fun scheduleExpirationAlarm(appContext: Context, triggerAtMillis: Long) {
    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val pi = buildExpirePendingIntent(appContext)
    val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      alarmManager.canScheduleExactAlarms()
    } else {
      true
    }
    try {
      if (canExact) {
        val showIntent = Intent(appContext, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
          putExtra(TaskNotificationHelper.EXTRA_OPEN_TASK_TIMER, true)
        }
        val showPi = PendingIntent.getActivity(
          appContext,
          88422,
          showIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showPi), pi)
        return
      }
    } catch (_: Exception) {
    }
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (canExact) {
          alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
          alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
      } else {
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
      }
    } catch (_: Exception) {
      try {
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
      } catch (_: Exception) {
      }
    }
  }

  private fun cancelExpirationAlarm(appContext: Context) {
    try {
      val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
      alarmManager.cancel(buildExpirePendingIntent(appContext))
    } catch (_: Exception) {
    }
  }

  private fun buildExpirePendingIntent(appContext: Context): PendingIntent {
    val intent = Intent(appContext, TaskNotificationReceiver::class.java).apply {
      action = TaskNotificationHelper.ACTION_TIMER_EXPIRED
    }
    return PendingIntent.getBroadcast(
      appContext,
      88420,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  fun formatCountdown(millis: Long): String {
    val totalSeconds = ((millis.coerceAtLeast(0L) + 999L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
      String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
      String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
  }
}
