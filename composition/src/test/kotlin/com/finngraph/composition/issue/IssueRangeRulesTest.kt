package com.finngraph.composition.issue

import com.finngraph.news.model.IssueMention
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class IssueRangeRulesTest {

    private val today: LocalDate = LocalDate.parse("2030-03-11")

    private fun at(text: String): OffsetDateTime = OffsetDateTime.parse(text)

    private fun mention(clusterId: Long, last: String?) = IssueMention("000001", clusterId, last?.let(::at))

    private fun summary(id: Long, last: String?) = IssueSummary(
        id = id,
        title = "이슈 $id",
        titleSource = IssueTitleSource.CLUSTER,
        articleCount = 1,
        mediaCount = 1,
        firstPublishedAt = null,
        lastPublishedAt = last?.let(::at),
        keywords = emptyList(),
        summary = null,
        representativeNewsId = id,
        companies = emptyList(),
    )

    @Test
    fun `종목 이슈 기간은 to 기본값이 오늘이고 from 기본값이 to − 365일이다`() {
        assertEquals(DateRange(LocalDate.parse("2029-03-11"), today), IssueRules.stockRange(null, null, today))
        assertEquals(
            DateRange(LocalDate.parse("2029-01-01"), LocalDate.parse("2030-01-01")),
            IssueRules.stockRange(null, LocalDate.parse("2030-01-01"), today),
        )
        assertEquals(
            DateRange(LocalDate.parse("2030-02-01"), today),
            IssueRules.stockRange(LocalDate.parse("2030-02-01"), null, today),
        )
    }

    @Test
    fun `최근 이슈 기간은 오늘 − 30일부터 오늘까지다`() {
        assertEquals(DateRange(LocalDate.parse("2030-02-09"), today), IssueRules.latestRange(today))
    }

    @Test
    fun `기간 창은 from KST 자정부터 to 다음 날 KST 자정 직전까지다`() {
        val window = IssueRules.window(DateRange(LocalDate.parse("2030-03-01"), today))

        assertEquals(at("2030-03-01T00:00:00+09:00").toInstant(), window.start.toInstant())
        assertEquals(at("2030-03-12T00:00:00+09:00").toInstant(), window.end.toInstant())
        assertEquals(IssueRules.dayWindow(today), IssueRules.window(DateRange(today, today)))
    }

    @Test
    fun `종목 이슈 순서는 이슈 목록 recent 정렬과 같다`() {
        val lasts = mapOf(
            1L to "2030-03-11T09:00:00+09:00",
            2L to "2030-03-11T10:00:00+09:00",
            3L to "2030-03-11T10:00:00+09:00",
            4L to null,
            5L to "2030-03-10T23:00:00+09:00",
        )

        val mentions = lasts.map { (id, last) -> mention(id, last) }.sortedWith(IssueRules.mentionOrder()).map { it.clusterId }
        val summaries = lasts.map { (id, last) -> summary(id, last) }.sortedWith(IssueRules.order(IssueSort.RECENT)).map { it.id }

        assertEquals(listOf(3L, 2L, 1L, 5L, 4L), mentions)
        assertEquals(summaries, mentions)
    }
}
