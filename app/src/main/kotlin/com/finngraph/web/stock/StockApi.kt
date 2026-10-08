package com.finngraph.web.stock

import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorResponse
import com.finngraph.web.common.PageResponse
import com.finngraph.web.news.NewsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "Stock", description = "종목 조회")
@RequestMapping("/api/v1/stocks")
interface StockApi {

    @Operation(
        summary = "종목 전체 목록",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "503",
            description = "DB 접속 실패",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping
    fun list(): DataResponse<List<StockSummaryResponse>>

    @Operation(
        summary = "종목 단건",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "ticker 가 공백이거나 20자를 초과",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 종목",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{ticker}")
    fun detail(
        @Parameter(description = "종목코드") @PathVariable ticker: String,
    ): DataResponse<StockDetailResponse>

    @Operation(
        summary = "종목별 캔들",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "ticker 검증 실패, period 가 D/W/M 이 아님, limit 이 1~500 범위 밖",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 종목",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{ticker}/candles")
    fun candles(
        @Parameter(description = "종목코드") @PathVariable ticker: String,
        @Parameter(description = "캔들 주기 (D | W | M)")
        @RequestParam(required = false, defaultValue = "D")
        period: String,
        @Parameter(description = "개수 (1~500). 미지정 시 D=65, W=52, M=36")
        @RequestParam(required = false)
        limit: Int?,
    ): DataResponse<List<CandleResponse>>

    @Operation(
        summary = "종목별 투자자 수급",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "ticker 검증 실패, limit 이 1~250 범위 밖",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 종목",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{ticker}/investor-flows")
    fun investorFlows(
        @Parameter(description = "종목코드") @PathVariable ticker: String,
        @Parameter(description = "거래일 개수 (1~250)")
        @RequestParam(required = false, defaultValue = "40")
        limit: Int,
    ): DataResponse<List<InvestorFlowResponse>>

    @Operation(
        summary = "종목별 연간 재무",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "ticker 가 공백이거나 20자를 초과",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 종목",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{ticker}/financials")
    fun financials(
        @Parameter(description = "종목코드") @PathVariable ticker: String,
    ): DataResponse<List<AnnualFinancialsResponse>>

    @Operation(
        summary = "종목별 뉴스",
        description = "종목이 언급된 뉴스 전체",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "ticker 검증 실패, page < 0, size < 1, size > 100",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 종목",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{ticker}/news")
    fun news(
        @Parameter(description = "종목코드") @PathVariable ticker: String,
        @Parameter(description = "0-기반 페이지 번호") @RequestParam(required = false, defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(required = false, defaultValue = "20") size: Int,
    ): PageResponse<NewsResponse>

    @Operation(
        summary = "종목별 공급계약 공시",
        description = "제출사 또는 계약상대로 참여한 단일판매/공급계약 공시, 접수일 내림차순.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "ticker 검증 실패, limit 이 1~200 범위 밖",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{ticker}/contracts")
    fun contracts(
        @Parameter(description = "종목코드") @PathVariable ticker: String,
        @Parameter(description = "개수 (1~200)")
        @RequestParam(required = false, defaultValue = "50")
        limit: Int,
    ): DataResponse<List<StockContractResponse>>

    @Operation(
        summary = "종목 배당락 반응",
        description = "과거 배당마다 배당락일 시초가 갭, 이론 낙폭, 전날 종가 회복 거래일(최대 60). 최신 기준일순",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "ticker 가 공백이거나 20자를 초과",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 종목",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{ticker}/dividends")
    fun dividends(
        @Parameter(description = "종목코드") @PathVariable ticker: String,
    ): DataResponse<List<DividendReactionResponse>>

    @Operation(
        summary = "종목이 속한 테마",
        description = "테마 시가총액이 큰 순. 첫 항목이 종목 상세의 대표 테마(primary). change 는 테마 목록과 같은 테마 등락률",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "ticker 가 공백이거나 20자를 초과",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 종목",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{ticker}/themes")
    fun themes(
        @Parameter(description = "종목코드") @PathVariable ticker: String,
    ): DataResponse<List<StockThemeResponse>>

    @Operation(
        summary = "테마 안에서의 순위와 중앙값",
        description = "테마 활성 구성 종목 안에서 지표별 순위(RANK, 같은 값은 같은 순위)와 중앙값. " +
            "등락률·시가총액·거래대금·ROE·배당수익률은 큰 값이 1위, PER·PBR 은 낮은 값이 1위이고 0 이하는 뺀다. " +
            "값이 없는 종목은 순위·중앙값에서 빼고, 값이 있는 종목이 5개 미만이면 rank·median 은 null",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "ticker 검증 실패 또는 themeId 가 양수가 아님",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 종목·테마, 또는 그 테마에 속하지 않은 종목",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{ticker}/themes/{themeId}/compare")
    fun themeCompare(
        @Parameter(description = "종목코드") @PathVariable ticker: String,
        @Parameter(description = "테마 id") @PathVariable themeId: Long,
    ): DataResponse<StockThemeCompareResponse>
}
