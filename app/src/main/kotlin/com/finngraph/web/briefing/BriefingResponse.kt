package com.finngraph.web.briefing

import com.finngraph.briefing.model.AnalyzedNews
import com.finngraph.briefing.model.BriefingArticle
import com.finngraph.briefing.model.BriefingHeadline
import com.finngraph.briefing.model.BriefingIssue
import com.finngraph.briefing.model.BriefingLeader
import com.finngraph.briefing.model.BriefingSentence
import com.finngraph.briefing.model.BriefingStock
import com.finngraph.briefing.model.BriefingStockRef
import com.finngraph.briefing.model.BriefingSummary
import com.finngraph.briefing.model.BriefingTheme
import com.finngraph.briefing.model.Citation
import com.finngraph.briefing.model.DailyBriefing
import com.finngraph.briefing.model.GraphEdge
import com.finngraph.briefing.model.GraphNode
import com.finngraph.briefing.model.MarketSnapshot
import com.finngraph.briefing.model.RelationGraph
import com.finngraph.briefing.model.RelationLine
import com.finngraph.briefing.model.RelationParty
import com.finngraph.briefing.model.RiskItem
import com.finngraph.briefing.model.WatchPoint
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

data class CitationResponse(val type: String, val id: String, val label: String, val url: String?) {
    companion object {
        fun from(c: Citation) = CitationResponse(c.type.name, c.id, c.label, c.url)
    }
}

data class SentenceResponse(val text: String, val citations: List<CitationResponse>) {
    companion object {
        fun from(s: BriefingSentence) = SentenceResponse(s.text, s.citations.map(CitationResponse::from))
    }
}

data class HeadlineResponse(val text: String, val citations: List<CitationResponse>) {
    companion object {
        fun from(h: BriefingHeadline) = HeadlineResponse(h.text, h.citations.map(CitationResponse::from))
    }
}

data class CommentaryResponse(val sentences: List<SentenceResponse>)

data class StockResponse(val ticker: String, val name: String, val market: String?, val change: BigDecimal?) {
    companion object {
        fun from(s: BriefingStock) = StockResponse(s.ticker, s.name, s.market, s.change)
    }
}

data class StockRefResponse(val ticker: String, val name: String) {
    companion object {
        fun from(s: BriefingStockRef) = StockRefResponse(s.ticker, s.name)
    }
}

data class ArticleResponse(val newsId: Long, val title: String, val url: String?, val publishedAt: OffsetDateTime?) {
    companion object {
        fun from(a: BriefingArticle) = ArticleResponse(a.newsId, a.title, a.url, a.publishedAt)
    }
}

data class IssueResponse(
    val clusterId: Long,
    val title: String,
    val keywords: List<String>,
    val newsCount: Int,
    val firstPublishedAt: OffsetDateTime,
    val lastPublishedAt: OffsetDateTime,
    val stocks: List<StockResponse>,
    val articles: List<ArticleResponse>,
    val commentary: CommentaryResponse?,
) {
    companion object {
        fun from(i: BriefingIssue, member: Boolean) = IssueResponse(
            clusterId = i.clusterId,
            title = i.title,
            keywords = i.keywords,
            newsCount = i.newsCount,
            firstPublishedAt = i.firstPublishedAt,
            lastPublishedAt = i.lastPublishedAt,
            stocks = i.stocks.map(StockResponse::from),
            articles = i.articles.map(ArticleResponse::from),
            commentary = i.commentary?.takeIf { member }?.let { CommentaryResponse(it.map(SentenceResponse::from)) },
        )
    }
}

data class LeaderResponse(val ticker: String, val name: String, val change: BigDecimal) {
    companion object {
        fun from(l: BriefingLeader) = LeaderResponse(l.ticker, l.name, l.change)
    }
}

data class ThemeRadarResponse(
    val id: Long,
    val name: String,
    @Schema(description = "테마 지수 기준일 등락률(%). 테마 응답의 weightedChange와 같은 지표이고 change(10% 절사평균)가 아니다. 전환 전에 저장된 브리핑은 절사평균 값")
    val change: BigDecimal?,
    val hotSide: String,
    val stockCount: Int,
    val pricedCount: Int,
    val upCount: Int,
    val downCount: Int,
    val flatCount: Int,
    val leaders: List<LeaderResponse>,
) {
    companion object {
        fun from(t: BriefingTheme) = ThemeRadarResponse(
            t.id, t.name, t.change, t.hotSide, t.stockCount, t.pricedCount, t.upCount, t.downCount, t.flatCount,
            t.leaders.map(LeaderResponse::from),
        )
    }
}

data class WatchPointResponse(val kind: String, val text: String, val citations: List<CitationResponse>, val stocks: List<StockRefResponse>) {
    companion object {
        fun from(w: WatchPoint) = WatchPointResponse(w.kind.name, w.text, w.citations.map(CitationResponse::from), w.stocks.map(StockRefResponse::from))
    }
}

data class RiskResponse(val kind: String, val ticker: String, val name: String, val market: String?, val detail: String, val source: CitationResponse?) {
    companion object {
        fun from(r: RiskItem) = RiskResponse(r.kind.name, r.ticker, r.name, r.market, r.detail, r.source?.let(CitationResponse::from))
    }
}

data class RelationPartyResponse(val name: String, val ticker: String?) {
    companion object {
        fun from(p: RelationParty) = RelationPartyResponse(p.name, p.ticker)
    }
}

