package com.finngraph.briefing.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

enum class BriefingStatus { READY, PARTIAL }

enum class CitationType { NEWS, DISCLOSURE, RELATION, CLUSTER }

data class Citation(
    val type: CitationType,
    val id: String,
    val label: String,
    val url: String?,
) {
    fun key(): String = "${type.name}:$id"
}

data class BriefingSentence(
    val text: String,
    val citations: List<Citation>,
)

data class BriefingHeadline(
    val text: String,
    val citations: List<Citation>,
)

data class BriefingStock(
    val ticker: String,
    val name: String,
    val market: String?,
    val change: BigDecimal?,
)

data class BriefingStockRef(
    val ticker: String,
    val name: String,
)

data class BriefingArticle(
    val newsId: Long,
    val title: String,
    val url: String?,
    val publishedAt: OffsetDateTime?,
)

data class BriefingIssue(
    val clusterId: Long,
    val title: String,
    val keywords: List<String>,
    val newsCount: Int,
    val firstPublishedAt: OffsetDateTime,
    val lastPublishedAt: OffsetDateTime,
    val stocks: List<BriefingStock>,
    val articles: List<BriefingArticle>,
    val commentary: List<BriefingSentence>?,
)

data class BriefingLeader(
    val ticker: String,
    val name: String,
    val change: BigDecimal,
)

data class BriefingTheme(
    val id: Long,
    val name: String,
    val change: BigDecimal?,
    val hotSide: String,
    val stockCount: Int,
    val pricedCount: Int,
    val upCount: Int,
    val downCount: Int,
    val flatCount: Int,
    val leaders: List<BriefingLeader>,
)

enum class WatchKind { CORRECTION, CONTRACT_END, PLANNED_RELATION, ISSUE_SPREAD }

data class WatchPoint(
    val kind: WatchKind,
    val text: String,
    val citations: List<Citation>,
    val stocks: List<BriefingStockRef>,
)

enum class RiskKind {
    ADMINISTRATION_NEW,
    SUSPENDED_NEW,
    DELISTING_NEW,
    CORRECTION,
    RELATION_DENIED,
    RELATION_TERMINATED,
    SANCTION,
}

data class RiskItem(
    val kind: RiskKind,
    val ticker: String,
    val name: String,
    val market: String?,
    val detail: String,
    val source: Citation?,
)

data class RelationParty(
    val name: String,
    val ticker: String?,
)

data class RelationLine(
    val id: Long,
    val subject: RelationParty,
    val relation: String,
    val target: RelationParty,
    val item: String?,
    val polarity: String,
    val tense: String,
    val subjectImpact: String?,
    val objectImpact: String?,
    val sourceSentence: String?,
    val source: Citation,
)

data class AnalyzedNews(
    val newsId: Long,
    val title: String,
    val url: String?,
    val publishedAt: OffsetDateTime?,
    val summary: String?,
    val companies: List<BriefingStock>,
    val relations: List<RelationLine>,
)

data class GraphNode(
    val id: String,
    val name: String,
    val ticker: String?,
    val market: String?,
    val change: BigDecimal?,
)

data class GraphEdge(
    val id: String,
    val source: String,
    val target: String,
    val relation: String,
    val item: String?,
    val polarity: String,
    val tense: String,
    val mentionedCount: Int,
    val sources: List<Citation>,
)

data class RelationGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
) {
    companion object {
        val EMPTY = RelationGraph(emptyList(), emptyList())
    }
}

data class FlagSnapshotEntry(
    val ticker: String,
    val name: String,
    val market: String?,
    val underAdministration: Boolean,
    val tradingSuspended: Boolean,
    val delistingTrade: Boolean,
)

data class MarketSnapshot(
    val baseDate: LocalDate?,
    val pricedCount: Int,
    val upCount: Int,
    val downCount: Int,
    val flatCount: Int,
    val medianChange: BigDecimal?,
    val upRatio: BigDecimal?,
    val downRatio: BigDecimal?,
    val coverage: BigDecimal?,
)

data class DailyBriefing(
    val baseDate: LocalDate,
    val previousTradingDate: LocalDate?,
    val status: BriefingStatus,
    val generatedAt: OffsetDateTime,
    val market: MarketSnapshot,
    val headline: BriefingHeadline?,
    val issues: List<BriefingIssue>,
    val themes: List<BriefingTheme>,
    val watchPoints: List<WatchPoint>,
    val risks: List<RiskItem>,
    val analyzedNews: List<AnalyzedNews>,
    val relationGraph: RelationGraph,
    val flaggedSnapshot: List<FlagSnapshotEntry>,
)

data class BriefingSummary(
    val baseDate: LocalDate,
    val status: BriefingStatus,
    val generatedAt: OffsetDateTime,
    val headline: String?,
)
