package com.finngraph.web.issue

import com.finngraph.support.IssueTimelineSeed
import com.finngraph.support.TestContainers
import com.finngraph.web.common.ErrorCode
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class IssueTimelineApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `부모를 따라 요청 이슈부터 과거 순으로 주고 노드가 못 되는 이슈는 건너뛰며 끝까지 따라간다`() {
        val timeline = timeline(IssueTimelineSeed.CURRENT)
        val nodes = timeline.nodes()

        assertEquals(IssueTimelineSeed.CURRENT, timeline.long("issueId"))
        assertEquals(listOf(IssueTimelineSeed.CURRENT, IssueTimelineSeed.MIDDLE, IssueTimelineSeed.ROOT), nodes.ids())
        assertEquals(listOf(true, false, false), nodes.map { it["current"] })
        assertEquals(
            listOf(
                listOf(IssueTimelineSeed.DUPLICATE, IssueTimelineSeed.CURRENT),
                listOf(IssueTimelineSeed.MIDDLE),
                listOf(IssueTimelineSeed.ROOT),
            ),
            nodes.map { it.longs("mergedIssueIds") },
        )
    }

    @Test
    fun `같은 루트의 다른 갈래는 넣지 않는다`() {
        assertEquals(
            listOf(IssueTimelineSeed.SIBLING, IssueTimelineSeed.MIDDLE, IssueTimelineSeed.ROOT),
            timeline(IssueTimelineSeed.SIBLING).nodes().ids(),
        )
        assertTrue(IssueTimelineSeed.SIBLING !in timeline(IssueTimelineSeed.CURRENT).nodes().ids())
    }

    @Test
    fun `노드의 제목은 클러스터 제목이고 보도 시각은 이슈 상세처럼 합친 이슈들의 공개 기사로만 센다`() {
        val node = timeline(IssueTimelineSeed.CURRENT).nodes().first()
        val current = fetch("/api/v1/issues/${IssueTimelineSeed.CURRENT}")["data"] as Map<*, *>
        val duplicate = fetch("/api/v1/issues/${IssueTimelineSeed.DUPLICATE}")["data"] as Map<*, *>

        assertEquals("마이크론 4분기 실적 발표", node["title"])
        assertEquals("2025-12-17", node["date"])
        assertEquals("2025-12-17T11:00:00+09:00", node["firstPublishedAt"])
        assertEquals("2025-12-18T10:00:00+09:00", node["lastPublishedAt"])
        assertEquals(duplicate["firstPublishedAt"], node["firstPublishedAt"])
        assertEquals(current["lastPublishedAt"], node["lastPublishedAt"])
    }

    @Test
    fun `날짜는 첫 공개 기사 보도 시각의 KST 날짜다`() {
        val root = timeline(IssueTimelineSeed.ROOT).nodes().single()

        assertEquals(true, root["current"])
        assertEquals("2025-09-30", root["date"])
        assertEquals("2025-09-30T01:00:00+09:00", root["firstPublishedAt"])
    }

    @Test
    fun `요약은 대표 기사 핵심 포인트의 CHANGE 문장, 없으면 요약 첫 문장, 둘 다 없으면 null 이다`() {
        val summaries = timeline(IssueTimelineSeed.LATER, "limit=20").nodes().associate { it.long("issueId") to it["summary"] }

        assertEquals("마이크론이 4분기 실적 발표 일정을 확정했어요.", summaries[IssueTimelineSeed.ROOT])
        assertEquals("매출이 0.9% 늘었어요.", summaries[IssueTimelineSeed.MIDDLE])
        assertEquals("1분기 실적을 발표했어요", summaries[IssueTimelineSeed.LATER])

        val unlinked = timeline(IssueTimelineSeed.UNLINKED).nodes().single()
        assertTrue(unlinked.containsKey("summary"))
        assertNull(unlinked["summary"])
    }

    @Test
    fun `대표 기사가 공개 기사가 아니면 이슈 상세처럼 가장 이른 공개 기사로 요약을 고른다`() {
        val node = timeline(IssueTimelineSeed.SIBLING).nodes().first()
        val detail = fetch("/api/v1/issues/${IssueTimelineSeed.SIBLING}")["data"] as Map<*, *>

        assertEquals(IssueTimelineSeed.SIBLING_PUBLIC_NEWS, detail.long("representativeNewsId"))
        assertEquals("실적 발표 뒤 주가가 7% 올랐어요.", node["summary"])
    }

    @Test
    fun `same_event 로 이어진 이슈는 한 노드로 합치고 편입 기사 수가 많은 이슈가 대표다`() {
        val nodes = timeline(IssueTimelineSeed.DUPLICATE).nodes()
        val merged = nodes.first()

        assertEquals(listOf(IssueTimelineSeed.CURRENT, IssueTimelineSeed.MIDDLE, IssueTimelineSeed.ROOT), nodes.ids())
        assertEquals(true, merged["current"])
        assertEquals(listOf(IssueTimelineSeed.DUPLICATE, IssueTimelineSeed.CURRENT), merged.longs("mergedIssueIds"))
        assertEquals("마이크론 4분기 실적 발표", merged["title"])
        assertEquals("마이크론 4분기 매출이 46% 늘었어요.", merged["summary"])
        assertEquals("2025-12-17", merged["date"])
        assertEquals("2025-12-17T11:00:00+09:00", merged["firstPublishedAt"])
        assertEquals("2025-12-18T10:00:00+09:00", merged["lastPublishedAt"])
        assertEquals(merged, timeline(IssueTimelineSeed.CURRENT).nodes().first())
    }

    @Test
    fun `요청 이슈 쪽 다음 노드가 합친 노드여도 사슬 순서를 지키고 current 는 요청 이슈가 든 노드다`() {
        val nodes = timeline(IssueTimelineSeed.LATER).nodes()

        assertEquals(
            listOf(IssueTimelineSeed.LATER, IssueTimelineSeed.CURRENT, IssueTimelineSeed.MIDDLE, IssueTimelineSeed.ROOT),
            nodes.ids(),
        )
        assertEquals(listOf(true, false, false, false), nodes.map { it["current"] })
        assertEquals(listOf(IssueTimelineSeed.DUPLICATE, IssueTimelineSeed.CURRENT), nodes[1].longs("mergedIssueIds"))
    }

    @Test
    fun `편입 기사 수가 같으면 먼저 보도된 이슈가 대표이고 대표에 요약이 없으면 합친 다른 이슈의 요약을 쓴다`() {
        val node = timeline(IssueTimelineSeed.TWIN_LATE).nodes().single()

        assertEquals(IssueTimelineSeed.TWIN_EARLY, node.long("issueId"))
        assertEquals("미국 8월 건설지출 발표", node["title"])
        assertEquals(true, node["current"])
        assertEquals(listOf(IssueTimelineSeed.TWIN_LATE, IssueTimelineSeed.TWIN_EARLY), node.longs("mergedIssueIds"))
        assertEquals("2025-10-01", node["date"])
        assertEquals("2025-10-02T12:00:00+09:00", node["lastPublishedAt"])
        assertEquals("건설지출 요약이에요.", node["summary"])
        assertEquals(node, timeline(IssueTimelineSeed.TWIN_EARLY).nodes().single())
    }

    @Test
    fun `노드가 못 되는 이슈도 거르기 전 사슬로 같은 사건 묶음을 나눈다`() {
        val gap = timeline(IssueTimelineSeed.GAP_LATEST).nodes()
        assertEquals(listOf(IssueTimelineSeed.GAP_LATEST, IssueTimelineSeed.GAP_ROOT), gap.ids())
        assertEquals(
            listOf(listOf(IssueTimelineSeed.GAP_LATEST), listOf(IssueTimelineSeed.GAP_ROOT)),
            gap.map { it.longs("mergedIssueIds") },
        )

        val bridge = timeline(IssueTimelineSeed.BRIDGE_LATEST).nodes().single()
        assertEquals(IssueTimelineSeed.BRIDGE_ROOT, bridge.long("issueId"))
        assertEquals(true, bridge["current"])
        assertEquals(
            listOf(IssueTimelineSeed.BRIDGE_SIBLING, IssueTimelineSeed.BRIDGE_LATEST, IssueTimelineSeed.BRIDGE_ROOT),
            bridge.longs("mergedIssueIds"),
        )
    }

    @Test
    fun `같은 사건이 사슬 밖으로 갈라져도 사건의 어느 이슈를 요청하든 같은 노드다`() {
        val expected = timeline(IssueTimelineSeed.BRIDGE_LATEST).nodes().single()

        listOf(IssueTimelineSeed.BRIDGE_ROOT, IssueTimelineSeed.BRIDGE_SIBLING).forEach { id ->
            assertEquals(listOf(expected), timeline(id).nodes(), "$id")
        }
    }

    @Test
    fun `limit 은 합친 뒤 노드 수다`() {
        val two = timeline(IssueTimelineSeed.LATER, "limit=2").nodes()

        assertEquals(listOf(IssueTimelineSeed.LATER, IssueTimelineSeed.CURRENT), two.ids())
        assertEquals(listOf(IssueTimelineSeed.DUPLICATE, IssueTimelineSeed.CURRENT), two[1].longs("mergedIssueIds"))
        assertEquals(listOf(IssueTimelineSeed.LATER), timeline(IssueTimelineSeed.LATER, "limit=1").nodes().ids())
        assertEquals(4, timeline(IssueTimelineSeed.LATER, "limit=20").nodes().size)
    }

    @Test
    fun `연결 전 이슈는 자기 하나이고 제목 없는 이슈는 자기 노드 없이 앞선 노드만 준다`() {
        val unlinked = timeline(IssueTimelineSeed.UNLINKED).nodes().single()
        assertEquals(IssueTimelineSeed.UNLINKED, unlinked.long("issueId"))
        assertEquals(true, unlinked["current"])
        assertEquals(listOf(IssueTimelineSeed.UNLINKED), unlinked.longs("mergedIssueIds"))

        assertEquals(emptyList(), timeline(IssueTimelineSeed.UNLINKED_UNTITLED).nodes())

        val untitled = timeline(IssueTimelineSeed.UNTITLED).nodes()
        assertEquals(listOf(IssueTimelineSeed.MIDDLE, IssueTimelineSeed.ROOT), untitled.ids())
        assertTrue(untitled.none { it["current"] == true })
    }

    @Test
    fun `부모 연결이 순환해도 같은 이슈를 한 번만 준다`() {
        assertEquals(
            listOf(IssueTimelineSeed.CYCLE_A, IssueTimelineSeed.CYCLE_B),
            timeline(IssueTimelineSeed.CYCLE_A).nodes().ids(),
        )
    }

    @Test
    fun `없는 이슈와 공개 기사가 없는 이슈는 이슈 상세처럼 404`() {
        listOf(999_999L, IssueTimelineSeed.NO_PUBLIC, IssueTimelineSeed.HIDDEN, IssueTimelineSeed.BRIDGE_HIDDEN).forEach { id ->
            val timeline = rest.getForEntity("/api/v1/issues/$id/timeline", String::class.java)
            val detail = rest.getForEntity("/api/v1/issues/$id", String::class.java)

            assertEquals(HttpStatus.NOT_FOUND, timeline.statusCode, "$id")
            assertEquals(detail.statusCode, timeline.statusCode, "$id")
            assertTrue(timeline.body!!.contains(ErrorCode.ISSUE_NOT_FOUND), "$id")
        }
    }

    @Test
    fun `id 가 양수가 아니거나 limit 이 1~20 밖이면 400`() {
        val base = "/api/v1/issues/${IssueTimelineSeed.CURRENT}/timeline"
        mapOf(
            "/api/v1/issues/0/timeline" to "id",
            "/api/v1/issues/-1/timeline" to "id",
            "/api/v1/issues/abc/timeline" to "id",
            "$base?limit=0" to "limit",
            "$base?limit=21" to "limit",
            "$base?limit=abc" to "limit",
        ).forEach { (path, field) ->
            val response = rest.getForEntity(path, String::class.java)
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, path)
            assertTrue(response.body!!.contains(ErrorCode.INVALID_PARAMETER), path)
            assertTrue(response.body!!.contains("\"$field\""), path)
        }
        listOf("$base?limit=1", "$base?limit=20").forEach { path ->
            assertEquals(HttpStatus.OK, rest.getForEntity(path, String::class.java).statusCode, path)
        }
    }

    private fun timeline(id: Long, query: String = ""): Map<*, *> =
        fetch("/api/v1/issues/$id/timeline?$query")["data"] as Map<*, *>

    private fun fetch(path: String): Map<*, *> {
        val response = rest.getForEntity(path, String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode, path)
        return mapper.readValue(response.body, Map::class.java)
    }

    private fun Map<*, *>.nodes(): List<Map<*, *>> = (this["nodes"] as List<*>).map { it as Map<*, *> }

    private fun List<Map<*, *>>.ids(): List<Long> = map { it.long("issueId") }

    private fun Map<*, *>.long(key: String): Long = (this[key] as Number).toLong()

    private fun Map<*, *>.longs(key: String): List<Long> = (this[key] as List<*>).map { (it as Number).toLong() }

    companion object {
        @JvmStatic
        @BeforeAll
        fun seed() = IssueTimelineSeed.seed()

        @JvmStatic
        @AfterAll
        fun cleanup() = IssueTimelineSeed.cleanup()

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
