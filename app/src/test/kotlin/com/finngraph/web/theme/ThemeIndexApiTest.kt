package com.finngraph.web.theme

import com.finngraph.support.HotThemeSeed
import com.finngraph.support.TestContainers
import com.finngraph.support.ThemeIndexSeed
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ThemeIndexApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Test
    fun `일봉은 기준일까지 오름차순으로 주고 기준일 뒤의 장중 행은 뺀다`() {
        val candles = candles(ThemeIndexSeed.INDEXED_THEME, "period=D&limit=3")

        assertEquals(listOf("2026-07-29", "2026-07-30", "2026-07-31"), candles.map { it["date"] })
        val last = candles.last()
        assertEquals(1205.0, last.number("open"))
        assertEquals(1270.0, last.number("high"))
        assertEquals(1195.0, last.number("low"))
        assertEquals(1260.0, last.number("close"))
        assertEquals(140.0, last.number("volume"))
        assertEquals(1400.0, last.number("tradeValue"))
    }

    @Test
    fun `주기를 생략하면 일봉 기본 개수 안에서 기준일까지 전부 준다`() {
        val candles = candles(ThemeIndexSeed.INDEXED_THEME, "")

        assertEquals(10, candles.size)
        assertEquals("2025-07-30", candles.first()["date"])
        assertEquals("2026-07-31", candles.last()["date"])
    }

    @Test
    fun `주봉은 주 월요일 단위로 일봉을 묶는다`() {
        val weeks = candles(ThemeIndexSeed.INDEXED_THEME, "period=W&limit=2")

        assertEquals(listOf("2026-07-20", "2026-07-27"), weeks.map { it["date"] })
        val week = weeks.last()
        assertEquals(1105.0, week.number("open"))
        assertEquals(1270.0, week.number("high"))
        assertEquals(1100.0, week.number("low"))
        assertEquals(1260.0, week.number("close"))
        assertEquals(500.0, week.number("volume"))
        assertEquals(5000.0, week.number("tradeValue"))
    }

    @Test
    fun `월봉은 월 1일 단위로 묶고 기준일 뒤의 달은 만들지 않는다`() {
        val months = candles(ThemeIndexSeed.INDEXED_THEME, "period=M&limit=2")

        assertEquals(listOf("2026-06-01", "2026-07-01"), months.map { it["date"] })
        val month = months.last()
        assertEquals(1090.0, month.number("open"))
        assertEquals(1270.0, month.number("high"))
        assertEquals(1085.0, month.number("low"))
        assertEquals(1260.0, month.number("close"))
        assertEquals(600.0, month.number("volume"))
    }

    @Test
    fun `지수 성과는 기준일 종가로 기간 수익률과 52주 고점 대비를 낸다`() {
        val index = assertNotNull(index(ThemeIndexSeed.INDEXED_THEME))

        assertEquals(HotThemeSeed.BASE_DATE, index["date"])
        assertEquals(1260.0, index.number("close"))
        assertEquals(5.0, index.number("change"))
        assertEquals(14.5455, index.number("r1w"))
        assertEquals(26.0, index.number("r1m"))
        assertEquals(32.6316, index.number("r3m"))
        assertEquals(57.5, index.number("r1y"))
        assertEquals(40.0, index.number("ytd"))
        assertEquals(1300.0, index.number("high52w"))
        assertEquals(900.0, index.number("low52w"))
        assertEquals(-3.0769, index.number("fromHigh52w"))
        assertEquals(2.0, index.number("streak"))
    }

    @Test
    fun `기준일 지수가 없는 테마는 성과가 null이고 캔들은 있는 데까지 준다`() {
        assertNull(index(ThemeIndexSeed.LAGGING_THEME))
        assertEquals(
            listOf("2026-07-29", "2026-07-30"),
            candles(ThemeIndexSeed.LAGGING_THEME, "period=D").map { it["date"] },
        )
    }

    @Test
    fun `지수가 없는 테마는 빈 캔들과 null 성과를 준다`() {
        assertTrue(candles(ThemeIndexSeed.BARE_THEME, "period=W").isEmpty())
        assertNull(index(ThemeIndexSeed.BARE_THEME))
    }

    @Test
    fun `테마 요약의 weightedChange는 지수 성과의 change와 같은 값이다`() {
        val index = assertNotNull(index(ThemeIndexSeed.INDEXED_THEME))
        val detail = summary(ThemeIndexSeed.INDEXED_THEME)
        val listed = list().single { it["id"] == ThemeIndexSeed.INDEXED_THEME.toInt() }

        assertEquals(5.0, index.number("change"))
        assertEquals(index.number("change"), detail.number("weightedChange"))
        assertEquals(index.number("change"), listed.number("weightedChange"))
        assertNull(index(ThemeIndexSeed.LAGGING_THEME))
        assertNull(summary(ThemeIndexSeed.LAGGING_THEME)["weightedChange"])
        assertNull(summary(ThemeIndexSeed.BARE_THEME)["weightedChange"])
    }

    @Test
    fun `없는 테마는 404다`() {
        assertEquals(HttpStatus.NOT_FOUND, get("/api/v1/themes/999999/candles").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, get("/api/v1/themes/999999/index").statusCode)
    }

    @Test
    fun `캔들 파라미터 검증은 종목 캔들과 같은 규칙과 응답을 쓴다`() {
        listOf("period=X", "limit=0", "limit=2501", "period=Y&limit=600").forEach { query ->
            val theme = get("/api/v1/themes/${ThemeIndexSeed.INDEXED_THEME}/candles?$query")
            val stock = get("/api/v1/stocks/000000/candles?$query")

            assertEquals(HttpStatus.BAD_REQUEST, theme.statusCode, query)
            assertEquals(HttpStatus.BAD_REQUEST, stock.statusCode, query)
            assertEquals("INVALID_PARAMETER", theme.error()["code"], query)
            assertEquals(stock.error()["details"], theme.error()["details"], query)
        }
    }

    @Test
    fun `id가 양수가 아니면 400이다`() {
        assertEquals(HttpStatus.BAD_REQUEST, get("/api/v1/themes/0/candles").statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, get("/api/v1/themes/0/index").statusCode)
    }

    private fun candles(id: Long, query: String): List<Map<*, *>> {
        val response = get("/api/v1/themes/$id/candles?$query")
        assertEquals(HttpStatus.OK, response.statusCode, query)
        return (response.body!!["data"] as List<*>).map { it as Map<*, *> }
    }

    private fun index(id: Long): Map<*, *>? {
        val response = get("/api/v1/themes/$id/index")
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.containsKey("data"))
        return response.body!!["data"] as Map<*, *>?
    }

    private fun summary(id: Long): Map<*, *> {
        val response = get("/api/v1/themes/$id")
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body!!["data"] as Map<*, *>
    }

    private fun list(): List<Map<*, *>> {
        val response = get("/api/v1/themes")
        assertEquals(HttpStatus.OK, response.statusCode)
        return (response.body!!["data"] as List<*>).map { it as Map<*, *> }
    }

    private fun get(path: String): ResponseEntity<Map<*, *>> = rest.getForEntity(path, Map::class.java)

    private fun ResponseEntity<Map<*, *>>.error(): Map<*, *> = body!!["error"] as Map<*, *>

    private fun Map<*, *>.number(key: String): Double = (this[key] as Number).toDouble()

    companion object {
        @JvmStatic
        @BeforeAll
        fun seed() {
            HotThemeSeed.seed()
            ThemeIndexSeed.seed()
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {
            ThemeIndexSeed.cleanup()
            HotThemeSeed.cleanup()
        }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
