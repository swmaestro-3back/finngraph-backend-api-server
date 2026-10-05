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
