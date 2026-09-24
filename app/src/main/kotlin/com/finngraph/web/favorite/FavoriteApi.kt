package com.finngraph.web.favorite

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
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "Favorite", description = "관심 즐겨찾기 (종목·테마)")
@RequestMapping("/api/v1/me/favorites")
interface FavoriteApi {

    @Operation(summary = "관심 목록", description = "등록 역순. 종목은 현재가·등락률, 테마는 등락률·기준일을 함께 내려준다")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "401",
            description = "토큰 부재·만료·위조 또는 탈퇴한 사용자",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping
    fun list(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): DataResponse<FavoriteListResponse>

    @Operation(summary = "관심 등록", description = "멱등 — 이미 등록된 대상도 200. 합계 50개 초과만 409")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "등록됨 또는 이미 등록됨"),
        ApiResponse(
            responseCode = "400",
            description = "type이 STOCK/THEME가 아니거나 key 형식 오류",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "토큰에 이상이 있거나 탈퇴한 사용자",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 종목·테마",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "관심 목록 상한(50) 초과",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PutMapping("/{type}/{key}")
    fun add(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "STOCK 또는 THEME") @PathVariable type: String,
        @Parameter(description = "STOCK이면 ticker, THEME이면 테마 id") @PathVariable key: String,
    ): DataResponse<FavoriteResponse>

    @Operation(summary = "관심 해제", description = "멱등 — 없는 대상을 해제해도 204")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "해제 완료"),
        ApiResponse(
            responseCode = "400",
            description = "type이 STOCK/THEME가 아니거나 key 형식 오류",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "토큰에 이상이 있거나 탈퇴한 사용자",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @DeleteMapping("/{type}/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "STOCK 또는 THEME") @PathVariable type: String,
        @Parameter(description = "STOCK이면 ticker, THEME이면 테마 id") @PathVariable key: String,
    )

    @Operation(summary = "관심종목 뉴스 피드", description = "관심 STOCK 전체의 뉴스를 최신순으로. 관심 종목이 없으면 빈 페이지")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "page < 0, size < 1, size > 100",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "토큰에 이상이 있거나 탈퇴한 사용자",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/news")
    fun news(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "0-기반 페이지 번호") @RequestParam(required = false, defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(required = false, defaultValue = "20") size: Int,
    ): PageResponse<NewsResponse>
}
