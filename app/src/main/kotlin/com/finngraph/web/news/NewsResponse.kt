package com.finngraph.web.news

import com.finngraph.news.model.NewsDetail
import com.finngraph.news.model.NewsView
import java.math.BigDecimal
import java.time.OffsetDateTime

data class NewsResponse(
    val id: Long,
    val title: String?,
    val summary: String?,
    val url: String?,
    val publishedAt: OffsetDateTime?,
    val collectedAt: OffsetDateTime?,
) {
    companion object {
        fun from(view: NewsView) = NewsResponse(
            id = view.id,
            title = view.title,
            summary = view.summary,
            url = view.url,
            publishedAt = view.publishedAt,
            collectedAt = view.collectedAt,
        )
    }
}

data class NewsDetailResponse(
    val id: Long,
    val title: String?,
    val summary: String?,
    val url: String?,
    val originalUrl: String?,
    val publishedAt: OffsetDateTime?,
    val collectedAt: OffsetDateTime?,
) {
    companion object {
        fun from(detail: NewsDetail) = NewsDetailResponse(
            id = detail.id,
            title = detail.title,
            summary = detail.summary,
            url = detail.url,
            originalUrl = detail.originalUrl,
            publishedAt = detail.publishedAt,
            collectedAt = detail.collectedAt,
        )
    }
}

data class NewsRelatedStockResponse(
    val companyName: String,
    val ticker: String?,
    val market: String?,
    val price: BigDecimal?,
    val change: BigDecimal?,
)
