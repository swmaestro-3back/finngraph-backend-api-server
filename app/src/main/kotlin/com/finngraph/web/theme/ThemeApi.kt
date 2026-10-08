package com.finngraph.web.theme

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

@Tag(name = "Theme", description = "테마 조회")
@RequestMapping("/api/v1/themes")
interface ThemeApi {

    @Operation(
        summary = "테마 전체 목록",
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
    fun list(): DataResponse<List<ThemeSummaryResponse>>

    @Operation(
        summary = "핫테마 상위",
        description = "상승 ⌈N/2⌉ + 하락 ⌊N/2⌋, change(절사평균) 크기로 순위. 신뢰구간 하한·상한이 시장 중앙값을 0.5%p 넘는 테마만 후보. 상승은 weightedChange > 0, 하락은 < 0인 테마만. 이미 뽑힌 같은 방향 테마와 구성 종목이 절반 넘게 겹치면(작은 쪽 기준) 건너뜀. 적재율 0.8 미만이면 빈 목록",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "count가 10, 20, 30이 아님",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/hot")
    fun hot(
        @Parameter(description = "표시 개수(10, 20, 30)") @RequestParam(required = false, defaultValue = "20") count: Int,
    ): DataResponse<List<ThemeSummaryResponse>>

    @Operation(
        summary = "시장 전체 등락 통계",
        description = "기준일·집계 종목 수·상승/하락/보합 수·등락률 중앙값·적재율, 핫테마 판정과 같은 유니버스",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "503",
            description = "DB 접속 실패",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/market")
    fun market(): DataResponse<ThemeMarketResponse>

    @Operation(
        summary = "이름에 검색어가 들어간 테마와 그 종목",
        description = "테마 이름에 q 가 들어간(대소문자 무시) 테마 전부와, 그 테마들에 속한 활성 종목코드의 합집합. 종목 목록의 테마 검색 필터용",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "q 가 공백이거나 30자를 초과",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/tickers")
    fun tickers(
        @Parameter(description = "테마 이름 검색어") @RequestParam(required = false) q: String?,
    ): DataResponse<ThemeTickersResponse>

    @Operation(
        summary = "테마 단건",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "name 이 공백",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 테마",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{id}")
    fun detail(
        @Parameter(description = "테마 id") @PathVariable id: Long,
    ): DataResponse<ThemeSummaryResponse>

    @Operation(
        summary = "테마 소속 종목",
        description = "시가총액 내림차순 — 첫 행이 대장주",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "name 이 공백",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 테마",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{id}/stocks")
    fun stocks(
        @Parameter(description = "테마 id") @PathVariable id: Long,
    ): DataResponse<List<ThemeStockResponse>>

    @Operation(
        summary = "테마별 뉴스",
        description = "테마 편입 종목이 언급된 뉴스",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "name 이 공백이거나 page < 0, size < 1, size > 100",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 테마",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{id}/news")
    fun news(
        @Parameter(description = "테마 id") @PathVariable id: Long,
        @Parameter(description = "0-기반 페이지 번호") @RequestParam(required = false, defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(required = false, defaultValue = "20") size: Int,
    ): PageResponse<NewsResponse>

    @Operation(
        summary = "테마 지수 캔들",
        description = "구성 종목 시가총액 가중 체인 지수(종목당 25% 상한, 1000 기준). 테마 기준일까지의 봉만 내려주고, " +
            "주·월봉은 일봉을 주 월요일·월 1일 단위로 묶는다. 응답 모양은 종목 캔들과 같고 tradeValue가 더 있다",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공. 지수가 없는 테마는 빈 배열"),
        ApiResponse(
            responseCode = "400",
            description = "id 가 양수가 아님, period 가 D/W/M 이 아님, limit 이 1~2500 범위 밖",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 테마",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{id}/candles")
    fun candles(
        @Parameter(description = "테마 id") @PathVariable id: Long,
        @Parameter(description = "캔들 주기 (D | W | M)")
        @RequestParam(required = false, defaultValue = "D")
        period: String,
        @Parameter(description = "개수 (1~2500). 미지정 시 D=65, W=52, M=36")
        @RequestParam(required = false)
        limit: Int?,
    ): DataResponse<List<ThemeIndexCandleResponse>>

    @Operation(
        summary = "테마 지수 성과",
        description = "기준일 지수 종가와 일간 등락률, 기간 수익률(종목 r_1w와 같은 달력 규칙), 52주 종가 고저와 고점 대비, " +
            "연속 등락일(상승 +n, 하락 -n). 구성종목 절사평균인 테마 change·w1·m1·m3와는 다른 지표다. 기준일 지수 행이 없으면 data 는 null",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "id 가 양수가 아님",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 테마",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{id}/index")
    fun index(
        @Parameter(description = "테마 id") @PathVariable id: Long,
    ): DataResponse<ThemeIndexResponse?>
}
