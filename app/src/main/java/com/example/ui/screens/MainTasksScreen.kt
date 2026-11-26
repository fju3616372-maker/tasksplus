package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import com.example.ui.components.CompactCategoryFilterBar
import com.example.ui.components.TaskCategoryLabelBadge
import com.example.ui.components.TaskCompletionCheckButton
import kotlinx.coroutines.delay
import com.example.R
import com.example.data.model.CloudCreditTransaction
import com.example.data.model.CloudGroup
import com.example.data.model.CloudGroupInvite
import com.example.data.model.CloudGroupMember
import com.example.data.model.CloudGroupPerk
import com.example.data.model.CloudGroupRelay
import com.example.data.model.CloudGroupScreenTimeLimit
import com.example.data.model.CloudGroupTask
import com.example.data.model.CloudRelayStep
import com.example.data.model.CreditCalculator
import com.example.data.model.CreditRules
import com.example.data.model.GroupRole
import com.example.data.model.GroupType
import com.example.data.model.InstalledAppInfo
import com.example.data.model.PerkStatus
import com.example.data.model.RecurrenceType
import com.example.data.model.RemoteLockMode
import com.example.data.model.ScreenTimeLimitEntity
import com.example.data.model.ScreenTimePeriod
import com.example.data.model.TaskCategory
import com.example.data.model.TaskCategoryCatalog
import com.example.data.model.TaskEntity
import com.example.data.model.TaskPriority
import com.example.data.model.ThemeMode
import com.example.data.model.TonalSpotPalette
import com.example.data.model.UserPreferencesState
import com.example.notifications.TaskReminderManager
import com.example.notifications.TaskTimerState
import com.example.ui.components.ActiveTaskTimerBanner
import com.example.ui.components.CreditSheetTab
import com.example.ui.components.CreditSystemSheet
import com.example.ui.components.ProductivityDashboardWidget
import com.example.ui.components.ProfileAvatarBadge
import com.example.ui.components.TaskPlusExpressiveLogo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTasksScreen(
  activeTasks: List<TaskEntity>,
  completedTasks: List<TaskEntity>,
  screenTimeLimits: List<ScreenTimeLimitEntity>,
  lockedAppOverlay: ScreenTimeLimitEntity?,
  isCloudSignedIn: Boolean,
  snackbarHostState: SnackbarHostState,
  onOpenSettings: () -> Unit,
  onClickAddTask: () -> Unit,
  onLongPressTask: (TaskEntity) -> Unit,
  onCompleteTask: (TaskEntity) -> Unit,
  onCopyCompletedToTodo: (TaskEntity) -> Unit,
  onDeleteCompletedTask: (TaskEntity) -> Unit,
  onClearAllCompleted: () -> Unit,
  onSaveScreenTimeLimit: (String, String, Int, ScreenTimePeriod) -> Unit,
  onRecordScreenTimeUsage: (String, Int) -> Unit,
  onTriggerScreenTimeOverlay: (ScreenTimeLimitEntity) -> Unit,
  onRemoveScreenTimeLimit: (String) -> Unit,
  onDismissLockOverlayToManage: () -> Unit,
  useDynamicColor: Boolean = true,
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  tonalSpotPalette: TonalSpotPalette = TonalSpotPalette.INDIGO,
  requestedTab: Int = -1,
  onConsumedRequestedTab: () -> Unit = {},
  currentUserUid: String = "",
  currentUserEmail: String = "",
  currentUserDisplayName: String = "",
  activeGroup: CloudGroup? = null,
  pendingInvitesForMe: List<CloudGroupInvite> = emptyList(),
  sentGroupInvites: List<CloudGroupInvite> = emptyList(),
  groupMembers: List<CloudGroupMember> = emptyList(),
  groupTasks: List<CloudGroupTask> = emptyList(),
  groupScreenTimeLimits: List<CloudGroupScreenTimeLimit> = emptyList(),
  isEvaluatingProof: Boolean = false,
  isChildSupervised: Boolean = false,
  canManageGroupLimits: Boolean = false,
  onCreateGroup: (String, GroupType) -> Unit = { _, _ -> },
  onLeaveOrDissolveGroup: () -> Unit = {},
  onSendInviteRequest: (String, GroupRole) -> Unit = { _, _ -> },
  onAcceptInvite: (CloudGroupInvite) -> Unit = {},
  onDeclineInvite: (CloudGroupInvite) -> Unit = {},
  onRefreshGroupRequests: () -> Unit = {},
  onSyncMyDeviceApps: (List<InstalledAppInfo>) -> Unit = {},
  onUpdateMemberRoleOrLock: (String, GroupRole, Boolean, Boolean) -> Unit = { _, _, _, _ -> },
  onCreateGroupTask: (
    String, String, String, String, TaskPriority, Int, Boolean, Boolean, Boolean, String, String, String, Int, RemoteLockMode, Long, Boolean, Int
  ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
  onCompleteOrSubmitTaskProof: (CloudGroupTask, Bitmap?, String, Bitmap?) -> Unit = { _, _, _, _ -> },
  onRecheckProofWithGemini: (CloudGroupTask) -> Unit = {},
  onReviewTaskProof: (CloudGroupTask, Boolean, String) -> Unit = { _, _, _ -> },
  onDeleteGroupTask: (String) -> Unit = {},
  onSaveMemberScreenTimeLimit: (
    String, String, String, String, Int, ScreenTimePeriod, String, String, RemoteLockMode, Long, String, Boolean
  ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
  onLockMemberAppWithTask: (
    String, String, String, String, Int, ScreenTimePeriod, RemoteLockMode, Long, String, Boolean, String, String, String, Boolean, Boolean, String, Int
  ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
  onRemoveMemberScreenTimeLimit: (String) -> Unit = {},
  creditRules: CreditRules = CreditRules(enabled = false),
  groupPerks: List<CloudGroupPerk> = emptyList(),
  groupCreditTransactions: List<CloudCreditTransaction> = emptyList(),
  onUpdateGroupCreditRules: (CreditRules) -> Unit = {},
  onAdjustGroupMemberCredits: (CloudGroupMember, Long, String) -> Unit = { _, _, _ -> },
  onAssignGroupPerk: (CloudGroupMember, String, String, Long, Boolean) -> Unit = { _, _, _, _, _ -> },
  onRedeemGroupPerk: (CloudGroupPerk) -> Unit = {},
  onUpdateGroupPerkStatus: (CloudGroupPerk, PerkStatus) -> Unit = { _, _ -> },
  onDeleteGroupPerk: (String) -> Unit = {},
  onCloudSignInSuccess: () -> Unit = {},
  onShowSyncMessage: (String) -> Unit = {},
  timerState: TaskTimerState = TaskTimerState(),
  onOpenTaskTimer: () -> Unit = {},
  onPauseTaskTimer: () -> Unit = {},
  onResumeTaskTimer: () -> Unit = {},
  onAddTimerFiveMinutes: () -> Unit = {},
  onCheckTimerExpired: () -> Unit = {},
  showProductivityDashboard: Boolean = false,
  onToggleProductivityDashboard: (Boolean) -> Unit = {},
  categoriesEnabled: Boolean = true,
  activeCategories: List<TaskCategory> = TaskCategoryCatalog.premadeCategories,
  customCategories: List<TaskCategory> = emptyList(),
  onOpenCategorySettings: () -> Unit = {},
  onOpenRecordsLibrary: () -> Unit = {},
  preferences: UserPreferencesState = UserPreferencesState(),
  onToggleEarnYourScroll: (Boolean) -> Unit = {},
  onChangeEarnBaseMinutes: (Int) -> Unit = {},
  onSpendEarnedScreenTime: (ScreenTimeLimitEntity, Int) -> Unit = { _, _ -> },
  groupRelays: List<CloudGroupRelay> = emptyList(),
  onCreateGroupRelay: (String, String, List<CloudRelayStep>) -> Unit = { _, _, _ -> },
  onAdvanceGroupRelay: (CloudGroupRelay) -> Unit = {},
  onDeleteGroupRelay: (String) -> Unit = {},
  onOpenPowerLab: () -> Unit = {},
  isVoiceIdEnrolled: Boolean = false,
  onOpenVoiceCommands: () -> Unit = {}
) {
  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  var requestedGroupSubTab by remember { mutableIntStateOf(-1) }
  var showCreditSheet by rememberSaveable { mutableStateOf(false) }
  var creditSheetInitialTab by rememberSaveable { mutableStateOf(CreditSheetTab.POINTS) }
  val currentMember = remember(groupMembers, currentUserUid) {
    groupMembers.firstOrNull { it.uid == currentUserUid }
  }
  val hasActiveGroup = isCloudSignedIn && activeGroup != null
  val isMainManager = hasActiveGroup && activeGroup?.managerUid == currentUserUid
  val userFallbackInitial = remember(currentUserDisplayName, currentMember?.displayName, currentUserEmail) {
    (currentUserDisplayName.firstOrNull()
      ?: currentMember?.displayName?.firstOrNull()
      ?: currentUserEmail.firstOrNull()
      ?: 'U').uppercase()
  }

  androidx.compose.runtime.LaunchedEffect(requestedTab, isCloudSignedIn) {
    if (requestedTab in 0..2) {
      selectedTab = if (requestedTab == 2 && !isCloudSignedIn) 1 else requestedTab
      onConsumedRequestedTab()
    }
  }
  androidx.compose.runtime.LaunchedEffect(isCloudSignedIn) {
    if (!isCloudSignedIn && selectedTab == 2) {
      selectedTab = 0
    }
  }
  val activeLimitsCount = remember(screenTimeLimits) {
    screenTimeLimits.count { it.isEnabled && it.limitMinutes > 0 }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        TopAppBar(
          title = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              TaskPlusExpressiveLogo(
                useDynamicColor = useDynamicColor,
                themeMode = themeMode,
                tonalSpotPalette = tonalSpotPalette,
                modifier = Modifier
                  .size(36.dp)
                  .testTag("app_top_bar_logo")
              )
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
              )
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
              ) {
                val dueNowCount = remember(activeTasks) {
                  activeTasks.count { !it.isScheduledForFutureDay() }
                }
                Text(
                  text = "$dueNowCount active",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
              }
              if (isCloudSignedIn) {
                Icon(
                  imageVector = Icons.Default.CloudDone,
                  contentDescription = "Cloud Synced",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          },
          actions = {
            IconButton(
              onClick = onOpenPowerLab,
              modifier = Modifier
                .padding(end = 2.dp)
                .testTag("topbar_power_lab_button")
            ) {
              Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = stringResource(R.string.cd_power_lab_button),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }
            }
            if (isCloudSignedIn) {
              IconButton(
                onClick = onOpenRecordsLibrary,
                modifier = Modifier
                  .padding(end = 2.dp)
                  .testTag("topbar_records_library_button")
              ) {
                Surface(
                  color = MaterialTheme.colorScheme.secondaryContainer,
                  shape = CircleShape,
                  modifier = Modifier.size(40.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Default.LibraryBooks,
                      contentDescription = stringResource(R.string.cd_records_library_button),
                      tint = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                }
              }
            }
            IconButton(
              onClick = {
                creditSheetInitialTab = CreditSheetTab.POINTS
                showCreditSheet = true
              },
              modifier = Modifier
                .padding(end = 2.dp)
                .testTag("topbar_profile_button")
            ) {
              ProfileAvatarBadge(
                fallbackInitial = userFallbackInitial,
                size = 40.dp,
                modifier = Modifier.size(40.dp)
              )
            }
            IconButton(
              onClick = onOpenSettings,
              modifier = Modifier
                .padding(end = 4.dp)
                .testTag("settings_button")
            ) {
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.cd_settings_button),
                    tint = MaterialTheme.colorScheme.onSurface
                  )
                }
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          )
        )
      },
      bottomBar = {
        // Bottom dock with + New Task FAB and NavigationBar
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              ExtendedFloatingActionButton(
                onClick = onClickAddTask,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                icon = {
                  Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_add_task_fab)
                  )
                },
                text = {
                  Text(
                    text = "New Task",
                    style = MaterialTheme.typography.titleMedium
                  )
                },
                modifier = Modifier.testTag("add_task_fab")
              )

              FloatingActionButton(
                onClick = onOpenVoiceCommands,
                containerColor = if (isVoiceIdEnrolled) {
                  MaterialTheme.colorScheme.secondaryContainer
                } else {
                  MaterialTheme.colorScheme.tertiaryContainer
                },
                contentColor = if (isVoiceIdEnrolled) {
                  MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                  MaterialTheme.colorScheme.onTertiaryContainer
                },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("voice_command_fab")
              ) {
                Icon(
                  imageVector = Icons.Default.Mic,
                  contentDescription = stringResource(R.string.cd_voice_command_fab)
                )
              }
            }
          }

          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 4.dp,
            modifier = Modifier.navigationBarsPadding()
          ) {
            NavigationBarItem(
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 },
              icon = {
                BadgedBox(
                  badge = {
                    val dueNowCount = remember(activeTasks) {
                      activeTasks.count { !it.isScheduledForFutureDay() }
                    }
                    if (dueNowCount > 0) {
                      Badge { Text("$dueNowCount") }
                    }
                  }
                ) {
                  Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = stringResource(R.string.tab_todo)
                  )
                }
              },
              label = {
                Text(
                  text = stringResource(R.string.tab_todo),
                  style = MaterialTheme.typography.labelMedium,
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Ellipsis
                )
              },
              modifier = Modifier.testTag("tab_todo")
            )
            NavigationBarItem(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              icon = {
                BadgedBox(
                  badge = {
                    if (activeLimitsCount > 0) {
                      Badge { Text("$activeLimitsCount") }
                    }
                  }
                ) {
                  Icon(
                    imageVector = Icons.Default.HourglassTop,
                    contentDescription = stringResource(R.string.tab_screen_time)
                  )
                }
              },
              label = {
                Text(
                  text = stringResource(R.string.tab_screen_time),
                  style = MaterialTheme.typography.labelMedium,
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Ellipsis
                )
              },
              modifier = Modifier.testTag("tab_screen_time")
            )
            if (isCloudSignedIn) {
              val groupBadgeCount = pendingInvitesForMe.size + groupTasks.count { !it.isCompleted }
              NavigationBarItem(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                icon = {
                  BadgedBox(
                    badge = {
                      if (groupBadgeCount > 0) {
                        Badge { Text("$groupBadgeCount") }
                      }
                    }
                  ) {
                    Icon(
                      imageVector = Icons.Default.Groups,
                      contentDescription = stringResource(R.string.tab_group)
                    )
                  }
                },
                label = {
                  Text(
                    text = stringResource(R.string.tab_group),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                  )
                },
                modifier = Modifier.testTag("tab_group")
              )
            }
          }
        }
      }
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentAlignment = Alignment.TopCenter
      ) {
        AnimatedContent(
          targetState = selectedTab,
          transitionSpec = {
            if (targetState > initialState) {
              (slideInHorizontally(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
              ) { it / 3 } + fadeIn()).togetherWith(
                slideOutHorizontally { -it / 3 } + fadeOut()
              )
            } else {
              (slideInHorizontally(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
              ) { -it / 3 } + fadeIn()).togetherWith(
                slideOutHorizontally { it / 3 } + fadeOut()
              )
            }
          },
          label = "tasks_tab_transition",
          modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 600.dp)
        ) { tabIndex ->
          when (tabIndex) {
            0 -> {
              Column(modifier = Modifier.fillMaxSize()) {
                if (timerState.isActiveSession) {
                  ActiveTaskTimerBanner(
                    timerState = timerState,
                    onOpenTimerInSettings = onOpenTaskTimer,
                    onPauseTimer = onPauseTaskTimer,
                    onResumeTimer = onResumeTaskTimer,
                    onAddFiveMinutes = onAddTimerFiveMinutes,
                    onCheckExpired = onCheckTimerExpired,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                  )
                }
                ToDoTasksList(
                  tasks = activeTasks,
                  completedTasks = completedTasks,
                  creditRules = creditRules,
                  showCredits = false,
                  showProductivityDashboard = showProductivityDashboard,
                  onToggleProductivityDashboard = onToggleProductivityDashboard,
                  categoriesEnabled = categoriesEnabled,
                  activeCategories = activeCategories,
                  customCategories = customCategories,
                  onOpenCategorySettings = onOpenCategorySettings,
                  onLongPressTask = onLongPressTask,
                  onCompleteTask = onCompleteTask,
                  modifier = Modifier.weight(1f)
                )
              }
            }

            1 -> {
              ScreenTimeTabScreen(
                limits = screenTimeLimits,
                isChildSupervised = isChildSupervised,
                canManageGroupLimits = canManageGroupLimits,
                preferences = preferences,
                onToggleEarnYourScroll = onToggleEarnYourScroll,
                onChangeEarnBaseMinutes = onChangeEarnBaseMinutes,
                onSpendEarnedScreenTime = onSpendEarnedScreenTime,
                onSaveLimit = onSaveScreenTimeLimit,
                onRecordUsage = onRecordScreenTimeUsage,
                onTriggerOverlay = onTriggerScreenTimeOverlay,
                onRemoveLimit = onRemoveScreenTimeLimit
              )
            }

            else -> {
              GroupTabScreen(
                currentUserUid = currentUserUid,
                currentUserEmail = currentUserEmail,
                activeGroup = activeGroup,
                pendingInvitesForMe = pendingInvitesForMe,
                sentGroupInvites = sentGroupInvites,
                groupMembers = groupMembers,
                groupTasks = groupTasks,
                groupScreenTimeLimits = groupScreenTimeLimits,
                isEvaluatingProof = isEvaluatingProof,
                onCreateGroup = onCreateGroup,
                onLeaveOrDissolveGroup = onLeaveOrDissolveGroup,
                onSendInviteRequest = onSendInviteRequest,
                onAcceptInvite = onAcceptInvite,
                onDeclineInvite = onDeclineInvite,
                onRefreshRequests = onRefreshGroupRequests,
                onSyncMyDeviceApps = onSyncMyDeviceApps,
                onUpdateMemberRoleOrLock = onUpdateMemberRoleOrLock,
                onCreateGroupTask = onCreateGroupTask,
                onCompleteOrSubmitTaskProof = onCompleteOrSubmitTaskProof,
                onRecheckProofWithGemini = onRecheckProofWithGemini,
                onReviewTaskProof = onReviewTaskProof,
                onDeleteGroupTask = onDeleteGroupTask,
                onSaveMemberScreenTimeLimit = onSaveMemberScreenTimeLimit,
                onLockMemberAppWithTask = onLockMemberAppWithTask,
                onRemoveMemberScreenTimeLimit = onRemoveMemberScreenTimeLimit,
                onOpenCreditSheet = {
                  creditSheetInitialTab = CreditSheetTab.POINTS
                  showCreditSheet = true
                },
                groupPerks = groupPerks,
                requestedSubTab = requestedGroupSubTab,
                onConsumedRequestedSubTab = { requestedGroupSubTab = -1 },
                onAssignGroupPerk = onAssignGroupPerk,
                onRedeemGroupPerk = onRedeemGroupPerk,
                onUpdateGroupPerkStatus = onUpdateGroupPerkStatus,
                onDeleteGroupPerk = onDeleteGroupPerk,
                groupRelays = groupRelays,
                onCreateGroupRelay = onCreateGroupRelay,
                onAdvanceGroupRelay = onAdvanceGroupRelay,
                onDeleteGroupRelay = onDeleteGroupRelay
              )
            }
          }
        }
      }
    }

    if (showCreditSheet) {
      CreditSystemSheet(
        isSignedIn = isCloudSignedIn,
        currentUserUid = currentUserUid,
        userDisplayName = currentUserDisplayName,
        userEmail = currentUserEmail,
        hasActiveGroup = hasActiveGroup,
        groupName = activeGroup?.name.orEmpty(),
        currentBalance = currentMember?.creditBalance ?: 0L,
        totalEarnedCredits = currentMember?.totalEarnedCredits ?: 0L,
        totalDeductedCredits = currentMember?.totalDeductedCredits ?: 0L,
        creditRules = activeGroup?.creditRules ?: CreditRules(enabled = false),
        canEditRules = isMainManager,
        groupMembers = if (hasActiveGroup) groupMembers else emptyList(),
        groupPerks = if (hasActiveGroup) groupPerks else emptyList(),
        transactions = if (hasActiveGroup) groupCreditTransactions else emptyList(),
        userFallbackInitial = userFallbackInitial,
        initialTab = creditSheetInitialTab,
        onSignInSuccess = onCloudSignInSuccess,
        onSignInError = onShowSyncMessage,
        onOpenAccountsSettings = onOpenSettings,
        onOpenGroupPerks = {
          if (isCloudSignedIn) {
            selectedTab = 2
            requestedGroupSubTab = 4
          }
        },
        onDismiss = { showCreditSheet = false },
        onSaveRules = { newRules ->
          if (isMainManager) {
            onUpdateGroupCreditRules(newRules)
          }
        },
        onAdjustCredits = { member, delta, reason ->
          if (isMainManager && member != null) {
            onAdjustGroupMemberCredits(member, delta, reason)
          }
        },
        onRedeemPerk = onRedeemGroupPerk,
        onUpdatePerkStatus = onUpdateGroupPerkStatus
      )
    }

    // Full-screen Screen Time Lockout Overlay when an app exceeds its limit
    AnimatedVisibility(
      visible = lockedAppOverlay != null,
      enter = fadeIn() + scaleIn(initialScale = 0.92f),
      exit = fadeOut() + scaleOut(targetScale = 0.92f)
    ) {
      val locked = lockedAppOverlay
      if (locked != null) {
        ScreenTimeLockoutOverlay(
          lockedApp = locked,
          onManageInTasksPlus = {
            selectedTab = if (locked.isGroupEnforced && isCloudSignedIn) 2 else 1
            onDismissLockOverlayToManage()
          }
        )
      }
    }
  }
}

