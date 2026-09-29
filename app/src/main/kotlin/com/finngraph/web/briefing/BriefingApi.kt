package com.finngraph.web.briefing

import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "Briefing", description = "데일리 브리핑 (서버 생성)")
@RequestMapping("/api/v1/briefings")
interface BriefingApi {

    @Operation(summary = "브리핑 날짜 목록", description = "기준일 최신순. 헤드라인 문장은 공개.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "limit이 1~90 범위 밖",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping
    fun list(
        @Parameter(description = "개수 (1~90)")
        @RequestParam(required = false, defaultValue = "30")
        limit: Int,
    ): DataResponse<List<BriefingSummaryResponse>>

    @Operation(
        summary = "최신 브리핑",
        description = "토큰이 없거나 유효하지 않으면 해설·지켜볼 점·리스크·관계·그래프는 null 이고 locked 에 개수만 온다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "404",
            description = "생성된 브리핑이 없음",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/latest")
    fun latest(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long?,
    ): DataResponse<BriefingResponse>

    @Operation(summary = "기준일 브리핑", description = "YYYY-MM-DD. 회원 필드 규칙은 최신 브리핑과 같다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "날짜 형식 오류",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "해당 기준일 브리핑 없음",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{date}")
    fun byDate(
        @Parameter(description = "기준일 (YYYY-MM-DD)")
        @PathVariable date: String,
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long?,
    ): DataResponse<BriefingResponse>
}
