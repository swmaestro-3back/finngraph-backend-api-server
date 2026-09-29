package com.finngraph.web.auth

import com.finngraph.auth.model.Email
import com.finngraph.auth.model.VerificationCode
import com.finngraph.auth.model.VerificationResult
import com.finngraph.composition.account.AccountComposer
import com.finngraph.composition.account.IssuedSession
import com.finngraph.composition.account.KakaoSignupResult
import com.finngraph.composition.account.SessionComposer
import com.finngraph.composition.port.KakaoOAuthPort
import com.finngraph.user.model.Nickname
import com.finngraph.verification.EmailVerificationService
import com.finngraph.web.common.AuthenticationFailedException
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.security.RefreshTokenCookies
import com.finngraph.web.user.MeResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val accountComposer: AccountComposer,
    private val sessionComposer: SessionComposer,
    private val refreshTokens: RefreshTokenCookies,
    private val kakaoClient: KakaoOAuthPort,
    private val verification: EmailVerificationService,
    private val verificationCookies: VerificationCookies,
) : AuthApi {

    override fun kakaoLogin(
        request: KakaoLoginRequest,
        response: HttpServletResponse,
    ): DataResponse<AuthTokenResponse> {
        val kakaoUser = kakaoClient.exchange(validateCode(request.code))
        val nickname = kakaoNickname(kakaoUser.nickname, kakaoUser.id)

        val result = accountComposer.signupOrLoginKakao(kakaoUser.id, nickname)
        val isNewUser = result is KakaoSignupResult.SignedUp

        return respond(issueSession(result.userId), isNewUser, response)
    }

    override fun signup(
        request: SignupRequest,
        grant: String?,
        response: HttpServletResponse,
    ): DataResponse<AuthTokenResponse> {
        val command = validateSignup(request)
        val userId = accountComposer.signupEmail(command.email, command.password, command.nickname, grant)
        setCookie(response, verificationCookies.expiredCookie().toString())

        return respond(issueSession(userId), isNewUser = true, response)
    }

    override fun sendVerification(request: SendVerificationRequest) {
        verification.send(validateEmail(request.email))
    }

    override fun confirmVerification(
        request: ConfirmVerificationRequest,
        httpRequest: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val command = validateConfirm(request)

        when (val result = verification.confirm(command.email, command.code, httpRequest.remoteAddr)) {
            is VerificationResult.Verified ->
                setCookie(response, verificationCookies.cookie(result.grant).toString())

            is VerificationResult.Mismatch ->
                throw VerificationFailedException(ErrorCode.VERIFICATION_CODE_MISMATCH, result.remainingAttempts)

            VerificationResult.Expired ->
                throw VerificationFailedException(ErrorCode.VERIFICATION_EXPIRED, null)
        }
    }

    override fun login(
        request: LoginRequest,
        response: HttpServletResponse,
    ): DataResponse<AuthTokenResponse> {
        validateLogin(request)

        val email = runCatching { Email.of(request.email!!) }.getOrElse { throw invalidCredentials() }
        val userId = accountComposer.loginEmail(email, request.password!!) ?: throw invalidCredentials()

        return respond(issueSession(userId), isNewUser = false, response)
    }

    override fun refresh(
        refreshToken: String?,
        response: HttpServletResponse,
    ): DataResponse<AuthTokenResponse> {
        val presented = refreshToken ?: throw unauthorized()

        val session = sessionComposer.rotate(presented) ?: run {
            setCookie(response, refreshTokens.expiredCookie().toString())
            throw unauthorized()
        }
        return respond(session, isNewUser = false, response)
    }

    override fun logout(refreshToken: String?, response: HttpServletResponse) {
        refreshToken?.let(sessionComposer::revoke)
        setCookie(response, refreshTokens.expiredCookie().toString())
    }

    private fun issueSession(userId: Long): IssuedSession =
        sessionComposer.issue(userId) ?: throw unauthorized()

    private fun respond(
        session: IssuedSession,
        isNewUser: Boolean,
        response: HttpServletResponse,
    ): DataResponse<AuthTokenResponse> {
        setCookie(response, refreshTokens.cookie(session.refreshToken).toString())
        return DataResponse(
            AuthTokenResponse(
                accessToken = session.accessToken,
                expiresIn = session.expiresIn,
                isNewUser = isNewUser,
                user = MeResponse.from(session.profile),
            ),
        )
    }

    private fun setCookie(response: HttpServletResponse, cookie: String) =
        response.addHeader(HttpHeaders.SET_COOKIE, cookie)

    private fun validateCode(raw: String?): String {
        val code = raw?.trim().orEmpty()
        if (code.isEmpty() || code.length > MAX_CODE_LENGTH) {
            throw InvalidParameterException(
                "카카오 인가 코드가 올바르지 않습니다.",
                mapOf("code" to "must not be blank and at most $MAX_CODE_LENGTH characters"),
            )
        }
        return code
    }

    private fun validateLogin(request: LoginRequest) {
        val errors = buildMap {
            if (request.email.isNullOrBlank()) put("email", "must not be blank")
            if (request.password.isNullOrBlank()) put("password", "must not be blank")
        }
        if (errors.isNotEmpty()) throw InvalidParameterException("로그인 정보가 올바르지 않습니다.", errors)
    }

    private fun validateEmail(raw: String?): Email {
        val errors = mutableMapOf<String, String>()
        val email = parseEmail(raw, errors)
        if (errors.isNotEmpty()) throw InvalidParameterException("이메일이 올바르지 않습니다.", errors)
        return email!!
    }

    private fun validateConfirm(request: ConfirmVerificationRequest): ConfirmCommand {
        val errors = mutableMapOf<String, String>()
        val email = parseEmail(request.email, errors)
        val code = runCatching { VerificationCode.of(request.code.orEmpty()) }.getOrElse {
            errors["code"] = "must be ${VerificationCode.LENGTH} digits"
            null
        }
        if (errors.isNotEmpty()) throw InvalidParameterException("인증 정보가 올바르지 않습니다.", errors)
        return ConfirmCommand(email!!, code!!)
    }

    private fun parseEmail(raw: String?, errors: MutableMap<String, String>): Email? = when {
        raw.isNullOrBlank() -> {
            errors["email"] = "must not be blank"
            null
        }

        else -> runCatching { Email.of(raw) }.getOrElse {
            errors["email"] = "must be a valid email of at most ${Email.MAX_LENGTH} characters"
            null
        }
    }

    private fun validateSignup(request: SignupRequest): SignupCommand {
        val errors = mutableMapOf<String, String>()

        val email = parseEmail(request.email, errors)

        val password = request.password.orEmpty()
        when {
            password.length !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH ->
                errors["password"] = "must be $MIN_PASSWORD_LENGTH-$MAX_PASSWORD_LENGTH characters"

            password.any { it.isWhitespace() } -> errors["password"] = "must not contain whitespace"
            password.none { it.isLetter() } -> errors["password"] = "must contain a letter"
            password.none { it.isDigit() } -> errors["password"] = "must contain a digit"
        }

        val nickname = runCatching { Nickname.of(request.nickname.orEmpty()) }.getOrElse {
            errors["nickname"] = "must be ${Nickname.MIN_LENGTH}-${Nickname.MAX_LENGTH} characters"
            null
        }

        if (errors.isNotEmpty()) throw InvalidParameterException("가입 정보가 올바르지 않습니다.", errors)
        return SignupCommand(email!!, password, nickname!!)
    }

    private fun kakaoNickname(raw: String?, kakaoUserId: String): Nickname =
        runCatching { Nickname.of(raw.orEmpty()) }
            .getOrElse { Nickname.of(FALLBACK_NICKNAME_PREFIX + kakaoUserId.takeLast(FALLBACK_SUFFIX_LENGTH)) }

    private fun invalidCredentials() =
        AuthenticationFailedException(ErrorCode.INVALID_CREDENTIALS, "이메일 또는 비밀번호가 올바르지 않습니다.")

    private fun unauthorized() =
        AuthenticationFailedException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.")

    private data class ConfirmCommand(
        val email: Email,
        val code: VerificationCode,
    )

    private data class SignupCommand(
        val email: Email,
        val password: String,
        val nickname: Nickname,
    )

    private companion object {
        const val MAX_CODE_LENGTH = 512
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 128
        const val FALLBACK_NICKNAME_PREFIX = "사용자"
        const val FALLBACK_SUFFIX_LENGTH = 4
    }
}
