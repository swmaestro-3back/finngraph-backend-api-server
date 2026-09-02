package com.finngraph.web.composition

import com.finngraph.auth.port.CredentialPort
import com.finngraph.user.port.UserPort
import com.finngraph.web.user.MeResponse
import org.springframework.stereotype.Component

@Component
class UserProfileComposer(
    private val users: UserPort,
    private val credentials: CredentialPort,
) {

    fun profile(userId: Long): MeResponse? {
        val user = users.findById(userId) ?: return null
        val credential = credentials.findByUserId(userId) ?: return null
        return MeResponse(
            nickname = user.nickname,
            email = credential.email,
            provider = credential.provider,
            joinedAt = user.createdAt,
        )
    }
}
