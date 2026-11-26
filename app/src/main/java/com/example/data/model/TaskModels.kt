package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Calendar
import java.util.UUID

enum class RecurrenceType(val label: String) {
  NONE("One-time"),
  DAILY("Daily"),
  WEEKLY("Weekly"),
  CUSTOM_DAYS("Every N Days"),
  MONTHLY("Monthly"),
  ANNUALLY("Annually");

  companion object {
    fun fromName(value: String?): RecurrenceType =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
  }
}

enum class ThemeMode(val label: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark");

  companion object {
    fun fromName(value: String?): ThemeMode =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
  }
}

enum class TonalSpotPalette(
  val label: String,
  val primaryHex: Long,
  val secondaryHex: Long
) {
  INDIGO("Indigo Spot", 0xFF4559A9, 0xFF735472),
  EMERALD("Sage Teal", 0xFF006A60, 0xFF456179),
  TEAL("Violet Iris", 0xFF6750A4, 0xFF7D5260),
  ROSE("Coral Clay", 0xFF9C413B, 0xFF725B2E);

  companion object {
    fun fromName(value: String?): TonalSpotPalette =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: INDIGO
  }
}

enum class ScreenTimePeriod(val label: String) {
  HOURLY("Hourly"),
  DAILY("Daily"),
  WEEKLY("Weekly");

  companion object {
    fun fromName(value: String?): ScreenTimePeriod =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DAILY
  }
}

enum class TaskPriority(val label: String, val multiplier: Float, val defaultUrgencyLevel: Int) {
  LOW("Low", 1.25f, 1),
  MEDIUM("Medium", 1.0f, 3),
  HIGH("High", 0.85f, 4),
  CRITICAL("Critical", 0.5f, 5);

  companion object {
    fun fromName(value: String?): TaskPriority =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: HIGH

    fun fromUrgencyLevel(level: Int): TaskPriority = when (level.coerceIn(1, 5)) {
      1 -> LOW
      2 -> LOW
      3 -> MEDIUM
      4 -> HIGH
      else -> CRITICAL
    }
  }
}

object UrgencyLevelMeta {
  fun clamp(level: Int): Int = level.coerceIn(1, 5)

  fun shortLabel(level: Int): String = when (clamp(level)) {
    1 -> "Calm"
    2 -> "Mild"
    3 -> "Moderate"
    4 -> "High"
    else -> "Critical"
  }

  fun fullLabel(level: Int): String = "${clamp(level)}/5 • ${shortLabel(level)}"

  fun colorHex(level: Int): Long = when (clamp(level)) {
    1 -> 0xFF22A049L // Rich Green
    2 -> 0xFF7CB342L // Lime Green
    3 -> 0xFFF9A825L // Warm Amber
    4 -> 0xFFEF6C00L // Vivid Orange
    else -> 0xFFD32F2FL // Intense Red
  }

  fun containerColorHex(level: Int): Long = when (clamp(level)) {
    1 -> 0xFFDDF5E4L
    2 -> 0xFFECF6D8L
    3 -> 0xFFFFF3CDL
    4 -> 0xFFFFE5D0L
    else -> 0xFFFDE0E0L
  }

  fun onContainerColorHex(level: Int): Long = when (clamp(level)) {
    1 -> 0xFF0E5324L
    2 -> 0xFF365314L
    3 -> 0xFF7A4F01L
    4 -> 0xFF8A3400L
    else -> 0xFF8C1D18L
  }

  fun intervalMultiplier(level: Int): Float = when (clamp(level)) {
    1 -> 1.25f
    2 -> 1.1f
    3 -> 1.0f
    4 -> 0.85f
    else -> 0.5f
  }
}

