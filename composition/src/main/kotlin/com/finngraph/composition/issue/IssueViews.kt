package com.finngraph.composition.issue

import com.finngraph.news.model.PageResult
import java.time.LocalDate
import java.time.OffsetDateTime

data class IssueCompany(
    val ticker: String,
    val name: String,
    val mentionCount: Int,
)

data class IssueSummary(
    val id: Long,
    val title: String?,
    val titleSource: IssueTitleSource,
    val articleCount: Int,
    val mediaCount: Int,
    val firstPublishedAt: OffsetDateTime?,
    val lastPublishedAt: OffsetDateTime?,
    val keywords: List<String>,
    val summary: String?,
    val representativeNewsId: Long,
    val companies: List<IssueCompany>,
)

data class IssueArticleView(
    val id: Long,
    val title: String?,
    val url: String?,
    val press: String?,
    val publishedAt: OffsetDateTime?,
    val summary: String?,
    val tripleExtracted: Boolean,
)

data class IssueDetail(
    val issue: IssueSummary,
    val articles: List<IssueArticleView>,
)

data class IssuePage(
    val date: LocalDate?,
    val prevDate: LocalDate?,
    val nextDate: LocalDate?,
    val result: PageResult<IssueSummary>,
)

data class StockIssue(
    val issue: IssueSummary,
    val mentionCount: Int,
)

data class LatestStockIssue(
    val ticker: String,
    val issue: StockIssue?,
)

data class ThemeIssues(
    val themeId: Long,
    val issueCount: Int,
    val issueIds: List<Long>,
    val issues: List<IssueSummary>,
)

data class ThemeIssueBoard(
    val date: LocalDate?,
    val themes: List<ThemeIssues>,
)

data class IssueTimeline(
    val issueId: Long,
    val nodes: List<IssueTimelineNode>,
)

data class IssueTimelineNode(
    val issueId: Long,
    val title: String,
    val summary: String?,
    val date: LocalDate?,
    val firstPublishedAt: OffsetDateTime?,
    val lastPublishedAt: OffsetDateTime?,
    val current: Boolean,
    val mergedIssueIds: List<Long>,
)

// 타임라인 노드 후보. originalSize(편입 기사 수)는 같은 사건으로 합친 이슈 중 대표를 고를 때 쓴다.
data class TimelineMember(
    val issue: IssueSummary,
    val originalSize: Int,
)
