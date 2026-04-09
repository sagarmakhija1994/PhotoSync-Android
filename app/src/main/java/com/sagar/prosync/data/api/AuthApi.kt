package com.sagar.prosync.data.api

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

data class LoginRequest(val username: String, val password: String, val device_id: String, val device_name: String)
data class LoginResponse(val access_token: String, val token_type: String)

data class RegisterRequest(val username: String, val email: String, val password: String, val device_name: String)
data class RegisterResponse(val message: String)

interface AuthApi {
    @POST("/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("/auth/login")
    suspend fun login(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("device_uid") deviceId: String,
        @Query("device_name") deviceName: String
    ): LoginResponse

    @POST("/auth/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @POST("/auth/register")
    suspend fun register(
        @Query("username") username: String,
        @Query("email") email: String,
        @Query("password") password: String,
        @Query("device_name") deviceName: String
    ): RegisterResponse
}