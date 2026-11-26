package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.AdminPanelSettings
import com.example.data.model.InstalledAppInfo
import com.example.data.model.ScreenTimeLimitEntity
import com.example.data.model.ScreenTimePeriod
import com.example.screentime.RealDeviceAppsScanner
import com.example.screentime.ScreenTimeEnforcementEngine
import com.example.screentime.TasksPlusDeviceAdminReceiver
import kotlinx.coroutines.delay

object SystemPermissionsHelper {
  fun canDrawOverlays(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Settings.canDrawOverlays(context)
    } else {
      true
    }
  }

  fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      pm.isIgnoringBatteryOptimizations(context.packageName)
    } else {
      true
    }
  }

  fun isDeviceAdminActive(context: Context): Boolean {
    return TasksPlusDeviceAdminReceiver.isAdminActive(context)
  }

  fun openOverlayPermissionSettings(context: Context) {
    try {
      val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    } catch (_: Exception) {
      openAppDetailsSettings(context)
    }
  }

  fun openAccessibilitySettings(context: Context) {
    try {
      val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    } catch (_: Exception) {
      openAppDetailsSettings(context)
    }
  }

  fun openUsageAccessSettings(context: Context) {
    ScreenTimeEnforcementEngine.openUsageAccessSettings(context)
  }

  fun openBatteryOptimizationSettings(context: Context) {
    try {
      val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    } catch (_: Exception) {
      openAppDetailsSettings(context)
    }
  }

  fun openDeviceAdminSecuritySettings(context: Context) {
    TasksPlusDeviceAdminReceiver.requestDeviceAdminActivation(context)
  }

  fun openAppDetailsSettings(context: Context) {
    try {
      val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    } catch (_: Exception) {
    }
  }
}

