package com.finngraph.web.auth

import com.finngraph.auth.DuplicateCredentialException
import com.finngraph.auth.model.Email
import com.finngraph.auth.model.RotationResult
import com.finngraph.auth.port.CredentialPort
import com.finngraph.auth.port.TokenPort
import com.finngraph.user.model.Nickname
import com.finngraph.web.common.AuthenticationFailedException
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.composition.KakaoSignupResult
import com.finngraph.web.composition.SignupComposer
import com.finngraph.web.composition.UserProfileComposer
import com.finngraph.web.security.JwtTokenService
import com.finngraph.web.security.KakaoOAuthClient
import com.finngraph.web.security.RefreshTokens
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val signupComposer: SignupComposer,
    private val userProfile: UserProfileComposer,
    private val credentials: CredentialPort,
    private val tokens: TokenPort,
    private val jwt: JwtTokenService,
    private val refreshTokens: RefreshTokens,
    private val passwordEncoder: PasswordEncoder,
    private val kakaoClient: KakaoOAuthClient,
) : AuthApi {

    override fun kakaoLogin(
        request: KakaoLoginRequest,
        response: HttpServletResponse,
    ): DataResponse<AuthTokenResponse> {
        val kakaoUser = kakaoClient.exchange(validateCode(request.code))
        val nickname = kakaoNickname(kakaoUser.nickname, kakaoUser.id)

        val result = try {
            signupComposer.signupOrLoginKakao(kakaoUser.id, nickname)
        } catch (e: DuplicateCredentialException) {
            signupComposer.signupOrLoginKakao(kakaoUser.id, nickname)
        }

        return DataResponse(
            issueSession(result.userId, result is KakaoSignupResult.SignedUp, response),
        )
    }

    override fun signup(
        request: SignupRequest,
        response: HttpServletResponse,
    ): DataResponse<AuthTokenResponse> {
        val command = validateSignup(request)
        val passwordHash = checkNotNull(passwordEncoder.encode(command.password)) {
            "PasswordEncoder가 해시를 생성하지 못했습니다"
        }
        val userId = signupComposer.signupEmail(command.email, passwordHash, command.nickname)
        return DataResponse(issueSession(userId, isNewUser = true, response = response))
    }

    override fun login(
        request: LoginRequest,
        response: HttpServletResponse,
    ): DataResponse<AuthTokenResponse> {
        validateLogin(request)

        val email = runCatching { Email.of(request.email!!) }.getOrElse { throw invalidCredentials() }
        val credential = credentials.findByEmail(email) ?: throw invalidCredentials()
        if (!passwordEncoder.matches(request.password!!, credential.passwordHash)) throw invalidCredentials()

        return DataResponse(issueSession(credential.userId, isNewUser = false, response = response))
    }

    override fun refresh(
        refreshToken: String?,
        response: HttpServletResponse,
    ): DataResponse<AuthTokenResponse> {
        val presented = refreshToken ?: throw unauthorized()
        val rotated = refreshTokens.generate()

        return when (
            val result = tokens.rotate(
                refreshTokens.hash(presented),
                refreshTokens.hash(rotated),
                refreshTokens.ttl,
            )
        ) {
            is RotationResult.Rotated -> {
                setCookie(response, refreshTokens.cookie(rotated).toString())
                DataResponse(profileResponse(result.userId, isNewUser = false))
            }

            is RotationResult.ReuseDetected, RotationResult.Unknown -> {
                setCookie(response, refreshTokens.expiredCookie().toString())
                throw unauthorized()
            }
        }
    }

    override fun logout(refreshToken: String?, response: HttpServletResponse) {
        refreshToken?.let { tokens.revoke(refreshTokens.hash(it)) }
        setCookie(response, refreshTokens.expiredCookie().toString())
    }

    private fun issueSession(
        userId: Long,
        isNewUser: Boolean,
        response: HttpServletResponse,
    ): AuthTokenResponse {
        val refreshToken = refreshTokens.generate()
        tokens.issue(userId, refreshTokens.hash(refreshToken), refreshTokens.ttl)
        setCookie(response, refreshTokens.cookie(refreshToken).toString())
        return profileResponse(userId, isNewUser)
    }

    private fun profileResponse(userId: Long, isNewUser: Boolean): AuthTokenResponse {
        val profile = userProfile.profile(userId) ?: throw unauthorized()
        return AuthTokenResponse(
            accessToken = jwt.issueAccessToken(userId),
            expiresIn = jwt.accessTtl.seconds,
            isNewUser = isNewUser,
            user = profile,
        )
    }

    private fun setCookie(response: HttpServletResponse, cookie: String) =
        response.addHeader(HttpHeaders.SET_COOKIE, cookie)

    private fun validateCode(raw: String?): String {
        val code = raw?.trim().orEmpty()
        if (code.isEmpty() || code.length > MAX_CODE_LENGTH) {
            throw InvalidParameterException(
                "카카오 인가 코드가 올바르지 않습니다",
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
        if (errors.isNotEmpty()) throw InvalidParameterException("로그인 정보가 올바르지 않습니다", errors)
    }

    private fun validateSignup(request: SignupRequest): SignupCommand {
        val errors = mutableMapOf<String, String>()

        val email = when {
            request.email.isNullOrBlank() -> {
                errors["email"] = "must not be blank"
                null
            }

            else -> runCatching { Email.of(request.email) }.getOrElse {
                errors["email"] = "must be a valid email of at most ${Email.MAX_LENGTH} characters"
                null
            }
        }

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

        if (errors.isNotEmpty()) throw InvalidParameterException("가입 정보가 올바르지 않습니다", errors)
        return SignupCommand(email!!, password, nickname!!)
    }

    private fun kakaoNickname(raw: String?, kakaoUserId: String): Nickname =
        runCatching { Nickname.of(raw.orEmpty()) }
            .getOrElse { Nickname.of(FALLBACK_NICKNAME_PREFIX + kakaoUserId.takeLast(FALLBACK_SUFFIX_LENGTH)) }

    private fun invalidCredentials() =
        AuthenticationFailedException(ErrorCode.INVALID_CREDENTIALS, "이메일 또는 비밀번호가 올바르지 않습니다")

    private fun unauthorized() =
        AuthenticationFailedException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다")

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
