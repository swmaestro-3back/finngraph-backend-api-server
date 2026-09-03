package com.finngraph.web.auth

import com.fasterxml.jackson.annotation.JsonProperty
import com.finngraph.web.user.MeResponse

data class AuthTokenResponse(
    val accessToken: String,
    val expiresIn: Long,
    @get:JsonProperty("isNewUser") val isNewUser: Boolean,
    val user: MeResponse,
)
