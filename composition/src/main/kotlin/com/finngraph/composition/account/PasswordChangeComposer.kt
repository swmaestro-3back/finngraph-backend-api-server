package com.finngraph.composition.account

import com.finngraph.auth.port.CredentialPort
import com.finngraph.auth.port.TokenPort
import com.finngraph.composition.port.PasswordHasherPort
import org.springframework.stereotype.Component

@Component
class PasswordChangeComposer(
    private val verifier: PasswordVerifier,
    private val credentials: CredentialPort,
    private val passwordHasher: PasswordHasherPort,
    private val tokens: TokenPort,
    private val sessions: SessionComposer,
) {

    fun change(userId: Long, currentPassword: String, newPassword: String): IssuedSession? {
        verifier.verify(userId, currentPassword)
        if (!credentials.updatePasswordHash(userId, passwordHasher.hash(newPassword))) {
            throw PasswordNotSetException()
        }
        tokens.revokeAllByUserId(userId)
        return sessions.issue(userId)
    }
}
