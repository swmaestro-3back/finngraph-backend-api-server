package com.finngraph.web.news

import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorResponse
import com.finngraph.web.common.PageResponse
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

@Tag(name = "News", description = "뉴스 조회")
@RequestMapping("/api/v1/news")
interface NewsApi {

    @Operation(
        summary = "뉴스 목록",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "page < 0, size < 1, size > 100",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "503",
            description = "DB 접속 실패",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping
    fun list(
        @Parameter(description = "0-기반 페이지 번호") @RequestParam(required = false, defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(required = false, defaultValue = "20") size: Int,
    ): PageResponse<NewsResponse>

    @Operation(
        summary = "뉴스 상세",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 뉴스",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "400",
            description = "id 가 양수가 아니거나 숫자가 아님",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{id}")
    fun detail(@Parameter(description = "뉴스 id") @PathVariable id: Long): DataResponse<NewsDetailResponse>

    @Operation(
        summary = "뉴스별 관련 기업 + 시세",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "id 가 양수가 아니거나 숫자가 아님",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 뉴스",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{id}/companies")
    fun companies(@Parameter(description = "뉴스 id") @PathVariable id: Long): DataResponse<List<NewsRelatedStockResponse>>
}
