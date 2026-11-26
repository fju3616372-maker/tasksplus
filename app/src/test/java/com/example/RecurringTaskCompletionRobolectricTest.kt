package com.example

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TaskDatabase
import com.example.data.model.CloudGroupScreenTimeLimit
import com.example.data.model.CloudGroupTask
import com.example.data.model.CloudScreenTimeLimit
import com.example.data.model.CloudTask
import com.example.data.model.RecurrenceType
import com.example.data.model.TaskEntity
import com.google.firebase.firestore.PropertyName
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.M])
class RecurringTaskCompletionRobolectricTest {

  private lateinit var context: Context
  private lateinit var db: TaskDatabase

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, TaskDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `CloudTask and CloudGroupTask expose PropertyName isCompleted for Firestore`() {
    val taskGetter = CloudTask::class.java.getMethod("isCompleted")
    val taskAnnotation = taskGetter.getAnnotation(PropertyName::class.java)
    assertNotNull("CloudTask.isCompleted() must have @PropertyName", taskAnnotation)
    assertEquals("isCompleted", taskAnnotation?.value)

    val groupTaskGetter = CloudGroupTask::class.java.getMethod("isCompleted")
    val groupTaskAnnotation = groupTaskGetter.getAnnotation(PropertyName::class.java)
    assertNotNull("CloudGroupTask.isCompleted() must have @PropertyName", groupTaskAnnotation)
    assertEquals("isCompleted", groupTaskAnnotation?.value)

    val limitGetter = CloudScreenTimeLimit::class.java.getMethod("isEnabled")
    assertEquals("isEnabled", limitGetter.getAnnotation(PropertyName::class.java)?.value)

    val groupLimitGetter = CloudGroupScreenTimeLimit::class.java.getMethod("isEnabled")
    assertEquals("isEnabled", groupLimitGetter.getAnnotation(PropertyName::class.java)?.value)
  }

  @Test
  fun `CloudTask toEntity preserves isCompleted and self-heals corrupted done snapshots`() {
    val completedCloud = CloudTask(
      id = "task_regular_1",
      title = "Pay bills",
      isCompleted = true,
      completedAtMillis = 1_700_000_000_000L
    )
    val regularEntity = completedCloud.toEntity()
    assertTrue(regularEntity.isCompleted)
    assertEquals(1_700_000_000_000L, regularEntity.completedAtMillis)

    // Simulate legacy corrupted Firestore doc where isCompleted came back false on a done_* snapshot
    val corruptedSnapshot = CloudTask(
      id = "done_abc_123",
      title = "Daily Stretch",
      recurrenceType = RecurrenceType.DAILY.name,
      isCompleted = false,
      completedAtMillis = 1_700_000_123_000L
    )
    val healedEntity = corruptedSnapshot.toEntity()
    assertTrue("done_* snapshot must always resolve to isCompleted = true", healedEntity.isCompleted)
    assertEquals(RecurrenceType.NONE.name, healedEntity.recurrenceType)
    assertEquals(1_700_000_123_000L, healedEntity.completedAtMillis)
  }

  @Test
  fun `completing daily task moves today to Completed and hides tomorrow from today list`() = runBlocking {
    val cal = Calendar.getInstance().apply {
      set(Calendar.YEAR, 2026)
      set(Calendar.MONTH, Calendar.MAY)
      set(Calendar.DAY_OF_MONTH, 18)
      set(Calendar.HOUR_OF_DAY, 9)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val todayNineAm = cal.timeInMillis
    val todayTenAm = todayNineAm + 3_600_000L
    val completionNow = todayNineAm + 1_500_000L // 9:25 AM same day

    val dailyTask = TaskEntity(
      id = "task_daily_workout",
      title = "Morning Workout",
      scheduledAtMillis = todayNineAm,
      maxCompletionMillis = todayTenAm,
      recurrenceType = RecurrenceType.DAILY.name,
      isCompleted = false,
      updatedAtMillis = todayNineAm
    )

    val dao = db.taskDao()
    dao.upsertTask(dailyTask)

    // Before completion: visible in today's To-Do list
    assertFalse(dailyTask.isScheduledForFutureDay(completionNow))

    // Complete daily task
    val completedSnapshot = dailyTask.copy(
      id = "done_morning_workout_1",
      isCompleted = true,
      completedAtMillis = completionNow,
      recurrenceType = RecurrenceType.NONE.name,
      updatedAtMillis = completionNow
    )
    val (nextStart, nextMax) = dailyTask.computeNextOccurrenceMillis(completionNow)
    val nextOccurrence = dailyTask.copy(
      scheduledAtMillis = nextStart,
      maxCompletionMillis = nextMax,
      isCompleted = false,
      completedAtMillis = 0L,
      updatedAtMillis = completionNow
    )
    dao.upsertTask(completedSnapshot)
    dao.upsertTask(nextOccurrence)

    val activeTasks = dao.getActiveTasksOnce()
    val completedTasks = dao.getAllTasksOnce().filter { it.isCompleted }

    // Exactly 1 completed snapshot in Completed tab, 1 future-scheduled task in DB
    assertEquals(1, completedTasks.size)
    assertEquals("done_morning_workout_1", completedTasks.first().id)
    assertTrue(completedTasks.first().isCompleted)
    assertEquals(RecurrenceType.NONE.name, completedTasks.first().recurrenceType)

    assertEquals(1, activeTasks.size)
    assertEquals("task_daily_workout", activeTasks.first().id)

    // Tomorrow's occurrence preserves 9:00 AM and is hidden from today's active To-Do list
    val nextCal = Calendar.getInstance().apply { timeInMillis = activeTasks.first().scheduledAtMillis }
    assertEquals(19, nextCal.get(Calendar.DAY_OF_MONTH))
    assertEquals(9, nextCal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, nextCal.get(Calendar.MINUTE))

    val visibleToday = activeTasks.filter { !it.isScheduledForFutureDay(completionNow) }
    val upcomingRecurring = activeTasks.filter { it.isScheduledForFutureDay(completionNow) }
    assertEquals(0, visibleToday.size)
    assertEquals(1, upcomingRecurring.size)

    // Once tomorrow morning arrives, it automatically re-appears in Today's To-Do list!
    val tomorrowEightAm = Calendar.getInstance().apply {
      set(Calendar.YEAR, 2026)
      set(Calendar.MONTH, Calendar.MAY)
      set(Calendar.DAY_OF_MONTH, 19)
      set(Calendar.HOUR_OF_DAY, 8)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    assertFalse(activeTasks.first().isScheduledForFutureDay(tomorrowEightAm))
  }
}
