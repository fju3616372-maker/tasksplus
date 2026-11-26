package com.example

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.TaskEntity
import com.example.notifications.TaskNotificationHelper
import com.example.notifications.TaskNotificationReceiver
import com.example.notifications.TaskTimerManager
import com.example.notifications.TaskTimerStatus
import com.example.widget.TasksWidgetUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.M])
class TaskTimerRobolectricTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    TaskTimerManager.resetTimer(context)
  }

  @Test
  fun `TaskTimerManager lifecycle start pause resume add5 complete and expire works cleanly on API 23`() {
    val taskA = TaskEntity(
      id = "sprint_task_1",
      title = "Finish Algebra Worksheet",
      subtext = "Pages 12-14"
    )
    val taskB = TaskEntity(
      id = "sprint_task_2",
      title = "Read History Chapter",
      subtext = "Take notes"
    )

    // Start timer for 2 selected tasks (10 minutes)
    val started = TaskTimerManager.startTimer(context, 10 * 60_000L, listOf(taskA, taskB))
    assertEquals(TaskTimerStatus.RUNNING, started.status)
    assertEquals(2, started.totalTargetCount)
    assertEquals(0, started.completedTargetCount)
    assertTrue(started.endAtEpochMillis > System.currentTimeMillis())

    // Notification should be posted
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val shadowNm = Shadows.shadowOf(nm)
    assertNotNull(shadowNm.getNotification(TaskNotificationHelper.NOTIFICATION_ID_TASK_TIMER))

    // Pause and resume
    val paused = TaskTimerManager.pauseTimer(context)
    assertEquals(TaskTimerStatus.PAUSED, paused.status)
    assertTrue(paused.remainingMillisWhenPaused > 0L)

    val extended = TaskTimerManager.addMinutes(context, 5)
    assertEquals(TaskTimerStatus.PAUSED, extended.status)
    assertTrue(extended.remainingMillisWhenPaused >= paused.remainingMillisWhenPaused + 4 * 60_000L)

    val resumed = TaskTimerManager.resumeTimer(context)
    assertEquals(TaskTimerStatus.RUNNING, resumed.status)

    // Complete first target task -> still running (1/2)
    val afterOne = TaskTimerManager.onTaskCompleted(context, taskA.id, remainingActiveTaskCount = 1)
    assertEquals(TaskTimerStatus.RUNNING, afterOne.status)
    assertEquals(1, afterOne.completedTargetCount)
    assertEquals(listOf(taskB.id), afterOne.targetTaskIds)

    // Complete second target task -> auto-completes sprint!
    val afterTwo = TaskTimerManager.onTaskCompleted(context, taskB.id, remainingActiveTaskCount = 0)
    assertEquals(TaskTimerStatus.COMPLETED, afterTwo.status)
    assertEquals(2, afterTwo.completedTargetCount)

    // Expiration flow on a fresh sprint
    TaskTimerManager.startTimer(context, 60_000L, listOf(taskA))
    assertNotNull(shadowNm.getNotification(TaskNotificationHelper.NOTIFICATION_ID_TASK_TIMER))

    val expired = TaskTimerManager.onTimerExpired(context)
    assertEquals(TaskTimerStatus.EXPIRED, expired.status)
    assertEquals(0L, expired.remainingMillisAt())

    // Ongoing notification must be cancelled and a dedicated Time's Up alert notification must be sent!
    assertNull(shadowNm.getNotification(TaskNotificationHelper.NOTIFICATION_ID_TASK_TIMER))
    val expiredNotification = shadowNm.getNotification(TaskNotificationHelper.NOTIFICATION_ID_TASK_TIMER_ALERT)
    assertNotNull(expiredNotification)
    val title = expiredNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
    assertTrue("Expected Time's Up title, got: $title", title.contains("Time's Up"))

    // Updating widgets on API 23 must never throw NoSuchMethodError
    TasksWidgetUpdater.updateAllWidgets(context)

    // Reset clears both ongoing and alert notifications back to IDLE
    val reset = TaskTimerManager.resetTimer(context)
    assertEquals(TaskTimerStatus.IDLE, reset.status)
    assertNull(shadowNm.getNotification(TaskNotificationHelper.NOTIFICATION_ID_TASK_TIMER))
    assertNull(shadowNm.getNotification(TaskNotificationHelper.NOTIFICATION_ID_TASK_TIMER_ALERT))
  }

  @Test
  fun `ACTION_TIMER_EXPIRED broadcast posts Time's Up alert notification`() {
    val task = TaskEntity(id = "alarm_task", title = "Submit Lab Report")
    TaskTimerManager.startTimer(context, 30_000L, listOf(task))

    val receiver = TaskNotificationReceiver()
    val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
      action = TaskNotificationHelper.ACTION_TIMER_EXPIRED
    }
    receiver.onReceive(context, intent)

    // Allow IO coroutine inside BroadcastReceiver to complete
    val deadline = System.currentTimeMillis() + 3_000L
    while (TaskTimerManager.getCurrentState(context).status != TaskTimerStatus.EXPIRED &&
      System.currentTimeMillis() < deadline
    ) {
      Thread.sleep(25L)
    }

    assertEquals(TaskTimerStatus.EXPIRED, TaskTimerManager.getCurrentState(context).status)
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val shadowNm = Shadows.shadowOf(nm)
    assertNull(shadowNm.getNotification(TaskNotificationHelper.NOTIFICATION_ID_TASK_TIMER))
    val alertNotif = shadowNm.getNotification(TaskNotificationHelper.NOTIFICATION_ID_TASK_TIMER_ALERT)
    assertNotNull(alertNotif)
    val text = alertNotif.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
    assertTrue("Expected task title in notification text, got: $text", text.contains("Submit Lab Report"))
  }

  @Test
  fun `sendTestTimerExpiredNotification posts high-priority Time's Up alert`() {
    val helper = TaskNotificationHelper(context)
    val sent = helper.sendTestTimerExpiredNotification(ringSound = true)
    assertTrue(sent)

    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val shadowNm = Shadows.shadowOf(nm)
    val alertNotif = shadowNm.getNotification(TaskNotificationHelper.NOTIFICATION_ID_TASK_TIMER_ALERT)
    assertNotNull(alertNotif)
    val title = alertNotif.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
    assertTrue("Expected Time's Up title, got: $title", title.contains("Time's Up"))
  }

  @Test
  fun `formatCountdown formats minutes seconds and hours accurately`() {
    assertEquals("00:00", TaskTimerManager.formatCountdown(0L))
    assertEquals("00:45", TaskTimerManager.formatCountdown(45_000L))
    assertEquals("25:00", TaskTimerManager.formatCountdown(25 * 60_000L))
    assertEquals("1:05:09", TaskTimerManager.formatCountdown((3600 + 5 * 60 + 9) * 1000L))
  }
}
