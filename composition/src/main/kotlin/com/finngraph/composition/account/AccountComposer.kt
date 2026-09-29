package com.finngraph.composition.account

import com.finngraph.auth.DuplicateCredentialException
import com.finngraph.auth.model.Email
import com.finngraph.auth.port.CredentialPort
import com.finngraph.auth.port.VerificationCodePort
import com.finngraph.composition.port.PasswordHasherPort
import com.finngraph.user.model.Nickname
import org.springframework.stereotype.Component

sealed interface KakaoSignupResult {
    val userId: Long

    data class SignedUp(override val userId: Long) : KakaoSignupResult
    data class LoggedIn(override val userId: Long) : KakaoSignupResult
}

class EmailNotVerifiedException : RuntimeException("이메일 인증이 필요합니다")

@Component
class AccountComposer(
    private val accounts: AccountWriter,
    private val credentials: CredentialPort,
    private val passwordHasher: PasswordHasherPort,
    private val codes: VerificationCodePort,
    private val policies: VerificationPolicies,
) {

    fun signupEmail(email: Email, rawPassword: String, nickname: Nickname, grant: String?): Long {
        if (grant == null || !codes.consumeVerified(email, grant)) throw EmailNotVerifiedException()
        try {
            return accounts.createEmailAccount(email, passwordHasher.hash(rawPassword), nickname)
        } catch (e: Exception) {
            codes.restoreVerified(email, grant, policies.code.verifiedTtl)
            throw e
        }
    }

    fun signupOrLoginKakao(kakaoUserId: String, nickname: Nickname): KakaoSignupResult =
        try {
            accounts.linkOrFindKakao(kakaoUserId, nickname)
        } catch (_: DuplicateCredentialException) {
            accounts.linkOrFindKakao(kakaoUserId, nickname)
        }

    fun loginEmail(email: Email, rawPassword: String): Long? {
        val credential = credentials.findByEmail(email) ?: return null
        if (!passwordHasher.matches(rawPassword, credential.passwordHash)) return null
        return credential.userId
    }
}
