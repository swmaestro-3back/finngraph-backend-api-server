package com.finngraph.web.composition

import com.finngraph.auth.port.CredentialPort
import com.finngraph.user.port.UserPort
import com.finngraph.web.common.AuthenticationFailedException
import com.finngraph.web.common.ErrorCode
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class WithdrawalComposer(
    private val users: UserPort,
    private val credentials: CredentialPort,
) {

    @Transactional(transactionManager = "appTransactionManager")
    fun withdraw(userId: Long): String? {
        val kakaoUserId = credentials.findKakaoIdByUserId(userId)

        if (!users.delete(userId)) {
            throw AuthenticationFailedException(ErrorCode.UNAUTHORIZED, "사용자를 찾을 수 없습니다.")
        }
        return kakaoUserId
    }
}
