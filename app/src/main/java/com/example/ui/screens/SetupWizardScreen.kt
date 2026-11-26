package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.example.R
import com.example.data.model.TonalSpotPalette
import com.example.data.model.UserPreferencesState
import com.example.notifications.TaskReminderManager
import com.example.screentime.ScreenTimeEnforcementEngine
import com.example.ui.components.TaskPlusExpressiveLogo
import kotlinx.coroutines.delay

@Composable
fun SetupWizardScreen(
  preferences: UserPreferencesState,
  onToggleDynamicColor: (Boolean) -> Unit,
  onSelectPalette: (TonalSpotPalette) -> Unit,
  onFinishSetup: () -> Unit
) {
  val context = LocalContext.current
  var currentStep by remember { mutableIntStateOf(0) }
  var notifGranted by remember {
    mutableStateOf(TaskReminderManager.hasNotificationPermission(context))
  }
  var overlayGranted by remember {
    mutableStateOf(SystemPermissionsHelper.canDrawOverlays(context))
  }
  var accessibilityActive by remember {
    mutableStateOf(ScreenTimeEnforcementEngine.isAccessibilityServiceEnabled(context))
  }
  var batteryUnrestricted by remember {
    mutableStateOf(SystemPermissionsHelper.isIgnoringBatteryOptimizations(context))
  }
  var deviceAdminActive by remember {
    mutableStateOf(SystemPermissionsHelper.isDeviceAdminActive(context))
  }

  LaunchedEffect(currentStep) {
    while (true) {
      notifGranted = TaskReminderManager.hasNotificationPermission(context)
      overlayGranted = SystemPermissionsHelper.canDrawOverlays(context)
      accessibilityActive = ScreenTimeEnforcementEngine.isAccessibilityServiceEnabled(context)
      batteryUnrestricted = SystemPermissionsHelper.isIgnoringBatteryOptimizations(context)
      deviceAdminActive = SystemPermissionsHelper.isDeviceAdminActive(context)
      delay(1500L)
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    notifGranted = granted
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 560.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp)
          .navigationBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          // Top step indicator & Skip
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              repeat(3) { index ->
                val active = index == currentStep
                val indicatorWidth by animateDpAsState(
                  targetValue = if (active) 32.dp else 10.dp,
                  animationSpec = tween(durationMillis = 280),
                  label = "step_indicator_width"
                )
                Box(
                  modifier = Modifier
                    .height(8.dp)
                    .width(indicatorWidth)
                    .clip(CircleShape)
                    .background(
                      if (active) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
              }
            }
            TextButton(
              onClick = onFinishSetup,
              modifier = Modifier.testTag("setup_skip_button")
            ) {
              Text("Skip")
            }
          }

          // Hero illustration banner
          Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(176.dp)
          ) {
            Box(modifier = Modifier.fillMaxSize()) {
              Image(
                painter = painterResource(id = R.drawable.img_setup_hero_1794466713501),
                contentDescription = stringResource(R.string.cd_setup_hero),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
              TaskPlusExpressiveLogo(
                useDynamicColor = preferences.useDynamicColor,
                themeMode = preferences.themeMode,
                tonalSpotPalette = preferences.tonalSpotPalette,
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .padding(14.dp)
                  .size(56.dp)
              )
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                shape = CircleShape,
                modifier = Modifier
                  .align(Alignment.BottomStart)
                  .padding(14.dp)
              ) {
                Text(
                  text = "Tasks+ Setup • Step ${currentStep + 1} of 3",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
              }
            }
          }

          AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
              if (targetState > initialState) {
                (slideInHorizontally { it / 3 } + fadeIn()).togetherWith(
                  slideOutHorizontally { -it / 3 } + fadeOut()
                )
              } else {
                (slideInHorizontally { -it / 3 } + fadeIn()).togetherWith(
                  slideOutHorizontally { it / 3 } + fadeOut()
                )
              }
            },
            label = "setup_step_content"
          ) { step ->
            when (step) {
              0 -> SetupStepOneContent()
              1 -> SetupStepTwoContent()
              else -> SetupStepThreeContent(
                preferences = preferences,
                notifGranted = notifGranted,
                overlayGranted = overlayGranted,
                accessibilityActive = accessibilityActive,
                batteryUnrestricted = batteryUnrestricted,
                deviceAdminActive = deviceAdminActive,
                onRequestNotificationPermission = {
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  } else {
                    notifGranted = true
                  }
                },
                onOpenOverlayPermission = {
                  SystemPermissionsHelper.openOverlayPermissionSettings(context)
                  overlayGranted = SystemPermissionsHelper.canDrawOverlays(context)
                },
                onOpenAccessibility = {
                  SystemPermissionsHelper.openAccessibilitySettings(context)
                },
                onOpenBatteryOptimizations = {
                  SystemPermissionsHelper.openBatteryOptimizationSettings(context)
                  batteryUnrestricted =
                    SystemPermissionsHelper.isIgnoringBatteryOptimizations(context)
                },
                onOpenDeviceAdmin = {
                  SystemPermissionsHelper.openDeviceAdminSecuritySettings(context)
                },
                onToggleDynamicColor = onToggleDynamicColor,
                onSelectPalette = onSelectPalette
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Bottom Navigation Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (currentStep > 0) {
            FilledTonalButton(
              onClick = { currentStep -= 1 },
              modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .testTag("setup_back_button")
            ) {
              Text("Back")
            }
          }
          Button(
            onClick = {
              if (currentStep < 2) {
                currentStep += 1
              } else {
                onFinishSetup()
              }
            },
            modifier = Modifier
              .weight(2f)
              .height(52.dp)
              .testTag("setup_next_button")
          ) {
            Text(
              text = if (currentStep < 2) "Continue" else "Launch Tasks+",
              style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
              imageVector = if (currentStep < 2) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Check,
              contentDescription = null
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SetupStepOneContent() {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = "Welcome to Tasks+",
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onBackground
    )
    Text(
      text = "Tasks, Screen Time Guard, and Home Widgets in one place.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    WizardFeatureRow(
      icon = Icons.Default.AddCircle,
      title = "Bottom-Left + Button",
      subtitle = "Add tasks right above the To Do, Completed & Screen Time tabs."
    )
    WizardFeatureRow(
      icon = Icons.Default.TouchApp,
      title = "Hold to Edit & Copy Completed",
      subtitle = "Long-press tasks to edit/delete, or copy finished tasks."
    )
    WizardFeatureRow(
      icon = Icons.Default.Widgets,
      title = "Small & 4x4 Home Widgets",
      subtitle = "Swipe left/right on the small widget or scroll your 4x4 list."
    )
  }
}

@Composable
private fun SetupStepTwoContent() {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = "Alerts & Screen Time Guard",
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onBackground
    )
    Text(
      text = "Relentless reminders and un-bypassable app time limits.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    WizardFeatureRow(
      icon = Icons.Default.NotificationsActive,
      title = "10m, 30m, 1h, 2h Priority Alerts",
      subtitle = "Repeats on lock screen & banner until marked finished."
    )
    WizardFeatureRow(
      icon = Icons.Default.HourglassTop,
      title = "App Screen Time Shield",
      subtitle = "Set hourly, daily, or weekly limits synced across devices."
    )
    WizardFeatureRow(
      icon = Icons.Default.Autorenew,
      title = "Smart Recurring Schedules",
      subtitle = "Daily, weekly, monthly, or annual tasks auto-schedule."
    )
  }
}

