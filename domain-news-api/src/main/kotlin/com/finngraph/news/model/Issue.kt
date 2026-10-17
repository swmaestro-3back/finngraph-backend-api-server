package com.finngraph.news.model

import java.time.OffsetDateTime

data class IssueCluster(
    val id: Long,
    val title: String?,
    val keywords: List<String>,
    val representativeNewsId: Long?,
    val kind: String? = null,
    val storyRootId: Long? = null,
)

data class IssueArticle(
    val id: Long,
    val clusterId: Long,
    val title: String?,
    val url: String?,
    val publishedAt: OffsetDateTime?,
    val summary: String?,
    val summaryPoints: List<SummaryPoint>,
    val tripleExtracted: Boolean,
)

data class SummaryPoint(
    val kind: String,
    val text: String,
)

data class IssueNeighbors(
    val latestBefore: OffsetDateTime?,
    val earliestFrom: OffsetDateTime?,
)

data class IssueMention(
    val ticker: String,
    val clusterId: Long,
    val lastPublishedAt: OffsetDateTime?,
)

enum class IssueLinkRelation { FOLLOW_UP, SAME_EVENT }

data class IssueChainLink(
    val clusterId: Long,
    val relation: IssueLinkRelation?,
    val originalSize: Int,
    val startedAt: OffsetDateTime,
)

data class IssueDescendant(
    val link: IssueChainLink,
    val parentId: Long,
)

data class IssueChainStep(
    val clusterId: Long,
    val relation: IssueLinkRelation?,
    val event: Boolean,
)
