package com.finngraph.web.issue

import com.finngraph.support.IssueMentionSeed
import com.finngraph.support.IssueMentionSeed.day
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
class StockIssueApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `기본 기간은 오늘부터 365일 전까지이고 마지막 보도 시각 내림차순이다`() {
        val payload = fetch("/api/v1/stocks/${IssueMentionSeed.TICKER_A}/issues")

        assertEquals(
            listOf(IssueMentionSeed.RECENT, IssueMentionSeed.MIDDLE, IssueMentionSeed.SPANNING, IssueMentionSeed.OLD),
            payload.items().map { it.long("id") },
        )
        assertEquals(mapOf("page" to 0, "size" to 20, "totalElements" to 4, "totalPages" to 1), payload["pagination"])
        assertNull(payload["meta"])
    }

    @Test
    fun `공개 기사가 기간 안에 있으면 언급 기사가 기간 밖이어도 그 종목 이슈다`() {
        val spanning = fetch("/api/v1/stocks/${IssueMentionSeed.TICKER_A}/issues").items()
            .single { it.long("id") == IssueMentionSeed.SPANNING }

        assertEquals("${day(20)}T09:00:00+09:00", spanning["lastPublishedAt"])
        assertEquals(1, spanning["mentionCount"])
        assertEquals(2, spanning["articleCount"])
    }

    @Test
    fun `from to 는 공개 기사 보도일 기준이고 양끝을 포함한다`() {
        val wide = fetch("/api/v1/stocks/${IssueMentionSeed.TICKER_A}/issues?from=${day(400)}")
        val untilThreeDaysAgo = fetch("/api/v1/stocks/${IssueMentionSeed.TICKER_A}/issues?to=${day(3)}")
        val oneDay = fetch("/api/v1/stocks/${IssueMentionSeed.TICKER_A}/issues?from=${day(1)}&to=${day(1)}")
        val empty = fetch("/api/v1/stocks/${IssueMentionSeed.TICKER_A}/issues?from=${day(2)}&to=${day(2)}")

        assertEquals(
            listOf(
                IssueMentionSeed.RECENT,
                IssueMentionSeed.MIDDLE,
                IssueMentionSeed.SPANNING,
                IssueMentionSeed.OLD,
                IssueMentionSeed.OUT_OF_RANGE,
            ),
            wide.items().map { it.long("id") },
        )
        assertEquals(
            listOf(IssueMentionSeed.MIDDLE, IssueMentionSeed.SPANNING, IssueMentionSeed.OLD),
            untilThreeDaysAgo.items().map { it.long("id") },
        )
        assertEquals(listOf(IssueMentionSeed.RECENT), oneDay.items().map { it.long("id") })
        assertEquals(emptyList<Map<*, *>>(), empty.items())
        assertEquals(mapOf("page" to 0, "size" to 20, "totalElements" to 0, "totalPages" to 0), empty["pagination"])
    }

    @Test
    fun `관계가 나온 기사가 있는 이슈는 다른 기사 언급도 세고 그런 기사가 없는 이슈는 언급돼도 뺀다`() {
        val items = fetch("/api/v1/stocks/${IssueMentionSeed.TICKER_A}/issues?from=${day(400)}").items()
            .associateBy { it.long("id") }

        assertTrue(IssueMentionSeed.UNEXTRACTED !in items)
        assertTrue(IssueMentionSeed.HIDDEN_MENTION !in items)
        assertEquals(2, items.getValue(IssueMentionSeed.RECENT)["mentionCount"])
        assertEquals(2, items.getValue(IssueMentionSeed.MIDDLE)["mentionCount"])
        assertEquals(2, items.getValue(IssueMentionSeed.MIDDLE)["articleCount"])
        assertEquals("${day(3)}T10:00:00+09:00", items.getValue(IssueMentionSeed.MIDDLE)["lastPublishedAt"])
    }

    @Test
    fun `항목은 이슈 목록 항목과 같고 mentionCount 만 더한다`() {
        val stockItem = fetch("/api/v1/stocks/${IssueMentionSeed.TICKER_A}/issues").items()
            .single { it.long("id") == IssueMentionSeed.RECENT }
        val listItem = fetch("/api/v1/issues?date=${day(0)}").items()
            .single { it.long("id") == IssueMentionSeed.RECENT }

        assertEquals(listItem, stockItem - "mentionCount")
        assertEquals("가상 최근 이슈", stockItem["title"])
        assertEquals(3, stockItem["articleCount"])
        assertEquals(3, stockItem["mediaCount"])
        assertEquals("${day(0)}T09:00:00+09:00", stockItem["lastPublishedAt"])
        assertEquals(
            listOf(
                mapOf("ticker" to IssueMentionSeed.TICKER_A, "name" to "가상종목1", "mentionCount" to 2),
                mapOf("ticker" to IssueMentionSeed.TICKER_B, "name" to "가상종목2", "mentionCount" to 2),
            ),
            stockItem["companies"],
        )
    }

    @Test
    fun `페이지는 정렬 후에 자르고 범위를 넘으면 빈 목록이다`() {
        val second = fetch("/api/v1/stocks/${IssueMentionSeed.TICKER_A}/issues?size=2&page=1")
        val beyond = fetch("/api/v1/stocks/${IssueMentionSeed.TICKER_A}/issues?size=2&page=9")

        assertEquals(listOf(IssueMentionSeed.SPANNING, IssueMentionSeed.OLD), second.items().map { it.long("id") })
        assertEquals(mapOf("page" to 1, "size" to 2, "totalElements" to 4, "totalPages" to 2), second["pagination"])
        assertEquals(emptyList<Map<*, *>>(), beyond.items())
        assertEquals(mapOf("page" to 9, "size" to 2, "totalElements" to 4, "totalPages" to 2), beyond["pagination"])
    }

    @Test
    fun `없는 종목이나 비활성 종목은 종목 뉴스처럼 404`() {
        listOf(IssueMentionSeed.TICKER_UNKNOWN, IssueMentionSeed.TICKER_INACTIVE).forEach { ticker ->
            val issues = rest.getForEntity("/api/v1/stocks/$ticker/issues", String::class.java)
            val news = rest.getForEntity("/api/v1/stocks/$ticker/news", String::class.java)
            assertEquals(HttpStatus.NOT_FOUND, issues.statusCode, ticker)
            assertEquals(news.statusCode, issues.statusCode, ticker)
            assertTrue(issues.body!!.contains(ErrorCode.STOCK_NOT_FOUND), ticker)
        }
    }

    @Test
    fun `종목 이슈 파라미터가 올바르지 않으면 400`() {
        val base = "/api/v1/stocks/${IssueMentionSeed.TICKER_A}/issues"
        mapOf(
            "$base?from=2030-13-01" to "from",
            "$base?from=+2030-01-01" to "from",
            "$base?to=20300101" to "to",
            "$base?from=2030-03-02&to=2030-03-01" to "to",
            "$base?page=-1" to "page",
            "$base?size=0" to "size",
            "$base?size=101" to "size",
            "/api/v1/stocks/${"9".repeat(21)}/issues" to "ticker",
        ).forEach { (path, field) ->
            val response = rest.getForEntity(path, String::class.java)
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, path)
            assertTrue(response.body!!.contains(ErrorCode.INVALID_PARAMETER), path)
            assertTrue(response.body!!.contains("\"$field\""), path)
        }
        listOf("$base?size=100", "$base?from=&to=", "$base?from=2030-03-01&to=2030-03-01").forEach { path ->
            assertEquals(HttpStatus.OK, rest.getForEntity(path, String::class.java).statusCode, path)
        }
    }

    @Test
    fun `최근 이슈는 종목마다 최근 30일 안에서 마지막 보도가 가장 늦은 이슈 하나다`() {
        val tickers = listOf(
            IssueMentionSeed.TICKER_A,
            IssueMentionSeed.TICKER_B,
            IssueMentionSeed.TICKER_C,
            IssueMentionSeed.TICKER_INACTIVE,
            IssueMentionSeed.TICKER_OLD,
            IssueMentionSeed.TICKER_UNKNOWN,
            IssueMentionSeed.TICKER_A,
        )
        val data = latest(tickers.joinToString(","))

        assertEquals(
            listOf(
                IssueMentionSeed.TICKER_A,
                IssueMentionSeed.TICKER_B,
                IssueMentionSeed.TICKER_C,
                IssueMentionSeed.TICKER_INACTIVE,
                IssueMentionSeed.TICKER_OLD,
                IssueMentionSeed.TICKER_UNKNOWN,
            ),
            data.map { it["ticker"] },
        )
        val issues = data.associate { it["ticker"] to it["issue"] as Map<*, *>? }
        assertEquals(IssueMentionSeed.RECENT, issues.getValue(IssueMentionSeed.TICKER_A)!!.long("id"))
        assertEquals(2, issues.getValue(IssueMentionSeed.TICKER_A)!!["mentionCount"])
        assertEquals(IssueMentionSeed.RECENT, issues.getValue(IssueMentionSeed.TICKER_B)!!.long("id"))
        assertEquals(2, issues.getValue(IssueMentionSeed.TICKER_B)!!["mentionCount"])
        assertEquals(IssueMentionSeed.HIDDEN_MENTION, issues.getValue(IssueMentionSeed.TICKER_C)!!.long("id"))
        assertNull(issues.getValue(IssueMentionSeed.TICKER_INACTIVE))
        assertNull(issues.getValue(IssueMentionSeed.TICKER_OLD))
        assertNull(issues.getValue(IssueMentionSeed.TICKER_UNKNOWN))
        assertTrue(data.all { it.containsKey("issue") })
    }

    @Test
    fun `최근 이슈 항목은 종목 이슈 항목과 같다`() {
        val fromLatest = latest(IssueMentionSeed.TICKER_A).single()["issue"] as Map<*, *>
        val fromPage = fetch("/api/v1/stocks/${IssueMentionSeed.TICKER_A}/issues").items().first()

        assertEquals(fromPage, fromLatest)
    }

    @Test
    fun `최근 30일은 오늘 − 30일 KST 자정을 포함하고 그 전날은 뺀다`() {
        val data = latest("${IssueMentionSeed.TICKER_EDGE_IN},${IssueMentionSeed.TICKER_EDGE_OUT}")

        assertEquals(IssueMentionSeed.EDGE_IN, (data[0]["issue"] as Map<*, *>).long("id"))
        assertNull(data[1]["issue"])
    }

    @Test
    fun `tickers 가 없거나 50개를 넘거나 20자를 넘으면 400`() {
        mapOf(
            "" to "tickers",
            "?tickers=" to "tickers",
            "?tickers=,, ," to "tickers",
            "?tickers=${(1..51).joinToString(",") { "%06d".format(it) }}" to "tickers",
            "?tickers=${"9".repeat(21)}" to "tickers",
        ).forEach { (query, field) ->
            val response = rest.getForEntity("/api/v1/stocks/issues/latest$query", String::class.java)
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, query)
            assertTrue(response.body!!.contains(ErrorCode.INVALID_PARAMETER), query)
            assertTrue(response.body!!.contains("\"$field\""), query)
        }
        val fifty = (1..50).joinToString(",") { "%06d".format(it) }
        val withDuplicates = "$fifty,000001,000002"
        assertEquals(50, latest(fifty).size)
        assertEquals(50, latest(withDuplicates).size)
    }

    private fun fetch(path: String): Map<*, *> {
        val response = rest.getForEntity(path, String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode, path)
        return mapper.readValue(response.body, Map::class.java)
    }

    private fun latest(tickers: String): List<Map<*, *>> =
        fetch("/api/v1/stocks/issues/latest?tickers=$tickers").items()

    private fun Map<*, *>.items(): List<Map<*, *>> = (this["data"] as List<*>).map { it as Map<*, *> }

    private fun Map<*, *>.long(key: String): Long = (this[key] as Number).toLong()

    companion object {
        @JvmStatic
        @BeforeAll
        fun seed() = IssueMentionSeed.seed()

        @JvmStatic
        @AfterAll
        fun cleanup() = IssueMentionSeed.cleanup()

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
