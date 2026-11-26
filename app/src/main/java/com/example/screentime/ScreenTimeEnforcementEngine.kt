package com.example.screentime

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.MainActivity
import com.example.data.local.PreferencesRepository
import com.example.data.local.TaskDatabase
import com.example.data.model.InstalledAppInfo
import com.example.data.model.RemoteLockMode
import com.example.data.model.ScreenTimeLimitEntity
import com.example.data.model.ScreenTimePeriod
import com.example.notifications.TaskNotificationHelper
import com.example.ui.screens.ScreenTimeLockoutOverlay
import com.example.ui.theme.TasksPlusTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object RealDeviceAppsScanner {
  private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

  fun getAppIconBitmap(context: Context, packageName: String): ImageBitmap? {
    iconCache[packageName]?.let { return it }
    return try {
      val drawable = context.packageManager.getApplicationIcon(packageName)
      val bmp = drawableToBitmap(drawable, 96).asImageBitmap()
      iconCache[packageName] = bmp
      bmp
    } catch (_: Exception) {
      null
    }
  }

  private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
      return drawable.bitmap
    }
    val w = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(sizePx)
    val h = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(sizePx)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
  }

  /**
   * Queries ONLY real applications installed on the user's device.
   * Zero hardcoded or fake apps.
   */
  fun queryRealInstalledApps(
    context: Context,
    includeSystemApps: Boolean = false
  ): List<InstalledAppInfo> {
    val pm = context.packageManager
    val selfPkg = context.packageName
    val results = linkedMapOf<String, InstalledAppInfo>()

    // 1. Query LauncherApps for all launchable activities across user profiles
    try {
      val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
      val activities = launcherApps?.getActivityList(null, Process.myUserHandle()).orEmpty()
      for (info in activities) {
        val pkg = info.applicationInfo?.packageName ?: continue
        if (pkg == selfPkg) continue
        val label = info.label?.toString()?.trim().orEmpty().ifBlank { pkg }
        val appInfo = info.applicationInfo
        val isSys = appInfo != null &&
          (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
          (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        results[pkg] = InstalledAppInfo(
          packageName = pkg,
          appName = label,
          categoryHint = if (isSys) "System App" else "Installed App"
        )
      }
    } catch (_: Exception) {
    }

    // 2. Query PackageManager for all ACTION_MAIN + CATEGORY_LAUNCHER activities
    try {
      val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
      }
      val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PackageManager.MATCH_ALL
      } else {
        0
      }
      val resolved = pm.queryIntentActivities(mainIntent, flags)
      for (ri in resolved) {
        val pkg = ri.activityInfo?.packageName ?: continue
        if (pkg == selfPkg || results.containsKey(pkg)) continue
        val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg }
        val appInfo = ri.activityInfo?.applicationInfo
        val isSys = appInfo != null &&
          (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
          (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        results[pkg] = InstalledAppInfo(
          packageName = pkg,
          appName = label,
          categoryHint = if (isSys) "System App" else "Installed App"
        )
      }
    } catch (_: Exception) {
    }

    // 3. Query getInstalledApplications to catch any user-installed apps or (when toggled) system apps
    try {
      val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
      for (appInfo in installed) {
        val pkg = appInfo.packageName ?: continue
        if (pkg == selfPkg || results.containsKey(pkg)) continue
        val isUserInstalled = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
          (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val hasLaunchIntent = pm.getLaunchIntentForPackage(pkg) != null
        if (isUserInstalled || hasLaunchIntent || includeSystemApps) {
          val label = pm.getApplicationLabel(appInfo).toString().trim().ifBlank { pkg }
          // Skip raw overlay/framework packages without a launch intent unless includeSystemApps is true
          if (!includeSystemApps && !hasLaunchIntent && label == pkg) continue
          results[pkg] = InstalledAppInfo(
            packageName = pkg,
            appName = label,
            categoryHint = if (isUserInstalled) "User App" else "System App"
          )
        }
      }
    } catch (_: Exception) {
    }

    return results.values.sortedWith(
      compareBy<InstalledAppInfo> { it.categoryHint != "User App" && it.categoryHint != "Installed App" }
        .thenBy { it.appName.lowercase() }
    )
  }
}

object ScreenTimeEnforcementEngine {
  private const val PREFS_TRACKER = "tasksplus_screentime_tracker"
  const val EXTRA_OPEN_SCREEN_TIME_TAB = "extra_open_screen_time_tab"
  const val EXTRA_OPEN_GROUP_TAB = "extra_open_group_tab"
  const val EXTRA_LOCKED_PACKAGE = "extra_locked_package"
  const val EXTRA_LOCKED_APP_NAME = "extra_locked_app_name"
  const val EXTRA_LOCKED_USED_MINS = "extra_locked_used_mins"
  const val EXTRA_LOCKED_LIMIT_MINS = "extra_locked_limit_mins"
  const val EXTRA_LOCKED_PERIOD = "extra_locked_period"
  const val EXTRA_IS_GROUP_ENFORCED = "extra_is_group_enforced"
  const val EXTRA_ENFORCED_BY_NAME = "extra_enforced_by_name"
  const val EXTRA_REQUIRED_TASK_TITLE = "extra_required_task_title"
  const val EXTRA_REQUIRED_TASK_INSTRUCTIONS = "extra_required_task_instructions"
  const val EXTRA_LOCK_MODE = "extra_lock_mode"
  const val EXTRA_LOCK_AT_MILLIS = "extra_lock_at_millis"

  @Volatile
  var currentForegroundPackage: String? = null

  @Volatile
  var lastAccessibilityEventMillis: Long = 0L

  @Volatile
  var isFocusAuraShieldActive: Boolean = false

  @Volatile
  private var lastForegroundTickAtMillis: Long = 0L

  private val triggeredLockNotificationKeys =
    Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

  private var backgroundMonitorJob: Job? = null
  private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  fun clearTriggeredNotificationKey(packageName: String) {
    triggeredLockNotificationKeys.removeAll { it.startsWith("${packageName}::") }
  }

  fun hasUsageStatsPermission(context: Context): Boolean {
    return try {
      val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
      val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
          AppOpsManager.OPSTR_GET_USAGE_STATS,
          Process.myUid(),
          context.packageName
        )
      } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
          AppOpsManager.OPSTR_GET_USAGE_STATS,
          Process.myUid(),
          context.packageName
        )
      }
      mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
      false
    }
  }

  fun isAccessibilityServiceEnabled(context: Context): Boolean {
    return try {
      val expected = ComponentName(context, ScreenTimeAccessibilityService::class.java)
      val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
      ).orEmpty()
      enabledServices.split(':').any { flat ->
        ComponentName.unflattenFromString(flat) == expected
      }
    } catch (_: Exception) {
      false
    }
  }

  fun openUsageAccessSettings(context: Context) {
    try {
      val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        data = android.net.Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (_: Exception) {
      try {
        val fallback = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(fallback)
      } catch (_: Exception) {
      }
    }
  }

  /**
   * Starts a continuous background enforcement monitor that works alongside
   * ScreenTimeAccessibilityService and UsageStatsManager.
   */
  fun ensureBackgroundMonitorRunning(context: Context) {
    val appContext = context.applicationContext
    GroupCloudBackgroundSyncEngine.ensureStarted(appContext)
    if (backgroundMonitorJob?.isActive == true) return
    backgroundMonitorJob = engineScope.launch {
      while (isActive) {
        try {
          syncUsageAndEnforceForeground(appContext)
        } catch (_: Exception) {
        }
        delay(1000L)
      }
    }
  }

  fun periodStartTimeMillis(period: ScreenTimePeriod, now: Long = System.currentTimeMillis()): Long {
    return when (period) {
      ScreenTimePeriod.HOURLY -> now - 3_600_000L
      ScreenTimePeriod.DAILY -> {
        Calendar.getInstance().apply {
          timeInMillis = now
          set(Calendar.HOUR_OF_DAY, 0)
          set(Calendar.MINUTE, 0)
          set(Calendar.SECOND, 0)
          set(Calendar.MILLISECOND, 0)
        }.timeInMillis
      }
      ScreenTimePeriod.WEEKLY -> now - 7 * 86_400_000L
    }
  }

  /**
   * Computes real foreground usage minutes from Android's UsageStatsManager for the given package
   * and period window, combined with our direct foreground second counter.
   */
  fun computeRealUsedMinutes(
    context: Context,
    packageName: String,
    period: ScreenTimePeriod,
    existingUsedMinutes: Int,
    assignedAtMillis: Long = 0L,
    baselineStatsMinutes: Int = 0
  ): Int {
    val now = System.currentTimeMillis()
    val startMs = periodStartTimeMillis(period, now)
    var usageStatsMs = 0L

    if (hasUsageStatsPermission(context)) {
      try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usm != null) {
          val stats = usm.queryAndAggregateUsageStats(startMs, now)
          usageStatsMs = stats[packageName]?.totalTimeInForeground ?: 0L
        }
      } catch (_: Exception) {
      }
    }

    val rawStatsMinutes = (usageStatsMs / 60_000L).toInt()
    val effectiveStatsMinutes = if (assignedAtMillis > startMs && baselineStatsMinutes > 0) {
      (rawStatsMinutes - baselineStatsMinutes).coerceAtLeast(0)
    } else {
      rawStatsMinutes
    }

    val trackedSeconds = getTrackedForegroundSeconds(context, packageName, period, startMs)
    val fromTrackedMinutes = (trackedSeconds / 60L).toInt()
    return maxOf(effectiveStatsMinutes, fromTrackedMinutes, existingUsedMinutes)
  }

  fun recordForegroundSeconds(
    context: Context,
    packageName: String,
    period: ScreenTimePeriod,
    deltaSeconds: Long
  ): Int {
    val now = System.currentTimeMillis()
    val windowStart = periodStartTimeMillis(period, now)
    val prefs = context.getSharedPreferences(PREFS_TRACKER, Context.MODE_PRIVATE)
    val windowKey = "win_${packageName}_${period.name}"
    val secKey = "sec_${packageName}_${period.name}"

    val savedWindow = prefs.getLong(windowKey, 0L)
    val previousSeconds = if (savedWindow < windowStart - 60_000L) 0L else prefs.getLong(secKey, 0L)
    val newSeconds = previousSeconds + deltaSeconds
    prefs.edit()
      .putLong(windowKey, windowStart)
      .putLong(secKey, newSeconds)
      .apply()
    return (newSeconds / 60L).toInt()
  }

  fun resetTrackedForegroundSeconds(context: Context, packageName: String) {
    val prefs = context.getSharedPreferences(PREFS_TRACKER, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    ScreenTimePeriod.entries.forEach { period ->
      editor.remove("win_${packageName}_${period.name}")
      editor.remove("sec_${packageName}_${period.name}")
    }
    editor.apply()
  }

  private fun getTrackedForegroundSeconds(
    context: Context,
    packageName: String,
    period: ScreenTimePeriod,
    windowStart: Long
  ): Long {
    val prefs = context.getSharedPreferences(PREFS_TRACKER, Context.MODE_PRIVATE)
    val savedWindow = prefs.getLong("win_${packageName}_${period.name}", 0L)
    if (savedWindow < windowStart - 60_000L) return 0L
    return prefs.getLong("sec_${packageName}_${period.name}", 0L)
  }

  fun detectForegroundAppViaUsageStats(context: Context): String? {
    if (!hasUsageStatsPermission(context)) return null
    return try {
      val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        ?: return null
      val now = System.currentTimeMillis()
      val events = usm.queryEvents(now - 600_000L, now)
      val event = UsageEvents.Event()
      val activePackages = linkedMapOf<String, Long>()
      var latestResumedPkg: String? = null
      var latestResumedTime = 0L

      while (events.hasNextEvent()) {
        events.getNextEvent(event)
        val pkg = event.packageName ?: continue
        if (pkg == "com.android.systemui" || pkg.contains("inputmethod", ignoreCase = true)) {
          continue
        }
        @Suppress("DEPRECATION")
        val isResumed = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
          event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
        @Suppress("DEPRECATION")
        val isPaused = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
          event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) ||
          event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND

        if (isResumed) {
          activePackages[pkg] = event.timeStamp
          if (event.timeStamp >= latestResumedTime) {
            latestResumedTime = event.timeStamp
            latestResumedPkg = pkg
          }
        } else if (isPaused) {
          val prev = activePackages[pkg] ?: 0L
          if (event.timeStamp >= prev) {
            activePackages.remove(pkg)
          }
        }
      }

      val stillActive = activePackages.maxByOrNull { it.value }?.key
      if (!stillActive.isNullOrBlank()) {
        return stillActive
      }
      if (!latestResumedPkg.isNullOrBlank()) {
        return latestResumedPkg
      }

      // Fallback on devices/API levels where queryEvents buffer wrapped or wasn't populated yet
      val dailyStats = usm.queryUsageStats(
        UsageStatsManager.INTERVAL_DAILY,
        now - 3_600_000L,
        now
      ).orEmpty()
      dailyStats
        .filter { !it.packageName.isNullOrBlank() && it.lastTimeUsed > now - 120_000L }
        .maxByOrNull { it.lastTimeUsed }
        ?.packageName
    } catch (_: Exception) {
      null
    }
  }

  private fun resolveActiveForegroundPackage(context: Context): String? {
    val now = System.currentTimeMillis()
    val a11yAlive = isAccessibilityServiceEnabled(context) &&
      (now - lastAccessibilityEventMillis) < 12_000L
    return if (a11yAlive && !currentForegroundPackage.isNullOrBlank()) {
      currentForegroundPackage
    } else {
      detectForegroundAppViaUsageStats(context) ?: currentForegroundPackage
    }
  }

  suspend fun syncUsageAndEnforceForeground(context: Context) {
    val now = System.currentTimeMillis()
    val fgPkg = resolveActiveForegroundPackage(context)

    if (GroupCloudBackgroundSyncEngine.isRemoteDeviceLocked) {
      TasksPlusDeviceAdminReceiver.lockDeviceNow(context)
      if (fgPkg != null && fgPkg != context.packageName) {
        enforceBlockNow(
          context,
          ScreenTimeLimitEntity(
            packageName = fgPkg,
            appName = "Device Remotely Locked by Group Manager",
            limitMinutes = 1,
            usedMinutes = 1,
            periodType = ScreenTimePeriod.DAILY.name,
            isEnabled = true,
            isGroupEnforced = true,
            enforcedByName = "Group Manager",
            lockMode = RemoteLockMode.LOCK_NOW.name,
            taskInstructions = GroupCloudBackgroundSyncEngine.remoteLockReason
          )
        )
      }
      return
    }

    if (isFocusAuraShieldActive && fgPkg != null && fgPkg != context.packageName) {
      val db = TaskDatabase.getInstance(context)
      val monitoredLimit = db.screenTimeDao().getLimitByPackage(fgPkg)
      if (monitoredLimit != null && monitoredLimit.isEnabled) {
        enforceBlockNow(
          context,
          monitoredLimit.copy(
            lockMode = RemoteLockMode.LOCK_NOW.name,
            taskInstructions = "Focus Aura Active — Place your phone back face-down or disarm Focus Aura in Tasks+ to unlock ${monitoredLimit.appName}."
          )
        )
        return
      }
    }

    val dao = TaskDatabase.getInstance(context).screenTimeDao()
    val limits = dao.getAllLimitsOnce().filter { it.isEnabled }
    if (limits.isEmpty()) {
      SystemOverlayBlocker.dismissOverlay(context)
      return
    }

    // Advance direct foreground seconds counter if a limited app is currently in the foreground
    val elapsedMs = now - lastForegroundTickAtMillis
    val addSecs = if (lastForegroundTickAtMillis == 0L) 1L else (elapsedMs / 1000L).coerceIn(0L, 4L)
    if (addSecs > 0L) {
      lastForegroundTickAtMillis = now
    }

    for (limit in limits) {
      val trackedMins = if (
        addSecs > 0L &&
        fgPkg == limit.packageName &&
        fgPkg != context.packageName &&
        limit.parsedLockMode == RemoteLockMode.USAGE_LIMIT
      ) {
        recordForegroundSeconds(
          context = context,
          packageName = limit.packageName,
          period = limit.parsedPeriod,
          deltaSeconds = addSecs
        )
      } else {
        limit.usedMinutes
      }

      val computedUsed = if (limit.parsedLockMode == RemoteLockMode.USAGE_LIMIT) {
        computeRealUsedMinutes(
          context = context,
          packageName = limit.packageName,
          period = limit.parsedPeriod,
          existingUsedMinutes = maxOf(limit.usedMinutes, trackedMins),
          assignedAtMillis = limit.assignedAtMillis,
          baselineStatsMinutes = limit.baselineStatsMinutes
        )
      } else {
        limit.usedMinutes
      }

      val updatedLimit = if (computedUsed != limit.usedMinutes) {
        val copy = limit.copy(
          usedMinutes = computedUsed,
          updatedAtMillis = now
        )
        dao.upsertLimit(copy)
        copy
      } else {
        limit
      }

      val reached = updatedLimit.isLimitReachedAt(now)
      if (reached) {
        val triggerKey = "${updatedLimit.packageName}::${updatedLimit.lockMode}::${updatedLimit.lockAtMillis}::${updatedLimit.limitMinutes}"
        if (triggeredLockNotificationKeys.add(triggerKey) && updatedLimit.isGroupEnforced) {
          TaskNotificationHelper(context).showRemoteAppLockNotification(
            limit = updatedLimit,
            isScheduledPreview = false,
            playBeep = updatedLimit.playBeepOnLock
          )
        }
        if (fgPkg != null && fgPkg == updatedLimit.packageName && fgPkg != context.packageName) {
          enforceBlockNow(context, updatedLimit)
        }
      }
    }

    if (fgPkg == context.packageName) {
      SystemOverlayBlocker.dismissOverlay(context)
    }
  }

  fun enforceBlockNow(context: Context, limit: ScreenTimeLimitEntity) {
    // 1. Show system window overlay if SYSTEM_ALERT_WINDOW is granted
    if (Settings.canDrawOverlays(context)) {
      SystemOverlayBlocker.showBlockingOverlay(context, limit)
    }
    // 2. Also launch ScreenTimeLockActivity right on top of the blocked app
    val lockIntent = Intent(context, ScreenTimeLockActivity::class.java).apply {
      addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_CLEAR_TOP or
          Intent.FLAG_ACTIVITY_SINGLE_TOP
      )
      putExtra(EXTRA_LOCKED_PACKAGE, limit.packageName)
      putExtra(EXTRA_LOCKED_APP_NAME, limit.appName)
      putExtra(EXTRA_LOCKED_USED_MINS, limit.usedMinutes)
      putExtra(EXTRA_LOCKED_LIMIT_MINS, limit.limitMinutes)
      putExtra(EXTRA_LOCKED_PERIOD, limit.parsedPeriod.name)
      putExtra(EXTRA_IS_GROUP_ENFORCED, limit.isGroupEnforced)
      putExtra(EXTRA_ENFORCED_BY_NAME, limit.enforcedByName)
      putExtra(EXTRA_REQUIRED_TASK_TITLE, limit.requiredGroupTaskTitle)
      putExtra(EXTRA_REQUIRED_TASK_INSTRUCTIONS, limit.taskInstructions)
      putExtra(EXTRA_LOCK_MODE, limit.lockMode)
      putExtra(EXTRA_LOCK_AT_MILLIS, limit.lockAtMillis)
    }
    try {
      context.startActivity(lockIntent)
    } catch (_: Exception) {
    }
  }

  fun goHome(context: Context) {
    try {
      val homeIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(homeIntent)
    } catch (_: Exception) {
    }
  }

  fun openTasksPlusScreenTimeTab(context: Context) {
    SystemOverlayBlocker.dismissOverlay(context)
    try {
      val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_OPEN_SCREEN_TIME_TAB, true)
      }
      context.startActivity(intent)
    } catch (_: Exception) {
    }
  }

  fun openTasksPlusGroupTab(context: Context) {
    SystemOverlayBlocker.dismissOverlay(context)
    try {
      val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_OPEN_GROUP_TAB, true)
      }
      context.startActivity(intent)
    } catch (_: Exception) {
    }
  }
}

