package com.watchsos.data.api

import com.watchsos.model.LocationSample
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

data class CreateEmergencyRequest(
    val userId: String,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: String,
    val status: String = "ACTIVE",
    val simulated: Boolean,
)
data class EmergencyResponse(val emergencyId: String, val status: String)
data class FinishRequest(val timestamp: String)

interface EmergencyApi {
    @POST("api/emergencies")
    suspend fun create(@Header("Idempotency-Key") key: String, @Body body: CreateEmergencyRequest): EmergencyResponse

    @POST("api/emergencies/{id}/location")
    suspend fun location(@Path("id") id: String, @Body body: LocationSample)

    @Multipart
    @POST("api/emergencies/{id}/audio")
    suspend fun audio(@Path("id") id: String, @Header("X-Upload-Id") uploadId: String,
        @Part audio: MultipartBody.Part, @Part("simulated") simulated: RequestBody)

    @POST("api/emergencies/{id}/finish")
    suspend fun finish(@Path("id") id: String, @Body body: FinishRequest): EmergencyResponse
}
