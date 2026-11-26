package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.CloudCreditTransaction
import com.example.data.model.CloudScreenTimeLimit
import com.example.data.model.CloudTask
import com.example.data.model.CloudTaskRecord
import com.example.data.model.CloudUserPreferences
import com.example.data.model.ScreenTimeLimitEntity
import com.example.data.model.TaskCategoryCatalog
import com.example.data.model.TaskEntity
import com.example.data.model.TaskRecordScope
import com.example.data.model.TaskRecordType
import com.example.data.model.UserPreferencesState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  fun observeUserTasks(): Flow<List<CloudTask>> = flow {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      val fe = FirebaseFirestoreException(
        "PERMISSION_DENIED: User must be authenticated",
        FirebaseFirestoreException.Code.PERMISSION_DENIED
      )
      handleFirestoreError(fe, OperationType.LIST, "users/unauthenticated/tasks")
      throw fe
    }
    val path = "users/$uid/tasks"
    emitAll(
      db.collection("users").document(uid).collection("tasks")
        .snapshots()
        .map { snapshot ->
          snapshot.documents.map { doc -> CloudTask.fromSnapshot(doc) }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeScreenTimeLimits(): Flow<List<CloudScreenTimeLimit>> = flow {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      val fe = FirebaseFirestoreException(
        "PERMISSION_DENIED: User must be authenticated",
        FirebaseFirestoreException.Code.PERMISSION_DENIED
      )
      handleFirestoreError(fe, OperationType.LIST, "users/unauthenticated/screentime")
      throw fe
    }
    val path = "users/$uid/screentime"
    emitAll(
      db.collection("users").document(uid).collection("screentime")
        .snapshots()
        .map { snapshot ->
          snapshot.documents.map { doc -> CloudScreenTimeLimit.fromSnapshot(doc) }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun upsertCloudTask(task: TaskEntity): Result<String> {
    return try {
      val uid = requireUserId()
      val taskId = task.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "task_default" }
      val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
      val existingSnap = try {
        docRef.get().await()
      } catch (e: Exception) {
        null
      }
      val existingCreatedAt = existingSnap?.getTimestamp(
        "createdAt",
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
      )

      val payload = mapOf(
        "userId" to uid,
        "title" to task.title.trim().ifBlank { "Untitled Task" }.take(200),
        "subtext" to task.subtext.trim().take(500),
        "scheduledAtMillis" to task.scheduledAtMillis.coerceAtLeast(0L),
        "maxCompletionMillis" to task.maxCompletionMillis.coerceAtLeast(0L),
        "reminderIntervalMinutes" to task.reminderIntervalMinutes.coerceIn(0, 10080).toLong(),
        "recurrenceType" to task.parsedRecurrenceType.name,
        "recurrenceInterval" to task.recurrenceInterval.coerceIn(1, 365).toLong(),
        "recurrenceDaysCsv" to task.recurrenceDaysCsv.take(50),
        "priority" to task.parsedPriority.name,
        "urgencyLevel" to task.resolvedUrgencyLevel.toLong(),
        "categoryId" to task.categoryId.trim().take(64),
        "ringAlarmBell" to task.ringAlarmBell,
        "isCompleted" to task.isCompleted,
        "completedAtMillis" to task.completedAtMillis.coerceAtLeast(0L),
        "creditsAwarded" to task.creditsAwarded,
        "creditSecondsDelta" to task.creditSecondsDelta,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      val op = if (existingCreatedAt != null) OperationType.UPDATE else OperationType.CREATE
      try {
        docRef.set(payload).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, op, docRef.path)
        throw e
      }
      Result.success(taskId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun getTaskByIdForUser(targetUserId: String, taskId: String): Result<CloudTask?> {
    val docRef = db.collection("users").document(targetUserId).collection("tasks").document(taskId)
    return try {
      val snapshot = docRef.get().await()
      Result.success(
        if (snapshot.exists()) CloudTask.fromSnapshot(snapshot) else null
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun getUserTasksOnce(): Result<List<CloudTask>> {
    return try {
      val uid = requireUserId()
      val colRef = db.collection("users").document(uid).collection("tasks")
      val snapshot = try {
        colRef.get().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.LIST, colRef.path)
        throw e
      }
      Result.success(
        snapshot.documents.map { doc -> CloudTask.fromSnapshot(doc) }
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun deleteCloudTask(taskId: String): Result<Unit> {
    return try {
      val uid = requireUserId()
      val safeId = taskId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
      val docRef = db.collection("users").document(uid).collection("tasks").document(safeId)
      try {
        docRef.delete().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, docRef.path)
        throw e
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun upsertCloudScreenTimeLimit(limit: ScreenTimeLimitEntity): Result<String> {
    return try {
      val uid = requireUserId()
      val docId = limit.firestoreDocId
      val docRef = db.collection("users").document(uid).collection("screentime").document(docId)
      val existingSnap = try {
        docRef.get().await()
      } catch (e: Exception) {
        null
      }
      val existingCreatedAt = existingSnap?.getTimestamp(
        "createdAt",
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
      )
      val payload = mapOf(
        "userId" to uid,
        "packageName" to limit.packageName.trim().take(200),
        "appName" to limit.appName.trim().ifBlank { limit.packageName }.take(200),
        "limitMinutes" to limit.limitMinutes.coerceIn(1, 10080).toLong(),
        "usedMinutes" to limit.usedMinutes.coerceIn(0, 10080).toLong(),
        "periodType" to limit.parsedPeriod.name,
        "isEnabled" to limit.isEnabled,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      val op = if (existingCreatedAt != null) OperationType.UPDATE else OperationType.CREATE
      try {
        docRef.set(payload).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, op, docRef.path)
        throw e
      }
      Result.success(docId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun deleteCloudScreenTimeLimit(packageName: String): Result<Unit> {
    return try {
      val uid = requireUserId()
      val docId = packageName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "pkg_default" }
      val docRef = db.collection("users").document(uid).collection("screentime").document(docId)
      try {
        docRef.delete().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, docRef.path)
        throw e
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun pushUserPreferences(prefs: UserPreferencesState): Result<Unit> {
    return try {
      val uid = requireUserId()
      val docRef = db.collection("users").document(uid).collection("settings").document("preferences")
      val existingSnap = try {
        docRef.get().await()
      } catch (e: Exception) {
        null
      }
      val existingCreatedAt = existingSnap?.getTimestamp(
        "createdAt",
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
      )
      val payload = mapOf(
        "userId" to uid,
        "themeMode" to prefs.themeMode.name,
        "useDynamicColor" to prefs.useDynamicColor,
        "tonalSpotPalette" to prefs.tonalSpotPalette.name,
        "defaultReminderMinutes" to prefs.defaultReminderMinutes.coerceIn(0, 10080).toLong(),
        "notificationsEnabled" to prefs.notificationsEnabled,
        "autoDeleteUpdateApk" to prefs.autoDeleteUpdateApk,
        "creditSystemEnabled" to prefs.creditSystemEnabled,
        "personalCredits" to prefs.personalCredits,
        "earlyPointsPerSecond" to prefs.earlyPointsPerSecond.coerceIn(0.0, 1000.0),
        "latePenaltyPerSecond" to prefs.latePenaltyPerSecond.coerceIn(0.0, 1000.0),
        "creditGracePeriodSeconds" to prefs.creditGracePeriodSeconds.coerceIn(0L, 86_400L),
        "maxCreditPerTask" to prefs.maxCreditPerTask.coerceIn(0L, 1_000_000_000L),
        "maxPenaltyPerTask" to prefs.maxPenaltyPerTask.coerceIn(0L, 1_000_000_000L),
        "allowNegativeCredits" to prefs.allowNegativeCredits,
        "categoriesEnabled" to prefs.categoriesEnabled,
        "disabledPremadeCategoryIds" to prefs.disabledPremadeCategoryIds.sorted().take(20),
        "customCategoriesJson" to TaskCategoryCatalog.serializeCustomCategories(prefs.customCategories).take(8000),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      val op = if (existingCreatedAt != null) OperationType.UPDATE else OperationType.CREATE
      try {
        docRef.set(payload).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, op, docRef.path)
        throw e
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun fetchUserPreferences(): Result<CloudUserPreferences?> {
    return try {
      val uid = requireUserId()
      val docRef = db.collection("users").document(uid).collection("settings").document("preferences")
      val snapshot = try {
        docRef.get().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, docRef.path)
        throw e
      }
      Result.success(
        if (snapshot.exists()) {
          snapshot.toObject(
            CloudUserPreferences::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          )
        } else {
          null
        }
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun observeUserPreferences(): Flow<CloudUserPreferences?> = flow {
    val uid = requireUserId()
    val path = "users/$uid/settings/preferences"
    emitAll(
      db.collection("users").document(uid).collection("settings").document("preferences")
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(
              CloudUserPreferences::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
          } else {
            null
          }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.GET, path)
          }
          throw error
        }
    )
  }

  fun observePersonalCreditTransactions(): Flow<List<CloudCreditTransaction>> = flow {
    val uid = requireUserId()
    val path = "users/$uid/creditTransactions"
    emitAll(
      db.collection("users").document(uid).collection("creditTransactions")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(100)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            CloudCreditTransaction::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          )
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun addPersonalCreditTransaction(
    taskId: String,
    taskTitle: String,
    pointsDelta: Long,
    secondsDelta: Long,
    reason: String
  ): Result<String> {
    return try {
      val user = auth.currentUser
        ?: throw IllegalStateException("User must be signed in before logging credit transactions.")
      val uid = user.uid
      val displayName = user.displayName?.takeIf { it.isNotBlank() }
        ?: user.email?.substringBefore("@")
        ?: "You"
      val txId = "ctx_${UUID.randomUUID().toString().replace("-", "_").take(24)}"
      val docRef = db.collection("users").document(uid).collection("creditTransactions").document(txId)
      val payload = mapOf(
        "userId" to uid,
        "userName" to displayName.take(120),
        "groupId" to "",
        "taskId" to taskId.take(128),
        "taskTitle" to taskTitle.take(200),
        "pointsDelta" to pointsDelta,
        "secondsDelta" to secondsDelta,
        "reason" to reason.trim().ifBlank { "Task credit adjustment" }.take(300),
        "awardedByUid" to uid,
        "awardedByName" to displayName.take(120),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      try {
        docRef.set(payload).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, docRef.path)
        throw e
      }
      Result.success(txId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Streams the signed-in user's private Task Records Library ordered by most recent event time.
   * Strictly scoped to `/users/{uid}/taskRecords` so no other user can ever access these records.
   */
  fun observeUserTaskRecords(): Flow<List<CloudTaskRecord>> = flow {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      val fe = FirebaseFirestoreException(
        "PERMISSION_DENIED: User must be authenticated",
        FirebaseFirestoreException.Code.PERMISSION_DENIED
      )
      handleFirestoreError(fe, OperationType.LIST, "users/unauthenticated/taskRecords")
      throw fe
    }
    val path = "users/$uid/taskRecords"
    emitAll(
      db.collection("users").document(uid).collection("taskRecords")
        .orderBy("eventTimeMillis", Query.Direction.DESCENDING)
        .limit(300)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            CloudTaskRecord::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          )
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  /**
   * Creates or updates a private Task Record in `/users/{uid}/taskRecords/{recordId}` using a
   * deterministic ID to avoid duplicates while preserving original `createdAt`.
   */
  suspend fun upsertTaskRecord(
    recordId: String,
    recordType: TaskRecordType,
    sourceScope: TaskRecordScope,
    taskId: String,
    taskTitle: String,
    taskSubtext: String = "",
    groupId: String = "",
    groupName: String = "",
    counterpartyName: String = "",
    pointsReceived: Long = 0L,
    hasPoints: Boolean = false,
    pointsReason: String = "",
    eventTimeMillis: Long = System.currentTimeMillis(),
    dueAtMillis: Long = 0L
  ): Result<String> {
    return try {
      val uid = requireUserId()
      val cleanTitle = taskTitle.trim().ifBlank { "Untitled task" }.take(200)
      val safeId = recordId
        .replace(Regex("[^a-zA-Z0-9_-]"), "_")
        .take(120)
        .ifBlank { "rec_${UUID.randomUUID().toString().replace("-", "_").take(24)}" }

      val docRef = db.collection("users").document(uid).collection("taskRecords").document(safeId)
      val existing = try {
        docRef.get().await()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        null
      }

      val payload = mutableMapOf<String, Any>(
        "userId" to uid,
        "recordType" to recordType.name,
        "sourceScope" to sourceScope.name,
        "taskId" to taskId.trim().take(128),
        "taskTitle" to cleanTitle,
        "taskSubtext" to taskSubtext.trim().take(500),
        "groupId" to groupId.trim().take(128),
        "groupName" to groupName.trim().take(100),
        "counterpartyName" to counterpartyName.trim().take(120),
        "pointsReceived" to pointsReceived.coerceIn(-1_000_000_000L, 1_000_000_000L),
        "hasPoints" to hasPoints,
        "pointsReason" to pointsReason.trim().take(300),
        "eventTimeMillis" to eventTimeMillis.coerceAtLeast(0L),
        "dueAtMillis" to dueAtMillis.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp()
      )

      if (existing != null && existing.exists()) {
        payload["createdAt"] = existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
        try {
          docRef.set(payload).await()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.UPDATE, docRef.path)
          throw e
        }
      } else {
        payload["createdAt"] = FieldValue.serverTimestamp()
        try {
          docRef.set(payload).await()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.CREATE, docRef.path)
          throw e
        }
      }
      Result.success(safeId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun deleteTaskRecord(recordId: String): Result<Unit> {
    return try {
      val uid = requireUserId()
      val safeId = recordId.trim().take(128)
      if (safeId.isBlank()) return Result.success(Unit)
      val docRef = db.collection("users").document(uid).collection("taskRecords").document(safeId)
      try {
        docRef.delete().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, docRef.path)
        throw e
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun clearAllTaskRecords(): Result<Unit> {
    return try {
      val uid = requireUserId()
      val colRef = db.collection("users").document(uid).collection("taskRecords")
      val snapshot = try {
        colRef.get().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.LIST, "users/$uid/taskRecords")
        throw e
      }
      for (doc in snapshot.documents) {
        try {
          doc.reference.delete().await()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.DELETE, doc.reference.path)
          throw e
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
