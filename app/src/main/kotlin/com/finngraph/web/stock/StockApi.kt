package com.finngraph.web.stock

import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorResponse
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
            description = "ticker 검증 실패, limit 이 1~200 범위 밖",
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
        @Parameter(description = "거래일 개수 (1~200)")
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
}
