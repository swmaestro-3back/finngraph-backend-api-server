package com.finngraph.composition.briefing

import com.finngraph.briefing.model.BriefingIssue
import com.finngraph.briefing.model.BriefingLeader
import com.finngraph.briefing.model.BriefingStock
import com.finngraph.briefing.model.BriefingStockRef
import com.finngraph.briefing.model.BriefingTheme
import com.finngraph.briefing.model.Citation
import com.finngraph.briefing.model.CitationType
import com.finngraph.briefing.model.FlagSnapshotEntry
import com.finngraph.briefing.model.GraphEdge
import com.finngraph.briefing.model.GraphNode
import com.finngraph.briefing.model.RelationGraph
import com.finngraph.briefing.model.RiskItem
import com.finngraph.briefing.model.RiskKind
import com.finngraph.briefing.model.WatchKind
import com.finngraph.news.model.CompanyRef
import com.finngraph.news.model.NewsCluster
import com.finngraph.news.model.RelationSource
import com.finngraph.stock.model.StockFlags
import com.finngraph.stock.model.StockPriceView
import com.finngraph.stock.model.SupplyContract
import com.finngraph.stock.model.Ticker
import com.finngraph.theme.model.HotSide
import com.finngraph.theme.model.ThemeSummary
import java.math.BigDecimal
import java.time.LocalDate

object BriefingRules {

    const val RADAR_UP = 3
    const val RADAR_DOWN = 2
    const val ISSUE_STOCK_LIMIT = 6
    const val CONTRACT_END_DAYS = 30L
    const val WATCH_CANDIDATE_LIMIT = 12
    const val SENTENCE_PREVIEW_CHARS = 80

    private const val AFFIRMED = "affirmed"
    private const val DENIED = "denied"
    private const val TERMINATED = "terminated"
    private const val PAST = "past_or_present_fact"
    private const val PLANNED = "future_or_planned"
    private const val MODAL = "modal_possibility"
    private const val SANCTIONS = "SANCTIONS"

    private val RELATION_LABELS = mapOf(
        "SUPPLIES_TO" to "공급",
        "ACQUIRES" to "인수",
        "INVESTS_IN" to "투자",
        "PARTNERS_WITH" to "협력",
        "DIVESTS_FROM" to "매각",
        SANCTIONS to "제재",
    )

    fun relationLabel(relation: String): String = RELATION_LABELS[relation] ?: relation

    fun themeRadar(hot: List<ThemeSummary>): List<BriefingTheme> {
        val up = hot.filter { it.hotSide == HotSide.UP }.take(RADAR_UP)
        val down = hot.filter { it.hotSide == HotSide.DOWN }.take(RADAR_DOWN)
        return (up + down).map { theme ->
            BriefingTheme(
                id = theme.id,
                name = theme.name,
                change = theme.weightedChange,
                hotSide = requireNotNull(theme.hotSide).name,
                stockCount = theme.stockCount,
                pricedCount = theme.pricedCount,
                upCount = theme.upCount,
                downCount = theme.downCount,
                flatCount = theme.flatCount,
                leaders = theme.leaders.map { BriefingLeader(it.ticker, it.name, it.change) },
            )
        }
    }

    fun issueStocks(
        refs: List<CompanyRef>,
        prices: Map<Ticker, StockPriceView>,
        limit: Int = ISSUE_STOCK_LIMIT,
    ): List<BriefingStock> =
        refs.mapNotNull { it.ticker }
            .distinct()
            .mapNotNull { prices[Ticker(it)] }
            .map { BriefingStock(it.ticker, it.name, it.market, it.change) }
            .sortedWith(compareBy<BriefingStock, BigDecimal?>(nullsLast(reverseOrder())) { it.change?.abs() })
            .take(limit)