@Entity(tableName = "tasks")
data class TaskEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val title: String = "",
  val subtext: String = "",
  val scheduledAtMillis: Long = System.currentTimeMillis(),
  val maxCompletionMillis: Long = System.currentTimeMillis() + 3_600_000L,
  val reminderIntervalMinutes: Int = 30,
  val recurrenceType: String = RecurrenceType.NONE.name,
  val recurrenceInterval: Int = 1,
  val recurrenceDaysCsv: String = "",
  val priority: String = TaskPriority.HIGH.name,
  val urgencyLevel: Int = 0,
  val categoryId: String = "",
  val ringAlarmBell: Boolean = false,
  val isCompleted: Boolean = false,
  val completedAtMillis: Long = 0L,
  val creditsAwarded: Long = 0L,
  val creditSecondsDelta: Long = 0L,
  val voiceContractPath: String = "",
  val createdAtMillis: Long = System.currentTimeMillis(),
  val updatedAtMillis: Long = System.currentTimeMillis()
) {
  val parsedRecurrenceType: RecurrenceType
    get() = RecurrenceType.fromName(recurrenceType)

  val parsedPriority: TaskPriority
    get() = TaskPriority.fromName(priority)

  val resolvedUrgencyLevel: Int
    get() = if (urgencyLevel in 1..5) urgencyLevel else parsedPriority.defaultUrgencyLevel

  val selectedDaysOfWeek: Set<Int>
    get() = recurrenceDaysCsv
      .split(",")
      .mapNotNull { it.trim().toIntOrNull() }
      .filter { it in 1..7 }
      .toSet()

  fun isScheduledForFutureDay(nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (parsedRecurrenceType == RecurrenceType.NONE) return false
    val startOfTomorrow = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
    return scheduledAtMillis >= startOfTomorrow
  }

  fun computeNextOccurrenceMillis(fromBaseMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val duration = (maxCompletionMillis - scheduledAtMillis).coerceAtLeast(1_800_000L)
    val origCal = Calendar.getInstance().apply { timeInMillis = scheduledAtMillis }
    val origHour = origCal.get(Calendar.HOUR_OF_DAY)
    val origMinute = origCal.get(Calendar.MINUTE)
    val anchor = maxOf(scheduledAtMillis, fromBaseMillis)
    val calendar = Calendar.getInstance().apply { timeInMillis = anchor }
    val step = recurrenceInterval.coerceIn(1, 365)

    when (parsedRecurrenceType) {
      RecurrenceType.NONE -> {
        calendar.add(Calendar.DAY_OF_YEAR, 1)
      }
      RecurrenceType.DAILY -> {
        calendar.add(Calendar.DAY_OF_YEAR, 1)
      }
      RecurrenceType.CUSTOM_DAYS -> {
        calendar.add(Calendar.DAY_OF_YEAR, step)
      }
      RecurrenceType.WEEKLY -> {
        val days = selectedDaysOfWeek
        if (days.isEmpty()) {
          calendar.add(Calendar.WEEK_OF_YEAR, step)
        } else {
          var found = false
          for (offset in 1..14) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val isoDay = when (calendar.get(Calendar.DAY_OF_WEEK)) {
              Calendar.MONDAY -> 1
              Calendar.TUESDAY -> 2
              Calendar.WEDNESDAY -> 3
              Calendar.THURSDAY -> 4
              Calendar.FRIDAY -> 5
              Calendar.SATURDAY -> 6
              Calendar.SUNDAY -> 7
              else -> 1
            }
            if (isoDay in days) {
              found = true
              break
            }
          }
          if (!found) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
          }
        }
      }
      RecurrenceType.MONTHLY -> {
        calendar.add(Calendar.MONTH, step)
      }
      RecurrenceType.ANNUALLY -> {
        calendar.add(Calendar.YEAR, step)
      }
    }
    calendar.set(Calendar.HOUR_OF_DAY, origHour)
    calendar.set(Calendar.MINUTE, origMinute)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val nextStart = calendar.timeInMillis
    return nextStart to (nextStart + duration)
  }
}

