package com.finngraph.web.security

import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

@Component
class RefreshTokens(private val properties: JwtProperties) {

    private val random = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    val ttl: Duration = properties.refreshTtl

    fun generate(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    fun hash(token: String): String =
        encoder.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)),
        )

    fun cookie(token: String): ResponseCookie = baseCookie(token).maxAge(ttl).build()

    fun expiredCookie(): ResponseCookie = baseCookie("").maxAge(Duration.ZERO).build()

    private fun baseCookie(value: String): ResponseCookie.ResponseCookieBuilder =
        ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(properties.secureCookie)
            .sameSite("Strict")
            .path(COOKIE_PATH)

    companion object {
        const val COOKIE_NAME = "refresh_token"
        const val COOKIE_PATH = "/api/v1/auth"
        private const val TOKEN_BYTES = 32
    }
}
