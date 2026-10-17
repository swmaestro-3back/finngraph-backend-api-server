package com.finngraph.web.search

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
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "Search", description = "종목·테마·이슈 통합 검색")
interface SearchApi {

    @Operation(
        summary = "통합 검색",
        description = "q로 종목·테마·이슈를 한 번에 찾는다. 대소문자를 가리지 않는 부분 일치다. " +
            "종목은 이름·종목코드(앞부분)·회사 별칭(company_aliases)으로 찾고, 정확히 같음 → 앞부분 같음 → 그 밖 순, 같은 순위 안에서는 시가총액 내림차순이다. " +
            "테마는 이름으로 찾고 정확히 같음 → 앞부분 같음 → 짧은 이름 순이다. " +
            "이슈는 이슈 목록과 같은 공개 규칙의 클러스터 중 제목·키워드(정확히 같음)·기사 제목으로 찾고 마지막 보도 시각 내림차순이다. " +
            "묶음마다 최대 limit개",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공. 못 찾은 묶음은 빈 배열"),
        ApiResponse(
            responseCode = "400",
            description = "q가 없거나 공백, 50자 초과, limit이 1~20 밖",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/api/v1/search")
    fun search(
        @Parameter(description = "검색어(앞뒤 공백 제거 후 1~50자)") @RequestParam(required = false) q: String?,
        @Parameter(description = "묶음별 최대 개수(1~20)") @RequestParam(defaultValue = "5") limit: Int,
    ): DataResponse<SearchResponse>
}
