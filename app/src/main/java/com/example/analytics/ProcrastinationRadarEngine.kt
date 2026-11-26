package com.example.analytics

import com.example.data.model.CloudTaskRecord
import com.example.data.model.TaskCategory
import com.example.data.model.TaskEntity
import com.example.data.model.TaskRecordType
import java.util.Calendar
import kotlin.math.roundToInt

enum class DayTimeWindow(
  val label: String,
  val shortLabel: String,
  val recommendedHour: Int,
  val hourRangeLabel: String
) {
  EARLY_MORNING("Early Morning", "5–9 AM", 7, "5:00 AM – 9:00 AM"),
  MORNING("Morning", "9–12 PM", 10, "9:00 AM – 12:00 PM"),
  AFTERNOON("Afternoon", "12–5 PM", 14, "12:00 PM – 5:00 PM"),
  EVENING("Evening", "5–9 PM", 18, "5:00 PM – 9:00 PM"),
  NIGHT("Late Night", "9 PM–5 AM", 21, "9:00 PM – 5:00 AM");

  companion object {
    fun fromHourOfDay(hour: Int): DayTimeWindow = when (hour) {
      in 5..8 -> EARLY_MORNING
      in 9..11 -> MORNING
      in 12..16 -> AFTERNOON
      in 17..20 -> EVENING
      else -> NIGHT
    }
  }
}

data class TimeWindowStat(
  val window: DayTimeWindow,
  val totalTasks: Int,
  val completedOnTime: Int,
  val delayedOrOverdue: Int,
  val onTimeRatePercent: Int,
  val avgDelayMinutes: Int
)

data class ScheduleRiskAdvice(
  val warningTitle: String,
  val warningBody: String,
  val suggestedWindow: DayTimeWindow,
  val suggestedStartMillis: Long
)

data class ProcrastinationRadarReport(
  val sampleSize: Int,
  val overallOnTimePercent: Int,
  val windowStats: List<TimeWindowStat>,
  val peakWindow: TimeWindowStat?,
  val dangerWindow: TimeWindowStat?,
  val highestFrictionCategoryName: String?,
  val highestFrictionUrgency: Int?,
  val summaryNarrative: String
) {
  val hasEnoughHistory: Boolean get() = sampleSize >= 2
}

/**
 * Private on-device analytics engine that examines the user's real completed tasks, overdue active tasks,
 * and private Task Records Library timestamps to surface peak performance slots and procrastination zones.
 */
object ProcrastinationRadarEngine {

