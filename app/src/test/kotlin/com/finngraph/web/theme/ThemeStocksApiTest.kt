package com.finngraph.web.theme

import com.finngraph.support.HotThemeSeed
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ThemeStocksApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `구성 종목 표는 절사·정지·정리매매 상태를 달고 정리매매는 맨 뒤다`() {
        val stocks = stocks(HotThemeSeed.SURGE_THEME)

        assertEquals(
            listOf("909101", "909102", "909103", "909104", "909105", "909107", "909108", "909109"),
            stocks.map { it["ticker"] },
        )
        assertEquals(
            listOf("TRIMMED", "PRICED", "PRICED", "PRICED", "PRICED", "TRIMMED", "SUSPENDED", "DELISTING"),
            stocks.map { it["changeStatus"] },
        )
        assertEquals(3.0, (stocks[0]["change"] as Number).toDouble())
        assertEquals(8.0, (stocks[5]["change"] as Number).toDouble())
        assertNull(stocks[6]["change"])
        assertEquals(true, stocks[6]["tradingSuspended"])
        assertNull(stocks[7]["change"])
        assertEquals(true, stocks[7]["delistingTrade"])
        assertEquals(false, stocks[7]["underAdministration"])
        assertEquals(80.0, (stocks[7]["price"] as Number).toDouble())
    }

    @Test
    fun `구성 종목은 종목별 1주·1달·3달 수익률을 원값으로 내려주고 없으면 null이다`() {
        val byTicker = stocks(HotThemeSeed.SURGE_THEME).associateBy { it["ticker"] }

        val filled = byTicker.getValue("909101")
        assertEquals(4.5, (filled["r1w"] as Number).toDouble())
        assertEquals(-2.25, (filled["r1m"] as Number).toDouble())
        assertEquals(12.0, (filled["r3m"] as Number).toDouble())

        val empty = byTicker.getValue("909102")
        assertEquals(true, empty.containsKey("r1w"))
        assertNull(empty["r1w"])
        assertNull(empty["r1m"])
        assertNull(empty["r3m"])
    }

    @Test
    fun `부분 적재일 캔들은 전 거래일로 인정하지 않아 NO_PREV가 된다`() {
        val statuses = stocks(HotThemeSeed.SPARSE_THEME).associate { it["ticker"] to it["changeStatus"] }

        assertEquals("NO_CANDLE", statuses["909127"])
        assertEquals("NO_PREV", statuses["909128"])
        assertEquals("NO_PREV", statuses["909129"])
        assertEquals("PRICED", statuses["909123"])
    }

    @Test
    fun `목록은 전 테마에 hotSide를 채우고 결손 37점5퍼센트는 제외 30퍼센트는 통과한다`() {
        val response = rest.getForEntity("/api/v1/themes", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        val payload = mapper.readValue(response.body!!, Map::class.java)
        val themes = (payload["data"] as List<*>).map { it as Map<*, *> }.associateBy { it["name"] as String }

        assertEquals("UP", themes.getValue("시드급등테마")["hotSide"])
        assertEquals("UP", themes.getValue("시드경계테마")["hotSide"])
        assertEquals("DOWN", themes.getValue("시드하락테마")["hotSide"])
        assertNull(themes.getValue("시드결손테마")["hotSide"])
        assertNull(themes.getValue("시드쏠림테마")["hotSide"])
        assertNull(themes.getValue("시드완만테마")["hotSide"])

        val sparse = themes.getValue("시드결손테마")
        assertEquals(8, sparse["stockCount"])
        assertEquals(5, sparse["pricedCount"])
        assertEquals(5.0, (sparse["change"] as Number).toDouble())

        val skewed = themes.getValue("시드쏠림테마")
        assertEquals(0.8333, (skewed["change"] as Number).toDouble())
        assertEquals(2, skewed["upCount"])
        assertEquals(3, skewed["downCount"])

        val mild = themes.getValue("시드완만테마")
        assertEquals(1, mild["pricedCount"])
        assertNull(mild["change"])
        assertEquals(0, mild["trimCount"])
    }

    @Test
    fun `상세도 hotSide와 집계 필드를 내려준다`() {
        val response = rest.getForEntity("/api/v1/themes/${HotThemeSeed.FALL_THEME}", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        val data = mapper.readValue(response.body!!, Map::class.java)["data"] as Map<*, *>

        assertEquals("DOWN", data["hotSide"])
        assertEquals(5, data["pricedCount"])
        assertEquals(-3.8259, (data["changeUpper"] as Number).toDouble())
        assertEquals(listOf("judal"), data["sources"])
    }

    @Test
    fun `상세의 weightedChange는 기준일 지수 종가를 직전 지수 종가와 비교한 값이고 절사평균 change와 다르다`() {
        val response = rest.getForEntity("/api/v1/themes/${HotThemeSeed.FALL_THEME}", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        val data = mapper.readValue(response.body!!, Map::class.java)["data"] as Map<*, *>

        assertEquals(-4.5679, (data["weightedChange"] as Number).toDouble())
        assertEquals(-4.6667, (data["change"] as Number).toDouble())
    }

    @Test
    fun `목록도 weightedChange를 내려주고 직전 지수 행이 비면 그 앞 행과 비교하며 지수가 없으면 null이다`() {
        val response = rest.getForEntity("/api/v1/themes", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        val payload = mapper.readValue(response.body!!, Map::class.java)
        val themes = (payload["data"] as List<*>).map { it as Map<*, *> }.associateBy { it["name"] as String }

        assertEquals(4.321, (themes.getValue("시드급등테마")["weightedChange"] as Number).toDouble())
        assertEquals(3.6, (themes.getValue("시드경계테마")["weightedChange"] as Number).toDouble())
        assertEquals(5.0, (themes.getValue("시드결손테마")["weightedChange"] as Number).toDouble())
        val mild = themes.getValue("시드완만테마")
        assertTrue(mild.containsKey("weightedChange"))
        assertNull(mild["weightedChange"])
    }

    private fun stocks(themeId: Long): List<Map<*, *>> {
        val response = rest.getForEntity("/api/v1/themes/$themeId/stocks", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        val payload = mapper.readValue(response.body!!, Map::class.java)
        return (payload["data"] as List<*>).map { it as Map<*, *> }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun seed() {
            HotThemeSeed.seed()
            HotThemeSeed.seedPeriodReturns(9101, "4.5", "-2.25", "12.0")
        }

        @JvmStatic
        @AfterAll
        fun cleanup() = HotThemeSeed.cleanup()

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
