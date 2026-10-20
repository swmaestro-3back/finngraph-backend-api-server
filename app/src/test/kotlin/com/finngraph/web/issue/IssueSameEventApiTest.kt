package com.finngraph.web.issue

import com.finngraph.support.IssueTimelineSeed
import com.finngraph.support.TestContainers
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class IssueSameEventApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `같은 날 same_event 로 이어진 이슈는 목록에 대표 하나만 나오고 숨은 이슈를 건너도 묶인다`() {
        val day = list("2025-11-12")
        val ids = day.map { it.long("id") }

        assertTrue(IssueTimelineSeed.BRIDGE_ROOT in ids)
        assertFalse(IssueTimelineSeed.BRIDGE_LATEST in ids)
        assertFalse(IssueTimelineSeed.BRIDGE_SIBLING in ids)
        assertEquals(3, day.single { it.long("id") == IssueTimelineSeed.BRIDGE_ROOT }.int("articleCount"))
    }

    @Test
    fun `follow_up 은 합치지 않고 same_event 만 편입 기사 수가 많은 대표로 합친다`() {
        val ids = list("2025-12-17").map { it.long("id") }

        assertTrue(IssueTimelineSeed.CURRENT in ids)
        assertTrue(IssueTimelineSeed.SIBLING in ids)
        assertFalse(IssueTimelineSeed.DUPLICATE in ids)
    }

    @Test
    fun `날을 넘긴 same_event 도 한 사건이라 양쪽 날짜 목록에 같은 대표와 같은 숫자로 나온다`() {
        listOf("2025-10-01", "2025-10-02").forEach { date ->
            val day = list(date)
            val ids = day.map { it.long("id") }
            assertTrue(IssueTimelineSeed.TWIN_EARLY in ids, date)
            assertFalse(IssueTimelineSeed.TWIN_LATE in ids, date)
            assertEquals(3, day.single { it.long("id") == IssueTimelineSeed.TWIN_EARLY }.int("articleCount"), date)
        }
    }

    @Test
    fun `이슈 상세는 사건의 어느 이슈로 열어도 목록·타임라인과 같은 기사 수를 준다`() {
        val listed = list("2025-12-17").single { it.long("id") == IssueTimelineSeed.CURRENT }.int("articleCount")
        val node = (fetch("/api/v1/issues/${IssueTimelineSeed.CURRENT}/timeline")["data"] as Map<*, *>)
            .let { data -> (data["nodes"] as List<*>).map { it as Map<*, *> } }
            .single { it["current"] == true }

        listOf(IssueTimelineSeed.CURRENT, IssueTimelineSeed.DUPLICATE).forEach { id ->
            val detail = detail(id)
            assertEquals(id, detail.long("id"))
            assertEquals(listed, detail.int("articleCount"), "detail $id")
            assertEquals(listed, (detail["articles"] as List<*>).size, "detail $id articles")
        }
        assertEquals(listed, node.int("articleCount"))
        assertEquals(3, detail(IssueTimelineSeed.BRIDGE_LATEST).int("articleCount"))
    }

    @Test
    fun `연결 없는 이슈와 follow_up 순환은 그대로 따로 나온다`() {
        val ids = list("2025-10-05").map { it.long("id") }
        assertTrue(IssueTimelineSeed.CYCLE_A in ids)
        assertTrue(IssueTimelineSeed.CYCLE_B in ids)
        assertEquals(1, detail(IssueTimelineSeed.UNLINKED).int("articleCount"))
    }

    private fun list(date: String): List<Map<*, *>> =
        (fetch("/api/v1/issues?date=$date&size=50")["data"] as List<*>).map { it as Map<*, *> }

    private fun detail(id: Long): Map<*, *> = fetch("/api/v1/issues/$id")["data"] as Map<*, *>

    private fun fetch(path: String): Map<*, *> {
        val response = rest.getForEntity(path, String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode, path)
        return mapper.readValue(response.body, Map::class.java)
    }

    private fun Map<*, *>.long(key: String): Long = (this[key] as Number).toLong()

    private fun Map<*, *>.int(key: String): Int = (this[key] as Number).toInt()

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
