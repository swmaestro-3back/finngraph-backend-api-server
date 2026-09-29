package com.finngraph.dailybriefing

import com.finngraph.briefing.model.BriefingLeader
import com.finngraph.briefing.model.BriefingStock
import com.finngraph.briefing.model.BriefingStockRef
import com.finngraph.briefing.model.BriefingTheme
import com.finngraph.briefing.model.Citation
import com.finngraph.briefing.model.CitationType
import com.finngraph.briefing.model.MarketSnapshot
import com.finngraph.briefing.model.WatchKind
import com.finngraph.composition.briefing.BriefingValidator
import com.finngraph.composition.briefing.WatchCandidate
import com.finngraph.composition.port.ArticleBrief
import com.finngraph.composition.port.CommentaryInput
import com.finngraph.composition.port.ContractBrief
import com.finngraph.composition.port.HeadlineInput
import com.finngraph.composition.port.IssueBrief
import com.finngraph.composition.port.RelationBrief
import com.finngraph.composition.port.WatchInput
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertTrue

class BriefingPromptsTest {

    private val market = MarketSnapshot(LocalDate.parse("2026-08-20"), 100, 60, 35, 5, BigDecimal("0.5000"), null, null, BigDecimal("1.0000"))

    @Test
    fun `헤드라인 입력에는 시장 폭과 이슈 CLUSTER 키가 들어간다`() {
        val text = BriefingPrompts.headlineUser(
            HeadlineInput(
                baseDate = LocalDate.parse("2026-08-20"),
                market = market,
                themes = listOf(BriefingTheme(1, "배터리", BigDecimal("3.1"), "UP", 5, 5, 4, 1, 0, listOf(BriefingLeader("000001", "주도주", BigDecimal("7.0"))))),
                issues = listOf(IssueBrief(501, "시드 이슈", 3)),
            ),
        )

        assertTrue(text.contains("up 60 · down 35 · flat 5"))
        assertTrue(text.contains("[CLUSTER:501] 시드 이슈 (3 articles)"))
        assertTrue(text.contains("배터리 (UP, 3.1%"))
        assertTrue(text.contains("주도주 7.0%"))
    }

    @Test
    fun `해설 입력에는 기사·관계·공시 인용 키가 들어간다`() {
        val text = BriefingPrompts.commentaryUser(
            CommentaryInput(
                clusterId = 501,
                title = "시드 이슈",
                keywords = listOf("배터리"),
                articles = listOf(ArticleBrief(9001, "기사 제목", OffsetDateTime.parse("2026-08-20T09:00:00+09:00"), "본문 요약")),
                stocks = listOf(BriefingStock("000001", "A사", "KOSPI", BigDecimal("2.0"))),
                relations = listOf(RelationBrief(77, "A사가 B사에 공급한다", "affirmed", "future_or_planned")),
                contracts = listOf(ContractBrief("20260820", "단일판매ㆍ공급계약체결", "배터리 공급", "1500", "15.0", false)),
            ),
        )

        assertTrue(text.contains("[NEWS:9001] 08-20 09:00 기사 제목"))
        assertTrue(text.contains("본문 요약"))
        assertTrue(text.contains("[RELATION:77] A사가 B사에 공급한다 (polarity affirmed, tense future_or_planned)"))
        assertTrue(text.contains("[DISCLOSURE:20260820] 단일판매ㆍ공급계약체결 배터리 공급 amount 1500 sales ratio 15.0%"))
        assertTrue(text.contains("A사(2.0%)"))
    }

    @Test
    fun `지켜볼 점 입력은 후보 번호와 인용 키를 나열한다`() {
        val citation = Citation(CitationType.DISCLOSURE, "20260820", "공시", null)
        val text = BriefingPrompts.watchUser(
            WatchInput(listOf(WatchCandidate(WatchKind.CONTRACT_END, "계약 종료 예정일 2026-08-30", citation, listOf(BriefingStockRef("000001", "A사"))))),
        )

        assertTrue(text.contains("0. [CONTRACT_END] 계약 종료 예정일 2026-08-30 (cite DISCLOSURE:20260820)"))
    }

    @Test
    fun `시스템 프롬프트는 금칙어 목록을 그대로 싣는다`() {
        BriefingValidator.FORBIDDEN.forEach { word ->
            assertTrue(BriefingPrompts.HEADLINE_SYSTEM.contains(word), word)
            assertTrue(BriefingPrompts.COMMENTARY_SYSTEM.contains(word), word)
            assertTrue(BriefingPrompts.WATCH_SYSTEM.contains(word), word)
        }
    }
}