@Composable
private fun ToDoTasksList(
  tasks: List<TaskEntity>,
  completedTasks: List<TaskEntity>,
  creditRules: CreditRules,
  showCredits: Boolean,
  showProductivityDashboard: Boolean,
  onToggleProductivityDashboard: (Boolean) -> Unit,
  categoriesEnabled: Boolean,
  activeCategories: List<TaskCategory>,
  customCategories: List<TaskCategory>,
  onOpenCategorySettings: () -> Unit,
  onLongPressTask: (TaskEntity) -> Unit,
  onCompleteTask: (TaskEntity) -> Unit,
  modifier: Modifier = Modifier
) {
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var showUpcomingRecurring by rememberSaveable { mutableStateOf(false) }
  var selectedCategoryFilter by rememberSaveable { mutableStateOf<String?>(null) }

  val effectiveCategoryFilter = remember(categoriesEnabled, activeCategories, selectedCategoryFilter) {
    if (!categoriesEnabled) {
      null
    } else {
      selectedCategoryFilter?.takeIf { id -> activeCategories.any { it.id == id } }
    }
  }

  val filteredTasks = remember(tasks, searchQuery, effectiveCategoryFilter, customCategories) {
    val q = searchQuery.trim()
    tasks.filter { task ->
      val matchesCategory = effectiveCategoryFilter == null || task.categoryId == effectiveCategoryFilter
      if (!matchesCategory) return@filter false
      if (q.isEmpty()) {
        true
      } else {
        val catName = TaskCategoryCatalog.resolveById(task.categoryId, customCategories)?.name.orEmpty()
        task.title.contains(q, ignoreCase = true) ||
          task.subtext.contains(q, ignoreCase = true) ||
          catName.contains(q, ignoreCase = true) ||
          task.parsedRecurrenceType.label.contains(q, ignoreCase = true) ||
          task.parsedPriority.label.contains(q, ignoreCase = true) ||
          com.example.data.model.UrgencyLevelMeta.fullLabel(task.resolvedUrgencyLevel).contains(q, ignoreCase = true)
      }
    }
  }

  val isSearching = searchQuery.trim().isNotEmpty()
  val currentTasks = remember(filteredTasks, isSearching) {
    if (isSearching) filteredTasks else filteredTasks.filter { !it.isScheduledForFutureDay() }
  }
  val upcomingRecurringTasks = remember(filteredTasks, isSearching) {
    if (isSearching) emptyList() else filteredTasks.filter { it.isScheduledForFutureDay() }
  }

  Column(modifier = modifier.fillMaxSize()) {
    if (categoriesEnabled && activeCategories.isNotEmpty()) {
      CompactCategoryFilterBar(
        categories = activeCategories,
        selectedCategoryId = effectiveCategoryFilter,
        onSelectCategory = { selectedCategoryFilter = it },
        onManageCategories = onOpenCategorySettings
      )
    }

    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text("Search tasks...") },
      leadingIcon = {
        Icon(
          imageVector = Icons.Default.Search,
          contentDescription = "Search tasks",
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(
            onClick = { searchQuery = "" },
            modifier = Modifier.testTag("clear_search_button")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Clear search"
            )
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(20.dp),
      colors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
      ),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .testTag("todo_search_bar")
    )

    LazyColumn(
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .fillMaxSize()
        .testTag("todo_tasks_list")
    ) {
      item(key = "productivity_dashboard_item") {
        if (showProductivityDashboard) {
          ProductivityDashboardWidget(
            activeTasks = tasks,
            completedTasks = completedTasks,
            onHideWidget = { onToggleProductivityDashboard(false) }
          )
        } else {
          FilledTonalButton(
            onClick = { onToggleProductivityDashboard(true) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("show_productivity_widget_button")
          ) {
            Icon(
              imageVector = Icons.Default.BarChart,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Show Daily Productivity Chart")
          }
        }
      }

      if (tasks.isEmpty()) {
        item(key = "empty_todo_placeholder") {
          EmptyTabPlaceholder(
            title = "All Clear",
            subtitle = "Tap + in the bottom-left to schedule a task.",
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 28.dp)
          )
        }
      } else if (filteredTasks.isEmpty()) {
        val selectedCatName = effectiveCategoryFilter?.let { id ->
          activeCategories.firstOrNull { it.id == id }?.name
        }
        item(key = "no_match_placeholder") {
          EmptyTabPlaceholder(
            title = if (selectedCatName != null && searchQuery.isBlank()) "No $selectedCatName Tasks" else "No Matches",
            subtitle = if (selectedCatName != null && searchQuery.isBlank()) {
              "No active tasks assigned to $selectedCatName yet."
            } else {
              "No tasks match \"$searchQuery\"."
            },
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 28.dp)
          )
        }
      } else {
        if (currentTasks.isNotEmpty()) {
          item(key = "todo_hint_label") {
            Text(
              text = "Hold any task to edit or delete",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
          }
          items(items = currentTasks, key = { it.id }) { task ->
            val resolvedCategory = remember(task.categoryId, customCategories, categoriesEnabled) {
              if (categoriesEnabled) TaskCategoryCatalog.resolveById(task.categoryId, customCategories) else null
            }
            ActiveTaskCard(
              task = task,
              category = resolvedCategory,
              creditRules = creditRules,
              showCredits = showCredits,
              onLongPress = { onLongPressTask(task) },
              onComplete = { onCompleteTask(task) },
              modifier = Modifier.animateItem()
            )
          }
        } else if (upcomingRecurringTasks.isNotEmpty()) {
          item(key = "caught_up_today_placeholder") {
            EmptyTabPlaceholder(
              title = "All Caught Up for Today!",
              subtitle = "Tomorrow's recurring occurrences are scheduled below.",
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
            )
          }
        }

        if (upcomingRecurringTasks.isNotEmpty()) {
          item(key = "upcoming_recurring_header") {
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { showUpcomingRecurring = !showUpcomingRecurring }
                .testTag("toggle_upcoming_recurring_section")
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 12.dp)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Autorenew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                  )
                  Column {
                    Text(
                      text = "Upcoming Recurring (${upcomingRecurringTasks.size})",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                      text = if (showUpcomingRecurring) "Scheduled for tomorrow & later" else "Tap to view next scheduled occurrences",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
                Icon(
                  imageVector = if (showUpcomingRecurring) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                  contentDescription = if (showUpcomingRecurring) "Collapse upcoming recurring tasks" else "Expand upcoming recurring tasks",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          if (showUpcomingRecurring) {
            items(items = upcomingRecurringTasks, key = { "upcoming_${it.id}" }) { task ->
              val resolvedCategory = remember(task.categoryId, customCategories, categoriesEnabled) {
                if (categoriesEnabled) TaskCategoryCatalog.resolveById(task.categoryId, customCategories) else null
              }
              ActiveTaskCard(
                task = task,
                category = resolvedCategory,
                creditRules = creditRules,
                showCredits = showCredits,
                onLongPress = { onLongPressTask(task) },
                onComplete = { onCompleteTask(task) },
                modifier = Modifier.animateItem()
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ActiveTaskCard(
  task: TaskEntity,
  category: TaskCategory?,
  creditRules: CreditRules,
  showCredits: Boolean,
  onLongPress: () -> Unit,
  onComplete: () -> Unit,
  modifier: Modifier = Modifier
) {
  val haptics = LocalHapticFeedback.current
  val dateFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
  val now = System.currentTimeMillis()
  val isOverdue = task.maxCompletionMillis < now

  var isCompleting by remember(task.id, task.updatedAtMillis) { mutableStateOf(false) }

  LaunchedEffect(isCompleting) {
    if (isCompleting) {
      delay(310L)
      onComplete()
    }
  }

  val cardContainerColor by animateColorAsState(
    targetValue = if (isCompleting) {
      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
      MaterialTheme.colorScheme.surfaceContainer
    },
    animationSpec = tween(durationMillis = 220),
    label = "task_card_completion_tint"
  )
  val cardScale by animateFloatAsState(
    targetValue = if (isCompleting) 0.985f else 1f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium
    ),
    label = "task_card_completion_scale"
  )
  val contentAlpha by animateFloatAsState(
    targetValue = if (isCompleting) 0.68f else 1f,
    animationSpec = tween(durationMillis = 220),
    label = "task_card_completion_alpha"
  )

  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(
      containerColor = cardContainerColor
    ),
    modifier = modifier
      .fillMaxWidth()
      .graphicsLayer {
        scaleX = cardScale
        scaleY = cardScale
      }
      .clip(RoundedCornerShape(22.dp))
      .combinedClickable(
        enabled = !isCompleting,
        onClick = { onLongPress() },
        onLongClick = {
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          onLongPress()
        }
      )
      .testTag("todo_task_card_${task.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      TaskCompletionCheckButton(
        isChecked = isCompleting,
        contentDescription = "Mark ${task.title} finished",
        onClick = {
          if (!isCompleting) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            isCompleting = true
          }
        },
        modifier = Modifier.testTag("complete_task_button_${task.id}")
      )

      Column(
        modifier = Modifier
          .weight(1f)
          .alpha(contentAlpha),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          text = task.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          textDecoration = if (isCompleting) TextDecoration.LineThrough else TextDecoration.None,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )

        if (task.subtext.isNotBlank()) {
          Text(
            text = task.subtext,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // Optional Color-Coded Category Label
          if (category != null) {
            TaskCategoryLabelBadge(category = category)
          }

          // 1..5 Thermometer Urgency Badge
          com.example.ui.components.ThermometerUrgencyBadge(
            urgencyLevel = task.resolvedUrgencyLevel
          )

          // Last Date / Max Completion Badge
          MetaBadge(
            icon = Icons.Default.Schedule,
            text = "Due ${dateFmt.format(Date(task.maxCompletionMillis))}",
            containerColor = if (isOverdue) {
              MaterialTheme.colorScheme.errorContainer
            } else {
              MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (isOverdue) {
              MaterialTheme.colorScheme.onErrorContainer
            } else {
              MaterialTheme.colorScheme.onSecondaryContainer
            }
          )

          // Reminder Interval Badge
          MetaBadge(
            icon = Icons.Default.NotificationsActive,
            text = if (task.reminderIntervalMinutes > 0) {
              "Every ${TaskReminderManager.formatIntervalShort(task.reminderIntervalMinutes)}"
            } else {
              "Due-Date Alert"
            },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
          )

          // Optional Alarm Bell Badge
          if (task.ringAlarmBell) {
            MetaBadge(
              icon = Icons.Default.NotificationsActive,
              text = "Alarm Bell",
              containerColor = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
          }

          // Recurrence Badge
          if (task.parsedRecurrenceType != RecurrenceType.NONE) {
            MetaBadge(
              icon = Icons.Default.Autorenew,
              text = task.parsedRecurrenceType.label,
              containerColor = MaterialTheme.colorScheme.tertiaryContainer,
              contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
          }

          // Optional Credit Rate Badge (logged-in users only)
          if (showCredits && creditRules.enabled) {
            MetaBadge(
              icon = Icons.Default.Stars,
              text = "+${CreditCalculator.formatRate(creditRules.earlyPointsPerSecond)}/-${CreditCalculator.formatRate(creditRules.latePenaltyPerSecond)} pt/s",
              containerColor = MaterialTheme.colorScheme.tertiaryContainer,
              contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
          }
        }
      }

      IconButton(
        onClick = onLongPress,
        modifier = Modifier.size(48.dp)
      ) {
        Icon(
          imageVector = Icons.Default.MoreVert,
          contentDescription = "Edit or delete ${task.title}",
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun MetaBadge(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String,
  containerColor: androidx.compose.ui.graphics.Color,
  contentColor: androidx.compose.ui.graphics.Color
) {
  Surface(
    color = containerColor,
    shape = RoundedCornerShape(10.dp)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(13.dp)
      )
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor
      )
    }
  }
}

@Composable
private fun EmptyTabPlaceholder(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier
    .fillMaxSize()
    .padding(32.dp)
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
        modifier = Modifier.size(64.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.TaskAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(32.dp)
          )
        }
      }
      Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
