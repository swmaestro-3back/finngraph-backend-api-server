package com.finngraph.composition

import com.finngraph.auth.model.RotationResult
import com.finngraph.auth.port.TokenPort
import com.finngraph.composition.port.AccessTokenPort
import org.springframework.stereotype.Component

data class IssuedSession(
    val accessToken: String,
    val expiresIn: Long,
    val profile: UserProfile,
    val refreshToken: String,
)

@Component
class SessionComposer(
    private val userProfile: UserProfileComposer,
    private val tokens: TokenPort,
    private val accessTokens: AccessTokenPort,
    private val refreshTokens: RefreshTokenGenerator,
) {

    fun issue(userId: Long): IssuedSession? {
        val profile = userProfile.profile(userId) ?: return null
        val refreshToken = refreshTokens.generate()
        tokens.issue(userId, refreshTokens.hash(refreshToken), refreshTokens.ttl)
        return sessionOf(userId, profile, refreshToken)
    }

    fun rotate(presentedToken: String): IssuedSession? {
        val rotated = refreshTokens.generate()

        return when (
            val result = tokens.rotate(
                refreshTokens.hash(presentedToken),
                refreshTokens.hash(rotated),
                refreshTokens.ttl,
            )
        ) {
            is RotationResult.Rotated -> {
                val profile = userProfile.profile(result.userId)
                if (profile == null) {
                    tokens.revoke(refreshTokens.hash(rotated))
                    null
                } else {
                    sessionOf(result.userId, profile, rotated)
                }
            }

            is RotationResult.ReuseDetected, RotationResult.Unknown -> null
        }
    }

    fun revoke(presentedToken: String) {
        tokens.revoke(refreshTokens.hash(presentedToken))
    }

    private fun sessionOf(userId: Long, profile: UserProfile, refreshToken: String): IssuedSession {
        val access = accessTokens.issue(userId)
        return IssuedSession(
            accessToken = access.value,
            expiresIn = access.expiresIn,
            profile = profile,
            refreshToken = refreshToken,
        )
    }
}
