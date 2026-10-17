package com.finngraph.web.security

import com.finngraph.security.JwtProperties
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RefreshTokenCookies(private val properties: JwtProperties) {

    fun cookie(token: String): ResponseCookie = baseCookie(token).maxAge(properties.refreshTtl).build()

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
    }
}
