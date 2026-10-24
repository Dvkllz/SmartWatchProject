package com.watchsos.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import com.watchsos.AppThemeMode

data class SosPalette(
    val background: Color,
    val surface: Color,
    val foreground: Color,
    val secondary: Color,
    val alert: Color,
    val positive: Color,
    val warning: Color,
    val accent: Color,
)

private val DarkPalette = SosPalette(
    background = Color(0xFF000000), surface = Color(0xFF19191D), foreground = Color(0xFFF5F5F7),
    secondary = Color(0xFF8E8E93), alert = Color(0xFFFF453A), positive = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A), accent = Color(0xFF0A84FF),
)

private val LightPalette = SosPalette(
    background = Color(0xFFF2F2F7), surface = Color.White, foreground = Color(0xFF17171A),
    secondary = Color(0xFF6B6B70), alert = Color(0xFFD70015), positive = Color(0xFF248A3D),
    warning = Color(0xFF9A6700), accent = Color(0xFF0066CC),
)

val LocalSosPalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun SosTheme(theme: AppThemeMode, content: @Composable () -> Unit) {
    val palette = if (theme == AppThemeMode.LIGHT) LightPalette else DarkPalette
    CompositionLocalProvider(LocalSosPalette provides palette) {
        MaterialTheme(colors = Colors(primary = palette.alert, background = palette.background), content = content)
    }
}
