package com.finngraph.web.issue

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
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "Issue", description = "같은 사건을 다룬 기사 묶음(ETL news_clusters) 조회")
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
    @GetMapping("/api/v1/issues")
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
    @GetMapping("/api/v1/issues/{id}")
    fun detail(@Parameter(description = "이슈 id(news_clusters.id)") @PathVariable id: Long): DataResponse<IssueDetailResponse>

    @Operation(
        summary = "종목이 나온 이슈",
        description = "그 종목을 언급한 공개 기사가 속한 이슈 중 from~to(KST, 양끝 포함)에 보도된 공개 기사가 1건 이상 있는 이슈. " +
            "언급 기사는 기간 밖이어도 된다. 항목은 이슈 목록 항목에 mentionCount(이 이슈에서 이 종목을 언급한 공개 기사 수)를 더한다. " +
            "lastPublishedAt 내림차순, 같으면 id 내림차순. to를 빼면 오늘(KST), from을 빼면 to − 365일",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공. 이슈가 없으면 data가 빈 배열"),
        ApiResponse(
            responseCode = "400",
            description = "ticker 가 공백이거나 20자를 초과, from·to가 YYYY-MM-DD가 아님, to < from, page < 0, size < 1, size > 100",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 종목",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/api/v1/stocks/{ticker}/issues")
    fun stockIssues(
        @Parameter(description = "종목코드") @PathVariable ticker: String,
        @Parameter(description = "시작일 YYYY-MM-DD(KST, 포함). 생략하면 to − 365일") @RequestParam(required = false) from: String?,
        @Parameter(description = "종료일 YYYY-MM-DD(KST, 포함). 생략하면 오늘") @RequestParam(required = false) to: String?,
        @Parameter(description = "0-기반 페이지 번호") @RequestParam(required = false, defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(required = false, defaultValue = "20") size: Int,
    ): PageResponse<StockIssueResponse>

    @Operation(
        summary = "종목별 최근 이슈 하나",
        description = "종목마다 최근 30일(KST 오늘 − 30일 ~ 오늘, 양끝 포함)에 보도된 공개 기사가 있는 이슈 중 lastPublishedAt이 가장 늦은 이슈. " +
            "항목은 종목이 나온 이슈 항목과 같다. 요청 순서대로 중복 없이 내고, 이슈가 없거나 활성 종목이 아니면 issue가 null",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "tickers 누락, 50개 초과, 20자를 넘는 종목코드",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/api/v1/stocks/issues/latest")
    fun latestStockIssues(
        @Parameter(description = "쉼표로 구분한 종목코드 (최대 50개)") @RequestParam(required = false) tickers: String?,
    ): DataResponse<List<LatestStockIssueResponse>>

    @Operation(
        summary = "테마별 그날 이슈",
        description = "date(KST) 이슈 목록에 나오는 이슈 중 언급 상장 종목(이슈 상세 companies 전체)에 그 테마의 활성 구성 종목이 하나라도 있는 이슈. " +
            "issueIds는 전부, issues는 상위 3개이며 정렬은 매체 수·기사 수·마지막 보도 내림차순. " +
            "요청 순서대로 중복 없이 내고, 없는 테마는 빈 항목이다. date를 빼면 이슈 목록 기본 날짜를 쓴다",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "ids 누락, 양의 정수가 아닌 id, 50개 초과, date가 YYYY-MM-DD가 아님",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/api/v1/themes/issues")
    fun themeIssues(
        @Parameter(description = "쉼표로 구분한 테마 id (최대 50개)") @RequestParam(required = false) ids: String?,
        @Parameter(description = "조회 날짜 YYYY-MM-DD(KST). 생략하면 공개 기사가 보도된 가장 최근 날짜") @RequestParam(required = false) date: String?,
    ): ThemeIssueBoardResponse
}
