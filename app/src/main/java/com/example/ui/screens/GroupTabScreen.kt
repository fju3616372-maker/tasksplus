package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.GeminiTaskProofEvaluator
import com.example.data.model.CloudGroup
import com.example.data.model.CloudGroupInvite
import com.example.data.model.CloudGroupMember
import com.example.data.model.CloudGroupPerk
import com.example.data.model.CloudGroupScreenTimeLimit
import com.example.data.model.CloudGroupTask
import com.example.data.model.CreditCalculator
import com.example.data.model.GroupRole
import com.example.data.model.GroupType
import com.example.data.model.InstalledAppInfo
import com.example.data.model.PerkStatus
import com.example.data.model.ProofStatus
import com.example.data.model.RemoteLockMode
import com.example.data.model.ScreenTimePeriod
import com.example.data.model.TaskPriority
import com.example.screentime.RealDeviceAppsScanner
import com.example.screentime.TasksPlusDeviceAdminReceiver
import com.example.ui.components.GroupLeaderboardSection
import com.example.ui.components.ProfileAvatarBadge
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupTabScreen(
  currentUserUid: String,
  currentUserEmail: String,
  activeGroup: CloudGroup?,
  pendingInvitesForMe: List<CloudGroupInvite>,
  sentGroupInvites: List<CloudGroupInvite>,
  groupMembers: List<CloudGroupMember>,
  groupTasks: List<CloudGroupTask>,
  groupScreenTimeLimits: List<CloudGroupScreenTimeLimit>,
  isEvaluatingProof: Boolean,
  onCreateGroup: (groupName: String, groupType: GroupType) -> Unit,
  onLeaveOrDissolveGroup: () -> Unit,
  onSendInviteRequest: (inviteeEmail: String, role: GroupRole) -> Unit,
  onAcceptInvite: (CloudGroupInvite) -> Unit,
  onDeclineInvite: (CloudGroupInvite) -> Unit,
  onRefreshRequests: () -> Unit = {},
  onSyncMyDeviceApps: (List<InstalledAppInfo>) -> Unit = {},
  onUpdateMemberRoleOrLock: (memberUid: String, role: GroupRole, canManageOthers: Boolean, remoteDeviceLocked: Boolean) -> Unit,
  onCreateGroupTask: (
    title: String,
    subtext: String,
    assignedToUid: String,
    assignedToName: String,
    priority: TaskPriority,
    dueHoursFromNow: Int,
    ringAlarmBell: Boolean,
    requiresPhotoProof: Boolean,
    useGeminiAutoApproval: Boolean,
    geminiVerificationPrompt: String,
    gatedAppPackage: String,
    gatedAppName: String,
    unlockRewardMinutes: Int,
    lockMode: RemoteLockMode,
    lockAtMillis: Long,
    playBeepOnLock: Boolean,
    urgencyLevel: Int
  ) -> Unit,
  onCompleteOrSubmitTaskProof: (task: CloudGroupTask, proofBitmap: Bitmap?, proofNote: String, beforeBitmap: Bitmap?) -> Unit,
  onRecheckProofWithGemini: (CloudGroupTask) -> Unit = {},
  onReviewTaskProof: (task: CloudGroupTask, approved: Boolean, feedback: String) -> Unit,
  onDeleteGroupTask: (taskId: String) -> Unit,
  onSaveMemberScreenTimeLimit: (
    targetMemberUid: String,
    targetMemberName: String,
    packageName: String,
    appName: String,
    limitMinutes: Int,
    period: ScreenTimePeriod,
    requiredGroupTaskId: String,
    requiredGroupTaskTitle: String,
    lockMode: RemoteLockMode,
    lockAtMillis: Long,
    taskInstructions: String,
    playBeepOnLock: Boolean
  ) -> Unit,
  onLockMemberAppWithTask: (
    targetMemberUid: String,
    targetMemberName: String,
    packageName: String,
    appName: String,
    limitMinutes: Int,
    period: ScreenTimePeriod,
    lockMode: RemoteLockMode,
    lockAtMillis: Long,
    taskInstructions: String,
    playBeepOnLock: Boolean,
    existingTaskId: String,
    existingTaskTitle: String,
    inlineTaskTitle: String,
    inlineRequiresPhotoProof: Boolean,
    inlineUseGeminiAutoApproval: Boolean,
    inlineGeminiPrompt: String,
    inlineRewardMinutes: Int
  ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
  onRemoveMemberScreenTimeLimit: (limitId: String) -> Unit,
  onOpenCreditSheet: () -> Unit = {},
  groupPerks: List<CloudGroupPerk> = emptyList(),
  requestedSubTab: Int = -1,
  onConsumedRequestedSubTab: () -> Unit = {},
  onAssignGroupPerk: (
    targetMember: CloudGroupMember,
    title: String,
    description: String,
    pointsCost: Long,
    grantImmediately: Boolean
  ) -> Unit = { _, _, _, _, _ -> },
  onRedeemGroupPerk: (CloudGroupPerk) -> Unit = {},
  onUpdateGroupPerkStatus: (CloudGroupPerk, PerkStatus) -> Unit = { _, _ -> },
  onDeleteGroupPerk: (String) -> Unit = {},
  groupRelays: List<com.example.data.model.CloudGroupRelay> = emptyList(),
  onCreateGroupRelay: (String, String, List<com.example.data.model.CloudRelayStep>) -> Unit = { _, _, _ -> },
  onAdvanceGroupRelay: (com.example.data.model.CloudGroupRelay) -> Unit = {},
  onDeleteGroupRelay: (String) -> Unit = {}
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val currentMember = remember(groupMembers, currentUserUid) {
    groupMembers.firstOrNull { it.uid == currentUserUid }
  }
  val isMainManager = activeGroup?.managerUid == currentUserUid
  val isGroupManager = isMainManager || currentMember?.parsedRole == GroupRole.MANAGER
  val canManageGroup = isGroupManager ||
    currentMember?.parsedRole == GroupRole.PARENT_ADMIN ||
    currentMember?.canManageOthers == true
  val isChildSupervised = currentMember?.parsedRole == GroupRole.CHILD

  var isDeviceAdminGranted by remember {
    mutableStateOf(TasksPlusDeviceAdminReceiver.isAdminActive(context))
  }
  LaunchedEffect(Unit) {
    while (true) {
      isDeviceAdminGranted = TasksPlusDeviceAdminReceiver.isAdminActive(context)
      delay(1500L)
    }
  }

  var selectedEntryMode by rememberSaveable { mutableIntStateOf(0) } // 0 = Create Group, 1 = Requests
  var selectedSubTab by rememberSaveable { mutableIntStateOf(0) }
  LaunchedEffect(requestedSubTab) {
    if (requestedSubTab in 0..4) {
      selectedSubTab = requestedSubTab
      onConsumedRequestedSubTab()
    }
  }
  var showInviteSheet by remember { mutableStateOf(false) }
  var showAssignTaskSheet by remember { mutableStateOf(false) }
  var showAssignPerkForMember by remember { mutableStateOf<CloudGroupMember?>(null) }
  var showAssignPerkSheet by remember { mutableStateOf(false) }
  var showRemoteLimitSheetForMember by remember { mutableStateOf<CloudGroupMember?>(null) }
  var taskForProofSubmission by remember { mutableStateOf<CloudGroupTask?>(null) }

  val installedApps = remember(context) {
    RealDeviceAppsScanner.queryRealInstalledApps(context, includeSystemApps = false)
  }

  LazyColumn(
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
      .fillMaxSize()
      .testTag("group_tab_list")
  ) {
    // 1. Pending Email Invitations Banner (works even if invite was sent before account creation)
    if (pendingInvitesForMe.isNotEmpty()) {
      item {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("pending_group_invites_card")
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
              )
              Text(
                text = "Group Requests for $currentUserEmail (${pendingInvitesForMe.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
              )
            }

            if (isGroupManager) {
              Text(
                text = "You are currently a Group Manager. Remove your spot as Group Manager first if you want to join another group.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
              )
            }

            pendingInvitesForMe.forEach { invite ->
              Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Text(
                    text = invite.groupName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "Invited by ${invite.senderName} (${invite.senderEmail}) • Role: ${invite.parsedProposedRole.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    TextButton(
                      onClick = { onDeclineInvite(invite) },
                      modifier = Modifier.testTag("decline_group_invite_${invite.id}")
                    ) {
                      Text("Decline")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                      onClick = { onAcceptInvite(invite) },
                      enabled = !isGroupManager,
                      modifier = Modifier.testTag("accept_group_invite_${invite.id}")
                    ) {
                      Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Accept & Join")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    // 2. If User Is Not in Any Group Yet -> Mode Switcher: Create Group OR Requests to Join
    if (activeGroup == null) {
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("group_entry_mode_row"),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          FilterChip(
            selected = selectedEntryMode == 0,
            onClick = { selectedEntryMode = 0 },
            label = {
              Text(
                text = "Create Group",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
              )
            },
            leadingIcon = {
              Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            modifier = Modifier
              .weight(1f)
              .testTag("group_entry_create_button")
          )
          FilterChip(
            selected = selectedEntryMode == 1,
            onClick = {
              selectedEntryMode = 1
              onRefreshRequests()
            },
            label = {
              Text(
                text = "Requests (${pendingInvitesForMe.size})",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
              )
            },
            leadingIcon = {
              Icon(Icons.Default.Mail, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            modifier = Modifier
              .weight(1f)
              .testTag("group_entry_requests_button")
          )
        }
      }

      if (selectedEntryMode == 1) {
        item {
          JoinGroupRequestsEmptyOrListCard(
            currentUserEmail = currentUserEmail,
            pendingInvites = pendingInvitesForMe,
            onRefreshRequests = onRefreshRequests,
            onAcceptInvite = onAcceptInvite,
            onDeclineInvite = onDeclineInvite,
            onSwitchToCreate = { selectedEntryMode = 0 }
          )
        }
      } else {
        item {
          CreateGroupManagerSetupCard(
            currentUserEmail = currentUserEmail,
            pendingRequestsCount = pendingInvitesForMe.size,
            onCreateGroup = onCreateGroup,
            onOpenRequests = {
              selectedEntryMode = 1
              onRefreshRequests()
            }
          )
        }
      }
      return@LazyColumn
    }

    // 3. Active Group Header Card with "Request" Button & Role Controls
    item {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("active_group_header_card")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.weight(1f)
            ) {
              Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(46.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = if (activeGroup.parsedType == GroupType.FAMILY) {
                      Icons.Default.Diversity3
                    } else {
                      Icons.Default.Business
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                  )
                }
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = activeGroup.name,
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "${activeGroup.parsedType.label} • Your Role: ${currentMember?.parsedRole?.label ?: if (isGroupManager) "Group Manager" else "Member"}",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
              }
            }

            if (canManageGroup) {
              Button(
                onClick = { showInviteSheet = true },
                modifier = Modifier.testTag("send_group_request_button")
              ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Request")
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "${groupMembers.size} members • ${groupTasks.count { !it.isCompleted }} open tasks",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            // Supervised children cannot leave the group to bypass parental limits
            if (!isChildSupervised) {
              OutlinedButton(
                onClick = onLeaveOrDissolveGroup,
                colors = ButtonDefaults.outlinedButtonColors(
                  contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.testTag("leave_or_dissolve_group_button")
              ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  if (isGroupManager) "Remove Manager Spot" else "Leave Group",
                  style = MaterialTheme.typography.labelMedium
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            val myBalance = currentMember?.creditBalance ?: 0L
            Surface(
              color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
              shape = CircleShape
            ) {
              Text(
                text = if (activeGroup.creditRules.enabled) {
                  "Your Points: ${CreditCalculator.formatSignedPoints(myBalance)}"
                } else {
                  "Group Points: Off (Default)"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
              )
            }

            FilledTonalButton(
              onClick = onOpenCreditSheet,
              modifier = Modifier
                .height(48.dp)
                .testTag("group_credits_rules_button")
            ) {
              Icon(Icons.Default.Stars, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(if (isMainManager) "Points & Rules" else "View Points")
            }
          }
        }
      }
    }

    // 4. Device Admin Protection & Remote Lock Status Card
    if (!isDeviceAdminGranted || currentMember?.remoteDeviceLocked == true) {
      item {
        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (currentMember?.remoteDeviceLocked == true) {
              MaterialTheme.colorScheme.errorContainer
            } else {
              MaterialTheme.colorScheme.secondaryContainer
            }
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = if (currentMember?.remoteDeviceLocked == true) {
                  Icons.Default.Lock
                } else {
                  Icons.Default.AdminPanelSettings
                },
                contentDescription = null,
                tint = if (currentMember?.remoteDeviceLocked == true) {
                  MaterialTheme.colorScheme.onErrorContainer
                } else {
                  MaterialTheme.colorScheme.onSecondaryContainer
                }
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (currentMember?.remoteDeviceLocked == true) {
                    "Device Remotely Locked by Manager"
                  } else {
                    "Device Admin Guard Required"
                  },
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = if (currentMember?.remoteDeviceLocked == true) {
                    "Your device is locked via cloud policy until unlocked by a Parent / Manager."
                  } else {
                    "Prevents unauthorized uninstallation and enables cloud remote device lock."
                  },
                  style = MaterialTheme.typography.bodySmall
                )
              }
            }
            if (!isDeviceAdminGranted) {
              FilledTonalButton(
                onClick = { TasksPlusDeviceAdminReceiver.requestDeviceAdminActivation(context) },
                modifier = Modifier.testTag("group_enable_device_admin_button")
              ) {
                Text("Enable")
              }
            }
          }
        }
      }
    }

    // 5. Sub-Section Navigation Chips (Group Tasks | Perks | Leaderboard | Members | Limits)
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(
          selected = selectedSubTab == 0,
          onClick = { selectedSubTab = 0 },
          label = { Text("Tasks (${groupTasks.size})") },
          leadingIcon = {
            Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          modifier = Modifier.testTag("group_subtab_tasks")
        )
        FilterChip(
          selected = selectedSubTab == 4,
          onClick = { selectedSubTab = 4 },
          label = { Text("Perks (${groupPerks.count { it.parsedStatus == PerkStatus.ASSIGNED }})") },
          leadingIcon = {
            Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          modifier = Modifier.testTag("group_subtab_perks")
        )
        FilterChip(
          selected = selectedSubTab == 3,
          onClick = { selectedSubTab = 3 },
          label = { Text("Leaderboard") },
          leadingIcon = {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          modifier = Modifier.testTag("group_subtab_leaderboard")
        )
        FilterChip(
          selected = selectedSubTab == 1,
          onClick = { selectedSubTab = 1 },
          label = { Text("Members (${groupMembers.size})") },
          leadingIcon = {
            Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          modifier = Modifier.testTag("group_subtab_members")
        )
        FilterChip(
          selected = selectedSubTab == 2,
          onClick = { selectedSubTab = 2 },
          label = { Text("Limits (${groupScreenTimeLimits.size})") },
          leadingIcon = {
            Icon(Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          modifier = Modifier.testTag("group_subtab_screentime")
        )
      }
    }

    // 6. Sub-Section Content
    when (selectedSubTab) {
      0 -> {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Synced Group Tasks & Proofs",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            if (canManageGroup || !isChildSupervised) {
              Button(
                onClick = { showAssignTaskSheet = true },
                modifier = Modifier.testTag("assign_group_task_button")
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Assign Task")
              }
            }
          }
        }

        item {
          com.example.ui.components.GroupRelaySection(
            relays = groupRelays,
            members = groupMembers,
            currentUid = currentUserUid,
            isCurrentUserAdmin = canManageGroup,
            onCreateRelay = onCreateGroupRelay,
            onAdvanceRelay = onAdvanceGroupRelay,
            onDeleteRelay = onDeleteGroupRelay
          )
        }

        if (isEvaluatingProof) {
          item {
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Column {
                  Text(
                    text = "Gemini AI is evaluating task photo proof...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "Checking photo against the manager's verification instructions.",
                    style = MaterialTheme.typography.bodySmall
                  )
                }
              }
            }
          }
        }

        if (groupTasks.isEmpty()) {
          item {
            Card(
              shape = RoundedCornerShape(22.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.TaskAlt,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(36.dp)
                )
                Text(
                  text = "No Group Tasks Yet",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "Assign tasks to the whole group or a specific member. Optionally gate apps until a photo proof is approved by you or Gemini AI.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        } else {
          items(items = groupTasks, key = { it.id }) { gTask ->
            GroupTaskCard(
              task = gTask,
              currentUserUid = currentUserUid,
              canManageGroup = canManageGroup,
              onCompleteWithoutProof = {
                onCompleteOrSubmitTaskProof(gTask, null, "", null)
              },
              onOpenProofUpload = {
                taskForProofSubmission = gTask
              },
              onRecheckWithGemini = {
                onRecheckProofWithGemini(gTask)
              },
              onApproveProof = {
                onReviewTaskProof(gTask, true, "Approved by ${currentMember?.displayName ?: "Manager"}")
              },
              onRejectProof = {
                onReviewTaskProof(gTask, false, "Please redo and upload a clearer photo proof.")
              },
              onDeleteTask = {
                onDeleteGroupTask(gTask.id)
              }
            )
          }
        }
      }

      1 -> {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Group Members & Permissions",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            if (canManageGroup) {
              FilledTonalButton(
                onClick = { showInviteSheet = true }
              ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Invite by Email")
              }
            }
          }
        }

        items(items = groupMembers, key = { it.uid }) { member ->
          val assignedPerksCount = remember(groupPerks, member.uid) {
            groupPerks.count { it.assignedToUid == member.uid && it.parsedStatus == PerkStatus.ASSIGNED }
          }
          GroupMemberCard(
            member = member,
            isCurrentUser = member.uid == currentUserUid,
            isMainManager = isMainManager,
            isGroupManager = isGroupManager,
            canManageGroup = canManageGroup,
            creditSystemEnabled = activeGroup.creditSystemEnabled,
            assignedPerksCount = assignedPerksCount,
            onSyncMyApps = {
              coroutineScope.launch {
                val scanned = withContext(Dispatchers.IO) {
                  RealDeviceAppsScanner.queryRealInstalledApps(context, includeSystemApps = false)
                }
                onSyncMyDeviceApps(scanned)
              }
            },
            onUpdateRoleOrLock = { newRole, canManage, remoteLocked ->
              onUpdateMemberRoleOrLock(member.uid, newRole, canManage, remoteLocked)
            },
            onSetScreenTimeForMember = {
              showRemoteLimitSheetForMember = member
            },
            onAssignPerkToMember = {
              showAssignPerkForMember = member
              showAssignPerkSheet = true
            }
          )
        }

        if (sentGroupInvites.isNotEmpty()) {
          item {
            Text(
              text = "Pending Email Requests (${sentGroupInvites.size})",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 8.dp)
            )
          }
          items(items = sentGroupInvites, key = { it.id }) { sent ->
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = sent.targetEmail,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "Proposed Role: ${sent.parsedProposedRole.label} • Waiting for user to sign in & accept",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      }

      2 -> {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Member Screen Time & App Gates",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            if (canManageGroup && groupMembers.isNotEmpty()) {
              Button(
                onClick = {
                  val defaultTarget = groupMembers.firstOrNull { it.uid != currentUserUid }
                    ?: groupMembers.first()
                  showRemoteLimitSheetForMember = defaultTarget
                },
                modifier = Modifier.testTag("add_remote_limit_button")
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add App Limit")
              }
            }
          }
        }

        if (groupScreenTimeLimits.isEmpty()) {
          item {
            Card(
              shape = RoundedCornerShape(22.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.HourglassTop,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(36.dp)
                )
                Text(
                  text = "No Remote App Limits Set",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "Managers and Parent Co-Managers can enforce hourly, daily, or weekly app limits or lock apps behind task completion for children.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        } else {
          items(items = groupScreenTimeLimits, key = { it.id }) { limit ->
            val limitTimeFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text(
                    text = "${limit.appName} → ${limit.targetMemberName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = limit.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    val modeLabel = when (limit.parsedLockMode) {
                      RemoteLockMode.LOCK_NOW -> "Lock Now"
                      RemoteLockMode.LOCK_AT_TIME -> if (limit.lockAtMillis > 0L) {
                        "At ${limitTimeFmt.format(Date(limit.lockAtMillis))}"
                      } else {
                        "Exact Time"
                      }
                      RemoteLockMode.USAGE_LIMIT -> "${formatMinutesShort(limit.limitMinutes.toInt())} / ${limit.parsedPeriod.label}"
                    }
                    GroupBadge(
                      text = modeLabel,
                      containerColor = MaterialTheme.colorScheme.primaryContainer,
                      contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    GroupBadge(
                      text = if (limit.playBeepOnLock) "Beep On" else "Silent",
                      containerColor = if (limit.playBeepOnLock) {
                        MaterialTheme.colorScheme.tertiaryContainer
                      } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                      },
                      contentColor = if (limit.playBeepOnLock) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                      } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                      }
                    )
                  }
                  if (limit.parsedLockMode == RemoteLockMode.USAGE_LIMIT) {
                    Text(
                      text = "Used: ${formatMinutesShort(limit.usedMinutes.toInt())} • Set by ${limit.enforcedByName}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  } else {
                    Text(
                      text = "Set by ${limit.enforcedByName}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  if (limit.requireTaskTitle.isNotBlank()) {
                    Text(
                      text = "Locked until task completed: ${limit.requireTaskTitle}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.error,
                      fontWeight = FontWeight.Bold
                    )
                  }
                  if (limit.taskInstructions.isNotBlank()) {
                    Text(
                      text = "Instructions: ${limit.taskInstructions}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
                if (canManageGroup) {
                  IconButton(
                    onClick = { onRemoveMemberScreenTimeLimit(limit.id) },
                    modifier = Modifier.testTag("remove_remote_limit_${limit.id}")
                  ) {
                    Icon(
                      imageVector = Icons.Default.Delete,
                      contentDescription = "Remove remote limit",
                      tint = MaterialTheme.colorScheme.error
                    )
                  }
                }
              }
            }
          }
        }
      }

      3 -> {
        item {
          GroupLeaderboardSection(
            isSignedIn = currentUserUid.isNotBlank(),
            currentUserUid = currentUserUid,
            activeGroup = activeGroup,
            groupMembers = groupMembers,
            groupTasks = groupTasks,
            onOpenCreditSheet = onOpenCreditSheet
          )
        }
      }

      4 -> {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Manager-Assigned Perks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = if (isMainManager) {
                  "Assign custom rewards or privileges to specific group members."
                } else {
                  "Perks assigned to group members by ${activeGroup.managerName.ifBlank { "the Group Manager" }}."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            if (isMainManager) {
              Button(
                onClick = {
                  showAssignPerkForMember = groupMembers.firstOrNull { it.uid != activeGroup.managerUid }
                  showAssignPerkSheet = true
                },
                modifier = Modifier
                  .height(48.dp)
                  .testTag("assign_group_perk_button")
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Assign Perk")
              }
            }
          }
        }

        item {
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.CardGiftcard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
              )
              Text(
                text = if (activeGroup.creditSystemEnabled) {
                  "Group Points are ON (${CreditCalculator.formatSignedPoints(currentMember?.creditBalance ?: 0L)} yours). Redeem assigned perks with points or request a direct manager grant."
                } else {
                  "Group Points are currently OFF (default). Only the main Group Manager can turn Group Points on, or directly grant any perk anytime."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
              )
            }
          }
        }

        if (groupPerks.isEmpty()) {
          item {
            Card(
              shape = RoundedCornerShape(22.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("group_perks_empty_card")
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.CardGiftcard,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(36.dp)
                )
                Text(
                  text = "No Perks Assigned Yet",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = if (isMainManager) {
                    "Tap Assign Perk to reward a specific member with extra screen time, privileges, or point-redeemable perks."
                  } else {
                    "Only the main Group Manager can assign perks to members."
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        } else {
          val orderedPerks = groupPerks.sortedWith(
            compareBy<CloudGroupPerk>(
              { if (it.assignedToUid == currentUserUid && it.parsedStatus == PerkStatus.ASSIGNED) 0 else 1 },
              {
                when (it.parsedStatus) {
                  PerkStatus.ASSIGNED -> 0
                  PerkStatus.REDEEMED -> 1
                  PerkStatus.GRANTED -> 2
                }
              }
            )
          )
          items(items = orderedPerks, key = { it.id }) { perk ->
            GroupPerkCard(
              perk = perk,
              currentUserUid = currentUserUid,
              isMainManager = isMainManager,
              creditSystemEnabled = activeGroup.creditSystemEnabled,
              currentUserBalance = currentMember?.creditBalance ?: 0L,
              onRedeem = { onRedeemGroupPerk(perk) },
              onMarkGranted = { onUpdateGroupPerkStatus(perk, PerkStatus.GRANTED) },
              onMakeAvailableAgain = { onUpdateGroupPerkStatus(perk, PerkStatus.ASSIGNED) },
              onDelete = { onDeleteGroupPerk(perk.id) }
            )
          }
        }
      }
    }
  }

  // Sheet 5: Assign Perk to a Specific Member (Main Group Manager Only)
  if (showAssignPerkSheet && activeGroup != null && isMainManager) {
    AssignGroupPerkSheet(
      groupName = activeGroup.name,
      creditSystemEnabled = activeGroup.creditSystemEnabled,
      eligibleMembers = groupMembers.filter { it.uid != activeGroup.managerUid },
      initialTargetMember = showAssignPerkForMember,
      onDismiss = {
        showAssignPerkSheet = false
        showAssignPerkForMember = null
      },
      onAssign = { targetMember, title, description, pointsCost, grantImmediately ->
        onAssignGroupPerk(targetMember, title, description, pointsCost, grantImmediately)
        showAssignPerkSheet = false
        showAssignPerkForMember = null
      }
    )
  }

  // Sheet 1: Send Email Request / Invitation
  if (showInviteSheet && activeGroup != null) {
    SendGroupInviteSheet(
      groupName = activeGroup.name,
      groupType = activeGroup.parsedType,
      onDismiss = { showInviteSheet = false },
      onSendInvite = { email, role ->
        onSendInviteRequest(email, role)
        showInviteSheet = false
      }
    )
  }

  // Sheet 2: Create / Assign Group Task (with Photo Proof, Gated App & Gemini AI Verification)
  if (showAssignTaskSheet && activeGroup != null) {
    AssignGroupTaskSheet(
      members = groupMembers,
      localInstalledApps = installedApps,
      onDismiss = { showAssignTaskSheet = false },
      onAssign = { title, subtext, uid, name, prio, dueHours, alarm, reqProof, useGemini, geminiPrompt, gatePkg, gateName, rewardMins, lockMode, lockAtMs, playBeep, urgencyLevel ->
        onCreateGroupTask(
          title,
          subtext,
          uid,
          name,
          prio,
          dueHours,
          alarm,
          reqProof,
          useGemini,
          geminiPrompt,
          gatePkg,
          gateName,
          rewardMins,
          lockMode,
          lockAtMs,
          playBeep,
          urgencyLevel
        )
        showAssignTaskSheet = false
      }
    )
  }

  // Sheet 3: Submit Photo Proof (Camera or Photo Picker)
  val activeProofTask = taskForProofSubmission
  if (activeProofTask != null) {
    SubmitTaskProofSheet(
      task = activeProofTask,
      onDismiss = { taskForProofSubmission = null },
      onSubmitProof = { bitmap, note, beforeBmp ->
        onCompleteOrSubmitTaskProof(activeProofTask, bitmap, note, beforeBmp)
        taskForProofSubmission = null
      }
    )
  }

  // Sheet 4: Set Remote Screen Time Limit / App Lock for a Group Member
  val targetMemberForLimit = showRemoteLimitSheetForMember
  if (targetMemberForLimit != null) {
    SetMemberRemoteLimitSheet(
      targetMember = targetMemberForLimit,
      allMembers = groupMembers,
      localFallbackApps = installedApps,
      openGroupTasks = groupTasks.filter { !it.isCompleted },
      onDismiss = { showRemoteLimitSheetForMember = null },
      onSave = { member, pkg, appName, mins, period, lockMode, lockAtMs, instructions, playBeep, existingTaskId, existingTaskTitle, inlineTaskTitle, inlinePhotoProof, inlineUseGemini, inlineGeminiPrompt, inlineRewardMins ->
        if (inlineTaskTitle.isNotBlank()) {
          onLockMemberAppWithTask(
            member.uid,
            member.displayName,
            pkg,
            appName,
            mins,
            period,
            lockMode,
            lockAtMs,
            instructions,
            playBeep,
            existingTaskId,
            existingTaskTitle,
            inlineTaskTitle,
            inlinePhotoProof,
            inlineUseGemini,
            inlineGeminiPrompt,
            inlineRewardMins
          )
        } else {
          onSaveMemberScreenTimeLimit(
            member.uid,
            member.displayName,
            pkg,
            appName,
            mins,
            period,
            existingTaskId,
            existingTaskTitle,
            lockMode,
            lockAtMs,
            instructions,
            playBeep
          )
        }
        showRemoteLimitSheetForMember = null
      }
    )
  }
}

@Composable
private fun JoinGroupRequestsEmptyOrListCard(
  currentUserEmail: String,
  pendingInvites: List<CloudGroupInvite>,
  onRefreshRequests: () -> Unit,
  onAcceptInvite: (CloudGroupInvite) -> Unit,
  onDeclineInvite: (CloudGroupInvite) -> Unit,
  onSwitchToCreate: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(26.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("join_requests_view_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Surface(
          color = MaterialTheme.colorScheme.tertiaryContainer,
          shape = CircleShape,
          modifier = Modifier.size(48.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.Mail,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
          }
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Join a Group via Request",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Signed in as $currentUserEmail",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (pendingInvites.isEmpty()) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = "No pending requests for $currentUserEmail yet",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "To join an existing Family or Team group instead of creating a new one, ask the Group Manager to open their Group tab, tap Request, and send an invite to:\n$currentUserEmail",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        pendingInvites.forEach { invite ->
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = invite.groupName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "From ${invite.senderName} (${invite.senderEmail}) • Role: ${invite.parsedProposedRole.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
              ) {
                TextButton(
                  onClick = { onDeclineInvite(invite) },
                  modifier = Modifier.testTag("requests_view_decline_${invite.id}")
                ) {
                  Text("Decline")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                  onClick = { onAcceptInvite(invite) },
                  modifier = Modifier.testTag("requests_view_accept_${invite.id}")
                ) {
                  Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Accept & Join")
                }
              }
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        FilledTonalButton(
          onClick = onRefreshRequests,
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("refresh_group_requests_button")
        ) {
          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Check Again", maxLines = 1, softWrap = false)
        }
        OutlinedButton(
          onClick = onSwitchToCreate,
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("switch_to_create_group_button")
        ) {
          Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Create Group", maxLines = 1, softWrap = false)
        }
      }
    }
  }
}

