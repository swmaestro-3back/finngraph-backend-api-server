package com.finngraph.composition.briefing

import com.finngraph.briefing.model.BriefingHeadline
import com.finngraph.briefing.model.BriefingIssue
import com.finngraph.briefing.model.BriefingSentence
import com.finngraph.briefing.model.BriefingStatus
import com.finngraph.briefing.model.BriefingStockRef
import com.finngraph.briefing.model.Citation
import com.finngraph.briefing.model.CitationType
import com.finngraph.briefing.model.WatchKind
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BriefingValidatorTest {

    private val news = Citation(CitationType.NEWS, "1", "기사", "https://n/1")
    private val disclosure = Citation(CitationType.DISCLOSURE, "20260101", "공시", "https://d/1")
    private val allowed = mapOf(news.key() to news, disclosure.key() to disclosure)

    @Test
    fun `허용 집합에 있는 인용만 붙여 문장을 남긴다`() {
        val result = BriefingValidator.sentences(
            listOf(SentenceDraft("A사가 B사에 공급했습니다.", listOf("NEWS:1", "NEWS:1"))),
            allowed,
            maxChars = 90,
            requireCitation = true,
        )

        assertEquals(listOf(BriefingSentence("A사가 B사에 공급했습니다.", listOf(news))), result.sentences)
        assertEquals(0, result.dropped)
    }

    @Test
    fun `문장 안에 섞인 인용 키 표기는 지우고 남긴다`() {
        val result = BriefingValidator.sentences(
            listOf(SentenceDraft("계약이 체결되었습니다. (NEWS:1, DISCLOSURE:20260101)", listOf("NEWS:1"))),
            allowed,
            maxChars = 90,
            requireCitation = true,
        )

        assertEquals("계약이 체결되었습니다.", result.sentences.single().text)
    }

    @Test
    fun `지켜볼 점 문장 안의 인용 키도 지운다`() {
        val candidates = listOf(WatchCandidate(WatchKind.CONTRACT_END, "계약 종료", disclosure, emptyList()))

        val result = BriefingValidator.watchPoints(listOf(WatchDraft(0, "종료일을 확인할 것 (DISCLOSURE:20260101)", emptyList())), candidates)

        assertEquals("종료일을 확인할 것", result.points.single().text)
    }

    @Test
    fun `허용되지 않은 인용이 하나라도 있으면 문장을 버린다`() {
        val result = BriefingValidator.sentences(
            listOf(SentenceDraft("근거가 섞인 문장입니다.", listOf("NEWS:1", "NEWS:999"))),
            allowed,
            maxChars = 90,
            requireCitation = true,
        )

        assertEquals(emptyList(), result.sentences)
        assertEquals(1, result.dropped)
    }

    @Test
    fun `인용이 필수인데 없으면 버리고 선택이면 남긴다`() {
        val draft = SentenceDraft("인용 없는 문장입니다.", emptyList())

        assertEquals(1, BriefingValidator.sentences(listOf(draft), allowed, 90, requireCitation = true).dropped)
        assertEquals(0, BriefingValidator.sentences(listOf(draft), allowed, 90, requireCitation = false).dropped)
    }

    @Test
    fun `금칙어가 들어간 문장은 버린다`() {
        val drafts = listOf(
            SentenceDraft("매수 추천 종목입니다.", listOf("NEWS:1")),
            SentenceDraft("시장 전망이 밝습니다.", listOf("NEWS:1")),
            SentenceDraft("예상보다 많은 수주였습니다.", listOf("NEWS:1")),
            SentenceDraft("수혜주로 꼽힙니다.", listOf("NEWS:1")),
            SentenceDraft("계약이 체결되었습니다.", listOf("NEWS:1")),
        )

        val result = BriefingValidator.sentences(drafts, allowed, 90, requireCitation = true)

        assertEquals(listOf("계약이 체결되었습니다."), result.sentences.map { it.text })
        assertEquals(4, result.dropped)
    }

    @Test
    fun `길이 상한을 넘거나 비어 있으면 버린다`() {
        val drafts = listOf(
            SentenceDraft("가".repeat(91), listOf("NEWS:1")),
            SentenceDraft("   ", listOf("NEWS:1")),
            SentenceDraft("가".repeat(90), listOf("NEWS:1")),
        )

        val result = BriefingValidator.sentences(drafts, allowed, 90, requireCitation = true)

        assertEquals(1, result.sentences.size)
        assertEquals(2, result.dropped)
    }

    @Test
    fun `해설은 최대 3문장까지만 남긴다`() {
        val drafts = (1..5).map { SentenceDraft("문장 $it 입니다.", listOf("NEWS:1")) }

        val result = BriefingValidator.commentary(drafts, allowed)

        assertEquals(3, result.sentences.size)
        assertEquals(2, result.dropped)
    }

    @Test
    fun `헤드라인은 인용 없이도 허용하고 90자를 넘으면 버린다`() {
        val cluster = Citation(CitationType.CLUSTER, "5", "이슈", null)
        val ok = BriefingValidator.headline(HeadlineDraft("상승 60 하락 35였습니다.", emptyList()), mapOf(cluster.key() to cluster))
        val long = BriefingValidator.headline(HeadlineDraft("가".repeat(91), listOf("CLUSTER:5")), mapOf(cluster.key() to cluster))

        assertEquals(BriefingHeadline("상승 60 하락 35였습니다.", emptyList()), ok.headline)
        assertNull(long.headline)
        assertEquals(1, long.dropped)
    }

    @Test
    fun `지켜볼 점은 후보 밖 인덱스와 중복을 버리고 후보의 인용을 붙인다`() {
        val candidates = listOf(
            WatchCandidate(WatchKind.CORRECTION, "정정 공시", disclosure, listOf(BriefingStockRef("000001", "A사"))),
            WatchCandidate(WatchKind.CONTRACT_END, "계약 종료", disclosure, emptyList()),
        )
        val drafts = listOf(
            WatchDraft(1, "계약 종료일을 확인할 것", emptyList()),
            WatchDraft(1, "중복 문장", emptyList()),
            WatchDraft(7, "후보 밖", emptyList()),
            WatchDraft(0, "정정 공시 내용을 확인할 것", emptyList()),
        )

        val result = BriefingValidator.watchPoints(drafts, candidates)

        assertEquals(listOf(WatchKind.CONTRACT_END, WatchKind.CORRECTION), result.points.map { it.kind })
        assertEquals(listOf(disclosure), result.points[0].citations)
        assertEquals(listOf(BriefingStockRef("000001", "A사")), result.points[1].stocks)
        assertEquals(2, result.dropped)
    }

    @Test
    fun `지켜볼 점 문장의 금칙어와 80자 초과도 버린다`() {
        val candidates = listOf(WatchCandidate(WatchKind.CORRECTION, "정정", disclosure, emptyList()))
        val drafts = listOf(
            WatchDraft(0, "급등이 예상됩니다", emptyList()),
            WatchDraft(0, "가".repeat(81), emptyList()),
        )

        val result = BriefingValidator.watchPoints(drafts, candidates)

        assertEquals(emptyList(), result.points)
        assertEquals(2, result.dropped)
    }

    @Test
    fun `상태는 헤드라인과 해설 유무로 정한다`() {
        val now = OffsetDateTime.parse("2026-08-20T19:00:00+09:00")
        val withCommentary = issue(1, listOf(BriefingSentence("문장", listOf(news))), now)
        val without = issue(2, null, now)
        val headline = BriefingHeadline("한 줄", emptyList())

        assertEquals(BriefingStatus.READY, BriefingValidator.status(headline, emptyList()))
        assertEquals(BriefingStatus.READY, BriefingValidator.status(headline, listOf(withCommentary, without)))
        assertEquals(BriefingStatus.PARTIAL, BriefingValidator.status(headline, listOf(without)))
        assertEquals(BriefingStatus.PARTIAL, BriefingValidator.status(null, emptyList()))
    }

    private fun issue(id: Long, commentary: List<BriefingSentence>?, at: OffsetDateTime) = BriefingIssue(
        clusterId = id,
        title = "이슈 $id",
        keywords = emptyList(),
        newsCount = 3,
        firstPublishedAt = at,
        lastPublishedAt = at,
        stocks = emptyList(),
        articles = emptyList(),
        commentary = commentary,
    )
}
