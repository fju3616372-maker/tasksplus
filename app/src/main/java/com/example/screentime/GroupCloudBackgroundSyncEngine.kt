package com.example.screentime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.example.R
import com.example.data.local.TaskDatabase
import com.example.data.model.CloudGroup
import com.example.data.model.CloudGroupInvite
import com.example.data.model.CloudGroupMember
import com.example.data.model.CloudGroupScreenTimeLimit
import com.example.data.model.CloudGroupTask
import com.example.data.model.GroupRole
import com.example.data.model.ProofStatus
import com.example.data.model.RecurrenceType
import com.example.data.model.RemoteLockMode
import com.example.data.model.ScreenTimeLimitEntity
import com.example.data.model.TaskEntity
import com.example.data.model.TaskPriority
import com.example.data.remote.GroupRepository
import com.example.notifications.TaskNotificationHelper
import com.example.notifications.TaskReminderManager
import com.example.widget.TasksWidgetUpdater
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Persistent background cloud synchronization engine that keeps Firestore real-time listeners
 * active even when the Tasks+ UI is closed. Delivers instant group task notifications, enforces
 * remote screen time limits, and enforces remote Device Admin locks when the device screen turns on.
 */
object GroupCloudBackgroundSyncEngine {
  private const val TAG = "GroupBgSync"

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var isInitialized = false
  private var activeUid: String? = null
  private var activeGroupId: String? = null

  private var groupReg: ListenerRegistration? = null
  private var inviteReg: ListenerRegistration? = null
  private var memberSelfReg: ListenerRegistration? = null
  private var groupTasksReg: ListenerRegistration? = null
  private var groupScreenTimeReg: ListenerRegistration? = null

  private val notifiedInviteIds = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
  private val notifiedTaskAssignIds = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
  private val notifiedTaskStatusKeys = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
  private val notifiedRemoteLimitKeys = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
  private val appliedRemoteLimitSignatures = ConcurrentHashMap<String, String>()

  @Volatile
  var isRemoteDeviceLocked: Boolean = false
    private set

  @Volatile
  var remoteLockReason: String = ""
    private set

  @Volatile
  var isSupervisedChildRole: Boolean = false
    private set

  private var screenWakeReceiverRegistered = false

  fun ensureStarted(context: Context) {
    val appContext = context.applicationContext
    registerScreenWakeReceiverIfNeeded(appContext)

    if (!isInitialized) {
      isInitialized = true
      Firebase.auth.addAuthStateListener(
        FirebaseAuth.AuthStateListener { firebaseAuth ->
          val user = firebaseAuth.currentUser
          if (user == null) {
            clearAllListeners()
          } else if (activeUid != user.uid) {
            activeUid = user.uid
            attachUserListeners(appContext, user.uid, user.email.orEmpty())
          }
        }
      )
    }

    val current = Firebase.auth.currentUser
    if (current != null && activeUid != current.uid) {
      activeUid = current.uid
      attachUserListeners(appContext, current.uid, current.email.orEmpty())
    }
  }

