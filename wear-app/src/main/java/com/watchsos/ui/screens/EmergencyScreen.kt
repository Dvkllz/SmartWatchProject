package com.watchsos.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.watchsos.BuildConfig
import com.watchsos.model.EmergencyState
import com.watchsos.model.EmergencyStatus
import com.watchsos.ui.components.PermissionAction

@Composable
fun EmergencyScreen(state: EmergencyState, configurationReady: Boolean, onStart: () -> Unit, onFinish: () -> Unit, onRetry: () -> Unit) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val context = LocalContext.current
    val running = state.status in setOf(EmergencyStatus.STARTING, EmergencyStatus.ACTIVE)
    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 28.dp), autoCentering = null) {
            item {
                Text(when (state.status) {
                    EmergencyStatus.STARTING -> "INICIANDO SOS"
                    EmergencyStatus.ACTIVE -> "EMERGENCIA ACTIVA"
                    EmergencyStatus.STOPPING -> "FINALIZANDO"
                    EmergencyStatus.FINISHED -> "ALERTA FINALIZADA"
                    EmergencyStatus.ERROR -> if (state.pending) "ENVÍO PENDIENTE" else "REVISAR SOS"
                    EmergencyStatus.IDLE -> if (BuildConfig.DEMO) "SEGURIDAD · DEMO" else "SEGURIDAD"
                }, textAlign = TextAlign.Center, fontSize = 14.sp,
                    color = if (running) MaterialTheme.colors.primary else Color.White)
            }
            if (running) {
                item { Text(if (state.recording) { if (BuildConfig.DEMO) "● AUDIO DEMO" else "● REC" } else "AUDIO NO ACTIVO",
                    color = MaterialTheme.colors.primary, fontSize = 13.sp) }
                item { Text(state.locationMessage, textAlign = TextAlign.Center, fontSize = 12.sp) }
                item { Chip(onClick = onFinish, modifier = Modifier.fillMaxWidth(), label = { Text("FINALIZAR ALERTA", fontSize = 12.sp) }) }
                item { Text(if (state.serverConfirmed) "Alerta recibida por servidor" else "Servidor aún sin confirmar",
                    textAlign = TextAlign.Center, fontSize = 12.sp) }
            } else if (state.status == EmergencyStatus.STOPPING) {
                item { CircularProgressIndicator(Modifier.size(36.dp)) }
                item { Text("Deteniendo captura y enviando", fontSize = 12.sp, textAlign = TextAlign.Center) }
            } else if (state.pending) {
                item { Chip(onClick = onRetry, modifier = Modifier.fillMaxWidth(), label = { Text("REINTENTAR CIERRE", fontSize = 12.sp) }) }
            } else if (configurationReady) {
                item { Column(horizontalAlignment = Alignment.CenterHorizontally) { PermissionAction(onReady = onStart) } }
                item { Text("Mantén 2 segundos\npara pedir ayuda", textAlign = TextAlign.Center, fontSize = 12.sp) }
            }
            state.audioMessage?.takeIf { it != "AUDIO DEMO" }?.let { message ->
                item { Text(message, textAlign = TextAlign.Center, fontSize = 12.sp) }
            }
            state.syncMessage?.let { message -> item { Text(message, textAlign = TextAlign.Center, fontSize = 12.sp) } }
            if (BuildConfig.DEMO) item { Text("DEMO · audio sintético", fontSize = 10.sp, color = Color.LightGray) }
            if (!running && state.status != EmergencyStatus.STOPPING) item {
                CompactChip(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }, label = { Text("UBICACIÓN", fontSize = 11.sp) })
            }
        }
    }
}
