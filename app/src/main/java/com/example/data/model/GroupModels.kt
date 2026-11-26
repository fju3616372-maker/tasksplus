package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class GroupType(val label: String) {
  FAMILY("Family"),
  ORGANIZATION("Team / Business");

  companion object {
    fun fromName(value: String?): GroupType =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FAMILY
  }
}

enum class GroupRole(
  val label: String,
  val subtitle: String,
  val defaultCanManage: Boolean
) {
  MANAGER(
    label = "Group Manager",
    subtitle = "Primary owner with full permissions",
    defaultCanManage = true
  ),
  PARENT_ADMIN(
    label = "Parent / Co-Manager",
    subtitle = "Can assign tasks, approve proofs & manage screen time",
    defaultCanManage = true
  ),
  MEMBER(
    label = "Member / Employee",
    subtitle = "Receives group tasks & submits completion",
    defaultCanManage = false
  ),
  CHILD(
    label = "Child (Supervised)",
    subtitle = "Subject to enforced screen time, task gates & remote lock",
    defaultCanManage = false
  );

  companion object {
    fun fromName(value: String?): GroupRole =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEMBER
  }
}

enum class ProofStatus(val label: String) {
  NONE("No Proof Required"),
  PENDING_PROOF("Photo Proof Needed"),
  PENDING_APPROVAL("Waiting for Manager Review"),
  APPROVED("Proof Approved"),
  REJECTED("Needs Revision");

  companion object {
    fun fromName(value: String?): ProofStatus =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
  }
}

@IgnoreExtraProperties
data class CloudGroup(
  @DocumentId val id: String = "",
  val name: String = "",
  val groupType: String = GroupType.FAMILY.name,
  val managerUid: String = "",
  val managerEmail: String = "",
  val managerName: String = "",
  val memberUids: List<String> = emptyList(),
  val adminUids: List<String> = emptyList(),
  val invitedEmails: List<String> = emptyList(),
  val invitedAdminEmails: List<String> = emptyList(),
  val creditSystemEnabled: Boolean = false,
  val earlyPointsPerSecond: Double = 1.0,
  val latePenaltyPerSecond: Double = 1.0,
  val creditGracePeriodSeconds: Long = 0L,
  val maxCreditPerTask: Long = 0L,
  val maxPenaltyPerTask: Long = 0L,
  val allowNegativeCredits: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  val parsedType: GroupType
    get() = GroupType.fromName(groupType)

  val creditRules: CreditRules
    get() = CreditRules(
      enabled = creditSystemEnabled,
      earlyPointsPerSecond = earlyPointsPerSecond,
      latePenaltyPerSecond = latePenaltyPerSecond,
      gracePeriodSeconds = creditGracePeriodSeconds,
      maxCreditPerTask = maxCreditPerTask,
      maxPenaltyPerTask = maxPenaltyPerTask,
      allowNegativeBalance = allowNegativeCredits
    ).normalized()
}

@IgnoreExtraProperties
data class CloudGroupInvite(
  @DocumentId val id: String = "",
  val groupId: String = "",
  val groupName: String = "",
  val groupType: String = GroupType.FAMILY.name,
  val senderUid: String = "",
  val senderName: String = "",
  val senderEmail: String = "",
  val targetEmail: String = "",
  val proposedRole: String = GroupRole.MEMBER.name,
  val groupAdminUids: List<String> = emptyList(),
  val status: String = "PENDING",
  val acceptedByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  val parsedProposedRole: GroupRole
    get() = GroupRole.fromName(proposedRole)
}

enum class RemoteLockMode(val label: String) {
  LOCK_NOW("Lock Immediately"),
  LOCK_AT_TIME("Exact Time"),
  USAGE_LIMIT("Usage Limit");

  companion object {
    fun fromName(value: String?): RemoteLockMode =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: USAGE_LIMIT
  }
}

@IgnoreExtraProperties
data class SyncedInstalledApp(
  val packageName: String = "",
  val appName: String = ""
)

