package com.example.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.ThemeMode
import com.example.data.model.TonalSpotPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class ExpressiveLogoColorRoles(
  val primary: Color,
  val onPrimary: Color,
  val primaryContainer: Color,
  val onPrimaryContainer: Color,
  val secondaryContainer: Color,
  val tertiary: Color,
  val onTertiary: Color,
  val tertiaryContainer: Color,
  val onTertiaryContainer: Color
)

object ExpressiveLogoPalettes {
  // Indigo Spot — Default Logo when Wallpaper Dynamic Color is OFF or Android < 12
  val IndigoLight = ExpressiveLogoColorRoles(
    primary = Color(0xFF4559A9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001453),
    secondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFF735472),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7FA),
    onTertiaryContainer = Color(0xFF2B122C)
  )

  val IndigoDark = ExpressiveLogoColorRoles(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF102878),
    primaryContainer = Color(0xFF2C4090),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondaryContainer = Color(0xFF434659),
    tertiary = Color(0xFFE1BBDD),
    onTertiary = Color(0xFF422742),
    tertiaryContainer = Color(0xFF5A3D59),
    onTertiaryContainer = Color(0xFFFFD7FA)
  )

  // Sage Teal
  val SageTealLight = ExpressiveLogoColorRoles(
    primary = Color(0xFF006A60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9DF2E4),
    onPrimaryContainer = Color(0xFF00201C),
    secondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFF456179),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCE5FF),
    onTertiaryContainer = Color(0xFF001E31)
  )

  val SageTealDark = ExpressiveLogoColorRoles(
    primary = Color(0xFF80D5C8),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF9DF2E4),
    secondaryContainer = Color(0xFF334B47),
    tertiary = Color(0xFFADC9E5),
    onTertiary = Color(0xFF153349),
    tertiaryContainer = Color(0xFF2D4960),
    onTertiaryContainer = Color(0xFFCCE5FF)
  )

  // Violet Iris
  val VioletIrisLight = ExpressiveLogoColorRoles(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D)
  )

  val VioletIrisDark = ExpressiveLogoColorRoles(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondaryContainer = Color(0xFF4A4458),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4)
  )

  // Coral Clay
  val CoralClayLight = ExpressiveLogoColorRoles(
    primary = Color(0xFF9C413B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondaryContainer = Color(0xFFFFDBD7),
    tertiary = Color(0xFF725B2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF261900)
  )

  val CoralClayDark = ExpressiveLogoColorRoles(
    primary = Color(0xFFFFB4AC),
    onPrimary = Color(0xFF5F1412),
    primaryContainer = Color(0xFF7E2A26),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondaryContainer = Color(0xFF5D3F3C),
    tertiary = Color(0xFFE1C38C),
    onTertiary = Color(0xFF402D04),
    tertiaryContainer = Color(0xFF584419),
    onTertiaryContainer = Color(0xFFFFDEA6)
  )

  fun rolesForPalette(palette: TonalSpotPalette, darkTheme: Boolean): ExpressiveLogoColorRoles {
    return when (palette) {
      TonalSpotPalette.INDIGO -> if (darkTheme) IndigoDark else IndigoLight
      TonalSpotPalette.EMERALD -> if (darkTheme) SageTealDark else SageTealLight
      TonalSpotPalette.TEAL -> if (darkTheme) VioletIrisDark else VioletIrisLight
      TonalSpotPalette.ROSE -> if (darkTheme) CoralClayDark else CoralClayLight
    }
  }
}

/**
 * Synchronizes the home-screen Android launcher icon alias with the user's wallpaper setting
 * and selected [TonalSpotPalette].
 *
 * - Android 12+ & [useDynamicColor] == true -> Dynamic Wallpaper alias (`MainActivityDynamicAlias`)
 * - Otherwise -> Static alias matching [tonalSpotPalette] (defaults to `MainActivityIndigoAlias`)
 */
