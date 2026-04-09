package com.sagar.prosync.data

import android.content.Context
import android.util.Log
import com.sagar.prosync.data.api.AuthApi
import com.sagar.prosync.data.api.LoginRequest
import com.sagar.prosync.data.api.RegisterRequest
import com.sagar.prosync.device.DeviceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException

sealed class AuthResult {
    object Success : AuthResult()
    object PendingApproval : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthRepository(private val context: Context) {
    private val api = ApiClient.create(context).create(AuthApi::class.java)
    private val sessionStore = SessionStore(context)

    suspend fun login(username: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            // Include device info to prevent 422 errors
            val response = api.login(username, password, DeviceManager.getOrCreateDeviceId(context), DeviceManager.getDeviceName())
            sessionStore.saveToken(response.access_token)
            AuthResult.Success
        } catch (e: HttpException) {
            when (e.code()) {
                403 -> AuthResult.PendingApproval
                401 -> AuthResult.Error("Invalid credentials")
                422 -> AuthResult.Error("Server rejected data format (422)")
                else -> AuthResult.Error("Server error: ${e.code()}")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun register(username: String, email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            api.register(username, email, password, DeviceManager.getDeviceName())
            AuthResult.Success
        } catch (e: HttpException) {
            // 1. Catch HTTP errors specifically (like 400 Bad Request)
            val errorMessage = try {
                // 2. Read the error body sent by FastAPI
                val errorBodyString = e.response()?.errorBody()?.string()
                if (errorBodyString != null) {
                    // 3. Extract the "detail" string from {"detail": "User already exists"}
                    JSONObject(errorBodyString).getString("detail")
                } else {
                    "Registration failed. User may already exist."
                }
            } catch (parseException: Exception) {
                // Fallback if parsing fails
                "Server error: ${e.code()}"
            }
            AuthResult.Error(errorMessage)
        } catch (e: Exception) {
            // Catch network timeouts or no internet
            AuthResult.Error("Network error. Please check your connection.")
        }
    }
}