@Entity(tableName = "screen_time_limits")
data class ScreenTimeLimitEntity(
  @PrimaryKey val packageName: String,
  val appName: String = "",
  val limitMinutes: Int = 60,
  val usedMinutes: Int = 0,
  val periodType: String = ScreenTimePeriod.DAILY.name,
  val isEnabled: Boolean = true,
  val isGroupEnforced: Boolean = false,
  val enforcedByName: String = "",
  val requiredGroupTaskId: String = "",
  val requiredGroupTaskTitle: String = "",
  val lockMode: String = RemoteLockMode.USAGE_LIMIT.name,
  val lockAtMillis: Long = 0L,
  val taskInstructions: String = "",
  val playBeepOnLock: Boolean = false,
  val assignedAtMillis: Long = 0L,
  val baselineStatsMinutes: Int = 0,
  val updatedAtMillis: Long = System.currentTimeMillis()
) {
  val parsedPeriod: ScreenTimePeriod
    get() = ScreenTimePeriod.fromName(periodType)

  val parsedLockMode: RemoteLockMode
    get() = RemoteLockMode.fromName(lockMode)

  fun isLimitReachedAt(nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (!isEnabled) return false
    return when (parsedLockMode) {
      RemoteLockMode.LOCK_NOW -> true
      RemoteLockMode.LOCK_AT_TIME -> lockAtMillis > 0L && nowMillis >= lockAtMillis
      RemoteLockMode.USAGE_LIMIT -> limitMinutes > 0 && usedMinutes >= limitMinutes
    }
  }

  val isLimitReached: Boolean
    get() = isLimitReachedAt(System.currentTimeMillis())

  val remainingMinutes: Int
    get() = if (parsedLockMode == RemoteLockMode.LOCK_NOW) {
      0
    } else {
      (limitMinutes - usedMinutes).coerceAtLeast(0)
    }

  val firestoreDocId: String
    get() = packageName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "pkg_default" }
}

data class InstalledAppInfo(
  val packageName: String,
  val appName: String,
  val categoryHint: String = "App"
)