object LauncherIconThemeSwitcher {
  private const val TAG = "LauncherIconSwitcher"
  private const val ALIAS_DYNAMIC = "com.example.MainActivityDynamicAlias"
  private const val ALIAS_INDIGO = "com.example.MainActivityIndigoAlias"
  private const val ALIAS_TEAL = "com.example.MainActivityTealAlias"
  private const val ALIAS_VIOLET = "com.example.MainActivityVioletAlias"
  private const val ALIAS_CORAL = "com.example.MainActivityCoralAlias"

  private val allAliases = listOf(
    ALIAS_DYNAMIC,
    ALIAS_INDIGO,
    ALIAS_TEAL,
    ALIAS_VIOLET,
    ALIAS_CORAL
  )

  fun targetAliasClassName(
    useDynamicColor: Boolean,
    tonalSpotPalette: TonalSpotPalette,
    sdkInt: Int = Build.VERSION.SDK_INT
  ): String {
    if (useDynamicColor && sdkInt >= Build.VERSION_CODES.S) {
      return ALIAS_DYNAMIC
    }
    return when (tonalSpotPalette) {
      TonalSpotPalette.INDIGO -> ALIAS_INDIGO
      TonalSpotPalette.EMERALD -> ALIAS_TEAL
      TonalSpotPalette.TEAL -> ALIAS_VIOLET
      TonalSpotPalette.ROSE -> ALIAS_CORAL
    }
  }

  fun syncLauncherIcon(
    context: Context,
    useDynamicColor: Boolean,
    tonalSpotPalette: TonalSpotPalette
  ) {
    try {
      val pm = context.packageManager
      val pkg = context.packageName
      val desiredAlias = targetAliasClassName(useDynamicColor, tonalSpotPalette)

      // Always enable the desired alias first so the package never has zero active launcher
      // components mid-switch, then disable the inactive aliases.
      val orderedAliases = listOf(desiredAlias) + allAliases.filter { it != desiredAlias }
      for (alias in orderedAliases) {
        val component = ComponentName(pkg, alias)
        val desiredState = if (alias == desiredAlias) {
          PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
          PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val currentState = pm.getComponentEnabledSetting(component)
        // Default state in manifest has ALIAS_DYNAMIC enabled and others disabled
        val effectivelyMatches = when {
          currentState == desiredState -> true
          currentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
            alias == ALIAS_DYNAMIC &&
            desiredAlias == ALIAS_DYNAMIC -> true
          currentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
            alias != ALIAS_DYNAMIC &&
            desiredState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> true
          else -> false
        }
        if (!effectivelyMatches) {
          pm.setComponentEnabledSetting(
            component,
            desiredState,
            PackageManager.DONT_KILL_APP
          )
        }
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Unable to switch launcher icon alias", t)
    }
  }
}

/**
 * Renders the chosen "Soft Burst & Clover" (Design 1) M3 Expressive Tonal Spot App Logo.
 *
 * - When [useDynamicColor] is ON (default) and the device runs Android 12+ (API 31+),
 *   the logo dynamically recolors using the user's system wallpaper palette.
 * - When [useDynamicColor] is OFF or the device runs Android < 12 (API 23–30),
 *   the logo follows the selected [tonalSpotPalette] theme (and [themeMode] Light/Dark/System),
 *   defaulting to **Indigo Spot**.
 */
@Composable
fun TaskPlusExpressiveLogo(
  useDynamicColor: Boolean,
  modifier: Modifier = Modifier,
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  tonalSpotPalette: TonalSpotPalette = TonalSpotPalette.INDIGO,
  previewPalette: TonalSpotPalette? = null,
  cornerRadiusFraction: Float = 0.225f
) {
  val context = LocalContext.current
  val isDark = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }

  val targetRoles = when {
    previewPalette != null -> ExpressiveLogoPalettes.rolesForPalette(previewPalette, isDark)
    useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val dyn = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      ExpressiveLogoColorRoles(
        primary = dyn.primary,
        onPrimary = dyn.onPrimary,
        primaryContainer = dyn.primaryContainer,
        onPrimaryContainer = dyn.onPrimaryContainer,
        secondaryContainer = dyn.secondaryContainer,
        tertiary = dyn.tertiary,
        onTertiary = dyn.onTertiary,
        tertiaryContainer = dyn.tertiaryContainer,
        onTertiaryContainer = dyn.onTertiaryContainer
      )
    }
    else -> {
      // Follows selected theme palette (defaults to Indigo Spot) when wallpaper sync is off or device < Android 12
      ExpressiveLogoPalettes.rolesForPalette(tonalSpotPalette, isDark)
    }
  }

