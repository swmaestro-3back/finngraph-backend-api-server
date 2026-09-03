package com.finngraph.web.user

import com.finngraph.auth.port.TokenPort
import com.finngraph.user.model.Nickname
import com.finngraph.user.port.UserPort
import com.finngraph.web.common.AuthenticationFailedException
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.composition.UserProfileComposer
import com.finngraph.web.composition.WithdrawalComposer
import com.finngraph.web.security.KakaoOAuthClient
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(
    private val users: UserPort,
    private val userProfile: UserProfileComposer,
    private val withdrawalComposer: WithdrawalComposer,
    private val tokens: TokenPort,
    private val kakaoClient: KakaoOAuthClient,
) : UserApi {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun me(userId: Long): DataResponse<MeResponse> = DataResponse(profileOf(userId))

    override fun updateNickname(
        userId: Long,
        request: NicknameUpdateRequest,
    ): DataResponse<MeResponse> {
        val nickname = validateNickname(request.nickname)
        if (!users.updateNickname(userId, nickname)) throw unauthorized()
        return DataResponse(profileOf(userId))
    }

    override fun withdraw(userId: Long) {
        val kakaoUserId = withdrawalComposer.withdraw(userId)

        tokens.revokeAllByUserId(userId)
        kakaoUserId?.let(::unlinkQuietly)
    }

    private fun profileOf(userId: Long): MeResponse =
        userProfile.profile(userId) ?: throw unauthorized()

    private fun unlinkQuietly(kakaoUserId: String) {
        runCatching { kakaoClient.unlink(kakaoUserId) }
            .onFailure { log.error("카카오 연결 해제 실패 — 계정 삭제는 완료됨", it) }
    }

    private fun validateNickname(raw: String?): Nickname =
        runCatching { Nickname.of(raw.orEmpty()) }.getOrElse {
            throw InvalidParameterException(
                "닉네임이 올바르지 않습니다",
                mapOf("nickname" to "must be ${Nickname.MIN_LENGTH}-${Nickname.MAX_LENGTH} characters"),
            )
        }

    private fun unauthorized() =
        AuthenticationFailedException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다")
}
