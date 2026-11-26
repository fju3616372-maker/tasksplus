package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.example.data.model.TonalSpotPalette

// Indigo Spot (Default M3 Expressive Tonal Spot)
private val IndigoLightScheme = lightColorScheme(
  primary = Color(0xFF4559A9),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFDDE1FF),
  onPrimaryContainer = Color(0xFF001453),
  secondary = Color(0xFF5B5D72),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFE0E1F9),
  onSecondaryContainer = Color(0xFF181A2C),
  tertiary = Color(0xFF735472),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFFFD7FA),
  onTertiaryContainer = Color(0xFF2B122C),
  background = Color(0xFFFBF8FF),
  onBackground = Color(0xFF1B1B21),
  surface = Color(0xFFFBF8FF),
  onSurface = Color(0xFF1B1B21),
  surfaceVariant = Color(0xFFE2E1EC),
  onSurfaceVariant = Color(0xFF45464F),
  surfaceContainerLowest = Color(0xFFFFFFFF),
  surfaceContainerLow = Color(0xFFF5F2FA),
  surfaceContainer = Color(0xFFEFEDF4),
  surfaceContainerHigh = Color(0xFFE9E7EF),
  surfaceContainerHighest = Color(0xFFE3E1E9),
  outline = Color(0xFF767680),
  outlineVariant = Color(0xFFC6C5D0)
)

private val IndigoDarkScheme = darkColorScheme(
  primary = Color(0xFFB8C4FF),
  onPrimary = Color(0xFF102878),
  primaryContainer = Color(0xFF2C4090),
  onPrimaryContainer = Color(0xFFDDE1FF),
  secondary = Color(0xFFC3C5DD),
  onSecondary = Color(0xFF2D2F42),
  secondaryContainer = Color(0xFF434659),
  onSecondaryContainer = Color(0xFFE0E1F9),
  tertiary = Color(0xFFE1BBDD),
  onTertiary = Color(0xFF422742),
  tertiaryContainer = Color(0xFF5A3D59),
  onTertiaryContainer = Color(0xFFFFD7FA),
  background = Color(0xFF121318),
  onBackground = Color(0xFFE3E1E9),
  surface = Color(0xFF121318),
  onSurface = Color(0xFFE3E1E9),
  surfaceVariant = Color(0xFF45464F),
  onSurfaceVariant = Color(0xFFC6C5D0),
  surfaceContainerLowest = Color(0xFF0D0E13),
  surfaceContainerLow = Color(0xFF1B1B21),
  surfaceContainer = Color(0xFF1F1F25),
  surfaceContainerHigh = Color(0xFF292A2F),
  surfaceContainerHighest = Color(0xFF34343A),
  outline = Color(0xFF90909A),
  outlineVariant = Color(0xFF45464F)
)

// Sage Teal Tonal Spot
private val SageTealLightScheme = lightColorScheme(
  primary = Color(0xFF006A60),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFF9DF2E4),
  onPrimaryContainer = Color(0xFF00201C),
  secondary = Color(0xFF4A635F),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFCCE8E2),
  onSecondaryContainer = Color(0xFF05201C),
  tertiary = Color(0xFF456179),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFCCE5FF),
  onTertiaryContainer = Color(0xFF001E31),
  background = Color(0xFFF4FBF8),
  onBackground = Color(0xFF161D1C),
  surface = Color(0xFFF4FBF8),
  onSurface = Color(0xFF161D1C),
  surfaceVariant = Color(0xFFDAE5E1),
  onSurfaceVariant = Color(0xFF3F4947),
  surfaceContainerLow = Color(0xFFEFF5F2),
  surfaceContainer = Color(0xFFE9EFED),
  surfaceContainerHigh = Color(0xFFE3EAE7),
  surfaceContainerHighest = Color(0xFFDDE4E1)
)

private val SageTealDarkScheme = darkColorScheme(
  primary = Color(0xFF80D5C8),
  onPrimary = Color(0xFF003731),
  primaryContainer = Color(0xFF005048),
  onPrimaryContainer = Color(0xFF9DF2E4),
  secondary = Color(0xFFB1CCC6),
  onSecondary = Color(0xFF1C3531),
  secondaryContainer = Color(0xFF334B47),
  onSecondaryContainer = Color(0xFFCCE8E2),
  tertiary = Color(0xFFADC9E5),
  onTertiary = Color(0xFF153349),
  tertiaryContainer = Color(0xFF2D4960),
  onTertiaryContainer = Color(0xFFCCE5FF),
  background = Color(0xFF0E1513),
  onBackground = Color(0xFFDDE4E1),
  surface = Color(0xFF0E1513),
  onSurface = Color(0xFFDDE4E1),
  surfaceVariant = Color(0xFF3F4947),
  onSurfaceVariant = Color(0xFFBEC9C6),
  surfaceContainerLow = Color(0xFF161D1C),
  surfaceContainer = Color(0xFF1A2120),
  surfaceContainerHigh = Color(0xFF252B2A),
  surfaceContainerHighest = Color(0xFF303635)
)