@IgnoreExtraProperties
data class CloudTask(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val subtext: String = "",
  val scheduledAtMillis: Long = 0L,
  val maxCompletionMillis: Long = 0L,
  val reminderIntervalMinutes: Long = 0L,
  val recurrenceType: String = "NONE",
  val recurrenceInterval: Long = 1L,
  val recurrenceDaysCsv: String = "",
  val priority: String = "HIGH",
  val urgencyLevel: Long = 0L,
  val categoryId: String = "",
  val ringAlarmBell: Boolean = false,
  @get:PropertyName("isCompleted")
  @field:PropertyName("isCompleted")
  val isCompleted: Boolean = false,
  val completedAtMillis: Long = 0L,
  val creditsAwarded: Long = 0L,
  val creditSecondsDelta: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  fun toEntity(): TaskEntity {
    val createdMs = createdAt?.toDate()?.time ?: System.currentTimeMillis()
    val updatedMs = updatedAt?.toDate()?.time ?: createdMs
    val parsedPrio = TaskPriority.fromName(priority)
    val resolvedUrgency = urgencyLevel.toInt().let { if (it in 1..5) it else parsedPrio.defaultUrgencyLevel }
    val resolvedId = id.ifBlank { UUID.randomUUID().toString() }
    val resolvedCompleted = isCompleted || resolvedId.startsWith("done_") || completedAtMillis > 0L
    val resolvedCompletedAt = if (resolvedCompleted) {
      completedAtMillis.takeIf { it > 0L } ?: updatedMs
    } else {
      0L
    }
    return TaskEntity(
      id = resolvedId,
      title = title,
      subtext = subtext,
      scheduledAtMillis = scheduledAtMillis,
      maxCompletionMillis = maxCompletionMillis,
      reminderIntervalMinutes = reminderIntervalMinutes.toInt(),
      recurrenceType = if (resolvedId.startsWith("done_")) RecurrenceType.NONE.name else recurrenceType,
      recurrenceInterval = recurrenceInterval.toInt().coerceIn(1, 365),
      recurrenceDaysCsv = recurrenceDaysCsv,
      priority = parsedPrio.name,
      urgencyLevel = resolvedUrgency,
      categoryId = categoryId.trim().take(64),
      ringAlarmBell = ringAlarmBell,
      isCompleted = resolvedCompleted,
      completedAtMillis = resolvedCompletedAt,
      creditsAwarded = creditsAwarded,
      creditSecondsDelta = creditSecondsDelta,
      createdAtMillis = createdMs,
      updatedAtMillis = updatedMs
    )
  }

  companion object {
    fun fromSnapshot(doc: DocumentSnapshot): CloudTask {
      val behavior = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
      val docId = doc.id
      val completedFlag = doc.getBoolean("isCompleted")
        ?: doc.getBoolean("completed")
        ?: false
      val completedAt = doc.getLong("completedAtMillis") ?: 0L
      val resolvedCompleted = completedFlag || docId.startsWith("done_") || completedAt > 0L
      return CloudTask(
        id = docId,
        userId = doc.getString("userId").orEmpty(),
        title = doc.getString("title").orEmpty(),
        subtext = doc.getString("subtext").orEmpty(),
        scheduledAtMillis = doc.getLong("scheduledAtMillis") ?: 0L,
        maxCompletionMillis = doc.getLong("maxCompletionMillis") ?: 0L,
        reminderIntervalMinutes = doc.getLong("reminderIntervalMinutes") ?: 0L,
        recurrenceType = doc.getString("recurrenceType") ?: RecurrenceType.NONE.name,
        recurrenceInterval = doc.getLong("recurrenceInterval") ?: 1L,
        recurrenceDaysCsv = doc.getString("recurrenceDaysCsv").orEmpty(),
        priority = doc.getString("priority") ?: TaskPriority.HIGH.name,
        urgencyLevel = doc.getLong("urgencyLevel") ?: 0L,
        categoryId = doc.getString("categoryId").orEmpty(),
        ringAlarmBell = doc.getBoolean("ringAlarmBell") ?: false,
        isCompleted = resolvedCompleted,
        completedAtMillis = completedAt,
        creditsAwarded = doc.getLong("creditsAwarded") ?: 0L,
        creditSecondsDelta = doc.getLong("creditSecondsDelta") ?: 0L,
        createdAt = doc.getTimestamp("createdAt", behavior),
        updatedAt = doc.getTimestamp("updatedAt", behavior)
      )
    }
  }
}

@IgnoreExtraProperties
data class CloudUserPreferences(
  @DocumentId val id: String = "",
  val userId: String = "",
  val themeMode: String = "SYSTEM",
  val useDynamicColor: Boolean = true,
  val tonalSpotPalette: String = "INDIGO",
  val defaultReminderMinutes: Long = 30L,
  val notificationsEnabled: Boolean = true,
  val autoDeleteUpdateApk: Boolean = true,
  val creditSystemEnabled: Boolean = false,
  val personalCredits: Long = 0L,
  val earlyPointsPerSecond: Double = 1.0,
  val latePenaltyPerSecond: Double = 1.0,
  val creditGracePeriodSeconds: Long = 0L,
  val maxCreditPerTask: Long = 0L,
  val maxPenaltyPerTask: Long = 0L,
  val allowNegativeCredits: Boolean = true,
  val categoriesEnabled: Boolean = true,
  val disabledPremadeCategoryIds: List<String> = emptyList(),
  val customCategoriesJson: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  val creditRules: CreditRules
    get() = CreditRules(
      enabled = creditSystemEnabled,
      earlyPointsPerSecond = earlyPointsPerSecond,
      latePenaltyPerSecond = latePenaltyPerSecond,
      gracePeriodSeconds = creditGracePeriodSeconds,
      maxCreditPerTask = maxCreditPerTask,
      maxPenaltyPerTask = maxPenaltyPerTask,
      allowNegativeBalance = allowNegativeCredits
    ).normalized()
}