/**
 * Draws a real Android WindowManager TYPE_APPLICATION_OVERLAY directly over blocked apps
 * when the "Display over other apps" permission is granted.
 */
object SystemOverlayBlocker {
  private var overlayView: View? = null
  private var activeBlockedPackage: String? = null

  fun showBlockingOverlay(context: Context, limit: ScreenTimeLimitEntity) {
    if (!Settings.canDrawOverlays(context)) return
    val appContext = context.applicationContext
    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    mainHandler.post {
      try {
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (overlayView != null && activeBlockedPackage == limit.packageName) {
          return@post
        }
        dismissOverlayInternal(wm)

        val container = LinearLayout(appContext).apply {
          orientation = LinearLayout.VERTICAL
          gravity = Gravity.CENTER
          setBackgroundColor(AndroidColor.parseColor("#F214121F"))
          val pad = dpToPx(appContext, 28)
          setPadding(pad, pad, pad, pad)
        }

        val card = LinearLayout(appContext).apply {
          orientation = LinearLayout.VERTICAL
          gravity = Gravity.CENTER_HORIZONTAL
          background = GradientDrawable().apply {
            setColor(AndroidColor.parseColor("#252136"))
            cornerRadius = dpToPx(appContext, 28).toFloat()
            setStroke(dpToPx(appContext, 1), AndroidColor.parseColor("#EF4444"))
          }
          val pad = dpToPx(appContext, 24)
          setPadding(pad, pad, pad, pad)
        }

        val badgeView = TextView(appContext).apply {
          text = "TASKS+ SCREEN TIME GUARD"
          setTextColor(AndroidColor.parseColor("#FCA5A5"))
          setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
          setTypeface(typeface, Typeface.BOLD)
          gravity = Gravity.CENTER
        }

        val titleView = TextView(appContext).apply {
          text = "${limit.appName} is Locked"
          setTextColor(AndroidColor.WHITE)
          setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
          setTypeface(typeface, Typeface.BOLD)
          gravity = Gravity.CENTER
          setPadding(0, dpToPx(appContext, 10), 0, dpToPx(appContext, 6))
        }

        val subView = TextView(appContext).apply {
          text = buildString {
            when {
              limit.requiredGroupTaskTitle.isNotBlank() -> {
                append("Complete task \"${limit.requiredGroupTaskTitle}\" in Tasks+ before continuing to use ${limit.appName}.")
              }
              limit.parsedLockMode == RemoteLockMode.LOCK_AT_TIME && limit.lockAtMillis > 0L -> {
                val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                append("Locked at ${fmt.format(Date(limit.lockAtMillis))} by ${limit.enforcedByName.ifBlank { "Group Manager" }}.")
              }
              limit.parsedLockMode == RemoteLockMode.LOCK_NOW && limit.isGroupEnforced -> {
                append("Locked remotely by ${limit.enforcedByName.ifBlank { "Group Manager" }}.")
              }
              else -> {
                append("${limit.usedMinutes}m of ${limit.limitMinutes}m (${limit.parsedPeriod.label}) used.")
              }
            }
            if (limit.taskInstructions.isNotBlank()) {
              append("\n\nInstructions: ${limit.taskInstructions}")
            } else if (!limit.isGroupEnforced) {
              append("\nLimits can only be turned off inside the Tasks+ Screen Time tab.")
            }
          }
          setTextColor(AndroidColor.parseColor("#D1C4E9"))
          setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
          gravity = Gravity.CENTER
          setPadding(0, 0, 0, dpToPx(appContext, 18))
        }

        val openTasksBtn = Button(appContext).apply {
          text = if (limit.isGroupEnforced) {
            "Open Tasks+ Group Tab to Unlock"
          } else {
            "Manage in Tasks+ Screen Time"
          }
          setTextColor(AndroidColor.WHITE)
          isAllCaps = false
          background = GradientDrawable().apply {
            setColor(AndroidColor.parseColor("#4F46E5"))
            cornerRadius = dpToPx(appContext, 16).toFloat()
          }
          setOnClickListener {
            if (limit.isGroupEnforced) {
              ScreenTimeEnforcementEngine.openTasksPlusGroupTab(appContext)
            } else {
              ScreenTimeEnforcementEngine.openTasksPlusScreenTimeTab(appContext)
            }
          }
        }

        val homeBtn = Button(appContext).apply {
          text = "Go to Home Screen"
          setTextColor(AndroidColor.parseColor("#E5E0FF"))
          isAllCaps = false
          background = GradientDrawable().apply {
            setColor(AndroidColor.parseColor("#3B3554"))
            cornerRadius = dpToPx(appContext, 16).toFloat()
          }
          setOnClickListener {
            dismissOverlay(appContext)
            ScreenTimeEnforcementEngine.goHome(appContext)
          }
        }

        val btnParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          dpToPx(appContext, 48)
        ).apply {
          topMargin = dpToPx(appContext, 10)
        }

        card.addView(badgeView)
        card.addView(titleView)
        card.addView(subView)
        card.addView(openTasksBtn, btnParams)
        card.addView(homeBtn, btnParams)

        container.addView(
          card,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          )
        )

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
          @Suppress("DEPRECATION")
          WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
          WindowManager.LayoutParams.MATCH_PARENT,
          WindowManager.LayoutParams.MATCH_PARENT,
          layoutType,
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
          PixelFormat.TRANSLUCENT
        ).apply {
          gravity = Gravity.CENTER
        }

