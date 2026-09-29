package com.finngraph.composition.briefing

import com.finngraph.briefing.model.AnalyzedNews
import com.finngraph.briefing.model.BriefingTheme
import com.finngraph.briefing.model.Citation
import com.finngraph.briefing.model.CitationType
import com.finngraph.briefing.model.FlagSnapshotEntry
import com.finngraph.briefing.model.MarketSnapshot
import com.finngraph.briefing.model.RelationGraph
import com.finngraph.briefing.model.RelationLine
import com.finngraph.briefing.model.RelationParty
import com.finngraph.briefing.model.RiskItem
import com.finngraph.briefing.port.BriefingStorePort
import com.finngraph.composition.hottheme.HotThemeComposer
import com.finngraph.news.model.ClusterArticle
import com.finngraph.news.model.CompanyRef
import com.finngraph.news.model.NewsId
import com.finngraph.news.model.RelationSource
import com.finngraph.news.port.NewsClusterPort
import com.finngraph.news.port.NewsCompanyPort
import com.finngraph.news.port.NewsQueryPort
import com.finngraph.news.port.RelationSourcePort
import com.finngraph.stock.model.StockPriceView
import com.finngraph.stock.model.SupplyContract
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockContractPort
import com.finngraph.stock.port.StockQueryPort
import com.finngraph.theme.model.MarketStats
import com.finngraph.theme.port.ThemeQueryPort
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class BriefingInput(
    val baseDate: LocalDate,
    val previousTradingDate: LocalDate?,
    val market: MarketSnapshot,
    val themes: List<BriefingTheme>,
    val issues: List<IssueCandidate>,
    val analyzedNews: List<AnalyzedNews>,
    val relationGraph: RelationGraph,
    val risks: List<RiskItem>,
    val watchCandidates: List<WatchCandidate>,
    val flagged: List<FlagSnapshotEntry>,
    val citations: Map<String, Citation>,
)

sealed interface BriefingAssembly {
    data object NoBaseDate : BriefingAssembly
    data class DateMismatch(val requested: LocalDate, val actual: LocalDate) : BriefingAssembly
    data class LowCoverage(val baseDate: LocalDate, val coverage: BigDecimal?) : BriefingAssembly
    data class Ready(val input: BriefingInput) : BriefingAssembly
}