    fun foldGraph(
        relations: List<RelationSource>,
        prices: Map<Ticker, StockPriceView>,
        cite: (RelationSource) -> Citation,
    ): RelationGraph {
        val nodes = LinkedHashMap<String, GraphNode>()
        val grouped = LinkedHashMap<String, MutableList<RelationSource>>()
        for (relation in relations) {
            val source = node(relation.subjectName, relation.subjectCode, prices)
            val target = node(relation.objectName, relation.objectCode, prices)
            nodes.putIfAbsent(source.id, source)
            nodes.putIfAbsent(target.id, target)
            grouped.getOrPut("${source.id}|${relation.relation}|${target.id}") { mutableListOf() } += relation
        }
        val edges = grouped.map { (id, rows) ->
            val (sourceId, relation, targetId) = id.split("|")
            GraphEdge(
                id = id,
                source = sourceId,
                target = targetId,
                relation = relation,
                item = rows.firstNotNullOfOrNull { it.item },
                polarity = foldPolarity(rows),
                tense = foldTense(rows),
                mentionedCount = rows.size,
                sources = rows.map(cite).distinctBy { it.key() },
            )
        }
        return RelationGraph(nodes.values.toList(), edges)
    }

    fun risks(
        flags: List<StockFlags>,
        previous: List<FlagSnapshotEntry>?,
        contracts: List<SupplyContract>,
        relations: List<RelationSource>,
        cite: (RelationSource) -> Citation,
    ): List<RiskItem> {
        val items = mutableListOf<RiskItem>()
        if (previous != null) {
            val before = previous.associateBy { it.ticker }
            for (flag in flags) {
                val prior = before[flag.ticker]
                if (flag.underAdministration && prior?.underAdministration != true) {
                    items += RiskItem(RiskKind.ADMINISTRATION_NEW, flag.ticker, flag.name, flag.market, "관리종목 지정", null)
                }
                if (flag.tradingSuspended && prior?.tradingSuspended != true) {
                    items += RiskItem(RiskKind.SUSPENDED_NEW, flag.ticker, flag.name, flag.market, "거래정지", null)
                }
                if (flag.delistingTrade && prior?.delistingTrade != true) {
                    items += RiskItem(RiskKind.DELISTING_NEW, flag.ticker, flag.name, flag.market, "정리매매 진행", null)
                }
            }
        }
        for (contract in contracts) {
            val ticker = contract.filerTicker ?: continue
            if (!contract.isCorrection) continue
            items += RiskItem(
                kind = RiskKind.CORRECTION,
                ticker = ticker,
                name = contract.filerName ?: contract.filerCorpCode,
                market = contract.filerMarket,
                detail = contract.correctionReason ?: contract.reportName,
                source = disclosureCitation(contract),
            )
        }
        for (relation in relations) {
            val kind = when {
                relation.relation == SANCTIONS -> RiskKind.SANCTION
                relation.polarity == DENIED -> RiskKind.RELATION_DENIED
                relation.polarity == TERMINATED -> RiskKind.RELATION_TERMINATED
                else -> null
            } ?: continue
            val ticker = relation.subjectCode ?: relation.objectCode ?: continue
            val name = if (relation.subjectCode != null) relation.subjectName else relation.objectName
            items += RiskItem(
                kind = kind,
                ticker = ticker,
                name = name,
                market = null,
                detail = relation.sourceSentence?.take(SENTENCE_PREVIEW_CHARS) ?: "${relation.subjectName} → ${relation.objectName}",
                source = cite(relation),
            )
        }
        return items.sortedWith(compareBy({ it.kind.ordinal }, { it.name }))
    }

