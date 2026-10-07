package com.watchsos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        Modifier.size(100.dp).background(if (pressing) Color(0xFFFF5252) else Color(0xFFB71C1C), CircleShape)
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
    ) { Text(if (pressing) "…" else "SOS", fontSize = 28.sp, color = Color.White) }
}
