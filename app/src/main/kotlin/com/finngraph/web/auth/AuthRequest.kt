package com.finngraph.web.auth

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