    fun watchCandidates(
        baseDate: LocalDate,
        contracts: List<SupplyContract>,
        endingContracts: List<SupplyContract>,
        relations: List<RelationSource>,
        issues: List<IssueCandidate>,
        previousIssues: List<BriefingIssue>?,
        cite: (RelationSource) -> Citation,
    ): List<WatchCandidate> {
        val candidates = mutableListOf<WatchCandidate>()
        for (contract in contracts) {
            if (!contract.isCorrection) continue
            candidates += WatchCandidate(
                kind = WatchKind.CORRECTION,
                factText = "${filerName(contract)}: ${contractTitle(contract)} 정정 공시(${contract.correctionReason ?: "사유 미기재"})",
                citation = disclosureCitation(contract),
                stocks = contractStocks(contract),
            )
        }
        val endLimit = baseDate.plusDays(CONTRACT_END_DAYS)
        for (contract in endingContracts.distinctBy { it.rceptNo }) {
            val end = contract.endDate ?: continue
            if (end < baseDate || end > endLimit) continue
            candidates += WatchCandidate(
                kind = WatchKind.CONTRACT_END,
                factText = "${filerName(contract)}: ${contractTitle(contract)} 계약 종료 예정일 $end",
                citation = disclosureCitation(contract),
                stocks = contractStocks(contract),
            )
        }
        for (relation in relations) {
            if (relation.tense != PLANNED) continue
            val item = relation.item?.let { " ($it)" } ?: ""
            candidates += WatchCandidate(
                kind = WatchKind.PLANNED_RELATION,
                factText = "${relation.subjectName} → ${relation.objectName} ${relationLabel(relation.relation)} 계획$item",
                citation = cite(relation),
                stocks = relationStocks(relation),
            )
        }
        if (previousIssues != null) {
            val before = previousIssues.associateBy { it.clusterId }
            for (issue in issues) {
                val prior = before[issue.cluster.id] ?: continue
                if (issue.cluster.memberCount <= prior.newsCount) continue
                candidates += WatchCandidate(
                    kind = WatchKind.ISSUE_SPREAD,
                    factText = "'${issue.cluster.title ?: "이슈 ${issue.cluster.id}"}' 관련 기사 ${prior.newsCount}건 → ${issue.cluster.memberCount}건",
                    citation = clusterCitation(issue.cluster),
                    stocks = issue.stocks.map { BriefingStockRef(it.ticker, it.name) },
                )
            }
        }
        return candidates.take(WATCH_CANDIDATE_LIMIT)
    }

    fun snapshot(flags: List<StockFlags>): List<FlagSnapshotEntry> =
        flags.map { FlagSnapshotEntry(it.ticker, it.name, it.market, it.underAdministration, it.tradingSuspended, it.delistingTrade) }

    fun disclosureCitation(contract: SupplyContract): Citation =
        Citation(CitationType.DISCLOSURE, contract.rceptNo, contract.reportName, contract.link)

    fun clusterCitation(cluster: NewsCluster): Citation =
        Citation(CitationType.CLUSTER, cluster.id.toString(), cluster.title ?: "이슈 ${cluster.id}", null)

    fun relationCitation(relation: RelationSource, url: String?): Citation {
        val item = relation.item?.let { " ($it)" } ?: ""
        return Citation(CitationType.RELATION, relation.id.toString(), "${relation.subjectName} → ${relation.objectName}$item", url)
    }

    private fun node(name: String, code: String?, prices: Map<Ticker, StockPriceView>): GraphNode {
        val priced = code?.let { prices[Ticker(it)] }
        return if (code != null) {
            GraphNode("T:$code", priced?.name ?: name, code, priced?.market, priced?.change)
        } else {
            GraphNode("N:$name", name, null, null, null)
        }
    }

    private fun foldPolarity(rows: List<RelationSource>): String = when {
        rows.any { it.polarity == DENIED } -> DENIED
        rows.any { it.polarity == TERMINATED } -> TERMINATED
        else -> AFFIRMED
    }

    private fun foldTense(rows: List<RelationSource>): String = when {
        rows.any { it.tense == PAST || it.tense == null } -> PAST
        rows.any { it.tense == PLANNED } -> PLANNED
        else -> MODAL
    }

    private fun filerName(contract: SupplyContract): String = contract.filerName ?: contract.filerCorpCode

    private fun contractTitle(contract: SupplyContract): String = contract.contractName ?: contract.reportName

    private fun contractStocks(contract: SupplyContract): List<BriefingStockRef> = buildList {
        contract.filerTicker?.let { add(BriefingStockRef(it, filerName(contract))) }
        contract.counterpartyTicker?.let { ticker ->
            add(BriefingStockRef(ticker, contract.counterpartyCorpName ?: contract.counterparty ?: ticker))
        }
    }

    private fun relationStocks(relation: RelationSource): List<BriefingStockRef> = buildList {
        relation.subjectCode?.let { add(BriefingStockRef(it, relation.subjectName)) }
        relation.objectCode?.let { add(BriefingStockRef(it, relation.objectName)) }
    }
}
