package com.finngraph.composition.briefing

import com.finngraph.briefing.model.BriefingIssue
import com.finngraph.briefing.model.Citation
import com.finngraph.briefing.model.CitationType
import com.finngraph.briefing.model.FlagSnapshotEntry
import com.finngraph.briefing.model.RiskKind
import com.finngraph.briefing.model.WatchKind
import com.finngraph.news.model.ClusterArticle
import com.finngraph.news.model.CompanyRef
import com.finngraph.news.model.NewsCluster
import com.finngraph.news.model.RelationSource
import com.finngraph.stock.model.StockFlags
import com.finngraph.stock.model.StockPriceView
import com.finngraph.stock.model.SupplyContract
import com.finngraph.stock.model.Ticker
import com.finngraph.theme.model.HotSide
import com.finngraph.theme.model.ThemeLeader
import com.finngraph.theme.model.ThemeSummary
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BriefingRulesTest {

    private val baseDate: LocalDate = LocalDate.parse("2026-08-20")
    private val at: OffsetDateTime = OffsetDateTime.parse("2026-08-20T10:00:00+09:00")

    @Test
    fun `테마 레이더는 상승 3개 하락 2개를 선정 순서대로 뽑는다`() {
        val hot = listOf(
            theme(1, "상승1", HotSide.UP), theme(2, "상승2", HotSide.UP), theme(3, "상승3", HotSide.UP), theme(4, "상승4", HotSide.UP),
            theme(5, "하락1", HotSide.DOWN), theme(6, "하락2", HotSide.DOWN), theme(7, "하락3", HotSide.DOWN),
        )

        val radar = BriefingRules.themeRadar(hot)

        assertEquals(listOf("상승1", "상승2", "상승3", "하락1", "하락2"), radar.map { it.name })
        assertEquals(listOf("UP", "UP", "UP", "DOWN", "DOWN"), radar.map { it.hotSide })
        assertEquals("000001", radar[0].leaders.single().ticker)
    }

    @Test
    fun `이슈 종목은 상장 종목만 등락 절대값 순으로 최대 6개`() {
        val refs = listOf(
            CompanyRef("작은", "000001"), CompanyRef("비상장", null), CompanyRef("큰하락", "000002"),
            CompanyRef("없음", "000003"), CompanyRef("작은", "000001"), CompanyRef("무등락", "000004"),
        )
        val prices = mapOf(
            Ticker("000001") to price("000001", "작은", "1.0"),
            Ticker("000002") to price("000002", "큰하락", "-5.0"),
            Ticker("000004") to price("000004", "무등락", null),
        )

        val stocks = BriefingRules.issueStocks(refs, prices)

        assertEquals(listOf("000002", "000001", "000004"), stocks.map { it.ticker })
        assertNull(stocks[2].change)
    }

    @Test
    fun `같은 삼중항은 하나의 간선으로 접고 근거를 모은다`() {
        val a = relation(1, "A사", "000001", "SUPPLIES_TO", "B사", "000002", item = "배터리", newsId = 10)
        val b = relation(2, "A사", "000001", "SUPPLIES_TO", "B사", "000002", item = null, rceptNo = "R1", polarity = "terminated")
        val c = relation(3, "A사", "000001", "INVESTS_IN", "C사", null, tense = "future_or_planned", newsId = 13)
        val prices = mapOf(Ticker("000001") to price("000001", "A사", "2.0"))

        val graph = BriefingRules.foldGraph(listOf(a, b, c), prices) { cite(it) }

        assertEquals(listOf("T:000001", "T:000002", "N:C사"), graph.nodes.map { it.id })
        assertEquals(BigDecimal("2.0"), graph.nodes[0].change)
        assertEquals(2, graph.edges.size)
        val supply = graph.edges.single { it.relation == "SUPPLIES_TO" }
        assertEquals("T:000001|SUPPLIES_TO|T:000002", supply.id)
        assertEquals(2, supply.mentionedCount)
        assertEquals("배터리", supply.item)
        assertEquals("terminated", supply.polarity)
        assertEquals("past_or_present_fact", supply.tense)
        assertEquals(listOf("NEWS:10", "DISCLOSURE:R1"), supply.sources.map { it.key() })
        val invest = graph.edges.single { it.relation == "INVESTS_IN" }
        assertEquals("future_or_planned", invest.tense)
        assertEquals("affirmed", invest.polarity)
    }

    @Test
    fun `리스크는 새로 켜진 플래그와 정정·부인·종료를 kind 순으로 모은다`() {
        val flags = listOf(
            StockFlags("000001", "관리신규", "KOSDAQ", underAdministration = true, tradingSuspended = false, delistingTrade = false),
            StockFlags("000002", "정지지속", "KOSDAQ", underAdministration = false, tradingSuspended = true, delistingTrade = false),
            StockFlags("000003", "정리신규", "KOSPI", underAdministration = false, tradingSuspended = false, delistingTrade = true),
        )
        val previous = listOf(FlagSnapshotEntry("000002", "정지지속", "KOSDAQ", false, true, false))
        val correction = contract("R2", "000009", "정정사", isCorrection = true, reason = "금액 변경")
        val denied = relation(5, "D사", "000005", "ACQUIRES", "E사", null, polarity = "denied", newsId = 11)

        val risks = BriefingRules.risks(flags, previous, listOf(correction), listOf(denied)) { cite(it) }

        assertEquals(
            listOf(RiskKind.ADMINISTRATION_NEW, RiskKind.DELISTING_NEW, RiskKind.CORRECTION, RiskKind.RELATION_DENIED),
            risks.map { it.kind },
        )
        assertEquals("금액 변경", risks[2].detail)
        assertEquals("DISCLOSURE:R2", risks[2].source?.key())
        assertEquals("000005", risks[3].ticker)
    }

    @Test
    fun `첫 생성이면 플래그 리스크는 만들지 않는다`() {
        val flags = listOf(StockFlags("000001", "관리", "KOSDAQ", true, false, false))

        assertEquals(emptyList(), BriefingRules.risks(flags, null, emptyList(), emptyList()) { cite(it) })
    }

    @Test
    fun `지켜볼 점 후보는 정정·종료 임박·계획 관계·이슈 확산 순이다`() {
        val correction = contract("R2", "000009", "정정사", isCorrection = true, reason = "금액 변경")
        val ending = contract("R3", "000010", "종료사", endDate = baseDate.plusDays(10))
        val far = contract("R4", "000011", "먼사", endDate = baseDate.plusDays(60))
        val planned = relation(7, "P사", "000012", "SUPPLIES_TO", "Q사", "000013", item = "장비", tense = "future_or_planned", newsId = 12)
        val issue = IssueCandidate(
            cluster = NewsCluster(501, "이슈", listOf("k"), 5, at, at),
            articles = listOf(ClusterArticle(12, 501, "기사", "https://n/12", at, "요약")),
            stocks = emptyList(),
            relations = emptyList(),
            contracts = emptyList(),
        )
        val previousIssues = listOf(
            BriefingIssue(501, "이슈", emptyList(), 3, at, at, emptyList(), emptyList(), null),
        )

        val candidates = BriefingRules.watchCandidates(
            baseDate, listOf(correction), listOf(ending, far, correction), listOf(planned), listOf(issue), previousIssues,
        ) { cite(it) }

        assertEquals(
            listOf(WatchKind.CORRECTION, WatchKind.CONTRACT_END, WatchKind.PLANNED_RELATION, WatchKind.ISSUE_SPREAD),
            candidates.map { it.kind },
        )
        assertEquals("DISCLOSURE:R3", candidates[1].citation.key())
        assertEquals(listOf("000012", "000013"), candidates[2].stocks.map { it.ticker })
        assertEquals("CLUSTER:501", candidates[3].citation.key())
    }

    @Test
    fun `이슈 확산은 직전 브리핑보다 기사가 늘었을 때만이다`() {
        val issue = IssueCandidate(NewsCluster(501, "이슈", emptyList(), 3, at, at), emptyList(), emptyList(), emptyList(), emptyList())
        val same = listOf(BriefingIssue(501, "이슈", emptyList(), 3, at, at, emptyList(), emptyList(), null))

        assertEquals(emptyList(), BriefingRules.watchCandidates(baseDate, emptyList(), emptyList(), emptyList(), listOf(issue), same) { cite(it) })
        assertEquals(emptyList(), BriefingRules.watchCandidates(baseDate, emptyList(), emptyList(), emptyList(), listOf(issue), null) { cite(it) })
    }

    private fun cite(source: RelationSource): Citation =
        if (source.newsId != null) Citation(CitationType.NEWS, source.newsId.toString(), "기사", "https://n/${source.newsId}")
        else Citation(CitationType.DISCLOSURE, requireNotNull(source.rceptNo), "공시", "https://d/${source.rceptNo}")

    private fun theme(id: Long, name: String, side: HotSide) = ThemeSummary(
        id = id, name = name, description = null, baseDate = baseDate, change = BigDecimal("1.0"),
        tradingValue = null, avgTradingValue = null, tradingValueRatio = null, marketCap = null,
        w1 = null, m1 = null, m3 = null, stockCount = 5, pricedCount = 5, upCount = 4, downCount = 1, flatCount = 0,
        suspendedCount = 0, trimCount = 1, meanChange = null, changeLower = null, changeUpper = null, sensitivity = null,
        w1Count = 0, m1Count = 0, m3Count = 0, leaders = listOf(ThemeLeader("000001", "주도주", BigDecimal("3.0"))),
        sources = listOf("naver"), hotSide = side, topStocks = emptyList(),
    )

    private fun price(ticker: String, name: String, change: String?) =
        StockPriceView(ticker, name, "KOSPI", BigDecimal("100"), change?.let(::BigDecimal), null)

    private fun relation(
        id: Long, subject: String, subjectCode: String?, relation: String, obj: String, objectCode: String?,
        item: String? = null, newsId: Long? = null, rceptNo: String? = null,
        polarity: String = "affirmed", tense: String = "past_or_present_fact",
    ) = RelationSource(
        id = id, sourceType = if (newsId != null) "news" else "disclosure", newsId = newsId, rceptNo = rceptNo,
        subjectName = subject, subjectCode = subjectCode, relation = relation, objectName = obj, objectCode = objectCode,
        item = item, sourceSentence = "$subject $obj", polarity = polarity, tense = tense,
        subjectImpact = null, objectImpact = null, mentionedAt = baseDate,
    )

    private fun contract(
        rceptNo: String, ticker: String, name: String,
        isCorrection: Boolean = false, reason: String? = null, endDate: LocalDate? = null,
    ) = SupplyContract(
        rceptNo = rceptNo, rceptDate = baseDate, reportName = if (isCorrection) "[기재정정]단일판매ㆍ공급계약체결" else "단일판매ㆍ공급계약체결",
        filerTicker = ticker, filerName = name, filerCorpCode = "C$ticker", filerMarket = "KOSPI",
        contractType = "공사수주", contractName = "$name 계약", counterparty = "상대", counterpartyCorpName = null, counterpartyTicker = null,
        contractAmountText = "1000", salesRatioText = "10.0", startDate = baseDate, endDate = endDate,
        link = "https://d/$rceptNo", isCorrection = isCorrection, correctionReason = reason,
    )
}
