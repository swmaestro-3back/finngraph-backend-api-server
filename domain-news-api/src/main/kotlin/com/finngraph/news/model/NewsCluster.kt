package com.finngraph.news.model

import java.time.OffsetDateTime

data class NewsCluster(
    val id: Long,
    val title: String?,
    val keywords: List<String>,
    val memberCount: Int,
    val firstPublishedAt: OffsetDateTime,
    val lastPublishedAt: OffsetDateTime,
)

data class ClusterArticle(
    val newsId: Long,
    val clusterId: Long,
    val title: String?,
    val url: String?,
    val publishedAt: OffsetDateTime?,
    val summary: String?,
)
