package com.finngraph.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.jwt")
data class JwtProperties(
    val secret: String,
    val accessTtl: Duration,
    val refreshTtl: Duration,
    val secureCookie: Boolean = false,
)

@ConfigurationProperties("app.kakao")
data class KakaoProperties(
    val clientId: String,
    val clientSecret: String,
    val adminKey: String,
    val redirectUri: String,
    val tokenUri: String,
    val userUri: String,
    val unlinkUri: String,
)
