package com.finngraph.web.composition

import com.finngraph.auth.model.Email
import com.finngraph.auth.port.CredentialPort
import com.finngraph.user.model.Nickname
import com.finngraph.user.port.UserPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

sealed interface KakaoSignupResult {
    val userId: Long

    data class SignedUp(override val userId: Long) : KakaoSignupResult
    data class LoggedIn(override val userId: Long) : KakaoSignupResult
}

@Component
class SignupComposer(
    private val users: UserPort,
    private val credentials: CredentialPort,
) {

    @Transactional(transactionManager = "appTransactionManager")
    fun signupOrLoginKakao(kakaoUserId: String, nickname: Nickname): KakaoSignupResult {
        credentials.findUserIdByKakao(kakaoUserId)?.let { return KakaoSignupResult.LoggedIn(it) }

        val userId = users.create(nickname)
        credentials.linkKakao(userId, kakaoUserId)
        return KakaoSignupResult.SignedUp(userId)
    }

    @Transactional(transactionManager = "appTransactionManager")
    fun signupEmail(email: Email, passwordHash: String, nickname: Nickname): Long {
        val userId = users.create(nickname)
        credentials.registerEmail(userId, email, passwordHash)
        return userId
    }
}