@Composable
private fun CreateGroupManagerSetupCard(
  currentUserEmail: String,
  pendingRequestsCount: Int,
  onCreateGroup: (groupName: String, groupType: GroupType) -> Unit,
  onOpenRequests: () -> Unit
) {
  var groupName by rememberSaveable { mutableStateOf("") }
  var selectedType by rememberSaveable { mutableStateOf(GroupType.FAMILY) }

  Card(
    shape = RoundedCornerShape(26.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("create_group_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = CircleShape,
          modifier = Modifier.size(48.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.Groups,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
          }
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Start or Join a Group",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Signed in as $currentUserEmail",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Text(
        text = "Become a Group Manager for your family or business team to send email requests, assign real-time tasks, gate apps behind task proofs, or let Gemini AI automatically verify task photos.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        GroupType.entries.forEach { type ->
          FilterChip(
            selected = selectedType == type,
            onClick = { selectedType = type },
            label = { Text(type.label) },
            leadingIcon = if (selectedType == type) {
              { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
            modifier = Modifier
              .weight(1f)
              .testTag("group_type_chip_${type.name}")
          )
        }
      }

      OutlinedTextField(
        value = groupName,
        onValueChange = { groupName = it },
        label = {
          Text(
            if (selectedType == GroupType.FAMILY) {
              "Family Group Name (e.g., Smith Family)"
            } else {
              "Team / Business Name (e.g., Design Ops)"
            }
          )
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("group_name_input")
      )

      Button(
        onClick = {
          val finalName = groupName.trim().ifBlank {
            if (selectedType == GroupType.FAMILY) "My Family Group" else "My Team Group"
          }
          onCreateGroup(finalName, selectedType)
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("create_group_button")
      ) {
        Icon(Icons.Default.Shield, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Become Group Manager & Create Group")
      }

      OutlinedButton(
        onClick = onOpenRequests,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("create_card_view_requests_button")
      ) {
        Icon(Icons.Default.Mail, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("View Requests to Join ($pendingRequestsCount)")
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupTaskCard(
  task: CloudGroupTask,
  currentUserUid: String,
  canManageGroup: Boolean,
  onCompleteWithoutProof: () -> Unit,
  onOpenProofUpload: () -> Unit,
  onRecheckWithGemini: () -> Unit,
  onApproveProof: () -> Unit,
  onRejectProof: () -> Unit,
  onDeleteTask: () -> Unit
) {
  val dateFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
  val isAssignedToMe = task.assignedToUid == "ALL" || task.assignedToUid == currentUserUid
  val proofBitmap = remember(task.proofImageBase64) {
    GeminiTaskProofEvaluator.decodeBase64ToBitmap(task.proofImageBase64)
  }

  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (task.isCompleted) {
        MaterialTheme.colorScheme.surfaceContainerLow
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      }
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("group_task_card_${task.id}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = task.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          if (task.subtext.isNotBlank()) {
            Text(
              text = task.subtext,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        if (canManageGroup) {
          IconButton(
            onClick = onDeleteTask,
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = "Delete group task",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        GroupBadge(
          text = "For: ${task.assignedToName}",
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        GroupBadge(
          text = "By ${task.createdByName}",
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        GroupBadge(
          text = "Due ${dateFmt.format(Date(task.dueAtMillis))}",
          containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
          contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        com.example.ui.components.ThermometerUrgencyBadge(
          urgencyLevel = task.resolvedUrgencyLevel
        )
        if (task.gatedAppPackage.isNotBlank()) {
          GroupBadge(
            text = "Unlocks ${task.gatedAppName.ifBlank { task.gatedAppPackage }} (${formatMinutesShort(task.unlockRewardMinutes.toInt())})",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
          )
        }
        if (task.requiresPhotoProof) {
          GroupBadge(
            text = when (task.parsedProofStatus) {
              ProofStatus.NONE -> "Photo Proof Required"
              ProofStatus.PENDING_PROOF -> "Photo Proof Required"
              ProofStatus.PENDING_APPROVAL -> "Waiting for Review"
              ProofStatus.APPROVED -> "Proof Approved"
              ProofStatus.REJECTED -> "Proof Rejected - Retry"
            },
            containerColor = when (task.parsedProofStatus) {
              ProofStatus.APPROVED -> MaterialTheme.colorScheme.primaryContainer
              ProofStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
              else -> MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = when (task.parsedProofStatus) {
              ProofStatus.APPROVED -> MaterialTheme.colorScheme.onPrimaryContainer
              ProofStatus.REJECTED -> MaterialTheme.colorScheme.onErrorContainer
              else -> MaterialTheme.colorScheme.onSecondaryContainer
            }
          )
        }
        if (task.useGeminiAutoApproval) {
          GroupBadge(
            text = "Gemini AI Verifier",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
          )
        }
        if (task.isCompleted && task.creditsApplied && task.creditsDelta != 0L) {
          val dur = CreditCalculator.formatDurationSeconds(task.creditSecondsDelta)
          val timing = if (task.creditSecondsDelta > 0L) "early $dur" else "late $dur"
          val positive = task.creditsDelta >= 0L
          GroupBadge(
            text = "${CreditCalculator.formatSignedPoints(task.creditsDelta)} ($timing)",
            containerColor = if (positive) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.errorContainer
            },
            contentColor = if (positive) {
              MaterialTheme.colorScheme.onPrimaryContainer
            } else {
              MaterialTheme.colorScheme.onErrorContainer
            }
          )
        }
      }

      if (task.useGeminiAutoApproval && task.geminiVerificationPrompt.isNotBlank()) {
        Text(
          text = "Gemini Check: \"${task.geminiVerificationPrompt}\"",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary
        )
      }

      // Show uploaded photo proof & Gemini feedback if present
      if (proofBitmap != null) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Image(
              bitmap = proofBitmap.asImageBitmap(),
              contentDescription = "Submitted Task Proof Photo",
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(12.dp))
            )
            if (task.proofNote.isNotBlank()) {
              Text(
                text = "Note: ${task.proofNote}",
                style = MaterialTheme.typography.bodySmall
              )
            }
            if (task.geminiEvaluationResult.isNotBlank()) {
              Text(
                text = task.geminiEvaluationResult,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (task.parsedProofStatus == ProofStatus.APPROVED) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.error
                }
              )
            }
          }
        }
      } else if (task.geminiEvaluationResult.isNotBlank()) {
        Text(
          text = task.geminiEvaluationResult,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = if (task.parsedProofStatus == ProofStatus.APPROVED) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.error
          }
        )
      }

      // Action buttons for Assignee or Manager
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (!task.isCompleted && isAssignedToMe) {
          if (task.requiresPhotoProof) {
            Button(
              onClick = onOpenProofUpload,
              modifier = Modifier.testTag("upload_proof_button_${task.id}")
            ) {
              Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                if (task.parsedProofStatus == ProofStatus.PENDING_APPROVAL) {
                  "Update Photo Proof"
                } else {
                  "Submit Photo Proof"
                }
              )
            }
          } else {
            Button(
              onClick = onCompleteWithoutProof,
              modifier = Modifier.testTag("complete_group_task_${task.id}")
            ) {
              Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Mark Completed")
            }
          }
        }

        if (!task.isCompleted &&
          task.useGeminiAutoApproval &&
          task.proofImageBase64.isNotBlank() &&
          (task.parsedProofStatus == ProofStatus.PENDING_APPROVAL || task.parsedProofStatus == ProofStatus.REJECTED)
        ) {
          FilledTonalButton(
            onClick = onRecheckWithGemini,
            modifier = Modifier.testTag("recheck_gemini_${task.id}")
          ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Re-check with Gemini AI")
          }
        }

        if (canManageGroup && task.requiresPhotoProof && task.parsedProofStatus == ProofStatus.PENDING_APPROVAL) {
          OutlinedButton(
            onClick = onRejectProof,
            colors = ButtonDefaults.outlinedButtonColors(
              contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.testTag("reject_proof_${task.id}")
          ) {
            Text("Reject")
          }
          Button(
            onClick = onApproveProof,
            modifier = Modifier.testTag("approve_proof_${task.id}")
          ) {
            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Approve & Unlock")
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupMemberCard(
  member: CloudGroupMember,
  isCurrentUser: Boolean,
  isMainManager: Boolean,
  isGroupManager: Boolean,
  canManageGroup: Boolean,
  creditSystemEnabled: Boolean,
  assignedPerksCount: Int,
  onSyncMyApps: () -> Unit,
  onUpdateRoleOrLock: (GroupRole, Boolean, Boolean) -> Unit,
  onSetScreenTimeForMember: () -> Unit,
  onAssignPerkToMember: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("group_member_card_${member.uid}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        ProfileAvatarBadge(
          fallbackInitial = (member.displayName.firstOrNull() ?: member.email.firstOrNull() ?: 'M').uppercase(),
          size = 44.dp,
          modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (isCurrentUser) "${member.displayName} (You)" else member.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "${member.email} • ${member.parsedRole.label}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = if (member.installedApps.isNotEmpty()) {
              "${member.installedApps.size} device apps synced"
            } else {
              "Device apps not synced yet"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (member.installedApps.isNotEmpty()) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            }
          )
          if (creditSystemEnabled) {
            Text(
              text = "Points: ${CreditCalculator.formatSignedPoints(member.creditBalance)} (+${member.totalEarnedCredits} / -${member.totalDeductedCredits})",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = if (member.creditBalance >= 0L) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.error
              },
              modifier = Modifier.testTag("member_credit_badge_${member.uid}")
            )
          } else if (assignedPerksCount > 0) {
            Text(
              text = "$assignedPerksCount available perk(s)",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        if (isCurrentUser) {
          FilledTonalButton(
            onClick = onSyncMyApps,
            modifier = Modifier
              .height(48.dp)
              .testTag("sync_my_device_apps_button")
          ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Sync My Apps")
          }
        } else if (canManageGroup) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isMainManager && member.parsedRole != GroupRole.MANAGER) {
              OutlinedButton(
                onClick = onAssignPerkToMember,
                modifier = Modifier
                  .height(48.dp)
                  .testTag("assign_member_perk_${member.uid}")
              ) {
                Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Perk")
              }
            }
            FilledTonalButton(
              onClick = onSetScreenTimeForMember,
              modifier = Modifier
                .height(48.dp)
                .testTag("set_member_limit_${member.uid}")
            ) {
              Icon(Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Limit")
            }
          }
        }
      }

      // Group Manager can assign Parent/Co-Manager, Member, or Child roles to other members
      if (isGroupManager && !isCurrentUser && member.parsedRole != GroupRole.MANAGER) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf(GroupRole.PARENT_ADMIN, GroupRole.MEMBER, GroupRole.CHILD).forEach { roleOption ->
            FilterChip(
              selected = member.parsedRole == roleOption,
              onClick = {
                onUpdateRoleOrLock(
                  roleOption,
                  roleOption == GroupRole.PARENT_ADMIN,
                  member.remoteDeviceLocked
                )
              },
              label = { Text(roleOption.label) },
              modifier = Modifier.testTag("role_chip_${member.uid}_${roleOption.name}")
            )
          }
        }
      }

      // Remote Device Lock toggle for Managers / Parent Co-Managers on supervised members
      if (canManageGroup && !isCurrentUser && member.parsedRole != GroupRole.MANAGER) {
        Surface(
          color = if (member.remoteDeviceLocked) {
            MaterialTheme.colorScheme.errorContainer
          } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = if (member.remoteDeviceLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = if (member.remoteDeviceLocked) {
                  MaterialTheme.colorScheme.onErrorContainer
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
              )
              Column {
                Text(
                  text = if (member.remoteDeviceLocked) {
                    "Remote Device Lock: LOCKED"
                  } else {
                    "Remote Device Lock (Device Admin)"
                  },
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "Locks ${member.displayName}'s device immediately when turned on",
                  style = MaterialTheme.typography.labelSmall
                )
              }
            }
            Switch(
              checked = member.remoteDeviceLocked,
              onCheckedChange = { locked ->
                onUpdateRoleOrLock(member.parsedRole, member.canManageOthers, locked)
              },
              modifier = Modifier.testTag("toggle_device_lock_${member.uid}")
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendGroupInviteSheet(
  groupName: String,
  groupType: GroupType,
  onDismiss: () -> Unit,
  onSendInvite: (email: String, role: GroupRole) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var email by rememberSaveable { mutableStateOf("") }
  var selectedRole by rememberSaveable {
    mutableStateOf(if (groupType == GroupType.FAMILY) GroupRole.CHILD else GroupRole.MEMBER)
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 10.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Text(
        text = "Send Request to Join $groupName",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "Enter their Gmail address. Even if they haven't signed into Tasks+ yet, they will see your request immediately in the Group tab once they sign in.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Recipient Gmail / Email Address") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("invite_email_input")
      )

      Text(
        text = "Assign Role & Permissions",
        style = MaterialTheme.typography.labelLarge
      )

      listOf(GroupRole.PARENT_ADMIN, GroupRole.MEMBER, GroupRole.CHILD).forEach { role ->
        Surface(
          color = if (selectedRole == role) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.surfaceContainer
          },
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { selectedRole = role }
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = role.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = role.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            if (selectedRole == role) {
              Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }

      Button(
        onClick = { onSendInvite(email, selectedRole) },
        enabled = email.contains("@"),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("confirm_send_invite_button")
      ) {
        Icon(Icons.Default.Send, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Send Group Request")
      }
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AssignGroupTaskSheet(
  members: List<CloudGroupMember>,
  localInstalledApps: List<InstalledAppInfo>,
  onDismiss: () -> Unit,
  onAssign: (
    title: String,
    subtext: String,
    assignedToUid: String,
    assignedToName: String,
    priority: TaskPriority,
    dueHoursFromNow: Int,
    ringAlarmBell: Boolean,
    requiresPhotoProof: Boolean,
    useGeminiAutoApproval: Boolean,
    geminiVerificationPrompt: String,
    gatedAppPackage: String,
    gatedAppName: String,
    unlockRewardMinutes: Int,
    lockMode: RemoteLockMode,
    lockAtMillis: Long,
    playBeepOnLock: Boolean,
    urgencyLevel: Int
  ) -> Unit
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val dateTimeFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

  var title by rememberSaveable { mutableStateOf("") }
  var subtext by rememberSaveable { mutableStateOf("") }
  var assignedToUid by rememberSaveable { mutableStateOf("ALL") }
  var assignedToName by rememberSaveable { mutableStateOf("Everyone in Group") }
  var urgencyLevel by rememberSaveable { mutableIntStateOf(4) }
  var dueHours by rememberSaveable { mutableIntStateOf(4) }
  var ringAlarmBell by rememberSaveable { mutableStateOf(false) }

  var gateSpecificApp by rememberSaveable { mutableStateOf(false) }
  var appSearchQuery by rememberSaveable { mutableStateOf("") }
  var selectedAppPkg by rememberSaveable { mutableStateOf("") }
  var selectedAppName by rememberSaveable { mutableStateOf("") }
  var unlockRewardMinutes by rememberSaveable { mutableIntStateOf(45) }
  var lockMode by rememberSaveable { mutableStateOf(RemoteLockMode.LOCK_NOW) }
  var lockAtMillis by rememberSaveable {
    mutableLongStateOf(System.currentTimeMillis() + 5 * 60_000L)
  }
  var playBeepOnLock by rememberSaveable { mutableStateOf(true) }

  var requiresPhotoProof by rememberSaveable { mutableStateOf(false) }
  var useGeminiAutoApproval by rememberSaveable { mutableStateOf(false) }
  var geminiVerificationPrompt by rememberSaveable { mutableStateOf("") }

  val targetMemberApps = remember(assignedToUid, members, localInstalledApps) {
    val synced = if (assignedToUid == "ALL") {
      members
        .flatMap { it.installedApps }
        .distinctBy { it.packageName }
        .map { InstalledAppInfo(it.packageName, it.appName) }
    } else {
      members
        .firstOrNull { it.uid == assignedToUid }
        ?.installedApps
        ?.map { InstalledAppInfo(it.packageName, it.appName) }
        .orEmpty()
    }
    synced.ifEmpty { localInstalledApps }
  }
  val hasRemoteApps = remember(assignedToUid, members) {
    if (assignedToUid == "ALL") {
      members.any { it.installedApps.isNotEmpty() }
    } else {
      members.firstOrNull { it.uid == assignedToUid }?.installedApps?.isNotEmpty() == true
    }
  }
  val filteredApps = remember(targetMemberApps, appSearchQuery) {
    val q = appSearchQuery.trim()
    if (q.isEmpty()) {
      targetMemberApps.take(25)
    } else {
      targetMemberApps.filter {
        it.appName.contains(q, ignoreCase = true) ||
          it.packageName.contains(q, ignoreCase = true)
      }.take(25)
    }
  }

  LaunchedEffect(assignedToUid, targetMemberApps) {
    if (selectedAppPkg.isBlank() && targetMemberApps.isNotEmpty()) {
      selectedAppPkg = targetMemberApps.first().packageName
      selectedAppName = targetMemberApps.first().appName
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 10.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
        text = "Assign Group Task",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
      )

      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Task Title (e.g., Finish Math Homework)") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("group_task_title_input")
      )

      OutlinedTextField(
        value = subtext,
        onValueChange = { subtext = it },
        label = { Text("Instructions / Details (shown on child's lock screen)") },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
      )

      Text(
        text = "Assign To",
        style = MaterialTheme.typography.labelLarge
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(
          selected = assignedToUid == "ALL",
          onClick = {
            assignedToUid = "ALL"
            assignedToName = "Everyone in Group"
          },
          label = { Text("Whole Group") }
        )
        members.forEach { member ->
          FilterChip(
            selected = assignedToUid == member.uid,
            onClick = {
              assignedToUid = member.uid
              assignedToName = member.displayName
            },
            label = { Text(member.displayName) }
          )
        }
      }

      com.example.ui.components.UrgencyThermometerSlider(
        urgencyLevel = urgencyLevel,
        onUrgencyLevelChange = { urgencyLevel = it },
        title = "Urgency Level"
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Ring Alarm Bell on Recipient Device", style = MaterialTheme.typography.bodyMedium)
        Switch(checked = ringAlarmBell, onCheckedChange = { ringAlarmBell = it })
      }

      HorizontalDivider()

      // Task-Gated App Access
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Lock / Gate an App Until Task Completed",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Locks the selected app on $assignedToName's device immediately or at an exact assigned time until completed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Switch(checked = gateSpecificApp, onCheckedChange = { gateSpecificApp = it })
      }

      AnimatedVisibility(visible = gateSpecificApp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          if (!hasRemoteApps) {
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "Member's synced device app list not uploaded yet — showing fallback device apps. They sync automatically whenever the member opens Tasks+ → Group tab, or type App Name & Package Name manually below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(10.dp)
              )
            }
          }

          OutlinedTextField(
            value = appSearchQuery,
            onValueChange = { appSearchQuery = it },
            placeholder = { Text("Search $assignedToName's apps or package...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
              if (appSearchQuery.isNotEmpty()) {
                IconButton(onClick = { appSearchQuery = "" }) {
                  Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
          )

          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 200.dp)
          ) {
            Column(
              modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              filteredApps.forEach { app ->
                val isSelected = selectedAppPkg == app.packageName
                Surface(
                  color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                  } else {
                    MaterialTheme.colorScheme.surfaceContainer
                  },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                      selectedAppPkg = app.packageName
                      selectedAppName = app.appName
                    }
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = app.appName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                    if (isSelected) {
                      Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                      )
                    }
                  }
                }
              }
            }
          }

          OutlinedTextField(
            value = selectedAppName,
            onValueChange = { selectedAppName = it },
            label = { Text("App Name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = selectedAppPkg,
            onValueChange = { selectedAppPkg = it },
            label = { Text("App Package Name (e.g., com.google.android.youtube)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
          )

          Text("When Should This App Lock?", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(RemoteLockMode.LOCK_NOW, RemoteLockMode.LOCK_AT_TIME).forEach { modeOpt ->
              FilterChip(
                selected = lockMode == modeOpt,
                onClick = {
                  lockMode = modeOpt
                  if (modeOpt == RemoteLockMode.LOCK_AT_TIME && lockAtMillis <= System.currentTimeMillis()) {
                    lockAtMillis = System.currentTimeMillis() + 5 * 60_000L
                  }
                },
                label = {
                  Text(if (modeOpt == RemoteLockMode.LOCK_NOW) "Lock Now" else "Exact Time")
                },
                modifier = Modifier.weight(1f)
              )
            }
          }

          if (lockMode == RemoteLockMode.LOCK_AT_TIME) {
            Text(
              text = "Scheduled Lock Time: ${dateTimeFmt.format(Date(lockAtMillis))}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf(1 to "+1m", 5 to "+5m", 15 to "+15m", 30 to "+30m", 60 to "+1h").forEach { (mins, label) ->
                FilterChip(
                  selected = false,
                  onClick = { lockAtMillis = System.currentTimeMillis() + mins * 60_000L },
                  label = { Text(label) }
                )
              }
            }
            OutlinedButton(
              onClick = {
                openDateTimePicker(context, lockAtMillis) { pickedMillis ->
                  lockAtMillis = pickedMillis
                }
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Pick Custom Date & Time")
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = if (playBeepOnLock) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
              )
              Text("Send Notification + Audible Beep on Lock", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = playBeepOnLock, onCheckedChange = { playBeepOnLock = it })
          }

          Text("Reward Screen Time After Completion:", style = MaterialTheme.typography.labelMedium)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 15, 30, 45, 60, 120).forEach { mins ->
              FilterChip(
                selected = unlockRewardMinutes == mins,
                onClick = { unlockRewardMinutes = mins },
                label = { Text(if (mins == 0) "Unlock Fully" else formatMinutesShort(mins)) }
              )
            }
          }
        }
      }

      HorizontalDivider()

      // Photo Proof + Gemini AI Verification
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Require Photo Proof of Completion",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Assignee must take a photo of the finished task before it is marked completed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Switch(
          checked = requiresPhotoProof,
          onCheckedChange = {
            requiresPhotoProof = it
            if (!it) useGeminiAutoApproval = false
          },
          modifier = Modifier.testTag("toggle_require_photo_proof")
        )
      }

      AnimatedVisibility(visible = requiresPhotoProof) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  )
                  Column {
                    Text(
                      text = "Assign Gemini AI to Verify Proof",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = "Gemini evaluates the uploaded photo and auto-approves & unlocks apps when conditions are met.",
                      style = MaterialTheme.typography.bodySmall
                    )
                  }
                }
                Switch(
                  checked = useGeminiAutoApproval,
                  onCheckedChange = { useGeminiAutoApproval = it },
                  modifier = Modifier.testTag("toggle_gemini_auto_approval")
                )
              }

              if (useGeminiAutoApproval) {
                OutlinedTextField(
                  value = geminiVerificationPrompt,
                  onValueChange = { geminiVerificationPrompt = it },
                  label = {
                    Text("Tell Gemini what to verify in the photo (e.g., Clean desk and completed worksheet)")
                  },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_prompt_input")
                )
              }
            }
          }
        }
      }

      Button(
        onClick = {
          onAssign(
            title,
            subtext,
            assignedToUid,
            assignedToName,
            TaskPriority.fromUrgencyLevel(urgencyLevel),
            dueHours,
            ringAlarmBell,
            requiresPhotoProof,
            useGeminiAutoApproval,
            geminiVerificationPrompt.ifBlank { "Verify that the photo clearly shows \"$title\" is completed." },
            if (gateSpecificApp) selectedAppPkg.trim() else "",
            if (gateSpecificApp) selectedAppName.trim().ifBlank { selectedAppPkg.trim() } else "",
            unlockRewardMinutes,
            if (gateSpecificApp) lockMode else RemoteLockMode.LOCK_NOW,
            if (gateSpecificApp && lockMode == RemoteLockMode.LOCK_AT_TIME) lockAtMillis else 0L,
            if (gateSpecificApp) playBeepOnLock else ringAlarmBell,
            urgencyLevel
          )
        },
        enabled = title.isNotBlank() && (!gateSpecificApp || selectedAppPkg.isNotBlank()),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("confirm_assign_group_task_button")
      ) {
        Icon(Icons.Default.Send, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Send Task to Device Now")
      }
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubmitTaskProofSheet(
  task: CloudGroupTask,
  onDismiss: () -> Unit,
  onSubmitProof: (bitmap: Bitmap?, note: String, beforeBitmap: Bitmap?) -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var beforeBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var proofNote by rememberSaveable { mutableStateOf("") }
  var pickerError by remember { mutableStateOf<String?>(null) }

  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { bmp ->
    if (bmp != null) {
      pickerError = null
      capturedBitmap = bmp
    }
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      scope.launch {
        val bmp = withContext(Dispatchers.IO) {
          GeminiTaskProofEvaluator.loadBitmapFromUri(context, uri, maxDimension = 1024)
        }
        if (bmp != null) {
          pickerError = null
          capturedBitmap = bmp
        } else {
          pickerError = "Could not load selected image. Try another photo."
        }
      }
    }
  }

  val beforePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      scope.launch {
        val bmp = withContext(Dispatchers.IO) {
          GeminiTaskProofEvaluator.loadBitmapFromUri(context, uri, maxDimension = 896)
        }
        if (bmp != null) {
          pickerError = null
          beforeBitmap = bmp
        }
      }
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 10.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Text(
        text = "Submit Photo Proof",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "Task: ${task.title}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
      )
      if (task.useGeminiAutoApproval) {
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
              text = "Gemini AI will evaluate your photo against: \"${task.geminiVerificationPrompt.ifBlank { task.title }}\" and unlock your app automatically if approved!",
              style = MaterialTheme.typography.bodySmall
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        FilledTonalButton(
          onClick = {
            try {
              cameraLauncher.launch(null)
            } catch (_: Exception) {
              pickerError = "No camera app available on this device. Use Choose Photo instead."
            }
          },
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("take_photo_proof_button")
        ) {
          Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Take Photo")
        }
        OutlinedButton(
          onClick = {
            try {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            } catch (_: Exception) {
              pickerError = "No photo gallery picker available on this device."
            }
          },
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("pick_photo_proof_button")
        ) {
          Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Choose Photo")
        }
      }

      if (!pickerError.isNullOrBlank()) {
        Text(
          text = pickerError.orEmpty(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error
        )
      }

      val bmp = capturedBitmap
      if (bmp != null) {
        Image(
          bitmap = bmp.asImageBitmap(),
          contentDescription = "Selected Proof Photo",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(16.dp))
        )
      }

      if (task.useGeminiAutoApproval) {
        OutlinedButton(
          onClick = {
            try {
              beforePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            } catch (_: Exception) {
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .testTag("pick_before_proof_button")
        ) {
          Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            if (beforeBitmap != null) {
              "Before Photo Attached (Gemini Before/After Diff Ready)"
            } else {
              "Optional: Add 'Before' Photo for Gemini Diff Score"
            }
          )
        }
      }

      OutlinedTextField(
        value = proofNote,
        onValueChange = { proofNote = it },
        label = { Text("Add a short note (optional)") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
      )

      Button(
        onClick = { onSubmitProof(capturedBitmap, proofNote, beforeBitmap) },
        enabled = capturedBitmap != null,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("submit_proof_button")
      ) {
        Icon(
          imageVector = if (task.useGeminiAutoApproval) Icons.Default.AutoAwesome else Icons.Default.Send,
          contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          if (task.useGeminiAutoApproval) {
            "Submit & Verify with Gemini AI"
          } else {
            "Send Proof to Manager"
          }
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SetMemberRemoteLimitSheet(
  targetMember: CloudGroupMember,
  allMembers: List<CloudGroupMember>,
  localFallbackApps: List<InstalledAppInfo>,
  openGroupTasks: List<CloudGroupTask>,
  onDismiss: () -> Unit,
  onSave: (
    member: CloudGroupMember,
    packageName: String,
    appName: String,
    limitMinutes: Int,
    period: ScreenTimePeriod,
    lockMode: RemoteLockMode,
    lockAtMillis: Long,
    taskInstructions: String,
    playBeepOnLock: Boolean,
    existingTaskId: String,
    existingTaskTitle: String,
    inlineTaskTitle: String,
    inlineRequiresPhotoProof: Boolean,
    inlineUseGeminiAutoApproval: Boolean,
    inlineGeminiPrompt: String,
    inlineRewardMinutes: Int
  ) -> Unit
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val dateTimeFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

  var selectedMember by remember { mutableStateOf(targetMember) }
  var appSearchQuery by rememberSaveable { mutableStateOf("") }
  var selectedPkg by rememberSaveable { mutableStateOf("") }
  var selectedAppName by rememberSaveable { mutableStateOf("") }

  var lockMode by rememberSaveable { mutableStateOf(RemoteLockMode.LOCK_NOW) }
  var lockAtMillis by rememberSaveable {
    mutableLongStateOf(System.currentTimeMillis() + 5 * 60_000L)
  }
  var selectedPeriod by rememberSaveable { mutableStateOf(ScreenTimePeriod.DAILY) }
  var limitMinutes by rememberSaveable { mutableIntStateOf(30) }
  var customMinutesText by rememberSaveable { mutableStateOf("30") }
  var taskInstructions by rememberSaveable { mutableStateOf("") }
  var playBeepOnLock by rememberSaveable { mutableStateOf(true) }

  // 0 = None, 1 = Link Existing Task, 2 = Create New Unlock Task
  var taskGateMode by rememberSaveable { mutableIntStateOf(0) }
  var selectedRequiredTask by remember { mutableStateOf<CloudGroupTask?>(null) }
  var inlineTaskTitle by rememberSaveable { mutableStateOf("") }
  var inlineRequiresPhotoProof by rememberSaveable { mutableStateOf(true) }
  var inlineUseGeminiAutoApproval by rememberSaveable { mutableStateOf(true) }
  var inlineGeminiPrompt by rememberSaveable { mutableStateOf("") }
  var inlineRewardMinutes by rememberSaveable { mutableIntStateOf(30) }

  val memberApps = remember(selectedMember, localFallbackApps) {
    val synced = selectedMember.installedApps.map {
      InstalledAppInfo(packageName = it.packageName, appName = it.appName)
    }
    synced
  }
  val displayedApps = remember(memberApps, localFallbackApps, appSearchQuery) {
    val base = memberApps.ifEmpty { localFallbackApps }
    val q = appSearchQuery.trim()
    if (q.isEmpty()) {
      base.take(30)
    } else {
      base.filter {
        it.appName.contains(q, ignoreCase = true) ||
          it.packageName.contains(q, ignoreCase = true)
      }.take(30)
    }
  }

  LaunchedEffect(selectedMember.uid, memberApps) {
    val firstApp = memberApps.firstOrNull() ?: localFallbackApps.firstOrNull()
    if (firstApp != null) {
      selectedPkg = firstApp.packageName
      selectedAppName = firstApp.appName
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 10.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
        text = "Lock / Limit Child's Device App",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
      )

      Text("Target Group Member:", style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        allMembers.forEach { member ->
          FilterChip(
            selected = selectedMember.uid == member.uid,
            onClick = {
              selectedMember = member
              appSearchQuery = ""
            },
            label = {
              Text("${member.displayName} (${member.installedApps.size})")
            }
          )
        }
      }

      if (memberApps.isEmpty()) {
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Default.PhoneAndroid,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
              text = "${selectedMember.displayName} hasn't synced their device apps yet. Ask them to open Tasks+ → Group tab on their device, or pick/enter an App Name & Package Name manually below.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer
            )
          }
        }
      } else {
        Text(
          text = "Select from ${selectedMember.displayName}'s ${memberApps.size} Installed Apps:",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary
        )
      }

      OutlinedTextField(
        value = appSearchQuery,
        onValueChange = { appSearchQuery = it },
        placeholder = { Text("Search ${selectedMember.displayName}'s apps or package name...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
          if (appSearchQuery.isNotEmpty()) {
            IconButton(onClick = { appSearchQuery = "" }) {
              Icon(Icons.Default.Close, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("member_app_search_input")
      )

      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 210.dp)
      ) {
        Column(
          modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          if (displayedApps.isEmpty()) {
            Text(
              text = "No matching apps found. Enter App Name & Package Name manually below.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(8.dp)
            )
          } else {
            displayedApps.forEach { app ->
              val isSelected = selectedPkg == app.packageName
              Surface(
                color = if (isSelected) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  MaterialTheme.colorScheme.surfaceContainer
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .clickable {
                    selectedPkg = app.packageName
                    selectedAppName = app.appName
                  }
                  .testTag("member_app_option_${app.packageName}")
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = app.appName,
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = app.packageName,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                  if (isSelected) {
                    Icon(
                      imageVector = Icons.Default.CheckCircle,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }

      OutlinedTextField(
        value = selectedAppName,
        onValueChange = { selectedAppName = it },
        label = { Text("App Name") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
      )

      OutlinedTextField(
        value = selectedPkg,
        onValueChange = { selectedPkg = it },
        label = { Text("App Package Name (e.g., com.google.android.youtube)") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
      )

      HorizontalDivider()

      Text("Lock Mode & Exact Schedule:", style = MaterialTheme.typography.labelLarge)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        RemoteLockMode.entries.forEach { mode ->
          FilterChip(
            selected = lockMode == mode,
            onClick = {
              lockMode = mode
              if (mode == RemoteLockMode.LOCK_AT_TIME && lockAtMillis <= System.currentTimeMillis()) {
                lockAtMillis = System.currentTimeMillis() + 5 * 60_000L
              }
            },
            label = {
              Text(
                when (mode) {
                  RemoteLockMode.LOCK_NOW -> "Lock Now"
                  RemoteLockMode.LOCK_AT_TIME -> "Exact Time"
                  RemoteLockMode.USAGE_LIMIT -> "Usage Limit"
                }
              )
            },
            modifier = Modifier.testTag("remote_lock_mode_${mode.name}")
          )
        }
      }

      if (lockMode == RemoteLockMode.LOCK_AT_TIME) {
        Text(
          text = "Blocks at exact time: ${dateTimeFmt.format(Date(lockAtMillis))}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(1 to "+1m", 5 to "+5m", 15 to "+15m", 30 to "+30m", 60 to "+1h").forEach { (mins, label) ->
            FilterChip(
              selected = false,
              onClick = { lockAtMillis = System.currentTimeMillis() + mins * 60_000L },
              label = { Text(label) }
            )
          }
        }
        OutlinedButton(
          onClick = {
            openDateTimePicker(context, lockAtMillis) { pickedMillis ->
              lockAtMillis = pickedMillis
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("pick_exact_lock_time_button")
        ) {
          Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Pick Date & Time")
        }
      }

      if (lockMode == RemoteLockMode.USAGE_LIMIT) {
        Text("Period & Allowed Minutes:", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ScreenTimePeriod.entries.forEach { p ->
            FilterChip(
              selected = selectedPeriod == p,
              onClick = { selectedPeriod = p },
              label = { Text(p.label) },
              modifier = Modifier.weight(1f)
            )
          }
        }
        OutlinedTextField(
          value = customMinutesText,
          onValueChange = { valDigits ->
            val clean = valDigits.filter { it.isDigit() }.take(4)
            customMinutesText = clean
            clean.toIntOrNull()?.let { if (it > 0) limitMinutes = it }
          },
          label = { Text("Allowed Minutes") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        )
      }

      OutlinedTextField(
        value = taskInstructions,
        onValueChange = { taskInstructions = it },
        label = { Text("Instructions shown on ${selectedMember.displayName}'s lock screen") },
        placeholder = { Text("e.g., Complete 20 math problems and upload a photo to unlock") },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("remote_lock_instructions_input")
      )

      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = if (playBeepOnLock) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
            Column {
              Text(
                text = "Send Notification + Beep on Lock",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Notifies ${selectedMember.displayName} when the app locks (optional beep).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          Switch(
            checked = playBeepOnLock,
            onCheckedChange = { playBeepOnLock = it },
            modifier = Modifier.testTag("toggle_beep_on_lock")
          )
        }
      }

      HorizontalDivider()

      Text(
        text = "Task Required to Unlock This App:",
        style = MaterialTheme.typography.labelLarge
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(
          selected = taskGateMode == 0,
          onClick = {
            taskGateMode = 0
            selectedRequiredTask = null
            inlineTaskTitle = ""
          },
          label = { Text("No Task Gate") }
        )
        if (openGroupTasks.isNotEmpty()) {
          FilterChip(
            selected = taskGateMode == 1,
            onClick = {
              taskGateMode = 1
              inlineTaskTitle = ""
              if (selectedRequiredTask == null) {
                selectedRequiredTask = openGroupTasks.firstOrNull()
              }
            },
            label = { Text("Existing Task") }
          )
        }
        FilterChip(
          selected = taskGateMode == 2,
          onClick = {
            taskGateMode = 2
            selectedRequiredTask = null
          },
          label = { Text("Create New Unlock Task") },
          modifier = Modifier.testTag("create_inline_unlock_task_chip")
        )
      }

      if (taskGateMode == 1 && openGroupTasks.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          openGroupTasks.forEach { task ->
            FilterChip(
              selected = selectedRequiredTask?.id == task.id,
              onClick = { selectedRequiredTask = task },
              label = { Text(task.title) }
            )
          }
        }
      }

      if (taskGateMode == 2) {
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedTextField(
              value = inlineTaskTitle,
              onValueChange = { inlineTaskTitle = it },
              label = { Text("Required Task Title (e.g., Clean your study desk)") },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("inline_unlock_task_title")
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Require Photo Proof", style = MaterialTheme.typography.bodyMedium)
              Switch(
                checked = inlineRequiresPhotoProof,
                onCheckedChange = {
                  inlineRequiresPhotoProof = it
                  if (!it) inlineUseGeminiAutoApproval = false
                }
              )
            }
            if (inlineRequiresPhotoProof) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Verify Proof with Gemini AI", style = MaterialTheme.typography.bodyMedium)
                Switch(
                  checked = inlineUseGeminiAutoApproval,
                  onCheckedChange = { inlineUseGeminiAutoApproval = it }
                )
              }
              if (inlineUseGeminiAutoApproval) {
                OutlinedTextField(
                  value = inlineGeminiPrompt,
                  onValueChange = { inlineGeminiPrompt = it },
                  label = { Text("What should Gemini check in the photo?") },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth()
                )
              }
            }
            Text("Unlock Reward After Approval:", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf(0, 15, 30, 45, 60).forEach { mins ->
                FilterChip(
                  selected = inlineRewardMinutes == mins,
                  onClick = { inlineRewardMinutes = mins },
                  label = { Text(if (mins == 0) "Unlock Fully" else formatMinutesShort(mins)) }
                )
              }
            }
          }
        }
      }

      Button(
        onClick = {
          val finalMins = customMinutesText.toIntOrNull()?.coerceIn(1, 10080) ?: limitMinutes
          val effectiveTaskTitle = if (taskGateMode == 2) inlineTaskTitle.trim() else ""
          val existingTask = if (taskGateMode == 1) selectedRequiredTask else null
          onSave(
            selectedMember,
            selectedPkg.trim(),
            selectedAppName.trim().ifBlank { selectedPkg.trim() },
            finalMins,
            selectedPeriod,
            lockMode,
            if (lockMode == RemoteLockMode.LOCK_AT_TIME) lockAtMillis else 0L,
            taskInstructions.trim(),
            playBeepOnLock,
            existingTask?.id.orEmpty(),
            existingTask?.title.orEmpty(),
            effectiveTaskTitle,
            inlineRequiresPhotoProof,
            inlineUseGeminiAutoApproval,
            inlineGeminiPrompt.trim().ifBlank {
              "Verify that the photo shows completion of \"$effectiveTaskTitle\"."
            },
            inlineRewardMinutes
          )
        },
        enabled = selectedPkg.isNotBlank() && (taskGateMode != 2 || inlineTaskTitle.isNotBlank()),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("save_remote_limit_button")
      ) {
        Icon(Icons.Default.Lock, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          when (lockMode) {
            RemoteLockMode.LOCK_NOW -> "Lock App on ${selectedMember.displayName}'s Device"
            RemoteLockMode.LOCK_AT_TIME -> "Schedule Exact Lock for ${selectedMember.displayName}"
            RemoteLockMode.USAGE_LIMIT -> "Enforce Limit on ${selectedMember.displayName}'s Device"
          }
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

private fun openDateTimePicker(
  context: android.content.Context,
  initialMillis: Long,
  onPicked: (Long) -> Unit
) {
  val base = Calendar.getInstance().apply {
    timeInMillis = if (initialMillis > System.currentTimeMillis()) initialMillis else System.currentTimeMillis() + 60_000L
  }
  DatePickerDialog(
    context,
    { _, year, month, dayOfMonth ->
      TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
          val picked = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
          }
          onPicked(picked.timeInMillis)
        },
        base.get(Calendar.HOUR_OF_DAY),
        base.get(Calendar.MINUTE),
        false
      ).show()
    },
    base.get(Calendar.YEAR),
    base.get(Calendar.MONTH),
    base.get(Calendar.DAY_OF_MONTH)
  ).show()
}

@Composable
private fun GroupBadge(
  text: String,
  containerColor: androidx.compose.ui.graphics.Color,
  contentColor: androidx.compose.ui.graphics.Color
) {
  Surface(
    color = containerColor,
    shape = RoundedCornerShape(10.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = contentColor,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupPerkCard(
  perk: CloudGroupPerk,
  currentUserUid: String,
  isMainManager: Boolean,
  creditSystemEnabled: Boolean,
  currentUserBalance: Long,
  onRedeem: () -> Unit,
  onMarkGranted: () -> Unit,
  onMakeAvailableAgain: () -> Unit,
  onDelete: () -> Unit
) {
  val dateFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
  val isAssignedToMe = perk.assignedToUid == currentUserUid
  val status = perk.parsedStatus
  val statusColors = when (status) {
    PerkStatus.ASSIGNED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    PerkStatus.REDEEMED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    PerkStatus.GRANTED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
  }

  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isAssignedToMe && status == PerkStatus.ASSIGNED) {
        MaterialTheme.colorScheme.surfaceContainerHigh
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      }
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("group_perk_card_${perk.id}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(
            text = perk.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          if (perk.description.isNotBlank()) {
            Text(
              text = perk.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        if (isMainManager) {
          IconButton(
            onClick = onDelete,
            modifier = Modifier
              .size(48.dp)
              .testTag("delete_group_perk_${perk.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = "Delete perk",
              tint = MaterialTheme.colorScheme.error
            )
          }
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        GroupBadge(
          text = status.label,
          containerColor = statusColors.first,
          contentColor = statusColors.second
        )
        GroupBadge(
          text = if (isAssignedToMe) "For You (${perk.assignedToName})" else "For ${perk.assignedToName}",
          containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
          contentColor = MaterialTheme.colorScheme.onSurface
        )
        GroupBadge(
          text = if (perk.pointsCost > 0L) "${perk.pointsCost} pts" else "Direct Manager Grant",
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        if (perk.redeemedCount > 0L) {
          GroupBadge(
            text = "Redeemed ${perk.redeemedCount}x",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      val footerNote = buildString {
        append("Assigned by ${perk.assignedByName.ifBlank { "Group Manager" }}")
        if (perk.redeemedAtMillis > 0L) {
          append(" • Updated ${dateFmt.format(Date(perk.redeemedAtMillis))}")
        }
      }
      Text(
        text = footerNote,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      if (isAssignedToMe && status == PerkStatus.ASSIGNED && perk.pointsCost > 0L) {
        val canRedeemNow = creditSystemEnabled && currentUserBalance >= perk.pointsCost
        Button(
          onClick = onRedeem,
          enabled = canRedeemNow,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("redeem_group_perk_${perk.id}")
        ) {
          Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            when {
              !creditSystemEnabled -> "Group Points Off (${perk.pointsCost} pts)"
              currentUserBalance < perk.pointsCost -> "Need ${perk.pointsCost - currentUserBalance} More Points"
              else -> "Redeem (-${perk.pointsCost} pts)"
            }
          )
        }
      } else if (isAssignedToMe && status == PerkStatus.ASSIGNED && perk.pointsCost == 0L) {
        Text(
          text = "Waiting for your Group Manager to grant this perk directly.",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary
        )
      }

      if (isMainManager) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (status != PerkStatus.GRANTED) {
            FilledTonalButton(
              onClick = onMarkGranted,
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("grant_group_perk_${perk.id}")
            ) {
              Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Mark Granted")
            }
          }
          if (status != PerkStatus.ASSIGNED) {
            OutlinedButton(
              onClick = onMakeAvailableAgain,
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("reopen_group_perk_${perk.id}")
            ) {
              Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Make Available")
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AssignGroupPerkSheet(
  groupName: String,
  creditSystemEnabled: Boolean,
  eligibleMembers: List<CloudGroupMember>,
  initialTargetMember: CloudGroupMember?,
  onDismiss: () -> Unit,
  onAssign: (
    targetMember: CloudGroupMember,
    title: String,
    description: String,
    pointsCost: Long,
    grantImmediately: Boolean
  ) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var selectedMemberUid by rememberSaveable {
    mutableStateOf(initialTargetMember?.uid ?: eligibleMembers.firstOrNull()?.uid.orEmpty())
  }
  var title by rememberSaveable { mutableStateOf("") }
  var description by rememberSaveable { mutableStateOf("") }
  var costText by rememberSaveable { mutableStateOf(if (creditSystemEnabled) "100" else "0") }
  var grantImmediately by rememberSaveable { mutableStateOf(false) }

  val selectedMember = eligibleMembers.firstOrNull { it.uid == selectedMemberUid }
    ?: eligibleMembers.firstOrNull()
  val parsedCost = costText.toLongOrNull()?.coerceIn(0L, 1_000_000_000L) ?: 0L

  val presetPerks = remember {
    listOf(
      "Extra 30m Screen Time" to "100",
      "Movie Night Pick" to "200",
      "Skip One Chore" to "150",
      "Coffee / Snack Treat" to "250",
      "Bonus Day Off Task" to "0"
    )
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 10.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
        text = "Assign Perk in $groupName",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "Only you (the main Group Manager) can assign or grant perks to other group members.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      if (eligibleMembers.isEmpty()) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "Invite at least one other member to your group before assigning perks.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(14.dp)
          )
        }
      } else {
        Text("Assign To Member:", style = MaterialTheme.typography.labelLarge)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          eligibleMembers.forEach { member ->
            val label = member.displayName.ifBlank { member.email.substringBefore("@") }
            FilterChip(
              selected = selectedMember?.uid == member.uid,
              onClick = { selectedMemberUid = member.uid },
              label = { Text("$label (${member.parsedRole.label})") },
              modifier = Modifier.testTag("perk_target_member_${member.uid}")
            )
          }
        }

        Text("Quick Presets:", style = MaterialTheme.typography.labelLarge)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          presetPerks.forEach { (presetTitle, presetCost) ->
            FilterChip(
              selected = title == presetTitle,
              onClick = {
                title = presetTitle
                costText = if (creditSystemEnabled) presetCost else "0"
              },
              label = { Text(presetTitle) }
            )
          }
        }

        OutlinedTextField(
          value = title,
          onValueChange = { title = it.take(120) },
          label = { Text("Perk Title") },
          placeholder = { Text("e.g., Extra 30m Screen Time or Coffee Treat") },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("perk_title_input")
        )

        OutlinedTextField(
          value = description,
          onValueChange = { description = it.take(500) },
          label = { Text("Details (optional)") },
          placeholder = { Text("Describe when or how this perk can be used") },
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("perk_description_input")
        )

        OutlinedTextField(
          value = costText,
          onValueChange = { costText = it.filter { ch -> ch.isDigit() }.take(7) },
          label = { Text("Points Cost (0 = Direct Manager Grant)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("perk_cost_input")
        )

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf(0L to "Direct (0)", 50L to "50 pts", 100L to "100 pts", 250L to "250 pts", 500L to "500 pts").forEach { (amt, label) ->
            FilterChip(
              selected = parsedCost == amt,
              onClick = { costText = amt.toString() },
              label = { Text(label) }
            )
          }
        }

        if (!creditSystemEnabled && parsedCost > 0L) {
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "Note: Group Points are currently off. Enable Group Points in Points & Rules so members can earn and redeem point-priced perks, or grant this perk directly.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(12.dp)
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Mark as Granted Immediately",
              style = MaterialTheme.typography.titleSmall
            )
            Text(
              text = "Grant right away instead of making the member redeem with points.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = grantImmediately,
            onCheckedChange = { grantImmediately = it },
            modifier = Modifier.testTag("perk_grant_immediately_switch")
          )
        }

        Button(
          onClick = {
            if (selectedMember != null && title.isNotBlank()) {
              onAssign(
                selectedMember,
                title.trim(),
                description.trim(),
                if (grantImmediately) 0L else parsedCost,
                grantImmediately
              )
            }
          },
          enabled = selectedMember != null && title.isNotBlank(),
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("confirm_assign_perk_button")
        ) {
          Icon(Icons.Default.CardGiftcard, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            if (grantImmediately) {
              "Grant Perk to ${selectedMember?.displayName ?: "Member"}"
            } else {
              "Assign Perk to ${selectedMember?.displayName ?: "Member"}"
            }
          )
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

