package com.watchsos.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Text
import com.watchsos.BuildConfig
import com.watchsos.model.EmergencyState
import com.watchsos.model.EmergencyStatus
import com.watchsos.ui.components.PermissionAction

private val Canvas = Color.Black
private val Ink = Color(0xFFF5F5F7)
private val Secondary = Color(0xFF8E8E93)
private val Red = Color(0xFFFF453A)
private val Green = Color(0xFF30D158)
private val Gold = Color(0xFFFF9F0A)
private val Blue = Color(0xFF0A84FF)

@Composable
fun EmergencyScreen(
    state: EmergencyState,
    configurationReady: Boolean,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val running = state.status in setOf(EmergencyStatus.STARTING, EmergencyStatus.ACTIVE)
    val accent = when {
        running -> Red
        state.pending -> Gold
        state.status == EmergencyStatus.FINISHED -> Green
        else -> Blue
    }

    Box(Modifier.fillMaxSize().background(Canvas), contentAlignment = Alignment.Center) {
        when {
            state.status == EmergencyStatus.STOPPING -> CenterMessage(
                label = "CERRANDO ALERTA",
                title = "Un momento",
                detail = "Guardando y enviando tus datos",
                accent = Blue,
                progress = true,
            )
            state.pending -> PendingState(state, onRetry)
            running -> ActiveState(state, onFinish, accent)
            state.status == EmergencyStatus.FINISHED -> FinishedState(state, configurationReady, onStart)
            else -> HomeState(
                state = state,
                configurationReady = configurationReady,
                onStart = onStart,
                onOpenLocation = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            )
        }
    }
}

@Composable
private fun HomeState(
    state: EmergencyState,
    configurationReady: Boolean,
    onStart: () -> Unit,
    onOpenLocation: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (state.status == EmergencyStatus.ERROR && !state.pending) state.syncMessage ?: "Revisa los permisos"
                else "Emergencia SOS",
            fontSize = 12.sp, letterSpacing = 0.1.sp,
            fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(listOf(Red.copy(alpha = 0.15f), Color.Transparent)), CircleShape,
                    ),
                )
                if (configurationReady) PermissionAction(onReady = onStart)
                else Text("…", color = Secondary, fontSize = 28.sp)
            }
            Text("Mantén pulsado 2 s", fontSize = 10.sp, letterSpacing = 0.1.sp,
                fontWeight = FontWeight.Medium, color = Secondary)
        }

        Text(
            text = if (BuildConfig.DEMO) "Ubicación simulada" else "GPS del reloj",
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onOpenLocation)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            color = Secondary,
            fontSize = 9.sp, letterSpacing = 0.1.sp, fontWeight = FontWeight.Medium,
            maxLines = 1,
        )

    }
}

@Composable
private fun ActiveState(state: EmergencyState, onFinish: () -> Unit, accent: Color) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        LabelWithDot(if (state.status == EmergencyStatus.STARTING) "INICIANDO" else "EMERGENCIA ACTIVA", accent)

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (state.serverConfirmed) "AYUDA EN CAMINO" else "ALERTA ENVIÁNDOSE",
                fontSize = 16.sp, letterSpacing = 0.4.sp, lineHeight = 19.sp,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = Ink)
            Text(
                text = buildString {
                    append(if (state.recording) "● Audio activo" else "Audio no disponible")
                    append("\n")
                    append(state.locationMessage.ifBlank { "Buscando ubicación" })
                },
                fontSize = 10.sp, lineHeight = 15.sp, textAlign = TextAlign.Center,
                color = Secondary, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Text(if (state.serverConfirmed) "SERVIDOR CONFIRMADO" else "ESPERANDO AL SERVIDOR",
                fontSize = 8.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold,
                color = if (state.serverConfirmed) Green else Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        ActionButton("FINALIZAR", Red, onFinish)
    }
}

@Composable
private fun PendingState(state: EmergencyState, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        LabelWithDot("DATOS A SALVO", Gold)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("SIN CONEXIÓN", fontSize = 17.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = Ink)
            Text(state.syncMessage ?: "La alerta sigue guardada en el reloj.", fontSize = 10.sp,
                lineHeight = 14.sp, textAlign = TextAlign.Center, color = Secondary,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        ActionButton("REINTENTAR ENVÍO", Gold, onRetry)
    }
}

@Composable
private fun FinishedState(state: EmergencyState, configurationReady: Boolean, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        LabelWithDot("CIERRE CONFIRMADO", Green)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ALERTA\nFINALIZADA", fontSize = 17.sp, lineHeight = 20.sp, letterSpacing = 0.6.sp,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = Ink)
            Text(state.syncMessage ?: "El servidor confirmó el cierre.", fontSize = 9.sp, lineHeight = 12.sp,
                textAlign = TextAlign.Center, color = Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (configurationReady) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PermissionAction(onReady = onStart)
                Text("MANTÉN PARA OTRA ALERTA", fontSize = 7.sp, letterSpacing = 0.6.sp, color = Secondary)
            }
        }
    }
}

@Composable
private fun CenterMessage(label: String, title: String, detail: String, accent: Color, progress: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress) CircularProgressIndicator(Modifier.size(30.dp), indicatorColor = accent)
        Spacer(Modifier.height(12.dp))
        LabelWithDot(label, accent)
        Spacer(Modifier.height(8.dp))
        Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(5.dp))
        Text(detail, color = Secondary, fontSize = 10.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LabelWithDot(label: String, accent: Color) {
    Row(
        Modifier.clip(CircleShape).background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(5.dp).background(accent, CircleShape))
        Text(label, fontSize = 7.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold,
            color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(40.dp).clip(CircleShape)
            .background(accent.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = accent, fontSize = 9.sp, letterSpacing = 1.sp,
            fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}
