package com.sagar.prosync.device

import android.content.Context
import com.sagar.prosync.data.ApiClient
import com.sagar.prosync.data.SessionStore
import com.sagar.prosync.data.api.DeviceApi
import com.sagar.prosync.data.api.DeviceRegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class DeviceRegistrationResult {
    object Success : DeviceRegistrationResult()
    object Blocked : DeviceRegistrationResult()
    data class Error(val message: String) : DeviceRegistrationResult()
}

class DeviceRegistrationRepository(
    private val context: Context
) {

    private val sessionStore = SessionStore(context)
    private val api = ApiClient.create(context).create(DeviceApi::class.java)

    suspend fun register(): DeviceRegistrationResult = withContext(Dispatchers.IO) {
        val deviceId = DeviceManager.getOrCreateDeviceId(context)
        val deviceName = DeviceManager.getDeviceName()

        api.registerDevice(
            DeviceRegisterRequest(device_uid = deviceId, device_name = deviceName)
        )

        return@withContext try {
            val res = api.registerDevice(
                DeviceRegisterRequest(
                    device_uid = deviceId,
                    device_name = deviceName
                )
            )

            when (res.status) {
                "registered", "already_registered" ->
                    DeviceRegistrationResult.Success

                else ->
                    DeviceRegistrationResult.Error("Unknown response")
            }

        } catch (e: retrofit2.HttpException) {
            if (e.code() == 403) {
                DeviceRegistrationResult.Blocked
            } else {
                DeviceRegistrationResult.Error("Server error ${e.code()}")
            }
        } catch (e: Exception) {
            DeviceRegistrationResult.Error(e.message ?: "Network error")
        }
    }
}
