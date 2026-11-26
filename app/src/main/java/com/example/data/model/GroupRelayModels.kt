package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class GroupRelayStatus(val label: String) {
  ACTIVE("Active Relay"),
  COMPLETED("Completed");

  companion object {
    fun fromName(value: String?): GroupRelayStatus =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ACTIVE
  }
}

@Keep
@IgnoreExtraProperties
data class CloudRelayStep(
  val stepTitle: String = "",
  val assigneeUid: String = "",
  val assigneeName: String = "",
  val durationMinutes: Long = 15L,
  val done: Boolean = false,
  val completedAtMillis: Long = 0L,
  val handoffSpeedSeconds: Long = 0L
) {
  fun toMap(): Map<String, Any> = mapOf(
    "stepTitle" to stepTitle.trim().take(120),
    "assigneeUid" to assigneeUid.trim().take(128),
    "assigneeName" to assigneeName.trim().take(120),
    "durationMinutes" to durationMinutes.coerceIn(1L, 1440L),
    "done" to done,
    "completedAtMillis" to completedAtMillis.coerceAtLeast(0L),
    "handoffSpeedSeconds" to handoffSpeedSeconds.coerceAtLeast(0L)
  )
}

@Keep
@IgnoreExtraProperties
data class CloudGroupRelay(
  @DocumentId val id: String = "",
  val groupId: String = "",
  val title: String = "",
  val description: String = "",
  val createdByUid: String = "",
  val createdByName: String = "",
  val steps: List<CloudRelayStep> = emptyList(),
  val currentStepIndex: Long = 0L,
  val currentAssigneeUid: String = "",
  val relayStatus: String = GroupRelayStatus.ACTIVE.name,
  val streakCount: Long = 0L,
  val startedAtMillis: Long = 0L,
  val lastHandoffAtMillis: Long = 0L,
  val completedAtMillis: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  val parsedStatus: GroupRelayStatus
    get() = GroupRelayStatus.fromName(relayStatus)

  val isRelayCompleted: Boolean
    get() = parsedStatus == GroupRelayStatus.COMPLETED ||
      (steps.isNotEmpty() && currentStepIndex.toInt() >= steps.size)

  val activeStep: CloudRelayStep?
    get() {
      if (isRelayCompleted) return null
      val idx = currentStepIndex.toInt()
      return steps.getOrNull(idx)
    }

  val completedStepCount: Int
    get() = steps.count { it.done }

  val progressFraction: Float
    get() = if (steps.isEmpty()) 0f else (completedStepCount.toFloat() / steps.size.toFloat()).coerceIn(0f, 1f)

  /**
   * Pure helper computing the updated relay state when the current runner completes their baton leg.
   */
  fun advanceBaton(nowMillis: Long = System.currentTimeMillis()): CloudGroupRelay {
    if (isRelayCompleted || steps.isEmpty()) return this
    val idx = currentStepIndex.toInt().coerceIn(0, steps.lastIndex)
    val referenceStart = lastHandoffAtMillis.takeIf { it > 0L } ?: startedAtMillis.takeIf { it > 0L } ?: nowMillis
    val handoffSeconds = ((nowMillis - referenceStart) / 1_000L).coerceAtLeast(1L)

    val updatedSteps = steps.mapIndexed { i, step ->
      if (i == idx) {
        step.copy(
          done = true,
          completedAtMillis = nowMillis,
          handoffSpeedSeconds = handoffSeconds
        )
      } else {
        step
      }
    }
    val nextIdx = idx + 1
    val finishedAll = nextIdx >= updatedSteps.size
    return copy(
      steps = updatedSteps,
      currentStepIndex = nextIdx.toLong(),
      currentAssigneeUid = if (finishedAll) "" else updatedSteps[nextIdx].assigneeUid,
      relayStatus = if (finishedAll) GroupRelayStatus.COMPLETED.name else GroupRelayStatus.ACTIVE.name,
      streakCount = streakCount + 1L,
      lastHandoffAtMillis = nowMillis,
      completedAtMillis = if (finishedAll) nowMillis else 0L
    )
  }
}
