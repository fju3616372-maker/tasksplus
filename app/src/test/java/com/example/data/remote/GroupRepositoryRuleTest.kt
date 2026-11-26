package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CreditRules
import com.example.data.model.GroupRole
import com.example.data.model.GroupType
import com.example.data.model.InstalledAppInfo
import com.example.data.model.PerkStatus
import com.example.data.model.ProofStatus
import com.example.data.model.RemoteLockMode
import com.example.data.model.ScreenTimePeriod
import com.example.data.model.TaskPriority
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private val runSuffix = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
  private val aliceEmail = "alice_grp_$runSuffix@test.com"
  private val bobEmail = "bob_grp_$runSuffix@test.com"
  private val outsiderEmail = "outsider_grp_$runSuffix@test.com"
  private val teamOwnerEmail = "owner_grp_$runSuffix@test.com"

  @Test
  fun createGroupAsManager_sendInvite_acceptInvite_andGroupTasks_succeed() = runBlocking {
    val aliceUid = signInTestUser(aliceEmail, verifyEmail = true)
    val repo = GroupRepository(firestore)

    // 1. Alice creates a group as Group Manager
    val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createGroupAsManager("vasudev", GroupType.FAMILY)
    }
    assertTrue("Expected createGroupAsManager to succeed: ${createRes.exceptionOrNull()}", createRes.isSuccess)
    val groupId = createRes.getOrThrow()

    val aliceGroups = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeUserGroups().first { groups -> groups.any { it.id == groupId } }
    }
    val activeGroup = aliceGroups.first { it.id == groupId }
    assertEquals(aliceUid, activeGroup.managerUid)
    assertFalse("Group points must be disabled by default", activeGroup.creditSystemEnabled)

    // Enable optional group points so early completion awards points later in this flow
    assertTrue(
      repo.updateGroupCreditRules(groupId, CreditRules(enabled = true)).isSuccess
    )

    // 2. Alice sends an invite to Bob
    val inviteRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.sendGroupInviteByEmail(activeGroup, bobEmail, GroupRole.CHILD)
    }
    assertTrue("Expected sendGroupInviteByEmail to succeed: ${inviteRes.exceptionOrNull()}", inviteRes.isSuccess)

    val sentInvites = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupSentInvites(groupId).first { list -> list.any { it.targetEmail == bobEmail } }
    }
    assertTrue(sentInvites.any { it.targetEmail == bobEmail })

    // 3. Bob signs in and fetches & accepts the pending request
    val bobUid = signInTestUser(bobEmail, verifyEmail = true)
    val bobPending = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.fetchPendingInvitesForEmailOnce(bobEmail)
    }
    assertTrue("Expected fetchPendingInvitesForEmailOnce to succeed: ${bobPending.exceptionOrNull()}", bobPending.isSuccess)
    val bobInvite = bobPending.getOrThrow().first { it.groupId == groupId }

    val acceptRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.acceptGroupInvite(bobInvite)
    }
    assertTrue("Expected acceptGroupInvite to succeed: ${acceptRes.exceptionOrNull()}", acceptRes.isSuccess)

    // 4. Alice signs back in, creates a task gating YouTube for Bob, and locks Bob's device remotely
    signInTestUser(aliceEmail, verifyEmail = true)
    val members = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupMembers(groupId).first { list -> list.any { it.uid == bobUid } }
    }
    val bobMember = members.first { it.uid == bobUid }

    // 3b. Bob syncs his installed device apps so Manager can see them
    signInTestUser(bobEmail, verifyEmail = true)
    val syncAppsRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.syncCurrentUserInstalledApps(
        groupId = groupId,
        apps = listOf(
          InstalledAppInfo("com.google.android.youtube", "YouTube"),
          InstalledAppInfo("com.zhiliaoapp.musically", "TikTok")
        )
      )
    }
    assertTrue("Expected syncCurrentUserInstalledApps to succeed: ${syncAppsRes.exceptionOrNull()}", syncAppsRes.isSuccess)
    val syncedMembers = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupMembers(groupId).first { list ->
        list.firstOrNull { it.uid == bobUid }?.installedApps?.isNotEmpty() == true
      }
    }
    assertEquals(2, syncedMembers.first { it.uid == bobUid }.installedApps.size)

    // 4. Alice signs back in, creates a task + sets an exact-time remote app lock with instructions & beep
    signInTestUser(aliceEmail, verifyEmail = true)
    val lockRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateMemberRoleOrDeviceLock(
        group = activeGroup,
        member = bobMember,
        newRole = GroupRole.CHILD,
        canManageOthers = false,
        remoteDeviceLocked = true,
        remoteLockReason = "Finish homework first"
      )
    }
    assertTrue("Expected remote lock update to succeed: ${lockRes.exceptionOrNull()}", lockRes.isSuccess)

    val taskRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createOrUpdateGroupTask(
        groupId = groupId,
        title = "Clean room",
        subtext = "Make bed and organize desk",
        assignedToUid = bobUid,
        assignedToName = bobMember.displayName,
        priority = TaskPriority.HIGH,
        dueAtMillis = System.currentTimeMillis() + 3_600_000L,
        reminderIntervalMinutes = 30,
        ringAlarmBell = true,
        gatedAppPackage = "com.google.android.youtube",
        gatedAppName = "YouTube",
        unlockRewardMinutes = 30,
        requiresPhotoProof = true,
        useGeminiAutoApproval = true,
        geminiVerificationPrompt = "Verify clean room",
        lockMode = RemoteLockMode.LOCK_AT_TIME,
        lockAtMillis = System.currentTimeMillis() + 300_000L,
        playBeepOnLock = true
      )
    }
    assertTrue("Expected createOrUpdateGroupTask to succeed: ${taskRes.exceptionOrNull()}", taskRes.isSuccess)

    val exactLimitRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.setMemberScreenTimeLimit(
        groupId = groupId,
        targetUid = bobUid,
        targetMemberName = bobMember.displayName,
        packageName = "com.zhiliaoapp.musically",
        appName = "TikTok",
        limitMinutes = 1,
        period = ScreenTimePeriod.DAILY,
        requireTaskIdToUnlock = taskRes.getOrThrow(),
        requireTaskTitle = "Clean room",
        lockMode = RemoteLockMode.LOCK_AT_TIME,
        lockAtMillis = System.currentTimeMillis() + 600_000L,
        taskInstructions = "Take a photo of your tidy desk to unlock TikTok.",
        playBeepOnLock = true
      )
    }
    assertTrue("Expected LOCK_AT_TIME remote limit to succeed: ${exactLimitRes.exceptionOrNull()}", exactLimitRes.isSuccess)

    // 5. Bob completes Gemini-verified task & automatically unlocks linked gated apps
    signInTestUser(bobEmail, verifyEmail = true)
    val bobTasks = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupTasks(groupId).first { list -> list.any { it.title == "Clean room" } }
    }
    val createdTask = bobTasks.first { it.title == "Clean room" }
    val submitRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateGroupTaskCompletionOrProof(
        task = createdTask,
        proofStatus = ProofStatus.APPROVED,
        proofImageBase64 = "dGVzdA==",
        proofNote = "Done!",
        geminiEvaluationResult = "Gemini AI Approved (95%)",
        isCompleted = true,
        completedByName = "Bob"
      )
    }
    assertTrue("Expected Gemini-approved completion & unlock to succeed: ${submitRes.exceptionOrNull()}", submitRes.isSuccess)

    // 5b. Also check Bob's credit balance & ledger entry from completing early
    val updatedMembers = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupMembers(groupId).first { list ->
        (list.firstOrNull { it.uid == bobUid }?.creditBalance ?: 0L) > 0L
      }
    }
    assertTrue(updatedMembers.first { it.uid == bobUid }.creditBalance > 0L)
    val txs = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupCreditTransactions(groupId).first { list -> list.any { it.taskId == createdTask.id } }
    }
    assertTrue(txs.any { it.taskId == createdTask.id && it.pointsDelta > 0L })

    // 6. Verify Alice can dissolve group cleanly
    signInTestUser(aliceEmail, verifyEmail = true)
    val latestGroup = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeUserGroups().first { list -> list.any { it.id == groupId } }
    }.first { it.id == groupId }
    val dissolveRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.removeManagerSpotOrLeaveGroup(latestGroup)
    }
    assertTrue("Expected group dissolution to succeed: ${dissolveRes.exceptionOrNull()}", dissolveRes.isSuccess)
  }

  @Test
  fun creditSystemRules_latePenalty_managerAdjustment_andChildDenied() = runBlocking {
    val repo = GroupRepository(firestore)
    signInTestUser(aliceEmail, verifyEmail = true)
    val groupId = repo.createGroupAsManager("Credit Family", GroupType.FAMILY).getOrThrow()
    val group = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeUserGroups().first { list -> list.any { it.id == groupId } }
    }.first { it.id == groupId }

    // Configure rules: enabled = true, 1 pt/sec early, 1 pt/sec late
    val customRules = com.example.data.model.CreditRules(
      enabled = true,
      earlyPointsPerSecond = 1.0,
      latePenaltyPerSecond = 1.0,
      gracePeriodSeconds = 0L,
      maxCreditPerTask = 0L,
      maxPenaltyPerTask = 0L,
      allowNegativeBalance = true
    )
    val ruleUpdateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateGroupCreditRules(groupId, customRules)
    }
    assertTrue("Expected manager rule update to succeed", ruleUpdateRes.isSuccess)

    // Invite Bob as CHILD & accept
    repo.sendGroupInviteByEmail(group, bobEmail, GroupRole.CHILD).getOrThrow()
    val bobUid = signInTestUser(bobEmail, verifyEmail = true)
    val invite = repo.fetchPendingInvitesForEmailOnce(bobEmail).getOrThrow().first { it.groupId == groupId }
    repo.acceptGroupInvite(invite).getOrThrow()

    // Child Bob CANNOT alter group credit rules
    val childRuleRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateGroupCreditRules(groupId, customRules.copy(enabled = false))
    }
    assertTrue("Expected child rule update to be rejected", childRuleRes.isFailure)

    // Alice creates an already-overdue task (due 60 seconds ago) for Bob
    signInTestUser(aliceEmail, verifyEmail = true)
    val bobMember = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupMembers(groupId).first { list -> list.any { it.uid == bobUid } }
    }.first { it.uid == bobUid }

    val overdueTaskId = repo.createOrUpdateGroupTask(
      groupId = groupId,
      title = "Overdue Homework",
      subtext = "Submit late",
      assignedToUid = bobUid,
      assignedToName = bobMember.displayName,
      priority = TaskPriority.MEDIUM,
      dueAtMillis = System.currentTimeMillis() - 60_000L,
      reminderIntervalMinutes = 15,
      ringAlarmBell = false
    ).getOrThrow()

    // Bob completes late task -> negative points deducted (~ -60 pts)
    signInTestUser(bobEmail, verifyEmail = true)
    val bobTask = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupTasks(groupId).first { list -> list.any { it.id == overdueTaskId } }
    }.first { it.id == overdueTaskId }
    repo.updateGroupTaskCompletionOrProof(
      task = bobTask,
      proofStatus = ProofStatus.NONE,
      isCompleted = true,
      completedByName = "Bob"
    ).getOrThrow()

    val penalizedMember = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupMembers(groupId).first { list ->
        (list.firstOrNull { it.uid == bobUid }?.creditBalance ?: 0L) < 0L
      }
    }.first { it.uid == bobUid }
    assertTrue("Expected negative credit balance after late completion", penalizedMember.creditBalance <= -50L)

    // Manager Alice gives Bob a +100 pt bonus adjustment
    signInTestUser(aliceEmail, verifyEmail = true)
    val adjustRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.awardOrDeductMemberCredits(group, penalizedMember, 100L, "Effort bonus")
    }
    assertTrue("Expected manager bonus adjustment to succeed", adjustRes.isSuccess)
    val adjustedMember = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupMembers(groupId).first { list ->
        (list.firstOrNull { it.uid == bobUid }?.creditBalance ?: -999L) > 0L
      }
    }.first { it.uid == bobUid }
    assertTrue(adjustedMember.creditBalance > 0L)
  }

  @Test
  fun groupPerks_mainManagerAssignsAndDeletes_memberRedeemsWithPointsAndCoAdminDenied() = runBlocking {
    val repo = GroupRepository(firestore)
    signInTestUser(aliceEmail, verifyEmail = true)
    val groupId = repo.createGroupAsManager("Perks Family", GroupType.FAMILY).getOrThrow()
    val initialGroup = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeUserGroups().first { list -> list.any { it.id == groupId } }
    }.first { it.id == groupId }

    // Invite Bob as CHILD and CoAdmin (outsiderEmail) as PARENT_ADMIN
    repo.sendGroupInviteByEmail(initialGroup, bobEmail, GroupRole.CHILD).getOrThrow()
    repo.sendGroupInviteByEmail(initialGroup, outsiderEmail, GroupRole.PARENT_ADMIN).getOrThrow()

    val bobUid = signInTestUser(bobEmail, verifyEmail = true)
    val bobInvite = repo.fetchPendingInvitesForEmailOnce(bobEmail).getOrThrow().first { it.groupId == groupId }
    repo.acceptGroupInvite(bobInvite).getOrThrow()

    val coAdminUid = signInTestUser(outsiderEmail, verifyEmail = true)
    val coAdminInvite = repo.fetchPendingInvitesForEmailOnce(outsiderEmail).getOrThrow().first { it.groupId == groupId }
    repo.acceptGroupInvite(coAdminInvite).getOrThrow()

    val coAdminMembers = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupMembers(groupId).first { list -> list.any { it.uid == bobUid } && list.any { it.uid == coAdminUid } }
    }
    val bobMember = coAdminMembers.first { it.uid == bobUid }

    // Co-admin CANNOT enable group points or assign perks (main manager only)
    val coAdminEnablePts = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateGroupCreditRules(groupId, CreditRules(enabled = true))
    }
    assertTrue("Expected co-admin credit rules edit to fail", coAdminEnablePts.isFailure)

    val coAdminAssignPerk = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.assignGroupPerk(initialGroup, bobMember, "Unauthorized Perk", "", 50L, false)
    }
    assertTrue("Expected co-admin perk assignment to fail", coAdminAssignPerk.isFailure)

    // Main manager Alice enables Group Points, awards Bob 150 pts, and assigns two perks to Bob
    signInTestUser(aliceEmail, verifyEmail = true)
    repo.updateGroupCreditRules(groupId, CreditRules(enabled = true)).getOrThrow()
    val enabledGroup = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeUserGroups().first { list -> list.firstOrNull { it.id == groupId }?.creditSystemEnabled == true }
    }.first { it.id == groupId }

    repo.awardOrDeductMemberCredits(enabledGroup, bobMember, 150L, "Weekly helper bonus").getOrThrow()

    val screenPerkId = repo.assignGroupPerk(
      group = enabledGroup,
      targetMember = bobMember,
      title = "Extra 30m Screen Time",
      description = "Redeem anytime this weekend",
      pointsCost = 100L,
      grantImmediately = false
    ).getOrThrow()

    val snackPerkId = repo.assignGroupPerk(
      group = enabledGroup,
      targetMember = bobMember,
      title = "Ice Cream Treat",
      description = "Direct reward",
      pointsCost = 0L,
      grantImmediately = true
    ).getOrThrow()

    // Bob observes assigned perks and redeems Extra 30m Screen Time (-100 pts -> 50 pts left)
    signInTestUser(bobEmail, verifyEmail = true)
    val bobPerks = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupPerks(groupId).first { list -> list.any { it.id == screenPerkId } && list.any { it.id == snackPerkId } }
    }
    val screenPerk = bobPerks.first { it.id == screenPerkId }
    assertEquals(PerkStatus.ASSIGNED, screenPerk.parsedStatus)
    assertEquals(PerkStatus.GRANTED, bobPerks.first { it.id == snackPerkId }.parsedStatus)

    val redeemRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.redeemGroupPerk(enabledGroup, screenPerk)
    }
    assertTrue("Expected Bob perk redemption to succeed: ${redeemRes.exceptionOrNull()}", redeemRes.isSuccess)

    val bobAfterRedeem = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupMembers(groupId).first { list ->
        list.firstOrNull { it.uid == bobUid }?.creditBalance == 50L
      }
    }.first { it.uid == bobUid }
    assertEquals(50L, bobAfterRedeem.creditBalance)

    val redeemedPerkSnap = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeGroupPerks(groupId).first { list ->
        list.firstOrNull { it.id == screenPerkId }?.parsedStatus == PerkStatus.REDEEMED
      }
    }.first { it.id == screenPerkId }
    assertEquals(1L, redeemedPerkSnap.redeemedCount)

    // Bob CANNOT delete a perk; only main manager Alice can
    assertTrue(repo.deleteGroupPerk(enabledGroup, snackPerkId).isFailure)
    signInTestUser(aliceEmail, verifyEmail = true)
    assertTrue(repo.deleteGroupPerk(enabledGroup, snackPerkId).isSuccess)
  }

  @Test
  fun outsiderCannotReadOrModifyGroup_failsWithPermissionDenied() = runBlocking {
    signInTestUser(teamOwnerEmail, verifyEmail = true)
    val repo = GroupRepository(firestore)
    val groupId = repo.createGroupAsManager("Private Team", GroupType.ORGANIZATION).getOrThrow()

    signInTestUser(outsiderEmail, verifyEmail = true)
    val outsiderRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.removeMemberScreenTimeLimit(groupId, "nonexistent")
    }
    assertTrue("Expected outsider delete limit to fail", outsiderRes.isFailure)

    // Attempting a direct group read as non-member must fail with PERMISSION_DENIED
    val directReadEx = try {
      firestore.collection("groups").document(groupId).get().await()
      null
    } catch (e: Exception) {
      e.unwrapFirestoreException()
    }
    assertNotNull("Expected outsider group read to fail with PERMISSION_DENIED", directReadEx)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, directReadEx?.code)
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 8000L
    const val FLOW_TIMEOUT_MS = 5000L
  }
}
