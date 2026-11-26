package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.CreditCalculator
import com.example.data.model.CreditRules
import com.example.data.model.TaskCategory
import com.example.data.model.TaskCategoryCatalog
import com.example.data.model.ThemeMode
import com.example.data.model.TonalSpotPalette
import com.example.data.model.UserPreferencesState
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private val Context.dataStore by preferencesDataStore(name = "tasks_plus_prefs")

class PreferencesRepository(private val context: Context) {

  companion object {
    private const val BOOT_CACHE_PREFS_NAME = "tasks_plus_boot_cache"
    private const val BOOT_SETUP_COMPLETED = "has_completed_setup_wizard"
    private const val BOOT_THEME_MODE = "theme_mode"
    private const val BOOT_USE_DYNAMIC_COLOR = "use_dynamic_color"
    private const val BOOT_TONAL_SPOT_PALETTE = "tonal_spot_palette"
  }

  private val bootCache by lazy {
    context.applicationContext.getSharedPreferences(BOOT_CACHE_PREFS_NAME, Context.MODE_PRIVATE)
  }

  private object Keys {
    val SETUP_COMPLETED = booleanPreferencesKey("has_completed_setup_wizard")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    val TONAL_SPOT_PALETTE = stringPreferencesKey("tonal_spot_palette")
    val DEFAULT_REMINDER_MINUTES = intPreferencesKey("default_reminder_minutes")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val AUTO_DELETE_UPDATE_APK = booleanPreferencesKey("auto_delete_update_apk")
    val LAST_SYNCED_MILLIS = longPreferencesKey("last_synced_at_millis")
    val CREDIT_SYSTEM_ENABLED = booleanPreferencesKey("credit_system_enabled")
    val PERSONAL_CREDITS = longPreferencesKey("personal_credits")
    val EARLY_POINTS_PER_SECOND = doublePreferencesKey("early_points_per_second")
    val LATE_PENALTY_PER_SECOND = doublePreferencesKey("late_penalty_per_second")
    val CREDIT_GRACE_PERIOD_SECONDS = longPreferencesKey("credit_grace_period_seconds")
    val MAX_CREDIT_PER_TASK = longPreferencesKey("max_credit_per_task")
    val MAX_PENALTY_PER_TASK = longPreferencesKey("max_penalty_per_task")
    val ALLOW_NEGATIVE_CREDITS = booleanPreferencesKey("allow_negative_credits")
    val SHOW_PRODUCTIVITY_DASHBOARD = booleanPreferencesKey("show_productivity_dashboard")
    val CATEGORIES_ENABLED = booleanPreferencesKey("categories_enabled")
    val DISABLED_PREMADE_CATEGORIES = stringPreferencesKey("disabled_premade_categories")
    val CUSTOM_CATEGORIES_JSON = stringPreferencesKey("custom_categories_json")
    val EARN_YOUR_SCROLL_ENABLED = booleanPreferencesKey("earn_your_scroll_enabled")
    val EARNED_SCREEN_TIME_MINUTES = intPreferencesKey("earned_screen_time_minutes")
    val TOTAL_EARNED_SCREEN_TIME_MINUTES = intPreferencesKey("total_earned_screen_time_minutes")
    val EARN_MINUTES_BASE = intPreferencesKey("earn_minutes_base")
    val FOCUS_AURA_STREAK_COUNT = intPreferencesKey("focus_aura_streak_count")
  }

  /**
   * Synchronously reads the lightweight boot cache so returning users render their saved
   * setup-completion state and theme on the very first frame without waiting for async DataStore I/O.
   */
  fun getInitialBootPreferences(): UserPreferencesState {
    return UserPreferencesState(
      hasCompletedSetupWizard = bootCache.getBoolean(BOOT_SETUP_COMPLETED, false),
      themeMode = ThemeMode.fromName(bootCache.getString(BOOT_THEME_MODE, null)),
      useDynamicColor = bootCache.getBoolean(BOOT_USE_DYNAMIC_COLOR, true),
      tonalSpotPalette = TonalSpotPalette.fromName(bootCache.getString(BOOT_TONAL_SPOT_PALETTE, null))
    )
  }

