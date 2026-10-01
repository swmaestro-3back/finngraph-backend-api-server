package com.finngraph.news.model

import java.time.OffsetDateTime

data class IssueTimeline(
    val clusterId: Long,
    val nodes: List<IssueTimelineNode>,
) {
    companion object {
        const val MAX_LIMIT: Int = 20
    }
}

data class IssueTimelineNode(
    val clusterId: Long,
    val title: String,
    val summary: String?,
    val firstPublishedAt: OffsetDateTime,
    val lastPublishedAt: OffsetDateTime,
    val current: Boolean,
)