@Composable
fun ScreenTimeTabScreen(
  limits: List<ScreenTimeLimitEntity>,
  isChildSupervised: Boolean = false,
  canManageGroupLimits: Boolean = false,
  preferences: com.example.data.model.UserPreferencesState = com.example.data.model.UserPreferencesState(),
  onToggleEarnYourScroll: (Boolean) -> Unit = {},
  onChangeEarnBaseMinutes: (Int) -> Unit = {},
  onSpendEarnedScreenTime: (ScreenTimeLimitEntity, Int) -> Unit = { _, _ -> },
  onSaveLimit: (packageName: String, appName: String, limitMinutes: Int, period: ScreenTimePeriod) -> Unit,
  onRecordUsage: (packageName: String, addMinutes: Int) -> Unit,
  onTriggerOverlay: (ScreenTimeLimitEntity) -> Unit,
  onRemoveLimit: (packageName: String) -> Unit
) {
  val context = LocalContext.current
  var includeSystemApps by rememberSaveable { mutableStateOf(false) }
  var refreshTick by remember { mutableIntStateOf(0) }

  var isAccessibilityActive by remember {
    mutableStateOf(ScreenTimeEnforcementEngine.isAccessibilityServiceEnabled(context))
  }
  var isUsageAccessGranted by remember {
    mutableStateOf(ScreenTimeEnforcementEngine.hasUsageStatsPermission(context))
  }
  var isOverlayGranted by remember {
    mutableStateOf(SystemPermissionsHelper.canDrawOverlays(context))
  }
  var isDeviceAdminActive by remember {
    mutableStateOf(SystemPermissionsHelper.isDeviceAdminActive(context))
  }

  // Poll permission states every 1.5s while on the Screen Time tab so changes in Android Settings reflect immediately
  LaunchedEffect(Unit) {
    while (true) {
      isAccessibilityActive = ScreenTimeEnforcementEngine.isAccessibilityServiceEnabled(context)
      isUsageAccessGranted = ScreenTimeEnforcementEngine.hasUsageStatsPermission(context)
      isOverlayGranted = SystemPermissionsHelper.canDrawOverlays(context)
      isDeviceAdminActive = SystemPermissionsHelper.isDeviceAdminActive(context)
      delay(1500L)
    }
  }

  val installedApps = remember(context, includeSystemApps, refreshTick) {
    RealDeviceAppsScanner.queryRealInstalledApps(
      context = context,
      includeSystemApps = includeSystemApps
    )
  }

  var searchQuery by rememberSaveable { mutableStateOf("") }
  var selectedAppForDialog by remember { mutableStateOf<InstalledAppInfo?>(null) }

  val activeLimits = remember(limits) {
    limits.filter { it.isEnabled && it.limitMinutes > 0 }
  }
  val limitsByPackage = remember(limits) {
    limits.associateBy { it.packageName }
  }

  val filteredApps = remember(installedApps, searchQuery, limitsByPackage) {
    val q = searchQuery.trim()
    val base = if (q.isEmpty()) {
      installedApps
    } else {
      installedApps.filter {
        it.appName.contains(q, ignoreCase = true) ||
          it.packageName.contains(q, ignoreCase = true) ||
          it.categoryHint.contains(q, ignoreCase = true)
      }
    }
    base.sortedWith(
      compareByDescending<InstalledAppInfo> { limitsByPackage[it.packageName]?.isEnabled == true }
        .thenBy { it.appName.lowercase() }
    )
  }

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search installed device apps...") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search apps",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Default.Close, contentDescription = "Clear search")
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
          .weight(1f)
          .testTag("screen_time_search_bar")
      )

      IconButton(
        onClick = { refreshTick += 1 },
        modifier = Modifier.testTag("refresh_device_apps_button")
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = "Refresh installed apps"
        )
      }
    }

    LazyColumn(
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .fillMaxSize()
        .testTag("screen_time_list")
    ) {
      // Earn Your Scroll Wallet Card
      item {
        com.example.ui.components.EarnYourScrollCard(
          preferences = preferences,
          onToggleEnabled = onToggleEarnYourScroll,
          onChangeBaseMinutes = onChangeEarnBaseMinutes
        )
      }

      // Real-time Enforcement Permissions Card if any guard permission is missing
      if (!isAccessibilityActive || !isUsageAccessGranted || !isOverlayGranted || !isDeviceAdminActive) {
        item {
          EnforcementGuardSetupCard(
            isAccessibilityActive = isAccessibilityActive,
            isUsageAccessGranted = isUsageAccessGranted,
            isOverlayGranted = isOverlayGranted,
            isDeviceAdminActive = isDeviceAdminActive,
            onOpenAccessibility = { SystemPermissionsHelper.openAccessibilitySettings(context) },
            onOpenUsageAccess = { SystemPermissionsHelper.openUsageAccessSettings(context) },
            onOpenOverlay = { SystemPermissionsHelper.openOverlayPermissionSettings(context) },
            onOpenDeviceAdmin = { SystemPermissionsHelper.openDeviceAdminSecuritySettings(context) }
          )
        }
      }

      // Highlighted Active Limits Section at top of screen
      item {
        AnimatedVisibility(
          visible = activeLimits.isNotEmpty(),
          enter = fadeIn() + expandVertically(),
          exit = fadeOut() + shrinkVertically()
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.LockClock,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp)
                )
                Text(
                  text = "Active App Limits (${activeLimits.size})",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold
                )
              }
              Text(
                text = "Tap to adjust",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            activeLimits.forEach { limit ->
              val isProtectedByGroup = (limit.isGroupEnforced && !canManageGroupLimits) || isChildSupervised
              HighlightedLimitCard(
                limit = limit,
                isProtectedByGroup = isProtectedByGroup,
                canSpendWalletMinutes = preferences.earnYourScrollEnabled &&
                  preferences.earnedScreenTimeMinutes >= 10 &&
                  !isProtectedByGroup,
                onSpendWalletMinutes = { onSpendEarnedScreenTime(limit, 10) },
                onClickAdjust = {
                  selectedAppForDialog = InstalledAppInfo(
                    packageName = limit.packageName,
                    appName = limit.appName,
                    categoryHint = "Limited App"
                  )
                },
                onLockAppNow = {
                  val needed = (limit.limitMinutes - limit.usedMinutes).coerceAtLeast(1)
                  onRecordUsage(limit.packageName, needed)
                },
                onTriggerLockOverlay = { onTriggerOverlay(limit) }
              )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
          }
        }
      }

      // Header + System Apps filter chip
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Apps,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = "Installed Device Apps (${filteredApps.size})",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          FilterChip(
            selected = includeSystemApps,
            onClick = { includeSystemApps = !includeSystemApps },
            label = { Text("All System Apps") },
            leadingIcon = if (includeSystemApps) {
              { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
            } else null,
            modifier = Modifier.testTag("toggle_system_apps_chip")
          )
        }
      }

      items(items = filteredApps, key = { it.packageName }) { appInfo ->
        val existingLimit = limitsByPackage[appInfo.packageName]
        DeviceAppRowCard(
          appInfo = appInfo,
          limit = existingLimit,
          onClick = { selectedAppForDialog = appInfo }
        )
      }
    }
  }

  val editingApp = selectedAppForDialog
  if (editingApp != null) {
    val existing = limitsByPackage[editingApp.packageName]
    val isLockedByGroup = (existing?.isGroupEnforced == true && !canManageGroupLimits) || isChildSupervised
    ScreenTimeConfigSheet(
      appInfo = editingApp,
      existingLimit = existing,
      isLockedByGroup = isLockedByGroup,
      onDismiss = { selectedAppForDialog = null },
      onSave = { mins, period ->
        onSaveLimit(editingApp.packageName, editingApp.appName, mins, period)
        selectedAppForDialog = null
      },
      onRemove = {
        onRemoveLimit(editingApp.packageName)
        selectedAppForDialog = null
      }
    )
  }
}

