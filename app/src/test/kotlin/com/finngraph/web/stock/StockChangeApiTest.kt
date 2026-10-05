package com.finngraph.web.stock

import com.finngraph.support.BasePriceSeed
import com.finngraph.support.BasePriceSeed.BASED_TICKER
import com.finngraph.support.BasePriceSeed.FIRST_TICKER
import com.finngraph.support.BasePriceSeed.PLAIN_TICKER
import com.finngraph.support.BasePriceSeed.RESUMED_TICKER
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class StockChangeApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Test
    fun `기준가가 있으면 등락률은 직전 종가가 아니라 기준가 대비다`() {
        val detail = data("/api/v1/stocks/$BASED_TICKER")

        assertEquals(BasePriceSeed.DAY3, detail["baseDate"])
        assertEquals(10600.0, detail.number("price"))
        assertEquals(1.9231, detail.number("change"))
        assertEquals(200.0, detail.number("changeAmount"))
    }

    @Test
    fun `기준가가 없으면 등락률은 직전 거래일 종가 대비다`() {
        val detail = data("/api/v1/stocks/$PLAIN_TICKER")

        assertEquals(2.0, detail.number("change"))
        assertEquals(380.0, detail.number("changeAmount"))
    }

    @Test
    fun `목록의 등락률도 상세와 같은 기준이다`() {
        val listed = list("/api/v1/stocks").associateBy { it["ticker"] }

        assertEquals(1.9231, listed.getValue(BASED_TICKER).number("change"))
        assertEquals(2.0, listed.getValue(PLAIN_TICKER).number("change"))
        assertEquals(1.0, listed.getValue(RESUMED_TICKER).number("change"))
        assertEquals(200.0, listed.getValue(BASED_TICKER).number("changeAmount"))
        assertEquals(380.0, listed.getValue(PLAIN_TICKER).number("changeAmount"))
        assertEquals(300.0, listed.getValue(RESUMED_TICKER).number("changeAmount"))
    }

    @Test
    fun `변동액은 등락률과 같은 기준이고 목록과 상세가 같다`() {
        val listed = list("/api/v1/stocks").associateBy { it["ticker"] }

        listOf(BASED_TICKER, PLAIN_TICKER, RESUMED_TICKER).forEach { ticker ->
            val detail = data("/api/v1/stocks/$ticker")
            val amount = detail.number("changeAmount")
            val base = detail.number("price") - amount
            assertEquals(detail.number("change"), Math.round(amount / base * 100 * 10_000) / 10_000.0, ticker)
            assertEquals(amount, listed.getValue(ticker).number("changeAmount"), ticker)
        }
    }

    @Test
    fun `기준가도 직전 거래일 종가도 없으면 등락률과 변동액은 null이다`() {
        val detail = data("/api/v1/stocks/$FIRST_TICKER")
        val listed = list("/api/v1/stocks").single { it["ticker"] == FIRST_TICKER }

        assertEquals(5000.0, detail.number("price"))
        listOf(detail, listed).forEach { row ->
            assertTrue(row.containsKey("changeAmount"))
            assertNull(row["change"])
            assertNull(row["changeAmount"])
        }
    }

    @Test
    fun `일봉은 봉마다 기준가 우선 등락률을 주고 마지막 봉은 상세 등락률과 같다`() {
        val candles = list("/api/v1/stocks/$BASED_TICKER/candles?period=D")

        assertEquals(listOf(BasePriceSeed.DAY1, BasePriceSeed.DAY2, BasePriceSeed.DAY3), candles.map { it["date"] })
        assertTrue(candles[0].containsKey("changeRate"))
        assertNull(candles[0]["changeRate"])
        assertEquals(3.9604, candles[1].number("changeRate"))
        assertEquals(data("/api/v1/stocks/$BASED_TICKER").number("change"), candles[2].number("changeRate"))
    }

    @Test
    fun `요청 구간의 첫 봉도 구간 밖 직전 종가로 등락률을 낸다`() {
        val candles = list("/api/v1/stocks/$PLAIN_TICKER/candles?period=D&limit=2")

        assertEquals(listOf(BasePriceSeed.DAY2, BasePriceSeed.DAY3), candles.map { it["date"] })
        assertEquals(-5.0, candles[0].number("changeRate"))
        assertEquals(data("/api/v1/stocks/$PLAIN_TICKER").number("change"), candles[1].number("changeRate"))
    }

    @Test
    fun `테마 구성 종목 등락률은 종목 상세와 같고 기준가가 있으면 직전 봉이 전 거래일이 아니어도 낸다`() {
        val members = list("/api/v1/themes/${BasePriceSeed.THEME_ID}/stocks").associateBy { it["ticker"] }

        listOf(BASED_TICKER, PLAIN_TICKER, RESUMED_TICKER).forEach { ticker ->
            val member = members.getValue(ticker)
            assertTrue(member["changeStatus"] in setOf("PRICED", "TRIMMED"), ticker)
            assertEquals(data("/api/v1/stocks/$ticker").number("change"), member.number("change"), ticker)
        }
        assertEquals(1.0, members.getValue(RESUMED_TICKER).number("change"))
    }

    private fun data(path: String): Map<*, *> {
        val response = rest.getForEntity(path, Map::class.java)
        assertEquals(HttpStatus.OK, response.statusCode, path)
        return response.body!!["data"] as Map<*, *>
    }

    private fun list(path: String): List<Map<*, *>> {
        val response = rest.getForEntity(path, Map::class.java)
        assertEquals(HttpStatus.OK, response.statusCode, path)
        return (response.body!!["data"] as List<*>).map { it as Map<*, *> }
    }

    private fun Map<*, *>.number(key: String): Double = (this[key] as Number).toDouble()

    companion object {
        @JvmStatic
        @BeforeAll
        fun seed() = BasePriceSeed.seed()

        @JvmStatic
        @AfterAll
        fun cleanup() = BasePriceSeed.cleanup()

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
