package com.finngraph.composition

import com.finngraph.auth.model.AuthProvider
import com.finngraph.auth.port.CredentialPort
import com.finngraph.user.model.Nickname
import com.finngraph.user.model.UserView
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

    fun profile(userId: Long): UserProfile? =
        users.findById(userId)?.let(::withCredential)

    fun updateNickname(userId: Long, nickname: Nickname): UserProfile? =
        users.updateNickname(userId, nickname)?.let(::withCredential)

    private fun withCredential(user: UserView): UserProfile? {
        val credential = credentials.findByUserId(user.id) ?: return null
        return UserProfile(
            nickname = user.nickname,
            email = credential.email,
            provider = credential.provider,
            joinedAt = user.createdAt,
        )
    }
}
