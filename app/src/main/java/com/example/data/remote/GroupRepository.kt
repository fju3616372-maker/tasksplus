package com.example.data.remote

import com.example.data.model.CloudCreditTransaction
import com.example.data.model.CloudGroup
import com.example.data.model.CloudGroupInvite
import com.example.data.model.CloudGroupMember
import com.example.data.model.CloudGroupPerk
import com.example.data.model.CloudGroupRelay
import com.example.data.model.CloudGroupScreenTimeLimit
import com.example.data.model.CloudGroupTask
import com.example.data.model.CloudRelayStep
import com.example.data.model.CreditCalculationResult
import com.example.data.model.CreditCalculator
import com.example.data.model.CreditRules
import com.example.data.model.GroupRole
import com.example.data.model.GroupType
import com.example.data.model.InstalledAppInfo
import com.example.data.model.PerkStatus
import com.example.data.model.ProofStatus
import com.example.data.model.RemoteLockMode
import com.example.data.model.ScreenTimePeriod
import com.example.data.model.TaskPriority
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class GroupRepository(private val firestore: FirebaseFirestore) {

  private fun groupsCollection() = firestore.collection("groups")
  private fun invitesCollection() = firestore.collection("groupInvites")
  private fun membersCollection(groupId: String) =
    groupsCollection().document(groupId).collection("members")
  private fun groupTasksCollection(groupId: String) =
    groupsCollection().document(groupId).collection("groupTasks")
  private fun memberScreenTimeCollection(groupId: String) =
    groupsCollection().document(groupId).collection("memberScreenTime")
  private fun creditTransactionsCollection(groupId: String) =
    groupsCollection().document(groupId).collection("creditTransactions")
  private fun perksCollection(groupId: String) =
    groupsCollection().document(groupId).collection("perks")
  private fun relaysCollection(groupId: String) =
    groupsCollection().document(groupId).collection("relays")

  /**
   * Real-time stream of all Groups where the currently signed-in user is a member.
   */
  fun observeUserGroups(): Flow<List<CloudGroup>> = callbackFlow {
    val uid = Firebase.auth.currentUser?.uid
    if (uid == null) {
      trySend(emptyList())
      close()
      return@callbackFlow
    }
    val reg = groupsCollection()
      .whereArrayContains("memberUids", uid)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, "groups")
          close(error)
          return@addSnapshotListener
        }
        val list = snapshot?.toObjects(CloudGroup::class.java).orEmpty()
        trySend(list)
      }
    awaitClose { reg.remove() }
  }

  /**
   * Real-time stream of pending email invitations sent to the current user's Gmail address,
   * even if the invite was sent before the user ever logged in.
   */
  fun observePendingInvitesForEmail(email: String): Flow<List<CloudGroupInvite>> = callbackFlow {
    val cleanEmail = email.trim().lowercase()
    if (cleanEmail.isBlank()) {
      trySend(emptyList())
      close()
      return@callbackFlow
    }
    val reg = invitesCollection()
      .whereEqualTo("targetEmail", cleanEmail)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, "groupInvites")
          close(error)
          return@addSnapshotListener
        }
        val list = snapshot?.toObjects(CloudGroupInvite::class.java)
          .orEmpty()
          .filter { it.status == "PENDING" }
        trySend(list)
      }
    awaitClose { reg.remove() }
  }

  /**
   * One-shot fetch of pending email invitations for the current user.
   */
  suspend fun fetchPendingInvitesForEmailOnce(email: String): Result<List<CloudGroupInvite>> =
    withContext(Dispatchers.IO) {
      try {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) {
          return@withContext Result.success(emptyList())
        }
        val snapshot = invitesCollection()
          .whereEqualTo("targetEmail", cleanEmail)
          .get()
          .await()
        val list = snapshot.toObjects(CloudGroupInvite::class.java)
          .filter { it.status == "PENDING" }
        Result.success(list)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.LIST, "groupInvites")
        Result.failure(e)
      }
    }

  /**
   * Real-time stream of all invitations sent by a specific Group (filtered by admin UID for rule safety).
   */
  fun observeGroupSentInvites(groupId: String): Flow<List<CloudGroupInvite>> = callbackFlow {
    val uid = Firebase.auth.currentUser?.uid
    if (groupId.isBlank() || uid == null) {
      trySend(emptyList())
      close()
      return@callbackFlow
    }
    val reg = invitesCollection()
      .whereArrayContains("groupAdminUids", uid)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, "groupInvites")
          close(error)
          return@addSnapshotListener
        }
        val list = snapshot?.toObjects(CloudGroupInvite::class.java)
          .orEmpty()
          .filter { it.groupId == groupId }
        trySend(list)
      }
    awaitClose { reg.remove() }
  }

  /**
   * Real-time stream of members in a Group.
   */
  fun observeGroupMembers(groupId: String): Flow<List<CloudGroupMember>> = callbackFlow {
    if (groupId.isBlank()) {
      trySend(emptyList())
      close()
      return@callbackFlow
    }
    val reg = membersCollection(groupId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, "groups/$groupId/members")
          close(error)
          return@addSnapshotListener
        }
        val list = snapshot?.toObjects(CloudGroupMember::class.java).orEmpty()
        trySend(list)
      }
    awaitClose { reg.remove() }
  }

  /**
   * Real-time stream of tasks in a Group.
   */
  fun observeGroupTasks(groupId: String): Flow<List<CloudGroupTask>> = callbackFlow {
    if (groupId.isBlank()) {
      trySend(emptyList())
      close()
      return@callbackFlow
    }
    val reg = groupTasksCollection(groupId)
      .orderBy("dueAtMillis", Query.Direction.ASCENDING)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, "groups/$groupId/groupTasks")
          close(error)
          return@addSnapshotListener
        }
        val list = snapshot?.toObjects(CloudGroupTask::class.java).orEmpty()
        trySend(list)
      }
    awaitClose { reg.remove() }
  }

  /**
   * Real-time stream of remotely enforced screen time limits in a Group.
   */
  fun observeGroupScreenTimeLimits(groupId: String): Flow<List<CloudGroupScreenTimeLimit>> = callbackFlow {
    if (groupId.isBlank()) {
      trySend(emptyList())
      close()
      return@callbackFlow
    }
    val reg = memberScreenTimeCollection(groupId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, "groups/$groupId/memberScreenTime")
          close(error)
          return@addSnapshotListener
        }
        val list = snapshot?.toObjects(CloudGroupScreenTimeLimit::class.java).orEmpty()
        trySend(list)
      }
    awaitClose { reg.remove() }
  }

  /**
   * Creates a new Group with the current user as the primary Group Manager.
   */
  suspend fun createGroupAsManager(
    groupName: String,
    groupType: GroupType
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val user = Firebase.auth.currentUser
        ?: return@withContext Result.failure(IllegalStateException("Must be signed in to create a group"))
      val uid = user.uid
      val email = user.email?.trim()?.lowercase().orEmpty()
      val displayName = user.displayName?.takeIf { it.isNotBlank() }
        ?: email.substringBefore("@").ifBlank { "Group Manager" }

      // Check if user is already in a group or already a manager
      val existingGroups = groupsCollection().whereArrayContains("memberUids", uid).get().await()
      if (!existingGroups.isEmpty) {
        return@withContext Result.failure(
          IllegalStateException("You are already in a group. Leave or remove your manager spot first.")
        )
      }

      val groupId = "grp_${UUID.randomUUID().toString().replace("-", "_").take(24)}"
      val groupData = mapOf(
        "name" to groupName.trim().take(100),
        "groupType" to groupType.name,
        "managerUid" to uid,
        "managerEmail" to email.take(200),
        "managerName" to displayName.take(120),
        "memberUids" to listOf(uid),
        "adminUids" to listOf(uid),
        "invitedEmails" to emptyList<String>(),
        "invitedAdminEmails" to emptyList<String>(),
        "creditSystemEnabled" to false,
        "earlyPointsPerSecond" to 1.0,
        "latePenaltyPerSecond" to 1.0,
        "creditGracePeriodSeconds" to 0L,
        "maxCreditPerTask" to 0L,
        "maxPenaltyPerTask" to 0L,
        "allowNegativeCredits" to true,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      groupsCollection().document(groupId).set(groupData).await()

      val managerMemberData = mapOf(
        "groupId" to groupId,
        "uid" to uid,
        "email" to email.take(200),
        "displayName" to displayName.take(120),
        "role" to GroupRole.MANAGER.name,
        "canManageOthers" to true,
        "remoteDeviceLocked" to false,
        "remoteLockReason" to "",
        "installedApps" to emptyList<Map<String, String>>(),
        "appsLastSyncedAtMillis" to 0L,
        "creditBalance" to 0L,
        "totalEarnedCredits" to 0L,
        "totalDeductedCredits" to 0L,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      membersCollection(groupId).document(uid).set(managerMemberData).await()
      Result.success(groupId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, "groups")
      Result.failure(e)
    }
  }

  /**
   * Removes the current user's Group Manager spot (dissolves the group if they are the primary manager,
   * or leaves the group if they are a member) so they can join another group.
   */
  suspend fun removeManagerSpotOrLeaveGroup(group: CloudGroup): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val uid = Firebase.auth.currentUser?.uid
        ?: return@withContext Result.failure(IllegalStateException("Not signed in"))

      if (group.managerUid == uid) {
        // Delete subcollection docs then sent invites then delete group
        val members = membersCollection(group.id).get().await()
        for (doc in members.documents) {
          doc.reference.delete().await()
        }
        val tasks = groupTasksCollection(group.id).get().await()
        for (doc in tasks.documents) {
          doc.reference.delete().await()
        }
        val limits = memberScreenTimeCollection(group.id).get().await()
        for (doc in limits.documents) {
          doc.reference.delete().await()
        }
        val txs = creditTransactionsCollection(group.id).get().await()
        for (doc in txs.documents) {
          doc.reference.delete().await()
        }
        val perks = perksCollection(group.id).get().await()
        for (doc in perks.documents) {
          doc.reference.delete().await()
        }
        val invites = invitesCollection().whereArrayContains("groupAdminUids", uid).get().await()
        for (doc in invites.documents) {
          if (doc.getString("groupId") == group.id) {
            doc.reference.delete().await()
          }
        }
        groupsCollection().document(group.id).delete().await()
      } else {
        val groupRef = groupsCollection().document(group.id)
        val snap = groupRef.get().await()
        if (snap.exists()) {
          val freshGroup = snap.toObject(CloudGroup::class.java) ?: group
          val updatedMembers = freshGroup.memberUids.filter { it != uid }.ifEmpty { listOf(freshGroup.managerUid) }
          val updatedAdmins = freshGroup.adminUids.filter { it != uid }.ifEmpty { listOf(freshGroup.managerUid) }
          groupRef.update(
            mapOf(
              "memberUids" to updatedMembers,
              "adminUids" to updatedAdmins,
              "updatedAt" to FieldValue.serverTimestamp()
            )
          ).await()
        }
        membersCollection(group.id).document(uid).delete().await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, "groups/${group.id}")
      Result.failure(e)
    }
  }

  /**
   * Sends a Group join request to a target Gmail / email address with a proposed role
   * (`PARENT_ADMIN`, `MEMBER`, or `CHILD`). Works even before that email has signed into Tasks+.
   */
  suspend fun sendGroupInviteByEmail(
    group: CloudGroup,
    targetEmail: String,
    proposedRole: GroupRole
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val user = Firebase.auth.currentUser
        ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
      val cleanEmail = targetEmail.trim().lowercase()
      if (!cleanEmail.contains("@")) {
        return@withContext Result.failure(IllegalArgumentException("Enter a valid email address"))
      }

      val normalizedRole = if (proposedRole == GroupRole.MANAGER) GroupRole.PARENT_ADMIN else proposedRole
      val groupRef = groupsCollection().document(group.id)
      val groupSnap = groupRef.get().await()
      val freshGroup = groupSnap.toObject(CloudGroup::class.java) ?: group
      val freshAdmins = freshGroup.adminUids.ifEmpty { listOf(freshGroup.managerUid) }

      val updatedInvited = (freshGroup.invitedEmails + cleanEmail).distinct().take(50)
      val updatedInvitedAdmins = if (normalizedRole == GroupRole.PARENT_ADMIN) {
        (freshGroup.invitedAdminEmails + cleanEmail).distinct().take(50)
      } else {
        freshGroup.invitedAdminEmails.filterNot { it.equals(cleanEmail, ignoreCase = true) }
      }
      groupRef.update(
        mapOf(
          "invitedEmails" to updatedInvited,
          "invitedAdminEmails" to updatedInvitedAdmins,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()

      val inviteId = "inv_${group.id}_${cleanEmail.replace(Regex("[^a-zA-Z0-9]"), "_")}".take(120)
      val senderName = user.displayName?.takeIf { it.isNotBlank() }
        ?: user.email?.substringBefore("@") ?: "Group Manager"
      val docRef = invitesCollection().document(inviteId)
      val existingInvites = invitesCollection()
        .whereArrayContains("groupAdminUids", user.uid)
        .get()
        .await()
      val existing = existingInvites.documents.firstOrNull { it.id == inviteId }

      val payload = mutableMapOf<String, Any>(
        "groupId" to group.id,
        "groupName" to freshGroup.name.take(100),
        "groupType" to freshGroup.groupType,
        "senderUid" to user.uid,
        "senderName" to senderName.take(120),
        "senderEmail" to user.email.orEmpty().take(200),
        "targetEmail" to cleanEmail.take(200),
        "proposedRole" to normalizedRole.name,
        "groupAdminUids" to freshAdmins,
        "status" to "PENDING",
        "acceptedByUid" to "",
        "updatedAt" to FieldValue.serverTimestamp()
      )
      if (existing == null || !existing.exists()) {
        payload["createdAt"] = FieldValue.serverTimestamp()
        docRef.set(payload).await()
      } else {
        payload["createdAt"] = existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
        docRef.set(payload, SetOptions.merge()).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "groupInvites")
      Result.failure(e)
    }
  }

  /**
   * Accepts a pending email invitation and adds the user to the Group with the invited role.
   * Blocks acceptance if the user is currently a Group Manager of another group.
   */
  suspend fun acceptGroupInvite(invite: CloudGroupInvite): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val user = Firebase.auth.currentUser
        ?: return@withContext Result.failure(IllegalStateException("Must be signed in"))
      val uid = user.uid
      val email = user.email?.trim()?.lowercase().orEmpty()
      val displayName = user.displayName?.takeIf { it.isNotBlank() }
        ?: email.substringBefore("@").ifBlank { "Member" }

      // Enforce Manager exclusivity rule:
      val currentGroups = groupsCollection().whereArrayContains("memberUids", uid).get().await()
        .toObjects(CloudGroup::class.java)
      if (currentGroups.any { it.managerUid == uid && it.id != invite.groupId }) {
        return@withContext Result.failure(
          IllegalStateException("You are a Group Manager. Remove your spot as Group Manager before joining another group.")
        )
      }

      val groupRef = groupsCollection().document(invite.groupId)
      val groupSnap = groupRef.get().await()
      if (!groupSnap.exists()) {
        return@withContext Result.failure(IllegalStateException("This group no longer exists."))
      }
      val group = groupSnap.toObject(CloudGroup::class.java)
        ?: return@withContext Result.failure(IllegalStateException("Invalid group data"))

      val role = GroupRole.fromName(invite.proposedRole)
      val isAdmin = role == GroupRole.PARENT_ADMIN || role == GroupRole.MANAGER
      val newMemberUids = (group.memberUids + uid).distinct()
      val newAdminUids = if (isAdmin) (group.adminUids + uid).distinct() else group.adminUids
      val remainingInvited = group.invitedEmails.filterNot { it.equals(email, ignoreCase = true) }
      val remainingInvitedAdmins = group.invitedAdminEmails.filterNot { it.equals(email, ignoreCase = true) }

      groupRef.update(
        mapOf(
          "memberUids" to newMemberUids,
          "adminUids" to newAdminUids,
          "invitedEmails" to remainingInvited,
          "invitedAdminEmails" to remainingInvitedAdmins,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()

      val memberRef = membersCollection(invite.groupId).document(uid)
      val existingMember = memberRef.get().await()
      val memberPayload = mutableMapOf<String, Any>(
        "groupId" to invite.groupId,
        "uid" to uid,
        "email" to email.take(200),
        "displayName" to displayName.take(120),
        "role" to role.name,
        "canManageOthers" to isAdmin,
        "remoteDeviceLocked" to false,
        "remoteLockReason" to "",
        "installedApps" to emptyList<Map<String, String>>(),
        "appsLastSyncedAtMillis" to 0L,
        "creditBalance" to (existingMember.getLong("creditBalance") ?: 0L),
        "totalEarnedCredits" to (existingMember.getLong("totalEarnedCredits") ?: 0L),
        "totalDeductedCredits" to (existingMember.getLong("totalDeductedCredits") ?: 0L),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      if (!existingMember.exists()) {
        memberPayload["createdAt"] = FieldValue.serverTimestamp()
        memberRef.set(memberPayload).await()
      } else {
        memberPayload["createdAt"] = existingMember.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
        memberRef.set(memberPayload, SetOptions.merge()).await()
      }

      val inviteRef = invitesCollection().document(invite.id)
      val inviteSnap = inviteRef.get().await()
      if (inviteSnap.exists()) {
        inviteRef.update(
          mapOf(
            "status" to "ACCEPTED",
            "acceptedByUid" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
      }

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "groups/${invite.groupId}")
      Result.failure(e)
    }
  }

  suspend fun declineGroupInvite(invite: CloudGroupInvite): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val inviteRef = invitesCollection().document(invite.id)
      val snap = inviteRef.get().await()
      if (snap.exists()) {
        inviteRef.update(
          mapOf(
            "status" to "DECLINED",
            "acceptedByUid" to "",
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "groupInvites/${invite.id}")
      Result.failure(e)
    }
  }

  /**
   * Updates a Group Member's role (`PARENT_ADMIN`, `MEMBER`, `CHILD`), management permission
   * (`canManageOthers`), or cloud-toggled Remote Device Lock (`remoteDeviceLocked`).
   */
  suspend fun updateMemberRoleOrDeviceLock(
    group: CloudGroup,
    member: CloudGroupMember,
    newRole: GroupRole = member.parsedRole,
    canManageOthers: Boolean = member.canManageOthers,
    remoteDeviceLocked: Boolean = member.remoteDeviceLocked,
    remoteLockReason: String = member.remoteLockReason
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val memberRef = membersCollection(group.id).document(member.uid)
      val snap = memberRef.get().await()
      val effectiveAdmin = canManageOthers || newRole == GroupRole.PARENT_ADMIN || newRole == GroupRole.MANAGER
      val payload = mapOf(
        "role" to newRole.name,
        "canManageOthers" to effectiveAdmin,
        "remoteDeviceLocked" to remoteDeviceLocked,
        "remoteLockReason" to remoteLockReason.take(300),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      if (snap.exists()) {
        memberRef.update(payload).await()
      } else {
        val createPayload = payload + mapOf(
          "groupId" to group.id,
          "uid" to member.uid,
          "email" to member.email.take(200),
          "displayName" to member.displayName.take(120).ifBlank { "Member" },
          "installedApps" to emptyList<Map<String, String>>(),
          "appsLastSyncedAtMillis" to 0L,
          "createdAt" to FieldValue.serverTimestamp()
        )
        memberRef.set(createPayload).await()
      }

      // Keep group.adminUids in sync
      val groupRef = groupsCollection().document(group.id)
      val groupSnap = groupRef.get().await()
      if (groupSnap.exists()) {
        val freshGroup = groupSnap.toObject(CloudGroup::class.java) ?: group
        val updatedAdmins = if (effectiveAdmin) {
          (freshGroup.adminUids + member.uid).distinct()
        } else {
          (freshGroup.adminUids - member.uid).ifEmpty { listOf(freshGroup.managerUid) }
        }
        groupRef.update(
          mapOf(
            "adminUids" to updatedAdmins,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "groups/${group.id}/members/${member.uid}")
      Result.failure(e)
    }
  }

  /**
   * Collects the signed-in user's device app names & package names and syncs them to their
   * Group Member document so Group Managers/Parents can view & lock apps on that device.
   */
  suspend fun syncCurrentUserInstalledApps(
    groupId: String,
    apps: List<InstalledAppInfo>
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val user = Firebase.auth.currentUser
        ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
      if (groupId.isBlank()) {
        return@withContext Result.failure(IllegalArgumentException("Missing groupId"))
      }

      val distinctApps = apps
        .filter { it.packageName.isNotBlank() }
        .distinctBy { it.packageName }
        .take(250)
        .map { app ->
          mapOf(
            "packageName" to app.packageName.trim().take(200),
            "appName" to app.appName.trim().ifBlank { app.packageName }.take(120)
          )
        }

      val displayName = user.displayName?.takeIf { it.isNotBlank() }
        ?: user.email?.substringBefore("@")?.ifBlank { "Member" }
        ?: "Member"

      val memberRef = membersCollection(groupId).document(user.uid)
      val snap = memberRef.get().await()
      if (snap.exists()) {
        memberRef.update(
          mapOf(
            "installedApps" to distinctApps,
            "appsLastSyncedAtMillis" to System.currentTimeMillis(),
            "displayName" to displayName.take(120),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "groups/$groupId/members")
      Result.failure(e)
    }
  }

  /**
   * Assigns a task to the entire Group (`assignedToUid = "ALL"`) or a specific member,
   * optionally gating a specific app until the task is completed and verified via Photo Proof
   * (either manually by a Manager/Parent or automatically by Gemini AI).
   */
  suspend fun createOrUpdateGroupTask(
    groupId: String,
    existingTaskId: String? = null,
    title: String,
    subtext: String,
    assignedToUid: String,
    assignedToName: String,
    priority: TaskPriority,
    dueAtMillis: Long,
    reminderIntervalMinutes: Int,
    ringAlarmBell: Boolean,
    gatedAppPackage: String = "",
    gatedAppName: String = "",
    unlockRewardMinutes: Int = 0,
    requiresPhotoProof: Boolean = false,
    useGeminiAutoApproval: Boolean = false,
    geminiVerificationPrompt: String = "",
    lockMode: RemoteLockMode = RemoteLockMode.LOCK_NOW,
    lockAtMillis: Long = 0L,
    playBeepOnLock: Boolean = ringAlarmBell,
    urgencyLevel: Int = priority.defaultUrgencyLevel
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val user = Firebase.auth.currentUser
        ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
      val taskId = existingTaskId ?: "gtask_${UUID.randomUUID().toString().replace("-", "_").take(24)}"
      val docRef = groupTasksCollection(groupId).document(taskId)
      val existing = docRef.get().await()
      val creatorName = user.displayName?.takeIf { it.isNotBlank() }
        ?: user.email?.substringBefore("@") ?: "Manager"

      val initialProofStatus = if (requiresPhotoProof) {
        ProofStatus.PENDING_PROOF.name
      } else {
        ProofStatus.NONE.name
      }

      val payload = mutableMapOf<String, Any>(
        "groupId" to groupId,
        "title" to title.trim().take(200),
        "subtext" to subtext.trim().take(500),
        "createdByUid" to (existing.getString("createdByUid") ?: user.uid),
        "createdByName" to (existing.getString("createdByName") ?: creatorName.take(120)),
        "assignedToUid" to assignedToUid.take(128),
        "assignedToName" to assignedToName.take(120).ifBlank { "Everyone in Group" },
        "priority" to priority.name,
        "urgencyLevel" to urgencyLevel.coerceIn(1, 5).toLong(),
        "dueAtMillis" to dueAtMillis.coerceAtLeast(0L),
        "reminderIntervalMinutes" to reminderIntervalMinutes.coerceIn(0, 10080).toLong(),
        "ringAlarmBell" to ringAlarmBell,
        "gatedAppPackage" to gatedAppPackage.trim().take(200),
        "gatedAppName" to gatedAppName.trim().take(200),
        "unlockRewardMinutes" to unlockRewardMinutes.coerceIn(0, 10080).toLong(),
        "requiresPhotoProof" to requiresPhotoProof,
        "useGeminiAutoApproval" to useGeminiAutoApproval,
        "geminiVerificationPrompt" to geminiVerificationPrompt.trim().take(1000),
        "proofStatus" to (existing.getString("proofStatus")?.takeIf { existingTaskId != null } ?: initialProofStatus),
        "proofImageBase64" to (existing.getString("proofImageBase64") ?: ""),
        "proofNote" to (existing.getString("proofNote") ?: ""),
        "geminiEvaluationResult" to (existing.getString("geminiEvaluationResult") ?: ""),
        "isCompleted" to (existing.getBoolean("isCompleted") ?: false),
        "completedByUid" to (existing.getString("completedByUid") ?: ""),
        "completedByName" to (existing.getString("completedByName") ?: ""),
        "completedAtMillis" to (existing.getLong("completedAtMillis") ?: 0L),
        "creditsDelta" to (existing.getLong("creditsDelta") ?: 0L),
        "creditSecondsDelta" to (existing.getLong("creditSecondsDelta") ?: 0L),
        "creditsApplied" to (existing.getBoolean("creditsApplied") ?: false),
        "proofSubmittedAtMillis" to (existing.getLong("proofSubmittedAtMillis") ?: 0L),
        "proofSubmittedByUid" to (existing.getString("proofSubmittedByUid") ?: ""),
        "updatedAt" to FieldValue.serverTimestamp()
      )

      if (!existing.exists()) {
        payload["createdAt"] = FieldValue.serverTimestamp()
        docRef.set(payload).await()
      } else {
        payload["createdAt"] = existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
        docRef.set(payload, SetOptions.merge()).await()
      }

      // If this task gates a specific app for a specific member, also create/update their GroupScreenTimeLimit!
      if (gatedAppPackage.isNotBlank() && assignedToUid != "ALL") {
        setMemberScreenTimeLimit(
          groupId = groupId,
          targetUid = assignedToUid,
          targetMemberName = assignedToName,
          packageName = gatedAppPackage,
          appName = gatedAppName.ifBlank { gatedAppPackage },
          limitMinutes = 1, // Locked according to lockMode until task is completed!
          period = ScreenTimePeriod.DAILY,
          requireTaskIdToUnlock = taskId,
          requireTaskTitle = title.trim(),
          lockMode = lockMode,
          lockAtMillis = lockAtMillis,
          taskInstructions = subtext.trim(),
          playBeepOnLock = playBeepOnLock
        )
      }

      Result.success(taskId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "groups/$groupId/groupTasks")
      Result.failure(e)
    }
  }

  fun observeGroupCreditTransactions(groupId: String): Flow<List<CloudCreditTransaction>> = callbackFlow {
    if (groupId.isBlank()) {
      trySend(emptyList())
      close()
      return@callbackFlow
    }
    val reg = creditTransactionsCollection(groupId)
      .orderBy("createdAt", Query.Direction.DESCENDING)
      .limit(150)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, "groups/$groupId/creditTransactions")
          close(error)
          return@addSnapshotListener
        }
        val list = snapshot?.toObjects(CloudCreditTransaction::class.java).orEmpty()
        trySend(list)
      }
    awaitClose { reg.remove() }
  }

  fun observeGroupPerks(groupId: String): Flow<List<CloudGroupPerk>> = callbackFlow {
    if (groupId.isBlank()) {
      trySend(emptyList())
      close()
      return@callbackFlow
    }
    val reg = perksCollection(groupId)
      .orderBy("createdAt", Query.Direction.DESCENDING)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, "groups/$groupId/perks")
          close(error)
          return@addSnapshotListener
        }
        val list = snapshot?.toObjects(CloudGroupPerk::class.java).orEmpty()
        trySend(list)
      }
    awaitClose { reg.remove() }
  }

  /**
   * Updates the Group's configurable optional points rules (only permitted for the main Group Manager).
   */
  suspend fun updateGroupCreditRules(
    groupId: String,
    rules: CreditRules
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val user = Firebase.auth.currentUser
        ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
      val groupSnap = groupsCollection().document(groupId).get().await()
      val managerUid = groupSnap.getString("managerUid").orEmpty()
      if (managerUid.isNotBlank() && managerUid != user.uid) {
        return@withContext Result.failure(
          IllegalStateException("Only the main Group Manager can configure group points rules.")
        )
      }
      val clean = rules.normalized()
      groupsCollection().document(groupId).update(
        mapOf(
          "creditSystemEnabled" to clean.enabled,
          "earlyPointsPerSecond" to clean.earlyPointsPerSecond,
          "latePenaltyPerSecond" to clean.latePenaltyPerSecond,
          "creditGracePeriodSeconds" to clean.gracePeriodSeconds,
          "maxCreditPerTask" to clean.maxCreditPerTask,
          "maxPenaltyPerTask" to clean.maxPenaltyPerTask,
          "allowNegativeCredits" to clean.allowNegativeBalance,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "groups/$groupId")
      Result.failure(e)
    }
  }

  /**
   * Manually awards or deducts group points for a specific member (main Group Manager only).
   */
  suspend fun awardOrDeductMemberCredits(
    group: CloudGroup,
    targetMember: CloudGroupMember,
    pointsDelta: Long,
    reason: String
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val user = Firebase.auth.currentUser
        ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
      if (user.uid != group.managerUid) {
        return@withContext Result.failure(
          IllegalStateException("Only the main Group Manager can manually award or deduct points.")
        )
      }
      val actorName = user.displayName?.takeIf { it.isNotBlank() }
        ?: user.email?.substringBefore("@") ?: "Group Manager"

      val memberRef = membersCollection(group.id).document(targetMember.uid)
      val snap = memberRef.get().await()
      val currentBalance = snap.getLong("creditBalance") ?: targetMember.creditBalance
      val currentEarned = snap.getLong("totalEarnedCredits") ?: targetMember.totalEarnedCredits
      val currentDeducted = snap.getLong("totalDeductedCredits") ?: targetMember.totalDeductedCredits

      val newBalance = CreditCalculator.applyBalanceDelta(
        currentBalance = currentBalance,
        delta = pointsDelta,
        allowNegativeBalance = group.creditRules.allowNegativeBalance
      )
      val effectiveAppliedDelta = newBalance - currentBalance
      val newEarned = if (pointsDelta > 0L) currentEarned + pointsDelta else currentEarned
      val newDeducted = if (pointsDelta < 0L) currentDeducted + (-pointsDelta) else currentDeducted

      memberRef.update(
        mapOf(
          "creditBalance" to newBalance,
          "totalEarnedCredits" to newEarned,
          "totalDeductedCredits" to newDeducted,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()

      val txId = "ctx_${UUID.randomUUID().toString().replace("-", "_").take(24)}"
      val txPayload = mapOf(
        "userId" to targetMember.uid,
        "userName" to targetMember.displayName.ifBlank { targetMember.email.substringBefore("@") }.take(120),
        "groupId" to group.id,
        "taskId" to "",
        "taskTitle" to "Manual Adjustment",
        "pointsDelta" to effectiveAppliedDelta,
        "secondsDelta" to 0L,
        "reason" to reason.trim().ifBlank {
          if (pointsDelta >= 0L) "Manager bonus" else "Manager deduction"
        }.take(300),
        "awardedByUid" to user.uid,
        "awardedByName" to actorName.take(120),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      creditTransactionsCollection(group.id).document(txId).set(txPayload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "groups/${group.id}/creditTransactions")
      Result.failure(e)
    }
  }

  /**
   * Main Group Manager assigns a specific perk to another member (cannot target self or ALL).
   */
  suspend fun assignGroupPerk(
    group: CloudGroup,
    targetMember: CloudGroupMember,
    title: String,
    description: String,
    pointsCost: Long,
    grantImmediately: Boolean = false
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val user = Firebase.auth.currentUser
        ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
      if (user.uid != group.managerUid) {
        return@withContext Result.failure(
          IllegalStateException("Only the main Group Manager can assign perks.")
        )
      }
      if (targetMember.uid == user.uid || targetMember.uid !in group.memberUids) {
        return@withContext Result.failure(
          IllegalStateException("Perks must be assigned to another member of this group.")
        )
      }
      val cleanTitle = title.trim().take(120)
      if (cleanTitle.isBlank()) {
        return@withContext Result.failure(IllegalStateException("Perk title cannot be empty."))
      }
      val managerName = user.displayName?.takeIf { it.isNotBlank() }
        ?: user.email?.substringBefore("@")
        ?: group.managerName.ifBlank { "Group Manager" }
      val targetName = targetMember.displayName.ifBlank {
        targetMember.email.substringBefore("@").ifBlank { "Member" }
      }.take(120)
      val perkId = "perk_${UUID.randomUUID().toString().replace("-", "_").take(24)}"
      val safeCost = pointsCost.coerceIn(0L, 1_000_000_000L)
      val status = if (grantImmediately) PerkStatus.GRANTED else PerkStatus.ASSIGNED
      val payload = mapOf(
        "groupId" to group.id,
        "managerUid" to user.uid,
        "assignedToUid" to targetMember.uid,
        "assignedToName" to targetName,
        "assignedByName" to managerName.take(120),
        "title" to cleanTitle,
        "description" to description.trim().take(500),
        "pointsCost" to safeCost,
        "perkStatus" to status.name,
        "redeemedCount" to 0L,
        "redeemedAtMillis" to 0L,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      perksCollection(group.id).document(perkId).set(payload).await()
      Result.success(perkId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, "groups/${group.id}/perks")
      Result.failure(e)
    }
  }

  /**
   * Atomically redeems an assigned group perk for its assignee when Group Points are enabled.
   */
  suspend fun redeemGroupPerk(
    group: CloudGroup,
    perk: CloudGroupPerk
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val user = Firebase.auth.currentUser
        ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
      if (perk.assignedToUid != user.uid) {
        return@withContext Result.failure(
          IllegalStateException("Only ${perk.assignedToName} can redeem this perk.")
        )
      }
      val groupRef = groupsCollection().document(group.id)
      val perkRef = perksCollection(group.id).document(perk.id)
      val memberRef = membersCollection(group.id).document(user.uid)
      val now = System.currentTimeMillis()
      val txId = "ctx_${UUID.randomUUID().toString().replace("-", "_").take(24)}"
      val txRef = creditTransactionsCollection(group.id).document(txId)

      firestore.runTransaction { transaction ->
        val groupSnap = transaction.get(groupRef)
        val perkSnap = transaction.get(perkRef)
        val memberSnap = transaction.get(memberRef)

        val pointsEnabled = groupSnap.getBoolean("creditSystemEnabled") ?: false
        if (!pointsEnabled) {
          throw IllegalStateException("Group points are off. Ask the Group Manager to grant this perk directly.")
        }
        val currentStatus = perkSnap.getString("perkStatus") ?: PerkStatus.ASSIGNED.name
        if (currentStatus != PerkStatus.ASSIGNED.name) {
          throw IllegalStateException("This perk is not currently available to redeem.")
        }
        val cost = (perkSnap.getLong("pointsCost") ?: perk.pointsCost).coerceAtLeast(0L)
        val currentBalance = memberSnap.getLong("creditBalance") ?: 0L
        val currentDeducted = memberSnap.getLong("totalDeductedCredits") ?: 0L
        if (cost > 0L && currentBalance < cost) {
          throw IllegalStateException("Need ${cost - currentBalance} more points to redeem ${perk.title}.")
        }
        val currentRedeemedCount = perkSnap.getLong("redeemedCount") ?: perk.redeemedCount
        val memberName = memberSnap.getString("displayName")
          ?.takeIf { it.isNotBlank() }
          ?: perk.assignedToName.ifBlank { user.displayName ?: "Member" }

        transaction.update(
          perkRef,
          mapOf(
            "perkStatus" to PerkStatus.REDEEMED.name,
            "redeemedCount" to (currentRedeemedCount + 1L),
            "redeemedAtMillis" to now,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        )

        if (cost > 0L) {
          transaction.update(
            memberRef,
            mapOf(
              "creditBalance" to (currentBalance - cost),
              "totalDeductedCredits" to (currentDeducted + cost),
              "updatedAt" to FieldValue.serverTimestamp()
            )
          )
          transaction.set(
            txRef,
            mapOf(
              "userId" to user.uid,
              "userName" to memberName.take(120),
              "groupId" to group.id,
              "taskId" to "perk_${perk.id}".take(128),
              "taskTitle" to "Perk: ${perk.title}".take(200),
              "pointsDelta" to -cost,
              "secondsDelta" to 0L,
              "reason" to "Redeemed group perk: ${perk.title}".take(300),
              "awardedByUid" to user.uid,
              "awardedByName" to memberName.take(120),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp()
            )
          )
        }
        null
      }.await()

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "groups/${group.id}/perks/${perk.id}")
      Result.failure(e)
    }
  }

  /**
   * Main Group Manager updates perk status (e.g., GRANTED or reset to ASSIGNED).
   */
  suspend fun updateGroupPerkStatus(
    group: CloudGroup,
    perk: CloudGroupPerk,
    status: PerkStatus
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val user = Firebase.auth.currentUser
        ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
      if (user.uid != group.managerUid) {
        return@withContext Result.failure(
          IllegalStateException("Only the main Group Manager can update perks.")
        )
      }
      perksCollection(group.id).document(perk.id).update(
        mapOf(
          "perkStatus" to status.name,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "groups/${group.id}/perks/${perk.id}")
      Result.failure(e)
    }
  }

  /**
   * Main Group Manager deletes a group perk.
   */
  suspend fun deleteGroupPerk(
    group: CloudGroup,
    perkId: String
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val user = Firebase.auth.currentUser
        ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
      if (user.uid != group.managerUid) {
        return@withContext Result.failure(
          IllegalStateException("Only the main Group Manager can remove perks.")
        )
      }
      perksCollection(group.id).document(perkId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, "groups/${group.id}/perks/$perkId")
      Result.failure(e)
    }
  }

  /**
   * Submits task completion or photo proof (and optional Gemini AI auto-verification result),
   * computing early-completion bonus or late-completion penalty points according to group rules.
   */
  suspend fun updateGroupTaskCompletionOrProof(
    task: CloudGroupTask,
    proofStatus: ProofStatus,
    proofImageBase64: String = task.proofImageBase64,
    proofNote: String = task.proofNote,
    geminiEvaluationResult: String = task.geminiEvaluationResult,
    isCompleted: Boolean,
    completedByName: String,
    onCreditCalculated: ((CreditCalculationResult) -> Unit)? = null
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val docRef = groupTasksCollection(task.groupId).document(task.id)
      val existing = docRef.get().await()
      if (!existing.exists()) return@withContext Result.failure(IllegalStateException("Task not found"))

      val currentUser = Firebase.auth.currentUser
      val currentUid = currentUser?.uid.orEmpty()
      val existingTask = existing.toObject(CloudGroupTask::class.java) ?: task
      val now = System.currentTimeMillis()

      val resolvedProofSubmittedAt = when (proofStatus) {
        ProofStatus.PENDING_APPROVAL, ProofStatus.APPROVED ->
          existingTask.proofSubmittedAtMillis.takeIf { it > 0L } ?: now
        ProofStatus.REJECTED -> 0L
        else -> existingTask.proofSubmittedAtMillis
      }
      val resolvedProofSubmittedByUid = when (proofStatus) {
        ProofStatus.PENDING_APPROVAL, ProofStatus.APPROVED ->
          existingTask.proofSubmittedByUid.ifBlank { currentUid }
        ProofStatus.REJECTED -> ""
        else -> existingTask.proofSubmittedByUid
      }

      var resolvedCompletedByUid = existingTask.completedByUid
      var resolvedCreditsDelta = existingTask.creditsDelta
      var resolvedCreditSecondsDelta = existingTask.creditSecondsDelta
      var resolvedCreditsApplied = existingTask.creditsApplied
      var pendingCreditResult: CreditCalculationResult? = null
      var recipientUid = ""
      var effectiveAllowNegative = true

      if (isCompleted) {
        recipientUid = when {
          task.assignedToUid != "ALL" -> task.assignedToUid
          resolvedProofSubmittedByUid.isNotBlank() -> resolvedProofSubmittedByUid
          else -> currentUid
        }
        resolvedCompletedByUid = recipientUid

        if (!existingTask.creditsApplied) {
          val groupSnap = groupsCollection().document(task.groupId).get().await()
          val rules = groupSnap.toObject(CloudGroup::class.java)?.creditRules ?: CreditRules(enabled = false)
          effectiveAllowNegative = rules.allowNegativeBalance
          val effectiveFinishedAt = if (task.requiresPhotoProof && resolvedProofSubmittedAt > 0L) {
            resolvedProofSubmittedAt
          } else {
            now
          }
          val calc = CreditCalculator.calculateTaskCredit(
            deadlineMillis = task.dueAtMillis,
            completedAtMillis = effectiveFinishedAt,
            rules = rules
          )
          resolvedCreditsDelta = calc.pointsDelta
          resolvedCreditSecondsDelta = calc.secondsDelta
          resolvedCreditsApplied = true
          if (rules.enabled) {
            pendingCreditResult = calc
          }
        }
      }

      val payload = mapOf(
        "groupId" to task.groupId,
        "title" to task.title.take(200),
        "subtext" to task.subtext.take(500),
        "createdByUid" to task.createdByUid,
        "createdByName" to task.createdByName,
        "assignedToUid" to task.assignedToUid,
        "assignedToName" to task.assignedToName,
        "priority" to task.priority,
        "dueAtMillis" to task.dueAtMillis,
        "reminderIntervalMinutes" to task.reminderIntervalMinutes,
        "ringAlarmBell" to task.ringAlarmBell,
        "gatedAppPackage" to task.gatedAppPackage,
        "gatedAppName" to task.gatedAppName,
        "unlockRewardMinutes" to task.unlockRewardMinutes,
        "requiresPhotoProof" to task.requiresPhotoProof,
        "useGeminiAutoApproval" to task.useGeminiAutoApproval,
        "geminiVerificationPrompt" to task.geminiVerificationPrompt.take(1000),
        "proofStatus" to proofStatus.name,
        "proofImageBase64" to proofImageBase64.take(350000),
        "proofNote" to proofNote.take(500),
        "geminiEvaluationResult" to geminiEvaluationResult.take(2000),
        "isCompleted" to isCompleted,
        "completedByUid" to resolvedCompletedByUid.take(128),
        "completedByName" to completedByName.take(120),
        "completedAtMillis" to if (isCompleted) now else 0L,
        "creditsDelta" to resolvedCreditsDelta,
        "creditSecondsDelta" to resolvedCreditSecondsDelta,
        "creditsApplied" to resolvedCreditsApplied,
        "proofSubmittedAtMillis" to resolvedProofSubmittedAt,
        "proofSubmittedByUid" to resolvedProofSubmittedByUid.take(128),
        "createdAt" to (existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      docRef.set(payload, SetOptions.merge()).await()

      if (isCompleted && pendingCreditResult != null && recipientUid.isNotBlank()) {
        val calc = pendingCreditResult!!
        if (calc.pointsDelta != 0L) {
          val memberRef = membersCollection(task.groupId).document(recipientUid)
          val memberSnap = memberRef.get().await()
          if (memberSnap.exists()) {
            val currentBal = memberSnap.getLong("creditBalance") ?: 0L
            val currentEarned = memberSnap.getLong("totalEarnedCredits") ?: 0L
            val currentDeducted = memberSnap.getLong("totalDeductedCredits") ?: 0L
            val newBal = CreditCalculator.applyBalanceDelta(
              currentBalance = currentBal,
              delta = calc.pointsDelta,
              allowNegativeBalance = effectiveAllowNegative
            )
            val appliedDelta = newBal - currentBal
            val newEarned = if (calc.pointsDelta > 0L) currentEarned + calc.pointsDelta else currentEarned
            val newDeducted = if (calc.pointsDelta < 0L) currentDeducted + (-calc.pointsDelta) else currentDeducted

            memberRef.update(
              mapOf(
                "creditBalance" to newBal,
                "totalEarnedCredits" to newEarned,
                "totalDeductedCredits" to newDeducted,
                "updatedAt" to FieldValue.serverTimestamp()
              )
            ).await()

            val targetName = memberSnap.getString("displayName")
              ?.takeIf { it.isNotBlank() }
              ?: completedByName.ifBlank { task.assignedToName }
            val actorName = currentUser?.displayName?.takeIf { it.isNotBlank() }
              ?: completedByName.ifBlank { "Member" }
            val txId = "ctx_${UUID.randomUUID().toString().replace("-", "_").take(24)}"
            val txPayload = mapOf(
              "userId" to recipientUid,
              "userName" to targetName.take(120),
              "groupId" to task.groupId,
              "taskId" to task.id,
              "taskTitle" to task.title.take(200),
              "pointsDelta" to appliedDelta,
              "secondsDelta" to calc.secondsDelta,
              "reason" to CreditCalculator.buildReasonText(calc).take(300),
              "awardedByUid" to currentUid,
              "awardedByName" to actorName.take(120),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp()
            )
            creditTransactionsCollection(task.groupId).document(txId).set(txPayload).await()
          }
        }
        onCreditCalculated?.invoke(calc)
      }

      // If approved & completed, unlock or grant reward minutes on any gated app or linked limit!
      if (isCompleted) {
        val effectiveTargetUid = if (task.assignedToUid == "ALL") {
          currentUid
        } else {
          task.assignedToUid
        }
        if (effectiveTargetUid.isNotBlank()) {
          unlockOrRewardGatedApp(
            groupId = task.groupId,
            targetUid = effectiveTargetUid,
            packageName = task.gatedAppPackage,
            taskId = task.id,
            rewardMinutes = task.unlockRewardMinutes.toInt()
          )
        }
      }

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "groups/${task.groupId}/groupTasks/${task.id}")
      Result.failure(e)
    }
  }

  suspend fun deleteGroupTask(groupId: String, taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      groupTasksCollection(groupId).document(taskId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, "groups/$groupId/groupTasks/$taskId")
      Result.failure(e)
    }
  }

  /**
   * Sets a remotely enforced screen time limit or exact-time app lock for a specific Group member (e.g., a Child).
   */
  suspend fun setMemberScreenTimeLimit(
    groupId: String,
    targetUid: String,
    targetMemberName: String,
    packageName: String,
    appName: String,
    limitMinutes: Int,
    period: ScreenTimePeriod,
    requireTaskIdToUnlock: String = "",
    requireTaskTitle: String = "",
    lockMode: RemoteLockMode = if (requireTaskIdToUnlock.isNotBlank()) RemoteLockMode.LOCK_NOW else RemoteLockMode.USAGE_LIMIT,
    lockAtMillis: Long = 0L,
    taskInstructions: String = "",
    playBeepOnLock: Boolean = false
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val user = Firebase.auth.currentUser
        ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
      val sanitizedPkg = packageName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(80)
      val limitId = "st_${targetUid.take(32)}_$sanitizedPkg"
      val docRef = memberScreenTimeCollection(groupId).document(limitId)
      val existing = docRef.get().await()
      val enforcerName = user.displayName?.takeIf { it.isNotBlank() }
        ?: user.email?.substringBefore("@") ?: "Group Manager"

      val safeLimitMinutes = limitMinutes.coerceIn(1, 10080).toLong()
      val initialUsedMinutes = when (lockMode) {
        RemoteLockMode.LOCK_NOW -> safeLimitMinutes
        RemoteLockMode.LOCK_AT_TIME -> 0L
        RemoteLockMode.USAGE_LIMIT -> 0L
      }

      val payload = mutableMapOf<String, Any>(
        "groupId" to groupId,
        "targetUid" to targetUid.take(128),
        "targetMemberName" to targetMemberName.take(120).ifBlank { "Member" },
        "packageName" to packageName.trim().take(200),
        "appName" to appName.trim().take(200).ifBlank { packageName },
        "limitMinutes" to safeLimitMinutes,
        "usedMinutes" to initialUsedMinutes,
        "periodType" to period.name,
        "lockMode" to lockMode.name,
        "lockAtMillis" to lockAtMillis.coerceAtLeast(0L),
        "taskInstructions" to taskInstructions.trim().take(1000),
        "playBeepOnLock" to playBeepOnLock,
        "requireTaskIdToUnlock" to requireTaskIdToUnlock.take(128),
        "requireTaskTitle" to requireTaskTitle.trim().take(200),
        "enforcedByUid" to user.uid,
        "enforcedByName" to enforcerName.take(120),
        "isEnabled" to true,
        "updatedAt" to FieldValue.serverTimestamp()
      )
      if (!existing.exists()) {
        payload["createdAt"] = FieldValue.serverTimestamp()
        docRef.set(payload).await()
      } else {
        payload["createdAt"] = existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
        docRef.set(payload, SetOptions.merge()).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "groups/$groupId/memberScreenTime")
      Result.failure(e)
    }
  }

  private suspend fun unlockOrRewardGatedApp(
    groupId: String,
    targetUid: String,
    packageName: String,
    taskId: String,
    rewardMinutes: Int
  ) {
    val docsToUnlock = linkedMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
    if (packageName.isNotBlank()) {
      val sanitizedPkg = packageName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(80)
      val limitId = "st_${targetUid.take(32)}_$sanitizedPkg"
      val directSnap = memberScreenTimeCollection(groupId).document(limitId).get().await()
      if (directSnap.exists()) {
        docsToUnlock[directSnap.id] = directSnap
      }
    }
    if (taskId.isNotBlank()) {
      val linkedSnaps = memberScreenTimeCollection(groupId)
        .whereEqualTo("targetUid", targetUid)
        .whereEqualTo("requireTaskIdToUnlock", taskId)
        .get()
        .await()
      for (snap in linkedSnaps.documents) {
        docsToUnlock[snap.id] = snap
      }
    }

    for (snap in docsToUnlock.values) {
      val current = snap.toObject(CloudGroupScreenTimeLimit::class.java) ?: continue
      val docRef = snap.reference
      if (rewardMinutes <= 0) {
        docRef.delete().await()
      } else {
        val newLimit = rewardMinutes.coerceIn(1, 10080)
        val payload = mapOf(
          "groupId" to current.groupId,
          "targetUid" to current.targetUid,
          "targetMemberName" to current.targetMemberName,
          "packageName" to current.packageName,
          "appName" to current.appName,
          "limitMinutes" to newLimit.toLong(),
          "usedMinutes" to 0L,
          "periodType" to current.periodType,
          "lockMode" to RemoteLockMode.USAGE_LIMIT.name,
          "lockAtMillis" to 0L,
          "taskInstructions" to "",
          "playBeepOnLock" to false,
          "requireTaskIdToUnlock" to "",
          "requireTaskTitle" to "",
          "enforcedByUid" to current.enforcedByUid,
          "enforcedByName" to current.enforcedByName,
          "isEnabled" to true,
          "createdAt" to (snap.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
          "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload, SetOptions.merge()).await()
      }
    }
  }

  suspend fun removeMemberScreenTimeLimit(groupId: String, limitId: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        memberScreenTimeCollection(groupId).document(limitId).delete().await()
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "groups/$groupId/memberScreenTime/$limitId")
        Result.failure(e)
      }
    }

  /**
   * Real-time stream of Pass-the-Baton Group Relay Chains in this group.
   */
  fun observeGroupRelays(groupId: String): Flow<List<CloudGroupRelay>> = callbackFlow {
    if (groupId.isBlank()) {
      trySend(emptyList())
      close()
      return@callbackFlow
    }
    val reg = relaysCollection(groupId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, "groups/$groupId/relays")
          close(error)
          return@addSnapshotListener
        }
        val list = snapshot?.toObjects(CloudGroupRelay::class.java)
          .orEmpty()
          .sortedWith(
            compareBy<CloudGroupRelay> { it.isRelayCompleted }
              .thenByDescending { it.lastHandoffAtMillis.takeIf { ts -> ts > 0L } ?: it.startedAtMillis }
          )
        trySend(list)
      }
    awaitClose { reg.remove() }
  }

  /**
   * Creates a multi-step Pass-the-Baton Group Relay Chain (2 to 10 sequential steps).
   */
  suspend fun createGroupRelay(
    groupId: String,
    title: String,
    description: String,
    steps: List<CloudRelayStep>
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val user = Firebase.auth.currentUser
        ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
      val cleanTitle = title.trim().take(160)
      if (cleanTitle.isBlank()) {
        return@withContext Result.failure(IllegalArgumentException("Relay title cannot be empty."))
      }
      if (steps.size !in 2..10) {
        return@withContext Result.failure(IllegalArgumentException("A relay chain needs between 2 and 10 steps."))
      }
      val creatorName = user.displayName?.takeIf { it.isNotBlank() }
        ?: user.email?.substringBefore("@")
        ?: "Group Member"
      val now = System.currentTimeMillis()
      val relayId = "relay_${UUID.randomUUID().toString().replace("-", "_").take(24)}"
      val cleanSteps = steps.map {
        it.copy(
          stepTitle = it.stepTitle.trim().take(120).ifBlank { "Relay Step" },
          done = false,
          completedAtMillis = 0L,
          handoffSpeedSeconds = 0L
        )
      }
      val payload = mapOf(
        "groupId" to groupId,
        "title" to cleanTitle,
        "description" to description.trim().take(500),
        "createdByUid" to user.uid,
        "createdByName" to creatorName.take(120),
        "steps" to cleanSteps.map { it.toMap() },
        "currentStepIndex" to 0L,
        "currentAssigneeUid" to cleanSteps.first().assigneeUid.trim().take(128),
        "relayStatus" to com.example.data.model.GroupRelayStatus.ACTIVE.name,
        "streakCount" to 0L,
        "startedAtMillis" to now,
        "lastHandoffAtMillis" to now,
        "completedAtMillis" to 0L,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      relaysCollection(groupId).document(relayId).set(payload).await()
      Result.success(relayId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, "groups/$groupId/relays")
      Result.failure(e)
    }
  }

  /**
   * Advances the baton on the active step of a Group Relay Chain.
   */
  suspend fun completeCurrentRelayStep(
    relay: CloudGroupRelay
  ): Result<CloudGroupRelay> = withContext(Dispatchers.IO) {
    try {
      Firebase.auth.currentUser
        ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
      if (relay.isRelayCompleted) {
        return@withContext Result.success(relay)
      }
      val now = System.currentTimeMillis()
      val advanced = relay.advanceBaton(now)
      relaysCollection(relay.groupId).document(relay.id).update(
        mapOf(
          "steps" to advanced.steps.map { it.toMap() },
          "currentStepIndex" to advanced.currentStepIndex,
          "currentAssigneeUid" to advanced.currentAssigneeUid,
          "relayStatus" to advanced.relayStatus,
          "streakCount" to advanced.streakCount,
          "lastHandoffAtMillis" to advanced.lastHandoffAtMillis,
          "completedAtMillis" to advanced.completedAtMillis,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(advanced)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "groups/${relay.groupId}/relays/${relay.id}")
      Result.failure(e)
    }
  }

  suspend fun deleteGroupRelay(groupId: String, relayId: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        relaysCollection(groupId).document(relayId).delete().await()
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "groups/$groupId/relays/$relayId")
        Result.failure(e)
      }
    }
}
