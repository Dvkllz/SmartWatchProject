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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Text
import com.watchsos.BuildConfig
import com.watchsos.AppLanguage
import com.watchsos.AppThemeMode
import com.watchsos.AppUiPreferences
import com.watchsos.model.EmergencyState
import com.watchsos.model.EmergencyStatus
import com.watchsos.ui.components.PermissionAction
import com.watchsos.ui.theme.LocalSosPalette

@Composable
fun EmergencyScreen(
    state: EmergencyState,
    configurationReady: Boolean,
    preferences: AppUiPreferences,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
    onThemeChange: (AppThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalSosPalette.current
    val running = state.status in setOf(EmergencyStatus.STARTING, EmergencyStatus.ACTIVE)
    val screen = when {
        state.status == EmergencyStatus.STOPPING -> "stopping"
        state.pending -> "pending"
        running -> "active"
        state.status == EmergencyStatus.FINISHED -> "finished"
        else -> "home"
    }

    Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.97f, animationSpec = tween(220))) togetherWith fadeOut(tween(140))
            },
            label = "emergency-screen-transition",
        ) { target ->
            when (target) {
                "stopping" -> CenterMessage(
                    label = tx(preferences.language, "Cerrando alerta", "Closing alert"),
                    title = tx(preferences.language, "Un momento", "One moment"),
                    detail = tx(preferences.language, "Guardando y enviando tus datos", "Saving and sending your data"),
                    accent = palette.accent,
                    progress = true,
                )
                "pending" -> PendingState(state, onRetry, preferences.language)
                "active" -> ActiveState(state, onFinish, preferences.language)
                "finished" -> FinishedState(state, configurationReady, onStart, preferences.language)
                else -> HomeState(
                    state = state,
                    configurationReady = configurationReady,
                    preferences = preferences,
                    onStart = onStart,
                    onOpenLocation = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    onThemeChange = onThemeChange,
                    onLanguageChange = onLanguageChange,
                )
            }
        }
    }
}

