package com.finngraph.support

import com.finngraph.briefing.model.AnalyzedNews
import com.finngraph.briefing.model.BriefingArticle
import com.finngraph.briefing.model.BriefingHeadline
import com.finngraph.briefing.model.BriefingIssue
import com.finngraph.briefing.model.BriefingLeader
import com.finngraph.briefing.model.BriefingSentence
import com.finngraph.briefing.model.BriefingStatus
import com.finngraph.briefing.model.BriefingStock
import com.finngraph.briefing.model.BriefingStockRef
import com.finngraph.briefing.model.BriefingTheme
import com.finngraph.briefing.model.Citation
import com.finngraph.briefing.model.CitationType
import com.finngraph.briefing.model.DailyBriefing
import com.finngraph.briefing.model.FlagSnapshotEntry
import com.finngraph.briefing.model.GraphEdge
import com.finngraph.briefing.model.GraphNode
import com.finngraph.briefing.model.MarketSnapshot
import com.finngraph.briefing.model.RelationGraph
import com.finngraph.briefing.model.RelationLine
import com.finngraph.briefing.model.RelationParty
import com.finngraph.briefing.model.RiskItem
import com.finngraph.briefing.model.RiskKind
import com.finngraph.briefing.model.WatchKind
import com.finngraph.briefing.model.WatchPoint
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

object BriefingFixtures {

    val KST: ZoneOffset = ZoneOffset.ofHours(9)

    val NEWS_CITATION = Citation(CitationType.NEWS, "9001", "시드 기사 제목", "https://seed.test/news/9001")
    val DISCLOSURE_CITATION = Citation(CitationType.DISCLOSURE, "99990001000003", "[기재정정]단일판매ㆍ공급계약체결", "https://seed.test/dart/99990001000003")
    val RELATION_CITATION = Citation(CitationType.RELATION, "77", "시드공급사 → 시드파트너 (배터리)", "https://seed.test/news/9001")
    val CLUSTER_CITATION = Citation(CitationType.CLUSTER, "501", "시드 이슈 클러스터", null)

    fun sample(baseDate: LocalDate = LocalDate.parse("2026-07-31")): DailyBriefing = DailyBriefing(
        baseDate = baseDate,
        previousTradingDate = baseDate.minusDays(1),
        status = BriefingStatus.READY,
        generatedAt = OffsetDateTime.of(baseDate.atTime(19, 30), KST),
        market = MarketSnapshot(
            baseDate = baseDate,
            pricedCount = 100,
            upCount = 60,
            downCount = 35,
            flatCount = 5,
            medianChange = BigDecimal("0.5000"),
            upRatio = BigDecimal("0.6000"),
            downRatio = BigDecimal("0.3500"),
            coverage = BigDecimal("0.9800"),
        ),
        headline = BriefingHeadline("상승 60·하락 35로 상승 우위였습니다.", listOf(CLUSTER_CITATION)),
        issues = listOf(
            BriefingIssue(
                clusterId = 501,
                title = "시드 이슈 클러스터",
                keywords = listOf("배터리", "공급"),
                newsCount = 3,
                firstPublishedAt = OffsetDateTime.of(baseDate.atTime(9, 0), KST),
                lastPublishedAt = OffsetDateTime.of(baseDate.atTime(15, 0), KST),
                stocks = listOf(BriefingStock("940001", "시드공급사", "KOSPI", BigDecimal("3.2100"))),
                articles = listOf(BriefingArticle(9001, "시드 기사 제목", "https://seed.test/news/9001", OffsetDateTime.of(baseDate.atTime(9, 0), KST))),
                commentary = listOf(
                    BriefingSentence("시드공급사가 시드파트너에 배터리를 공급했습니다.", listOf(NEWS_CITATION)),
                    BriefingSentence("계약 규모는 매출의 15%입니다.", listOf(DISCLOSURE_CITATION)),
                ),
            ),
        ),
        themes = listOf(
            BriefingTheme(9201, "시드급등테마", BigDecimal("4.1000"), "UP", 6, 6, 6, 0, 0, listOf(BriefingLeader("909107", "시드급등6", BigDecimal("8.0000")))),
        ),
        watchPoints = listOf(
            WatchPoint(WatchKind.CORRECTION, "정정 공시의 계약 종료일 변경을 확인할 것", listOf(DISCLOSURE_CITATION), listOf(BriefingStockRef("940001", "시드공급사"))),
        ),
        risks = listOf(
            RiskItem(RiskKind.CORRECTION, "940001", "시드공급사", "KOSPI", "시드 2차 정정", DISCLOSURE_CITATION),
            RiskItem(RiskKind.SUSPENDED_NEW, "909108", "시드정지", "KOSPI", "거래정지", null),
        ),
        analyzedNews = listOf(
            AnalyzedNews(
                newsId = 9001,
                title = "시드 기사 제목",
                url = "https://seed.test/news/9001",
                publishedAt = OffsetDateTime.of(baseDate.atTime(9, 0), KST),
                summary = "시드 요약",
                companies = listOf(BriefingStock("940001", "시드공급사", "KOSPI", BigDecimal("3.2100"))),
                relations = listOf(
                    RelationLine(
                        id = 77,
                        subject = RelationParty("시드공급사", "940001"),
                        relation = "SUPPLIES_TO",
                        target = RelationParty("시드파트너", "940002"),
                        item = "배터리",
                        polarity = "affirmed",
                        tense = "past_or_present_fact",
                        subjectImpact = "positive",
                        objectImpact = "neutral",
                        sourceSentence = "시드공급사는 시드파트너에 배터리를 공급한다.",
                        source = NEWS_CITATION,
                    ),
                ),
            ),
        ),
        relationGraph = RelationGraph(
            nodes = listOf(
                GraphNode("T:940001", "시드공급사", "940001", "KOSPI", BigDecimal("3.2100")),
                GraphNode("T:940002", "시드파트너", "940002", "KOSPI", null),
            ),
            edges = listOf(
                GraphEdge("T:940001|SUPPLIES_TO|T:940002", "T:940001", "T:940002", "SUPPLIES_TO", "배터리", "affirmed", "past_or_present_fact", 1, listOf(NEWS_CITATION)),
            ),
        ),
        flaggedSnapshot = listOf(
            FlagSnapshotEntry("909108", "시드정지", "KOSPI", false, true, false),
        ),
    )
}
