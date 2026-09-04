package com.finngraph.composition

import com.finngraph.auth.model.Email
import com.finngraph.auth.port.CredentialPort
import com.finngraph.user.model.Nickname
import com.finngraph.user.port.UserPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class DeletedAccount(val kakaoUserId: String?)

@Component
class AccountWriter(
    private val users: UserPort,
    private val credentials: CredentialPort,
) {

    @Transactional(transactionManager = "appTransactionManager")
    fun createEmailAccount(email: Email, passwordHash: String, nickname: Nickname): Long {
        val userId = users.create(nickname)
        credentials.registerEmail(userId, email, passwordHash)
        return userId
    }

    @Transactional(transactionManager = "appTransactionManager")
    fun linkOrFindKakao(kakaoUserId: String, nickname: Nickname): KakaoSignupResult {
        credentials.findUserIdByKakao(kakaoUserId)?.let { return KakaoSignupResult.LoggedIn(it) }

        val userId = users.create(nickname)
        credentials.linkKakao(userId, kakaoUserId)
        return KakaoSignupResult.SignedUp(userId)
    }

    @Transactional(transactionManager = "appTransactionManager")
    fun deleteAccount(userId: Long): DeletedAccount? {
        val kakaoUserId = credentials.findKakaoIdByUserId(userId)
        if (!users.delete(userId)) return null
        return DeletedAccount(kakaoUserId)
    }
}
