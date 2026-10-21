package com.finngraph.web.issue

import com.finngraph.composition.issue.IssueArticleView
import com.finngraph.composition.issue.IssueCompany
import com.finngraph.composition.issue.IssueDetail
import com.finngraph.composition.issue.IssuePage
import com.finngraph.composition.issue.IssueStoryCompany
import com.finngraph.composition.issue.IssueSummary
import com.finngraph.composition.issue.IssueTimeline
import com.finngraph.composition.issue.IssueTimelineNode
import com.finngraph.composition.issue.LatestStockIssue
import com.finngraph.composition.issue.StockIssue
import com.finngraph.composition.issue.StockIssueFlow
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
    @Schema(description = "이슈 성격. event: 회사에 일어난 사건, market_reaction: 외부 요인·테마에 따른 주가 반응, null: 아직 분류 전", allowableValues = ["event", "market_reaction"])
    val kind: String?,
    @Schema(description = "이어진 이슈 흐름에서 이 이슈가 몇 번째인지(앞선 이슈 노드 수 + 1, same_event로 이어진 이슈는 같은 노드). 연결 전이거나 흐름의 첫 이슈면 1")
    val timelinePosition: Int,
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
            kind = issue.kind,
            timelinePosition = issue.timelinePosition,
        )
    }
}

data class IssueCompanyResponse(
    val ticker: String,
    val name: String,
    @Schema(description = "이 종목을 언급한 공개 기사 수")
    val mentionCount: Int,
    @Schema(
        description = "이 이슈 기사에서 뽑은 관계 근거 문장 중 이 종목이 나온 한 문장(이 종목이 주어인 것 → 대표 기사 → 최신 순). " +
            "언급 많은 종목부터 고르며 앞 종목이 이미 쓴 문장은 다른 후보가 있으면 피한다. 이슈 상세에서만 채우고 목록·타임라인에서는 null",
    )
    val role: String? = null,
) {
    companion object {
        fun from(company: IssueCompany) = IssueCompanyResponse(company.ticker, company.name, company.mentionCount, company.role)
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
    @Schema(description = "대표 기사 요약의 핵심 포인트(ETL 표시 순서 그대로, 보통 2~3개). kind 는 CHANGE·AFFECTED·SCALE·CAUSE·RIPPLE")
    val summaryPoints: List<SummaryPointResponse>,
    @Schema(description = "공개 기사에 언급된 상장 종목 전부. mentionCount 내림차순")
    val companies: List<IssueCompanyResponse>,
    @Schema(description = "공개 기사 전부. publishedAt 오름차순")
    val articles: List<IssueArticleResponse>,
    @Schema(description = "이슈 성격. event: 회사에 일어난 사건, market_reaction: 외부 요인·테마에 따른 주가 반응, null: 아직 분류 전", allowableValues = ["event", "market_reaction"])
    val kind: String?,
    @Schema(description = "이어진 이슈 흐름에서 이 이슈가 몇 번째인지(앞선 이슈 노드 수 + 1, same_event로 이어진 이슈는 같은 노드). 연결 전이거나 흐름의 첫 이슈면 1")
    val timelinePosition: Int,
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
                summaryPoints = detail.summaryPoints.map { SummaryPointResponse(it.kind, it.text) },
                companies = companies,
                articles = detail.articles.map(IssueArticleResponse::from),
                kind = kind,
                timelinePosition = timelinePosition,
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
    @Schema(description = "이 기사에 언급된 상장 종목코드(중복 제거). 이슈 상세에서만 채우고 그 밖에서는 빈 배열")
    val tickers: List<String> = emptyList(),
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
            tickers = article.tickers,
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
    @Schema(description = "이슈 성격. event: 회사에 일어난 사건, market_reaction: 외부 요인·테마에 따른 주가 반응, null: 아직 분류 전", allowableValues = ["event", "market_reaction"])
    val kind: String?,
    @Schema(description = "이어진 이슈 흐름에서 이 이슈가 몇 번째인지(앞선 이슈 노드 수 + 1, same_event로 이어진 이슈는 같은 노드). 연결 전이거나 흐름의 첫 이슈면 1")
    val timelinePosition: Int,
    @Schema(description = "이 이슈가 속한 흐름의 첫 이슈 id(news_clusters.story_root_id, 흐름 첫 이슈는 자기 id). ETL 연결 판정 전이면 null")
    val storyRootId: Long?,
    @Schema(description = "흐름 첫 이슈의 클러스터 제목. 이 이슈가 흐름의 첫 이슈(storyRootId = id)이거나, 첫 이슈가 공개 이슈가 아니거나 제목이 없으면 null")
    val storyTitle: String?,
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
                kind = kind,
                timelinePosition = timelinePosition,
                storyRootId = found.issue.storyRootId,
                storyTitle = found.storyTitle,
            )
        }
    }
}

