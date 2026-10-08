package com.watchsos.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

@Composable
fun SosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = Colors(primary = Color(0xFFFF453A), background = Color.Black), content = content)
}
