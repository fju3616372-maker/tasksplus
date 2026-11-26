package com.example.widget

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.example.R
import com.example.data.local.PreferencesRepository
import com.example.data.model.ThemeMode
import com.example.data.model.TonalSpotPalette
import com.example.data.model.UserPreferencesState
import com.example.ui.theme.tonalSpotScheme
import kotlinx.coroutines.flow.first

data class WidgetThemePalette(
  val darkTheme: Boolean,
  val useDynamicTint: Boolean,
  val surfaceColorInt: Int,
  val cardColorInt: Int,
  val pillColorInt: Int,
  val onSurfaceColorInt: Int,
  val onSurfaceVariantColorInt: Int,
  val accentColorInt: Int,
  val onPillColorInt: Int,
  val chartGridColorInt: Int,
  val chartBaselineColorInt: Int,
  val chartAxisTextColorInt: Int,
  val chartTodayTextColorInt: Int,
  val completedLegendColorInt: Int,
  val pendingLegendColorInt: Int,
  @DrawableRes val surfaceDrawableRes: Int,
  @DrawableRes val cardDrawableRes: Int,
  @DrawableRes val pillDrawableRes: Int
) {
  fun applySurfaceBackground(views: RemoteViews, viewId: Int) {
    applyBackground(views, viewId, R.drawable.bg_widget_surface_tintable, surfaceDrawableRes, surfaceColorInt)
  }

  fun applyCardBackground(views: RemoteViews, viewId: Int) {
    applyBackground(views, viewId, R.drawable.bg_widget_card_tintable, cardDrawableRes, cardColorInt)
  }

  fun applyPillBackground(views: RemoteViews, viewId: Int) {
    applyBackground(views, viewId, R.drawable.bg_widget_pill_tintable, pillDrawableRes, pillColorInt)
  }

  private fun applyBackground(
    views: RemoteViews,
    viewId: Int,
    @DrawableRes tintableRes: Int,
    @DrawableRes staticRes: Int,
    tintColor: Int
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (useDynamicTint) {
        views.setInt(viewId, "setBackgroundResource", tintableRes)
        views.setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(tintColor))
      } else {
        views.setInt(viewId, "setBackgroundResource", staticRes)
        views.setColorStateList(viewId, "setBackgroundTintList", null)
      }
    } else {
      views.setInt(viewId, "setBackgroundResource", staticRes)
    }
  }
}

object WidgetThemeHelper {

  suspend fun resolveForContext(context: Context): WidgetThemePalette {
    val prefs = try {
      PreferencesRepository(context).preferencesFlow.first()
    } catch (_: Exception) {
      PreferencesRepository(context).getInitialBootPreferences()
    }
    return fromPreferences(context, prefs)
  }

  fun fromPreferences(context: Context, prefs: UserPreferencesState): WidgetThemePalette {
    val systemDark =
      (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val darkTheme = when (prefs.themeMode) {
      ThemeMode.SYSTEM -> systemDark
      ThemeMode.LIGHT -> false
      ThemeMode.DARK -> true
    }

    val useDynamicTint = prefs.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = if (useDynamicTint && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
      tonalSpotScheme(prefs.tonalSpotPalette, darkTheme)
    }

    val surfaceRes = when (prefs.tonalSpotPalette) {
      TonalSpotPalette.INDIGO ->
        if (darkTheme) R.drawable.bg_widget_surface_indigo_dark else R.drawable.bg_widget_surface_indigo_light
      TonalSpotPalette.EMERALD ->
        if (darkTheme) R.drawable.bg_widget_surface_emerald_dark else R.drawable.bg_widget_surface_emerald_light
      TonalSpotPalette.TEAL ->
        if (darkTheme) R.drawable.bg_widget_surface_teal_dark else R.drawable.bg_widget_surface_teal_light
      TonalSpotPalette.ROSE ->
        if (darkTheme) R.drawable.bg_widget_surface_rose_dark else R.drawable.bg_widget_surface_rose_light
    }
    val cardRes = when (prefs.tonalSpotPalette) {
      TonalSpotPalette.INDIGO ->
        if (darkTheme) R.drawable.bg_widget_card_indigo_dark else R.drawable.bg_widget_card_indigo_light
      TonalSpotPalette.EMERALD ->
        if (darkTheme) R.drawable.bg_widget_card_emerald_dark else R.drawable.bg_widget_card_emerald_light
      TonalSpotPalette.TEAL ->
        if (darkTheme) R.drawable.bg_widget_card_teal_dark else R.drawable.bg_widget_card_teal_light
      TonalSpotPalette.ROSE ->
        if (darkTheme) R.drawable.bg_widget_card_rose_dark else R.drawable.bg_widget_card_rose_light
    }
    val pillRes = when (prefs.tonalSpotPalette) {
      TonalSpotPalette.INDIGO ->
        if (darkTheme) R.drawable.bg_widget_pill_indigo_dark else R.drawable.bg_widget_pill_indigo_light
      TonalSpotPalette.EMERALD ->
        if (darkTheme) R.drawable.bg_widget_pill_emerald_dark else R.drawable.bg_widget_pill_emerald_light
      TonalSpotPalette.TEAL ->
        if (darkTheme) R.drawable.bg_widget_pill_teal_dark else R.drawable.bg_widget_pill_teal_light
      TonalSpotPalette.ROSE ->
        if (darkTheme) R.drawable.bg_widget_pill_rose_dark else R.drawable.bg_widget_pill_rose_light
    }

    return WidgetThemePalette(
      darkTheme = darkTheme,
      useDynamicTint = useDynamicTint,
      surfaceColorInt = scheme.surfaceContainerLow.toArgb(),
      cardColorInt = scheme.surfaceContainerHighest.toArgb(),
      pillColorInt = scheme.primary.toArgb(),
      onSurfaceColorInt = scheme.onSurface.toArgb(),
      onSurfaceVariantColorInt = scheme.onSurfaceVariant.toArgb(),
      accentColorInt = scheme.primary.toArgb(),
      onPillColorInt = scheme.onPrimary.toArgb(),
      chartGridColorInt = scheme.outlineVariant.copy(alpha = 0.55f).toArgb(),
      chartBaselineColorInt = scheme.outline.copy(alpha = 0.78f).toArgb(),
      chartAxisTextColorInt = scheme.onSurfaceVariant.toArgb(),
      chartTodayTextColorInt = scheme.primary.toArgb(),
      completedLegendColorInt = if (darkTheme) 0xFF34D399.toInt() else 0xFF059669.toInt(),
      pendingLegendColorInt = if (darkTheme) 0xFFFBBF24.toInt() else 0xFFD97706.toInt(),
      surfaceDrawableRes = surfaceRes,
      cardDrawableRes = cardRes,
      pillDrawableRes = pillRes
    )
  }
}
