package com.finngraph.web.issue

import com.finngraph.composition.issue.IssueArticleView
import com.finngraph.composition.issue.IssueCompany
import com.finngraph.composition.issue.IssueDetail
import com.finngraph.composition.issue.IssuePage
import com.finngraph.composition.issue.IssueSummary
import com.finngraph.composition.issue.LatestStockIssue
import com.finngraph.composition.issue.StockIssue
import com.finngraph.composition.issue.ThemeIssueBoard
import com.finngraph.composition.issue.ThemeIssues
import com.finngraph.web.common.Pagination
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.OffsetDateTime

data class IssuePageResponse(
    val data: List<IssueSummaryResponse>,
    val pagination: Pagination,
    val meta: IssueListMeta,
) {
    companion object {
        fun from(page: IssuePage) = IssuePageResponse(
            data = page.result.content.map(IssueSummaryResponse::from),
            pagination = Pagination(page.result.page, page.result.size, page.result.totalElements, page.result.totalPages),
            meta = IssueListMeta(page.date, page.prevDate, page.nextDate),
        )
    }
}

data class IssueListMeta(
    @Schema(description = "조회한 날짜(KST). date를 빼고 공개 기사가 하나도 없으면 null")
    val date: LocalDate?,
    @Schema(description = "이 날짜 전에 이슈의 공개 기사가 보도된 가장 가까운 날짜. 없으면 null")
    val prevDate: LocalDate?,
    @Schema(description = "이 날짜 뒤에 이슈의 공개 기사가 보도된 가장 가까운 날짜. 없으면 null")
    val nextDate: LocalDate?,
)

data class IssueSummaryResponse(
    val id: Long,
    @Schema(description = "클러스터 제목 → 대표 공개 기사 제목 → 가장 이른 공개 기사 제목 순으로 고른 제목")
    val title: String?,
    @Schema(description = "제목 출처", allowableValues = ["cluster", "article"])
    val titleSource: String,
    @Schema(description = "공개 기사 수")
    val articleCount: Int,
    @Schema(description = "공개 기사 URL 호스트(소문자, www. 제거)로 센 고유 매체 수")
    val mediaCount: Int,
    @Schema(description = "공개 기사 보도 시각의 최솟값. 보도 시각이 있는 공개 기사가 없으면 null")
    val firstPublishedAt: OffsetDateTime?,
    @Schema(description = "공개 기사 보도 시각의 최댓값. 보도 시각이 있는 공개 기사가 없으면 null")
    val lastPublishedAt: OffsetDateTime?,
    val keywords: List<String>,
    @Schema(description = "대표 공개 기사의 요약")
    val summary: String?,
    @Schema(description = "대표 공개 기사 id. 클러스터 대표 기사가 공개 기사가 아니면 가장 이른 공개 기사")
    val representativeNewsId: Long,
    @Schema(description = "공개 기사에 언급된 상장 종목. mentionCount 내림차순, 목록은 최대 5개")
    val companies: List<IssueCompanyResponse>,
) {
    companion object {
        fun from(issue: IssueSummary) = IssueSummaryResponse(
            id = issue.id,
            title = issue.title,
            titleSource = issue.titleSource.name.lowercase(),
            articleCount = issue.articleCount,
            mediaCount = issue.mediaCount,
            firstPublishedAt = issue.firstPublishedAt,
            lastPublishedAt = issue.lastPublishedAt,
            keywords = issue.keywords,
            summary = issue.summary,
            representativeNewsId = issue.representativeNewsId,
            companies = issue.companies.map(IssueCompanyResponse::from),
        )
    }
}

data class IssueCompanyResponse(
    val ticker: String,
    val name: String,
    @Schema(description = "이 종목을 언급한 공개 기사 수")
    val mentionCount: Int,
) {
    companion object {
        fun from(company: IssueCompany) = IssueCompanyResponse(company.ticker, company.name, company.mentionCount)
    }
}

data class IssueDetailResponse(
    val id: Long,
    @Schema(description = "클러스터 제목 → 대표 공개 기사 제목 → 가장 이른 공개 기사 제목 순으로 고른 제목")
    val title: String?,
    @Schema(description = "제목 출처", allowableValues = ["cluster", "article"])
    val titleSource: String,
    @Schema(description = "공개 기사 수")
    val articleCount: Int,
    @Schema(description = "공개 기사 URL 호스트(소문자, www. 제거)로 센 고유 매체 수")
    val mediaCount: Int,
    @Schema(description = "공개 기사 보도 시각의 최솟값. 보도 시각이 있는 공개 기사가 없으면 null")
    val firstPublishedAt: OffsetDateTime?,
    @Schema(description = "공개 기사 보도 시각의 최댓값. 보도 시각이 있는 공개 기사가 없으면 null")
    val lastPublishedAt: OffsetDateTime?,
    val keywords: List<String>,
    @Schema(description = "대표 공개 기사의 요약")
    val summary: String?,
    @Schema(description = "대표 공개 기사 id. 클러스터 대표 기사가 공개 기사가 아니면 가장 이른 공개 기사")
    val representativeNewsId: Long,
    @Schema(description = "공개 기사에 언급된 상장 종목 전부. mentionCount 내림차순")
    val companies: List<IssueCompanyResponse>,
    @Schema(description = "공개 기사 전부. publishedAt 오름차순")
    val articles: List<IssueArticleResponse>,
) {
    companion object {
        fun from(detail: IssueDetail) = with(IssueSummaryResponse.from(detail.issue)) {
            IssueDetailResponse(
                id = id,
                title = title,
                titleSource = titleSource,
                articleCount = articleCount,
                mediaCount = mediaCount,
                firstPublishedAt = firstPublishedAt,
                lastPublishedAt = lastPublishedAt,
                keywords = keywords,
                summary = summary,
                representativeNewsId = representativeNewsId,
                companies = companies,
                articles = detail.articles.map(IssueArticleResponse::from),
            )
        }
    }
}