@Component
class BriefingComposer(
    private val themeQuery: ThemeQueryPort,
    private val hotThemes: HotThemeComposer,
    private val clusters: NewsClusterPort,
    private val relationSources: RelationSourcePort,
    private val newsQuery: NewsQueryPort,
    private val newsCompany: NewsCompanyPort,
    private val stockQuery: StockQueryPort,
    private val stockContract: StockContractPort,
    private val store: BriefingStorePort,
) {

    fun assemble(requestedDate: LocalDate?): BriefingAssembly {
        val basis = themeQuery.pricingBasis()
        val baseDate = basis.baseDate ?: return BriefingAssembly.NoBaseDate
        if (requestedDate != null && requestedDate != baseDate) return BriefingAssembly.DateMismatch(requestedDate, baseDate)

        val market = themeQuery.marketStats().toSnapshot()
        val coverage = market.coverage
        if (coverage == null || coverage < MIN_COVERAGE) return BriefingAssembly.LowCoverage(baseDate, coverage)

        val previous = basis.prevTradingDate
        val windowStart = previous ?: baseDate.minusDays(1)
        val since = OffsetDateTime.of(windowStart.atTime(WINDOW_HOUR, 0), KST)

        val clusterList = clusters.findRecent(since, MIN_CLUSTER_MEMBERS, ISSUE_LIMIT)
        val articles = clusters.findArticles(clusterList.map { it.id }, ARTICLES_PER_ISSUE)
        val relations = relationSources.findMentionedBetween(windowStart, baseDate)
        val contracts = stockContract.findReceivedSince(windowStart.plusDays(1)).filter { it.rceptDate <= baseDate }
        val endingContracts = stockContract.findEndingBetween(baseDate, baseDate.plusDays(BriefingRules.CONTRACT_END_DAYS))

        val articleNewsIds = articles.values.flatten().map { it.newsId }
        val relationNewsIds = relations.mapNotNull { it.newsId }
        val newsIds = (articleNewsIds + relationNewsIds).distinct()
        val newsDetails = newsQuery.findByIds(newsIds.map(::NewsId))
        val companyRefs = newsCompany.findByNewsIds(newsIds.map(::NewsId))

        val tickers = buildSet {
            companyRefs.values.flatten().mapNotNullTo(this) { it.ticker }
            relations.forEach { relation ->
                relation.subjectCode?.let(::add)
                relation.objectCode?.let(::add)
            }
            contracts.forEach { contract ->
                contract.filerTicker?.let(::add)
                contract.counterpartyTicker?.let(::add)
            }
        }
        val prices = stockQuery.findByTickers(tickers.map(::Ticker))

        val citations = LinkedHashMap<String, Citation>()
        fun register(citation: Citation): Citation = citations.getOrPut(citation.key()) { citation }

        articles.values.flatten().forEach { register(newsCitation(it.newsId, it.title, it.url)) }
        newsDetails.values.forEach { register(newsCitation(it.id, it.title, it.originalUrl ?: it.url)) }
        contracts.forEach { register(BriefingRules.disclosureCitation(it)) }
        clusterList.forEach { register(BriefingRules.clusterCitation(it)) }

        val contractsByRcept = contracts.associateBy { it.rceptNo }
        val sourceOf: (RelationSource) -> Citation = { relation ->
            val key = relation.newsId?.let { "${CitationType.NEWS.name}:$it" }
                ?: relation.rceptNo?.let { "${CitationType.DISCLOSURE.name}:$it" }
            key?.let { citations[it] }
                ?: relation.rceptNo?.let { rcept ->
                    register(Citation(CitationType.DISCLOSURE, rcept, "공시 $rcept", contractsByRcept[rcept]?.link))
                }
                ?: register(Citation(CitationType.NEWS, relation.newsId.toString(), "기사 ${relation.newsId}", null))
        }
        val relationCite: (RelationSource) -> Citation = { relation ->
            register(BriefingRules.relationCitation(relation, sourceOf(relation).url))
        }
        relations.forEach { relationCite(it) }

        val issues = clusterList.map { cluster ->
            val clusterArticles = articles[cluster.id].orEmpty()
            val refs = clusterArticles.flatMap { companyRefs[NewsId(it.newsId)].orEmpty() }
            val stocks = BriefingRules.issueStocks(refs, prices)
            val stockTickers = stocks.map { it.ticker }.toSet()
            IssueCandidate(
                cluster = cluster,
                articles = clusterArticles,
                stocks = stocks,
                relations = relations.filter { it.subjectCode in stockTickers || it.objectCode in stockTickers },
                contracts = contracts.filter { it.filerTicker in stockTickers || it.counterpartyTicker in stockTickers },
            )
        }

        val analyzedNews = relations
            .filter { it.newsId != null }
            .groupBy { requireNotNull(it.newsId) }
            .mapNotNull { (newsId, rows) ->
                val detail = newsDetails[NewsId(newsId)] ?: return@mapNotNull null
                val refs = companyRefs[NewsId(newsId)].orEmpty()
                AnalyzedNews(
                    newsId = newsId,
                    title = detail.title ?: "(제목 없음)",
                    url = detail.originalUrl ?: detail.url,
                    publishedAt = detail.publishedAt,
                    summary = detail.summary,
                    companies = BriefingRules.issueStocks(refs, prices),
                    relations = rows.map { it.toLine(prices, sourceOf(it)) },
                )
            }
            .sortedWith(compareByDescending<AnalyzedNews> { it.relations.size }.thenByDescending { it.publishedAt })
            .take(ANALYZED_NEWS_LIMIT)

        val previousBriefing = store.findPreviousBefore(baseDate)
        val flags = stockQuery.findFlagged()

        return BriefingAssembly.Ready(
            BriefingInput(
                baseDate = baseDate,
                previousTradingDate = previous,
                market = market,
                themes = BriefingRules.themeRadar(hotThemes.hot(HOT_THEME_COUNT)),
                issues = issues,
                analyzedNews = analyzedNews,
                relationGraph = BriefingRules.foldGraph(relations, prices, sourceOf),
                risks = BriefingRules.risks(flags, previousBriefing?.flaggedSnapshot, contracts, relations, sourceOf),
                watchCandidates = BriefingRules.watchCandidates(baseDate, contracts, endingContracts, relations, issues, previousBriefing?.issues, sourceOf),
                flagged = BriefingRules.snapshot(flags),
                citations = citations,
            ),
        )
    }

    private fun newsCitation(id: Long, title: String?, url: String?): Citation =
        Citation(CitationType.NEWS, id.toString(), title ?: "기사 $id", url)

    private fun RelationSource.toLine(prices: Map<Ticker, StockPriceView>, source: Citation): RelationLine = RelationLine(
        id = id,
        subject = RelationParty(subjectName, subjectCode?.takeIf { prices.containsKey(Ticker(it)) }),
        relation = relation,
        target = RelationParty(objectName, objectCode?.takeIf { prices.containsKey(Ticker(it)) }),
        item = item,
        polarity = polarity ?: "affirmed",
        tense = tense ?: "past_or_present_fact",
        subjectImpact = subjectImpact,
        objectImpact = objectImpact,
        sourceSentence = sourceSentence,
        source = source,
    )

    private fun MarketStats.toSnapshot() = MarketSnapshot(
        baseDate = baseDate,
        pricedCount = pricedCount,
        upCount = upCount,
        downCount = downCount,
        flatCount = flatCount,
        medianChange = medianChange,
        upRatio = upRatio,
        downRatio = downRatio,
        coverage = coverage,
    )

    companion object {
        const val MIN_CLUSTER_MEMBERS = 3
        const val ISSUE_LIMIT = 5
        const val ARTICLES_PER_ISSUE = 5
        const val ANALYZED_NEWS_LIMIT = 8
        const val HOT_THEME_COUNT = 10
        const val WINDOW_HOUR = 18
        val MIN_COVERAGE: BigDecimal = BigDecimal("0.8")
        val KST: ZoneOffset = ZoneOffset.ofHours(9)
    }
}