data class RelationLineResponse(
    val id: Long,
    val subject: RelationPartyResponse,
    val relation: String,
    val `object`: RelationPartyResponse,
    val item: String?,
    val polarity: String,
    val tense: String,
    val subjectImpact: String?,
    val objectImpact: String?,
    val sourceSentence: String?,
    val source: CitationResponse,
) {
    companion object {
        fun from(l: RelationLine) = RelationLineResponse(
            id = l.id,
            subject = RelationPartyResponse.from(l.subject),
            relation = l.relation,
            `object` = RelationPartyResponse.from(l.target),
            item = l.item,
            polarity = l.polarity,
            tense = l.tense,
            subjectImpact = l.subjectImpact,
            objectImpact = l.objectImpact,
            sourceSentence = l.sourceSentence,
            source = CitationResponse.from(l.source),
        )
    }
}

data class AnalyzedNewsResponse(
    val newsId: Long,
    val title: String,
    val url: String?,
    val publishedAt: OffsetDateTime?,
    val summary: String?,
    val companies: List<StockResponse>,
    val relationCount: Int,
    val relations: List<RelationLineResponse>?,
) {
    companion object {
        fun from(n: AnalyzedNews, member: Boolean) = AnalyzedNewsResponse(
            newsId = n.newsId,
            title = n.title,
            url = n.url,
            publishedAt = n.publishedAt,
            summary = n.summary,
            companies = n.companies.map(StockResponse::from),
            relationCount = n.relations.size,
            relations = if (member) n.relations.map(RelationLineResponse::from) else null,
        )
    }
}

data class GraphNodeResponse(val id: String, val name: String, val ticker: String?, val market: String?, val change: BigDecimal?) {
    companion object {
        fun from(n: GraphNode) = GraphNodeResponse(n.id, n.name, n.ticker, n.market, n.change)
    }
}

data class GraphEdgeResponse(
    val id: String,
    val source: String,
    val target: String,
    val relation: String,
    val item: String?,
    val polarity: String,
    val tense: String,
    val mentionedCount: Int,
    val sources: List<CitationResponse>,
) {
    companion object {
        fun from(e: GraphEdge) = GraphEdgeResponse(
            e.id, e.source, e.target, e.relation, e.item, e.polarity, e.tense, e.mentionedCount, e.sources.map(CitationResponse::from),
        )
    }
}

data class RelationGraphResponse(val nodes: List<GraphNodeResponse>, val edges: List<GraphEdgeResponse>) {
    companion object {
        fun from(g: RelationGraph) = RelationGraphResponse(g.nodes.map(GraphNodeResponse::from), g.edges.map(GraphEdgeResponse::from))
    }
}

data class MarketResponse(
    val baseDate: LocalDate?,
    val pricedCount: Int,
    val upCount: Int,
    val downCount: Int,
    val flatCount: Int,
    val medianChange: BigDecimal?,
    val upRatio: BigDecimal?,
    val downRatio: BigDecimal?,
    val coverage: BigDecimal?,
) {
    companion object {
        fun from(m: MarketSnapshot) = MarketResponse(
            m.baseDate, m.pricedCount, m.upCount, m.downCount, m.flatCount, m.medianChange, m.upRatio, m.downRatio, m.coverage,
        )
    }
}

data class LockedResponse(
    val commentaries: Int,
    val watchPoints: Int,
    val risks: Int,
    val relations: Int,
    val graphEdges: Int,
)

data class BriefingResponse(
    val baseDate: LocalDate,
    val previousTradingDate: LocalDate?,
    val generatedAt: OffsetDateTime,
    val status: String,
    val market: MarketResponse,
    val headline: HeadlineResponse?,
    val issues: List<IssueResponse>,
    val themes: List<ThemeRadarResponse>,
    val watchPoints: List<WatchPointResponse>?,
    val risks: List<RiskResponse>?,
    val analyzedNews: List<AnalyzedNewsResponse>,
    val relationGraph: RelationGraphResponse?,
    val locked: LockedResponse?,
) {
    companion object {
        fun from(b: DailyBriefing, member: Boolean) = BriefingResponse(
            baseDate = b.baseDate,
            previousTradingDate = b.previousTradingDate,
            generatedAt = b.generatedAt,
            status = b.status.name,
            market = MarketResponse.from(b.market),
            headline = b.headline?.let(HeadlineResponse::from),
            issues = b.issues.map { IssueResponse.from(it, member) },
            themes = b.themes.map(ThemeRadarResponse::from),
            watchPoints = if (member) b.watchPoints.map(WatchPointResponse::from) else null,
            risks = if (member) b.risks.map(RiskResponse::from) else null,
            analyzedNews = b.analyzedNews.map { AnalyzedNewsResponse.from(it, member) },
            relationGraph = if (member) RelationGraphResponse.from(b.relationGraph) else null,
            locked = if (member) null else LockedResponse(
                commentaries = b.issues.count { it.commentary != null },
                watchPoints = b.watchPoints.size,
                risks = b.risks.size,
                relations = b.analyzedNews.sumOf { it.relations.size },
                graphEdges = b.relationGraph.edges.size,
            ),
        )
    }
}

data class BriefingSummaryResponse(
    val baseDate: LocalDate,
    val status: String,
    val generatedAt: OffsetDateTime,
    val headline: String?,
) {
    companion object {
        fun from(s: BriefingSummary) = BriefingSummaryResponse(s.baseDate, s.status.name, s.generatedAt, s.headline)
    }
}