@Composable
private fun EnforcementGuardSetupCard(
  isAccessibilityActive: Boolean,
  isUsageAccessGranted: Boolean,
  isOverlayGranted: Boolean,
  isDeviceAdminActive: Boolean,
  onOpenAccessibility: () -> Unit,
  onOpenUsageAccess: () -> Unit,
  onOpenOverlay: () -> Unit,
  onOpenDeviceAdmin: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("enforcement_guard_setup_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Security,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = "Enable Real App Blocking",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold
        )
      }

      GuardPermissionCompactRow(
        icon = Icons.Default.AccessibilityNew,
        title = "Accessibility Blocker Service",
        isGranted = isAccessibilityActive,
        onClick = onOpenAccessibility
      )
      GuardPermissionCompactRow(
        icon = Icons.Default.QueryStats,
        title = "Usage Access (Track App Time)",
        isGranted = isUsageAccessGranted,
        onClick = onOpenUsageAccess
      )
      GuardPermissionCompactRow(
        icon = Icons.Default.Layers,
        title = "Display Over Other Apps",
        isGranted = isOverlayGranted,
        onClick = onOpenOverlay
      )
      GuardPermissionCompactRow(
        icon = Icons.Default.AdminPanelSettings,
        title = "Device Admin Guard",
        isGranted = isDeviceAdminActive,
        onClick = onOpenDeviceAdmin
      )
    }
  }
}