data class StockIssueFlowResponse(
    @Schema(description = "흐름 id. 이어진 흐름의 첫 이슈(news_clusters.story_root_id)")
    val id: Long,
    @Schema(description = "흐름 이름. 흐름 첫 이슈(story_root_id)의 클러스터 제목, 없으면 이 종목이 나온 가장 이른 이슈의 제목")
    val title: String?,
    @Schema(description = "흐름 안에서 이 종목이 나온 이슈 수(2 이상)")
    val issueCount: Int,
    @Schema(description = "흐름 안 이슈들의 공개 기사 보도 시각 최솟값")
    val firstPublishedAt: OffsetDateTime?,
    @Schema(description = "흐름 안 이슈들의 공개 기사 보도 시각 최댓값")
    val lastPublishedAt: OffsetDateTime?,
    @Schema(description = "흐름 첫 이슈의 매체 수")
    val firstMediaCount: Int,
    @Schema(description = "흐름 마지막 이슈의 매체 수")
    val lastMediaCount: Int,
    @Schema(description = "가장 최근 공개 기사 보도 시각 기준 7일 안에 보도가 있으면 true")
    val ongoing: Boolean,
    @Schema(description = "흐름 안에서 이 종목이 나온 이슈. 처음 보도 시각 오름차순")
    val issues: List<StockIssueResponse>,
) {
    companion object {
        fun from(flow: StockIssueFlow): StockIssueFlowResponse {
            val issues = flow.issues.map(StockIssueResponse::from)
            return StockIssueFlowResponse(
                id = flow.id,
                title = flow.title ?: issues.first().title,
                issueCount = issues.size,
                firstPublishedAt = issues.mapNotNull { it.firstPublishedAt }.minOrNull(),
                lastPublishedAt = issues.mapNotNull { it.lastPublishedAt }.maxOrNull(),
                firstMediaCount = issues.first().mediaCount,
                lastMediaCount = issues.last().mediaCount,
                ongoing = flow.ongoing,
                issues = issues,
            )
        }
    }
}

data class IssueLeadResponse(
    @Schema(description = "요청한 이슈 id(news_clusters.id)")
    val id: Long,
    @Schema(description = "같은 사건(same_event)으로 합쳐지는 이슈 묶음의 대표 이슈 id. 합쳐질 이슈가 없으면 id와 같다")
    val leadId: Long,
)

data class LatestStockIssueResponse(
    val ticker: String,
    @Schema(description = "최근 30일 안에서 lastPublishedAt이 가장 늦은 이슈. 없으면 null")
    val issue: StockIssueResponse?,
) {
    companion object {
        fun from(latest: LatestStockIssue) = LatestStockIssueResponse(latest.ticker, latest.issue?.let(StockIssueResponse::from))
    }
}

data class IssueTimelineResponse(
    @Schema(description = "요청 이슈 id")
    val issueId: Long,
    @Schema(description = "후속 이슈부터 요청 이슈, 앞선 이슈 순(최신순)")
    val nodes: List<IssueTimelineNodeResponse>,
    @Schema(description = "노드 공개 기사에 언급된 상장 종목. issueCount(언급된 노드 수) 내림차순, 최대 5개")
    val companies: List<IssueStoryCompanyResponse>,
) {
    companion object {
        fun from(timeline: IssueTimeline) = IssueTimelineResponse(
            issueId = timeline.issueId,
            nodes = timeline.nodes.map(IssueTimelineNodeResponse::from),
            companies = timeline.companies.map(IssueStoryCompanyResponse::from),
        )
    }
}

data class IssueStoryCompanyResponse(
    val ticker: String,
    val name: String,
    @Schema(description = "이 종목이 언급된 노드 수")
    val issueCount: Int,
) {
    companion object {
        fun from(company: IssueStoryCompany) = IssueStoryCompanyResponse(company.ticker, company.name, company.issueCount)
    }
}

data class IssueTimelineNodeResponse(
    @Schema(description = "노드 대표 이슈 id. 같은 사건으로 합친 이슈 중 편입 기사 수(member_count)가 가장 많은 이슈")
    val issueId: Long,
    @Schema(description = "대표 이슈의 클러스터 제목")
    val title: String,
    @Schema(description = "대표 공개 기사 요약 핵심 포인트의 CHANGE 문장 → 요약 첫 문장. 대표 이슈에 둘 다 없으면 대표를 고르는 순서대로 합친 다른 이슈에서 찾는다. 모두 없으면 null")
    val summary: String?,
    @Schema(description = "firstPublishedAt의 KST 날짜. firstPublishedAt이 null이면 null", example = "2026-10-01")
    val date: LocalDate?,
    @Schema(description = "합친 이슈 전체의 공개 기사 보도 시각 최솟값. 보도 시각이 있는 공개 기사가 없으면 null")
    val firstPublishedAt: OffsetDateTime?,
    @Schema(description = "합친 이슈 전체의 공개 기사 보도 시각 최댓값. 보도 시각이 있는 공개 기사가 없으면 null")
    val lastPublishedAt: OffsetDateTime?,
    @Schema(description = "요청 이슈가 이 노드에 들어 있는지")
    val current: Boolean,
    @Schema(description = "이 노드로 합친 이슈 id 전부(대표 포함). 사슬 밖에서 같은 사건으로 갈라진 이슈도 든다. 클러스터 첫 기사 시각 최신순")
    val mergedIssueIds: List<Long>,
    @Schema(description = "합친 이슈 전체의 공개 기사 수")
    val articleCount: Int,
    @Schema(description = "합친 이슈 전체의 공개 기사 URL 호스트(소문자, www. 제거)로 센 고유 매체 수")
    val mediaCount: Int,
    @Schema(description = "합친 이슈 전체의 공개 기사에 언급된 상장 종목. mentionCount 내림차순, 최대 5개")
    val companies: List<IssueCompanyResponse>,
    @Schema(description = "합친 이슈 전체의 공개 기사에 언급된 상장 종목 수(companies는 5개로 잘림)")
    val companyCount: Int,
) {
    companion object {
        fun from(node: IssueTimelineNode) = IssueTimelineNodeResponse(
            issueId = node.issueId,
            title = node.title,
            summary = node.summary,
            date = node.date,
            firstPublishedAt = node.firstPublishedAt,
            lastPublishedAt = node.lastPublishedAt,
            current = node.current,
            mergedIssueIds = node.mergedIssueIds,
            articleCount = node.articleCount,
            mediaCount = node.mediaCount,
            companies = node.companies.map(IssueCompanyResponse::from),
            companyCount = node.companyCount,
        )
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

data class SummaryPointResponse(
    val kind: String,
    val text: String,
)