@Composable
private fun HomeState(
    state: EmergencyState,
    configurationReady: Boolean,
    preferences: AppUiPreferences,
    onStart: () -> Unit,
    onOpenLocation: () -> Unit,
    onThemeChange: (AppThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    val palette = LocalSosPalette.current
    var showSettings by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(start = 20.dp, top = 9.dp, end = 20.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.padding(end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = if (state.status == EmergencyStatus.ERROR && !state.pending)
                            localize(state.syncMessage ?: tx(preferences.language, "Revisa los permisos", "Check permissions"), preferences.language)
                        else tx(preferences.language, "Emergencia SOS", "Emergency SOS"),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.foreground,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (state.status != EmergencyStatus.ERROR) LabelWithDot(tx(preferences.language, "Lista para ayudarte", "Ready to help"), palette.positive)
                }
                SmallSettingsButton(
                    language = preferences.language,
                    palette = palette,
                    modifier = Modifier.align(Alignment.TopEnd),
                    onClick = { showSettings = true },
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (configurationReady) PermissionAction(preferences.language, onReady = onStart)
                else Text("…", color = palette.secondary, fontSize = 28.sp)
                Text(tx(preferences.language, "Mantén pulsado 2 s", "Press and hold 2 s"), fontSize = 10.sp,
                    fontWeight = FontWeight.Medium, color = palette.secondary)
            }

            Text(
                text = if (BuildConfig.DEMO) tx(preferences.language, "Ubicación simulada", "Simulated location")
                    else tx(preferences.language, "GPS del reloj", "Watch GPS"),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onOpenLocation)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = palette.secondary, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            )
        }

        if (showSettings) SettingsDialog(
            preferences = preferences,
            onThemeChange = onThemeChange,
            onLanguageChange = onLanguageChange,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun ActiveState(state: EmergencyState, onFinish: () -> Unit, language: AppLanguage) {
    val palette = LocalSosPalette.current
    val accent = palette.alert
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        LabelWithDot(if (state.status == EmergencyStatus.STARTING) tx(language, "Iniciando", "Starting") else tx(language, "Emergencia activa", "Emergency active"), accent,
            animated = state.status == EmergencyStatus.ACTIVE)

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (state.serverConfirmed) tx(language, "Ayuda en camino", "Help is on the way") else tx(language, "Enviando alerta", "Sending alert"),
                fontSize = 17.sp, letterSpacing = 0.1.sp, lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = palette.foreground)
            Text(
                text = buildString {
                    append(if (state.recording) tx(language, "Audio activo", "Audio is active") else tx(language, "Audio no disponible", "Audio unavailable"))
                    append("\n")
                    append(localize(state.locationMessage.ifBlank { "Buscando ubicación" }, language))
                },
                fontSize = 10.sp, lineHeight = 15.sp, textAlign = TextAlign.Center,
                color = palette.secondary, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Text(if (state.serverConfirmed) tx(language, "Servidor confirmado", "Server confirmed") else tx(language, "Esperando al servidor", "Waiting for server"),
                fontSize = 9.sp, letterSpacing = 0.1.sp, fontWeight = FontWeight.Medium,
                color = if (state.serverConfirmed) palette.positive else palette.warning, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        ActionButton(tx(language, "Finalizar alerta", "End alert"), accent, onFinish, filled = true)
    }
}

@Composable
private fun PendingState(state: EmergencyState, onRetry: () -> Unit, language: AppLanguage) {
    val palette = LocalSosPalette.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        LabelWithDot(tx(language, "Datos a salvo", "Data is safe"), palette.warning)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(tx(language, "Sin conexión", "Offline"), fontSize = 18.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = palette.foreground)
            Text(localize(state.syncMessage ?: "La alerta sigue guardada en el reloj.", language), fontSize = 10.sp,
                lineHeight = 14.sp, textAlign = TextAlign.Center, color = palette.secondary,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        ActionButton(tx(language, "Reintentar envío", "Retry upload"), palette.accent, onRetry, filled = true)
    }
}

@Composable
private fun FinishedState(state: EmergencyState, configurationReady: Boolean, onStart: () -> Unit, language: AppLanguage) {
    val palette = LocalSosPalette.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        LabelWithDot(tx(language, "Cierre confirmado", "Closure confirmed"), palette.positive)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tx(language, "Alerta finalizada", "Alert finished"), fontSize = 17.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = palette.foreground)
            Text(localize(state.syncMessage ?: "El servidor confirmó el cierre.", language), fontSize = 9.sp, lineHeight = 12.sp,
                textAlign = TextAlign.Center, color = palette.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (configurationReady) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PermissionAction(language, onReady = onStart)
                Text(tx(language, "Mantén SOS para otra alerta", "Hold SOS for a new alert"), fontSize = 8.sp, color = palette.secondary)
            }
        }
    }
}

