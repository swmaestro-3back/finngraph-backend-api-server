package com.finngraph.web.auth

import com.finngraph.composition.account.VerificationPolicies
import com.finngraph.security.JwtProperties
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class VerificationCookies(
    private val jwt: JwtProperties,
    private val policies: VerificationPolicies,
) {

    fun cookie(grant: String): ResponseCookie = baseCookie(grant).maxAge(policies.code.verifiedTtl).build()

    fun expiredCookie(): ResponseCookie = baseCookie("").maxAge(Duration.ZERO).build()

    private fun baseCookie(value: String): ResponseCookie.ResponseCookieBuilder =
        ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(jwt.secureCookie)
            .sameSite("Strict")
            .path(COOKIE_PATH)

    companion object {
        const val COOKIE_NAME = "verification_grant"
        const val COOKIE_PATH = "/api/v1/auth"
    }
}