  private fun mirrorBootCache(state: UserPreferencesState) {
    val cachedSetup = bootCache.getBoolean(BOOT_SETUP_COMPLETED, false)
    val cachedTheme = bootCache.getString(BOOT_THEME_MODE, null)
    val cachedDynamic = bootCache.getBoolean(BOOT_USE_DYNAMIC_COLOR, true)
    val cachedPalette = bootCache.getString(BOOT_TONAL_SPOT_PALETTE, null)
    val needsWrite =
      !bootCache.contains(BOOT_SETUP_COMPLETED) ||
        cachedSetup != state.hasCompletedSetupWizard ||
        cachedTheme != state.themeMode.name ||
        cachedDynamic != state.useDynamicColor ||
        cachedPalette != state.tonalSpotPalette.name
    if (needsWrite) {
      bootCache.edit()
        .putBoolean(BOOT_SETUP_COMPLETED, state.hasCompletedSetupWizard)
        .putString(BOOT_THEME_MODE, state.themeMode.name)
        .putBoolean(BOOT_USE_DYNAMIC_COLOR, state.useDynamicColor)
        .putString(BOOT_TONAL_SPOT_PALETTE, state.tonalSpotPalette.name)
        .apply()
    }
  }

  val preferencesFlow: Flow<UserPreferencesState> = context.dataStore.data.map { prefs ->
    UserPreferencesState(
      hasCompletedSetupWizard = prefs[Keys.SETUP_COMPLETED] ?: false,
      themeMode = ThemeMode.fromName(prefs[Keys.THEME_MODE]),
      useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: true,
      tonalSpotPalette = TonalSpotPalette.fromName(prefs[Keys.TONAL_SPOT_PALETTE]),
      defaultReminderMinutes = prefs[Keys.DEFAULT_REMINDER_MINUTES] ?: 30,
      notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
      autoDeleteUpdateApk = prefs[Keys.AUTO_DELETE_UPDATE_APK] ?: true,
      lastSyncedAtMillis = prefs[Keys.LAST_SYNCED_MILLIS] ?: 0L,
      creditSystemEnabled = prefs[Keys.CREDIT_SYSTEM_ENABLED] ?: false,
      personalCredits = prefs[Keys.PERSONAL_CREDITS] ?: 0L,
      earlyPointsPerSecond = prefs[Keys.EARLY_POINTS_PER_SECOND] ?: 1.0,
      latePenaltyPerSecond = prefs[Keys.LATE_PENALTY_PER_SECOND] ?: 1.0,
      creditGracePeriodSeconds = prefs[Keys.CREDIT_GRACE_PERIOD_SECONDS] ?: 0L,
      maxCreditPerTask = prefs[Keys.MAX_CREDIT_PER_TASK] ?: 0L,
      maxPenaltyPerTask = prefs[Keys.MAX_PENALTY_PER_TASK] ?: 0L,
      allowNegativeCredits = prefs[Keys.ALLOW_NEGATIVE_CREDITS] ?: true,
      showProductivityDashboard = prefs[Keys.SHOW_PRODUCTIVITY_DASHBOARD] ?: false,
      categoriesEnabled = prefs[Keys.CATEGORIES_ENABLED] ?: true,
      disabledPremadeCategoryIds = TaskCategoryCatalog.parseIdCsv(prefs[Keys.DISABLED_PREMADE_CATEGORIES]),
      customCategories = TaskCategoryCatalog.parseCustomCategories(prefs[Keys.CUSTOM_CATEGORIES_JSON]),
      earnYourScrollEnabled = prefs[Keys.EARN_YOUR_SCROLL_ENABLED] ?: false,
      earnedScreenTimeMinutes = (prefs[Keys.EARNED_SCREEN_TIME_MINUTES] ?: 0).coerceAtLeast(0),
      totalEarnedScreenTimeMinutes = (prefs[Keys.TOTAL_EARNED_SCREEN_TIME_MINUTES] ?: 0).coerceAtLeast(0),
      earnMinutesBase = (prefs[Keys.EARN_MINUTES_BASE] ?: 5).coerceIn(2, 20),
      focusAuraStreakCount = (prefs[Keys.FOCUS_AURA_STREAK_COUNT] ?: 0).coerceAtLeast(0)
    )
  }.onEach { state ->
    mirrorBootCache(state)
  }

