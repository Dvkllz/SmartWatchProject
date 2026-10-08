package com.watchsos.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

private val Night = Color(0xFF080D16)
private val Panel = Color(0xFF111B29)
private val SoftWhite = Color(0xFFF3F6FB)
private val Muted = Color(0xFF99A8BA)
private val Coral = Color(0xFFFF5964)
private val Mint = Color(0xFF62E6B7)
private val Amber = Color(0xFFFFC46A)

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
    val palette = when {
        running -> Coral
        state.pending -> Amber
        state.status == EmergencyStatus.FINISHED -> Mint
        else -> Color(0xFF76A9FF)
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF152235), Night, Color(0xFF05080D)),
                radius = 480f,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().padding(6.dp)
                .border(1.dp, Color.White.copy(alpha = 0.055f), CircleShape),
        )

        when {
            state.status == EmergencyStatus.STOPPING -> CenterState(
                eyebrow = "WATCH SOS",
                title = "CERRANDO ALERTA",
                subtitle = "Deteniendo sensores y\nenviando datos guardados",
                accent = Color(0xFF76A9FF),
                icon = "◈",
                middle = { CircularProgressIndicator(Modifier.size(38.dp), indicatorColor = Color(0xFF76A9FF)) },
            )

            state.pending -> PendingState(state, onRetry, palette)

            running -> ActiveState(state, onFinish, palette)

            state.status == EmergencyStatus.FINISHED -> FinishedState(
                state = state,
                configurationReady = configurationReady,
                onStart = onStart,
            )

            else -> HomeState(
                state = state,
                configurationReady = configurationReady,
                onStart = onStart,
                onOpenLocationSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            )
        }
    }
}

@Composable
private fun HomeState(
    state: EmergencyState,
    configurationReady: Boolean,
    onStart: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 21.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("WATCH SOS", modifier = Modifier.fillMaxWidth(), fontSize = 11.sp,
            letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold, color = SoftWhite, textAlign = TextAlign.Center)

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("PROTECCIÓN ACTIVA", fontSize = 10.sp, lineHeight = 12.sp,
                letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = Muted, textAlign = TextAlign.Center)
            if (configurationReady) PermissionAction(onReady = onStart)
            else Text("Preparando seguridad…", color = Muted, fontSize = 11.sp)
            Text("MANTÉN 2 S PARA ACTIVAR", fontSize = 8.sp, letterSpacing = 0.8.sp,
                fontWeight = FontWeight.SemiBold, color = Color(0xFFD5DCE6))
        }

        Row(
            modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.055f))
                .clickable(role = Role.Button, onClick = onOpenLocationSettings)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("⌖", fontSize = 13.sp, color = Color(0xFF76A9FF))
            Text("UBICACIÓN", fontSize = 9.sp, letterSpacing = 0.7.sp, color = Muted)
            Text("·", color = Muted, fontSize = 10.sp)
            Text(if (BuildConfig.DEMO) "DEMO" else "REAL", fontSize = 9.sp,
                letterSpacing = 0.7.sp, color = if (BuildConfig.DEMO) Amber else Mint, fontWeight = FontWeight.Bold)
        }

        if (state.status == EmergencyStatus.ERROR && !state.pending) {
            state.syncMessage?.let { Text(it, color = Amber, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 2) }
        }
    }
}

@Composable
private fun ActiveState(state: EmergencyState, onFinish: () -> Unit, accent: Color) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            StatusPill(if (state.status == EmergencyStatus.STARTING) "ACTIVANDO" else "SOS ACTIVO", accent)
            Text(if (state.status == EmergencyStatus.STARTING) "PREPARANDO ALERTA" else "AYUDA EN CAMINO",
                fontSize = 12.sp, letterSpacing = 0.9.sp, fontWeight = FontWeight.Bold, color = SoftWhite, textAlign = TextAlign.Center)
        }

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            InfoLine("●", if (state.recording) {
                if (BuildConfig.DEMO) "AUDIO DE PRUEBA" else "MICRÓFONO ACTIVO"
            } else "AUDIO NO DISPONIBLE", if (state.recording) Mint else Amber)
            InfoLine("⌖", state.locationMessage.ifBlank { "Buscando ubicación" }, Color(0xFF76A9FF))
            val sync = when {
                state.serverConfirmed -> "RECIBIDA POR SERVIDOR"
                else -> state.syncMessage?.uppercase() ?: "ENVIANDO ALERTA"
            }
            InfoLine("↗", sync, if (state.serverConfirmed) Mint else Amber)
        }

        ActionPill(label = "FINALIZAR ALERTA", accent = accent, onClick = onFinish)
    }
}

@Composable
private fun PendingState(state: EmergencyState, onRetry: () -> Unit, accent: Color) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        StatusPill("GUARDADO EN EL RELOJ", accent)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("ENVÍO\nPENDIENTE", fontSize = 19.sp, lineHeight = 22.sp, letterSpacing = 0.6.sp,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = SoftWhite)
            Text(state.syncMessage ?: "Tus datos siguen guardados y listos para enviar.",
                fontSize = 10.sp, lineHeight = 13.sp, textAlign = TextAlign.Center, color = Muted, maxLines = 3,
                overflow = TextOverflow.Ellipsis)
        }
        ActionPill(label = "REINTENTAR ENVÍO", accent = accent, onClick = onRetry)
    }
}

@Composable
private fun FinishedState(state: EmergencyState, configurationReady: Boolean, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        StatusPill("CERRADO", Mint)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("ALERTA\nFINALIZADA", fontSize = 19.sp, lineHeight = 22.sp, letterSpacing = 0.6.sp,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = SoftWhite)
            Text(state.syncMessage ?: "El servidor confirmó el cierre.", fontSize = 10.sp, lineHeight = 13.sp,
                textAlign = TextAlign.Center, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (configurationReady) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                PermissionAction(onReady = onStart)
                Text("MANTÉN PARA NUEVA ALERTA", fontSize = 8.sp, letterSpacing = 0.6.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun CenterState(
    eyebrow: String,
    title: String,
    subtitle: String,
    accent: Color,
    icon: String,
    middle: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 25.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(icon, fontSize = 24.sp, color = accent)
        Spacer(Modifier.height(9.dp))
        Text(eyebrow, fontSize = 9.sp, letterSpacing = 1.5.sp, color = Muted)
        Spacer(Modifier.height(5.dp))
        Text(title, fontSize = 14.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, color = SoftWhite)
        Spacer(Modifier.height(13.dp))
        middle()
        Spacer(Modifier.height(10.dp))
        Text(subtitle, fontSize = 10.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, color = Muted)
    }
}

@Composable
private fun StatusPill(label: String, accent: Color) {
    Row(
        modifier = Modifier.clip(CircleShape).background(accent.copy(alpha = 0.13f))
            .border(1.dp, accent.copy(alpha = 0.26f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(5.dp).background(accent, CircleShape))
        Text(label, fontSize = 8.sp, letterSpacing = 0.65.sp, fontWeight = FontWeight.Bold, color = accent,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoLine(icon: String, label: String, tint: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(icon, fontSize = 12.sp, color = tint, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 9.sp, letterSpacing = 0.25.sp, color = SoftWhite, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionPill(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(43.dp).clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.82f))))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 10.sp, letterSpacing = 0.7.sp, fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF10151C), maxLines = 1)
    }
}
