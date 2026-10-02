package com.finngraph.web.calendar

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
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "Calendar", description = "증시 캘린더(휴장일·배당·증자·주총)와 공모주")
interface CalendarApi {

    @Operation(summary = "시장 캘린더", description = "휴장일과 KRX300 종목의 배당,무상,유상증자,주총 일정. 기간은 양끝 포함 최대 62일")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "from-to 누락, YYYY-MM-DD 형식 오류, from > to, 62일 초과",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/api/v1/calendar")
    fun calendar(
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
    ): DataResponse<CalendarResponse>

    @Operation(summary = "내 캘린더", description = "시장 캘린더에 관심종목 일정을 더한다. 관심종목 일정은 favorite=true로 먼저 정렬")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "기간 파라미터 오류",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "토큰 부재, 만료, 위조 또는 탈퇴한 사용자",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/api/v1/me/calendar")
    fun myCalendar(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
    ): DataResponse<CalendarResponse>

    @Operation(
        summary = "종목 일정",
        description = "한 종목의 일정을 권리 단위로 묶고 매수 마감일, 배당, 유상, 무상증자 지표를 더한다. from-to는 기준일 범위로 양끝 포함 최대 366일",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "ticker 형식 오류, from-to 누락, YYYY-MM-DD 형식 오류, from > to, 366일 초과",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 종목",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/api/v1/stocks/{ticker}/calendar")
    fun stockCalendar(
        @Parameter(description = "종목코드") @PathVariable ticker: String,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
    ): DataResponse<StockCalendarResponse>

    @Operation(summary = "공모주", description = "오늘(KST)기준 14일 전 ~ 30일 후와 겹치는 공모. 상태는 UPCOMING·SUBSCRIBING·LISTING_PENDING·LISTED")
    @ApiResponses(ApiResponse(responseCode = "200", description = "조회 성공"))
    @GetMapping("/api/v1/ipos")
    fun ipos(): DataResponse<IpoListResponse>
}
