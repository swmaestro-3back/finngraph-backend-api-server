package com.finngraph.web.user

import com.finngraph.auth.model.PasswordPolicy
import com.finngraph.composition.account.PasswordChangeComposer
import com.finngraph.composition.account.UserProfile
import com.finngraph.composition.account.UserProfileComposer
import com.finngraph.composition.account.WithdrawalComposer
import com.finngraph.user.model.Nickname
import com.finngraph.web.auth.AuthTokenResponse
import com.finngraph.web.common.AuthenticationFailedException
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.security.RefreshTokenCookies
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(
    private val userProfile: UserProfileComposer,
    private val withdrawalComposer: WithdrawalComposer,
    private val passwordChange: PasswordChangeComposer,
    private val refreshTokens: RefreshTokenCookies,
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

    override fun changePassword(
        userId: Long,
        request: PasswordChangeRequest,
        response: HttpServletResponse,
    ): DataResponse<AuthTokenResponse> {
        val command = validatePasswordChange(request)
        val session = passwordChange.change(userId, command.currentPassword, command.newPassword)
            ?: throw unauthorized()
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokens.cookie(session.refreshToken).toString())
        return DataResponse(
            AuthTokenResponse(
                accessToken = session.accessToken,
                expiresIn = session.expiresIn,
                isNewUser = false,
                user = MeResponse.from(session.profile),
            ),
        )
    }

    override fun withdraw(userId: Long, request: WithdrawalRequest?) {
        if (!withdrawalComposer.withdraw(userId, request?.password)) {
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

    private fun validatePasswordChange(request: PasswordChangeRequest): PasswordChangeCommand {
        val current = request.currentPassword.orEmpty()
        val next = request.newPassword.orEmpty()
        val errors = buildMap {
            if (current.isBlank()) put("currentPassword", "must not be blank")
            val violation = PasswordPolicy.violation(next)
            if (violation != null) {
                put("newPassword", violation)
            } else if (next == current) {
                put("newPassword", "must differ from current password")
            }
        }
        if (errors.isNotEmpty()) throw InvalidParameterException("비밀번호 정보가 올바르지 않습니다", errors)
        return PasswordChangeCommand(current, next)
    }

    private fun unauthorized() =
        AuthenticationFailedException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다")

    private data class PasswordChangeCommand(
        val currentPassword: String,
        val newPassword: String,
    )
}
