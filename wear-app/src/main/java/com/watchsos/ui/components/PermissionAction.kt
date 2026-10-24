package com.watchsos.ui.components

import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Text
import com.watchsos.util.SosPermissions
import com.watchsos.AppLanguage

@Composable
fun PermissionAction(language: AppLanguage, onReady: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        message = if (language == AppLanguage.ES) {
            if (SosPermissions.hasRequired(context)) "Permisos listos. Mantén SOS de nuevo."
            else "Se necesitan micrófono y ubicación. Puedes reintentar o abrir ajustes."
        } else {
            if (SosPermissions.hasRequired(context)) "Permissions ready. Press and hold SOS again."
            else "Microphone and location are required. Retry or open settings."
        }
    }
    HoldSosButton(language) {
        if (SosPermissions.hasRequired(context)) {
            message = null
            onReady()
        } else permissions.launch(SosPermissions.requestList())
    }
    message?.let {
        Text(it)
        CompactChip(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
        }, label = { Text(if (language == AppLanguage.ES) "AJUSTES" else "SETTINGS") })
    }
}
