package com.example.flikky.ui.theme

import android.app.Activity
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.example.flikky.data.settings.DarkMode
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.ThemeMode

val LocalFlikkySettings = compositionLocalOf { FlikkySettings() }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlikkyTheme(settings: FlikkySettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (settings.darkMode) {
        DarkMode.SYSTEM -> systemDark
        DarkMode.LIGHT  -> false
        DarkMode.DARK   -> true
    }
    val base = when {
        settings.themeMode == ThemeMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (useDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        else -> presetScheme(settings.presetTheme, useDark)
    }
    val scheme = if (settings.amoled && useDark) amoledOverride(base) else base

    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(useDark) {
            val window = (view.context as Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                // 浅色主题 → 深色系统栏图标；深色主题 → 浅色图标。状态栏与导航栏一起设，
                // 配合 isNavigationBarContrastEnforced=false 让透明系统栏上的图标始终可读。
                isAppearanceLightStatusBars = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
            onDispose {}
        }
    }

    // 全局动画速度倍率 = 用户设置（阶段 2.1 接入，暂默认 1.0）× 系统 animatorDurationScale。
    // 系统把动画关掉时 animatorDurationScale==0 → 倍率 0 → Motion 退化 snap，自动尊重 reduce-motion。
    val context = LocalContext.current
    val systemAnimScale = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    val motionScale = systemAnimScale.coerceIn(0f, 5f)

    CompositionLocalProvider(
        LocalFlikkySettings provides settings,
        LocalMotionScale provides motionScale,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = Typography,
            shapes = FlikkyShapes,
            content = content,
        )
    }
}
