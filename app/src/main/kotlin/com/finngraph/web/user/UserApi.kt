package com.finngraph.web.user

import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
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

    @Operation(summary = "유저 탈퇴")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "탈퇴 완료"),
        ApiResponse(
            responseCode = "401",
            description = "토큰에 이상이 있거나 탈퇴한 사용자",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    )
}
