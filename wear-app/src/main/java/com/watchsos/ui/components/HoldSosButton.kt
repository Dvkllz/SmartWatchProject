package com.watchsos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import kotlinx.coroutines.*

private const val HOLD_MILLIS = 2_000L

@Composable
fun HoldSosButton(onActivate: () -> Unit) {
    val activate by rememberUpdatedState(onActivate)
    var pressing by remember { mutableStateOf(false) }
    Box(
        Modifier.size(88.dp)
            .background(
                Brush.verticalGradient(
                    if (pressing) listOf(Color(0xFFFF7782), Color(0xFFE83F4E))
                    else listOf(Color(0xFFFF6572), Color(0xFFE83949)),
                ), CircleShape,
            )
            .border(1.dp, Color.White.copy(alpha = 0.52f), CircleShape)
            .semantics {
                contentDescription = "SOS. Mantén presionado dos segundos"
                role = Role.Button
                onLongClick(label = "Activar SOS") { activate(); true }
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
