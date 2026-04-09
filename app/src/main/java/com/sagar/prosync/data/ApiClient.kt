package com.sagar.prosync.data

import android.content.Context
import com.sagar.prosync.device.DeviceManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private const val BASE_URL = "http://192.168.0.181:8000/"

    fun create(context: Context): Retrofit {

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = Interceptor { chain ->
            val session = SessionStore(context)

            val builder = chain.request().newBuilder()

            // ✅ THIS IS MANDATORY
            session.getToken()?.let { token ->
                builder.addHeader("Authorization", "Bearer $token")
            }

            builder.addHeader(
                "X-Device-ID",
                DeviceManager.getOrCreateDeviceId(context)
            )

            chain.proceed(builder.build())
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}