// Violet Iris Tonal Spot
private val VioletIrisLightScheme = lightColorScheme(
  primary = Color(0xFF6750A4),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFEADDFF),
  onPrimaryContainer = Color(0xFF21005D),
  secondary = Color(0xFF625B71),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFE8DEF8),
  onSecondaryContainer = Color(0xFF1D192B),
  tertiary = Color(0xFF7D5260),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFFFD8E4),
  onTertiaryContainer = Color(0xFF31111D),
  background = Color(0xFFFEF7FF),
  onBackground = Color(0xFF1D1B20),
  surface = Color(0xFFFEF7FF),
  onSurface = Color(0xFF1D1B20),
  surfaceVariant = Color(0xFFE7E0EC),
  onSurfaceVariant = Color(0xFF49454F),
  surfaceContainerLow = Color(0xFFF7F2FA),
  surfaceContainer = Color(0xFFF3EDF7),
  surfaceContainerHigh = Color(0xFFECE6F0),
  surfaceContainerHighest = Color(0xFFE6E0E9)
)

private val VioletIrisDarkScheme = darkColorScheme(
  primary = Color(0xFFD0BCFF),
  onPrimary = Color(0xFF381E72),
  primaryContainer = Color(0xFF4F378B),
  onPrimaryContainer = Color(0xFFEADDFF),
  secondary = Color(0xFFCCC2DC),
  onSecondary = Color(0xFF332D41),
  secondaryContainer = Color(0xFF4A4458),
  onSecondaryContainer = Color(0xFFE8DEF8),
  tertiary = Color(0xFFEFB8C8),
  onTertiary = Color(0xFF492532),
  tertiaryContainer = Color(0xFF633B48),
  onTertiaryContainer = Color(0xFFFFD8E4),
  background = Color(0xFF141218),
  onBackground = Color(0xFFE6E0E9),
  surface = Color(0xFF141218),
  onSurface = Color(0xFFE6E0E9),
  surfaceVariant = Color(0xFF49454F),
  onSurfaceVariant = Color(0xFFCAC4D0),
  surfaceContainerLow = Color(0xFF1D1B20),
  surfaceContainer = Color(0xFF211F26),
  surfaceContainerHigh = Color(0xFF2B2930),
  surfaceContainerHighest = Color(0xFF36343B)
)

// Coral Clay Tonal Spot
private val CoralClayLightScheme = lightColorScheme(
  primary = Color(0xFF9C413B),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFFFDAD6),
  onPrimaryContainer = Color(0xFF410002),
  secondary = Color(0xFF775653),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFFFDBD7),
  onSecondaryContainer = Color(0xFF2C1513),
  tertiary = Color(0xFF725B2E),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFFFDEA6),
  onTertiaryContainer = Color(0xFF261900),
  background = Color(0xFFFFF8F7),
  onBackground = Color(0xFF231918),
  surface = Color(0xFFFFF8F7),
  onSurface = Color(0xFF231918),
  surfaceVariant = Color(0xFFF5DDDA),
  onSurfaceVariant = Color(0xFF534341),
  surfaceContainerLow = Color(0xFFFFF0EE),
  surfaceContainer = Color(0xFFFCEAE7),
  surfaceContainerHigh = Color(0xFFF6E4E2),
  surfaceContainerHighest = Color(0xFFF1DEDD)
)

private val CoralClayDarkScheme = darkColorScheme(
  primary = Color(0xFFFFB4AC),
  onPrimary = Color(0xFF5F1412),
  primaryContainer = Color(0xFF7E2A26),
  onPrimaryContainer = Color(0xFFFFDAD6),
  secondary = Color(0xFFE7BDB8),
  onSecondary = Color(0xFF442927),
  secondaryContainer = Color(0xFF5D3F3C),
  onSecondaryContainer = Color(0xFFFFDBD7),
  tertiary = Color(0xFFE1C38C),
  onTertiary = Color(0xFF402D04),
  tertiaryContainer = Color(0xFF584419),
  onTertiaryContainer = Color(0xFFFFDEA6),
  background = Color(0xFF1A1110),
  onBackground = Color(0xFFF1DEDD),
  surface = Color(0xFF1A1110),
  onSurface = Color(0xFFF1DEDD),
  surfaceVariant = Color(0xFF534341),
  onSurfaceVariant = Color(0xFFD8C2BF),
  surfaceContainerLow = Color(0xFF231918),
  surfaceContainer = Color(0xFF271D1C),
  surfaceContainerHigh = Color(0xFF322826),
  surfaceContainerHighest = Color(0xFF3D3231)
)

fun tonalSpotScheme(palette: TonalSpotPalette, darkTheme: Boolean): ColorScheme {
  return when (palette) {
    TonalSpotPalette.INDIGO -> if (darkTheme) IndigoDarkScheme else IndigoLightScheme
    TonalSpotPalette.EMERALD -> if (darkTheme) SageTealDarkScheme else SageTealLightScheme
    TonalSpotPalette.TEAL -> if (darkTheme) VioletIrisDarkScheme else VioletIrisLightScheme
    TonalSpotPalette.ROSE -> if (darkTheme) CoralClayDarkScheme else CoralClayLightScheme
  }
}
