package com.example

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.TaskEntity
import com.example.data.model.TaskPriority
import com.example.notifications.TaskNotificationHelper
import com.example.screentime.GroupCloudBackgroundSyncEngine
import com.example.screentime.RealDeviceAppsScanner
import com.example.screentime.ScreenTimeEnforcementEngine
import com.example.screentime.TasksPlusDeviceAdminReceiver
import com.example.ui.screens.SystemPermissionsHelper
import com.example.update.AppUpdateManager
import com.example.widget.TasksWidgetUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.M])
class Android6CompatRobolectricTest {

  @Test
  fun `running on API 23 Marshmallow loads resources and initializes GroupCloudBackgroundSyncEngine without NoSuchMethodError`() {
    assertEquals(Build.VERSION_CODES.M, Build.VERSION.SDK_INT)
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("Tasks+", context.getString(R.string.app_name))

    // Trigger <clinit> of GroupCloudBackgroundSyncEngine (previously crashed on API 23 due to ConcurrentHashMap.newKeySet)
    assertEquals(false, GroupCloudBackgroundSyncEngine.isRemoteDeviceLocked)
    assertEquals(false, GroupCloudBackgroundSyncEngine.isSupervisedChildRole)
  }

  @Test
  fun `TaskNotificationHelper and AlarmBellPlayer work cleanly on API 23`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val helper = TaskNotificationHelper(context)
    helper.ensureNotificationChannels()
    assertTrue(helper.hasNotificationPermission())

    val now = System.currentTimeMillis()
    val task = TaskEntity(
      id = "api23_alarm_task",
      title = "Marshmallow Reminder",
      subtext = "Test wake-up alarm on API 23",
      scheduledAtMillis = now,
      maxCompletionMillis = now + 1_200_000L,
      reminderIntervalMinutes = 15,
      priority = TaskPriority.CRITICAL.name,
      ringAlarmBell = true
    )

