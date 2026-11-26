package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.configureAppCheck
import com.example.audio.GoogleTtsSpeaker
import com.example.audio.VoiceprintStore
import com.example.auth.GoogleAuthHelper
import com.example.data.local.PreferencesRepository
import com.example.data.local.TaskDatabase
import com.example.data.local.TransformationProofStore
import com.example.data.model.GroupRole
import com.example.data.model.InstalledAppInfo
import com.example.data.model.RemoteLockMode
import com.example.data.model.ScreenTimePeriod
import com.example.data.model.TaskEntity
import com.example.data.remote.CloudSyncRepository
import com.example.data.remote.GroupRepository
import com.example.notifications.TaskNotificationHelper
import com.example.notifications.TaskReminderManager
import com.example.notifications.TaskTimerManager
import com.example.notifications.TaskTimerStatus
import com.example.screentime.GroupCloudBackgroundSyncEngine
import com.example.screentime.RealDeviceAppsScanner
import com.example.screentime.ScreenTimeEnforcementEngine
import com.example.screentime.SystemOverlayBlocker
import com.example.sensors.FocusAuraSensorEngine
import com.example.ui.components.TaskEditorSheet
import com.example.ui.components.TaskLongPressActionSheet
import com.example.ui.components.TaskSheetMode
import com.example.ui.components.VoiceCommandSheet
import com.example.ui.screens.MainTasksScreen
import com.example.ui.screens.PowerLabScreen
import com.example.ui.screens.PowerLabTab
import com.example.ui.screens.SettingsAndAboutScreen
import com.example.ui.screens.SettingsCategory
import com.example.ui.screens.SetupWizardScreen
import com.example.ui.screens.TaskRecordsLibraryScreen
import com.example.ui.theme.TasksPlusTheme
import com.example.update.AppUpdateManager
import com.example.viewmodel.TasksPlusViewModel
import com.example.widget.TasksWidgetUpdater
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  private var requestedTabState = mutableIntStateOf(-1)
  private var requestedOpenTimerState = mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    try {
      com.google.android.gms.security.ProviderInstaller.installIfNeededAsync(
        applicationContext,
        object : com.google.android.gms.security.ProviderInstaller.ProviderInstallListener {
          override fun onProviderInstalled() {}
          override fun onProviderInstallFailed(errorCode: Int, recoveryIntent: Intent?) {}
        }
      )
    } catch (_: Throwable) {
    }
    configureAppCheck(this, intent)
    enableEdgeToEdge()
    TaskReminderManager.ensureNotificationChannel(this)
    TasksWidgetUpdater.updateAllWidgets(this)
    GroupCloudBackgroundSyncEngine.ensureStarted(this)
    ScreenTimeEnforcementEngine.ensureBackgroundMonitorRunning(this)
    SystemOverlayBlocker.dismissOverlay(this)

    handleTabRequestFromIntent(intent)

    setContent {
      TasksPlusAppRoot(
        requestedTab = requestedTabState.intValue,
        onConsumedRequestedTab = { requestedTabState.intValue = -1 },
        requestedOpenTimer = requestedOpenTimerState.value,
        onConsumedRequestedOpenTimer = { requestedOpenTimerState.value = false }
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    SystemOverlayBlocker.dismissOverlay(this)
    handleTabRequestFromIntent(intent)
  }

  private fun handleTabRequestFromIntent(intent: Intent?) {
    when {
      intent?.getBooleanExtra(TaskNotificationHelper.EXTRA_OPEN_TASK_TIMER, false) == true -> {
        requestedOpenTimerState.value = true
      }
      intent?.getBooleanExtra(ScreenTimeEnforcementEngine.EXTRA_OPEN_GROUP_TAB, false) == true -> {
        requestedTabState.intValue = 2
      }
      intent?.getBooleanExtra(ScreenTimeEnforcementEngine.EXTRA_OPEN_SCREEN_TIME_TAB, false) == true -> {
        requestedTabState.intValue = 1
      }
    }
  }

  override fun onResume() {
    super.onResume()
    ScreenTimeEnforcementEngine.currentForegroundPackage = packageName
    SystemOverlayBlocker.dismissOverlay(this)
    TaskTimerManager.syncOnAppForeground(this)
  }

  override fun onPause() {
    super.onPause()
    if (ScreenTimeEnforcementEngine.currentForegroundPackage == packageName) {
      ScreenTimeEnforcementEngine.currentForegroundPackage = null
    }
  }
}

