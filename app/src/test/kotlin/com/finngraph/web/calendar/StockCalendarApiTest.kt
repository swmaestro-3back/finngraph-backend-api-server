package com.finngraph.web.calendar

import com.finngraph.support.StockCalendarSeed
import com.finngraph.support.StockCalendarSeed.BONUS_TICKER
import com.finngraph.support.StockCalendarSeed.DIVIDEND_TICKER
import com.finngraph.support.StockCalendarSeed.INACTIVE_TICKER
import com.finngraph.support.StockCalendarSeed.RIGHTS_TICKER
import com.finngraph.support.StockCalendarSeed.YEAR
import com.finngraph.support.TestContainers
import com.finngraph.web.common.ErrorCode
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockCalendarApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @BeforeAll
    fun seed() = StockCalendarSeed.seed()

    @AfterAll
    fun cleanup() = StockCalendarSeed.cleanup()

    @Test
    fun `DH-01 배당락 반응을 최신 기준일순으로 주고 계산할 수 없는 배당은 뺀다`() {
        val rows = dividends(DIVIDEND_TICKER)

        assertEquals(listOf("2027-03-26", "2027-01-29"), rows.map { it["recordDate"] })
        val pending = rows[0]
        assertEquals("분기", pending["kind"])
        assertEquals("2027-03-25", pending["exDate"])
        assertEquals(10000.0, pending["prevClose"].num())
        assertEquals(9950.0, pending["exOpen"].num())
        assertEquals(1.0, pending["theoreticalDrop"].num())
        assertEquals(-0.5, pending["openGap"].num())
        assertNull(pending["recoveryDays"])
        assertEquals(true, pending["pending"])
        val recovered = rows[1]
        assertEquals("결산", recovered["kind"])
        assertEquals(500.0, recovered["dps"].num())
        assertEquals("2027-01-28", recovered["exDate"])
        assertEquals(9600.0, recovered["exOpen"].num())
        assertEquals(5.0, recovered["theoreticalDrop"].num())
        assertEquals(-4.0, recovered["openGap"].num())
        assertEquals(3, recovered["recoveryDays"])
        assertEquals(false, recovered["pending"])
    }

    @Test
    fun `DH-02 없는 종목과 비활성 종목의 배당락 반응은 404`() {
        listOf("999999", INACTIVE_TICKER).forEach { ticker ->
            val response = rest.getForEntity("/api/v1/stocks/$ticker/dividends", Map::class.java)
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode, ticker)
            assertEquals(ErrorCode.STOCK_NOT_FOUND, response.errorCode(), ticker)
        }
    }

    @Test
    fun `SC-01 기준일 범위 안의 일정을 권리 단위로 묶어 첫 단계 날짜순으로 주고 범위 밖 기준일은 뺀다`() {
        val data = calendar(DIVIDEND_TICKER)

        assertEquals(DIVIDEND_TICKER, data["ticker"])
        assertEquals("캘린더배당", data["stockName"])
        assertEquals("KOSPI", data["market"])
        assertEquals(9900.0, data["price"].num())
        assertEquals("2027-03-31", data["priceDate"])
        assertEquals("2027-01-01", data["from"])
        assertEquals("2027-12-31", data["to"])
        assertEquals(
            listOf("AGM" to "2027-02-15", "DIV" to "2027-03-26", "DIV" to "2027-04-30", "AGM" to "2027-06-15"),
            data.actions().map { it["family"] to it["basisDate"] },
        )
    }

    @Test
    fun `SC-02 배당 묶음은 단계와 매수 마감일과 이번 배당 수익률을 준다`() {
        val div = action(DIVIDEND_TICKER, "2027-03-26")

        assertEquals("분기", div["label"])
        assertEquals(
            listOf("DIV_EX" to "2027-03-25", "DIV_RECORD" to "2027-03-26", "DIV_PAY" to "2027-04-20"),
            div.steps(),
        )
        assertEquals("2027-03-24", div["lastBuyDate"])
        assertEquals(false, div["lastBuyEstimated"])
        val dividend = div["dividend"] as Map<*, *>
        assertEquals(100.0, dividend["dps"].num())
        assertEquals("CURRENT", dividend["dpsBasis"])
        assertEquals(1.0101, dividend["expectedYield"].num())
        assertNull(div["rights"])
        assertNull(div["bonus"])
    }

    @Test
    fun `SC-03 금액이 없는 배당은 같은 종류의 직전 배당금으로 수익률을 계산한다`() {
        val div = action(DIVIDEND_TICKER, "2027-04-30")

        assertEquals("2027-04-28", div["lastBuyDate"])
        val dividend = div["dividend"] as Map<*, *>
        assertEquals(500.0, dividend["dps"].num())
        assertEquals("PREVIOUS", dividend["dpsBasis"])
        assertEquals(5.0505, dividend["expectedYield"].num())
    }

    @Test
    fun `SC-04 주총은 기준일로 매수 마감일을 내고 안건에 태그를 붙인다`() {
        val agm = action(DIVIDEND_TICKER, "2027-02-15")

        assertEquals(listOf("AGM" to "2027-03-10"), agm.steps())
        assertEquals("2027-02-11", agm["lastBuyDate"])
        assertEquals(false, agm["lastBuyEstimated"])
        assertEquals(
            listOf(
                mapOf("text" to "합병승인", "tags" to listOf("합병")),
                mapOf("text" to "사내이사 선임", "tags" to emptyList<String>()),
            ),
            agm["agenda"],
        )
        assertEquals(false, agm["agendaTruncated"])
        assertNull(agm["dividend"])
    }

    @Test
    fun `SC-05 휴장일 정보가 없는 기준일은 평일 규칙으로 추정한다`() {
        val agm = action(DIVIDEND_TICKER, "2027-06-15")

        assertEquals("2027-06-11", agm["lastBuyDate"])
        assertEquals(true, agm["lastBuyEstimated"])
    }

    @Test
    fun `SC-06 유상증자는 권리락 전날 종가로 이론가를 확정하고 희석률과 발행가 대비 현재가를 준다`() {
        val action = action(RIGHTS_TICKER, "2027-02-17")

        assertEquals(
            listOf("RIGHTS_EX" to "2027-02-16", "RIGHTS_SUBSCRIBE" to "2027-03-15", "RIGHTS_LIST" to "2027-04-07"),
            action.steps(),
        )
        assertEquals("2027-03-16", (action["steps"] as List<*>).map { it as Map<*, *> }[1]["endDate"])
        val rights = action["rights"] as Map<*, *>
        assertEquals(20.0, rights["dilution"].num())
        assertEquals(8000.0, rights["issuePrice"].num())
        assertEquals(25.0, rights["priceVsIssue"].num())
        val exPrice = rights["exPrice"] as Map<*, *>
        assertEquals(10400.0, exPrice["theoretical"].num())
        assertEquals("PREVIOUS_CLOSE", exPrice["basis"])
        assertEquals(10300.0, exPrice["actualOpen"].num())
    }

    @Test
    fun `SC-07 무상증자는 이론가와 권리락 후 5·20거래일 수익률을 준다`() {
        val bonus = action(BONUS_TICKER, "2027-02-03")["bonus"] as Map<*, *>

        val exPrice = bonus["exPrice"] as Map<*, *>
        assertEquals(10000.0, exPrice["theoretical"].num())
        assertEquals("PREVIOUS_CLOSE", exPrice["basis"])
        assertEquals(10.0, bonus["returnAfter5"].num())
        assertEquals(0.0, bonus["returnAfter20"].num())
    }

    @Test
    fun `SC-08 기간은 기준일 양끝 포함 366일까지이고 오류는 400, 없는 종목과 비활성 종목은 404`() {
        assertEquals(
            HttpStatus.OK,
            rest.getForEntity("/api/v1/stocks/$DIVIDEND_TICKER/calendar?from=2027-01-01&to=2028-01-01", Map::class.java).statusCode,
        )
        listOf("?from=2027-01-01&to=2028-01-02", "?from=2027-01-01", "?from=2027-02-01&to=2027-01-01", "?from=2027-02-30&to=2027-03-01", "").forEach { query ->
            val response = rest.getForEntity("/api/v1/stocks/$DIVIDEND_TICKER/calendar$query", Map::class.java)
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, query)
            assertEquals(ErrorCode.INVALID_PARAMETER, response.errorCode(), query)
        }
        listOf("999999", INACTIVE_TICKER).forEach { ticker ->
            val response = rest.getForEntity("/api/v1/stocks/$ticker/calendar$YEAR", Map::class.java)
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode, ticker)
            assertEquals(ErrorCode.STOCK_NOT_FOUND, response.errorCode(), ticker)
        }
    }

    @Test
    fun `SC-09 일정이 없는 종목은 빈 묶음 목록과 시세를 준다`() {
        val data = (rest.getForEntity("/api/v1/stocks/$BONUS_TICKER/calendar?from=2027-06-01&to=2027-06-30", Map::class.java)
            .body!!["data"] as Map<*, *>)

        assertEquals(emptyList<Any>(), data["actions"])
        assertEquals(10000.0, data["price"].num())
        assertEquals("2027-03-31", data["priceDate"])
    }

    private fun calendar(ticker: String): Map<*, *> {
        val response = rest.getForEntity("/api/v1/stocks/$ticker/calendar$YEAR", Map::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body!!["data"] as Map<*, *>
    }

    private fun action(ticker: String, basisDate: String): Map<*, *> =
        calendar(ticker).actions().first { it["basisDate"] == basisDate }

    private fun Map<*, *>.actions(): List<Map<*, *>> = (this["actions"] as List<*>).map { it as Map<*, *> }

    private fun Map<*, *>.steps(): List<Pair<Any?, Any?>> =
        (this["steps"] as List<*>).map { it as Map<*, *> }.map { it["kind"] to it["date"] }

    private fun dividends(ticker: String): List<Map<*, *>> {
        val response = rest.getForEntity("/api/v1/stocks/$ticker/dividends", Map::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        return (response.body!!["data"] as List<*>).map { it as Map<*, *> }
    }

    private fun Any?.num(): Double = (this as Number).toDouble()

    private fun ResponseEntity<Map<*, *>>.errorCode(): String = (body!!["error"] as Map<*, *>)["code"] as String

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