        wm.addView(container, params)
        overlayView = container
        activeBlockedPackage = limit.packageName
      } catch (_: Exception) {
      }
    }
  }

  fun dismissOverlay(context: Context) {
    val appContext = context.applicationContext
    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    mainHandler.post {
      val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
      dismissOverlayInternal(wm)
    }
  }

  private fun dismissOverlayInternal(wm: WindowManager) {
    val current = overlayView
    if (current != null) {
      try {
        wm.removeViewImmediate(current)
      } catch (_: Exception) {
      }
      overlayView = null
      activeBlockedPackage = null
    }
  }

  private fun dpToPx(context: Context, dp: Int): Int {
    return TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      dp.toFloat(),
      context.resources.displayMetrics
    ).toInt()
  }
}

/**
 * Full-screen Lockout Activity launched immediately over any blocked app when its Screen Time
 * limit is exceeded.
 */
class ScreenTimeLockActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (intent?.getBooleanExtra(ScreenTimeEnforcementEngine.EXTRA_IS_GROUP_ENFORCED, false) == true ||
      GroupCloudBackgroundSyncEngine.isRemoteDeviceLocked
    ) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
      } else {
        @Suppress("DEPRECATION")
        window.addFlags(
          WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
      }
    }
    enableEdgeToEdge()
    renderLockoutContent(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    renderLockoutContent(intent)
  }

  private fun renderLockoutContent(intent: Intent?) {
    val pkg = intent?.getStringExtra(ScreenTimeEnforcementEngine.EXTRA_LOCKED_PACKAGE)
      .orEmpty()
      .ifBlank { "blocked.app" }
    val appName = intent?.getStringExtra(ScreenTimeEnforcementEngine.EXTRA_LOCKED_APP_NAME)
      .orEmpty()
      .ifBlank { pkg }
    val usedMins = intent?.getIntExtra(ScreenTimeEnforcementEngine.EXTRA_LOCKED_USED_MINS, 60) ?: 60
    val limitMins = intent?.getIntExtra(ScreenTimeEnforcementEngine.EXTRA_LOCKED_LIMIT_MINS, 60) ?: 60
    val periodName = intent?.getStringExtra(ScreenTimeEnforcementEngine.EXTRA_LOCKED_PERIOD)
      ?: ScreenTimePeriod.DAILY.name
    val isGroupEnforced = intent?.getBooleanExtra(ScreenTimeEnforcementEngine.EXTRA_IS_GROUP_ENFORCED, false) ?: false
    val enforcedByName = intent?.getStringExtra(ScreenTimeEnforcementEngine.EXTRA_ENFORCED_BY_NAME).orEmpty()
    val requiredTaskTitle = intent?.getStringExtra(ScreenTimeEnforcementEngine.EXTRA_REQUIRED_TASK_TITLE).orEmpty()
    val taskInstructions = intent?.getStringExtra(ScreenTimeEnforcementEngine.EXTRA_REQUIRED_TASK_INSTRUCTIONS).orEmpty()
    val lockMode = intent?.getStringExtra(ScreenTimeEnforcementEngine.EXTRA_LOCK_MODE)
      ?: RemoteLockMode.USAGE_LIMIT.name
    val lockAtMillis = intent?.getLongExtra(ScreenTimeEnforcementEngine.EXTRA_LOCK_AT_MILLIS, 0L) ?: 0L

    val lockedEntity = ScreenTimeLimitEntity(
      packageName = pkg,
      appName = appName,
      limitMinutes = limitMins,
      usedMinutes = usedMins,
      periodType = periodName,
      isEnabled = true,
      isGroupEnforced = isGroupEnforced,
      enforcedByName = enforcedByName,
      requiredGroupTaskTitle = requiredTaskTitle,
      taskInstructions = taskInstructions,
      lockMode = lockMode,
      lockAtMillis = lockAtMillis
    )

    val bootPrefs = PreferencesRepository(this).getInitialBootPreferences()
    setContent {
      TasksPlusTheme(
        themeMode = bootPrefs.themeMode,
        useDynamicColor = bootPrefs.useDynamicColor,
        tonalSpotPalette = bootPrefs.tonalSpotPalette
      ) {
        ScreenTimeLockoutOverlay(
          lockedApp = lockedEntity,
          onManageInTasksPlus = {
            if (isGroupEnforced) {
              ScreenTimeEnforcementEngine.openTasksPlusGroupTab(this)
            } else {
              ScreenTimeEnforcementEngine.openTasksPlusScreenTimeTab(this)
            }
            finish()
          },
          onGoToHomeScreen = {
            SystemOverlayBlocker.dismissOverlay(this)
            ScreenTimeEnforcementEngine.goHome(this)
            finish()
          }
        )
      }
    }
  }
}

