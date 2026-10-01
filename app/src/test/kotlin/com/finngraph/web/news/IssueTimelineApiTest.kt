package com.finngraph.web.news

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
import org.springframework.http.ResponseEntity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class IssueTimelineApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Test
    fun `부모를 따라 올라간 제목 있는 이슈를 요청 이슈부터 주고 요청 이슈를 표시한다`() {
        val timeline = timeline(IssueTimelineSeed.CURRENT)

        assertEquals(IssueTimelineSeed.CURRENT, timeline.long("clusterId"))
        val nodes = timeline.nodes()
        assertEquals(listOf(IssueTimelineSeed.CURRENT, IssueTimelineSeed.MIDDLE, IssueTimelineSeed.ROOT), nodes.ids())
        assertEquals(listOf(true, false, false), nodes.map { it["current"] })

        val current = nodes.first()
        assertEquals("마이크론 4분기 실적 발표", current["title"])
        assertEquals("마이크론 4분기 매출이 전년 대비 46% 늘었어요.", current["summary"])
        assertEquals("2025-12-17", current["date"])
        assertEquals("2025-12-17T09:00:00+09:00", current["firstPublishedAt"])
        assertEquals("2025-12-18T10:00:00+09:00", current["lastPublishedAt"])

        assertTrue(nodes[1].containsKey("summary"))
        assertNull(nodes[1]["summary"])
    }

    @Test
    fun `날짜는 첫 기사 시각의 KST 날짜다`() {
        val root = timeline(IssueTimelineSeed.ROOT).nodes().single()

        assertEquals(IssueTimelineSeed.ROOT, root.long("clusterId"))
        assertEquals(true, root["current"])
        assertEquals("2025-09-30", root["date"])
        assertEquals("2025-09-30T01:00:00+09:00", root["firstPublishedAt"])
    }

    @Test
    fun `부모 사슬만 따라가고 같은 루트의 다른 갈래는 넣지 않는다`() {
        assertEquals(
            listOf(IssueTimelineSeed.SIBLING, IssueTimelineSeed.MIDDLE, IssueTimelineSeed.ROOT),
            timeline(IssueTimelineSeed.SIBLING).nodes().ids(),
        )
    }

    @Test
    fun `limit만큼 요청 이슈부터 자른다`() {
        assertEquals(
            listOf(IssueTimelineSeed.LATER, IssueTimelineSeed.CURRENT),
            timeline(IssueTimelineSeed.LATER, "limit=2").nodes().ids(),
        )
        assertEquals(
            listOf(IssueTimelineSeed.LATER, IssueTimelineSeed.CURRENT, IssueTimelineSeed.MIDDLE, IssueTimelineSeed.ROOT),
            timeline(IssueTimelineSeed.LATER, "limit=20").nodes().ids(),
        )
    }

    @Test
    fun `시작 시각이 부모보다 앞당겨져도 부모가 빠지지 않는다`() {
        assertEquals(
            listOf(IssueTimelineSeed.PULLED_EARLIER, IssueTimelineSeed.CURRENT, IssueTimelineSeed.MIDDLE, IssueTimelineSeed.ROOT),
            timeline(IssueTimelineSeed.PULLED_EARLIER).nodes().ids(),
        )
    }

    @Test
    fun `제목 없는 중간 이슈는 건너뛰고 그 앞 이슈까지 이어 준다`() {
        assertEquals(
            listOf(IssueTimelineSeed.BEHIND_UNTITLED, IssueTimelineSeed.MIDDLE, IssueTimelineSeed.ROOT),
            timeline(IssueTimelineSeed.BEHIND_UNTITLED).nodes().ids(),
        )
    }

    @Test
    fun `제목 없는 이슈는 연결돼 있어도 빈 목록이다`() {
        assertTrue(timeline(IssueTimelineSeed.UNTITLED).nodes().isEmpty())
    }

    @Test
    fun `연결 전 이슈는 자기 자신만, 제목 없는 연결 전 이슈는 빈 목록이다`() {
        val unlinked = timeline(IssueTimelineSeed.UNLINKED).nodes().single()
        assertEquals(IssueTimelineSeed.UNLINKED, unlinked.long("clusterId"))
        assertEquals(true, unlinked["current"])
        assertEquals("2025-10-01", unlinked["date"])

        assertTrue(timeline(IssueTimelineSeed.UNLINKED_UNTITLED).nodes().isEmpty())
    }

    @Test
    fun `없는 이슈는 404다`() {
        val response = get("/api/v1/news/clusters/999999/timeline")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.NEWS_CLUSTER_NOT_FOUND, response.error()["code"])
    }

    @Test
    fun `id가 양수가 아니거나 limit이 1~20 밖이면 400이다`() {
        listOf(
            "0/timeline",
            "abc/timeline",
            "${IssueTimelineSeed.CURRENT}/timeline?limit=0",
            "${IssueTimelineSeed.CURRENT}/timeline?limit=21",
        ).forEach { path ->
            val response = get("/api/v1/news/clusters/$path")
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, path)
            assertEquals(ErrorCode.INVALID_PARAMETER, response.error()["code"], path)
        }
    }

    private fun timeline(clusterId: Long, query: String = ""): Map<*, *> {
        val response = get("/api/v1/news/clusters/$clusterId/timeline?$query")
        assertEquals(HttpStatus.OK, response.statusCode, query)
        return response.body!!["data"] as Map<*, *>
    }

    private fun get(path: String): ResponseEntity<Map<*, *>> = rest.getForEntity(path, Map::class.java)

    private fun ResponseEntity<Map<*, *>>.error(): Map<*, *> = body!!["error"] as Map<*, *>

    private fun Map<*, *>.nodes(): List<Map<*, *>> = (this["nodes"] as List<*>).map { it as Map<*, *> }

    private fun List<Map<*, *>>.ids(): List<Long> = map { it.long("clusterId") }

    private fun Map<*, *>.long(key: String): Long = (this[key] as Number).toLong()

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