  fun analyze(
    activeTasks: List<TaskEntity>,
    completedTasks: List<TaskEntity>,
    records: List<CloudTaskRecord> = emptyList(),
    categories: List<TaskCategory> = emptyList(),
    nowMillis: Long = System.currentTimeMillis()
  ): ProcrastinationRadarReport {
    val categoryLookup = categories.associateBy { it.id }

    data class Observation(
      val window: DayTimeWindow,
      val onTime: Boolean,
      val delayMinutes: Int,
      val categoryId: String,
      val urgencyLevel: Int
    )

    val observations = mutableListOf<Observation>()

    completedTasks.forEach { task ->
      val anchorTime = task.scheduledAtMillis.takeIf { it > 0L } ?: task.createdAtMillis
      val hour = hourOfDay(anchorTime)
      val completedAt = task.completedAtMillis.takeIf { it > 0L } ?: task.updatedAtMillis
      val deadline = task.maxCompletionMillis
      val delayMin = if (deadline > 0L && completedAt > deadline) {
        ((completedAt - deadline) / 60_000L).toInt().coerceAtLeast(1)
      } else {
        0
      }
      observations.add(
        Observation(
          window = DayTimeWindow.fromHourOfDay(hour),
          onTime = delayMin == 0,
          delayMinutes = delayMin,
          categoryId = task.categoryId,
          urgencyLevel = task.resolvedUrgencyLevel
        )
      )
    }

    // Include currently overdue active tasks as procrastination friction signals
    activeTasks.filter { it.maxCompletionMillis in 1L until nowMillis }.forEach { overdue ->
      val anchorTime = overdue.scheduledAtMillis.takeIf { it > 0L } ?: overdue.createdAtMillis
      val delayMin = ((nowMillis - overdue.maxCompletionMillis) / 60_000L).toInt().coerceAtLeast(1)
      observations.add(
        Observation(
          window = DayTimeWindow.fromHourOfDay(hourOfDay(anchorTime)),
          onTime = false,
          delayMinutes = delayMin.coerceAtMost(1440),
          categoryId = overdue.categoryId,
          urgencyLevel = overdue.resolvedUrgencyLevel
        )
      )
    }

    // Supplement with completed personal/group records from the private library if local Room history is sparse
    if (observations.size < 4 && records.isNotEmpty()) {
      records.filter {
        it.parsedType == TaskRecordType.DONE
      }.forEach { rec ->
        val ts = rec.resolvedEventTimeMillis().takeIf { it > 0L } ?: return@forEach
        val onTime = rec.pointsReceived >= 0L
        observations.add(
          Observation(
            window = DayTimeWindow.fromHourOfDay(hourOfDay(ts)),
            onTime = onTime,
            delayMinutes = if (onTime) 0 else 20,
            categoryId = "",
            urgencyLevel = 3
          )
        )
      }
    }

    val stats = DayTimeWindow.entries.map { window ->
      val matching = observations.filter { it.window == window }
      val total = matching.size
      val onTimeCount = matching.count { it.onTime }
      val delayedCount = total - onTimeCount
      val rate = if (total > 0) ((onTimeCount * 100.0) / total).roundToInt() else 0
      val avgDelay = if (delayedCount > 0) {
        matching.filter { !it.onTime }.map { it.delayMinutes }.average().roundToInt()
      } else {
        0
      }
      TimeWindowStat(
        window = window,
        totalTasks = total,
        completedOnTime = onTimeCount,
        delayedOrOverdue = delayedCount,
        onTimeRatePercent = rate,
        avgDelayMinutes = avgDelay
      )
    }

    val populated = stats.filter { it.totalTasks > 0 }
    val peak = populated.maxWithOrNull(
      compareBy<TimeWindowStat> { it.onTimeRatePercent }.thenBy { it.completedOnTime }
    )
    val danger = populated
      .filter { it.delayedOrOverdue > 0 && it.window != peak?.window }
      .minWithOrNull(
        compareBy<TimeWindowStat> { it.onTimeRatePercent }.thenByDescending { it.avgDelayMinutes }
      )
      ?: populated
        .filter { it.delayedOrOverdue > 0 }
        .minWithOrNull(compareBy<TimeWindowStat> { it.onTimeRatePercent }.thenByDescending { it.avgDelayMinutes })

    val delayedObs = observations.filter { !it.onTime }
    val topFrictionCategory = delayedObs
      .filter { it.categoryId.isNotBlank() }
      .groupingBy { it.categoryId }
      .eachCount()
      .maxByOrNull { it.value }
      ?.key
      ?.let { id -> categoryLookup[id]?.name ?: id }

    val topFrictionUrgency = delayedObs
      .groupingBy { it.urgencyLevel }
      .eachCount()
      .maxByOrNull { it.value }
      ?.key

    val totalObs = observations.size
    val totalOnTime = observations.count { it.onTime }
    val overallRate = if (totalObs > 0) ((totalOnTime * 100.0) / totalObs).roundToInt() else 100

    val narrative = when {
      totalObs < 2 ->
        "Complete a couple of tasks and your private Radar will map your peak momentum hours and procrastination danger zones."
      peak != null && danger != null && peak.window != danger.window ->
        "You shine during ${peak.window.label} (${peak.onTimeRatePercent}% on-time), while ${danger.window.label} tasks slip more often (avg +${danger.avgDelayMinutes}m delay)."
      peak != null ->
        "Strong rhythm! Your peak completion window is ${peak.window.label} with a ${peak.onTimeRatePercent}% on-time rate."
      else ->
        "Your private productivity rhythm is calibrating from $totalObs recent tasks."
    }

    return ProcrastinationRadarReport(
      sampleSize = totalObs,
      overallOnTimePercent = overallRate,
      windowStats = stats,
      peakWindow = peak,
      dangerWindow = danger,
      highestFrictionCategoryName = topFrictionCategory,
      highestFrictionUrgency = topFrictionUrgency,
      summaryNarrative = narrative
    )
  }

  /**
   * Evaluates whether a proposed scheduled start time falls inside the user's Procrastination Danger Zone,
   * and if so recommends shifting to their Peak Window.
   */
  fun evaluateScheduleRisk(
    scheduledMillis: Long,
    report: ProcrastinationRadarReport,
    nowMillis: Long = System.currentTimeMillis()
  ): ScheduleRiskAdvice? {
    if (!report.hasEnoughHistory) return null
    val danger = report.dangerWindow ?: return null
    val peak = report.peakWindow ?: return null
    if (danger.window == peak.window) return null
    if (danger.onTimeRatePercent > 65 && danger.delayedOrOverdue < 2) return null

    val scheduledWindow = DayTimeWindow.fromHourOfDay(hourOfDay(scheduledMillis))
    if (scheduledWindow != danger.window) return null

    val suggestedStart = computeNextSlotForWindow(scheduledMillis, peak.window, nowMillis)
    return ScheduleRiskAdvice(
      warningTitle = "Procrastination Radar: ${danger.window.label} Caution",
      warningBody = "Historically only ${danger.onTimeRatePercent}% of your ${danger.window.label} tasks finish on time. Your peak slot is ${peak.window.label} (${peak.onTimeRatePercent}% on-time).",
      suggestedWindow = peak.window,
      suggestedStartMillis = suggestedStart
    )
  }

  private fun computeNextSlotForWindow(
    referenceMillis: Long,
    targetWindow: DayTimeWindow,
    nowMillis: Long
  ): Long {
    val cal = Calendar.getInstance().apply {
      timeInMillis = maxOf(referenceMillis, nowMillis)
      set(Calendar.HOUR_OF_DAY, targetWindow.recommendedHour)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= nowMillis + 5 * 60_000L) {
      cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return cal.timeInMillis
  }

  private fun hourOfDay(millis: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
  }
}