  private fun registerScreenWakeReceiverIfNeeded(appContext: Context) {
    if (screenWakeReceiverRegistered) return
    try {
      val filter = IntentFilter().apply {
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_USER_PRESENT)
      }
      val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
          val targetCtx = ctx ?: appContext
          if (isRemoteDeviceLocked) {
            enforceRemoteDeviceLockImmediately(targetCtx, remoteLockReason)
          }
        }
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
      } else {
        appContext.registerReceiver(receiver, filter)
      }
      screenWakeReceiverRegistered = true
    } catch (e: Exception) {
      Log.w(TAG, "Could not register screen wake receiver", e)
    }
  }

  private fun attachUserListeners(appContext: Context, uid: String, email: String) {
    clearAllListeners()
    activeUid = uid
    val databaseId = appContext.getString(R.string.firestore_database_id)
    val firestore = FirebaseFirestore.getInstance(databaseId)

    // 1. Listen for pending email invitations
    val cleanEmail = email.trim().lowercase()
    if (cleanEmail.isNotBlank()) {
      inviteReg = firestore.collection("groupInvites")
        .whereEqualTo("targetEmail", cleanEmail)
        .addSnapshotListener { snap, err ->
          if (err != null || snap == null) return@addSnapshotListener
          val invites = snap.toObjects(CloudGroupInvite::class.java)
            .filter { it.status == "PENDING" }
          for (inv in invites) {
            if (notifiedInviteIds.add(inv.id)) {
              val notifyTask = TaskEntity(
                id = "group_invite_${inv.id}",
                title = "Group Request: ${inv.groupName}",
                subtext = "${inv.senderName} invited you (${inv.parsedProposedRole.label}). Open Group tab to accept.",
                priority = TaskPriority.HIGH.name,
                reminderIntervalMinutes = 0
              )
              TaskReminderManager.showPriorityTaskNotification(appContext, notifyTask, triggerAlarmBell = false)
            }
          }
        }
    }

    // 2. Listen for the user's active Group
    groupReg = firestore.collection("groups")
      .whereArrayContains("memberUids", uid)
      .addSnapshotListener { snap, err ->
        if (err != null || snap == null) return@addSnapshotListener
        val group = snap.toObjects(CloudGroup::class.java).firstOrNull()
        if (group == null) {
          detachGroupSubListeners()
          isRemoteDeviceLocked = false
          isSupervisedChildRole = false
        } else if (activeGroupId != group.id) {
          attachGroupSubListeners(appContext, firestore, group.id, uid)
        }
      }
  }

  private fun attachGroupSubListeners(
    appContext: Context,
    firestore: FirebaseFirestore,
    groupId: String,
    uid: String
  ) {
    detachGroupSubListeners()
    activeGroupId = groupId
    val groupDoc = firestore.collection("groups").document(groupId)

    // Collect & upload real device installed apps (name + package name) so Managers/Parents can pick them remotely
    scope.launch {
      try {
        val apps = RealDeviceAppsScanner.queryRealInstalledApps(appContext, includeSystemApps = false)
        if (apps.isNotEmpty()) {
          GroupRepository(firestore).syncCurrentUserInstalledApps(groupId, apps)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to sync installed apps for group $groupId", e)
      }
    }

    // 2a. Listen to current user's Member document for role & Remote Device Lock
    memberSelfReg = groupDoc.collection("members").document(uid)
      .addSnapshotListener { snap, err ->
        if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
        val member = snap.toObject(CloudGroupMember::class.java) ?: return@addSnapshotListener
        isSupervisedChildRole = member.parsedRole == GroupRole.CHILD && !member.canManageOthers
        val wasLocked = isRemoteDeviceLocked
        isRemoteDeviceLocked = member.remoteDeviceLocked
        remoteLockReason = member.remoteLockReason.ifBlank {
          "Device locked remotely by Group Manager / Parent"
        }

        if (isRemoteDeviceLocked) {
          enforceRemoteDeviceLockImmediately(appContext, remoteLockReason)
        } else if (wasLocked) {
          SystemOverlayBlocker.dismissOverlay(appContext)
        }
      }

    // 2b. Listen to Group Tasks for instant assignment & completion notifications
    groupTasksReg = groupDoc.collection("groupTasks")
      .addSnapshotListener { snap, err ->
        if (err != null || snap == null) return@addSnapshotListener
        val tasks = snap.toObjects(CloudGroupTask::class.java)
        scope.launch {
          val db = TaskDatabase.getInstance(appContext)
          val taskDao = db.taskDao()

          for (gt in tasks) {
            val isForMe = gt.assignedToUid == "ALL" || gt.assignedToUid == uid
            val isCreatedByMe = gt.createdByUid == uid

            if (isForMe && !gt.isCompleted) {
              val localId = "grouptask_${gt.id}"
              val existingLocal = taskDao.getTaskById(localId)
              val entity = TaskEntity(
                id = localId,
                title = "[Group] ${gt.title}",
                subtext = buildString {
                  if (gt.subtext.isNotBlank()) {
                    append(gt.subtext)
                    append(" • ")
                  }
                  append("From ${gt.createdByName}")
                  if (gt.gatedAppName.isNotBlank()) {
                    append(" • Unlocks ${gt.gatedAppName}")
                  }
                },
                scheduledAtMillis = System.currentTimeMillis(),
                maxCompletionMillis = gt.dueAtMillis,
                reminderIntervalMinutes = gt.reminderIntervalMinutes.toInt(),
                recurrenceType = RecurrenceType.NONE.name,
                priority = gt.priority,
                urgencyLevel = gt.resolvedUrgencyLevel,
                ringAlarmBell = gt.ringAlarmBell,
                isCompleted = false,
                updatedAtMillis = System.currentTimeMillis()
              )
              taskDao.upsertTask(entity)

              val isFirstSeen = notifiedTaskAssignIds.add(gt.id)
              if (isFirstSeen && existingLocal == null && gt.createdByUid != uid) {
                TaskReminderManager.scheduleTaskReminder(
                  context = appContext,
                  task = entity,
                  notificationsEnabled = true,
                  fireInitialImmediately = true
                )
              } else {
                TaskReminderManager.scheduleTaskReminder(
                  context = appContext,
                  task = entity,
                  notificationsEnabled = true,
                  fireInitialImmediately = false
                )
              }
            } else if (gt.isCompleted) {
              val localId = "grouptask_${gt.id}"
              val existingLocal = taskDao.getTaskById(localId)
              if (existingLocal != null && !existingLocal.isCompleted) {
                TaskReminderManager.cancelTaskReminder(appContext, localId)
                taskDao.upsertTask(
                  existingLocal.copy(
                    isCompleted = true,
                    completedAtMillis = gt.completedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
                  )
                )
              }
            }

            // Notify Manager/Creator when proof is uploaded or task is completed
            if (isCreatedByMe && gt.assignedToUid != uid) {
              val statusKey = "${gt.id}_${gt.proofStatus}_${gt.isCompleted}"
              if (notifiedTaskStatusKeys.add(statusKey)) {
                if (gt.parsedProofStatus == ProofStatus.PENDING_APPROVAL) {
                  val alert = TaskEntity(
                    id = "proof_review_${gt.id}",
                    title = "Proof Submitted: ${gt.title}",
                    subtext = "${gt.assignedToName} uploaded photo proof. Tap to review in Group tab.",
                    priority = TaskPriority.HIGH.name,
                    reminderIntervalMinutes = 0
                  )
                  TaskReminderManager.showPriorityTaskNotification(appContext, alert, triggerAlarmBell = false)
                } else if (gt.isCompleted && gt.completedByName.isNotBlank()) {
                  val alert = TaskEntity(
                    id = "gtask_done_${gt.id}",
                    title = "Group Task Completed: ${gt.title}",
                    subtext = "Finished by ${gt.completedByName}",
                    priority = TaskPriority.MEDIUM.name,
                    reminderIntervalMinutes = 0
                  )
                  TaskReminderManager.showPriorityTaskNotification(appContext, alert, triggerAlarmBell = false)
                }
              }
            }
          }
          TasksWidgetUpdater.updateAllWidgets(appContext)
        }
      }

    // 2c. Listen to remotely enforced Screen Time Limits for this user
    groupScreenTimeReg = groupDoc.collection("memberScreenTime")
      .whereEqualTo("targetUid", uid)
      .addSnapshotListener { snap, err ->
        if (err != null || snap == null) return@addSnapshotListener
        val remoteLimits = snap.toObjects(CloudGroupScreenTimeLimit::class.java)
        scope.launch {
          val now = System.currentTimeMillis()
          val notifHelper = TaskNotificationHelper(appContext)
          val screenTimeDao = TaskDatabase.getInstance(appContext).screenTimeDao()
          val allLocal = screenTimeDao.getAllLimitsOnce()
          val remotePackages = remoteLimits.map { it.packageName }.toSet()

          // Remove any previously group-enforced local limits that were lifted remotely
          for (local in allLocal) {
            if (local.isGroupEnforced && local.packageName !in remotePackages) {
              screenTimeDao.deleteLimitByPackage(local.packageName)
              appliedRemoteLimitSignatures.remove(local.packageName)
              ScreenTimeEnforcementEngine.clearTriggeredNotificationKey(local.packageName)
              ScreenTimeEnforcementEngine.resetTrackedForegroundSeconds(appContext, local.packageName)
              notifHelper.cancelRemoteAppLockAlarm(local.packageName)
              SystemOverlayBlocker.dismissOverlay(appContext)
            }
          }

          // Upsert active group-enforced limits
          for (remote in remoteLimits) {
            if (remote.packageName.isBlank()) continue
            val existing = screenTimeDao.getLimitByPackage(remote.packageName)
            val remoteUpdatedMs = remote.updatedAt?.toDate()?.time ?: 0L
            val ruleSignature = "${remote.id}_${remote.lockMode}_${remote.lockAtMillis}_${remote.limitMinutes}_${remote.requireTaskIdToUnlock}_${remoteUpdatedMs}"
            val prevSignature = appliedRemoteLimitSignatures.put(remote.packageName, ruleSignature)
            val isNewOrChangedRule = prevSignature != null && prevSignature != ruleSignature

            if (isNewOrChangedRule || existing == null) {
              ScreenTimeEnforcementEngine.clearTriggeredNotificationKey(remote.packageName)
              ScreenTimeEnforcementEngine.resetTrackedForegroundSeconds(appContext, remote.packageName)
            }

            val parsedMode = remote.parsedLockMode
            val assignedAt = if (existing == null || isNewOrChangedRule) now else existing.assignedAtMillis
            val baselineStatsMins = if (existing == null || isNewOrChangedRule) {
              if (parsedMode == RemoteLockMode.USAGE_LIMIT) {
                ScreenTimeEnforcementEngine.computeRealUsedMinutes(
                  context = appContext,
                  packageName = remote.packageName,
                  period = remote.parsedPeriod,
                  existingUsedMinutes = 0,
                  assignedAtMillis = 0L,
                  baselineStatsMinutes = 0
                )
              } else {
                0
              }
            } else {
              existing.baselineStatsMinutes
            }

            val usedMins = when (parsedMode) {
              RemoteLockMode.LOCK_NOW -> maxOf(existing?.usedMinutes ?: 0, remote.limitMinutes.toInt().coerceAtLeast(1))
              RemoteLockMode.LOCK_AT_TIME -> {
                if (remote.lockAtMillis > 0L && now >= remote.lockAtMillis) {
                  remote.limitMinutes.toInt().coerceAtLeast(1)
                } else {
                  0
                }
              }
              RemoteLockMode.USAGE_LIMIT -> {
                if (isNewOrChangedRule || existing == null) {
                  remote.usedMinutes.toInt()
                } else {
                  existing.usedMinutes
                }
              }
            }

            val entity = ScreenTimeLimitEntity(
              packageName = remote.packageName,
              appName = remote.appName.ifBlank { remote.packageName },
              limitMinutes = remote.limitMinutes.toInt().coerceIn(1, 10080),
              usedMinutes = usedMins,
              periodType = remote.periodType,
              isEnabled = remote.isEnabled,
              isGroupEnforced = true,
              enforcedByName = remote.enforcedByName,
              requiredGroupTaskId = remote.requireTaskIdToUnlock,
              requiredGroupTaskTitle = remote.requireTaskTitle,
              lockMode = remote.lockMode,
              lockAtMillis = remote.lockAtMillis,
              taskInstructions = remote.taskInstructions,
              playBeepOnLock = remote.playBeepOnLock,
              assignedAtMillis = assignedAt,
              baselineStatsMinutes = baselineStatsMins,
              updatedAtMillis = now
            )
            screenTimeDao.upsertLimit(entity)

            if (entity.isEnabled && parsedMode == RemoteLockMode.LOCK_AT_TIME && entity.lockAtMillis > now + 1500L) {
              notifHelper.scheduleRemoteAppLockAlarm(entity)
            } else if (parsedMode != RemoteLockMode.LOCK_AT_TIME) {
              notifHelper.cancelRemoteAppLockAlarm(entity.packageName)
            }

            if (entity.isEnabled && notifiedRemoteLimitKeys.add(ruleSignature)) {
              if (entity.isLimitReachedAt(now)) {
                notifHelper.showRemoteAppLockNotification(
                  limit = entity,
                  isScheduledPreview = false,
                  playBeep = remote.playBeepOnLock
                )
              } else {
                notifHelper.showRemoteAppLockNotification(
                  limit = entity,
                  isScheduledPreview = true,
                  playBeep = false
                )
              }
            }
          }

          ScreenTimeEnforcementEngine.syncUsageAndEnforceForeground(appContext)
        }
      }
  }

  fun enforceRemoteDeviceLockImmediately(context: Context, reason: String) {
    // 1. Lock physical screen immediately via DevicePolicyManager if Device Admin is active
    TasksPlusDeviceAdminReceiver.lockDeviceNow(context)

    // 2. Also show the un-bypassable full-screen Lockout Shield explaining the remote parent/manager lock
    val lockLimit = ScreenTimeLimitEntity(
      packageName = "device.remote.lock",
      appName = "Device Remotely Locked",
      limitMinutes = 1,
      usedMinutes = 1,
      isEnabled = true,
      isGroupEnforced = true,
      enforcedByName = reason.ifBlank { "Group Manager / Parent" },
      requiredGroupTaskTitle = "Ask your Group Manager or Parent to unlock your device in Tasks+"
    )
    ScreenTimeEnforcementEngine.enforceBlockNow(context, lockLimit)
  }

  private fun detachGroupSubListeners() {
    memberSelfReg?.remove()
    groupTasksReg?.remove()
    groupScreenTimeReg?.remove()
    memberSelfReg = null
    groupTasksReg = null
    groupScreenTimeReg = null
    activeGroupId = null
  }

  private fun clearAllListeners() {
    detachGroupSubListeners()
    groupReg?.remove()
    inviteReg?.remove()
    groupReg = null
    inviteReg = null
    activeUid = null
    isRemoteDeviceLocked = false
    isSupervisedChildRole = false
  }
}
