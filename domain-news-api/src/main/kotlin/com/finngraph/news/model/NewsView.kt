package com.finngraph.news.model

import java.time.OffsetDateTime

data class NewsView(
    val id: Long,
    val title: String?,
    val summary: String?,
    val url: String?,
    val publishedAt: OffsetDateTime?,
    val collectedAt: OffsetDateTime?,
)

data class NewsDetail(
    val id: Long,
    val title: String?,
    val summary: String?,
    val url: String?,
    val originalUrl: String?,
    val publishedAt: OffsetDateTime?,
    val collectedAt: OffsetDateTime?,
)