/**
 * Real Android AccessibilityService that monitors foreground package changes across the device,
 * tracks foreground app usage, and immediately blocks apps that exceed their configured Screen Time
 * limit or reach an exact remote lock time.
 */
class ScreenTimeAccessibilityService : AccessibilityService() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var tickerJob: Job? = null

  override fun onServiceConnected() {
    super.onServiceConnected()
    GroupCloudBackgroundSyncEngine.ensureStarted(this)
    ScreenTimeEnforcementEngine.ensureBackgroundMonitorRunning(this)
    startForegroundTicker()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val pkg = event?.packageName?.toString() ?: return
    if (pkg == "com.android.systemui" || pkg.contains("inputmethod", ignoreCase = true)) {
      return
    }
    ScreenTimeEnforcementEngine.currentForegroundPackage = pkg
    ScreenTimeEnforcementEngine.lastAccessibilityEventMillis = System.currentTimeMillis()

    if (pkg == packageName) {
      SystemOverlayBlocker.dismissOverlay(this)
      return
    }

    serviceScope.launch {
      checkAndBlockPackageIfNeeded(pkg)
    }
  }

  override fun onInterrupt() {
    // No-op
  }

  override fun onDestroy() {
    tickerJob?.cancel()
    serviceScope.cancel()
    super.onDestroy()
  }

  private fun startForegroundTicker() {
    tickerJob?.cancel()
    tickerJob = serviceScope.launch {
      while (isActive) {
        delay(1000L)
        val fgPkg = ScreenTimeEnforcementEngine.currentForegroundPackage
          ?: ScreenTimeEnforcementEngine.detectForegroundAppViaUsageStats(this@ScreenTimeAccessibilityService)
        if (!fgPkg.isNullOrBlank() && fgPkg != packageName) {
          checkAndBlockPackageIfNeeded(fgPkg)
        }
      }
    }
  }

  private suspend fun checkAndBlockPackageIfNeeded(pkg: String) {
    val dao = TaskDatabase.getInstance(this).screenTimeDao()
    val limit = dao.getLimitByPackage(pkg) ?: return
    if (!limit.isEnabled) return

    val now = System.currentTimeMillis()
    if (limit.isLimitReachedAt(now)) {
      ScreenTimeEnforcementEngine.enforceBlockNow(this, limit)
    } else {
      ScreenTimeEnforcementEngine.syncUsageAndEnforceForeground(this)
    }
  }
}