private enum class RootDestination {
  SETUP,
  SETTINGS,
  RECORDS_LIBRARY,
  POWER_LAB,
  MAIN
}

@Composable
fun TasksPlusAppRoot(
  requestedTab: Int = -1,
  onConsumedRequestedTab: () -> Unit = {},
  requestedOpenTimer: Boolean = false,
  onConsumedRequestedOpenTimer: () -> Unit = {}
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val viewModel: TasksPlusViewModel = viewModel(
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val databaseId = app.getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(databaseId)
        val db = TaskDatabase.getInstance(app)
        val prefsRepo = PreferencesRepository(app)
        val cloudRepo = CloudSyncRepository(firestore)
        val groupRepo = GroupRepository(firestore)
        val updateMgr = AppUpdateManager(app)

        TasksPlusViewModel(
          taskDao = db.taskDao(),
          screenTimeDao = db.screenTimeDao(),
          preferencesRepository = prefsRepo,
          cloudSyncRepository = cloudRepo,
          updateManager = updateMgr,
          onScheduleReminder = { task, enabled, fireImmediately ->
            TaskReminderManager.scheduleTaskReminder(
              context = app,
              task = task,
              notificationsEnabled = enabled,
              fireInitialImmediately = fireImmediately
            )
          },
          onCancelReminder = { taskId ->
            TaskReminderManager.cancelTaskReminder(app, taskId)
          },
          onWidgetsChanged = {
            TasksWidgetUpdater.updateAllWidgets(app)
          },
          onLimitClearedForPackage = { pkg ->
            ScreenTimeEnforcementEngine.resetTrackedForegroundSeconds(app, pkg)
            ScreenTimeEnforcementEngine.clearTriggeredNotificationKey(pkg)
            TaskNotificationHelper(app).cancelRemoteAppLockAlarm(pkg)
            SystemOverlayBlocker.dismissOverlay(app)
          },
          groupRepository = groupRepo,
          transformationProofStore = TransformationProofStore(app),
          voiceprintStore = VoiceprintStore(app),
          onTaskCompletedForTimer = { completedTaskId, remainingActiveCount ->
            TaskTimerManager.onTaskCompleted(app, completedTaskId, remainingActiveCount)
          }
        )
      }
    }
  )

  val timerStateFlow = remember(context) { TaskTimerManager.stateFlow(context) }
  val timerState by timerStateFlow.collectAsStateWithLifecycle()

  val activeTasks by viewModel.activeTasks.collectAsStateWithLifecycle()
  val completedTasks by viewModel.completedTasks.collectAsStateWithLifecycle()
  val screenTimeLimits by viewModel.screenTimeLimits.collectAsStateWithLifecycle()
  val lockedAppOverlay by viewModel.lockedAppOverlay.collectAsStateWithLifecycle()
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val isPreferencesLoaded by viewModel.isPreferencesLoaded.collectAsStateWithLifecycle()
  val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
  val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
  val updateState by viewModel.updateState.collectAsStateWithLifecycle()
  val syncBannerMessage by viewModel.syncBannerMessage.collectAsStateWithLifecycle()

  val activeGroup by viewModel.activeGroup.collectAsStateWithLifecycle()
  val pendingInvitesForMe by viewModel.pendingInvitesForMe.collectAsStateWithLifecycle()
  val sentGroupInvites by viewModel.sentGroupInvites.collectAsStateWithLifecycle()
  val groupMembers by viewModel.groupMembers.collectAsStateWithLifecycle()
  val groupTasks by viewModel.groupTasks.collectAsStateWithLifecycle()
  val groupPerks by viewModel.groupPerks.collectAsStateWithLifecycle()
  val groupScreenTimeLimits by viewModel.groupScreenTimeLimits.collectAsStateWithLifecycle()
  val isEvaluatingProof by viewModel.isEvaluatingProof.collectAsStateWithLifecycle()
  val groupCreditTransactions by viewModel.groupCreditTransactions.collectAsStateWithLifecycle()
  val groupRelays by viewModel.groupRelays.collectAsStateWithLifecycle()
  val taskRecords by viewModel.taskRecords.collectAsStateWithLifecycle()
  val transformationProofs by viewModel.transformationProofs.collectAsStateWithLifecycle()
  val isEvaluatingTransformation by viewModel.isEvaluatingTransformation.collectAsStateWithLifecycle()
  val lastTransformationVerdict by viewModel.lastTransformationVerdict.collectAsStateWithLifecycle()
  val sparkPlanState by viewModel.sparkPlanState.collectAsStateWithLifecycle()
  val procrastinationReport by viewModel.procrastinationReport.collectAsStateWithLifecycle()
  val voiceAssistantState by viewModel.voiceAssistantState.collectAsStateWithLifecycle()

  val googleTtsSpeaker = remember(context) { GoogleTtsSpeaker(context) }
  DisposableEffect(googleTtsSpeaker) {
    onDispose { googleTtsSpeaker.release() }
  }
  val ttsState by googleTtsSpeaker.state.collectAsStateWithLifecycle()

  val focusAuraEngine = remember(context) {
    FocusAuraSensorEngine(
      context = context,
      onFaceDownActivated = {
        val currentTimer = TaskTimerManager.getCurrentState(context)
        when (currentTimer.status) {
          TaskTimerStatus.PAUSED -> TaskTimerManager.resumeTimer(context)
          TaskTimerStatus.IDLE, TaskTimerStatus.COMPLETED, TaskTimerStatus.EXPIRED -> {
            val topTasks = viewModel.activeTasks.value.take(3)
            TaskTimerManager.startTimer(
              context = context,
              durationMillis = currentTimer.defaultDurationMinutes * 60_000L,
              selectedTasks = topTasks
            )
          }
          TaskTimerStatus.RUNNING -> Unit
        }
      },
      onEarlyPickupPaused = {
        val currentTimer = TaskTimerManager.getCurrentState(context)
        if (currentTimer.status == TaskTimerStatus.RUNNING) {
          TaskTimerManager.pauseTimer(context)
        }
      },
      onMeaningfulFocusSessionEnded = { elapsedSeconds ->
        viewModel.recordFocusAuraCompletion(elapsedSeconds)
      }
    )
  }
  DisposableEffect(focusAuraEngine) {
    onDispose { focusAuraEngine.release() }
  }
  val focusAuraState by focusAuraEngine.state.collectAsStateWithLifecycle()

  val currentUid = currentUser?.uid.orEmpty()
  val currentEmail = currentUser?.email.orEmpty()
  val currentMember = remember(groupMembers, currentUid) {
    groupMembers.firstOrNull { it.uid == currentUid }
  }
  val isChildSupervised = currentMember?.parsedRole == GroupRole.CHILD
  val canManageGroupLimits = activeGroup?.managerUid == currentUid ||
    currentMember?.parsedRole == GroupRole.MANAGER ||
    currentMember?.parsedRole == GroupRole.PARENT_ADMIN ||
    currentMember?.canManageOthers == true

  val snackbarHostState = remember { SnackbarHostState() }

  val notifPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      val currentTimer = TaskTimerManager.getCurrentState(context)
      if (currentTimer.isActiveSession) {
        TaskNotificationHelper(context).showTaskTimerNotification(currentTimer)
      }
    }
  }

  LaunchedEffect(isPreferencesLoaded, preferences.hasCompletedSetupWizard) {
    if (isPreferencesLoaded &&
      preferences.hasCompletedSetupWizard &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      !TaskReminderManager.hasNotificationPermission(context)
    ) {
      notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  LaunchedEffect(Unit) {
    val credentialManager = CredentialManager.create(context)
    GoogleAuthHelper.attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = {},
      onUnauthenticated = {},
      scope = scope
    )
  }

  LaunchedEffect(activeGroup?.id, currentUid) {
    if (activeGroup != null && currentUid.isNotBlank()) {
      val apps = withContext(Dispatchers.IO) {
        RealDeviceAppsScanner.queryRealInstalledApps(context, includeSystemApps = false)
      }
      if (apps.isNotEmpty()) {
        viewModel.syncCurrentUserDeviceApps(apps, showBanner = false)
      }
    }
  }

  LaunchedEffect(syncBannerMessage) {
    val msg = syncBannerMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearSyncBannerMessage()
    }
  }

  LaunchedEffect(isPreferencesLoaded, preferences.useDynamicColor, preferences.tonalSpotPalette) {
    if (isPreferencesLoaded) {
      withContext(Dispatchers.IO) {
        com.example.ui.components.LauncherIconThemeSwitcher.syncLauncherIcon(
          context = context,
          useDynamicColor = preferences.useDynamicColor,
          tonalSpotPalette = preferences.tonalSpotPalette
        )
      }
    }
  }

  val systemInDark = isSystemInDarkTheme()
  val isAppDarkTheme = when (preferences.themeMode) {
    com.example.data.model.ThemeMode.SYSTEM -> systemInDark
    com.example.data.model.ThemeMode.LIGHT -> false
    com.example.data.model.ThemeMode.DARK -> true
  }
  LaunchedEffect(isAppDarkTheme) {
    val activity = context as? ComponentActivity
    activity?.enableEdgeToEdge(
      statusBarStyle = if (isAppDarkTheme) {
        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
      } else {
        SystemBarStyle.light(
          android.graphics.Color.TRANSPARENT,
          android.graphics.Color.TRANSPARENT
        )
      },
      navigationBarStyle = if (isAppDarkTheme) {
        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
      } else {
        SystemBarStyle.light(
          android.graphics.Color.TRANSPARENT,
          android.graphics.Color.TRANSPARENT
        )
      }
    )
  }

  var showSettings by rememberSaveable { mutableStateOf(false) }
  var showRecordsLibrary by rememberSaveable { mutableStateOf(false) }
  var showPowerLab by rememberSaveable { mutableStateOf(false) }
  var showVoiceCommandSheet by rememberSaveable { mutableStateOf(false) }
  var powerLabInitialTab by remember { mutableStateOf(PowerLabTab.FOCUS_AURA) }
  var powerLabSparkTask by remember { mutableStateOf<TaskEntity?>(null) }
  var powerLabProofTask by remember { mutableStateOf<TaskEntity?>(null) }
  var requestedSettingsCategory by remember { mutableStateOf<SettingsCategory?>(null) }
  var localRequestedTab by remember { mutableIntStateOf(-1) }
  var editorSheetMode by remember { mutableStateOf<TaskSheetMode?>(null) }
  var taskForSheet by remember { mutableStateOf<TaskEntity?>(null) }
  var longPressedTask by remember { mutableStateOf<TaskEntity?>(null) }

  LaunchedEffect(currentUser) {
    if (currentUser == null && showRecordsLibrary) {
      showRecordsLibrary = false
    }
  }

  LaunchedEffect(requestedOpenTimer) {
    if (requestedOpenTimer) {
      requestedSettingsCategory = SettingsCategory.TASK_TIMER
      showRecordsLibrary = false
      showPowerLab = false
      showSettings = true
      onConsumedRequestedOpenTimer()
    }
  }

  TasksPlusTheme(
    themeMode = preferences.themeMode,
    useDynamicColor = preferences.useDynamicColor,
    tonalSpotPalette = preferences.tonalSpotPalette
  ) {
    if (!isPreferencesLoaded && !preferences.hasCompletedSetupWizard) {
      // Wait briefly for DataStore's initial read on first run / upgrade before deciding
      // whether to show SetupWizardScreen so returning users never see a setup flash.
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
      )
    } else {
      val rootDestination = when {
        !preferences.hasCompletedSetupWizard -> RootDestination.SETUP
        showPowerLab -> RootDestination.POWER_LAB
        showRecordsLibrary && currentUser != null -> RootDestination.RECORDS_LIBRARY
        showSettings -> RootDestination.SETTINGS
        else -> RootDestination.MAIN
      }
      Crossfade(
        targetState = rootDestination,
        label = "root_screen_crossfade"
      ) { destination ->
        when (destination) {
          RootDestination.SETUP -> {
            SetupWizardScreen(
              preferences = preferences,
              onToggleDynamicColor = viewModel::updateDynamicColor,
              onSelectPalette = viewModel::selectTonalSpotPalette,
              onFinishSetup = viewModel::completeSetupWizard
            )
          }

          RootDestination.SETTINGS -> {
          SettingsAndAboutScreen(
            preferences = preferences,
            currentUser = currentUser,
            isSyncing = isSyncing,
            updateState = updateState,
            onNavigateBack = { showSettings = false },
            onSyncNow = { viewModel.performFullBidirectionalSync(showStatus = true) },
            onShowMessage = viewModel::setSyncBannerMessage,
            onUpdateThemeMode = viewModel::updateThemeMode,
            onUpdateDynamicColor = viewModel::updateDynamicColor,
            onUpdatePalette = viewModel::selectTonalSpotPalette,
            onUpdateNotificationsEnabled = viewModel::updateNotificationsEnabled,
            onUpdateDefaultReminderMinutes = viewModel::updateDefaultReminderMinutes,
            onUpdateAutoDeleteApk = viewModel::updateAutoDeleteApk,
            onRefreshChangelog = viewModel::refreshUpdateChangelog,
            onDownloadUpdateApk = viewModel::startApkDownload,
            onInstallDownloadedApk = viewModel::installDownloadedUpdateApk,
            onDeleteDownloadedApk = viewModel::deleteDownloadedApkFile,
            onReopenSetupWizard = {
              showSettings = false
              viewModel.reopenSetupWizard()
            },
            onUpdateGroupCreditRules = viewModel::updateGroupCreditRules,
            activeGroup = activeGroup,
            groupMembers = groupMembers,
            groupTasks = groupTasks,
            groupPerks = groupPerks,
            onRedeemGroupPerk = viewModel::redeemGroupPerk,
            onOpenGroupsTab = {
              showSettings = false
              localRequestedTab = 2
            },
            timerState = timerState,
            activeTasks = activeTasks,
            requestedCategory = requestedSettingsCategory,
            onConsumedRequestedCategory = { requestedSettingsCategory = null },
            onStartTaskTimer = { durationMs, chosenTasks ->
              TaskTimerManager.startTimer(context, durationMs, chosenTasks)
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !TaskReminderManager.hasNotificationPermission(context)
              ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                viewModel.setSyncBannerMessage(
                  "Timer started (${TaskTimerManager.formatCountdown(durationMs)}). Allow notifications for the Time's Up alert."
                )
              } else {
                viewModel.setSyncBannerMessage(
                  "Task timer started (${TaskTimerManager.formatCountdown(durationMs)})"
                )
              }
            },
            onPauseTaskTimer = { TaskTimerManager.pauseTimer(context) },
            onResumeTaskTimer = { TaskTimerManager.resumeTimer(context) },
            onAddTimerFiveMinutes = { TaskTimerManager.addMinutes(context, 5) },
            onResetTaskTimer = { TaskTimerManager.resetTimer(context) },
            onCheckTimerExpired = { TaskTimerManager.onTimerExpired(context) },
            onCompleteTaskFromTimer = viewModel::completeTask,
            onUpdateTaskTimerSettings = { defaultMins, ringSound, autoComplete ->
              TaskTimerManager.updateSettings(context, defaultMins, ringSound, autoComplete)
            },
            onUpdateShowProductivityDashboard = viewModel::updateShowProductivityDashboard,
            onUpdateCategoriesEnabled = viewModel::updateCategoriesEnabled,
            onSetPremadeCategoryEnabled = viewModel::setPremadeCategoryEnabled,
            onAddCustomCategory = viewModel::addCustomCategory,
            onDeleteCustomCategory = viewModel::deleteCustomCategory,
            taskRecordsCount = taskRecords.size,
            onOpenRecordsLibrary = {
              if (currentUser != null) {
                showRecordsLibrary = true
              }
            },
            focusAuraState = focusAuraState,
            onToggleFocusAura = { focusAuraEngine.toggleArm() },
            onOpenPowerLab = {
              powerLabInitialTab = PowerLabTab.FOCUS_AURA
              powerLabSparkTask = null
              powerLabProofTask = null
              showPowerLab = true
            }
          )
        }

          RootDestination.RECORDS_LIBRARY -> {
            val signedInName = currentUser?.displayName?.takeIf { it.isNotBlank() }
              ?: currentUser?.email?.substringBefore("@")
              ?: "You"
            TaskRecordsLibraryScreen(
              isSignedIn = currentUser != null,
              userDisplayName = signedInName,
              records = if (currentUser != null) taskRecords else emptyList(),
              onNavigateBack = { showRecordsLibrary = false },
              onDeleteRecord = viewModel::deleteTaskRecord,
              onClearAllRecords = viewModel::clearAllTaskRecords
            )
          }

          RootDestination.POWER_LAB -> {
            PowerLabScreen(
              preferences = preferences,
              activeTasks = activeTasks,
              focusAuraState = focusAuraState,
              radarReport = procrastinationReport,
              sparkPlanState = sparkPlanState,
              transformationProofs = transformationProofs,
              isEvaluatingTransformation = isEvaluatingTransformation,
              lastTransformationVerdict = lastTransformationVerdict,
              initialTab = powerLabInitialTab,
              initialSparkTask = powerLabSparkTask,
              initialProofTask = powerLabProofTask,
              onToggleFocusAura = { focusAuraEngine.toggleArm() },
              onToggleEarnYourScroll = viewModel::setEarnYourScrollEnabled,
              onChangeEarnBaseMinutes = viewModel::setEarnMinutesBase,
              onGenerateFiveMinuteSpark = viewModel::generateFiveMinuteSpark,
              onAddSparkTask = viewModel::createTaskFromFiveMinuteSpark,
              onClearSparkPlan = viewModel::clearSparkPlan,
              onEvaluateTransformation = viewModel::evaluatePersonalTransformationProof,
              onClearTransformationVerdict = viewModel::clearLastTransformationVerdict,
              onDeleteTransformationProof = viewModel::deleteTransformationProof,
              onAttachVoiceContract = viewModel::attachVoiceContractToTask,
              voiceAssistantState = voiceAssistantState,
              ttsState = ttsState,
              onOpenVoiceCommands = {
                viewModel.resetVoiceCommandStatus()
                showVoiceCommandSheet = true
              },
              onTestGoogleTts = {
                googleTtsSpeaker.speak(
                  "Google Text to Speech is ready. Say add task, complete task, or remove task after enrolling your Voice ID."
                )
              },
              onNavigateBack = {
                showPowerLab = false
                powerLabSparkTask = null
                powerLabProofTask = null
              }
            )
          }

          RootDestination.MAIN -> {
          MainTasksScreen(
            activeTasks = activeTasks,
            completedTasks = completedTasks,
            screenTimeLimits = screenTimeLimits,
            lockedAppOverlay = lockedAppOverlay,
            isCloudSignedIn = currentUser != null,
            snackbarHostState = snackbarHostState,
            useDynamicColor = preferences.useDynamicColor,
            themeMode = preferences.themeMode,
            tonalSpotPalette = preferences.tonalSpotPalette,
            onOpenSettings = { showSettings = true },
            onClickAddTask = {
              taskForSheet = null
              editorSheetMode = TaskSheetMode.CREATE
            },
            onLongPressTask = { task ->
              longPressedTask = task
            },
            onCompleteTask = viewModel::completeTask,
            onCopyCompletedToTodo = { completedTask ->
              taskForSheet = completedTask
              editorSheetMode = TaskSheetMode.COPY_COMPLETED
            },
            onDeleteCompletedTask = viewModel::deleteTask,
            onClearAllCompleted = viewModel::clearCompletedTasks,
            onSaveScreenTimeLimit = viewModel::saveScreenTimeLimit,
            onRecordScreenTimeUsage = viewModel::recordScreenTimeUsage,
            onTriggerScreenTimeOverlay = viewModel::triggerScreenTimeLockOverlay,
            onRemoveScreenTimeLimit = viewModel::removeScreenTimeLimit,
            onDismissLockOverlayToManage = viewModel::dismissLockOverlayToManageInTasksPlus,
            requestedTab = if (localRequestedTab in 0..2) localRequestedTab else requestedTab,
            onConsumedRequestedTab = {
              localRequestedTab = -1
              onConsumedRequestedTab()
            },
            currentUserUid = currentUid,
            currentUserEmail = currentEmail,
            currentUserDisplayName = currentUser?.displayName.orEmpty(),
            onCloudSignInSuccess = { viewModel.performFullBidirectionalSync(showStatus = true) },
            onShowSyncMessage = viewModel::setSyncBannerMessage,
            activeGroup = activeGroup,
            pendingInvitesForMe = pendingInvitesForMe,
            sentGroupInvites = sentGroupInvites,
            groupMembers = groupMembers,
            groupTasks = groupTasks,
            groupScreenTimeLimits = groupScreenTimeLimits,
            isEvaluatingProof = isEvaluatingProof,
            isChildSupervised = isChildSupervised,
            canManageGroupLimits = canManageGroupLimits,
            onCreateGroup = viewModel::createGroup,
            onLeaveOrDissolveGroup = viewModel::leaveOrDissolveGroup,
            onSendInviteRequest = viewModel::sendGroupInvite,
            onAcceptInvite = viewModel::acceptGroupInvite,
            onDeclineInvite = viewModel::declineGroupInvite,
            onRefreshGroupRequests = viewModel::refreshPendingGroupRequests,
            onSyncMyDeviceApps = { apps: List<InstalledAppInfo> ->
              viewModel.syncCurrentUserDeviceApps(apps, showBanner = true)
            },
            onUpdateMemberRoleOrLock = viewModel::updateMemberRoleOrLock,
            onCreateGroupTask = { title, sub, uid, name, prio, dueHrs, bell, reqProof, useGemini, geminiPrompt, pkg, pkgName, rewardMins, lockMode, lockAtMs, playBeep, urgencyLvl ->
              viewModel.createGroupTask(
                title = title,
                subtext = sub,
                assignedToUid = uid,
                assignedToName = name,
                priority = prio,
                dueHoursFromNow = dueHrs,
                ringAlarmBell = bell,
                requiresPhotoProof = reqProof,
                useGeminiAutoApproval = useGemini,
                geminiVerificationPrompt = geminiPrompt,
                gatedAppPackage = pkg,
                gatedAppName = pkgName,
                unlockRewardMinutes = rewardMins,
                lockMode = lockMode,
                lockAtMillis = lockAtMs,
                playBeepOnLock = playBeep,
                urgencyLevel = urgencyLvl
              )
            },
            onCompleteOrSubmitTaskProof = viewModel::completeOrSubmitGroupTaskProof,
            onRecheckProofWithGemini = viewModel::recheckTaskProofWithGemini,
            onReviewTaskProof = viewModel::reviewGroupTaskProof,
            onDeleteGroupTask = viewModel::deleteGroupTask,
            onSaveMemberScreenTimeLimit = { uid, name, pkg, appName, mins, period, reqId, reqTitle, lockMode, lockAtMs, instr, playBeep ->
              viewModel.saveMemberScreenTimeLimit(
                targetMemberUid = uid,
                targetMemberName = name,
                packageName = pkg,
                appName = appName,
                limitMinutes = mins,
                period = period,
                requiredGroupTaskId = reqId,
                requiredGroupTaskTitle = reqTitle,
                lockMode = lockMode,
                lockAtMillis = lockAtMs,
                taskInstructions = instr,
                playBeepOnLock = playBeep
              )
            },
            onLockMemberAppWithTask = viewModel::lockMemberAppWithOptionalNewTask,
            onRemoveMemberScreenTimeLimit = viewModel::removeMemberScreenTimeLimit,
            creditRules = activeGroup?.creditRules ?: preferences.creditRules,
            groupPerks = groupPerks,
            groupCreditTransactions = groupCreditTransactions,
            onUpdateGroupCreditRules = viewModel::updateGroupCreditRules,
            onAdjustGroupMemberCredits = viewModel::adjustGroupMemberCredits,
            onAssignGroupPerk = viewModel::assignGroupPerk,
            onRedeemGroupPerk = viewModel::redeemGroupPerk,
            onUpdateGroupPerkStatus = viewModel::updateGroupPerkStatus,
            onDeleteGroupPerk = viewModel::deleteGroupPerk,
            timerState = timerState,
            onOpenTaskTimer = {
              requestedSettingsCategory = SettingsCategory.TASK_TIMER
              showSettings = true
            },
            onPauseTaskTimer = { TaskTimerManager.pauseTimer(context) },
            onResumeTaskTimer = { TaskTimerManager.resumeTimer(context) },
            onAddTimerFiveMinutes = { TaskTimerManager.addMinutes(context, 5) },
            onCheckTimerExpired = { TaskTimerManager.onTimerExpired(context) },
            showProductivityDashboard = preferences.showProductivityDashboard,
            onToggleProductivityDashboard = viewModel::updateShowProductivityDashboard,
            categoriesEnabled = preferences.categoriesEnabled,
            activeCategories = preferences.activeCategories,
            customCategories = preferences.customCategories,
            onOpenCategorySettings = {
              requestedSettingsCategory = SettingsCategory.CATEGORIES
              showSettings = true
            },
            onOpenRecordsLibrary = {
              if (currentUser != null) {
                showRecordsLibrary = true
              }
            },
            preferences = preferences,
            onToggleEarnYourScroll = viewModel::setEarnYourScrollEnabled,
            onChangeEarnBaseMinutes = viewModel::setEarnMinutesBase,
            onSpendEarnedScreenTime = viewModel::spendEarnedScreenTimeForApp,
            groupRelays = groupRelays,
            onCreateGroupRelay = viewModel::createGroupRelay,
            onAdvanceGroupRelay = viewModel::completeCurrentRelayStep,
            onDeleteGroupRelay = viewModel::deleteGroupRelay,
            onOpenPowerLab = {
              powerLabInitialTab = PowerLabTab.FOCUS_AURA
              powerLabSparkTask = null
              powerLabProofTask = null
              showPowerLab = true
            },
            isVoiceIdEnrolled = voiceAssistantState.enrolledProfile != null,
            onOpenVoiceCommands = {
              viewModel.resetVoiceCommandStatus()
              showVoiceCommandSheet = true
            }
          )
          }
        }
      }
    }

    val activeSheetMode = editorSheetMode
    if (activeSheetMode != null) {
      TaskEditorSheet(
        mode = activeSheetMode,
        initialTask = taskForSheet,
        defaultReminderMinutes = preferences.defaultReminderMinutes,
        categoriesEnabled = preferences.categoriesEnabled,
        availableCategories = preferences.activeCategories,
        radarReport = procrastinationReport,
        onDismiss = {
          editorSheetMode = null
          taskForSheet = null
        },
        onSave = { existingId, title, subtext, startMs, maxMs, reminderMins, priority, ringAlarmBell, recType, recInt, days, urgencyLvl, categoryId, voiceContractPath ->
          viewModel.saveTask(
            existingTaskId = existingId,
            title = title,
            subtext = subtext,
            scheduledAtMillis = startMs,
            maxCompletionMillis = maxMs,
            reminderIntervalMinutes = reminderMins,
            priority = priority,
            ringAlarmBell = ringAlarmBell,
            recurrenceType = recType,
            recurrenceInterval = recInt,
            selectedDaysOfWeek = days,
            urgencyLevel = urgencyLvl,
            categoryId = categoryId,
            voiceContractPath = voiceContractPath
          )
        }
      )
    }

    val selectedLongPressTask = longPressedTask
    if (selectedLongPressTask != null) {
      TaskLongPressActionSheet(
        task = selectedLongPressTask,
        onDismiss = { longPressedTask = null },
        onEdit = {
          taskForSheet = selectedLongPressTask
          editorSheetMode = TaskSheetMode.EDIT
        },
        onComplete = {
          viewModel.completeTask(selectedLongPressTask)
        },
        onTriggerAlertNow = {
          TaskReminderManager.showPriorityTaskNotification(
            context = context,
            task = selectedLongPressTask,
            triggerAlarmBellIfEnabled = true
          )
          viewModel.setSyncBannerMessage("Priority alert sent for ${selectedLongPressTask.title}")
        },
        onStopAlarmBell = {
          TaskReminderManager.stopAlarmBell(context)
          viewModel.setSyncBannerMessage("Alarm bell stopped")
        },
        onDelete = {
          viewModel.deleteTask(selectedLongPressTask)
        },
        onOpenFiveMinSpark = {
          powerLabInitialTab = PowerLabTab.RADAR_SPARK
          powerLabSparkTask = selectedLongPressTask
          powerLabProofTask = null
          viewModel.clearSparkPlan()
          showPowerLab = true
        },
        onOpenTransformationProof = {
          powerLabInitialTab = PowerLabTab.BEFORE_AFTER
          powerLabSparkTask = null
          powerLabProofTask = selectedLongPressTask
          viewModel.clearLastTransformationVerdict()
          showPowerLab = true
        }
      )
    }

    if (showVoiceCommandSheet) {
      VoiceCommandSheet(
        voiceState = voiceAssistantState,
        ttsState = ttsState,
        onRecordEnrollmentStep = { stepIndex, capture ->
          viewModel.recordVoiceEnrollmentStep(
            stepIndex = stepIndex,
            capture = capture,
            onSpeak = { googleTtsSpeaker.speak(it) }
          )
        },
        onBeginReEnrollment = viewModel::beginVoiceReEnrollment,
        onCancelReEnrollment = viewModel::cancelVoiceReEnrollment,
        onDeleteVoiceprint = {
          viewModel.deleteVoiceprintProfile(onSpeak = { googleTtsSpeaker.speak(it) })
        },
        onChangeStrictness = viewModel::setVoiceLockStrictness,
        onProcessVoiceCommand = { capture, verifyOnly ->
          viewModel.processVoiceCommandRecording(
            capture = capture,
            verifyOnlyMode = verifyOnly,
            onSpeak = { googleTtsSpeaker.speak(it) }
          )
        },
        onSpeakText = { googleTtsSpeaker.speak(it) },
        onToggleTtsMute = { googleTtsSpeaker.setMuted(it) },
        onDismiss = { showVoiceCommandSheet = false }
      )
    }
  }
}
