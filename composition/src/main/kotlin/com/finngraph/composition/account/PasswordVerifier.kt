package com.finngraph.composition.account

import com.finngraph.auth.model.PasswordAttemptDecision
import com.finngraph.auth.model.PasswordAttemptPolicy
import com.finngraph.auth.model.ThrottleScope
import com.finngraph.auth.port.CredentialPort
import com.finngraph.auth.port.PasswordAttemptPort
import com.finngraph.composition.port.PasswordHasherPort
import org.springframework.stereotype.Component

class PasswordMismatchException(val remainingAttempts: Int) : RuntimeException("비밀번호가 일치하지 않습니다")

class PasswordNotSetException : RuntimeException("비밀번호가 설정되지 않은 계정입니다")

class PasswordRequiredException : RuntimeException("비밀번호가 필요합니다")

@Component
class PasswordVerifier(
    private val credentials: CredentialPort,
    private val attempts: PasswordAttemptPort,
    private val passwordHasher: PasswordHasherPort,
    private val policy: PasswordAttemptPolicy,
) {

    fun verify(userId: Long, rawPassword: String) {
        val hash = credentials.findPasswordHashByUserId(userId) ?: throw PasswordNotSetException()
        matchWithinLimit(userId, rawPassword, hash)
    }

    fun verifyIfSet(userId: Long, rawPassword: String?) {
        val hash = credentials.findPasswordHashByUserId(userId) ?: return
        if (rawPassword.isNullOrBlank()) throw PasswordRequiredException()
        matchWithinLimit(userId, rawPassword, hash)
    }

    private fun matchWithinLimit(userId: Long, rawPassword: String, hash: String) {
        when (val decision = attempts.reserve(userId, policy)) {
            is PasswordAttemptDecision.Denied ->
                throw RateLimitedException(ThrottleScope.PASSWORD, decision.retryAfter)

            is PasswordAttemptDecision.Allowed -> {
                if (!passwordHasher.matches(rawPassword, hash)) throw PasswordMismatchException(decision.remaining)
                attempts.reset(userId)
            }
        }
    }
}
