package com.example.screentime

import kotlin.math.roundToInt

/**
 * Pure calculator for "Earn Your Scroll" (Earned Screen-Time Wallet).
 * Higher urgency tasks mint proportionally more mindful screen-time minutes.
 */
object EarnedScreenTimeCalculator {

  fun minutesEarnedForUrgency(urgencyLevel: Int, baseMinutes: Int = 5): Int {
    val safeBase = baseMinutes.coerceIn(2, 20)
    val multiplier = when (urgencyLevel.coerceIn(1, 5)) {
      1 -> 1.0f
      2 -> 1.5f
      3 -> 2.2f
      4 -> 3.2f
      else -> 4.5f
    }
    return (safeBase * multiplier).roundToInt().coerceAtLeast(2)
  }

  fun minutesEarnedForGroupRelayStep(baseMinutes: Int = 5): Int {
    val safeBase = baseMinutes.coerceIn(2, 20)
    return (safeBase * 2.0f).roundToInt().coerceAtLeast(5)
  }

  fun rewardBreakdownLabels(baseMinutes: Int = 5): List<Pair<Int, Int>> {
    return (1..5).map { level -> level to minutesEarnedForUrgency(level, baseMinutes) }
  }
}
