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
        summary = "핫테마 등락률 상위",
        description = "상승 상위 절반 및 하락 상위 절반, 대시보드 트리맵과 동일 선정",
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
}