@Composable
private fun SetupStepThreeContent(
  preferences: UserPreferencesState,
  notifGranted: Boolean,
  overlayGranted: Boolean,
  accessibilityActive: Boolean,
  batteryUnrestricted: Boolean,
  deviceAdminActive: Boolean,
  onRequestNotificationPermission: () -> Unit,
  onOpenOverlayPermission: () -> Unit,
  onOpenAccessibility: () -> Unit,
  onOpenBatteryOptimizations: () -> Unit,
  onOpenDeviceAdmin: () -> Unit,
  onToggleDynamicColor: (Boolean) -> Unit,
  onSelectPalette: (TonalSpotPalette) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      text = "Permissions & Theme",
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onBackground
    )

    PermissionSetupRow(
      icon = Icons.Default.NotificationsActive,
      title = "Priority Notifications",
      subtitle = if (notifGranted) "Granted" else "Required for lock-screen alerts",
      actionLabel = if (notifGranted) "Done" else "Allow",
      isDone = notifGranted,
      tag = "setup_grant_notif_button",
      onClick = onRequestNotificationPermission
    )

    PermissionSetupRow(
      icon = Icons.Default.Layers,
      title = "Screen Overlay (Optional)",
      subtitle = if (overlayGranted) "Enabled" else "For Screen Time lockout shield",
      actionLabel = if (overlayGranted) "Active" else "Open",
      isDone = false,
      tag = "setup_overlay_permission_button",
      onClick = onOpenOverlayPermission
    )

    PermissionSetupRow(
      icon = Icons.Default.AccessibilityNew,
      title = "Accessibility (Optional)",
      subtitle = if (accessibilityActive) "Active" else "Detects foreground app usage",
      actionLabel = if (accessibilityActive) "Active" else "Open",
      isDone = false,
      tag = "setup_accessibility_button",
      onClick = onOpenAccessibility
    )

    PermissionSetupRow(
      icon = Icons.Default.BatteryChargingFull,
      title = "Battery Optimizations Off",
      subtitle = if (batteryUnrestricted) "Unrestricted" else "Keeps interval timers reliable",
      actionLabel = if (batteryUnrestricted) "Active" else "Configure",
      isDone = false,
      tag = "setup_battery_opt_button",
      onClick = onOpenBatteryOptimizations
    )

    PermissionSetupRow(
      icon = Icons.Default.AdminPanelSettings,
      title = "Device Admin Guard",
      subtitle = if (deviceAdminActive) "Active" else "Protects Screen Time rules",
      actionLabel = if (deviceAdminActive) "Active" else "Activate",
      isDone = false,
      tag = "setup_device_admin_button",
      onClick = onOpenDeviceAdmin
    )

    Card(
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
      ),
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
          ) {
            TaskPlusExpressiveLogo(
              useDynamicColor = preferences.useDynamicColor,
              themeMode = preferences.themeMode,
              tonalSpotPalette = preferences.tonalSpotPalette,
              modifier = Modifier.size(44.dp)
            )
            Column {
              Text(
                text = "Change Based on Wallpaper",
                style = MaterialTheme.typography.titleSmall
              )
              Text(
                text = "Logo & UI adapt to Android 12+ wallpaper (Selected theme when off; Indigo Spot default)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          Switch(
            checked = preferences.useDynamicColor,
            onCheckedChange = onToggleDynamicColor
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          val isStaticPaletteActive =
            !preferences.useDynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
          TonalSpotPalette.entries.forEach { palette ->
            val selected = isStaticPaletteActive && preferences.tonalSpotPalette == palette
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  onSelectPalette(palette)
                }
                .padding(4.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(Color(palette.primaryHex))
                  .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    shape = CircleShape
                  ),
                contentAlignment = Alignment.Center
              ) {
                if (selected) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = palette.label,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(3.dp))
              Text(
                text = palette.label.substringBefore(" "),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PermissionSetupRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  actionLabel: String,
  isDone: Boolean,
  tag: String,
  onClick: () -> Unit
) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
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
          modifier = Modifier.size(22.dp)
        )
        Column {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      FilledTonalButton(
        onClick = onClick,
        enabled = !isDone,
        modifier = Modifier.testTag(tag)
      ) {
        Text(actionLabel)
      }
    }
  }
}

@Composable
private fun WizardFeatureRow(
  icon: ImageVector,
  title: String,
  subtitle: String
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.size(44.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer
          )
        }
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
