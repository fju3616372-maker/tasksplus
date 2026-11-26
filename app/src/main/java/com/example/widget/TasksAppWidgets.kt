package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.MainActivity
import com.example.R
import com.example.data.local.TaskDatabase
import com.example.data.model.ProductivityStatsCalculator
import com.example.data.model.RecurrenceType
import com.example.data.model.TaskEntity
import com.example.data.remote.CloudSyncRepository
import com.example.notifications.TaskNotificationHelper
import com.example.notifications.TaskReminderManager
import com.example.notifications.TaskTimerManager
import com.example.notifications.TaskTimerState
import com.example.notifications.TaskTimerStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object TasksWidgetUpdater {
  private const val PREFS_NAME = "tasks_widget_state"
  private const val KEY_SMALL_INDEX_PREFIX = "small_idx_"

  fun getSmallWidgetIndex(context: Context, appWidgetId: Int): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt("$KEY_SMALL_INDEX_PREFIX$appWidgetId", 0).coerceAtLeast(0)
  }

  fun setSmallWidgetIndex(context: Context, appWidgetId: Int, index: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt("$KEY_SMALL_INDEX_PREFIX$appWidgetId", index.coerceAtLeast(0)).apply()
  }

  fun updateAllWidgets(context: Context) {
    val appContext = context.applicationContext
    val manager = AppWidgetManager.getInstance(appContext)

    // Update Small widgets
    val smallComponent = ComponentName(appContext, TasksSmallWidgetProvider::class.java)
    val smallIds = manager.getAppWidgetIds(smallComponent)
    if (smallIds.isNotEmpty()) {
      TasksSmallWidgetProvider.updateWidgets(appContext, manager, smallIds)
    }

    // Update Large 4x4 widgets
    val largeComponent = ComponentName(appContext, TasksLargeWidgetProvider::class.java)
    val largeIds = manager.getAppWidgetIds(largeComponent)
    if (largeIds.isNotEmpty()) {
      manager.notifyAppWidgetViewDataChanged(largeIds, R.id.widget_large_list)
      TasksLargeWidgetProvider.updateWidgets(appContext, manager, largeIds)
    }

    // Update dedicated Countdown Timer widgets
    val timerComponent = ComponentName(appContext, TasksTimerWidgetProvider::class.java)
    val timerIds = manager.getAppWidgetIds(timerComponent)
    if (timerIds.isNotEmpty()) {
      TasksTimerWidgetProvider.updateWidgets(appContext, manager, timerIds)
    }

    // Update optional Daily Productivity Bar Chart widgets
    val productivityComponent = ComponentName(appContext, TasksProductivityWidgetProvider::class.java)
    val productivityIds = manager.getAppWidgetIds(productivityComponent)
    if (productivityIds.isNotEmpty()) {
      TasksProductivityWidgetProvider.updateWidgets(appContext, manager, productivityIds)
    }
  }

  internal fun buildOpenTimerPendingIntent(context: Context, requestCode: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(TaskNotificationHelper.EXTRA_OPEN_TASK_TIMER, true)
    }
    return PendingIntent.getActivity(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  /**
   * Binds a live native countdown [Chronometer] on API 24+ when RUNNING, with an API 23-safe
   * static [TextView] fallback and clear paused/expired/completed/idle text.
   */
  internal fun bindCountdownDisplay(
    views: RemoteViews,
    chronometerViewId: Int,
    fallbackTextViewId: Int,
    timerState: TaskTimerState
  ) {
    val now = System.currentTimeMillis()
    val remainingMs = timerState.remainingMillisAt(now)
    val formatted = TaskTimerManager.formatCountdown(remainingMs)

    if (timerState.status == TaskTimerStatus.RUNNING &&
      remainingMs > 0L &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    ) {
      val base = SystemClock.elapsedRealtime() + remainingMs
      views.setViewVisibility(chronometerViewId, View.VISIBLE)
      views.setViewVisibility(fallbackTextViewId, View.GONE)
      views.setChronometerCountDown(chronometerViewId, true)
      views.setChronometer(chronometerViewId, base, "%s", true)
    } else {
      views.setViewVisibility(chronometerViewId, View.GONE)
      views.setViewVisibility(fallbackTextViewId, View.VISIBLE)
      val displayText = when (timerState.status) {
        TaskTimerStatus.RUNNING -> formatted
        TaskTimerStatus.PAUSED -> "⏸ $formatted"
        TaskTimerStatus.EXPIRED -> "00:00"
        TaskTimerStatus.COMPLETED -> "✓ $formatted"
        TaskTimerStatus.IDLE -> TaskTimerManager.formatCountdown(timerState.defaultDurationMinutes * 60_000L)
      }
      views.setTextViewText(fallbackTextViewId, displayText)
    }
  }
}

class TasksSmallWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    updateWidgets(context, appWidgetManager, appWidgetIds)
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    val action = intent.action ?: return
    val appWidgetId = intent.getIntExtra(
      AppWidgetManager.EXTRA_APPWIDGET_ID,
      AppWidgetManager.INVALID_APPWIDGET_ID
    )

    when (action) {
      ACTION_PREV_TASK, ACTION_NEXT_TASK, ACTION_COMPLETE_CURRENT -> {
        val pendingResult = goAsync()
        scope.launch {
          try {
            val db = TaskDatabase.getInstance(context)
            val active = db.taskDao().getActiveTasksOnce().filter { !it.isScheduledForFutureDay() }
            if (active.isNotEmpty() && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
              val currentIndex = TasksWidgetUpdater.getSmallWidgetIndex(context, appWidgetId)
                .coerceIn(0, active.lastIndex)
              when (action) {
                ACTION_PREV_TASK -> {
                  val prev = if (currentIndex - 1 < 0) active.lastIndex else currentIndex - 1
                  TasksWidgetUpdater.setSmallWidgetIndex(context, appWidgetId, prev)
                }

                ACTION_NEXT_TASK -> {
                  val next = (currentIndex + 1) % active.size
                  TasksWidgetUpdater.setSmallWidgetIndex(context, appWidgetId, next)
                }

                ACTION_COMPLETE_CURRENT -> {
                  val targetTask = active[currentIndex]
                  completeTaskFromWidget(context, targetTask)
                }
              }
            }
            TasksWidgetUpdater.updateAllWidgets(context)
          } finally {
            pendingResult.finish()
          }
        }
      }
    }
  }

  companion object {
    const val ACTION_PREV_TASK = "com.example.tasksplus.widget.ACTION_PREV_TASK"
    const val ACTION_NEXT_TASK = "com.example.tasksplus.widget.ACTION_NEXT_TASK"
    const val ACTION_COMPLETE_CURRENT = "com.example.tasksplus.widget.ACTION_COMPLETE_CURRENT"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray
    ) {
      scope.launch {
        val activeTasks = TaskDatabase.getInstance(context).taskDao().getActiveTasksOnce()
          .filter { !it.isScheduledForFutureDay() }
        val timerState = TaskTimerManager.getCurrentState(context)
        val widgetTheme = WidgetThemeHelper.resolveForContext(context)
        val dateFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        for (widgetId in appWidgetIds) {
          val views = RemoteViews(context.packageName, R.layout.widget_tasks_small)
          widgetTheme.applySurfaceBackground(views, R.id.widget_small_root)
          widgetTheme.applyPillBackground(views, R.id.widget_small_counter)
          widgetTheme.applyCardBackground(views, R.id.widget_small_card)
          widgetTheme.applyCardBackground(views, R.id.widget_small_timer_bar)
          widgetTheme.applyCardBackground(views, R.id.widget_small_prev_btn)
          widgetTheme.applyPillBackground(views, R.id.widget_small_done_btn)
          widgetTheme.applyCardBackground(views, R.id.widget_small_next_btn)

          views.setTextColor(R.id.widget_small_header, widgetTheme.accentColorInt)
          views.setTextColor(R.id.widget_small_counter, widgetTheme.onPillColorInt)
          views.setTextColor(R.id.widget_small_task_title, widgetTheme.onSurfaceColorInt)
          views.setTextColor(R.id.widget_small_task_meta, widgetTheme.onSurfaceVariantColorInt)
          views.setTextColor(R.id.widget_small_timer_label, widgetTheme.accentColorInt)
          views.setTextColor(R.id.widget_small_timer_chronometer, widgetTheme.onSurfaceColorInt)
          views.setTextColor(R.id.widget_small_timer_text, widgetTheme.onSurfaceColorInt)
          views.setTextColor(R.id.widget_small_prev_btn, widgetTheme.onSurfaceColorInt)
          views.setTextColor(R.id.widget_small_done_btn, widgetTheme.onPillColorInt)
          views.setTextColor(R.id.widget_small_next_btn, widgetTheme.onSurfaceColorInt)

          val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
          }
          val openAppPi = PendingIntent.getActivity(
            context,
            widgetId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
          )
          views.setOnClickPendingIntent(R.id.widget_small_card, openAppPi)
          views.setOnClickPendingIntent(R.id.widget_small_header, openAppPi)

          // Live Task Timer strip inside small widget
          val smallTimerLabel = when (timerState.status) {
            TaskTimerStatus.RUNNING -> "⏱ ${timerState.effectiveLabel()}"
            TaskTimerStatus.PAUSED -> "⏸ ${timerState.effectiveLabel()}"
            TaskTimerStatus.EXPIRED -> "⏰ Time's Up!"
            TaskTimerStatus.COMPLETED -> "✓ Sprint Done"
            TaskTimerStatus.IDLE -> "⏱ Task Timer"
          }
          views.setTextViewText(R.id.widget_small_timer_label, smallTimerLabel)
          TasksWidgetUpdater.bindCountdownDisplay(
            views = views,
            chronometerViewId = R.id.widget_small_timer_chronometer,
            fallbackTextViewId = R.id.widget_small_timer_text,
            timerState = timerState
          )
          views.setOnClickPendingIntent(
            R.id.widget_small_timer_bar,
            TasksWidgetUpdater.buildOpenTimerPendingIntent(context, 7000 + widgetId)
          )

          if (activeTasks.isEmpty()) {
            views.setTextViewText(R.id.widget_small_counter, "0 / 0")
            views.setTextViewText(R.id.widget_small_task_title, "All tasks complete!")
            views.setTextViewText(R.id.widget_small_task_meta, "Tap to add a new task in Tasks+")
          } else {
            val safeIndex = TasksWidgetUpdater.getSmallWidgetIndex(context, widgetId)
              .coerceIn(0, activeTasks.lastIndex)
            TasksWidgetUpdater.setSmallWidgetIndex(context, widgetId, safeIndex)
            val task = activeTasks[safeIndex]
            views.setTextViewText(R.id.widget_small_counter, "${safeIndex + 1} / ${activeTasks.size}")
            views.setTextViewText(R.id.widget_small_task_title, task.title)
            val intervalLabel = if (task.reminderIntervalMinutes > 0) {
              "Every ${TaskReminderManager.formatIntervalShort(task.reminderIntervalMinutes)}"
            } else {
              "Alerts Off"
            }
            views.setTextViewText(
              R.id.widget_small_task_meta,
              "Due ${dateFmt.format(Date(task.maxCompletionMillis))} • $intervalLabel"
            )
          }

          views.setOnClickPendingIntent(
            R.id.widget_small_prev_btn,
            buildBroadcastPendingIntent(context, widgetId, ACTION_PREV_TASK, 1000 + widgetId)
          )
          views.setOnClickPendingIntent(
            R.id.widget_small_next_btn,
            buildBroadcastPendingIntent(context, widgetId, ACTION_NEXT_TASK, 2000 + widgetId)
          )
          views.setOnClickPendingIntent(
            R.id.widget_small_done_btn,
            buildBroadcastPendingIntent(context, widgetId, ACTION_COMPLETE_CURRENT, 3000 + widgetId)
          )

          appWidgetManager.updateAppWidget(widgetId, views)
        }
      }
    }

    private fun buildBroadcastPendingIntent(
      context: Context,
      appWidgetId: Int,
      action: String,
      requestCode: Int
    ): PendingIntent {
      val intent = Intent(context, TasksSmallWidgetProvider::class.java).apply {
        this.action = action
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
      }
      return PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
    }

    private suspend fun completeTaskFromWidget(context: Context, task: TaskEntity) {
      val now = System.currentTimeMillis()
      val dao = TaskDatabase.getInstance(context).taskDao()
      val latest = dao.getTaskById(task.id) ?: return
      if (latest.isCompleted) return
      if (latest.parsedRecurrenceType != RecurrenceType.NONE &&
        (latest.updatedAtMillis > task.updatedAtMillis || latest.maxCompletionMillis != task.maxCompletionMillis)
      ) {
        return
      }
      TaskReminderManager.cancelTaskReminder(context, latest.id)

      val cloudRepo = try {
        if (Firebase.auth.currentUser != null) CloudSyncRepository(context) else null
      } catch (_: Exception) {
        null
      }

      if (latest.parsedRecurrenceType != RecurrenceType.NONE) {
        val completedCopy = latest.copy(
          id = "done_${UUID.randomUUID().toString().replace("-", "_")}",
          isCompleted = true,
          completedAtMillis = now,
          recurrenceType = RecurrenceType.NONE.name,
          updatedAtMillis = now
        )
        val (nextStart, nextMax) = latest.computeNextOccurrenceMillis(now)
        val nextTask = latest.copy(
          scheduledAtMillis = nextStart,
          maxCompletionMillis = nextMax,
          isCompleted = false,
          completedAtMillis = 0L,
          updatedAtMillis = now
        )
        dao.upsertTask(completedCopy)
        dao.upsertTask(nextTask)
        TaskReminderManager.scheduleTaskReminder(context, nextTask, true)
        cloudRepo?.upsertCloudTask(completedCopy)
        cloudRepo?.upsertCloudTask(nextTask)
      } else {
        val completedTask = latest.copy(
          isCompleted = true,
          completedAtMillis = now,
          updatedAtMillis = now
        )
        dao.upsertTask(completedTask)
        cloudRepo?.upsertCloudTask(completedTask)
      }
      val remainingCount = dao.getActiveTasksOnce().count { !it.isScheduledForFutureDay() }
      TaskTimerManager.onTaskCompleted(context, latest.id, remainingCount)
    }
  }
}

class TasksLargeWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    updateWidgets(context, appWidgetManager, appWidgetIds)
  }

  companion object {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray
    ) {
      scope.launch {
        val activeCount = TaskDatabase.getInstance(context).taskDao().getActiveTasksOnce()
          .count { !it.isScheduledForFutureDay() }
        val timerState = TaskTimerManager.getCurrentState(context)
        val widgetTheme = WidgetThemeHelper.resolveForContext(context)
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

        for (widgetId in appWidgetIds) {
          val views = RemoteViews(context.packageName, R.layout.widget_tasks_large)
          widgetTheme.applySurfaceBackground(views, R.id.widget_large_root)
          widgetTheme.applyPillBackground(views, R.id.widget_large_add_btn)
          widgetTheme.applyCardBackground(views, R.id.widget_large_timer_bar)
          widgetTheme.applyPillBackground(views, R.id.widget_large_timer_chronometer)
          widgetTheme.applyPillBackground(views, R.id.widget_large_timer_text)
          widgetTheme.applyCardBackground(views, R.id.widget_large_empty)

          views.setTextColor(R.id.widget_large_title, widgetTheme.onSurfaceColorInt)
          views.setTextColor(R.id.widget_large_subtitle, widgetTheme.onSurfaceVariantColorInt)
          views.setTextColor(R.id.widget_large_add_btn, widgetTheme.onPillColorInt)
          views.setTextColor(R.id.widget_large_timer_title, widgetTheme.onSurfaceColorInt)
          views.setTextColor(R.id.widget_large_timer_sub, widgetTheme.onSurfaceVariantColorInt)
          views.setTextColor(R.id.widget_large_timer_chronometer, widgetTheme.onPillColorInt)
          views.setTextColor(R.id.widget_large_timer_text, widgetTheme.onPillColorInt)
          views.setTextColor(R.id.widget_large_empty, widgetTheme.onSurfaceVariantColorInt)

          views.setTextViewText(R.id.widget_large_title, "Tasks+ To Do ($activeCount)")

          // Live Task Timer banner in 4x4 widget
          val timerTitle = when (timerState.status) {
            TaskTimerStatus.RUNNING -> "⏱ ${timerState.effectiveLabel()}"
            TaskTimerStatus.PAUSED -> "⏸ Paused • ${timerState.effectiveLabel()}"
            TaskTimerStatus.EXPIRED -> "⏰ Time's Up • ${timerState.effectiveLabel()}"
            TaskTimerStatus.COMPLETED -> "✓ Sprint Complete!"
            TaskTimerStatus.IDLE -> "⏱ Task Countdown Timer"
          }
          val timerSub = when (timerState.status) {
            TaskTimerStatus.RUNNING -> {
              val endStr = if (timerState.endAtEpochMillis > 0L) {
                "Ends ${timeFmt.format(Date(timerState.endAtEpochMillis))}"
              } else {
                "Running"
              }
              if (timerState.totalTargetCount > 1) {
                "$endStr • ${timerState.completedTargetCount}/${timerState.totalTargetCount} done"
              } else {
                "$endStr • Tap to manage"
              }
            }

            TaskTimerStatus.PAUSED -> "Tap to resume or add +5m"
            TaskTimerStatus.EXPIRED -> "Tap to add +5m or finish tasks"
            TaskTimerStatus.COMPLETED -> "Finished ahead of schedule!"
            TaskTimerStatus.IDLE -> "Tap to set a countdown for tasks"
          }
          views.setTextViewText(R.id.widget_large_timer_title, timerTitle)
          views.setTextViewText(R.id.widget_large_timer_sub, timerSub)
          TasksWidgetUpdater.bindCountdownDisplay(
            views = views,
            chronometerViewId = R.id.widget_large_timer_chronometer,
            fallbackTextViewId = R.id.widget_large_timer_text,
            timerState = timerState
          )
          views.setOnClickPendingIntent(
            R.id.widget_large_timer_bar,
            TasksWidgetUpdater.buildOpenTimerPendingIntent(context, 8000 + widgetId)
          )

          val serviceIntent = Intent(context, TasksWidgetRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
          }
          views.setRemoteAdapter(R.id.widget_large_list, serviceIntent)
          views.setEmptyView(R.id.widget_large_list, R.id.widget_large_empty)

          val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
          }
          val openAppPi = PendingIntent.getActivity(
            context,
            4000 + widgetId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
          )
          views.setOnClickPendingIntent(R.id.widget_large_add_btn, openAppPi)
          views.setOnClickPendingIntent(R.id.widget_large_title, openAppPi)
          views.setPendingIntentTemplate(R.id.widget_large_list, openAppPi)

          appWidgetManager.updateAppWidget(widgetId, views)
        }
      }
    }
  }
}

class TasksTimerWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    updateWidgets(context, appWidgetManager, appWidgetIds)
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    val action = intent.action ?: return
    when (action) {
      ACTION_TIMER_TOGGLE, ACTION_TIMER_ADD_5M, ACTION_TIMER_RESET -> {
        val pendingResult = goAsync()
        scope.launch {
          try {
            val state = TaskTimerManager.getCurrentState(context)
            when (action) {
              ACTION_TIMER_TOGGLE -> {
                when (state.status) {
                  TaskTimerStatus.RUNNING -> TaskTimerManager.pauseTimer(context)
                  TaskTimerStatus.PAUSED -> TaskTimerManager.resumeTimer(context)
                  TaskTimerStatus.IDLE, TaskTimerStatus.COMPLETED, TaskTimerStatus.EXPIRED -> {
                    val activeTasks = TaskDatabase.getInstance(context).taskDao().getActiveTasksOnce()
                    TaskTimerManager.startQuickDefaultTimer(context, activeTasks)
                  }
                }
              }

              ACTION_TIMER_ADD_5M -> {
                if (state.status == TaskTimerStatus.IDLE || state.status == TaskTimerStatus.COMPLETED) {
                  val activeTasks = TaskDatabase.getInstance(context).taskDao().getActiveTasksOnce()
                  TaskTimerManager.startTimer(context, 5 * 60_000L, activeTasks.take(1))
                } else {
                  TaskTimerManager.addMinutes(context, 5)
                }
              }

              ACTION_TIMER_RESET -> {
                TaskTimerManager.resetTimer(context)
              }
            }
          } finally {
            pendingResult.finish()
          }
        }
      }
    }
  }

  companion object {
    const val ACTION_TIMER_TOGGLE = "com.example.tasksplus.widget.ACTION_TIMER_TOGGLE"
    const val ACTION_TIMER_ADD_5M = "com.example.tasksplus.widget.ACTION_TIMER_ADD_5M"
    const val ACTION_TIMER_RESET = "com.example.tasksplus.widget.ACTION_TIMER_RESET"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray
    ) {
      scope.launch {
        val timerState = TaskTimerManager.getCurrentState(context)
        val widgetTheme = WidgetThemeHelper.resolveForContext(context)
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

        for (widgetId in appWidgetIds) {
          val views = RemoteViews(context.packageName, R.layout.widget_task_timer)
          widgetTheme.applySurfaceBackground(views, R.id.widget_timer_root)
          widgetTheme.applyPillBackground(views, R.id.widget_timer_status_badge)
          widgetTheme.applyCardBackground(views, R.id.widget_timer_center_card)
          widgetTheme.applyPillBackground(views, R.id.widget_timer_toggle_btn)
          widgetTheme.applyCardBackground(views, R.id.widget_timer_add5_btn)
          widgetTheme.applyCardBackground(views, R.id.widget_timer_reset_btn)

          views.setTextColor(R.id.widget_timer_app_label, widgetTheme.accentColorInt)
          views.setTextColor(R.id.widget_timer_status_badge, widgetTheme.onPillColorInt)
          views.setTextColor(R.id.widget_timer_main_chronometer, widgetTheme.onSurfaceColorInt)
          views.setTextColor(R.id.widget_timer_main_text, widgetTheme.onSurfaceColorInt)
          views.setTextColor(R.id.widget_timer_task_target, widgetTheme.onSurfaceVariantColorInt)
          views.setTextColor(R.id.widget_timer_toggle_btn, widgetTheme.onPillColorInt)
          views.setTextColor(R.id.widget_timer_add5_btn, widgetTheme.onSurfaceColorInt)
          views.setTextColor(R.id.widget_timer_reset_btn, widgetTheme.onSurfaceColorInt)

          val statusLabel = when (timerState.status) {
            TaskTimerStatus.RUNNING -> "LIVE"
            TaskTimerStatus.PAUSED -> "PAUSED"
            TaskTimerStatus.EXPIRED -> "TIME'S UP"
            TaskTimerStatus.COMPLETED -> "DONE"
            TaskTimerStatus.IDLE -> "READY"
          }
          views.setTextViewText(R.id.widget_timer_status_badge, statusLabel)

          TasksWidgetUpdater.bindCountdownDisplay(
            views = views,
            chronometerViewId = R.id.widget_timer_main_chronometer,
            fallbackTextViewId = R.id.widget_timer_main_text,
            timerState = timerState
          )

          val targetSubtitle = when (timerState.status) {
            TaskTimerStatus.RUNNING -> {
              val endStr = if (timerState.endAtEpochMillis > 0L) {
                "Ends ${timeFmt.format(Date(timerState.endAtEpochMillis))}"
              } else {
                "Running"
              }
              "${timerState.effectiveLabel()} • $endStr"
            }

            TaskTimerStatus.PAUSED -> "Paused • ${timerState.effectiveLabel()}"
            TaskTimerStatus.EXPIRED -> "Time's up for ${timerState.effectiveLabel()}"
            TaskTimerStatus.COMPLETED -> "Completed ${timerState.effectiveLabel()}!"
            TaskTimerStatus.IDLE -> "Tap Start or open Settings → Task Timer"
          }
          views.setTextViewText(R.id.widget_timer_task_target, targetSubtitle)

          val toggleText = when (timerState.status) {
            TaskTimerStatus.RUNNING -> "⏸ Pause"
            TaskTimerStatus.PAUSED -> "▶ Resume"
            else -> "▶ Start"
          }
          views.setTextViewText(R.id.widget_timer_toggle_btn, toggleText)

          val openTimerPi = TasksWidgetUpdater.buildOpenTimerPendingIntent(context, 9000 + widgetId)
          views.setOnClickPendingIntent(R.id.widget_timer_header, openTimerPi)
          views.setOnClickPendingIntent(R.id.widget_timer_center_card, openTimerPi)

          views.setOnClickPendingIntent(
            R.id.widget_timer_toggle_btn,
            buildTimerWidgetBroadcastPi(context, ACTION_TIMER_TOGGLE, 9100 + widgetId)
          )
          views.setOnClickPendingIntent(
            R.id.widget_timer_add5_btn,
            buildTimerWidgetBroadcastPi(context, ACTION_TIMER_ADD_5M, 9200 + widgetId)
          )
          views.setOnClickPendingIntent(
            R.id.widget_timer_reset_btn,
            buildTimerWidgetBroadcastPi(context, ACTION_TIMER_RESET, 9300 + widgetId)
          )

          appWidgetManager.updateAppWidget(widgetId, views)
        }
      }
    }

    private fun buildTimerWidgetBroadcastPi(
      context: Context,
      action: String,
      requestCode: Int
    ): PendingIntent {
      val intent = Intent(context, TasksTimerWidgetProvider::class.java).apply {
        this.action = action
      }
      return PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
    }
  }
}

class TasksProductivityWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    updateWidgets(context, appWidgetManager, appWidgetIds)
  }

  companion object {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray
    ) {
      scope.launch {
        val allTasks = TaskDatabase.getInstance(context).taskDao().getAllTasksOnce()
        val (completedTasks, activeTasks) = allTasks.partition { it.isCompleted }
        val widgetTheme = WidgetThemeHelper.resolveForContext(context)
        val summary = ProductivityStatsCalculator.computeDailySummary(
          activeTasks = activeTasks,
          completedTasks = completedTasks,
          dayCount = 7
        )
        val chartBitmap = ProductivityChartBitmapRenderer.renderChartBitmap(
          summary = summary,
          widthPx = 720,
          heightPx = 300,
          gridColor = widgetTheme.chartGridColorInt,
          baselineColor = widgetTheme.chartBaselineColorInt,
          axisTextColor = widgetTheme.chartAxisTextColorInt,
          todayTextColor = widgetTheme.chartTodayTextColorInt
        )

        val windowTotal = summary.totalCompletedInWindow + summary.totalPendingInWindow
        val windowPct = if (windowTotal == 0) {
          0
        } else {
          ((summary.totalCompletedInWindow * 100f) / windowTotal).toInt().coerceIn(0, 100)
        }
        val footerText = buildString {
          append("Today: ${summary.todayCompleted} done • ${summary.todayPending} pending")
          if (summary.upcomingPendingCount > 0) {
            append(" • +${summary.upcomingPendingCount} upcoming")
          }
        }

        for (widgetId in appWidgetIds) {
          val views = RemoteViews(context.packageName, R.layout.widget_productivity_dashboard)
          widgetTheme.applySurfaceBackground(views, R.id.widget_prod_root)
          widgetTheme.applyPillBackground(views, R.id.widget_prod_rate_badge)
          widgetTheme.applyCardBackground(views, R.id.widget_prod_chart_card)

          views.setTextColor(R.id.widget_prod_title, widgetTheme.onSurfaceColorInt)
          views.setTextColor(R.id.widget_prod_rate_badge, widgetTheme.onPillColorInt)
          views.setTextColor(R.id.widget_prod_done_count, widgetTheme.completedLegendColorInt)
          views.setTextColor(R.id.widget_prod_pending_count, widgetTheme.pendingLegendColorInt)
          views.setTextColor(R.id.widget_prod_footer, widgetTheme.onSurfaceVariantColorInt)

          views.setTextViewText(R.id.widget_prod_rate_badge, "$windowPct% done")
          views.setTextViewText(
            R.id.widget_prod_done_count,
            "● Completed: ${summary.totalCompletedInWindow}"
          )
          views.setTextViewText(
            R.id.widget_prod_pending_count,
            "● Pending: ${summary.totalPendingInWindow}"
          )
          views.setImageViewBitmap(R.id.widget_prod_chart_image, chartBitmap)
          views.setTextViewText(R.id.widget_prod_footer, footerText)

          val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
          }
          val openAppPi = PendingIntent.getActivity(
            context,
            9600 + widgetId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
          )
          views.setOnClickPendingIntent(R.id.widget_prod_root, openAppPi)

          appWidgetManager.updateAppWidget(widgetId, views)
        }
      }
    }
  }
}

