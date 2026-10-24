package com.watchsos.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchsos.AppLanguage
import androidx.wear.compose.material.Text
import kotlinx.coroutines.*

private const val HOLD_MILLIS = 2_000L

@Composable
fun HoldSosButton(language: AppLanguage, onActivate: () -> Unit) {
    val activate by rememberUpdatedState(onActivate)
    var pressing by remember { mutableStateOf(false) }
    val buttonColor by animateColorAsState(
        targetValue = if (pressing) Color(0xFFFF6B63) else Color(0xFFFF453A),
        animationSpec = tween(150),
        label = "sos-button-color",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressing) 0.94f else 1f,
        animationSpec = tween(150),
        label = "sos-button-press",
    )
    Box(
        Modifier.size(80.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(16.dp, CircleShape, clip = false, ambientColor = Color(0x55FF453A), spotColor = Color(0x55FF453A))
            .background(Brush.verticalGradient(listOf(buttonColor.copy(alpha = 0.92f), buttonColor)), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.42f), CircleShape)
            .semantics {
                contentDescription = if (language == AppLanguage.ES) "SOS. Mantén presionado dos segundos" else "SOS. Press and hold for two seconds"
                role = Role.Button
                onLongClick(label = if (language == AppLanguage.ES) "Activar SOS" else "Activate SOS") { activate(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    coroutineScope {
                        pressing = true
                        val hold = launch { delay(HOLD_MILLIS); activate() }
                        try { tryAwaitRelease() } finally { hold.cancel(); pressing = false }
                    }
                })
            }, contentAlignment = Alignment.Center
    ) {
        Text(if (pressing) "…" else "SOS", fontSize = 25.sp, letterSpacing = 1.sp,
            color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
    }
}
