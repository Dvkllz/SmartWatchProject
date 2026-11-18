package com.watchsos.ui.screens

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Text
import com.watchsos.AppLanguage
import com.watchsos.model.RecordingItem
import com.watchsos.model.RecordingsState
import com.watchsos.ui.theme.LocalSosPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun RecordingsScreen(
    state: RecordingsState,
    language: AppLanguage,
    onRefresh: () -> Unit,
    onPrepare: suspend (RecordingItem) -> String,
    onClose: () -> Unit,
) {
    val palette = LocalSosPalette.current
    var playingId by remember { mutableStateOf<String?>(null) }
    var loadingId by remember { mutableStateOf<String?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) { onDispose { player?.release() } }

    fun play(item: RecordingItem) {
        if (playingId == item.uploadId || loadingId == item.uploadId) {
            player?.release(); player = null; playingId = null; loadingId = null
            return
        }
        player?.release()
        playbackError = null
        playingId = null
        loadingId = item.uploadId
        scope.launch {
            try {
                val path = onPrepare(item)
                if (loadingId != item.uploadId) return@launch
                val next = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA).build())
                    setDataSource(path)
                    setOnPreparedListener { prepared -> loadingId = null; playingId = item.uploadId; prepared.start() }
                    setOnCompletionListener { playingId = null; loadingId = null; it.release(); player = null }
                    setOnErrorListener { failed, _, _ ->
                        playingId = null; loadingId = null
                        playbackError = if (language == AppLanguage.ES) "No se pudo reproducir" else "Playback failed"
                        failed.release(); player = null; true
                    }
                    prepareAsync()
                }
                player = next
            } catch (_: Exception) {
                loadingId = null
                playbackError = if (language == AppLanguage.ES) "No se pudo descargar" else "Download failed"
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(palette.background).verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CircleTextButton("‹", if (language == AppLanguage.ES) "Volver" else "Back", onClose)
            Text(if (language == AppLanguage.ES) "Grabaciones" else "Recordings",
                Modifier.weight(1f), color = palette.foreground, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            CircleTextButton("↻", if (language == AppLanguage.ES) "Actualizar" else "Refresh", onRefresh)
        }
        Spacer(Modifier.height(8.dp))
        when {
            state.loading -> CircularProgressIndicator(Modifier.size(28.dp), indicatorColor = palette.accent)
            state.error != null -> {
                Text(if (language == AppLanguage.ES) state.error else "Could not load recordings",
                    color = palette.warning, fontSize = 10.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                CompactButton(if (language == AppLanguage.ES) "Reintentar" else "Retry", onRefresh)
            }
            state.items.isEmpty() -> Text(if (language == AppLanguage.ES) "Aún no hay audios" else "No recordings yet",
                color = palette.secondary, fontSize = 11.sp, textAlign = TextAlign.Center)
            else -> state.items.forEach { item ->
                RecordingRow(item, language, playingId == item.uploadId, loadingId == item.uploadId) { play(item) }
                Spacer(Modifier.height(6.dp))
            }
        }
        playbackError?.let { Text(it, color = palette.warning, fontSize = 9.sp, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun RecordingRow(item: RecordingItem, language: AppLanguage, playing: Boolean, loading: Boolean, onClick: () -> Unit) {
    val palette = LocalSosPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface)
            .border(1.dp, palette.secondary.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (playing) palette.positive else palette.accent),
            contentAlignment = Alignment.Center) {
            Text(if (loading) "…" else if (playing) "■" else "▶", color = Color.White, fontSize = 9.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            val alert = item.emergencyId.take(4).uppercase()
            val title = "$alert · Audio ${item.segmentNumber}"
            val status = if (language == AppLanguage.ES) {
                if (item.emergencyStatus == "FINISHED") "Finalizada" else "Activa"
            } else if (item.emergencyStatus == "FINISHED") "Finished" else "Active"
            Text(title, color = palette.foreground, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$status · ${formatTime(item.emergencyReceivedAt)} · ${item.bytes / 1024} KB",
                color = palette.secondary, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CircleTextButton(symbol: String, description: String, onClick: () -> Unit) {
    val palette = LocalSosPalette.current
    Box(Modifier.size(28.dp).clip(CircleShape).background(palette.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(symbol, color = palette.foreground, fontSize = 16.sp)
    }
}

@Composable
private fun CompactButton(label: String, onClick: () -> Unit) {
    val palette = LocalSosPalette.current
    Box(Modifier.height(30.dp).clip(CircleShape).background(palette.accent).clickable(onClick = onClick)
        .padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("dd MMM · HH:mm").format(Instant.parse(value).atZone(ZoneId.systemDefault()))
}.getOrDefault(value.take(16))
