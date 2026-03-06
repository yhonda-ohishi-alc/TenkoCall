package com.example.tenkocall.data.remote

import com.example.tenkocall.data.model.*
import retrofit2.http.Body
import retrofit2.http.POST

interface TenkoApi {
    @POST("/api/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @POST("/api/tenko")
    suspend fun sendTenko(@Body request: TenkoRequest): TenkoResponse
}
