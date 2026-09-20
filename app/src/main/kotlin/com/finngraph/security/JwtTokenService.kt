package com.finngraph.security

import com.finngraph.composition.port.AccessToken
import com.finngraph.composition.port.AccessTokenPort
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.proc.SingleKeyJWSKeySelector
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64

sealed interface TokenResolution {
    data class Valid(val userId: Long) : TokenResolution
    data object Expired : TokenResolution
    data object Invalid : TokenResolution
}

@Component
class JwtTokenService(private val properties: JwtProperties) : AccessTokenPort {

    private val signingKey: ECPrivateKey = decodePrivateKey(properties.signingKey)
    private val publicKey: ECPublicKey = decodePublicKey(properties.publicKey)

    init {
        check(Curve.forECParameterSpec(signingKey.params) == Curve.P_256) {
            "signing key는 P-256곡선이어야 합니다."
        }
        check(Curve.forECParameterSpec(publicKey.params) == Curve.P_256) {
            "public key는 P-256곡선이어야 합니다."
        }
        verifyKeyPair()
    }

    private val encoder = NimbusJwtEncoder(
        ImmutableJWKSet<SecurityContext>(
            JWKSet(ECKey.Builder(Curve.P_256, publicKey).privateKey(signingKey).build()),
        ),
    )

    private val decoder = NimbusJwtDecoder(
        DefaultJWTProcessor<SecurityContext>().apply {
            setJWSKeySelector(SingleKeyJWSKeySelector(JWSAlgorithm.ES256, publicKey))
            setJWTClaimsSetVerifier { _, _ -> }
        },
    ).apply { setJwtValidator { OAuth2TokenValidatorResult.success() } }

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
            JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.ES256).build(), claims),
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

    private fun decodePrivateKey(raw: String): ECPrivateKey = try {
        val der = Base64.getDecoder().decode(raw)
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der)) as ECPrivateKey
    } catch (e: Exception) {
        throw IllegalStateException("signing-key가 형식에 맞지 않습니다.", e)
    }

    private fun decodePublicKey(raw: String): ECPublicKey = try {
        val der = Base64.getDecoder().decode(raw)
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der)) as ECPublicKey
    } catch (e: Exception) {
        throw IllegalStateException("public-key가 형식에 맞지 않습니다.", e)
    }

    private fun verifyKeyPair() {
        val probe = "finngraph-jwt-key-check".toByteArray()
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(signingKey)
        signature.update(probe)
        val signed = signature.sign()
        signature.initVerify(publicKey)
        signature.update(probe)
        check(signature.verify(signed)) {
            "signing-key와 public-key가 짝이 아닙니다."
        }
    }
}
