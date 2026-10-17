package com.finngraph.composition.port

data class AccessToken(
    val value: String,
    val expiresIn: Long,
)

interface AccessTokenPort {
    fun issue(userId: Long): AccessToken
}
