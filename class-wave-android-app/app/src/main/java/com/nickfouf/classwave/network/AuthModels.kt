package com.nickfouf.classwave.network

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val password: String,
    val role: String
)

data class AuthResponse(
    val _id: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val iconColor: String?,
    val token: String
)

data class UserProfileResponse(
    val _id: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val role: String,
    val iconColor: String?
)

data class UpdateProfileRequest(
    val firstName: String,
    val lastName: String,
    val iconColor: String?
)