@IgnoreExtraProperties
data class CloudGroupMember(
  @DocumentId val id: String = "",
  val groupId: String = "",
  val uid: String = "",
  val email: String = "",
  val displayName: String = "",
  val role: String = GroupRole.MEMBER.name,
  val canManageOthers: Boolean = false,
  val remoteDeviceLocked: Boolean = false,
  val remoteLockReason: String = "",
  val installedApps: List<SyncedInstalledApp> = emptyList(),
  val appsLastSyncedAtMillis: Long = 0L,
  val creditBalance: Long = 0L,
  val totalEarnedCredits: Long = 0L,
  val totalDeductedCredits: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  val parsedRole: GroupRole
    get() = GroupRole.fromName(role)

  val hasAdminAccess: Boolean
    get() = canManageOthers || parsedRole == GroupRole.MANAGER || parsedRole == GroupRole.PARENT_ADMIN
}

@IgnoreExtraProperties
data class CloudGroupTask(
  @DocumentId val id: String = "",
  val groupId: String = "",
  val title: String = "",
  val subtext: String = "",
  val createdByUid: String = "",
  val createdByName: String = "",
  val assignedToUid: String = "ALL",
  val assignedToName: String = "Everyone in Group",
  val priority: String = TaskPriority.HIGH.name,
  val urgencyLevel: Long = 0L,
  val dueAtMillis: Long = System.currentTimeMillis() + 3_600_000L,
  val reminderIntervalMinutes: Long = 30L,
  val ringAlarmBell: Boolean = false,
  val gatedAppPackage: String = "",
  val gatedAppName: String = "",
  val unlockRewardMinutes: Long = 0L,
  val requiresPhotoProof: Boolean = false,
  val useGeminiAutoApproval: Boolean = false,
  val geminiVerificationPrompt: String = "",
  val proofStatus: String = ProofStatus.NONE.name,
  val proofImageBase64: String = "",
  val proofNote: String = "",
  val geminiEvaluationResult: String = "",
  @get:PropertyName("isCompleted")
  @field:PropertyName("isCompleted")
  val isCompleted: Boolean = false,
  val completedByUid: String = "",
  val completedByName: String = "",
  val completedAtMillis: Long = 0L,
  val creditsDelta: Long = 0L,
  val creditSecondsDelta: Long = 0L,
  val creditsApplied: Boolean = false,
  val proofSubmittedAtMillis: Long = 0L,
  val proofSubmittedByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  val parsedPriority: TaskPriority
    get() = TaskPriority.fromName(priority)

  val resolvedUrgencyLevel: Int
    get() = if (urgencyLevel in 1L..5L) urgencyLevel.toInt() else parsedPriority.defaultUrgencyLevel

  val parsedProofStatus: ProofStatus
    get() = ProofStatus.fromName(proofStatus)
}

@IgnoreExtraProperties
data class CloudGroupScreenTimeLimit(
  @DocumentId val id: String = "",
  val groupId: String = "",
  val targetUid: String = "",
  val targetMemberName: String = "",
  val packageName: String = "",
  val appName: String = "",
  val limitMinutes: Long = 30L,
  val usedMinutes: Long = 0L,
  val periodType: String = ScreenTimePeriod.DAILY.name,
  val lockMode: String = RemoteLockMode.USAGE_LIMIT.name,
  val lockAtMillis: Long = 0L,
  val taskInstructions: String = "",
  val playBeepOnLock: Boolean = false,
  val requireTaskIdToUnlock: String = "",
  val requireTaskTitle: String = "",
  val enforcedByUid: String = "",
  val enforcedByName: String = "",
  @get:PropertyName("isEnabled")
  @field:PropertyName("isEnabled")
  val isEnabled: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  val parsedPeriod: ScreenTimePeriod
    get() = ScreenTimePeriod.fromName(periodType)

  val parsedLockMode: RemoteLockMode
    get() = RemoteLockMode.fromName(lockMode)
}

enum class PerkStatus(val label: String) {
  ASSIGNED("Available"),
  REDEEMED("Redeemed"),
  GRANTED("Granted");

  companion object {
    fun fromName(value: String?): PerkStatus =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ASSIGNED
  }
}

@IgnoreExtraProperties
data class CloudGroupPerk(
  @DocumentId val id: String = "",
  val groupId: String = "",
  val managerUid: String = "",
  val assignedToUid: String = "",
  val assignedToName: String = "",
  val assignedByName: String = "",
  val title: String = "",
  val description: String = "",
  val pointsCost: Long = 0L,
  val perkStatus: String = PerkStatus.ASSIGNED.name,
  val redeemedCount: Long = 0L,
  val redeemedAtMillis: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  val parsedStatus: PerkStatus
    get() = PerkStatus.fromName(perkStatus)
}
