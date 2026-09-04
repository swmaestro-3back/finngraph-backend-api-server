package com.finngraph.web.user

import com.finngraph.composition.UserProfile
import com.finngraph.composition.UserProfileComposer
import com.finngraph.composition.WithdrawalComposer
import com.finngraph.user.model.Nickname
import com.finngraph.web.common.AuthenticationFailedException
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(
    private val userProfile: UserProfileComposer,
    private val withdrawalComposer: WithdrawalComposer,
) : UserApi {

    override fun me(userId: Long): DataResponse<MeResponse> = DataResponse(MeResponse.from(profileOf(userId)))

    override fun updateNickname(
        userId: Long,
        request: NicknameUpdateRequest,
    ): DataResponse<MeResponse> {
        val nickname = validateNickname(request.nickname)
        val updated = userProfile.updateNickname(userId, nickname) ?: throw unauthorized()
        return DataResponse(MeResponse.from(updated))
    }

    override fun withdraw(userId: Long) {
        if (!withdrawalComposer.withdraw(userId)) {
            throw AuthenticationFailedException(ErrorCode.UNAUTHORIZED, "사용자를 찾을 수 없습니다.")
        }
    }

    private fun profileOf(userId: Long): UserProfile =
        userProfile.profile(userId) ?: throw unauthorized()

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
