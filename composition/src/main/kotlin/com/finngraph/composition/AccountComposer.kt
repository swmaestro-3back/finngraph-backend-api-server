package com.finngraph.composition

import com.finngraph.auth.DuplicateCredentialException
import com.finngraph.auth.model.Email
import com.finngraph.auth.port.CredentialPort
import com.finngraph.composition.port.PasswordHasherPort
import com.finngraph.user.model.Nickname
import org.springframework.stereotype.Component

sealed interface KakaoSignupResult {
    val userId: Long

    data class SignedUp(override val userId: Long) : KakaoSignupResult
    data class LoggedIn(override val userId: Long) : KakaoSignupResult
}

@Component
class AccountComposer(
    private val accounts: AccountWriter,
    private val credentials: CredentialPort,
    private val passwordHasher: PasswordHasherPort,
) {

    fun signupEmail(email: Email, rawPassword: String, nickname: Nickname): Long =
        accounts.createEmailAccount(email, passwordHasher.hash(rawPassword), nickname)

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