@Composable
private fun CenterMessage(label: String, title: String, detail: String, accent: Color, progress: Boolean) {
    val palette = LocalSosPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress) CircularProgressIndicator(Modifier.size(30.dp), indicatorColor = accent)
        Spacer(Modifier.height(12.dp))
        LabelWithDot(label, accent)
        Spacer(Modifier.height(8.dp))
        Text(title, color = palette.foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(5.dp))
        Text(detail, color = palette.secondary, fontSize = 10.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LabelWithDot(label: String, accent: Color, animated: Boolean = false) {
    val pulseSpec: androidx.compose.animation.core.AnimationSpec<Float> = if (animated) {
        androidx.compose.animation.core.infiniteRepeatable<Float>(
            animation = tween(durationMillis = 850),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        )
    } else tween(durationMillis = 160)
    val pulse by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (animated) 1f else 0.7f,
        animationSpec = pulseSpec,
        label = "status-pulse",
    )
    Row(
        Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(5.dp).background(accent.copy(alpha = pulse), CircleShape))
        Text(label, fontSize = 9.sp, letterSpacing = 0.1.sp, fontWeight = FontWeight.Medium,
            color = LocalSosPalette.current.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionButton(label: String, accent: Color, onClick: () -> Unit, filled: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().height(40.dp).clip(CircleShape)
            .background(if (filled) accent else accent.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) Color.White else accent, fontSize = 10.sp, letterSpacing = 0.1.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SmallSettingsButton(
    language: AppLanguage,
    palette: com.watchsos.ui.theme.SosPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.padding(end = 11.dp, top = 4.dp).size(27.dp).clip(CircleShape).background(palette.surface)
            .border(1.dp, palette.secondary.copy(alpha = 0.22f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = tx(language, "Configuración", "Settings"); role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Text("⚙", fontSize = 12.sp, color = palette.secondary)
    }
}

@Composable
private fun SettingsDialog(
    preferences: AppUiPreferences,
    onThemeChange: (AppThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalSosPalette.current
    val language = preferences.language
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.52f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).clip(RoundedCornerShape(26.dp))
                    .background(palette.surface).border(1.dp, palette.secondary.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tx(language, "Configuración", "Settings"), modifier = Modifier.weight(1f),
                        color = palette.foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("×", Modifier.clickable(onClick = onDismiss).padding(horizontal = 5.dp),
                        color = palette.secondary, fontSize = 18.sp)
                }
                Text(tx(language, "Apariencia", "Appearance"), color = palette.secondary, fontSize = 9.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OptionChip(tx(language, "Oscuro", "Dark"), preferences.theme == AppThemeMode.DARK,
                        palette, Modifier.weight(1f)) { onThemeChange(AppThemeMode.DARK) }
                    OptionChip(tx(language, "Claro", "Light"), preferences.theme == AppThemeMode.LIGHT,
                        palette, Modifier.weight(1f)) { onThemeChange(AppThemeMode.LIGHT) }
                }
                Text(tx(language, "Idioma", "Language"), color = palette.secondary, fontSize = 9.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OptionChip("Español", language == AppLanguage.ES, palette, Modifier.weight(1f)) { onLanguageChange(AppLanguage.ES) }
                    OptionChip("English", language == AppLanguage.EN, palette, Modifier.weight(1f)) { onLanguageChange(AppLanguage.EN) }
                }
                Box(
                    Modifier.fillMaxWidth().height(28.dp).clip(CircleShape).background(palette.accent)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tx(language, "Listo", "Done"), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, palette: com.watchsos.ui.theme.SosPalette,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(26.dp).clip(CircleShape)
            .background(if (selected) palette.accent else palette.background)
            .border(1.dp, if (selected) palette.accent else palette.secondary.copy(alpha = 0.24f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.White else palette.foreground,
            fontSize = 8.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

private fun tx(language: AppLanguage, spanish: String, english: String) =
    if (language == AppLanguage.ES) spanish else english

private fun localize(value: String, language: AppLanguage): String {
    if (language == AppLanguage.ES) return value
    return when (value) {
        "Buscando ubicación" -> "Getting location"
        "Ubicación recibida" -> "Location received"
        "Ubicación simulada" -> "Simulated location"
        "Permiso de ubicación no disponible" -> "Location permission unavailable"
        "GPS no disponible; alerta sin ubicación nueva" -> "GPS unavailable; alert has no new location"
        "Sesión interrumpida. Finaliza y envía los datos pendientes." -> "Session interrupted. Finish and send pending data."
        "No se pudo leer la sesión o configuración. Los archivos se conservaron." -> "Could not read the session or settings. Your files were kept."
        "Cierre pendiente. Comprueba conexión y backend; los datos siguen guardados." -> "Closure pending. Check your connection; data is still saved."
        "Captura detenida. Cierre pendiente: reintenta el envío." -> "Capture stopped. Closure pending: retry sending."
        "Envío interrumpido. Reintenta el cierre." -> "Upload interrupted. Retry closing the alert."
        "Alerta finalizada en el servidor" -> "Alert closed on the server"
        "Servidor actualizado" -> "Server updated"
        "Sin confirmación reciente; datos pendientes" -> "No recent confirmation; data is pending"
        "No se pudo iniciar. Revisa permisos, espacio y configuración." -> "Could not start. Check permissions, storage, and settings."
        "Micrófono y ubicación necesitan permiso" -> "Microphone and location permission are required"
        "No se pudo iniciar. Abre la app y revisa permisos." -> "Could not start. Open the app and check permissions."
        "No se pudo pedir el cierre. Reintenta desde la app." -> "Could not request closure. Retry from the app."
        "Cierre pendiente" -> "Closure pending"
        else -> value
    }
}
