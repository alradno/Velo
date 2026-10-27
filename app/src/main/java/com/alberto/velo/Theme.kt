package com.alberto.velo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

val Mint = Color(0xFFBDEBC4)
val Ember = Color(0xFFFFB58D)
private val Dark = darkColorScheme(primary = Mint, onPrimary = Color(0xFF15382C), primaryContainer = Color(0xFF1B372D), onPrimaryContainer = Mint, secondary = Ember, secondaryContainer = Color(0xFF2C433B), onSecondaryContainer = Mint, background = Color(0xFF0C1215), onBackground = Color(0xFFF1F3EB), surface = Color(0xFF141D20), onSurface = Color(0xFFF1F3EB), surfaceVariant = Color(0xFF202B2D), onSurfaceVariant = Color(0xFFA6B6AE), outline = Color(0xFF35433D), error = Color(0xFFFFB4AB))
private val Light = lightColorScheme(primary = Color(0xFF246448), onPrimary = Color.White, primaryContainer = Color(0xFFD9EEDB), onPrimaryContainer = Color(0xFF174730), secondary = Color(0xFF95502C), secondaryContainer = Color(0xFFE0E9DB), onSecondaryContainer = Color(0xFF174730), background = Color(0xFFF4F6EF), onBackground = Color(0xFF15251D), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF15251D), surfaceVariant = Color(0xFFE4EBE0), onSurfaceVariant = Color(0xFF53665A), outline = Color(0xFFC6D1C2))
@Composable fun VeloTheme(settings: Settings = Settings(), content: @Composable () -> Unit) {
    val dark = when (settings.theme) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val density = LocalDensity.current
    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            view.post { WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            }
        }
    }
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * settings.fontScale), LocalVeloMotion provides (settings.animations && android.provider.Settings.Global.getFloat(view.context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f)) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(), content = content)
    }
}