    helper.scheduleTaskReminder(task, notificationsEnabled = true, fireInitialImmediately = false)
    val sent = helper.showPriorityTaskNotification(task, triggerAlarmBell = false)
    assertTrue(sent)
    helper.cancelTaskReminder(task.id)
  }

  @Test
  fun `AppUpdateManager reports install permission granted and resolves API 23 paths`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = AppUpdateManager(context)
    assertTrue(manager.canInstallUnknownApps())
    assertNotNull(manager.state.value)
  }

  @Test
  fun `ScreenTime and DeviceAdmin helpers work cleanly on API 23`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val component = TasksPlusDeviceAdminReceiver.getComponentName(context)
    assertNotNull(component)
    assertEquals(false, SystemPermissionsHelper.isDeviceAdminActive(context))

    // Should not throw on API 23
    val apps = RealDeviceAppsScanner.queryRealInstalledApps(context, includeSystemApps = false)
    assertNotNull(apps)
    ScreenTimeEnforcementEngine.detectForegroundAppViaUsageStats(context)
    TasksWidgetUpdater.updateAllWidgets(context)
  }

  @Test
  fun `RemoteLockMode exact time, lock now, usage limit and lock beep notification work on API 23`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val helper = TaskNotificationHelper(context)
    val now = 1_762_900_000_000L

    val lockNow = com.example.data.model.ScreenTimeLimitEntity(
      packageName = "com.example.game",
      appName = "Game",
      limitMinutes = 30,
      usedMinutes = 0,
      isEnabled = true,
      isGroupEnforced = true,
      lockMode = com.example.data.model.RemoteLockMode.LOCK_NOW.name,
      taskInstructions = "Finish reading chapter 3 first.",
      playBeepOnLock = true
    )
    assertTrue(lockNow.isLimitReachedAt(now))

    val lockAtFuture = lockNow.copy(
      lockMode = com.example.data.model.RemoteLockMode.LOCK_AT_TIME.name,
      lockAtMillis = now + 120_000L
    )
    assertEquals(false, lockAtFuture.isLimitReachedAt(now))
    assertTrue(lockAtFuture.isLimitReachedAt(now + 120_000L))
    assertTrue(lockAtFuture.isLimitReachedAt(now + 125_000L))

    val usageLimit = lockNow.copy(
      lockMode = com.example.data.model.RemoteLockMode.USAGE_LIMIT.name,
      limitMinutes = 15,
      usedMinutes = 14
    )
    assertEquals(false, usageLimit.isLimitReachedAt(now))
    assertTrue(usageLimit.copy(usedMinutes = 15).isLimitReachedAt(now))

    helper.scheduleRemoteAppLockAlarm(lockAtFuture)
    val notified = helper.showRemoteAppLockNotification(lockNow, isScheduledPreview = false, playBeep = true)
    assertTrue(notified)
    com.example.notifications.AlarmBellPlayer.playShortNotificationBeep(context)
    helper.cancelRemoteAppLockAlarm(lockAtFuture.packageName)
  }

  @Test
  fun `Soft Burst and Clover adaptive icon drawables and Indigo Spot default palette load cleanly on API 23`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertNotNull(context.getDrawable(R.drawable.ic_launcher_background))
    assertNotNull(context.getDrawable(R.drawable.ic_launcher_foreground))
    assertNotNull(context.getDrawable(R.drawable.ic_launcher_monochrome))

    // Verify all 4 themed adaptive icon drawable sets load cleanly on API 23
    listOf(
      R.drawable.ic_launcher_indigo_background,
      R.drawable.ic_launcher_indigo_foreground,
      R.drawable.ic_launcher_teal_background,
      R.drawable.ic_launcher_teal_foreground,
      R.drawable.ic_launcher_violet_background,
      R.drawable.ic_launcher_violet_foreground,
      R.drawable.ic_launcher_coral_background,
      R.drawable.ic_launcher_coral_foreground
    ).forEach { resId ->
      assertNotNull(context.getDrawable(resId))
    }

    val indigoLight = com.example.ui.components.ExpressiveLogoPalettes.rolesForPalette(
      com.example.data.model.TonalSpotPalette.INDIGO,
      darkTheme = false
    )
    assertEquals(0xFF4559A9L, com.example.data.model.TonalSpotPalette.INDIGO.primaryHex)
    assertNotNull(indigoLight)

    // Verify all 4 TonalSpotPalette logo roles resolve in both light and dark modes
    com.example.data.model.TonalSpotPalette.entries.forEach { palette ->
      assertNotNull(com.example.ui.components.ExpressiveLogoPalettes.rolesForPalette(palette, darkTheme = false))
      assertNotNull(com.example.ui.components.ExpressiveLogoPalettes.rolesForPalette(palette, darkTheme = true))
    }
  }

  @Test
  fun `LauncherIconThemeSwitcher resolves dynamic vs themed aliases and switches safely on API 23`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // API 31+ with wallpaper dynamic color ON -> Dynamic alias
    assertEquals(
      "com.example.MainActivityDynamicAlias",
      com.example.ui.components.LauncherIconThemeSwitcher.targetAliasClassName(
        useDynamicColor = true,
        tonalSpotPalette = com.example.data.model.TonalSpotPalette.EMERALD,
        sdkInt = Build.VERSION_CODES.S
      )
    )

    // API 31+ with wallpaper dynamic color OFF -> Selected theme alias
    assertEquals(
      "com.example.MainActivityIndigoAlias",
      com.example.ui.components.LauncherIconThemeSwitcher.targetAliasClassName(
        useDynamicColor = false,
        tonalSpotPalette = com.example.data.model.TonalSpotPalette.INDIGO,
        sdkInt = Build.VERSION_CODES.S
      )
    )
    assertEquals(
      "com.example.MainActivityTealAlias",
      com.example.ui.components.LauncherIconThemeSwitcher.targetAliasClassName(
        useDynamicColor = false,
        tonalSpotPalette = com.example.data.model.TonalSpotPalette.EMERALD,
        sdkInt = Build.VERSION_CODES.S
      )
    )
    assertEquals(
      "com.example.MainActivityVioletAlias",
      com.example.ui.components.LauncherIconThemeSwitcher.targetAliasClassName(
        useDynamicColor = false,
        tonalSpotPalette = com.example.data.model.TonalSpotPalette.TEAL,
        sdkInt = Build.VERSION_CODES.S
      )
    )
    assertEquals(
      "com.example.MainActivityCoralAlias",
      com.example.ui.components.LauncherIconThemeSwitcher.targetAliasClassName(
        useDynamicColor = false,
        tonalSpotPalette = com.example.data.model.TonalSpotPalette.ROSE,
        sdkInt = Build.VERSION_CODES.S
      )
    )

    // API 23 (Android 6) even when useDynamicColor = true -> falls back to selected theme alias
    assertEquals(
      "com.example.MainActivityCoralAlias",
      com.example.ui.components.LauncherIconThemeSwitcher.targetAliasClassName(
        useDynamicColor = true,
        tonalSpotPalette = com.example.data.model.TonalSpotPalette.ROSE,
        sdkInt = Build.VERSION_CODES.M
      )
    )

    // Syncing launcher icon on API 23 does not throw
    com.example.ui.components.LauncherIconThemeSwitcher.syncLauncherIcon(
      context = context,
      useDynamicColor = false,
      tonalSpotPalette = com.example.data.model.TonalSpotPalette.EMERALD
    )
    com.example.ui.components.LauncherIconThemeSwitcher.syncLauncherIcon(
      context = context,
      useDynamicColor = true,
      tonalSpotPalette = com.example.data.model.TonalSpotPalette.INDIGO
    )
  }

  @Test
  fun `ThermometerBitmapRenderer creates 1-to-5 green-to-red pictures and attaches to notification on API 23`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    for (level in 1..5) {
      val banner = com.example.notifications.ThermometerBitmapRenderer.renderNotificationPicture(
        urgencyLevel = level,
        taskTitle = "Level $level Task",
        dueSummary = "Due Soon"
      )
      assertEquals(960, banner.width)
      assertEquals(300, banner.height)

      val largeIcon = com.example.notifications.ThermometerBitmapRenderer.renderLargeIconBitmap(level)
      assertEquals(128, largeIcon.width)
      assertEquals(128, largeIcon.height)
    }

    // Verify UrgencyLevelMeta mapping 1..5 -> TaskPriority & TaskEntity resolution
    assertEquals(TaskPriority.LOW, TaskPriority.fromUrgencyLevel(1))
    assertEquals(TaskPriority.CRITICAL, TaskPriority.fromUrgencyLevel(5))

    val task = TaskEntity(
      id = "thermometer_notif_test",
      title = "Check thermometer alert",
      subtext = "Visual thermometer scale",
      urgencyLevel = 5,
      priority = TaskPriority.CRITICAL.name,
      reminderIntervalMinutes = 15
    )
    assertEquals(5, task.resolvedUrgencyLevel)

    val helper = TaskNotificationHelper(context)
    assertTrue(helper.showPriorityTaskNotification(task, triggerAlarmBell = false))

    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val shadowNm = org.robolectric.Shadows.shadowOf(nm)
    val posted = shadowNm.allNotifications.lastOrNull()
    assertNotNull(posted)
    assertNotNull(posted!!.extras.getParcelable<android.graphics.Bitmap>(android.app.Notification.EXTRA_PICTURE))
  }
}
