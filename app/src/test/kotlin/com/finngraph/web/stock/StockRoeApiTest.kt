package com.finngraph.web.stock

import com.finngraph.support.FinancialsSeed
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
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class StockRoeApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `최신 연간 행이 DART 면 같은 해 KIS ROE 를 상세에 내려준다`() {
        val data = data("/api/v1/stocks/${FinancialsSeed.MIXED_TICKER}") as Map<*, *>

        assertDecimal(FinancialsSeed.KIS_ROE_2025, data["roe"])
    }

    @Test
    fun `목록도 상세와 같은 ROE 를 내려준다`() {
        val rows = data("/api/v1/stocks") as List<*>
        val row = rows.map { it as Map<*, *> }.single { it["ticker"] == FinancialsSeed.MIXED_TICKER }

        assertDecimal(FinancialsSeed.KIS_ROE_2025, row["roe"])
    }

    @Test
    fun `같은 해 KIS ROE 가 없으면 이전 해 값을 끌어오지 않는다`() {
        val data = data("/api/v1/stocks/${FinancialsSeed.STALE_TICKER}") as Map<*, *>

        assertNull(data["roe"])
    }

    @Test
    fun `연간 재무는 연도별로 같은 해 KIS ROE 를 쓰고 나머지 수치는 DART 행을 유지한다`() {
        val years = annual(FinancialsSeed.MIXED_TICKER)

        assertDecimal(FinancialsSeed.KIS_ROE_2025, years.getValue(2025)["roe"])
        assertEquals(FinancialsSeed.DART_REVENUE_2025, (years.getValue(2025)["revenue"] as Number).toLong())
        assertDecimal(FinancialsSeed.KIS_ROE_2022, years.getValue(2022)["roe"])
    }

    @Test
    fun `연간 재무에서 KIS 가 없는 해의 ROE 는 비운다`() {
        val years = annual(FinancialsSeed.STALE_TICKER)

        assertDecimal(FinancialsSeed.KIS_ROE_2024_STALE, years.getValue(2024)["roe"])
        assertNull(years.getValue(2025)["roe"])
    }

    private fun annual(ticker: String): Map<Int, Map<*, *>> =
        (data("/api/v1/stocks/$ticker/financials") as List<*>)
            .map { it as Map<*, *> }
            .associateBy { (it["year"] as Number).toInt() }

    private fun data(path: String): Any? {
        val response = rest.getForEntity(path, String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode, path)
        return mapper.readValue(response.body!!, Map::class.java)["data"]
    }

    private fun assertDecimal(expected: String, actual: Any?) {
        assertNotNull(actual, "roe expected $expected but was null")
        assertEquals(0, BigDecimal(expected).compareTo(BigDecimal(actual.toString())), "$expected vs $actual")
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun seed() = FinancialsSeed.seed()

        @JvmStatic
        @AfterAll
        fun cleanup() = FinancialsSeed.cleanup()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
