package com.finngraph.composition.issue

import com.finngraph.news.model.IssueChainLink
import com.finngraph.news.model.IssueLinkRelation
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IssueTimelineRulesTest {

    private fun link(id: Long, relation: IssueLinkRelation?, startedAt: String = "2030-01-01T09:00:00+09:00") =
        IssueChainLink(id, relation, 1, OffsetDateTime.parse(startedAt))

    private fun eventIds(groups: List<List<IssueChainLink>>): List<List<Long>> =
        groups.map { group -> group.map { it.clusterId } }

    private fun groupIds(chain: List<IssueChainLink>): List<List<Long>> = eventIds(IssueRules.timelineGroups(chain))

    private fun member(id: Long, originalSize: Int, first: String?) = TimelineMember(
        issue = IssueSummary(
            id = id,
            title = "이슈 $id",
            titleSource = IssueTitleSource.CLUSTER,
            articleCount = 1,
            mediaCount = 1,
            firstPublishedAt = first?.let(OffsetDateTime::parse),
            lastPublishedAt = null,
            keywords = emptyList(),
            summary = null,
            representativeNewsId = id,
            companies = emptyList(),
        ),
        originalSize = originalSize,
    )

    @Test
    fun `앞 칸이 부모와 same_event 로 이어졌을 때만 부모를 같은 묶음에 넣는다`() {
        val chain = listOf(
            link(1, IssueLinkRelation.FOLLOW_UP),
            link(2, IssueLinkRelation.SAME_EVENT),
            link(3, IssueLinkRelation.SAME_EVENT),
            link(4, IssueLinkRelation.FOLLOW_UP),
            link(5, null),
        )

        assertEquals(listOf(listOf(1L), listOf(2L, 3L, 4L), listOf(5L)), groupIds(chain))
    }

    @Test
    fun `마지막 칸의 관계는 사슬 밖 부모를 가리키므로 묶음에 영향이 없다`() {
        val chain = listOf(link(1, IssueLinkRelation.FOLLOW_UP), link(2, IssueLinkRelation.SAME_EVENT))

        assertEquals(listOf(listOf(1L), listOf(2L)), groupIds(chain))
        assertEquals(emptyList(), groupIds(emptyList()))
    }

    @Test
    fun `묶음 맨 위 이슈의 same_event 자손을 더해 최신순으로 두고 앞 묶음에 든 이슈는 다시 넣지 않는다`() {
        val root = link(1, null, "2030-01-01T09:00:00+09:00")
        val top = link(2, IssueLinkRelation.FOLLOW_UP, "2030-01-02T09:00:00+09:00")
        val requested = link(3, IssueLinkRelation.SAME_EVENT, "2030-01-03T09:00:00+09:00")
        val sibling = link(4, IssueLinkRelation.SAME_EVENT, "2030-01-04T09:00:00+09:00")
        val groups = listOf(listOf(requested, top), listOf(root))
        // 순환 같은 잘못된 데이터로 뒤 묶음의 자손에 앞 묶음 이슈가 섞여도 앞 묶음에만 둔다.
        val trees = mapOf(2L to listOf(top, requested, sibling), 1L to listOf(root, sibling))

        assertEquals(listOf(listOf(4L, 3L, 2L), listOf(1L)), eventIds(IssueRules.timelineEvents(groups, trees)))
        assertEquals(listOf(listOf(1L)), eventIds(IssueRules.timelineEvents(listOf(listOf(root)), emptyMap())))
    }

    @Test
    fun `대표는 편입 기사 수가 많은 이슈, 같으면 먼저 보도된 이슈, 그래도 같으면 작은 id 다`() {
        assertEquals(
            2L,
            IssueRules.timelineLeadOrder(
                listOf(member(1, 2, "2030-01-01T09:00:00+09:00"), member(2, 5, "2030-01-02T09:00:00+09:00")),
            ).first().issue.id,
        )
        assertEquals(
            3L,
            IssueRules.timelineLeadOrder(
                listOf(
                    member(1, 3, null),
                    member(2, 3, "2030-01-02T09:00:00+09:00"),
                    member(3, 3, "2030-01-01T09:00:00+09:00"),
                ),
            ).first().issue.id,
        )
        assertEquals(
            4L,
            IssueRules.timelineLeadOrder(
                listOf(member(7, 3, "2030-01-01T09:00:00+09:00"), member(4, 3, "2030-01-01T09:00:00+09:00")),
            ).first().issue.id,
        )
    }

    @Test
    fun `첫 문장은 문장 끝 부호 뒤에 공백이나 끝이 올 때까지다`() {
        assertEquals("매출이 0.9% 늘었어요.", IssueRules.firstSentence("매출이 0.9% 늘었어요. 둘째 문장이에요."))
        assertEquals("정말 올랐나요?", IssueRules.firstSentence("정말 올랐나요?\n둘째 문장이에요."))
        assertEquals(
            "회사는 \"공급을 늘리겠다.\"라고 밝혔어요.",
            IssueRules.firstSentence("회사는 \"공급을 늘리겠다.\"라고 밝혔어요. 둘째 문장이에요."),
        )
        assertEquals("그는 \"좋아요.\"", IssueRules.firstSentence("그는 \"좋아요.\" 둘째 문장이에요."))
        assertEquals("끝 부호가 없어요", IssueRules.firstSentence("  끝 부호가 없어요  "))
        assertNull(IssueRules.firstSentence("   "))
        assertNull(IssueRules.firstSentence(null))
    }
}