  suspend fun setSetupCompleted(completed: Boolean) {
    bootCache.edit().putBoolean(BOOT_SETUP_COMPLETED, completed).apply()
    context.dataStore.edit { it[Keys.SETUP_COMPLETED] = completed }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    bootCache.edit().putString(BOOT_THEME_MODE, mode.name).apply()
    context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun setUseDynamicColor(enabled: Boolean) {
    bootCache.edit().putBoolean(BOOT_USE_DYNAMIC_COLOR, enabled).apply()
    context.dataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
  }

  suspend fun setTonalSpotPalette(palette: TonalSpotPalette) {
    bootCache.edit().putString(BOOT_TONAL_SPOT_PALETTE, palette.name).apply()
    context.dataStore.edit { it[Keys.TONAL_SPOT_PALETTE] = palette.name }
  }

  suspend fun selectTonalSpotPalette(palette: TonalSpotPalette) {
    bootCache.edit()
      .putBoolean(BOOT_USE_DYNAMIC_COLOR, false)
      .putString(BOOT_TONAL_SPOT_PALETTE, palette.name)
      .apply()
    context.dataStore.edit { prefs ->
      prefs[Keys.USE_DYNAMIC_COLOR] = false
      prefs[Keys.TONAL_SPOT_PALETTE] = palette.name
    }
  }

  suspend fun setDefaultReminderMinutes(minutes: Int) {
    context.dataStore.edit { it[Keys.DEFAULT_REMINDER_MINUTES] = minutes.coerceIn(0, 10080) }
  }

  suspend fun setNotificationsEnabled(enabled: Boolean) {
    context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
  }

  suspend fun setAutoDeleteUpdateApk(enabled: Boolean) {
    context.dataStore.edit { it[Keys.AUTO_DELETE_UPDATE_APK] = enabled }
  }

  suspend fun setCreditSystemEnabled(enabled: Boolean) {
    context.dataStore.edit { it[Keys.CREDIT_SYSTEM_ENABLED] = enabled }
  }

  suspend fun setShowProductivityDashboard(enabled: Boolean) {
    context.dataStore.edit { it[Keys.SHOW_PRODUCTIVITY_DASHBOARD] = enabled }
  }

  suspend fun setCategoriesEnabled(enabled: Boolean) {
    context.dataStore.edit { it[Keys.CATEGORIES_ENABLED] = enabled }
  }

  suspend fun setPremadeCategoryEnabled(categoryId: String, enabled: Boolean) {
    context.dataStore.edit { prefs ->
      val disabled = TaskCategoryCatalog.parseIdCsv(prefs[Keys.DISABLED_PREMADE_CATEGORIES]).toMutableSet()
      if (enabled) {
        disabled.remove(categoryId)
      } else {
        disabled.add(categoryId)
      }
      prefs[Keys.DISABLED_PREMADE_CATEGORIES] = TaskCategoryCatalog.toIdCsv(disabled)
    }
  }

  suspend fun addCustomCategory(name: String, colorHex: Long): TaskCategory? {
    val cleanName = name.trim().take(32)
    if (cleanName.isBlank()) return null
    var created: TaskCategory? = null
    context.dataStore.edit { prefs ->
      val current = TaskCategoryCatalog.parseCustomCategories(prefs[Keys.CUSTOM_CATEGORIES_JSON]).toMutableList()
      if (current.size >= 30) return@edit
      val duplicate = current.any { it.name.equals(cleanName, ignoreCase = true) } ||
        TaskCategoryCatalog.premadeCategories.any { it.name.equals(cleanName, ignoreCase = true) }
      if (duplicate) return@edit
      val newCat = TaskCategory(
        id = "cat_custom_${UUID.randomUUID().toString().replace("-", "_").take(12)}",
        name = cleanName,
        colorHex = colorHex,
        isPremade = false
      )
      current.add(newCat)
      prefs[Keys.CUSTOM_CATEGORIES_JSON] = TaskCategoryCatalog.serializeCustomCategories(current)
      created = newCat
    }
    return created
  }

  suspend fun deleteCustomCategory(categoryId: String) {
    context.dataStore.edit { prefs ->
      val updated = TaskCategoryCatalog.parseCustomCategories(prefs[Keys.CUSTOM_CATEGORIES_JSON])
        .filterNot { it.id == categoryId }
      prefs[Keys.CUSTOM_CATEGORIES_JSON] = TaskCategoryCatalog.serializeCustomCategories(updated)
    }
  }

  suspend fun setEarnYourScrollEnabled(enabled: Boolean) {
    context.dataStore.edit { it[Keys.EARN_YOUR_SCROLL_ENABLED] = enabled }
  }

  suspend fun setEarnMinutesBase(baseMinutes: Int) {
    context.dataStore.edit { it[Keys.EARN_MINUTES_BASE] = baseMinutes.coerceIn(2, 20) }
  }

  suspend fun mintEarnedScreenTimeMinutes(minutes: Int): Int {
    val safeAdd = minutes.coerceIn(0, 240)
    if (safeAdd <= 0) return 0
    var newBalance = 0
    context.dataStore.edit { prefs ->
      val current = (prefs[Keys.EARNED_SCREEN_TIME_MINUTES] ?: 0).coerceAtLeast(0)
      val total = (prefs[Keys.TOTAL_EARNED_SCREEN_TIME_MINUTES] ?: 0).coerceAtLeast(0)
      newBalance = (current + safeAdd).coerceAtMost(1440)
      prefs[Keys.EARNED_SCREEN_TIME_MINUTES] = newBalance
      prefs[Keys.TOTAL_EARNED_SCREEN_TIME_MINUTES] = total + safeAdd
    }
    return newBalance
  }

  suspend fun spendEarnedScreenTimeMinutes(minutes: Int): Boolean {
    val cost = minutes.coerceAtLeast(1)
    var success = false
    context.dataStore.edit { prefs ->
      val current = (prefs[Keys.EARNED_SCREEN_TIME_MINUTES] ?: 0).coerceAtLeast(0)
      if (current >= cost) {
        prefs[Keys.EARNED_SCREEN_TIME_MINUTES] = current - cost
        success = true
      }
    }
    return success
  }

  suspend fun incrementFocusAuraStreak(): Int {
    var updated = 0
    context.dataStore.edit { prefs ->
      val current = (prefs[Keys.FOCUS_AURA_STREAK_COUNT] ?: 0).coerceAtLeast(0)
      updated = current + 1
      prefs[Keys.FOCUS_AURA_STREAK_COUNT] = updated
    }
    return updated
  }

  suspend fun updatePersonalCreditRules(rules: CreditRules) {
    val clean = rules.normalized()
    context.dataStore.edit { prefs ->
      prefs[Keys.CREDIT_SYSTEM_ENABLED] = clean.enabled
      prefs[Keys.EARLY_POINTS_PER_SECOND] = clean.earlyPointsPerSecond
      prefs[Keys.LATE_PENALTY_PER_SECOND] = clean.latePenaltyPerSecond
      prefs[Keys.CREDIT_GRACE_PERIOD_SECONDS] = clean.gracePeriodSeconds
      prefs[Keys.MAX_CREDIT_PER_TASK] = clean.maxCreditPerTask
      prefs[Keys.MAX_PENALTY_PER_TASK] = clean.maxPenaltyPerTask
      prefs[Keys.ALLOW_NEGATIVE_CREDITS] = clean.allowNegativeBalance
    }
  }

  suspend fun updatePersonalCreditsBalance(balance: Long) {
    context.dataStore.edit { it[Keys.PERSONAL_CREDITS] = balance }
  }

  suspend fun adjustPersonalCreditsBy(delta: Long, allowNegative: Boolean): Long {
    var resultBalance = 0L
    context.dataStore.edit { prefs ->
      val current = prefs[Keys.PERSONAL_CREDITS] ?: 0L
      val updated = CreditCalculator.applyBalanceDelta(current, delta, allowNegative)
      prefs[Keys.PERSONAL_CREDITS] = updated
      resultBalance = updated
    }
    return resultBalance
  }

  suspend fun markSynced(timestampMillis: Long = System.currentTimeMillis()) {
    context.dataStore.edit { it[Keys.LAST_SYNCED_MILLIS] = timestampMillis }
  }

  suspend fun applyCloudPreferences(
    themeMode: ThemeMode,
    useDynamicColor: Boolean,
    tonalSpotPalette: TonalSpotPalette,
    defaultReminderMinutes: Int,
    notificationsEnabled: Boolean,
    autoDeleteUpdateApk: Boolean,
    creditRules: CreditRules? = null,
    personalCredits: Long? = null,
    categoriesEnabled: Boolean? = null,
    disabledPremadeCategoryIds: List<String>? = null,
    customCategoriesJson: String? = null
  ) {
    bootCache.edit()
      .putString(BOOT_THEME_MODE, themeMode.name)
      .putBoolean(BOOT_USE_DYNAMIC_COLOR, useDynamicColor)
      .putString(BOOT_TONAL_SPOT_PALETTE, tonalSpotPalette.name)
      .apply()
    context.dataStore.edit { prefs ->
      prefs[Keys.THEME_MODE] = themeMode.name
      prefs[Keys.USE_DYNAMIC_COLOR] = useDynamicColor
      prefs[Keys.TONAL_SPOT_PALETTE] = tonalSpotPalette.name
      prefs[Keys.DEFAULT_REMINDER_MINUTES] = defaultReminderMinutes.coerceIn(0, 10080)
      prefs[Keys.NOTIFICATIONS_ENABLED] = notificationsEnabled
      prefs[Keys.AUTO_DELETE_UPDATE_APK] = autoDeleteUpdateApk
      if (creditRules != null) {
        val clean = creditRules.normalized()
        prefs[Keys.CREDIT_SYSTEM_ENABLED] = clean.enabled
        prefs[Keys.EARLY_POINTS_PER_SECOND] = clean.earlyPointsPerSecond
        prefs[Keys.LATE_PENALTY_PER_SECOND] = clean.latePenaltyPerSecond
        prefs[Keys.CREDIT_GRACE_PERIOD_SECONDS] = clean.gracePeriodSeconds
        prefs[Keys.MAX_CREDIT_PER_TASK] = clean.maxCreditPerTask
        prefs[Keys.MAX_PENALTY_PER_TASK] = clean.maxPenaltyPerTask
        prefs[Keys.ALLOW_NEGATIVE_CREDITS] = clean.allowNegativeBalance
      }
      if (personalCredits != null) {
        prefs[Keys.PERSONAL_CREDITS] = personalCredits
      }
      if (categoriesEnabled != null) {
        prefs[Keys.CATEGORIES_ENABLED] = categoriesEnabled
      }
      if (disabledPremadeCategoryIds != null) {
        prefs[Keys.DISABLED_PREMADE_CATEGORIES] = TaskCategoryCatalog.toIdCsv(disabledPremadeCategoryIds.toSet())
      }
      if (customCategoriesJson != null) {
        val parsed = TaskCategoryCatalog.parseCustomCategories(customCategoriesJson)
        prefs[Keys.CUSTOM_CATEGORIES_JSON] = TaskCategoryCatalog.serializeCustomCategories(parsed)
      }
      prefs[Keys.LAST_SYNCED_MILLIS] = System.currentTimeMillis()
    }
  }
}
