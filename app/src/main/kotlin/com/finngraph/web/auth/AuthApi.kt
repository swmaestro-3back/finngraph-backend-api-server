package com.finngraph.web.auth

import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "Auth", description = "인증")
@RequestMapping("/api/v1/auth")
interface AuthApi {

    @Operation(summary = "카카오 로그인 (최초 요청이면 가입)")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "로그인 성공"),
        ApiResponse(
            responseCode = "400",
            description = "code 누락 또는 512자 초과",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "카카오 code 무효·만료·redirect 불일치",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "502",
            description = "카카오 API 장애·타임아웃",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping("/kakao")
    fun kakaoLogin(
        @RequestBody request: KakaoLoginRequest,
        @Parameter(hidden = true) response: HttpServletResponse,
    ): DataResponse<AuthTokenResponse>

    @Operation(summary = "이메일 가입 (가입 즉시 로그인)")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "가입 성공"),
        ApiResponse(
            responseCode = "400",
            description = "이메일·비밀번호·닉네임 검증 실패",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "이미 가입된 이메일",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(
        @RequestBody request: SignupRequest,
        @Parameter(hidden = true) response: HttpServletResponse,
    ): DataResponse<AuthTokenResponse>

    @Operation(summary = "이메일 로그인")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "로그인 성공"),
        ApiResponse(
            responseCode = "400",
            description = "이메일·비밀번호 누락",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "자격 불일치",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        @Parameter(hidden = true) response: HttpServletResponse,
    ): DataResponse<AuthTokenResponse>

    @Operation(summary = "access 재발급 + refresh 회전")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "재발급 성공"),
        ApiResponse(
            responseCode = "401",
            description = "refresh 쿠키 부재·만료·미존재·재사용 감지",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping("/refresh")
    fun refresh(
        @Parameter(hidden = true) @CookieValue(name = "refresh_token", required = false) refreshToken: String?,
        @Parameter(hidden = true) response: HttpServletResponse,
    ): DataResponse<AuthTokenResponse>

    @Operation(summary = "로그아웃 (제시 토큰의 family 전체 폐기, 멱등)")
    @ApiResponses(ApiResponse(responseCode = "204", description = "로그아웃 완료"))
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @Parameter(hidden = true) @CookieValue(name = "refresh_token", required = false) refreshToken: String?,
        @Parameter(hidden = true) response: HttpServletResponse,
    )
}
