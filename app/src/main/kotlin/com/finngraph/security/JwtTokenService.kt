package com.finngraph.security

import com.finngraph.composition.port.AccessToken
import com.finngraph.composition.port.AccessTokenPort
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

sealed interface TokenResolution {
    data class Valid(val userId: Long) : TokenResolution
    data object Expired : TokenResolution
    data object Invalid : TokenResolution
}

@Component
class JwtTokenService(private val properties: JwtProperties) : AccessTokenPort {

    private val secretKey: SecretKey = decodeSecret(properties.secret)

    private val encoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(secretKey))

    private val decoder = NimbusJwtDecoder.withSecretKey(secretKey)
        .macAlgorithm(MacAlgorithm.HS256)
        .build()
        .apply { setJwtValidator { OAuth2TokenValidatorResult.success() } }

    val accessTtl: Duration = properties.accessTtl

    override fun issue(userId: Long): AccessToken =
        AccessToken(issueAccessToken(userId), accessTtl.seconds)

    fun issueAccessToken(userId: Long): String {
        val issuedAt = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(accessTtl))
            .build()
        return encoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims),
        ).tokenValue
    }

    fun resolve(token: String): TokenResolution {
        val decoded = try {
            decoder.decode(token)
        } catch (e: JwtException) {
            return TokenResolution.Invalid
        }

        val expiresAt = decoded.expiresAt ?: return TokenResolution.Invalid
        if (expiresAt.isBefore(Instant.now())) return TokenResolution.Expired

        val userId = decoded.subject?.toLongOrNull() ?: return TokenResolution.Invalid
        return TokenResolution.Valid(userId)
    }

    private fun decodeSecret(raw: String): SecretKey {
        val decoded = try {
            Base64.getDecoder().decode(raw)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("secret이 base64가 아닙니다.", e)
        }
        check(decoded.size >= MIN_SECRET_BYTES) {
            "secret은 base64 디코딩 후 ${MIN_SECRET_BYTES}바이트 이상이어야 합니다."
        }
        return SecretKeySpec(decoded, "HmacSHA256")
    }

    private companion object {
        const val MIN_SECRET_BYTES = 32
    }
}
