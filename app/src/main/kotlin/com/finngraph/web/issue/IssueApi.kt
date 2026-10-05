package com.finngraph.web.issue

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

@Tag(name = "Issue", description = "같은 사건을 다룬 기사 묶음(ETL news_clusters) 조회")
@RequestMapping("/api/v1/issues")
interface IssueApi {

    @Operation(
        summary = "날짜별 이슈 목록",
        description = "date(KST)에 보도된 공개 기사가 1건 이상 있는 이슈. 공개 기사는 관계 추출이 끝나 삼중항이 나온 기사다. " +
            "기사 수·매체 수·첫·마지막 보도 시각은 공개 기사로만 계산한다. " +
            "date를 빼면 공개 기사가 보도된 가장 최근 날짜를 쓴다. meta.prevDate·nextDate는 공개 기사가 보도된 인접 날짜(없으면 null)다. " +
            "companies는 언급 기사 수 상위 5개 상장 종목이며 시세는 없다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공. 이슈가 없는 날짜면 data가 빈 배열"),
        ApiResponse(
            responseCode = "400",
            description = "date가 YYYY-MM-DD가 아님, sort가 media·recent가 아님, page < 0, size < 1, size > 100",
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
        @Parameter(description = "조회 날짜 YYYY-MM-DD(KST). 생략하면 공개 기사가 보도된 가장 최근 날짜")
        @RequestParam(required = false)
        date: String?,
        @Parameter(
            description = "정렬. media: 매체 수·기사 수·마지막 보도 순, recent: 마지막 보도 순",
            schema = Schema(allowableValues = ["media", "recent"], defaultValue = "media"),
        )
        @RequestParam(required = false, defaultValue = "media")
        sort: String,
        @Parameter(description = "0-기반 페이지 번호") @RequestParam(required = false, defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(required = false, defaultValue = "20") size: Int,
    ): IssuePageResponse

    @Operation(
        summary = "이슈 상세",
        description = "목록 항목 필드에 공개 기사 전부(publishedAt 오름차순)와 언급 상장 종목 전부를 더한다. " +
            "공개 기사가 없는 이슈는 404",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "id가 양수가 아니거나 숫자가 아님",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "없는 이슈이거나 공개 기사가 0건",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{id}")
    fun detail(@Parameter(description = "이슈 id(news_clusters.id)") @PathVariable id: Long): DataResponse<IssueDetailResponse>
}
