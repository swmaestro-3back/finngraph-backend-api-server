package com.finngraph.news

import java.time.OffsetDateTime

data class NewsView(
    val id: Long,
    val title: String?,
    val summary: String?,
    val link: String?,
    val publishedAt: OffsetDateTime?,
    val sourceType: String?,
)

data class NewsDetail(
    val id: Long,
    val title: String?,
    val summary: String?,
    val link: String?,
    val originallink: String?,
    val publishedAt: OffsetDateTime?,
    val sourceType: String?,
)
