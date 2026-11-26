package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.RecurrenceType
import com.example.data.model.TaskEntity
import com.example.data.model.TaskPriority
import com.example.notifications.TaskNotificationHelper
import com.example.screentime.TasksPlusDeviceAdminReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Tasks+", appName)
  }

  @Test
  fun `computeNextOccurrenceMillis schedules next recurring window`() {
    val now = 1_762_900_000_000L
    val task = TaskEntity(
      id = "rec_1",
      title = "Daily standup",
      scheduledAtMillis = now,
      maxCompletionMillis = now + 3_600_000L,
      priority = TaskPriority.CRITICAL.name,
      ringAlarmBell = true,
      recurrenceType = RecurrenceType.DAILY.name,
      recurrenceInterval = 1
    )
    val (nextStart, nextMax) = task.computeNextOccurrenceMillis(now)
    assertTrue(nextStart > now)
    assertEquals(3_600_000L, nextMax - nextStart)
  }

  @Test
  fun `TaskNotificationHelper schedules wake-up alarms for priority and due date`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    TaskNotificationHelper.ensureNotificationChannels(context)
    val now = System.currentTimeMillis()
    val task = TaskEntity(
      id = "task_wakeup_test",
      title = "Urgent deadline",
      subtext = "Ring alarm bell on wake up",
      scheduledAtMillis = now,
      maxCompletionMillis = now + 1_800_000L,
      reminderIntervalMinutes = 10,
      priority = TaskPriority.CRITICAL.name,
      ringAlarmBell = true
    )

    TaskNotificationHelper.scheduleTaskReminder(
      context = context,
      task = task,
      notificationsEnabled = true,
      fireInitialImmediately = false
    )

    val wakeLock = TaskNotificationHelper.acquireWakeUpLock(context, 1000L)
    TaskNotificationHelper.releaseWakeUpLock(wakeLock)
    TaskNotificationHelper.cancelTaskReminder(context, task.id)
  }

  @Test
  fun `TasksPlusDeviceAdminReceiver component is properly configured`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val adminComponent = TasksPlusDeviceAdminReceiver.getComponentName(context)
    assertNotNull(adminComponent)
    assertEquals(
      "com.example.screentime.TasksPlusDeviceAdminReceiver",
      adminComponent.className
    )
  }

  @Test
  fun `Group models and group-enforced screen time limit parse roles and proof status`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("Group", context.getString(R.string.tab_group))

    val parentMember = com.example.data.model.CloudGroupMember(
      uid = "uid_parent",
      displayName = "Parent Co-Manager",
      role = com.example.data.model.GroupRole.PARENT_ADMIN.name,
      canManageOthers = true
    )
    assertTrue(parentMember.hasAdminAccess)
    assertEquals(com.example.data.model.GroupRole.PARENT_ADMIN, parentMember.parsedRole)

    val childLimit = com.example.data.model.ScreenTimeLimitEntity(
      packageName = "com.example.game",
      appName = "Game App",
      limitMinutes = 1,
      usedMinutes = 1,
      isGroupEnforced = true,
      enforcedByName = "Parent Co-Manager",
      requiredGroupTaskId = "gtask_1",
      requiredGroupTaskTitle = "Finish Math Homework"
    )
    assertTrue(childLimit.isGroupEnforced)
    assertTrue(childLimit.isLimitReached)
    assertEquals("Finish Math Homework", childLimit.requiredGroupTaskTitle)
  }
}
