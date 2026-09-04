package com.finngraph.composition

import com.finngraph.auth.port.TokenPort
import com.finngraph.composition.port.KakaoOAuthPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class WithdrawalComposer(
    private val accounts: AccountWriter,
    private val tokens: TokenPort,
    private val kakaoClient: KakaoOAuthPort,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun withdraw(userId: Long): Boolean {
        tokens.revokeAllByUserId(userId)
        val deleted = accounts.deleteAccount(userId) ?: return false
        deleted.kakaoUserId?.let(::unlinkQuietly)
        return true
    }

    private fun unlinkQuietly(kakaoUserId: String) {
        runCatching { kakaoClient.unlink(kakaoUserId) }
            .onFailure { log.error("카카오 연결 해제 실패 — 계정 삭제는 완료됨", it) }
    }
}
