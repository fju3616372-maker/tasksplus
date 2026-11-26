package com.example

import com.example.data.model.CreditCalculator
import com.example.data.model.CreditRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditCalculatorTest {

  @Test
  fun defaultRules_areDisabledByDefault_andWhenEnabled_earlyCompletionGrantsOnePointPerSecondSaved() {
    val defaultRules = CreditRules()
    assertFalse("Credit system must be disabled by default", defaultRules.enabled)

    val deadline = 1_000_000_000L
    val completedAt = deadline - 90_000L // 90 seconds early
    val res = CreditCalculator.calculateTaskCredit(deadline, completedAt, defaultRules.copy(enabled = true))

    assertEquals(90L, res.pointsDelta)
    assertEquals(90L, res.secondsDelta)
    assertTrue(res.isEarly)
    assertFalse(res.isLate)
    assertEquals("Completed 1m 30s before deadline", CreditCalculator.buildReasonText(res))
  }

  @Test
  fun enabledDefaultRates_lateCompletion_subtractsOnePointPerSecondLate() {
    val deadline = 1_000_000_000L
    val completedAt = deadline + 45_000L // 45 seconds late
    val res = CreditCalculator.calculateTaskCredit(deadline, completedAt, CreditRules(enabled = true))

    assertEquals(-45L, res.pointsDelta)
    assertEquals(-45L, res.secondsDelta)
    assertFalse(res.isEarly)
    assertTrue(res.isLate)
    assertEquals("Completed 45s past deadline", CreditCalculator.buildReasonText(res))
  }

  @Test
  fun disabledCreditSystem_returnsZeroDelta() {
    val deadline = 1_000_000_000L
    val rules = CreditRules(enabled = false, earlyPointsPerSecond = 2.0, latePenaltyPerSecond = 2.0)

    val early = CreditCalculator.calculateTaskCredit(deadline, deadline - 120_000L, rules)
    val late = CreditCalculator.calculateTaskCredit(deadline, deadline + 120_000L, rules)

    assertEquals(0L, early.pointsDelta)
    assertEquals(0L, late.pointsDelta)
  }

  @Test
  fun customRates_gracePeriod_andCaps_applyCorrectly() {
    val deadline = 1_000_000_000L
    val rules = CreditRules(
      enabled = true,
      earlyPointsPerSecond = 2.0,
      latePenaltyPerSecond = 0.5,
      gracePeriodSeconds = 30L,
      maxCreditPerTask = 100L,
      maxPenaltyPerTask = 50L
    )

    // Within grace period -> 0 pts
    val withinGraceEarly = CreditCalculator.calculateTaskCredit(deadline, deadline - 25_000L, rules)
    assertEquals(0L, withinGraceEarly.pointsDelta)

    // 70s early -> (70 - 30) * 2 = 80 pts
    val uncappedEarly = CreditCalculator.calculateTaskCredit(deadline, deadline - 70_000L, rules)
    assertEquals(80L, uncappedEarly.pointsDelta)

    // 200s early -> (200 - 30) * 2 = 340 -> capped at 100
    val cappedEarly = CreditCalculator.calculateTaskCredit(deadline, deadline - 200_000L, rules)
    assertEquals(100L, cappedEarly.pointsDelta)

    // 90s late -> (90 - 30) * 0.5 = 30 penalty -> -30
    val uncappedLate = CreditCalculator.calculateTaskCredit(deadline, deadline + 90_000L, rules)
    assertEquals(-30L, uncappedLate.pointsDelta)

    // 300s late -> (300 - 30) * 0.5 = 135 penalty -> capped at -50
    val cappedLate = CreditCalculator.calculateTaskCredit(deadline, deadline + 300_000L, rules)
    assertEquals(-50L, cappedLate.pointsDelta)
  }

  @Test
  fun applyBalanceDelta_respectsAllowNegativeBalanceSetting() {
    assertEquals(-25L, CreditCalculator.applyBalanceDelta(10L, -35L, allowNegativeBalance = true))
    assertEquals(0L, CreditCalculator.applyBalanceDelta(10L, -35L, allowNegativeBalance = false))
    assertEquals(140L, CreditCalculator.applyBalanceDelta(100L, 40L, allowNegativeBalance = false))
  }
}
