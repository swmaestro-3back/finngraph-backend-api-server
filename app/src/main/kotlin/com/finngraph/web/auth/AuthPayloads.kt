package com.finngraph.web.auth

import com.fasterxml.jackson.annotation.JsonProperty
import com.finngraph.web.user.MeResponse

data class KakaoLoginRequest(val code: String?)

data class SignupRequest(
    val email: String?,
    val password: String?,
    val nickname: String?,
)

data class LoginRequest(
    val email: String?,
    val password: String?,
)

data class AuthTokenResponse(
    val accessToken: String,
    val expiresIn: Long,
    @get:JsonProperty("isNewUser") val isNewUser: Boolean,
    val user: MeResponse,
)
