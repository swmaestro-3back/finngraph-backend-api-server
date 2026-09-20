package com.finngraph.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

class JwtTokenServiceTest {

    private fun generateKeyPair(curve: String = "secp256r1"): KeyPair =
        KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec(curve)) }
            .generateKeyPair()

    private fun encode(keyPair: KeyPair): Pair<String, String> =
        Base64.getEncoder().encodeToString(keyPair.private.encoded) to
            Base64.getEncoder().encodeToString(keyPair.public.encoded)

    private fun properties(
        signingKey: String,
        publicKey: String,
        accessTtl: Duration = Duration.ofMinutes(30),
    ) = JwtProperties(signingKey, publicKey, accessTtl, Duration.ofDays(14))

    private fun service(
        keyPair: KeyPair = generateKeyPair(),
        accessTtl: Duration = Duration.ofMinutes(30),
    ): JwtTokenService {
        val (signing, public) = encode(keyPair)
        return JwtTokenService(properties(signing, public, accessTtl))
    }

    private fun mintES256(keyPair: KeyPair, claims: JWTClaimsSet): String {
        val ecKey = ECKey.Builder(Curve.P_256, keyPair.public as ECPublicKey)
            .privateKey(keyPair.private as ECPrivateKey)
            .build()
        val jwt = SignedJWT(JWSHeader(JWSAlgorithm.ES256), claims)
        jwt.sign(ECDSASigner(ecKey))
        return jwt.serialize()
    }

    @Test
    fun `발급한 토큰은 Valid로 해석된다`() {
        val service = service()
        val token = service.issueAccessToken(42L)
        assertEquals(TokenResolution.Valid(42L), service.resolve(token))
    }

    @Test
    fun `만료된 토큰은 Expired`() {
        val keyPair = generateKeyPair()
        val service = service(keyPair)
        val token = mintES256(
            keyPair,
            JWTClaimsSet.Builder()
                .subject("42")
                .issueTime(Date.from(Instant.now().minus(Duration.ofMinutes(31))))
                .expirationTime(Date.from(Instant.now().minus(Duration.ofMinutes(1))))
                .build(),
        )
        assertEquals(TokenResolution.Expired, service.resolve(token))
    }

    @Test
    fun `쓰레기 문자열은 Invalid`() {
        assertEquals(TokenResolution.Invalid, service().resolve("garbage.garbage.garbage"))
    }

    @Test
    fun `다른 키로 서명한 토큰은 Invalid`() {
        val serviceA = service()
        val serviceB = service()
        val foreign = serviceB.issueAccessToken(42L)
        assertEquals(TokenResolution.Invalid, serviceA.resolve(foreign))
    }

    @Test
    fun `구 HS256 토큰은 Invalid`() {
        val service = service()
        val claims = JWTClaimsSet.Builder()
            .subject("42")
            .issueTime(Date())
            .expirationTime(Date.from(Instant.now().plus(Duration.ofMinutes(30))))
            .build()
        val hs = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        hs.sign(MACSigner(ByteArray(32) { (it * 7 + 13).toByte() }))
        assertEquals(TokenResolution.Invalid, service.resolve(hs.serialize()))
    }

    @Test
    fun `exp 없는 토큰은 Invalid`() {
        val keyPair = generateKeyPair()
        val service = service(keyPair)
        val token = mintES256(keyPair, JWTClaimsSet.Builder().subject("42").build())
        assertEquals(TokenResolution.Invalid, service.resolve(token))
    }

    @Test
    fun `sub가 숫자가 아니면 Invalid`() {
        val keyPair = generateKeyPair()
        val service = service(keyPair)
        val token = mintES256(
            keyPair,
            JWTClaimsSet.Builder()
                .subject("not-a-number")
                .expirationTime(Date.from(Instant.now().plus(Duration.ofMinutes(30))))
                .build(),
        )
        assertEquals(TokenResolution.Invalid, service.resolve(token))
    }

    @Test
    fun `signing-key가 base64가 아니면 기동 실패`() {
        val (_, public) = encode(generateKeyPair())
        assertThrows(IllegalStateException::class.java) {
            JwtTokenService(properties("!!!not-base64!!!", public))
        }
    }

    @Test
    fun `P-256이 아닌 곡선이면 기동 실패`() {
        val (signing, public) = encode(generateKeyPair("secp384r1"))
        val thrown = assertThrows(Exception::class.java) {
            JwtTokenService(properties(signing, public))
        }
        assertInstanceOf(IllegalStateException::class.java, thrown)
    }

    @Test
    fun `개인키와 공개키가 짝이 아니면 기동 실패`() {
        val (signing, _) = encode(generateKeyPair())
        val (_, otherPublic) = encode(generateKeyPair())
        assertThrows(IllegalStateException::class.java) {
            JwtTokenService(properties(signing, otherPublic))
        }
    }
}
