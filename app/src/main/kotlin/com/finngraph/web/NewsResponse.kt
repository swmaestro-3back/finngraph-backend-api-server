package com.finngraph.web

import com.finngraph.news.CompanyRef
import com.finngraph.news.NewsDetail
import com.finngraph.news.NewsView
import java.time.OffsetDateTime

data class NewsResponse(
    val id: Long,
    val title: String?,
    val summary: String?,
    val link: String?,
    val publishedAt: OffsetDateTime?,
    val sourceType: String?,
) {
    companion object {
        fun from(view: NewsView) = NewsResponse(
            id = view.id,
            title = view.title,
            summary = view.summary,
            link = view.link,
            publishedAt = view.publishedAt,
            sourceType = view.sourceType,
        )
    }
}

data class NewsDetailResponse(
    val id: Long,
    val title: String?,
    val summary: String?,
    val link: String?,
    val originallink: String?,
    val publishedAt: OffsetDateTime?,
    val sourceType: String?,
) {
    companion object {
        fun from(detail: NewsDetail) = NewsDetailResponse(
            id = detail.id,
            title = detail.title,
            summary = detail.summary,
            link = detail.link,
            originallink = detail.originallink,
            publishedAt = detail.publishedAt,
            sourceType = detail.sourceType,
        )
    }
}

data class CompanyResponse(
    val companyName: String,
    val ticker: String?,
) {
    companion object {
        fun from(ref: CompanyRef) = CompanyResponse(ref.companyName, ref.ticker)
    }
}
