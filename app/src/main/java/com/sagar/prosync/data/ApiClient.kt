package com.sagar.prosync.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sagar.prosync.device.DeviceManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {

    fun create(context: Context): Retrofit {

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val authInterceptor = Interceptor { chain ->
            val session = SessionStore(context)

            val builder = chain.request().newBuilder()

            // ✅ THIS IS MANDATORY (use access token)
            session.getAccessToken()?.let { token ->
                builder.addHeader("Authorization", "Bearer $token")
            }

            builder.addHeader(
                "X-Device-ID",
                DeviceManager.getOrCreateDeviceId(context)
            )

            chain.proceed(builder.build())
        }

        // 2. The Smart Dual-URL Fallback Interceptor
        val dynamicHostInterceptor = Interceptor { chain ->
            val settingsStore = SettingsStore(context)
            val mainUrl = settingsStore.serverUrl
            val localUrl = settingsStore.localServerUrl
            val useLocal = settingsStore.useLocalServer

            var request = chain.request()

            // ATTEMPT LOCAL CONNECTION FIRST (If enabled and on WiFi)
            if (useLocal && localUrl.isNotBlank()) {
                val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val networkCapabilities = connManager.getNetworkCapabilities(connManager.activeNetwork)
                val isWifi = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

                if (isWifi) {
                    val localHttpUrl = localUrl.toHttpUrlOrNull()
                    if (localHttpUrl != null) {
                        val localRequest = request.newBuilder()
                            .url(request.url.newBuilder()
                                .scheme(localHttpUrl.scheme)
                                .host(localHttpUrl.host)
                                .port(localHttpUrl.port)
                                .build())
                            .build()

                        try {
                            // Fast 2-second timeout. If it's a home network, it responds instantly.
                            // If it hangs (coffee shop WiFi), it catches the exception and falls back to Main URL.
                            return@Interceptor chain
                                .withConnectTimeout(2, TimeUnit.SECONDS)
                                .withReadTimeout(10, TimeUnit.SECONDS)
                                .proceed(localRequest)
                        } catch (e: IOException) {
                            // Local network failed/unreachable. Fall through to Cloudflare block below.
                        }
                    }
                }
            }

            // FALLBACK TO MAIN (CLOUDFLARE/PUBLIC) URL
            val mainHttpUrl = mainUrl.toHttpUrlOrNull()
            if (mainHttpUrl != null) {
                request = request.newBuilder()
                    .url(request.url.newBuilder()
                        .scheme(mainHttpUrl.scheme)
                        .host(mainHttpUrl.host)
                        .port(mainHttpUrl.port)
                        .build())
                    .build()
            }

            chain.proceed(request)
        }

        // Authenticator that attempts to refresh the access token using the refresh token
        val tokenAuthenticator = Authenticator { route, response ->
            val session = SessionStore(context)
            val refreshToken = session.getRefreshToken() ?: return@Authenticator null

            synchronized(this) {
                // Build a minimal client without the authInterceptor to avoid recursion
                val refreshClient = OkHttpClient.Builder()
                    .addInterceptor(dynamicHostInterceptor)
                    .addInterceptor(logging)
                    .build()

                val settingsStore = SettingsStore(context)
                val base = settingsStore.serverUrl.ifBlank { "http://127.0.0.1:8000" }
                val url = if (base.endsWith("/")) base + "auth/refresh" else base + "/auth/refresh"
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = JSONObject().put("refresh_token", refreshToken).toString().toRequestBody(mediaType)

                val req = Request.Builder().url(url).post(body).build()
                val resp = try { refreshClient.newCall(req).execute() } catch (e: Exception) { null }
                if (resp == null || !resp.isSuccessful) {
                    session.clear()
                    return@Authenticator null
                }

                val bodyStr = resp.body?.string() ?: return@Authenticator null
                val json = JSONObject(bodyStr)
                val newAccess = json.optString("access_token", null) ?: return@Authenticator null
                val newRefresh = json.optString("refresh_token", null)

                session.saveAccessToken(newAccess)
                if (newRefresh != null) session.saveRefreshToken(newRefresh)

                // Retry the original request with new access token
                return@Authenticator response.request.newBuilder()
                    .header("Authorization", "Bearer $newAccess")
                    .build()
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(dynamicHostInterceptor) // MUST run before auth/logging so URL is set first
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(logging)
            .build()

        // Retrofit requires a non-null Base URL at compile time, even if we overwrite it per-request.
        val settingsStore = SettingsStore(context)
        val initialBaseUrl = settingsStore.serverUrl.ifBlank { "http://127.0.0.1:8000/" }
        //val initialBaseUrl = settingsStore.serverUrl.ifBlank { "http://192.168.0.181:8000/" }

        return Retrofit.Builder()
            .baseUrl(initialBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}