  val bgColor by animateColorAsState(
    targetValue = targetRoles.primaryContainer,
    animationSpec = tween(280),
    label = "logo_bg"
  )
  val burstColor by animateColorAsState(
    targetValue = targetRoles.primary,
    animationSpec = tween(280),
    label = "logo_burst"
  )
  val checkColor by animateColorAsState(
    targetValue = targetRoles.onPrimary,
    animationSpec = tween(280),
    label = "logo_check"
  )
  val cloverColor by animateColorAsState(
    targetValue = targetRoles.tertiary,
    animationSpec = tween(280),
    label = "logo_clover"
  )
  val plusColor by animateColorAsState(
    targetValue = targetRoles.onTertiary,
    animationSpec = tween(280),
    label = "logo_plus"
  )

  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val scale = w / 512f
    val cornerRadius = w * cornerRadiusFraction.coerceIn(0f, 0.5f)

    // 1. Rounded Tonal Spot Container Backdrop (512x512 rx=115)
    drawRoundRect(
      color = bgColor,
      topLeft = Offset.Zero,
      size = Size(w, h),
      cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )

    // 2. 9-sided Soft Scallop Burst: makeScallopPath(242, 270, 148, 14, 9, -10)
    val burstPath = buildScallopPath(
      cx = 242f * scale,
      cy = 270f * scale,
      rBase = 148f * scale,
      amp = 14f * scale,
      lobes = 9,
      rotDeg = -10f
    )
    drawPath(
      path = burstPath,
      color = burstColor,
      style = Fill
    )

    // 3. Bold Rounded Checkmark: M 156 282 L 208 334 L 308 224
    val checkPath = Path().apply {
      moveTo(156f * scale, 282f * scale)
      lineTo(208f * scale, 334f * scale)
      lineTo(308f * scale, 224f * scale)
    }
    drawPath(
      path = checkPath,
      color = checkColor,
      style = Stroke(
        width = 34f * scale,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
      )
    )

    // 4. 4-sided Clover Plus Badge: makeScallopPath(368, 152, 56, 9, 4, 45)
    val cloverPath = buildScallopPath(
      cx = 368f * scale,
      cy = 152f * scale,
      rBase = 56f * scale,
      amp = 9f * scale,
      lobes = 4,
      rotDeg = 45f
    )
    drawPath(
      path = cloverPath,
      color = cloverColor,
      style = Fill
    )
    drawPath(
      path = cloverPath,
      color = bgColor,
      style = Stroke(
        width = 14f * scale,
        join = StrokeJoin.Round
      )
    )

    // 5. Plus Sign inside Clover Badge: M 344 152 H 392 M 368 128 V 176
    val plusStroke = 14f * scale
    drawLine(
      color = plusColor,
      start = Offset(344f * scale, 152f * scale),
      end = Offset(392f * scale, 152f * scale),
      strokeWidth = plusStroke,
      cap = StrokeCap.Round
    )
    drawLine(
      color = plusColor,
      start = Offset(368f * scale, 128f * scale),
      end = Offset(368f * scale, 176f * scale),
      strokeWidth = plusStroke,
      cap = StrokeCap.Round
    )
  }
}

private fun buildScallopPath(
  cx: Float,
  cy: Float,
  rBase: Float,
  amp: Float,
  lobes: Int,
  rotDeg: Float,
  steps: Int = 180
): Path {
  val rot = (rotDeg * PI / 180.0).toFloat()
  val path = Path()
  for (i in 0..steps) {
    val a = ((i.toDouble() / steps.toDouble()) * PI * 2.0).toFloat()
    val r = rBase + amp * cos(lobes * (a - rot))
    val x = cx + r * cos(a)
    val y = cy + r * sin(a)
    if (i == 0) {
      path.moveTo(x, y)
    } else {
      path.lineTo(x, y)
    }
  }
  path.close()
  return path
}
