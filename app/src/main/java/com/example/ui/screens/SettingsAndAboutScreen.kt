package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.data.model.CloudGroup
import com.example.data.model.CloudGroupMember
import com.example.data.model.CloudGroupPerk
import com.example.data.model.CloudGroupTask
import com.example.data.model.CreditCalculator
import com.example.data.model.CreditRules
import com.example.data.model.PerkStatus
import com.example.data.model.TaskEntity
import com.example.data.model.ThemeMode
import com.example.data.model.TonalSpotPalette
import com.example.data.model.UserPreferencesState
import com.example.notifications.TaskReminderManager
import com.example.notifications.TaskTimerManager
import com.example.notifications.TaskTimerState
import com.example.notifications.TaskTimerStatus
import com.example.sensors.FocusAuraSensorState
import com.example.ui.components.GroupLeaderboardSection
import com.example.ui.components.ProfileAvatarBadge
import com.example.ui.components.TaskCategoriesSettingsSection
import com.example.ui.components.TaskTimerSection
import com.example.ui.components.groupKindDisplayLabel
import com.example.ui.components.rankGroupMembers
import com.example.update.AppUpdateManager
import com.example.update.UpdateUiState
import com.google.firebase.auth.FirebaseUser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SettingsCategory(
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val tag: String
) {
  ACCOUNTS(
    title = "Accounts",
    subtitle = "Google Sign-In & cross-device cloud backup",
    icon = Icons.Default.AccountCircle,
    tag = "settings_nav_accounts"
  ),
  THEMES(
    title = "Themes & Widgets",
    subtitle = "Dark mode, wallpaper colors, palettes & optional dashboard widget",
    icon = Icons.Default.Palette,
    tag = "settings_nav_themes"
  ),
  PRIORITY_NOTIFICATIONS(
    title = "Priority Notifications",
    subtitle = "Intervals, permissions, overlay & battery guard",
    icon = Icons.Default.NotificationsActive,
    tag = "settings_nav_notifications"
  ),
  TASK_TIMER(
    title = "Task Timer",
    subtitle = "Complete tasks before the countdown ends",
    icon = Icons.Default.Timer,
    tag = "settings_nav_timer"
  ),
  POWER_LAB(
    title = "AI, Sensors & Focus Lab",
    subtitle = "Focus Aura, Radar, Gemini Spark, Proof & Voice",
    icon = Icons.Default.AutoAwesome,
    tag = "settings_nav_power_lab"
  ),
  CATEGORIES(
    title = "Task Categories",
    subtitle = "Top filter bar, premade & custom color labels",
    icon = Icons.Default.Label,
    tag = "settings_nav_categories"
  ),
  CREDIT_SYSTEM(
    title = "Group Points & Perks",
    subtitle = "Optional group points (off by default) & manager perks",
    icon = Icons.Default.Stars,
    tag = "settings_nav_credits"
  ),
  GROUP_LEADERBOARD(
    title = "Group Leaderboard",
    subtitle = "Family & Workplace rankings by group points",
    icon = Icons.Default.EmojiEvents,
    tag = "settings_nav_leaderboard"
  ),
  TASK_RECORDS(
    title = "Task Records Library",
    subtitle = "Private history of entered, done, given tasks & points",
    icon = Icons.Default.LibraryBooks,
    tag = "settings_nav_task_records"
  ),
  UPDATES(
    title = "Updates",
    subtitle = "In-app APK updater, changelog & GitHub releases",
    icon = Icons.Default.SystemUpdate,
    tag = "settings_nav_updates"
  ),
  PROJECT_DIARY(
    title = "Project Diary Booklet",
    subtitle = "Aesthetic 4-page journal: uses, features, timeline & PDF",
    icon = Icons.Default.AutoStories,
    tag = "settings_nav_project_diary"
  ),
  ABOUT(
    title = "About",
    subtitle = "Crafted with ❤️ by frosty-sys & more projects",
    icon = Icons.Default.Favorite,
    tag = "settings_nav_about"
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsAndAboutScreen(
  preferences: UserPreferencesState,
  currentUser: FirebaseUser?,
  isSyncing: Boolean,
  updateState: UpdateUiState,
  onNavigateBack: () -> Unit,
  onSyncNow: () -> Unit,
  onShowMessage: (String) -> Unit,
  onUpdateThemeMode: (ThemeMode) -> Unit,
  onUpdateDynamicColor: (Boolean) -> Unit,
  onUpdatePalette: (TonalSpotPalette) -> Unit,
  onUpdateNotificationsEnabled: (Boolean) -> Unit,
  onUpdateDefaultReminderMinutes: (Int) -> Unit,
  onUpdateAutoDeleteApk: (Boolean) -> Unit,
  onRefreshChangelog: () -> Unit,
  onDownloadUpdateApk: () -> Unit,
  onInstallDownloadedApk: () -> Unit,
  onDeleteDownloadedApk: () -> Unit,
  onReopenSetupWizard: () -> Unit,
  onUpdateGroupCreditRules: (CreditRules) -> Unit = {},
  activeGroup: CloudGroup? = null,
  groupMembers: List<CloudGroupMember> = emptyList(),
  groupTasks: List<CloudGroupTask> = emptyList(),
  groupPerks: List<CloudGroupPerk> = emptyList(),
  onRedeemGroupPerk: (CloudGroupPerk) -> Unit = {},
  onOpenGroupsTab: () -> Unit = {},
  timerState: TaskTimerState = TaskTimerState(),
  activeTasks: List<TaskEntity> = emptyList(),
  requestedCategory: SettingsCategory? = null,
  onConsumedRequestedCategory: () -> Unit = {},
  onStartTaskTimer: (Long, List<TaskEntity>) -> Unit = { _, _ -> },
  onPauseTaskTimer: () -> Unit = {},
  onResumeTaskTimer: () -> Unit = {},
  onAddTimerFiveMinutes: () -> Unit = {},
  onResetTaskTimer: () -> Unit = {},
  onCheckTimerExpired: () -> Unit = {},
  onCompleteTaskFromTimer: (TaskEntity) -> Unit = {},
  onUpdateTaskTimerSettings: (Int, Boolean, Boolean) -> Unit = { _, _, _ -> },
  onUpdateShowProductivityDashboard: (Boolean) -> Unit = {},
  onUpdateCategoriesEnabled: (Boolean) -> Unit = {},
  onSetPremadeCategoryEnabled: (String, Boolean) -> Unit = { _, _ -> },
  onAddCustomCategory: (String, Long) -> Unit = { _, _ -> },
  onDeleteCustomCategory: (String) -> Unit = {},
  taskRecordsCount: Int = 0,
  onOpenRecordsLibrary: () -> Unit = {},
  focusAuraState: FocusAuraSensorState? = null,
  onToggleFocusAura: (() -> Unit)? = null,
  onOpenPowerLab: () -> Unit = {}
) {
  var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(requestedCategory?.name) }
  val selectedCategory = selectedCategoryName?.let { name ->
    SettingsCategory.entries.firstOrNull { it.name == name }
  }

  LaunchedEffect(requestedCategory) {
    if (requestedCategory != null) {
      selectedCategoryName = requestedCategory.name
      onConsumedRequestedCategory()
    }
  }

  var settingsTimerTickNow by remember { mutableStateOf(System.currentTimeMillis()) }
  LaunchedEffect(timerState.status, timerState.endAtEpochMillis, selectedCategory) {
    settingsTimerTickNow = System.currentTimeMillis()
    while (selectedCategory == null && timerState.status == TaskTimerStatus.RUNNING) {
      kotlinx.coroutines.delay(1000L)
      settingsTimerTickNow = System.currentTimeMillis()
    }
  }

  BackHandler {
    if (selectedCategory != null) {
      selectedCategoryName = null
    } else {
      onNavigateBack()
    }
  }

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var isSigningIn by remember { mutableStateOf(false) }
  var notifPermissionGranted by remember {
    mutableStateOf(TaskReminderManager.hasNotificationPermission(context))
  }
  var overlayGranted by remember {
    mutableStateOf(SystemPermissionsHelper.canDrawOverlays(context))
  }
  var accessibilityActive by remember {
    mutableStateOf(com.example.screentime.ScreenTimeEnforcementEngine.isAccessibilityServiceEnabled(context))
  }
  var batteryUnrestricted by remember {
    mutableStateOf(SystemPermissionsHelper.isIgnoringBatteryOptimizations(context))
  }
  var deviceAdminActive by remember {
    mutableStateOf(SystemPermissionsHelper.isDeviceAdminActive(context))
  }
  var isAlarmRinging by remember {
    mutableStateOf(TaskReminderManager.isAlarmBellRinging())
  }

  LaunchedEffect(selectedCategory) {
    while (selectedCategory == SettingsCategory.PRIORITY_NOTIFICATIONS) {
      notifPermissionGranted = TaskReminderManager.hasNotificationPermission(context)
      overlayGranted = SystemPermissionsHelper.canDrawOverlays(context)
      accessibilityActive = com.example.screentime.ScreenTimeEnforcementEngine.isAccessibilityServiceEnabled(context)
      batteryUnrestricted = SystemPermissionsHelper.isIgnoringBatteryOptimizations(context)
      deviceAdminActive = SystemPermissionsHelper.isDeviceAdminActive(context)
      isAlarmRinging = TaskReminderManager.isAlarmBellRinging()
      kotlinx.coroutines.delay(1200L)
    }
  }

  val notifPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    notifPermissionGranted = granted
    if (granted) {
      onShowMessage("Notification permission granted!")
    }
  }

  LaunchedEffect(selectedCategory, currentUser?.uid) {
    if (selectedCategory == SettingsCategory.UPDATES &&
      updateState.changelogText.isBlank() &&
      !updateState.isCheckingChangelog
    ) {
      onRefreshChangelog()
    }
  }

  fun openExternalUrl(url: String) {
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (_: Exception) {
      onShowMessage("Could not open $url")
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = selectedCategory?.title ?: "Settings",
            style = MaterialTheme.typography.headlineMedium
          )
        },
        navigationIcon = {
          IconButton(
            onClick = {
              if (selectedCategory != null) {
                selectedCategoryName = null
              } else {
                onNavigateBack()
              }
            },
            modifier = Modifier.testTag("settings_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_navigate_back)
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter
    ) {
      AnimatedContent(
        targetState = selectedCategory,
        transitionSpec = {
          if (targetState != null) {
            (slideInHorizontally { it / 3 } + fadeIn()).togetherWith(
              slideOutHorizontally { -it / 3 } + fadeOut()
            )
          } else {
            (slideInHorizontally { -it / 3 } + fadeIn()).togetherWith(
              slideOutHorizontally { it / 3 } + fadeOut()
            )
          }
        },
        label = "settings_category_transition",
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 600.dp)
      ) { category ->
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .navigationBarsPadding(),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          when (category) {
            null -> {
              // Master Settings List — Task Records Library is strictly available only when signed in
              val visibleCategories = SettingsCategory.entries.filter {
                it != SettingsCategory.TASK_RECORDS || currentUser != null
              }
              visibleCategories.forEach { item ->
                SettingsCategoryListItem(
                  category = item,
                  statusBadge = when (item) {
                    SettingsCategory.ACCOUNTS -> if (currentUser != null) "Synced" else "Local"
                    SettingsCategory.THEMES -> preferences.tonalSpotPalette.label.substringBefore(" ")
                    SettingsCategory.PRIORITY_NOTIFICATIONS ->
                      if (preferences.notificationsEnabled) "Active" else "Off"
                    SettingsCategory.TASK_TIMER ->
                      when (timerState.status) {
                        TaskTimerStatus.RUNNING ->
                          TaskTimerManager.formatCountdown(timerState.remainingMillisAt(settingsTimerTickNow))
                        TaskTimerStatus.PAUSED -> "Paused"
                        TaskTimerStatus.EXPIRED -> "Time's Up"
                        TaskTimerStatus.COMPLETED -> "Done"
                        TaskTimerStatus.IDLE -> "${timerState.defaultDurationMinutes}m"
                      }
                    SettingsCategory.POWER_LAB ->
                      if (focusAuraState?.isFocusSessionActive == true) "Aura On" else "AI & Sensors"
                    SettingsCategory.CATEGORIES ->
                      if (preferences.categoriesEnabled) "${preferences.activeCategories.size} active" else "Off"
                    SettingsCategory.CREDIT_SYSTEM ->
                      when {
                        currentUser == null -> "Sign In"
                        activeGroup == null -> "Group Only"
                        activeGroup.creditSystemEnabled -> "Group On"
                        else -> "Off"
                      }
                    SettingsCategory.GROUP_LEADERBOARD ->
                      when {
                        currentUser == null -> "Sign In"
                        activeGroup == null -> "No Group"
                        else -> {
                          val standings = rankGroupMembers(groupMembers, groupTasks)
                          val myRank = standings.firstOrNull { it.member.uid == currentUser.uid }?.rank
                          val kind = groupKindDisplayLabel(activeGroup.parsedType)
                          if (myRank != null) "#$myRank • $kind" else kind
                        }
                      }
                    SettingsCategory.TASK_RECORDS ->
                      if (taskRecordsCount > 0) "$taskRecordsCount Private" else "Private"
                    SettingsCategory.UPDATES ->
                      if (updateState.isReadyToInstall) "Ready" else null
                    SettingsCategory.PROJECT_DIARY -> "4 Pages"
                    SettingsCategory.ABOUT -> "frosty-sys"
                  },
                  onClick = {
                    when (item) {
                      SettingsCategory.TASK_RECORDS -> {
                        if (currentUser != null) {
                          onOpenRecordsLibrary()
                        }
                      }
                      SettingsCategory.POWER_LAB -> {
                        onOpenPowerLab()
                      }
                      else -> {
                        selectedCategoryName = item.name
                      }
                    }
                  }
                )
              }
            }

            SettingsCategory.ACCOUNTS -> {
              SectionCard(
                title = "Accounts & Cloud Sync",
                icon = if (currentUser != null) Icons.Default.CloudDone else Icons.Default.AccountCircle
              ) {
                if (currentUser == null) {
                  Text(
                    text = "Sign in to sync tasks, screen time limits, and theme across devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Button(
                    onClick = {
                      isSigningIn = true
                      GoogleAuthHelper.onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                          isSigningIn = false
                          onSyncNow()
                        },
                        onAuthError = { err ->
                          isSigningIn = false
                          onShowMessage(err)
                        },
                        scope = scope,
                        onAuthCancelled = { isSigningIn = false }
                      )
                    },
                    enabled = !isSigningIn,
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(48.dp)
                      .testTag("google_sign_in_button")
                  ) {
                    if (isSigningIn) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                      )
                      Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text("Sign in with Google")
                  }
                } else {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    val userInitial = (currentUser.displayName?.firstOrNull()
                      ?: currentUser.email?.firstOrNull() ?: 'U').uppercase()
                    ProfileAvatarBadge(
                      fallbackInitial = userInitial,
                      size = 52.dp,
                      modifier = Modifier.size(52.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = currentUser.displayName?.takeIf { it.isNotBlank() }
                          ?: "Tasks+ Cloud User",
                        style = MaterialTheme.typography.titleMedium
                      )
                      Text(
                        text = currentUser.email ?: "Google Account Connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                      if (preferences.lastSyncedAtMillis > 0L) {
                        val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
                        Text(
                          text = "Synced ${fmt.format(Date(preferences.lastSyncedAtMillis))}",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                    }
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    FilledTonalButton(
                      onClick = onSyncNow,
                      enabled = !isSyncing,
                      modifier = Modifier
                        .weight(1f)
                        .testTag("sync_now_button")
                    ) {
                      Icon(
                        Icons.Default.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(if (isSyncing) "Syncing..." else "Sync Now")
                    }
                    OutlinedButton(
                      onClick = {
                        GoogleAuthHelper.signOut(credentialManager, scope) {
                          onShowMessage("Signed out. Local tasks kept safe.")
                        }
                      },
                      modifier = Modifier
                        .weight(1f)
                        .testTag("sign_out_button")
                    ) {
                      Text("Sign Out")
                    }
                  }
                }
              }
            }

            SettingsCategory.THEMES -> {
              SectionCard(
                title = "Themes & Tonal Spot",
                icon = Icons.Default.Palette
              ) {
                Surface(
                  color = MaterialTheme.colorScheme.surfaceContainerHigh,
                  shape = RoundedCornerShape(22.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                  ) {
                    com.example.ui.components.TaskPlusExpressiveLogo(
                      useDynamicColor = preferences.useDynamicColor,
                      themeMode = preferences.themeMode,
                      tonalSpotPalette = preferences.tonalSpotPalette,
                      modifier = Modifier
                        .size(72.dp)
                        .testTag("themes_expressive_logo_preview")
                    )
                    Column(
                      modifier = Modifier.weight(1f),
                      verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      Text(
                        text = "Soft Burst & Clover Logo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                      )
                      Text(
                        text = if (preferences.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                          "Wallpaper Dynamic Color active (Android 12+)"
                        } else {
                          "${preferences.tonalSpotPalette.label} theme logo active"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                }

                Text(
                  text = "Appearance Mode",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(
                  modifier = Modifier.fillMaxWidth()
                ) {
                  ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                      selected = preferences.themeMode == mode,
                      onClick = { onUpdateThemeMode(mode) },
                      shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ThemeMode.entries.size
                      ),
                      modifier = Modifier.testTag("theme_mode_${mode.name}")
                    ) {
                      Text(
                        text = mode.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Change Based on Wallpaper",
                      style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                      text = "On by default: syncs app, logo & widgets with Android 12+ wallpaper (or follows selected palette below)",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Switch(
                    checked = preferences.useDynamicColor,
                    onCheckedChange = onUpdateDynamicColor,
                    modifier = Modifier.testTag("dynamic_color_switch")
                  )
                }

                val isStaticPaletteActive =
                  !preferences.useDynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                Text(
                  text = if (isStaticPaletteActive) {
                    "Expressive Tonal Spot Palette"
                  } else {
                    "Expressive Tonal Spot Palette (tap to use custom palette)"
                  },
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  TonalSpotPalette.entries.forEach { palette ->
                    val selected = isStaticPaletteActive && preferences.tonalSpotPalette == palette
                    Column(
                      horizontalAlignment = Alignment.CenterHorizontally,
                      modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                          onUpdatePalette(palette)
                        }
                        .padding(6.dp)
                        .testTag("palette_${palette.name}")
                    ) {
                      Box(
                        modifier = Modifier
                          .size(44.dp),
                        contentAlignment = Alignment.Center
                      ) {
                        com.example.ui.components.TaskPlusExpressiveLogo(
                          useDynamicColor = false,
                          themeMode = preferences.themeMode,
                          previewPalette = palette,
                          modifier = Modifier
                            .size(40.dp)
                            .border(
                              width = if (selected) 2.5.dp else 0.dp,
                              color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                              shape = RoundedCornerShape(9.dp)
                            )
                        )
                      }
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(
                        text = palette.label.substringBefore(" "),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) {
                          MaterialTheme.colorScheme.primary
                        } else {
                          MaterialTheme.colorScheme.onSurfaceVariant
                        }
                      )
                    }
                  }
                }
              }

              SectionCard(
                title = "Optional Dashboard Widget",
                icon = Icons.Default.BarChart
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(
                    modifier = Modifier
                      .weight(1f)
                      .padding(end = 12.dp)
                  ) {
                    Text(
                      text = "Daily Productivity Bar Chart",
                      style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                      text = "Display a Completed vs. Pending daily bar chart widget on the To-Do screen. You can also pin the 4×3 Tasks+ Daily Productivity widget to your Android Home Screen.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Switch(
                    checked = preferences.showProductivityDashboard,
                    onCheckedChange = onUpdateShowProductivityDashboard,
                    modifier = Modifier.testTag("settings_productivity_widget_switch")
                  )
                }
              }
            }

            SettingsCategory.PRIORITY_NOTIFICATIONS -> {
              SectionCard(
                title = "Priority Notifications & Permissions",
                icon = Icons.Default.NotificationsActive
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Lock Screen & Banner Alerts",
                      style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                      text = "Repeat alerts at task interval until completed",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Switch(
                    checked = preferences.notificationsEnabled,
                    onCheckedChange = onUpdateNotificationsEnabled,
                    modifier = Modifier.testTag("notifications_enabled_switch")
                  )
                }

                Text(
                  text = "Default New Task Interval",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  listOf(0 to "Off", 10 to "10m", 30 to "30m", 60 to "1h", 120 to "2h").forEach { (mins, label) ->
                    FilterChip(
                      selected = preferences.defaultReminderMinutes == mins,
                      onClick = { onUpdateDefaultReminderMinutes(mins) },
                      label = { Text(label) }
                    )
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  FilledTonalButton(
                    onClick = {
                      if (!notifPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                      } else {
                        val sent = TaskReminderManager.sendTestPriorityNotification(context)
                        onShowMessage(
                          if (sent) "Priority alert banner sent!"
                          else "Please allow notifications in system settings."
                        )
                      }
                    },
                    modifier = Modifier
                      .weight(1f)
                      .testTag("test_priority_alert_button")
                  ) {
                    Text(if (notifPermissionGranted) "Test Alert" else "Grant & Test")
                  }
                  FilledTonalButton(
                    onClick = {
                      if (isAlarmRinging) {
                        TaskReminderManager.stopAlarmBell(context)
                        isAlarmRinging = false
                        onShowMessage("Alarm bell stopped.")
                      } else {
                        TaskReminderManager.startAlarmBell(context, "test_alarm_bell")
                        isAlarmRinging = true
                        onShowMessage("Alarm bell ringing! Tap again to stop.")
                      }
                    },
                    modifier = Modifier
                      .weight(1f)
                      .testTag("test_alarm_bell_button")
                  ) {
                    Text(if (isAlarmRinging) "Stop Bell" else "Test Alarm Bell")
                  }
                }

                OutlinedButton(
                  onClick = onReopenSetupWizard,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reopen_setup_wizard_button")
                ) {
                  Text("Reopen Setup Wizard")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                  text = "System Guard Permissions",
                  style = MaterialTheme.typography.titleSmall
                )

                PermissionActionRow(
                  icon = Icons.Default.Layers,
                  title = "Screen Overlay Permission",
                  actionText = if (overlayGranted) "Active" else "Configure",
                  onClick = { SystemPermissionsHelper.openOverlayPermissionSettings(context) }
                )
                PermissionActionRow(
                  icon = Icons.Default.AccessibilityNew,
                  title = "Accessibility Service (Optional)",
                  actionText = if (accessibilityActive) "Active" else "Open",
                  onClick = { SystemPermissionsHelper.openAccessibilitySettings(context) }
                )
                PermissionActionRow(
                  icon = Icons.Default.BatteryChargingFull,
                  title = "Turn Off Battery Optimizations",
                  actionText = if (batteryUnrestricted) "Active" else "Configure",
                  onClick = { SystemPermissionsHelper.openBatteryOptimizationSettings(context) }
                )
                PermissionActionRow(
                  icon = Icons.Default.AdminPanelSettings,
                  title = "Device Admin Guard",
                  actionText = if (deviceAdminActive) "Active" else "Activate",
                  onClick = { SystemPermissionsHelper.openDeviceAdminSecuritySettings(context) }
                )
              }
            }

            SettingsCategory.TASK_TIMER -> {
              TaskTimerSection(
                timerState = timerState,
                activeTasks = activeTasks,
                onStartTimer = onStartTaskTimer,
                onPauseTimer = onPauseTaskTimer,
                onResumeTimer = onResumeTaskTimer,
                onAddFiveMinutes = onAddTimerFiveMinutes,
                onResetTimer = onResetTaskTimer,
                onCheckExpired = onCheckTimerExpired,
                onCompleteTask = onCompleteTaskFromTimer,
                onUpdateSettings = onUpdateTaskTimerSettings,
                focusAuraState = focusAuraState,
                onToggleFocusAura = onToggleFocusAura
              )
            }

            SettingsCategory.POWER_LAB -> {
              LaunchedEffect(Unit) {
                selectedCategoryName = null
                onOpenPowerLab()
              }
            }

            SettingsCategory.CATEGORIES -> {
              TaskCategoriesSettingsSection(
                categoriesEnabled = preferences.categoriesEnabled,
                disabledPremadeIds = preferences.disabledPremadeCategoryIds,
                customCategories = preferences.customCategories,
                onToggleCategoriesEnabled = onUpdateCategoriesEnabled,
                onTogglePremadeCategory = onSetPremadeCategoryEnabled,
                onAddCustomCategory = onAddCustomCategory,
                onDeleteCustomCategory = onDeleteCustomCategory
              )
            }

            SettingsCategory.CREDIT_SYSTEM -> {
              if (currentUser == null) {
                SectionCard(
                  title = "Group Points & Perks (Sign In Required)",
                  icon = Icons.Default.Stars
                ) {
                  Text(
                    text = "Optional points (disabled by default) and manager-assigned perks are exclusive to Family and Workplace Groups. Sign in with Google to join or manage a group.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Button(
                    onClick = {
                      isSigningIn = true
                      GoogleAuthHelper.onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                          isSigningIn = false
                          onSyncNow()
                        },
                        onAuthError = { err ->
                          isSigningIn = false
                          onShowMessage(err)
                        },
                        scope = scope,
                        onAuthCancelled = { isSigningIn = false }
                      )
                    },
                    enabled = !isSigningIn,
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(48.dp)
                      .testTag("settings_credit_sign_in_button")
                  ) {
                    if (isSigningIn) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                      )
                      Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text("Sign in with Google")
                  }
                }
              } else if (activeGroup == null) {
                SectionCard(
                  title = "Group Points & Perks",
                  icon = Icons.Default.Stars
                ) {
                  Text(
                    text = "Points and perks are optional, off by default, and exclusive to Family or Workplace Groups. Create or join a group in the Group tab, where the main Group Manager can turn Group Points on and assign specific perks to members.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  FilledTonalButton(
                    onClick = onOpenGroupsTab,
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(48.dp)
                      .testTag("settings_open_groups_for_points_button")
                  ) {
                    Text("Open Group Tab")
                  }
                }
              } else {
                val isMainManager = activeGroup.managerUid == currentUser.uid
                val currentMember = groupMembers.firstOrNull { it.uid == currentUser.uid }
                val myBalance = currentMember?.creditBalance ?: 0L
                val currentRules = activeGroup.creditRules
                var creditEnabled by rememberSaveable(currentRules.enabled, activeGroup.id) {
                  mutableStateOf(currentRules.enabled)
                }
                var earlyRateStr by rememberSaveable(currentRules.earlyPointsPerSecond, activeGroup.id) {
                  mutableStateOf(CreditCalculator.formatRate(currentRules.earlyPointsPerSecond))
                }
                var lateRateStr by rememberSaveable(currentRules.latePenaltyPerSecond, activeGroup.id) {
                  mutableStateOf(CreditCalculator.formatRate(currentRules.latePenaltyPerSecond))
                }
                var graceSecStr by rememberSaveable(currentRules.gracePeriodSeconds, activeGroup.id) {
                  mutableStateOf(currentRules.gracePeriodSeconds.toString())
                }
                var allowNegative by rememberSaveable(currentRules.allowNegativeBalance, activeGroup.id) {
                  mutableStateOf(currentRules.allowNegativeBalance)
                }

                SectionCard(
                  title = "${activeGroup.name} • Group Points & Perks",
                  icon = Icons.Default.Stars
                ) {
                  Surface(
                    color = if (!currentRules.enabled) {
                      MaterialTheme.colorScheme.surfaceContainerHigh
                    } else if (myBalance >= 0L) {
                      MaterialTheme.colorScheme.primaryContainer
                    } else {
                      MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Column(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                      verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      Text(
                        text = "Your Group Points",
                        style = MaterialTheme.typography.labelLarge
                      )
                      Text(
                        text = if (currentRules.enabled) {
                          CreditCalculator.formatSignedPoints(myBalance)
                        } else {
                          "Off (Disabled by Default)"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                      )
                    }
                  }

                  if (!isMainManager) {
                    Surface(
                      color = MaterialTheme.colorScheme.secondaryContainer,
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Text(
                        text = "Main Manager Only — Only ${activeGroup.managerName.ifBlank { "the Group Manager" }} can turn Group Points on/off, adjust point rules, or assign perks.",
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
                        text = "Enable Group Points (Optional)",
                        style = MaterialTheme.typography.titleSmall
                      )
                      Text(
                        text = "Off by default. Reward early group task completion and let members redeem manager-assigned perks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                    Switch(
                      checked = creditEnabled,
                      onCheckedChange = { if (isMainManager) creditEnabled = it },
                      enabled = isMainManager,
                      modifier = Modifier.testTag("settings_credit_enabled_switch")
                    )
                  }

                  AnimatedVisibility(visible = creditEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                      ) {
                        OutlinedTextField(
                          value = earlyRateStr,
                          onValueChange = { s ->
                            if (isMainManager) earlyRateStr = s.filter { it.isDigit() || it == '.' }.take(6)
                          },
                          enabled = isMainManager,
                          label = { Text("Early (pts/sec)") },
                          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                          singleLine = true,
                          shape = RoundedCornerShape(14.dp),
                          modifier = Modifier
                            .weight(1f)
                            .testTag("settings_credit_early_rate_input")
                        )
                        OutlinedTextField(
                          value = lateRateStr,
                          onValueChange = { s ->
                            if (isMainManager) lateRateStr = s.filter { it.isDigit() || it == '.' }.take(6)
                          },
                          enabled = isMainManager,
                          label = { Text("Late (-pts/sec)") },
                          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                          singleLine = true,
                          shape = RoundedCornerShape(14.dp),
                          modifier = Modifier
                            .weight(1f)
                            .testTag("settings_credit_late_rate_input")
                        )
                      }

                      OutlinedTextField(
                        value = graceSecStr,
                        onValueChange = { s ->
                          if (isMainManager) graceSecStr = s.filter { it.isDigit() }.take(5)
                        },
                        enabled = isMainManager,
                        label = { Text("Grace Period (seconds)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                          .fillMaxWidth()
                          .testTag("settings_credit_grace_input")
                      )

                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                            text = "Allow Negative Balance",
                            style = MaterialTheme.typography.titleSmall
                          )
                          Text(
                            text = "Turn off to floor member balances at 0 pts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                          )
                        }
                        Switch(
                          checked = allowNegative,
                          onCheckedChange = { if (isMainManager) allowNegative = it },
                          enabled = isMainManager,
                          modifier = Modifier.testTag("settings_credit_allow_negative_switch")
                        )
                      }
                    }
                  }

                  if (isMainManager) {
                    Button(
                      onClick = {
                        onUpdateGroupCreditRules(
                          currentRules.copy(
                            enabled = creditEnabled,
                            earlyPointsPerSecond = earlyRateStr.toDoubleOrNull()?.coerceIn(0.0, 1000.0) ?: 1.0,
                            latePenaltyPerSecond = lateRateStr.toDoubleOrNull()?.coerceIn(0.0, 1000.0) ?: 1.0,
                            gracePeriodSeconds = graceSecStr.toLongOrNull()?.coerceIn(0L, 86_400L) ?: 0L,
                            allowNegativeBalance = allowNegative
                          )
                        )
                      },
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_save_credit_rules_button")
                    ) {
                      Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Save Group Points Rules")
                    }
                  }

                  HorizontalDivider()
                  Text(
                    text = "Group Perks (${groupPerks.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                  )
                  val myAvailablePerks = groupPerks.filter {
                    it.assignedToUid == currentUser.uid && it.parsedStatus == PerkStatus.ASSIGNED
                  }
                  if (myAvailablePerks.isNotEmpty()) {
                    myAvailablePerks.forEach { perk ->
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Column(modifier = Modifier.weight(1f)) {
                          Text(perk.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                          Text(
                            if (perk.pointsCost > 0L) "${perk.pointsCost} pts" else "Direct Manager Grant",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                          )
                        }
                        if (perk.pointsCost > 0L && currentRules.enabled) {
                          FilledTonalButton(
                            onClick = { onRedeemGroupPerk(perk) },
                            enabled = myBalance >= perk.pointsCost,
                            modifier = Modifier.height(48.dp)
                          ) {
                            Text("Redeem")
                          }
                        }
                      }
                    }
                  } else {
                    Text(
                      text = "Open Group Tab → Perks to ${if (isMainManager) "assign and manage member perks" else "view assigned perks"}.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  FilledTonalButton(
                    onClick = onOpenGroupsTab,
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(48.dp)
                      .testTag("settings_open_group_perks_button")
                  ) {
                    Text("Open Group Tab")
                  }
                }
              }
            }

            SettingsCategory.GROUP_LEADERBOARD -> {
              GroupLeaderboardSection(
                isSignedIn = currentUser != null,
                currentUserUid = currentUser?.uid.orEmpty(),
                activeGroup = activeGroup,
                groupMembers = groupMembers,
                groupTasks = groupTasks,
                isSigningIn = isSigningIn,
                onSignInClick = {
                  isSigningIn = true
                  GoogleAuthHelper.onGoogleSignInClicked(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {
                      isSigningIn = false
                      onSyncNow()
                    },
                    onAuthError = { err ->
                      isSigningIn = false
                      onShowMessage(err)
                    },
                    scope = scope,
                    onAuthCancelled = { isSigningIn = false }
                  )
                },
                onOpenGroupsTab = onOpenGroupsTab
              )
            }

            SettingsCategory.UPDATES -> {
              SectionCard(
                title = "Updates",
                icon = Icons.Default.SystemUpdate
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Tasks+ Direct Update Channel",
                      style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                      text = AppUpdateManager.APK_URL,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.primary
                    )
                  }
                  IconButton(
                    onClick = onRefreshChangelog,
                    modifier = Modifier.testTag("check_updates_button")
                  ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh update changelog")
                  }
                }

                Surface(
                  color = MaterialTheme.colorScheme.surfaceContainerHighest,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                      text = "What's New (tasks+.html)",
                      style = MaterialTheme.typography.labelLarge,
                      color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = if (updateState.isCheckingChangelog) {
                        "Fetching update description from ${AppUpdateManager.CHANGELOG_URL}..."
                      } else {
                        updateState.changelogText.ifBlank { "Tap refresh to check for new release notes." }
                      },
                      style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  }
                }

                AnimatedVisibility(
                  visible = updateState.isDownloading || updateState.downloadProgress > 0f || updateState.statusMessage != null
                ) {
                  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                      progress = { updateState.downloadProgress.coerceIn(0f, 1f) },
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                    )
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(
                        text = updateState.statusMessage ?: "Ready",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                      )
                      Text(
                        text = "${(updateState.downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                      )
                    }
                  }
                }

                updateState.errorMessage?.let { errMsg ->
                  Text(
                    text = errMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Delete installer file after update",
                      style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                      text = "Automatically clean up downloaded tasks+.apk",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Switch(
                    checked = preferences.autoDeleteUpdateApk,
                    onCheckedChange = onUpdateAutoDeleteApk,
                    modifier = Modifier.testTag("auto_delete_apk_switch")
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Button(
                    onClick = {
                      if (updateState.isReadyToInstall) {
                        onInstallDownloadedApk()
                      } else {
                        onDownloadUpdateApk()
                      }
                    },
                    enabled = !updateState.isDownloading && !updateState.isInstalling,
                    modifier = Modifier
                      .weight(1f)
                      .testTag("download_update_apk_button")
                  ) {
                    Icon(
                      imageVector = if (updateState.isReadyToInstall) Icons.Default.SystemUpdate else Icons.Default.Download,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = when {
                        updateState.isDownloading -> "Downloading..."
                        updateState.isInstalling -> "Installing..."
                        updateState.isReadyToInstall -> "Update Now"
                        else -> "In-App Update"
                      }
                    )
                  }

                  FilledTonalButton(
                    onClick = { openExternalUrl(AppUpdateManager.GITHUB_RELEASES_URL) },
                    modifier = Modifier
                      .weight(1f)
                      .testTag("github_releases_button")
                  ) {
                    Icon(
                      Icons.AutoMirrored.Filled.OpenInNew,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GitHub Releases")
                  }
                }

                OutlinedButton(
                  onClick = { openExternalUrl(AppUpdateManager.APK_URL) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("download_apk_browser_button")
                ) {
                  Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Download APK in Browser")
                }

                if (updateState.downloadedFilePath != null) {
                  OutlinedButton(
                    onClick = onDeleteDownloadedApk,
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("delete_cached_apk_button")
                  ) {
                    Icon(
                      Icons.Default.DeleteOutline,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Downloaded Installer File Now")
                  }
                }
              }
            }

            SettingsCategory.TASK_RECORDS -> {
              LaunchedEffect(currentUser) {
                selectedCategoryName = null
                if (currentUser != null) {
                  onOpenRecordsLibrary()
                }
              }
            }

            SettingsCategory.PROJECT_DIARY -> {
              ProjectDiaryBookletSection(
                onShowMessage = onShowMessage
              )
            }

            SettingsCategory.ABOUT -> {
              SectionCard(
                title = "About Tasks+",
                icon = Icons.Default.Favorite
              ) {
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                  ) {
                    com.example.ui.components.TaskPlusExpressiveLogo(
                      useDynamicColor = preferences.useDynamicColor,
                      themeMode = preferences.themeMode,
                      tonalSpotPalette = preferences.tonalSpotPalette,
                      modifier = Modifier.size(56.dp)
                    )
                    Column(
                      modifier = Modifier.weight(1f),
                      verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      Text(
                        text = stringResource(R.string.crafted_by_frosty),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                      )
                      Text(
                        text = "Expressive daily task & screen time manager.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                      )
                    }
                  }
                }

                Button(
                  onClick = { selectedCategoryName = SettingsCategory.PROJECT_DIARY.name },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("about_open_project_diary_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.AutoStories,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Read 4-Page Project Diary Booklet")
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  FilledTonalButton(
                    onClick = { openExternalUrl(AppUpdateManager.FROSTY_GITHUB_URL) },
                    modifier = Modifier
                      .weight(1f)
                      .testTag("visit_frosty_github_button")
                  ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("frosty-sys")
                  }
                  FilledTonalButton(
                    onClick = { openExternalUrl(AppUpdateManager.GITHUB_REPO_URL) },
                    modifier = Modifier
                      .weight(1f)
                      .testTag("visit_tasksplus_github_button")
                  ) {
                    Icon(
                      Icons.AutoMirrored.Filled.OpenInNew,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tasks+ Repo")
                  }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                  )
                  Text(
                    text = "Explore More Projects",
                    style = MaterialTheme.typography.titleMedium
                  )
                }

                ProjectLinkRow(
                  name = "RNA. TV",
                  url = AppUpdateManager.RNATV_GITHUB_URL,
                  tag = "project_rnatv_button",
                  onClick = { openExternalUrl(AppUpdateManager.RNATV_GITHUB_URL) }
                )

                ProjectLinkRow(
                  name = "navstack",
                  url = AppUpdateManager.NAVSTACK_GITHUB_URL,
                  tag = "project_navstack_button",
                  onClick = { openExternalUrl(AppUpdateManager.NAVSTACK_GITHUB_URL) }
                )

                ProjectLinkRow(
                  name = "Tasks+ (tasksplus)",
                  url = AppUpdateManager.GITHUB_REPO_URL,
                  tag = "project_tasksplus_button",
                  onClick = { openExternalUrl(AppUpdateManager.GITHUB_REPO_URL) }
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))
        }
      }
    }
  }
}

@Composable
private fun SettingsCategoryListItem(
  category: SettingsCategory,
  statusBadge: String?,
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .clickable(onClick = onClick)
      .testTag(category.tag)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.weight(1f)
      ) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.size(48.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = category.icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
          }
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = category.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = category.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (statusBadge != null) {
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape
          ) {
            Text(
              text = statusBadge,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
          }
        }
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun PermissionActionRow(
  icon: ImageVector,
  title: String,
  actionText: String,
  onClick: () -> Unit
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f)
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall
        )
      }
      FilledTonalButton(onClick = onClick) {
        Text(actionText)
      }
    }
  }
}

@Composable
private fun SectionCard(
  title: String,
  icon: ImageVector,
  content: @Composable () -> Unit
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary
        )
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
      content()
    }
  }
}

@Composable
private fun ProjectLinkRow(
  name: String,
  url: String,
  tag: String,
  onClick: () -> Unit
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .testTag(tag)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = name,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = url,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
        contentDescription = "Open $name",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp)
      )
    }
  }
}
