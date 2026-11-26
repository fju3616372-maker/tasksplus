package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyProductivityPoint(
  val dayStartMillis: Long,
  val shortLabel: String,
  val fullDateLabel: String,
  val completedCount: Int,
  val pendingCount: Int,
  val isToday: Boolean
) {
  val totalCount: Int
    get() = completedCount + pendingCount

  val completionPercent: Int
    get() = if (totalCount == 0) 0 else ((completedCount * 100f) / totalCount).toInt().coerceIn(0, 100)
}

data class DailyProductivitySummary(
  val days: List<DailyProductivityPoint>,
  val todayCompleted: Int,
  val todayPending: Int,
  val totalCompletedInWindow: Int,
  val totalPendingInWindow: Int,
  val upcomingPendingCount: Int
) {
  val todayTotal: Int
    get() = todayCompleted + todayPending

  val todayCompletionPercent: Int
    get() = if (todayTotal == 0) 0 else ((todayCompleted * 100f) / todayTotal).toInt().coerceIn(0, 100)

  val maxBarValue: Int
    get() = days.maxOfOrNull { maxOf(it.completedCount, it.pendingCount) }?.coerceAtLeast(1) ?: 1
}

/**
 * Aggregates real completed and pending [TaskEntity] records into a local-calendar daily series
 * for the Daily Productivity Dashboard bar chart and Home Screen AppWidget.
 * Uses [Calendar] for full Android 6.0 (API 23) compatibility.
 */
object ProductivityStatsCalculator {

  fun computeDailySummary(
    activeTasks: List<TaskEntity>,
    completedTasks: List<TaskEntity>,
    dayCount: Int = 7,
    nowMillis: Long = System.currentTimeMillis()
  ): DailyProductivitySummary {
    val safeDayCount = dayCount.coerceIn(1, 14)
    val todayStart = startOfLocalDayMillis(nowMillis)
    val shortFmt = SimpleDateFormat("EEE", Locale.getDefault())
    val fullFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    val dayStarts = ArrayList<Long>(safeDayCount)
    val cal = Calendar.getInstance().apply { timeInMillis = todayStart }
    cal.add(Calendar.DAY_OF_YEAR, -(safeDayCount - 1))
    for (i in 0 until safeDayCount) {
      dayStarts.add(cal.timeInMillis)
      cal.add(Calendar.DAY_OF_YEAR, 1)
    }

    val completedByDay = IntArray(safeDayCount)
    val pendingByDay = IntArray(safeDayCount)
    var upcomingPending = 0

    val windowStart = dayStarts.first()
    val tomorrowStart = Calendar.getInstance().apply {
      timeInMillis = todayStart
      add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    fun bucketIndexForMillis(epochMs: Long): Int {
      if (epochMs <= 0L) return safeDayCount - 1
      val targetDayStart = startOfLocalDayMillis(epochMs)
      for (i in 0 until safeDayCount) {
        if (dayStarts[i] == targetDayStart) return i
      }
      return -1
    }

    // Bucket completed tasks by completion timestamp (falling back to updatedAtMillis)
    for (task in completedTasks) {
      val ts = when {
        task.completedAtMillis > 0L -> task.completedAtMillis
        task.updatedAtMillis > 0L -> task.updatedAtMillis
        else -> nowMillis
      }
      val idx = bucketIndexForMillis(ts)
      if (idx in 0 until safeDayCount) {
        completedByDay[idx]++
      }
    }

    // Bucket pending (active) tasks by due date / scheduled date; overdue tasks count toward Today
    for (task in activeTasks) {
      if (task.isCompleted) continue
      val ts = when {
        task.maxCompletionMillis > 0L -> task.maxCompletionMillis
        task.scheduledAtMillis > 0L -> task.scheduledAtMillis
        task.createdAtMillis > 0L -> task.createdAtMillis
        else -> nowMillis
      }
      when {
        ts < windowStart -> {
          // Overdue pending task is still actionable today
          pendingByDay[safeDayCount - 1]++
        }
        ts >= tomorrowStart -> {
          upcomingPending++
        }
        else -> {
          val idx = bucketIndexForMillis(ts)
          if (idx in 0 until safeDayCount) {
            pendingByDay[idx]++
          } else {
            pendingByDay[safeDayCount - 1]++
          }
        }
      }
    }

    val points = dayStarts.mapIndexed { index, dayStart ->
      val isToday = index == safeDayCount - 1
      val date = Date(dayStart)
      DailyProductivityPoint(
        dayStartMillis = dayStart,
        shortLabel = if (isToday) "Today" else shortFmt.format(date),
        fullDateLabel = if (isToday) "Today (${fullFmt.format(date)})" else fullFmt.format(date),
        completedCount = completedByDay[index],
        pendingCount = pendingByDay[index],
        isToday = isToday
      )
    }

    val todayPoint = points.last()
    return DailyProductivitySummary(
      days = points,
      todayCompleted = todayPoint.completedCount,
      todayPending = todayPoint.pendingCount,
      totalCompletedInWindow = completedByDay.sum(),
      totalPendingInWindow = pendingByDay.sum(),
      upcomingPendingCount = upcomingPending
    )
  }

  private fun startOfLocalDayMillis(epochMillis: Long): Long {
    val cal = Calendar.getInstance().apply {
      timeInMillis = epochMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
  }
}
