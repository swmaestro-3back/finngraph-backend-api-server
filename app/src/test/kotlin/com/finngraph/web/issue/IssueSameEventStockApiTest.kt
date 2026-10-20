package com.finngraph.web.issue

import com.finngraph.support.IssueSameEventSeed
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class IssueSameEventStockApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `종목 이슈 목록은 same_event 묶음을 대표 하나로 내고 언급 수를 묶음 전체로 센다`() {
        val items = list(fetch("/api/v1/stocks/${IssueSameEventSeed.TICKER}/issues")["data"])

        assertEquals(listOf(IssueSameEventSeed.LEAD, IssueSameEventSeed.SOLO, IssueSameEventSeed.ROOT), items.map { it.long("id") })
        val lead = items.first()
        assertEquals(4, lead.int("articleCount"))
        assertEquals(4, lead.int("mentionCount"))
        assertEquals(4, detail(IssueSameEventSeed.TWIN).int("articleCount"))
    }

    @Test
    fun `종목 최근 이슈는 묶음 중 늦게 보도된 이슈가 아니라 사건 대표로 낸다`() {
        val latest = list(fetch("/api/v1/stocks/issues/latest?tickers=${IssueSameEventSeed.TICKER}")["data"]).single()
        val issue = latest["issue"] as Map<*, *>

        assertEquals(IssueSameEventSeed.LEAD, issue.long("id"))
        assertEquals(4, issue.int("articleCount"))
    }

    @Test
    fun `종목 이슈 흐름은 same_event 묶음을 한 단계로 센다`() {
        val flow = list(fetch("/api/v1/stocks/${IssueSameEventSeed.TICKER}/issue-flows")["data"]).single()

        assertEquals(IssueSameEventSeed.ROOT, flow.long("id"))
        assertEquals(2, flow.int("issueCount"))
        assertEquals(listOf(IssueSameEventSeed.ROOT, IssueSameEventSeed.LEAD), list(flow["issues"]).map { it.long("id") })
    }

    @Test
    fun `테마 이슈 보드는 같은 사건을 한 번만 세고 대표로 낸다`() {
        val board = fetch("/api/v1/themes/issues?ids=${IssueSameEventSeed.THEME_ID}&date=${IssueSameEventSeed.EVENT_DAY}")
        val theme = list(board["data"]).single()

        assertEquals(2, theme.int("issueCount"))
        assertEquals(listOf(IssueSameEventSeed.LEAD, IssueSameEventSeed.SOLO), (theme["issueIds"] as List<*>).map { (it as Number).toLong() })
        assertEquals(4, list(theme["issues"]).first().int("articleCount"))
    }

    @Test
    fun `이슈 검색은 같은 사건 둘이 걸려도 대표 하나만 낸다`() {
        val issues = list((fetch("/api/v1/search?q=${IssueSameEventSeed.QUERY}")["data"] as Map<*, *>)["issues"])

        assertEquals(listOf(IssueSameEventSeed.LEAD), issues.map { it.long("id") })
        assertEquals(4, issues.single().int("articleCount"))
    }

    private fun detail(id: Long): Map<*, *> = fetch("/api/v1/issues/$id")["data"] as Map<*, *>

    private fun fetch(path: String): Map<*, *> {
        val response = rest.getForEntity(path, String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode, path)
        return mapper.readValue(response.body, Map::class.java)
    }

    private fun list(value: Any?): List<Map<*, *>> = (value as List<*>).map { it as Map<*, *> }

    private fun Map<*, *>.long(key: String): Long = (this[key] as Number).toLong()

    private fun Map<*, *>.int(key: String): Int = (this[key] as Number).toInt()

    companion object {
        @JvmStatic
        @BeforeAll
        fun seed() = IssueSameEventSeed.seed()

        @JvmStatic
        @AfterAll
        fun cleanup() = IssueSameEventSeed.cleanup()

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
