package com.watchsos.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

object SosPermissions {
    fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasLocation(context: Context) = granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
        granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasRequired(context: Context) = hasLocation(context) && granted(context, Manifest.permission.RECORD_AUDIO)

    fun locationEnabled(context: Context) = context.getSystemService(LocationManager::class.java).isLocationEnabled

    fun requestList(): Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
}