data class IssueArticleResponse(
    val id: Long,
    val title: String?,
    @Schema(description = "원문 URL. 원문이 없으면 수집 URL")
    val url: String?,
    @Schema(description = "url 호스트(소문자, www. 제거)")
    val press: String?,
    val publishedAt: OffsetDateTime?,
    val summary: String?,
    @Schema(description = "관계 추출 결과. 공개 기사만 내므로 항상 true")
    val tripleExtracted: Boolean,
) {
    companion object {
        fun from(article: IssueArticleView) = IssueArticleResponse(
            id = article.id,
            title = article.title,
            url = article.url,
            press = article.press,
            publishedAt = article.publishedAt,
            summary = article.summary,
            tripleExtracted = article.tripleExtracted,
        )
    }
}

data class StockIssueResponse(
    val id: Long,
    @Schema(description = "클러스터 제목 → 대표 공개 기사 제목 → 가장 이른 공개 기사 제목 순으로 고른 제목")
    val title: String?,
    @Schema(description = "제목 출처", allowableValues = ["cluster", "article"])
    val titleSource: String,
    @Schema(description = "공개 기사 수")
    val articleCount: Int,
    @Schema(description = "공개 기사 URL 호스트(소문자, www. 제거)로 센 고유 매체 수")
    val mediaCount: Int,
    @Schema(description = "공개 기사 보도 시각의 최솟값. 보도 시각이 있는 공개 기사가 없으면 null")
    val firstPublishedAt: OffsetDateTime?,
    @Schema(description = "공개 기사 보도 시각의 최댓값. 보도 시각이 있는 공개 기사가 없으면 null")
    val lastPublishedAt: OffsetDateTime?,
    val keywords: List<String>,
    @Schema(description = "대표 공개 기사의 요약")
    val summary: String?,
    @Schema(description = "대표 공개 기사 id. 클러스터 대표 기사가 공개 기사가 아니면 가장 이른 공개 기사")
    val representativeNewsId: Long,
    @Schema(description = "공개 기사에 언급된 상장 종목. mentionCount 내림차순, 최대 5개")
    val companies: List<IssueCompanyResponse>,
    @Schema(description = "이 이슈에서 이 종목을 언급한 공개 기사 수")
    val mentionCount: Int,
) {
    companion object {
        fun from(found: StockIssue) = with(IssueSummaryResponse.from(found.issue)) {
            StockIssueResponse(
                id = id,
                title = title,
                titleSource = titleSource,
                articleCount = articleCount,
                mediaCount = mediaCount,
                firstPublishedAt = firstPublishedAt,
                lastPublishedAt = lastPublishedAt,
                keywords = keywords,
                summary = summary,
                representativeNewsId = representativeNewsId,
                companies = companies,
                mentionCount = found.mentionCount,
            )
        }
    }
}

data class LatestStockIssueResponse(
    val ticker: String,
    @Schema(description = "최근 30일 안에서 lastPublishedAt이 가장 늦은 이슈. 없으면 null")
    val issue: StockIssueResponse?,
) {
    companion object {
        fun from(latest: LatestStockIssue) = LatestStockIssueResponse(latest.ticker, latest.issue?.let(StockIssueResponse::from))
    }
}

data class ThemeIssueBoardResponse(
    val data: List<ThemeIssuesResponse>,
    val meta: ThemeIssueMeta,
) {
    companion object {
        fun from(board: ThemeIssueBoard) = ThemeIssueBoardResponse(
            data = board.themes.map(ThemeIssuesResponse::from),
            meta = ThemeIssueMeta(board.date),
        )
    }
}

data class ThemeIssueMeta(
    @Schema(description = "조회한 날짜(KST). date를 빼고 공개 기사가 하나도 없으면 null")
    val date: LocalDate?,
)

data class ThemeIssuesResponse(
    val themeId: Long,
    @Schema(description = "그날 이 테마 이슈 수")
    val issueCount: Int,
    @Schema(description = "그날 이 테마 이슈 id 전부. issues와 같은 순서")
    val issueIds: List<Long>,
    @Schema(description = "매체 수·기사 수·마지막 보도 내림차순 상위 3개")
    val issues: List<IssueSummaryResponse>,
) {
    companion object {
        fun from(found: ThemeIssues) = ThemeIssuesResponse(
            themeId = found.themeId,
            issueCount = found.issueCount,
            issueIds = found.issueIds,
            issues = found.issues.map(IssueSummaryResponse::from),
        )
    }
}
