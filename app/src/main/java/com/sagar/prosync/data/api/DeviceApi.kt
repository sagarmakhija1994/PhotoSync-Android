package com.sagar.prosync.data.api

import retrofit2.http.Body
import retrofit2.http.POST

data class DeviceRegisterRequest(
    val device_uid: String,
    val device_name: String
)

data class DeviceRegisterResponse(
    val status: String
)

interface DeviceApi {

    @POST("/devices/register")
    suspend fun registerDevice(
        @Body body: DeviceRegisterRequest
    ): DeviceRegisterResponse
}
