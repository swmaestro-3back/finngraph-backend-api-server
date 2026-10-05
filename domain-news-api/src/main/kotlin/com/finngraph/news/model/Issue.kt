package com.finngraph.news.model

import java.time.OffsetDateTime

data class IssueCluster(
    val id: Long,
    val title: String?,
    val keywords: List<String>,
    val representativeNewsId: Long?,
)

data class IssueArticle(
    val id: Long,
    val clusterId: Long,
    val title: String?,
    val url: String?,
    val publishedAt: OffsetDateTime?,
    val summary: String?,
    val tripleExtracted: Boolean,
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
