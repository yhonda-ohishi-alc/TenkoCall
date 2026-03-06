package com.example.tenkocall.data.model

data class TenkoRequest(
    val phone_number: String,
    val driver_name: String,
    val latitude: Double,
    val longitude: Double
)

data class TenkoResponse(
    val success: Boolean,
    val call_number: String?,
    val error: String?
)

data class RegisterRequest(
    val phone_number: String,
    val driver_name: String
)

data class RegisterResponse(
    val success: Boolean,
    val driver_id: Int?,
    val call_number: String?,
    val error: String?
)
