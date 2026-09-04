package com.finngraph.composition

import com.finngraph.auth.model.AuthProvider
import com.finngraph.auth.port.CredentialPort
import com.finngraph.user.model.Nickname
import com.finngraph.user.port.UserPort
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

data class UserProfile(
    val nickname: String,
    val email: String?,
    val provider: AuthProvider,
    val joinedAt: OffsetDateTime,
)

@Component
class UserProfileComposer(
    private val users: UserPort,
    private val credentials: CredentialPort,
) {

    fun profile(userId: Long): UserProfile? {
        val user = users.findById(userId) ?: return null
        val credential = credentials.findByUserId(userId) ?: return null
        return UserProfile(
            nickname = user.nickname,
            email = credential.email,
            provider = credential.provider,
            joinedAt = user.createdAt,
        )
    }

    fun updateNickname(userId: Long, nickname: Nickname): UserProfile? {
        if (!users.updateNickname(userId, nickname)) return null
        return profile(userId)
    }
}
