package com.finngraph.composition

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

class RefreshTokenGenerator(val ttl: Duration) {

    private val random = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    fun generate(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    fun hash(token: String): String =
        encoder.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)),
        )

    private companion object {
        const val TOKEN_BYTES = 32
    }
}
