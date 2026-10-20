package com.finngraph.composition.issue

import com.finngraph.news.model.IssueArticle
import com.finngraph.news.model.IssueChainLink
import com.finngraph.news.model.IssueChainStep
import com.finngraph.news.model.IssueLinkRelation
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SameEventRulesTest {

    private fun step(id: Long, relation: IssueLinkRelation?) = IssueChainStep(id, relation, true)

    private fun link(id: Long, size: Int, startedAt: String) =
        IssueChainLink(id, IssueLinkRelation.SAME_EVENT, size, OffsetDateTime.parse(startedAt))

    private fun article(id: Long, publishedAt: String?) = IssueArticle(
        id = id,
        clusterId = 1,
        title = "기사 $id",
        url = null,
        publishedAt = publishedAt?.let(OffsetDateTime::parse),
        summary = null,
        summaryPoints = emptyList(),
        tripleExtracted = true,
    )

    @Test
    fun `same_event 로 올라가다 다른 관계를 만난 이슈가 사건의 뿌리다`() {
        val same = IssueLinkRelation.SAME_EVENT
        val follow = IssueLinkRelation.FOLLOW_UP
        assertEquals(2L, IssueRules.sameEventRoot(1, listOf(step(1, same), step(2, follow), step(3, null))))
        assertEquals(3L, IssueRules.sameEventRoot(1, listOf(step(1, same), step(2, same), step(3, null))))
        assertEquals(1L, IssueRules.sameEventRoot(1, listOf(step(1, follow), step(2, null))))
        assertEquals(1L, IssueRules.sameEventRoot(1, listOf(step(1, null))))
        assertEquals(2L, IssueRules.sameEventRoot(1, listOf(step(1, same), step(2, same))))
        assertEquals(1L, IssueRules.sameEventRoot(1, emptyList()))
    }

    @Test
    fun `대표는 제목 있는 이슈 중 편입 기사 수가 많고 먼저 보도되고 id 가 작은 이슈다`() {
        val links = listOf(
            link(10, 9, "2030-01-01T08:00:00+09:00"),
            link(11, 4, "2030-01-01T10:00:00+09:00"),
            link(12, 4, "2030-01-01T09:00:00+09:00"),
            link(13, 4, "2030-01-01T09:00:00+09:00"),
        )
        assertEquals(12L, IssueRules.eventLead(links) { it != 10L })
        assertEquals(10L, IssueRules.eventLead(links) { true })
        assertEquals(10L, IssueRules.eventLead(links) { false })
        assertNull(IssueRules.eventLead(emptyList()) { true })
    }

    @Test
    fun `합친 기사는 중복 없이 보도 시각 순이고 시각 없는 기사는 뒤로 간다`() {
        val merged = IssueRules.mergeArticles(
            listOf(
                listOf(article(3, "2030-01-02T09:00:00+09:00"), article(1, "2030-01-01T09:00:00+09:00")),
                listOf(article(1, "2030-01-01T09:00:00+09:00"), article(2, null), article(4, "2030-01-01T09:00:00+09:00")),
            ),
        )
        assertEquals(listOf(1L, 4L, 3L, 2L), merged.map { it.id })
    }
}
