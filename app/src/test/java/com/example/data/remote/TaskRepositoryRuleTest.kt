package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.RecurrenceType
import com.example.data.model.ScreenTimeLimitEntity
import com.example.data.model.ScreenTimePeriod
import com.example.data.model.TaskEntity
import com.example.data.model.ThemeMode
import com.example.data.model.TonalSpotPalette
import com.example.data.model.UserPreferencesState
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TaskRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun upsertCloudTask_validPayload_createsAndReadsTask() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = CloudSyncRepository(firestore)
    val taskId = "task_${UUID.randomUUID().toString().replace("-", "_")}"
    val sampleTask = TaskEntity(
      id = taskId,
      title = "Review daily priorities",
      subtext = "Check morning items",
      scheduledAtMillis = 1762900000000L,
      maxCompletionMillis = 1762903600000L,
      reminderIntervalMinutes = 30,
      priority = com.example.data.model.TaskPriority.CRITICAL.name,
      ringAlarmBell = true,
      recurrenceType = RecurrenceType.DAILY.name,
      recurrenceInterval = 1,
      recurrenceDaysCsv = "",
      isCompleted = false,
      completedAtMillis = 0L
    )

    val createResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.upsertCloudTask(sampleTask) }
    assertTrue("Expected upsertCloudTask to succeed: ${createResult.exceptionOrNull()}", createResult.isSuccess)

    val listResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.getUserTasksOnce() }
    assertTrue(listResult.isSuccess)
    assertTrue(
      listResult.getOrThrow().any {
        it.id == taskId &&
          it.title == "Review daily priorities" &&
          it.priority == "CRITICAL" &&
          it.ringAlarmBell
      }
    )
  }

  @Test
  fun observeUserTasks_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = CloudSyncRepository(firestore)
    val taskId = "task_${UUID.randomUUID().toString().replace("-", "_")}"
    val sampleTask = TaskEntity(
      id = taskId,
      title = "Realtime synced task",
      subtext = "Verify flow emission",
      reminderIntervalMinutes = 10
    )

    withTimeout(DEFAULT_TIMEOUT_MS) { repository.upsertCloudTask(sampleTask).getOrThrow() }

    val emitted = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeUserTasks().first { list -> list.any { it.id == taskId } }
    }
    assertTrue(emitted.any { it.id == taskId })
  }

  @Test
  fun getTaskById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = CloudSyncRepository(firestore)
    val taskId = "task_${UUID.randomUUID().toString().replace("-", "_")}"
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.upsertCloudTask(TaskEntity(id = taskId, title = "Alice Private Task")).getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = CloudSyncRepository(firestore)
    val bobReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.getTaskByIdForUser(aliceUid, taskId)
    }
    assertTrue(bobReadResult.isFailure)
    val firestoreEx = bobReadResult.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull(firestoreEx)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
  }

  @Test
  fun observeUserTasks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repository = CloudSyncRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeUserTasks().first()
      }
      fail("Expected unauthenticated observeUserTasks to throw")
    } catch (e: Exception) {
      val firestoreEx = e.unwrapFirestoreException()
      assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }
  }

  @Test
  fun pushAndFetchUserPreferencesAndScreenTime_authenticatedOwner_succeeds() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = CloudSyncRepository(firestore)
    val prefs = UserPreferencesState(
      themeMode = ThemeMode.DARK,
      useDynamicColor = false,
      tonalSpotPalette = TonalSpotPalette.EMERALD,
      defaultReminderMinutes = 60,
      notificationsEnabled = true,
      autoDeleteUpdateApk = true
    )

    val pushRes = withTimeout(DEFAULT_TIMEOUT_MS) { repository.pushUserPreferences(prefs) }
    assertTrue(pushRes.isSuccess)

    val fetchRes = withTimeout(DEFAULT_TIMEOUT_MS) { repository.fetchUserPreferences() }
    assertTrue(fetchRes.isSuccess)
    val fetched = fetchRes.getOrThrow()
    assertNotNull(fetched)
    assertEquals("DARK", fetched?.themeMode)
    assertEquals("EMERALD", fetched?.tonalSpotPalette)

    val limitEntity = ScreenTimeLimitEntity(
      packageName = "com.google.android.youtube",
      appName = "YouTube",
      limitMinutes = 45,
      usedMinutes = 15,
      periodType = ScreenTimePeriod.DAILY.name,
      isEnabled = true
    )
    val stRes = withTimeout(DEFAULT_TIMEOUT_MS) { repository.upsertCloudScreenTimeLimit(limitEntity) }
    assertTrue("Expected upsertCloudScreenTimeLimit to succeed: ${stRes.exceptionOrNull()}", stRes.isSuccess)

    val emittedLimits = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeScreenTimeLimits().first { list -> list.any { it.packageName == "com.google.android.youtube" } }
    }
    assertTrue(emittedLimits.any { it.packageName == "com.google.android.youtube" && it.limitMinutes == 45L })
  }

  private companion object {
    const val ALICE_EMAIL = "alice@test.com"
    const val BOB_EMAIL = "bob@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