@IgnoreExtraProperties
data class CloudScreenTimeLimit(
  @DocumentId val id: String = "",
  val userId: String = "",
  val packageName: String = "",
  val appName: String = "",
  val limitMinutes: Long = 60L,
  val usedMinutes: Long = 0L,
  val periodType: String = "DAILY",
  @get:PropertyName("isEnabled")
  @field:PropertyName("isEnabled")
  val isEnabled: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  fun toEntity(): ScreenTimeLimitEntity {
    val updatedMs = updatedAt?.toDate()?.time ?: createdAt?.toDate()?.time ?: System.currentTimeMillis()
    return ScreenTimeLimitEntity(
      packageName = packageName.ifBlank { id },
      appName = appName.ifBlank { packageName },
      limitMinutes = limitMinutes.toInt().coerceIn(1, 10080),
      usedMinutes = usedMinutes.toInt().coerceIn(0, 10080),
      periodType = ScreenTimePeriod.fromName(periodType).name,
      isEnabled = isEnabled,
      updatedAtMillis = updatedMs
    )
  }

  companion object {
    fun fromSnapshot(doc: DocumentSnapshot): CloudScreenTimeLimit {
      val behavior = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
      val enabledFlag = doc.getBoolean("isEnabled")
        ?: doc.getBoolean("enabled")
        ?: true
      return CloudScreenTimeLimit(
        id = doc.id,
        userId = doc.getString("userId").orEmpty(),
        packageName = doc.getString("packageName").orEmpty(),
        appName = doc.getString("appName").orEmpty(),
        limitMinutes = doc.getLong("limitMinutes") ?: 60L,
        usedMinutes = doc.getLong("usedMinutes") ?: 0L,
        periodType = doc.getString("periodType") ?: "DAILY",
        isEnabled = enabledFlag,
        createdAt = doc.getTimestamp("createdAt", behavior),
        updatedAt = doc.getTimestamp("updatedAt", behavior)
      )
    }
  }
}

data class UserPreferencesState(
  val hasCompletedSetupWizard: Boolean = false,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val useDynamicColor: Boolean = true,
  val tonalSpotPalette: TonalSpotPalette = TonalSpotPalette.INDIGO,
  val defaultReminderMinutes: Int = 30,
  val notificationsEnabled: Boolean = true,
  val autoDeleteUpdateApk: Boolean = true,
  val lastSyncedAtMillis: Long = 0L,
  val creditSystemEnabled: Boolean = false,
  val personalCredits: Long = 0L,
  val earlyPointsPerSecond: Double = 1.0,
  val latePenaltyPerSecond: Double = 1.0,
  val creditGracePeriodSeconds: Long = 0L,
  val maxCreditPerTask: Long = 0L,
  val maxPenaltyPerTask: Long = 0L,
  val allowNegativeCredits: Boolean = true,
  val showProductivityDashboard: Boolean = false,
  val categoriesEnabled: Boolean = true,
  val disabledPremadeCategoryIds: Set<String> = emptySet(),
  val customCategories: List<TaskCategory> = emptyList(),
  val earnYourScrollEnabled: Boolean = false,
  val earnedScreenTimeMinutes: Int = 0,
  val totalEarnedScreenTimeMinutes: Int = 0,
  val earnMinutesBase: Int = 5,
  val focusAuraStreakCount: Int = 0
) {
  val activeCategories: List<TaskCategory>
    get() = TaskCategoryCatalog.activeCategories(disabledPremadeCategoryIds, customCategories)

  val creditRules: CreditRules
    get() = CreditRules(
      enabled = creditSystemEnabled,
      earlyPointsPerSecond = earlyPointsPerSecond,
      latePenaltyPerSecond = latePenaltyPerSecond,
      gracePeriodSeconds = creditGracePeriodSeconds,
      maxCreditPerTask = maxCreditPerTask,
      maxPenaltyPerTask = maxPenaltyPerTask,
      allowNegativeBalance = allowNegativeCredits
    ).normalized()
}
