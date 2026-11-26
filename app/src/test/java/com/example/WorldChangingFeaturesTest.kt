package com.example

import com.example.analytics.DayTimeWindow
import com.example.analytics.ProcrastinationRadarEngine
import com.example.data.model.CloudGroupRelay
import com.example.data.model.CloudRelayStep
import com.example.data.model.GroupRelayStatus
import com.example.data.model.TaskEntity
import com.example.screentime.EarnedScreenTimeCalculator
import com.example.sensors.FocusAuraSensorEngine
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldChangingFeaturesTest {

  @Test
  fun earnedScreenTimeCalculator_scalesWithUrgencyAndBaseMinutes() {
    val low = EarnedScreenTimeCalculator.minutesEarnedForUrgency(urgencyLevel = 1, baseMinutes = 5)
    val mid = EarnedScreenTimeCalculator.minutesEarnedForUrgency(urgencyLevel = 3, baseMinutes = 5)
    val critical = EarnedScreenTimeCalculator.minutesEarnedForUrgency(urgencyLevel = 5, baseMinutes = 5)

    assertEquals(5, low)
    assertEquals(11, mid)
    assertEquals(23, critical)
    assertTrue(critical > mid && mid > low)

    val relayReward = EarnedScreenTimeCalculator.minutesEarnedForGroupRelayStep(baseMinutes = 5)
    assertEquals(10, relayReward)

    // Base minutes clamping check
    assertEquals(2, EarnedScreenTimeCalculator.minutesEarnedForUrgency(urgencyLevel = 1, baseMinutes = 0))
  }

  @Test
  fun procrastinationRadar_identifiesPeakAndDangerWindowsAndAdvisesShift() {
    fun millisAtHour(hour: Int, minute: Int = 0): Long {
      return Calendar.getInstance().apply {
        set(2026, Calendar.MAY, 10, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
      }.timeInMillis
    }

    val morningStart = millisAtHour(10, 0)
    val morningDeadline = millisAtHour(11, 0)
    val eveningStart = millisAtHour(19, 0)
    val eveningDeadline = millisAtHour(19, 30)

    val completedTasks = listOf(
      TaskEntity(
        id = "m1",
        title = "Morning report",
        scheduledAtMillis = morningStart,
        maxCompletionMillis = morningDeadline,
        completedAtMillis = millisAtHour(10, 40),
        isCompleted = true,
        categoryId = "work"
      ),
      TaskEntity(
        id = "m2",
        title = "Morning inbox zero",
        scheduledAtMillis = morningStart,
        maxCompletionMillis = morningDeadline,
        completedAtMillis = millisAtHour(10, 50),
        isCompleted = true,
        categoryId = "work"
      ),
      TaskEntity(
        id = "e1",
        title = "Evening laundry",
        scheduledAtMillis = eveningStart,
        maxCompletionMillis = eveningDeadline,
        completedAtMillis = millisAtHour(20, 30),
        isCompleted = true,
        categoryId = "home"
      ),
      TaskEntity(
        id = "e2",
        title = "Evening dishes",
        scheduledAtMillis = eveningStart,
        maxCompletionMillis = eveningDeadline,
        completedAtMillis = millisAtHour(21, 0),
        isCompleted = true,
        categoryId = "home"
      )
    )

    val report = ProcrastinationRadarEngine.analyze(
      activeTasks = emptyList(),
      completedTasks = completedTasks,
      nowMillis = millisAtHour(22, 0)
    )

    assertTrue(report.hasEnoughHistory)
    assertEquals(4, report.sampleSize)
    assertEquals(50, report.overallOnTimePercent)
    assertEquals(DayTimeWindow.MORNING, report.peakWindow?.window)
    assertEquals(100, report.peakWindow?.onTimeRatePercent)
    assertEquals(DayTimeWindow.EVENING, report.dangerWindow?.window)
    assertEquals(0, report.dangerWindow?.onTimeRatePercent)
    assertEquals("home", report.highestFrictionCategoryName)

    val dangerAdvice = ProcrastinationRadarEngine.evaluateScheduleRisk(
      scheduledMillis = millisAtHour(19, 15),
      report = report,
      nowMillis = millisAtHour(8, 0)
    )
    assertNotNull(dangerAdvice)
    assertEquals(DayTimeWindow.MORNING, dangerAdvice?.suggestedWindow)

    val peakAdvice = ProcrastinationRadarEngine.evaluateScheduleRisk(
      scheduledMillis = millisAtHour(10, 15),
      report = report,
      nowMillis = millisAtHour(8, 0)
    )
    assertNull(peakAdvice)
  }

  @Test
  fun groupRelay_advancesBatonSequentiallyUntilCompletion() {
    val startMs = 1_700_000_000_000L
    val relay = CloudGroupRelay(
      id = "relay_1",
      groupId = "grp_1",
      title = "Saturday Kitchen Reset",
      createdByUid = "u1",
      steps = listOf(
        CloudRelayStep(stepTitle = "Clear table", assigneeUid = "u1", assigneeName = "Alex", durationMinutes = 10L),
        CloudRelayStep(stepTitle = "Load dishwasher", assigneeUid = "u2", assigneeName = "Sam", durationMinutes = 15L)
      ),
      currentStepIndex = 0L,
      currentAssigneeUid = "u1",
      relayStatus = GroupRelayStatus.ACTIVE.name,
      startedAtMillis = startMs,
      lastHandoffAtMillis = startMs
    )

    assertFalse(relay.isRelayCompleted)
    assertEquals("u1", relay.activeStep?.assigneeUid)
    assertEquals(0f, relay.progressFraction, 0.001f)

    // Leg 1 handoff
    val afterLeg1 = relay.advanceBaton(nowMillis = startMs + 120_000L)
    assertFalse(afterLeg1.isRelayCompleted)
    assertEquals(1L, afterLeg1.currentStepIndex)
    assertEquals("u2", afterLeg1.currentAssigneeUid)
    assertEquals(1L, afterLeg1.streakCount)
    assertTrue(afterLeg1.steps[0].done)
    assertFalse(afterLeg1.steps[1].done)
    assertEquals(120L, afterLeg1.steps[0].handoffSpeedSeconds)
    assertEquals(0.5f, afterLeg1.progressFraction, 0.001f)

    // Leg 2 final handoff
    val afterLeg2 = afterLeg1.advanceBaton(nowMillis = startMs + 300_000L)
    assertTrue(afterLeg2.isRelayCompleted)
    assertEquals(GroupRelayStatus.COMPLETED, afterLeg2.parsedStatus)
    assertEquals("", afterLeg2.currentAssigneeUid)
    assertEquals(2L, afterLeg2.streakCount)
    assertTrue(afterLeg2.steps[1].done)
    assertEquals(180L, afterLeg2.steps[1].handoffSpeedSeconds)
    assertEquals(1.0f, afterLeg2.progressFraction, 0.001f)
  }

  @Test
  fun focusAuraSensorEngine_accuratelyDetectsFaceDownOrientation() {
    // Upright on desk (+9.8 m/s^2 Z)
    assertFalse(
      FocusAuraSensorEngine.isFaceDownOrientation(
        accelX = 0.2f,
        accelY = 0.1f,
        accelZ = 9.8f,
        hasProximitySensor = true,
        isProximityCovered = false
      )
    )

    // Face-down flat with proximity covered
    assertTrue(
      FocusAuraSensorEngine.isFaceDownOrientation(
        accelX = 0.3f,
        accelY = -0.4f,
        accelZ = -9.6f,
        hasProximitySensor = true,
        isProximityCovered = true
      )
    )

    // Strongly inverted flat even if proximity sensor missing
    assertTrue(
      FocusAuraSensorEngine.isFaceDownOrientation(
        accelX = 0.5f,
        accelY = 0.5f,
        accelZ = -8.9f,
        hasProximitySensor = false,
        isProximityCovered = false
      )
    )

    // Tilted sideways too much (not flat on desk)
    assertFalse(
      FocusAuraSensorEngine.isFaceDownOrientation(
        accelX = 6.5f,
        accelY = 0.2f,
        accelZ = -7.8f,
        hasProximitySensor = false,
        isProximityCovered = false
      )
    )
  }
}
