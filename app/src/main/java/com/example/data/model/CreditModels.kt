package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.abs
import kotlin.math.floor

data class CreditRules(
  val enabled: Boolean = false,
  val earlyPointsPerSecond: Double = 1.0,
  val latePenaltyPerSecond: Double = 1.0,
  val gracePeriodSeconds: Long = 0L,
  val maxCreditPerTask: Long = 0L,
  val maxPenaltyPerTask: Long = 0L,
  val allowNegativeBalance: Boolean = true
) {
  fun normalized(): CreditRules = copy(
    earlyPointsPerSecond = earlyPointsPerSecond.coerceIn(0.0, 1000.0),
    latePenaltyPerSecond = latePenaltyPerSecond.coerceIn(0.0, 1000.0),
    gracePeriodSeconds = gracePeriodSeconds.coerceIn(0L, 86_400L),
    maxCreditPerTask = maxCreditPerTask.coerceIn(0L, 1_000_000_000L),
    maxPenaltyPerTask = maxPenaltyPerTask.coerceIn(0L, 1_000_000_000L)
  )
}

data class CreditCalculationResult(
  val pointsDelta: Long,
  val secondsDelta: Long,
  val isEarly: Boolean,
  val isLate: Boolean
)

@IgnoreExtraProperties
data class CloudCreditTransaction(
  @DocumentId val id: String = "",
  val userId: String = "",
  val userName: String = "",
  val groupId: String = "",
  val taskId: String = "",
  val taskTitle: String = "",
  val pointsDelta: Long = 0L,
  val secondsDelta: Long = 0L,
  val reason: String = "",
  val awardedByUid: String = "",
  val awardedByName: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

object CreditCalculator {

  fun calculateTaskCredit(
    deadlineMillis: Long,
    completedAtMillis: Long,
    rules: CreditRules
  ): CreditCalculationResult {
    val cleanRules = rules.normalized()
    if (!cleanRules.enabled || deadlineMillis <= 0L || completedAtMillis <= 0L) {
      return CreditCalculationResult(
        pointsDelta = 0L,
        secondsDelta = 0L,
        isEarly = false,
        isLate = false
      )
    }

    val diffMillis = deadlineMillis - completedAtMillis
    val grace = cleanRules.gracePeriodSeconds

    return when {
      diffMillis > 0L -> {
        val savedSeconds = diffMillis / 1000L
        val effectiveSeconds = (savedSeconds - grace).coerceAtLeast(0L)
        if (effectiveSeconds <= 0L || cleanRules.earlyPointsPerSecond <= 0.0) {
          CreditCalculationResult(
            pointsDelta = 0L,
            secondsDelta = savedSeconds,
            isEarly = false,
            isLate = false
          )
        } else {
          val rawPoints = floor(effectiveSeconds.toDouble() * cleanRules.earlyPointsPerSecond).toLong()
          val cappedPoints = if (cleanRules.maxCreditPerTask > 0L) {
            minOf(rawPoints, cleanRules.maxCreditPerTask)
          } else {
            rawPoints
          }
          CreditCalculationResult(
            pointsDelta = cappedPoints,
            secondsDelta = savedSeconds,
            isEarly = cappedPoints > 0L,
            isLate = false
          )
        }
      }

      diffMillis < 0L -> {
        val lateSeconds = (-diffMillis) / 1000L
        val effectiveSeconds = (lateSeconds - grace).coerceAtLeast(0L)
        if (effectiveSeconds <= 0L || cleanRules.latePenaltyPerSecond <= 0.0) {
          CreditCalculationResult(
            pointsDelta = 0L,
            secondsDelta = -lateSeconds,
            isEarly = false,
            isLate = false
          )
        } else {
          val rawPenalty = floor(effectiveSeconds.toDouble() * cleanRules.latePenaltyPerSecond).toLong()
          val cappedPenalty = if (cleanRules.maxPenaltyPerTask > 0L) {
            minOf(rawPenalty, cleanRules.maxPenaltyPerTask)
          } else {
            rawPenalty
          }
          CreditCalculationResult(
            pointsDelta = -cappedPenalty,
            secondsDelta = -lateSeconds,
            isEarly = false,
            isLate = cappedPenalty > 0L
          )
        }
      }

      else -> CreditCalculationResult(
        pointsDelta = 0L,
        secondsDelta = 0L,
        isEarly = false,
        isLate = false
      )
    }
  }

  fun applyBalanceDelta(
    currentBalance: Long,
    delta: Long,
    allowNegativeBalance: Boolean
  ): Long {
    val updated = currentBalance + delta
    return if (allowNegativeBalance) updated else updated.coerceAtLeast(0L)
  }

  fun formatSignedPoints(points: Long): String {
    return when {
      points > 0L -> "+$points pts"
      points < 0L -> "$points pts"
      else -> "0 pts"
    }
  }

  fun formatRate(rate: Double): String {
    return if (rate % 1.0 == 0.0) {
      rate.toLong().toString()
    } else {
      String.format(java.util.Locale.US, "%.2f", rate).trimEnd('0').trimEnd('.')
    }
  }

  fun formatDurationSeconds(totalSecondsAbs: Long): String {
    val secs = abs(totalSecondsAbs)
    if (secs < 60L) return "${secs}s"
    val minsTotal = secs / 60L
    val remSecs = secs % 60L
    if (minsTotal < 60L) {
      return if (remSecs > 0L) "${minsTotal}m ${remSecs}s" else "${minsTotal}m"
    }
    val hoursTotal = minsTotal / 60L
    val remMins = minsTotal % 60L
    if (hoursTotal < 24L) {
      return when {
        remMins > 0L && remSecs > 0L -> "${hoursTotal}h ${remMins}m ${remSecs}s"
        remMins > 0L -> "${hoursTotal}h ${remMins}m"
        else -> "${hoursTotal}h"
      }
    }
    val days = hoursTotal / 24L
    val remHours = hoursTotal % 24L
    return if (remHours > 0L) "${days}d ${remHours}h" else "${days}d"
  }

  fun buildReasonText(result: CreditCalculationResult): String {
    val dur = formatDurationSeconds(result.secondsDelta)
    return when {
      result.isEarly -> "Completed $dur before deadline"
      result.isLate -> "Completed $dur past deadline"
      result.secondsDelta == 0L -> "Completed right at deadline"
      else -> "Completed within grace window ($dur)"
    }
  }
}
