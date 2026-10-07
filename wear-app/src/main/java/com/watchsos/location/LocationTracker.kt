package com.watchsos.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.watchsos.model.LocationReading
import com.watchsos.model.LocationSample
import com.watchsos.util.EventLog
import com.watchsos.util.SosConfig
import com.watchsos.util.SosPermissions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.time.Instant

class LocationTracker(private val context: Context, private val demo: Boolean, private val allowRealLocation: Boolean = true) {
    // Cancelar el collector cancela también la petición GPS en curso.
    fun updates(): Flow<LocationReading> = flow {
        while (currentCoroutineContext().isActive) {
            emit(initialPosition())
            delay(SosConfig.LOCATION_INTERVAL_MS)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun initialPosition(): LocationReading {
        if (!SosPermissions.hasLocation(context)) return LocationReading(null, "Permiso de ubicación no disponible")
        val cancellation = CancellationTokenSource()
        try {
            val location = if (allowRealLocation && SosPermissions.locationEnabled(context)) withTimeoutOrNull(SosConfig.LOCATION_TIMEOUT_MS) {
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(5_000)
                    .setDurationMillis(SosConfig.LOCATION_TIMEOUT_MS)
                    .build()
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(request, cancellation.token).await()
            } else null
            if (location != null) {
                EventLog.event("location_received")
                return LocationReading(LocationSample(location.latitude, location.longitude, Instant.ofEpochMilli(location.time).toString()))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            EventLog.event("location_unavailable")
        } finally {
            cancellation.cancel()
        }
        return if (demo) {
            EventLog.event("location_demo")
            LocationReading(LocationSample(SosConfig.DEMO_LATITUDE, SosConfig.DEMO_LONGITUDE, simulated = true), "Ubicación simulada")
        } else LocationReading(null, "GPS no disponible; alerta sin ubicación nueva")
    }
}