class TasksWidgetRemoteViewsService : RemoteViewsService() {
  override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
    return TasksRemoteViewsFactory(applicationContext)
  }
}

private class TasksRemoteViewsFactory(
  private val context: Context
) : RemoteViewsService.RemoteViewsFactory {
  private var tasks: List<TaskEntity> = emptyList()
  private var widgetTheme: WidgetThemePalette? = null
  private val dateFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

  override fun onCreate() {
    loadTasksSynchronously()
  }

  override fun onDataSetChanged() {
    loadTasksSynchronously()
  }

  private fun loadTasksSynchronously() {
    runBlocking(Dispatchers.IO) {
      tasks = TaskDatabase.getInstance(context).taskDao().getActiveTasksOnce()
        .filter { !it.isScheduledForFutureDay() }
      widgetTheme = WidgetThemeHelper.resolveForContext(context)
    }
  }

  override fun onDestroy() {
    tasks = emptyList()
  }

  override fun getCount(): Int = tasks.size

  override fun getViewAt(position: Int): RemoteViews? {
    if (position !in tasks.indices) return null
    val task = tasks[position]
    val row = RemoteViews(context.packageName, R.layout.widget_task_item)
    val theme = widgetTheme ?: WidgetThemeHelper.fromPreferences(
      context,
      com.example.data.local.PreferencesRepository(context).getInitialBootPreferences()
    )
    theme.applyCardBackground(row, R.id.widget_item_row)
    theme.applyPillBackground(row, R.id.widget_item_badge)
    row.setTextColor(R.id.widget_item_title, theme.onSurfaceColorInt)
    row.setTextColor(R.id.widget_item_subtitle, theme.onSurfaceVariantColorInt)
    row.setTextColor(R.id.widget_item_badge, theme.onPillColorInt)

    row.setTextViewText(R.id.widget_item_title, task.title)

    val intervalLabel = if (task.reminderIntervalMinutes > 0) {
      "Every ${TaskReminderManager.formatIntervalShort(task.reminderIntervalMinutes)}"
    } else {
      "Alerts Off"
    }
    val sub = if (task.subtext.isNotBlank()) "${task.subtext} • " else ""
    row.setTextViewText(
      R.id.widget_item_subtitle,
      "${sub}Due ${dateFmt.format(Date(task.maxCompletionMillis))} • $intervalLabel"
    )
    row.setTextViewText(
      R.id.widget_item_badge,
      if (task.parsedRecurrenceType != RecurrenceType.NONE) task.parsedRecurrenceType.label else "Active"
    )

    val fillInIntent = Intent()
    row.setOnClickFillInIntent(R.id.widget_item_row, fillInIntent)
    return row
  }

  override fun getLoadingView(): RemoteViews? = null

  override fun getViewTypeCount(): Int = 1

  override fun getItemId(position: Int): Long =
    tasks.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

  override fun hasStableIds(): Boolean = true
}
