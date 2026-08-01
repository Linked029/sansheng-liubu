package com.lyihub.archiveassistant.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

private val DarkColorScheme =
  darkColorScheme(
    primary = DarkTerracotta,
    onPrimary = ImperialIvory,
    secondary = DarkCoral,
    onSecondary = ImperialIvory,
    tertiary = DarkMuted,
    onTertiary = ImperialIvory,
    background = DarkParchment,
    onBackground = ImperialIvory,
    surface = DarkIvory,
    onSurface = ImperialUmber,
    surfaceVariant = DarkWarmSurfaceVariant,
    onSurfaceVariant = ImperialUmber,
    outline = DarkWarmBorder,
    outlineVariant = DarkWarmSurface,
    inverseSurface = ImperialIvory,
    inverseOnSurface = ImperialUmber,
    error = ImperialCinnabar,
    onError = ImperialIvory,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Terracotta,
    onPrimary = Ivory,
    secondary = Coral,
    onSecondary = Ivory,
    tertiary = Muted,
    onTertiary = Ivory,
    background = Parchment,
    onBackground = DarkText,
    surface = Ivory,
    onSurface = DarkText,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = Muted,
    outline = WarmBorder,
    outlineVariant = WarmSurface,
    inverseSurface = DarkText,
    inverseOnSurface = Ivory,
    error = ImperialCinnabar,
    onError = Ivory,
  )

@Composable
fun ArchiveAssistantTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) {
          androidx.compose.material3.dynamicDarkColorScheme(context)
        } else {
          androidx.compose.material3.dynamicLightColorScheme(context)
        }
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val activity = view.context as? ComponentActivity ?: return@SideEffect
      val systemBarColor = ImperialIvory.toArgb()
      val systemBarStyle =
        if (darkTheme) {
          SystemBarStyle.dark(systemBarColor)
        } else {
          SystemBarStyle.light(systemBarColor, systemBarColor)
        }
      activity.enableEdgeToEdge(
        statusBarStyle = systemBarStyle,
        navigationBarStyle = systemBarStyle,
      )
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content,
  )
}
