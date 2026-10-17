package com.finngraph.web.user

import com.finngraph.web.auth.AuthTokenResponse
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
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "User", description = "내 정보")
@RequestMapping("/api/v1/me")
interface UserApi {

    @Operation(summary = "내 프로필")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "401",
            description = "토큰 부재·만료·위조 또는 탈퇴한 사용자",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping
    fun me(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): DataResponse<MeResponse>

    @Operation(summary = "닉네임 수정")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "닉네임 수정 성공"),
        ApiResponse(
            responseCode = "400",
            description = "닉네임이 공백 제외 2~20자가 아님",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "토큰에 이상이 있거나 탈퇴한 사용자",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PatchMapping("/nickname")
    fun updateNickname(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestBody request: NicknameUpdateRequest,
    ): DataResponse<MeResponse>

    @Operation(summary = "비밀번호 변경")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "변경 성공 — 기존 refresh 토큰을 모두 폐기하고 현재 기기에 새 세션 발급"),
        ApiResponse(
            responseCode = "400",
            description = "입력 검증 실패(INVALID_PARAMETER) 또는 현재 비밀번호 불일치(PASSWORD_MISMATCH)",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "토큰에 이상이 있거나 탈퇴한 사용자",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "비밀번호가 없는 카카오 계정(PASSWORD_NOT_SET)",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "429",
            description = "15분 내 비밀번호 시도 5회 초과(RATE_LIMITED)",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PatchMapping("/password")
    fun changePassword(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestBody request: PasswordChangeRequest,
        @Parameter(hidden = true) response: HttpServletResponse,
    ): DataResponse<AuthTokenResponse>

    @Operation(summary = "회원 탈퇴")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "탈퇴 완료"),
        ApiResponse(
            responseCode = "400",
            description = "이메일 계정의 비밀번호 누락(INVALID_PARAMETER) 또는 불일치(PASSWORD_MISMATCH)",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "토큰에 이상이 있거나 탈퇴한 사용자",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "429",
            description = "15분 내 비밀번호 시도 5회 초과(RATE_LIMITED)",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping("/withdrawal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestBody(required = false) request: WithdrawalRequest?,
    )
}
