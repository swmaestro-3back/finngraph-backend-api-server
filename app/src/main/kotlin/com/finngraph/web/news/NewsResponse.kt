package com.finngraph.web.news

import com.finngraph.composition.RelatedStock
import com.finngraph.news.model.IssueTimeline
import com.finngraph.news.model.IssueTimelineNode
import com.finngraph.news.model.NewsDetail
import com.finngraph.news.model.NewsView
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class NewsResponse(
    val id: Long,
    val title: String?,
    val summary: String?,
    val url: String?,
    val publishedAt: OffsetDateTime?,
    val collectedAt: OffsetDateTime?,
    @Schema(description = "관계 추출 결과 true: 삼중항 있음, false: 삼중항 없음")
    val tripleExtracted: Boolean,
) {
    companion object {
        fun from(view: NewsView) = NewsResponse(
            id = view.id,
            title = view.title,
            summary = view.summary,
            url = view.url,
            publishedAt = view.publishedAt,
            collectedAt = view.collectedAt,
            tripleExtracted = requireNotNull(view.tripleExtracted),
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
) {
    companion object {
        fun from(related: RelatedStock) = NewsRelatedStockResponse(
            companyName = related.companyName,
            ticker = related.ticker,
            market = related.market,
            price = related.price,
            change = related.change,
        )
    }
}

data class IssueTimelineResponse(
    val clusterId: Long,
    @Schema(description = "요청 이슈부터 부모를 따라 과거로")
    val nodes: List<IssueTimelineNodeResponse>,
) {
    companion object {
        fun from(timeline: IssueTimeline) = IssueTimelineResponse(
            clusterId = timeline.clusterId,
            nodes = timeline.nodes.map(IssueTimelineNodeResponse::from),
        )
    }
}

data class IssueTimelineNodeResponse(
    val clusterId: Long,
    val title: String,
    @Schema(description = "1~2문장 요약. 생성 전이거나 실패했으면 null")
    val summary: String?,
    @Schema(description = "firstPublishedAt 의 KST 날짜", example = "2026-09-29")
    val date: LocalDate,
    val firstPublishedAt: OffsetDateTime,
    val lastPublishedAt: OffsetDateTime,
    @Schema(description = "요청한 이슈 여부")
    val current: Boolean,
) {
    companion object {
        private val KST: ZoneOffset = ZoneOffset.ofHours(9)

        fun from(node: IssueTimelineNode) = IssueTimelineNodeResponse(
            clusterId = node.clusterId,
            title = node.title,
            summary = node.summary,
            date = node.firstPublishedAt.withOffsetSameInstant(KST).toLocalDate(),
            firstPublishedAt = node.firstPublishedAt,
            lastPublishedAt = node.lastPublishedAt,
            current = node.current,
        )
    }
}
