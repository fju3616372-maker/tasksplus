package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TaskRecordType(
  val label: String,
  val filterLabel: String,
  val timePrefix: String
) {
  ENTERED(
    label = "Entered",
    filterLabel = "Entered",
    timePrefix = "Entered"
  ),
  DONE(
    label = "Done",
    filterLabel = "Done",
    timePrefix = "Finished"
  ),
  GIVEN(
    label = "Given",
    filterLabel = "Given",
    timePrefix = "Given"
  ),
  POINTS_RECEIVED(
    label = "Points",
    filterLabel = "Points",
    timePrefix = "Awarded"
  );

  companion object {
    fun fromName(name: String?): TaskRecordType {
      return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ENTERED
    }
  }
}

enum class TaskRecordScope(val label: String) {
  PERSONAL("Personal"),
  FAMILY("Family Group"),
  WORKPLACE("Workplace Group");

  companion object {
    fun fromName(name: String?): TaskRecordScope {
      return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: PERSONAL
    }

    fun fromGroupType(groupType: GroupType): TaskRecordScope {
      return when (groupType) {
        GroupType.FAMILY -> FAMILY
        GroupType.ORGANIZATION -> WORKPLACE
      }
    }
  }
}

enum class TaskRecordFilter(val label: String, val tag: String) {
  ALL("All", "records_filter_all"),
  ENTERED("Entered", "records_filter_entered"),
  DONE("Done", "records_filter_done"),
  GIVEN("Given", "records_filter_given"),
  POINTS("Points", "records_filter_points")
}

@Keep
@IgnoreExtraProperties
data class CloudTaskRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val recordType: String = TaskRecordType.ENTERED.name,
  val sourceScope: String = TaskRecordScope.PERSONAL.name,
  val taskId: String = "",
  val taskTitle: String = "",
  val taskSubtext: String = "",
  val groupId: String = "",
  val groupName: String = "",
  val counterpartyName: String = "",
  val pointsReceived: Long = 0L,
  val hasPoints: Boolean = false,
  val pointsReason: String = "",
  val eventTimeMillis: Long = 0L,
  val dueAtMillis: Long = 0L,
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null
) {
  val parsedType: TaskRecordType
    get() = TaskRecordType.fromName(recordType)

  val parsedScope: TaskRecordScope
    get() = TaskRecordScope.fromName(sourceScope)

  fun resolvedEventTimeMillis(): Long {
    return when {
      eventTimeMillis > 0L -> eventTimeMillis
      createdAt != null -> createdAt.toDate().time
      updatedAt != null -> updatedAt.toDate().time
      else -> 0L
    }
  }

  fun formattedScopeLabel(): String {
    return when (parsedScope) {
      TaskRecordScope.PERSONAL -> "Personal"
      TaskRecordScope.FAMILY -> if (groupName.isNotBlank()) "Family • $groupName" else "Family Group"
      TaskRecordScope.WORKPLACE -> if (groupName.isNotBlank()) "Workplace • $groupName" else "Workplace Group"
    }
  }
}

data class TaskRecordsSummary(
  val totalCount: Int,
  val enteredCount: Int,
  val doneCount: Int,
  val givenCount: Int,
  val pointsEventCount: Int,
  val netTaskPoints: Long
)

object TaskRecordsLibraryHelper {

  /**
   * Deduplicates standalone POINTS_RECEIVED records when a matching DONE record for the same
   * group task already carries the points delta, preventing duplicate cards in "All" and "Points".
   */
  fun deduplicateForDisplay(records: List<CloudTaskRecord>): List<CloudTaskRecord> {
    val doneTaskIdsWithPoints = records
      .filter { it.parsedType == TaskRecordType.DONE && it.hasPoints && it.taskId.isNotBlank() }
      .map { "${it.groupId}::${it.taskId}" }
      .toSet()

    return records
      .filterNot { record ->
        record.parsedType == TaskRecordType.POINTS_RECEIVED &&
          record.taskId.isNotBlank() &&
          "${record.groupId}::${record.taskId}" in doneTaskIdsWithPoints
      }
      .sortedByDescending { it.resolvedEventTimeMillis() }
  }

  fun filterRecords(
    records: List<CloudTaskRecord>,
    filter: TaskRecordFilter,
    query: String = ""
  ): List<CloudTaskRecord> {
    val normalized = deduplicateForDisplay(records)
    val byFilter = when (filter) {
      TaskRecordFilter.ALL -> normalized
      TaskRecordFilter.ENTERED -> normalized.filter { it.parsedType == TaskRecordType.ENTERED }
      TaskRecordFilter.DONE -> normalized.filter { it.parsedType == TaskRecordType.DONE }
      TaskRecordFilter.GIVEN -> normalized.filter { it.parsedType == TaskRecordType.GIVEN }
      TaskRecordFilter.POINTS -> normalized.filter {
        it.parsedType == TaskRecordType.POINTS_RECEIVED ||
          (it.parsedType == TaskRecordType.DONE && it.hasPoints)
      }
    }

    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return byFilter

    return byFilter.filter { rec ->
      rec.taskTitle.contains(cleanQuery, ignoreCase = true) ||
        rec.taskSubtext.contains(cleanQuery, ignoreCase = true) ||
        rec.groupName.contains(cleanQuery, ignoreCase = true) ||
        rec.counterpartyName.contains(cleanQuery, ignoreCase = true) ||
        rec.pointsReason.contains(cleanQuery, ignoreCase = true) ||
        rec.formattedScopeLabel().contains(cleanQuery, ignoreCase = true)
    }
  }

  fun summarize(records: List<CloudTaskRecord>): TaskRecordsSummary {
    val normalized = deduplicateForDisplay(records)
    val entered = normalized.count { it.parsedType == TaskRecordType.ENTERED }
    val done = normalized.count { it.parsedType == TaskRecordType.DONE }
    val given = normalized.count { it.parsedType == TaskRecordType.GIVEN }
    val pointsEvents = normalized.filter {
      it.parsedType == TaskRecordType.POINTS_RECEIVED ||
        (it.parsedType == TaskRecordType.DONE && it.hasPoints)
    }
    val netPoints = pointsEvents.sumOf { it.pointsReceived }

    return TaskRecordsSummary(
      totalCount = normalized.size,
      enteredCount = entered,
      doneCount = done,
      givenCount = given,
      pointsEventCount = pointsEvents.size,
      netTaskPoints = netPoints
    )
  }

  fun formatRecordTimestamp(epochMillis: Long, locale: Locale = Locale.getDefault()): String {
    if (epochMillis <= 0L) return "Time recorded"
    val formatter = SimpleDateFormat("MMM d, yyyy • h:mm a", locale)
    return formatter.format(Date(epochMillis))
  }
}