@Composable
private fun GuardPermissionCompactRow(
  icon: ImageVector,
  title: String,
  isGranted: Boolean,
  onClick: () -> Unit
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
        imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
        contentDescription = null,
        tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp)
      )
      Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
    if (isGranted) {
      Text(
        text = "Active",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
      )
    } else {
      FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp)
      ) {
        Text("Enable", style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}

@Composable
private fun HighlightedLimitCard(
  limit: ScreenTimeLimitEntity,
  isProtectedByGroup: Boolean = false,
  canSpendWalletMinutes: Boolean = false,
  onSpendWalletMinutes: () -> Unit = {},
  onClickAdjust: () -> Unit,
  onLockAppNow: () -> Unit,
  onTriggerLockOverlay: () -> Unit
) {
  val context = LocalContext.current
  val appIcon = remember(context, limit.packageName) {
    RealDeviceAppsScanner.getAppIconBitmap(context, limit.packageName)
  }
  val rawProgress = (limit.usedMinutes.toFloat() / limit.limitMinutes.coerceAtLeast(1).toFloat())
    .coerceIn(0f, 1f)
  val animatedProgress by animateFloatAsState(
    targetValue = rawProgress,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessLow
    ),
    label = "screen_time_progress"
  )
  val isLocked = limit.isLimitReached

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isLocked) {
        MaterialTheme.colorScheme.errorContainer
      } else {
        MaterialTheme.colorScheme.primaryContainer
      }
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .clickable(onClick = onClickAdjust)
      .testTag("highlighted_limit_${limit.packageName}")
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
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          if (appIcon != null) {
            Image(
              bitmap = appIcon,
              contentDescription = limit.appName,
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
            )
          } else {
            Surface(
              color = if (isLocked) {
                MaterialTheme.colorScheme.error
              } else {
                MaterialTheme.colorScheme.primary
              },
              shape = CircleShape,
              modifier = Modifier.size(42.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.HourglassTop,
                  contentDescription = null,
                  tint = if (isLocked) {
                    MaterialTheme.colorScheme.onError
                  } else {
                    MaterialTheme.colorScheme.onPrimary
                  },
                  modifier = Modifier.size(20.dp)
                )
              }
            }
          }

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = limit.appName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = if (isLocked) {
                MaterialTheme.colorScheme.onErrorContainer
              } else {
                MaterialTheme.colorScheme.onPrimaryContainer
              },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = limit.packageName,
              style = MaterialTheme.typography.labelSmall,
              color = if (isLocked) {
                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
              } else {
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
              },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Surface(
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text(
            text = "${limit.parsedPeriod.label} • ${formatMinutesShort(limit.limitMinutes)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
          )
        }
      }

      if (limit.isGroupEnforced || isProtectedByGroup) {
        Surface(
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
              text = "Group Enforced by ${limit.enforcedByName.ifBlank { "Group Manager" }}",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            if (limit.requiredGroupTaskTitle.isNotBlank()) {
              Text(
                text = "Complete Task to Unlock: ${limit.requiredGroupTaskTitle}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
              )
            }
          }
        }
      }

      LinearProgressIndicator(
        progress = { animatedProgress },
        color = if (isLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
        modifier = Modifier
          .fillMaxWidth()
          .height(9.dp)
          .clip(CircleShape)
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (isLocked) {
            "Blocked when opened (${formatMinutesShort(limit.usedMinutes)} used)"
          } else {
            "${formatMinutesShort(limit.usedMinutes)} used • ${formatMinutesShort(limit.remainingMinutes)} left"
          },
          style = MaterialTheme.typography.labelMedium,
          color = if (isLocked) {
            MaterialTheme.colorScheme.onErrorContainer
          } else {
            MaterialTheme.colorScheme.onPrimaryContainer
          },
          modifier = Modifier.weight(1f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (canSpendWalletMinutes) {
            FilledTonalButton(
              onClick = onSpendWalletMinutes,
              modifier = Modifier.testTag("spend_wallet_${limit.packageName}")
            ) {
              Text("+10m Wallet")
            }
          }
          if (!isLocked) {
            FilledTonalButton(
              onClick = onLockAppNow,
              modifier = Modifier.testTag("lock_now_${limit.packageName}")
            ) {
              Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Lock Now")
            }
          } else {
            FilledTonalButton(
              onClick = onTriggerLockOverlay,
              modifier = Modifier.testTag("preview_lock_${limit.packageName}")
            ) {
              Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Shield Active")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DeviceAppRowCard(
  appInfo: InstalledAppInfo,
  limit: ScreenTimeLimitEntity?,
  onClick: () -> Unit
) {
  val context = LocalContext.current
  val appIcon = remember(context, appInfo.packageName) {
    RealDeviceAppsScanner.getAppIconBitmap(context, appInfo.packageName)
  }
  val hasActiveLimit = limit != null && limit.isEnabled && limit.limitMinutes > 0

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (hasActiveLimit) {
        MaterialTheme.colorScheme.secondaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      }
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .clickable(onClick = onClick)
      .testTag("app_item_${appInfo.packageName}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.weight(1f)
      ) {
        if (appIcon != null) {
          Image(
            bitmap = appIcon,
            contentDescription = appInfo.appName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(12.dp))
          )
        } else {
          Surface(
            color = if (hasActiveLimit) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.surfaceContainerHighest
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(44.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = appInfo.appName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (hasActiveLimit) {
                  MaterialTheme.colorScheme.onPrimary
                } else {
                  MaterialTheme.colorScheme.primary
                }
              )
            }
          }
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = appInfo.appName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = appInfo.packageName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      if (limit != null && limit.isEnabled && limit.limitMinutes > 0) {
        Surface(
          color = if (limit.isLimitReached) {
            MaterialTheme.colorScheme.error
          } else {
            MaterialTheme.colorScheme.primary
          },
          shape = RoundedCornerShape(12.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = if (limit.isLimitReached) Icons.Default.Lock else Icons.Default.Timer,
              contentDescription = null,
              tint = if (limit.isLimitReached) {
                MaterialTheme.colorScheme.onError
              } else {
                MaterialTheme.colorScheme.onPrimary
              },
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "${formatMinutesShort(limit.limitMinutes)} / ${limit.parsedPeriod.label}",
              style = MaterialTheme.typography.labelSmall,
              color = if (limit.isLimitReached) {
                MaterialTheme.colorScheme.onError
              } else {
                MaterialTheme.colorScheme.onPrimary
              }
            )
          }
        }
      } else {
        Text(
          text = "Set Limit",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScreenTimeConfigSheet(
  appInfo: InstalledAppInfo,
  existingLimit: ScreenTimeLimitEntity?,
  isLockedByGroup: Boolean = false,
  onDismiss: () -> Unit,
  onSave: (limitMinutes: Int, period: ScreenTimePeriod) -> Unit,
  onRemove: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var selectedPeriod by remember {
    mutableStateOf(existingLimit?.parsedPeriod ?: ScreenTimePeriod.DAILY)
  }
  var selectedMinutes by remember {
    mutableIntStateOf(existingLimit?.limitMinutes ?: 30)
  }
  var customMinutesText by remember {
    mutableStateOf((existingLimit?.limitMinutes ?: 30).toString())
  }

  val presetMinutes = remember(selectedPeriod) {
    when (selectedPeriod) {
      ScreenTimePeriod.HOURLY -> listOf(1, 5, 10, 15, 30, 45)
      ScreenTimePeriod.DAILY -> listOf(1, 5, 15, 30, 60, 120)
      ScreenTimePeriod.WEEKLY -> listOf(15, 60, 180, 300, 600, 900)
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 10.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.size(48.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.HourglassBottom,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
          }
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = appInfo.appName,
            style = MaterialTheme.typography.headlineSmall
          )
          Text(
            text = appInfo.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Text(
        text = "Schedule Period",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        ScreenTimePeriod.entries.forEach { period ->
          FilterChip(
            selected = selectedPeriod == period,
            onClick = {
              selectedPeriod = period
              val defaultForPeriod = when (period) {
                ScreenTimePeriod.HOURLY -> 15
                ScreenTimePeriod.DAILY -> 30
                ScreenTimePeriod.WEEKLY -> 180
              }
              selectedMinutes = defaultForPeriod
              customMinutesText = defaultForPeriod.toString()
            },
            label = { Text(period.label) },
            modifier = Modifier
              .weight(1f)
              .testTag("period_chip_${period.name}")
          )
        }
      }

      Text(
        text = "Allowed Time (${selectedPeriod.label})",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        presetMinutes.forEach { mins ->
          FilterChip(
            selected = selectedMinutes == mins,
            onClick = {
              selectedMinutes = mins
              customMinutesText = mins.toString()
            },
            label = { Text(formatMinutesShort(mins)) },
            leadingIcon = if (selectedMinutes == mins) {
              { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null
          )
        }
      }

      OutlinedTextField(
        value = customMinutesText,
        onValueChange = { value ->
          val digits = value.filter { it.isDigit() }.take(5)
          customMinutesText = digits
          digits.toIntOrNull()?.let { parsed ->
            if (parsed > 0) selectedMinutes = parsed
          }
        },
        label = { Text("Custom minutes") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("custom_limit_minutes_input")
      )

      if (isLockedByGroup) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = "Locked by ${existingLimit?.enforcedByName?.ifBlank { "Group Manager" } ?: "Group Manager"}",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
              text = if (!existingLimit?.requiredGroupTaskTitle.isNullOrBlank()) {
                "Complete your assigned task \"${existingLimit?.requiredGroupTaskTitle}\" in the Group tab to unlock this app. Supervised members cannot turn off or modify group screen time limits."
              } else {
                "Supervised members cannot manually turn off or modify screen time limits set by a Group Manager or Parent."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer
            )
          }
        }
        Button(
          onClick = onDismiss,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
        ) {
          Text("Close")
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (existingLimit != null) {
            OutlinedButton(
              onClick = onRemove,
              colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
              ),
              modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .testTag("turn_off_limit_button")
            ) {
              Text("Turn Off Limit")
            }
          }
          Button(
            onClick = {
              val finalMins = customMinutesText.toIntOrNull()?.coerceIn(1, 10080) ?: selectedMinutes
              onSave(finalMins, selectedPeriod)
            },
            modifier = Modifier
              .weight(1.4f)
              .height(50.dp)
              .testTag("save_screen_time_button")
          ) {
            Text("Save Limit")
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

/**
 * Full-screen inaccessible app lockout overlay shown when a limited app exceeds its screen time.
 * Cannot be dismissed via system back button; can only be turned off or adjusted inside Tasks+ Screen Time tab.
 */
@Composable
fun ScreenTimeLockoutOverlay(
  lockedApp: ScreenTimeLimitEntity,
  onManageInTasksPlus: () -> Unit,
  onGoToHomeScreen: (() -> Unit)? = null
) {
  val context = LocalContext.current
  BackHandler(enabled = true) {
    // Blocked intentionally: must exit to Home or manage inside Tasks+ Screen Time tab
  }

  val infiniteTransition = rememberInfiniteTransition(label = "lock_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.94f,
    targetValue = 1.08f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "shield_pulse_scale"
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f),
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f),
            MaterialTheme.colorScheme.surface
          )
        )
      )
      .clickable(enabled = true, onClick = {})
      .padding(24.dp)
      .testTag("screen_time_lockout_overlay"),
    contentAlignment = Alignment.Center
  ) {
    Card(
      shape = RoundedCornerShape(32.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
      ),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = CircleShape,
          modifier = Modifier
            .size(84.dp)
            .scale(pulseScale)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.Block,
              contentDescription = "App Locked",
              tint = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.size(44.dp)
            )
          }
        }

        Surface(
          color = MaterialTheme.colorScheme.error,
          shape = CircleShape
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Security,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onError,
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "TASKS+ SCREEN TIME GUARD",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onError,
              fontWeight = FontWeight.Bold
            )
          }
        }

        Text(
          text = "${lockedApp.appName} is Locked",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center
        )

        val lockSubtitle = when {
          lockedApp.parsedLockMode == com.example.data.model.RemoteLockMode.LOCK_AT_TIME && lockedApp.lockAtMillis > 0L -> {
            val fmt = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
            "Locked at ${fmt.format(java.util.Date(lockedApp.lockAtMillis))} by ${lockedApp.enforcedByName.ifBlank { "Group Manager" }}"
          }
          lockedApp.parsedLockMode == com.example.data.model.RemoteLockMode.LOCK_NOW && lockedApp.isGroupEnforced -> {
            "Remotely locked by ${lockedApp.enforcedByName.ifBlank { "Group Manager" }}"
          }
          else -> {
            "${formatMinutesShort(lockedApp.usedMinutes)} of ${formatMinutesShort(lockedApp.limitMinutes)} (${lockedApp.parsedPeriod.label}) used."
          }
        }

        Text(
          text = lockSubtitle,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center
        )

        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            if (lockedApp.requiredGroupTaskTitle.isNotBlank()) {
              Text(
                text = "Required Task Before Continuing:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = lockedApp.requiredGroupTaskTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
              )
            }
            Text(
              text = when {
                lockedApp.taskInstructions.isNotBlank() -> lockedApp.taskInstructions
                lockedApp.requiredGroupTaskTitle.isNotBlank() ->
                  "Open the Tasks+ Group tab, complete \"${lockedApp.requiredGroupTaskTitle}\", and submit proof (if required) to unlock ${lockedApp.appName}."
                lockedApp.isGroupEnforced ->
                  "Enforced by ${lockedApp.enforcedByName.ifBlank { "Group Manager" }}. Supervised members cannot disable this limit manually."
                else ->
                  "Direct dismissal is disabled to protect your focus. Limits can only be adjusted or turned off inside the Tasks+ Screen Time tab."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
          }
        }

        Button(
          onClick = onManageInTasksPlus,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("manage_in_tasksplus_button")
        ) {
          Icon(Icons.Default.LockClock, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            if (lockedApp.isGroupEnforced) {
              "Open Group Tab & Complete Task"
            } else {
              "Open Tasks+ Screen Time Tab"
            }
          )
        }

        OutlinedButton(
          onClick = {
            if (onGoToHomeScreen != null) {
              onGoToHomeScreen()
            } else {
              ScreenTimeEnforcementEngine.goHome(context)
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("lockout_go_home_button")
        ) {
          Icon(Icons.Default.Home, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Go to Home Screen")
        }
      }
    }
  }
}

fun formatMinutesShort(minutes: Int): String {
  if (minutes <= 0) return "0m"
  val h = minutes / 60
  val m = minutes % 60
  return when {
    h > 0 && m > 0 -> "${h}h ${m}m"
    h > 0 -> "${h}h"
    else -> "${m}m"
  }
}
