package com.watchsos.data.api

import com.watchsos.BuildConfig
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    fun create(): EmergencyApi {
        check(BuildConfig.DEBUG || BuildConfig.BASE_URL.startsWith("https://")) { "Release requiere HTTPS" }
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().serializeNulls().create()))
            .build().create(EmergencyApi::class.java)
    }
}
