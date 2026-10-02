package com.finngraph.web.calendar

import com.finngraph.support.IpoSeed
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
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IpoApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    private val today: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))

    @BeforeAll
    fun seed() = IpoSeed.seed(today)

    @AfterAll
    fun cleanup() = IpoSeed.cleanup()

    @Test
    fun `IB-01 보드는 연결 신고서를 예탁원 카드로 합치고 연결 안 된 신고서는 청약 시작 오늘~60일만 FILED로 더한다`() {
        assertEquals(
            mapOf(
                IpoSeed.LINKED_TICKER to "UPCOMING",
                IpoSeed.BEYOND_TICKER to "UPCOMING",
                IpoSeed.KSD_ONLY_TICKER to "LISTED",
                IpoSeed.FILED_CORP to "FILED",
                IpoSeed.SPAC_CORP to "FILED",
                IpoSeed.EDGE_CORP to "FILED",
            ),
            board().mapValues { it.value["status"] },
        )
    }

    @Test
    fun `IB-02 FILED 카드는 ticker 없이 신고서 일정과 예정가와 대표 주관사를 준다`() {
        val card = board().getValue(IpoSeed.FILED_CORP)

        assertNull(card["ticker"])
        assertEquals("신고서기업", card["name"])
        assertEquals(IpoSeed.FILED_CORP, card["corpCode"])
        assertEquals(false, card["spac"])
        assertEquals("PLANNED", card["priceBasis"])
        assertEquals(23000.0, card["offerPrice"].num())
        assertEquals(d(20), card["subscrStart"])
        assertEquals(d(21), card["subscrEnd"])
        assertEquals(d(23), card["payDate"])
        assertNull(card["refundDate"])
        assertNull(card["listingDate"])
        assertEquals("삼성증권", card["leadManagers"])
    }

    @Test
    fun `IB-03 예탁원 카드는 예탁원 값을 유지하고 연결된 신고서의 corpCode를 채운다`() {
        val cards = board()

        val linked = cards.getValue(IpoSeed.LINKED_TICKER)
        assertEquals("공모연결", linked["name"])
        assertEquals(IpoSeed.LINKED_CORP, linked["corpCode"])
        assertEquals(false, linked["spac"])
        assertEquals("CONFIRMED", linked["priceBasis"])
        assertEquals(15000.0, linked["offerPrice"].num())
        assertEquals("연결주관", linked["leadManagers"])
        val beyond = cards.getValue(IpoSeed.BEYOND_TICKER)
        assertEquals(IpoSeed.BEYOND_CORP, beyond["corpCode"])
        assertNull(beyond["offerPrice"])
        assertEquals("CONFIRMED", beyond["priceBasis"])
        val ksdOnly = cards.getValue(IpoSeed.KSD_ONLY_TICKER)
        assertNull(ksdOnly["corpCode"])
        assertEquals(false, ksdOnly["spac"])
        assertEquals("CONFIRMED", ksdOnly["priceBasis"])
    }

    @Test
    fun `IB-04 스팩 신고서는 spac=true이고 발행조건확정이어도 가격은 예정가다`() {
        val spac = board().getValue(IpoSeed.SPAC_CORP)

        assertEquals("테스트기업인수목적1호", spac["name"])
        assertEquals(true, spac["spac"])
        assertEquals("PLANNED", spac["priceBasis"])
        assertEquals(2000.0, spac["offerPrice"].num())
    }

    @Test
    fun `ID-01 신고서 단계 공모 상세는 공모 구조와 기업 개요를 서버 계산 비중과 함께 준다`() {
        val data = detail("?corpCode=${IpoSeed.FILED_CORP}")

        assertEquals(IpoSeed.FILED_CORP, data["corpCode"])
        assertNull(data["ticker"])
        assertEquals("신고서기업", data["name"])
        assertEquals("FILED", data["status"])
        assertEquals(false, data["spac"])
        assertEquals(
            mapOf("subscrStart" to d(20), "subscrEnd" to d(21), "payDate" to d(23), "refundDate" to null, "listingDate" to null),
            data["schedule"],
        )
        val offering = data["offering"] as Map<*, *>
        assertEquals(23000.0, offering["price"].num())
        assertEquals("PLANNED", offering["priceBasis"])
        assertEquals(2030000.0, offering["shares"].num())
        assertEquals(46690000000.0, offering["amount"].num())
        assertEquals("일반공모", offering["method"])
        val underwriters = offering.list("underwriters")
        assertEquals(listOf("삼성증권" to "대표", "테스트증권" to "인수"), underwriters.map { it["name"] to it["role"] })
        assertEquals(1500000.0, underwriters[0]["shares"].num())
        assertEquals(34500000000.0, underwriters[0]["amount"].num())
        assertEquals("총액인수", underwriters[0]["method"])
        assertEquals(
            listOf(Triple("시설자금", 31320000000.0, 99.8867), Triple("운영자금", 35540000.0, 0.1133)),
            offering.list("fundUses").map { Triple(it["purpose"], it["amount"].num(), it["share"].num()) },
        )
        assertEquals(
            listOf(
                listOf("최대주주", "최대주주", 1000000.0, 300000.0, 700000.0),
                listOf("벤처캐피탈", "기타", 500000.0, 106000.0, 394000.0),
            ),
            offering.list("sellers").map { listOf(it["holder"], it["relation"], it["before"].num(), it["sold"].num(), it["after"].num()) },
        )
        assertEquals(20.0, offering["oldShareRatio"].num())
        assertEquals(false, offering["fundUsesWithheld"])
        val putback = offering["putback"] as Map<*, *>
        assertEquals("공모가 하락", putback["reason"])
        assertEquals("일반청약자", putback["investors"])
        assertEquals("2030000", putback["shares"])
        assertEquals("상장일부터 1개월", putback["period"])
        assertEquals("공모가격의 90%", putback["price"])
        assertEquals(
            mapOf(
                "ceo" to "김대표",
                "establishedOn" to "2002-08-07",
                "address" to "서울특별시 테스트구 1",
                "homepage" to null,
                "description" to "신고서기업은 자동차 부품을 만든다.",
                "descriptionSource" to "DART_LLM",
                "descriptionRceptNo" to "20261001000579",
            ),
            data["company"],
        )
        assertNull(data["afterListing"])
        assertEquals(
            mapOf(
                "firstRceptNo" to "20260915000123",
                "latestRceptNo" to "20261001000579",
                "latestReportName" to "[기재정정]증권신고서(지분증권)",
            ),
            data["filing"],
        )
        assertNotNull(data["asOf"])
    }

    @Test
    fun `ID-02 예탁원과 연결된 공모는 ticker와 corpCode 어느 키로도 같은 값을 주고 일정·가격은 예탁원 값이다`() {
        val byTicker = detail("?ticker=${IpoSeed.LINKED_TICKER}")
        val byCorpCode = detail("?corpCode=${IpoSeed.LINKED_CORP}")

        assertEquals(byTicker, byCorpCode)
        assertEquals(IpoSeed.LINKED_TICKER, byTicker["ticker"])
        assertEquals(IpoSeed.LINKED_CORP, byTicker["corpCode"])
        assertEquals("UPCOMING", byTicker["status"])
        assertEquals(
            mapOf("subscrStart" to d(3), "subscrEnd" to d(4), "payDate" to d(6), "refundDate" to d(6), "listingDate" to null),
            byTicker["schedule"],
        )
        val offering = byTicker["offering"] as Map<*, *>
        assertEquals(15000.0, offering["price"].num())
        assertEquals("CONFIRMED", offering["priceBasis"])
        assertEquals(1000000.0, offering["shares"].num())
        assertEquals(15000000000.0, offering["amount"].num())
        assertEquals(listOf("연결증권"), offering.list("underwriters").map { it["name"] })
        assertEquals(15000000000.0, offering.list("underwriters")[0]["amount"].num())
        assertEquals(emptyList<Any>(), offering["fundUses"])
        assertEquals(emptyList<Any>(), offering["sellers"])
        assertNull(offering["oldShareRatio"])
        assertNull(offering["putback"])
        assertNull(byTicker["company"])
        assertEquals("[발행조건확정]증권신고서(지분증권)", (byTicker["filing"] as Map<*, *>)["latestReportName"])
    }

    @Test
    fun `ID-03 예탁원 행만 있으면 DART 항목과 기업 개요가 비고 stocks에 없으면 상장 후 성과도 비운다`() {
        val data = detail("?ticker=${IpoSeed.KSD_ONLY_TICKER}")

        assertEquals(IpoSeed.KSD_ONLY_TICKER, data["ticker"])
        assertNull(data["corpCode"])
        assertEquals("LISTED", data["status"])
        assertEquals(d(-5), (data["schedule"] as Map<*, *>)["listingDate"])
        val offering = data["offering"] as Map<*, *>
        assertEquals(10000.0, offering["price"].num())
        assertEquals("CONFIRMED", offering["priceBasis"])
        assertEquals<Map<*, *>>(
            listOf("shares", "amount", "method", "underwriters", "fundUses", "sellers", "oldShareRatio", "putback")
                .associateWith { null },
            offering.filterKeys { it != "price" && it != "priceBasis" && it != "fundUsesWithheld" },
        )
        assertEquals(false, offering["fundUsesWithheld"])
        assertNull(data["company"])
        assertNull(data["filing"])
        assertNull(data["afterListing"])
    }

    @Test
    fun `ID-04 상장 종목은 상장일 시초가·종가와 현재가의 공모가 대비 수익률을 준다`() {
        val data = detail("?ticker=${IpoSeed.LISTED_TICKER}")

        assertEquals(IpoSeed.LISTED_CORP, data["corpCode"])
        val after = data["afterListing"] as Map<*, *>
        assertEquals("2029-03-14", after["listingDate"])
        assertEquals(20000.0, after["open"].num())
        assertEquals(18000.0, after["close"].num())
        assertEquals(100.0, after["openReturn"].num())
        assertEquals(80.0, after["closeReturn"].num())
        assertEquals(12500.0, after["price"].num())
        assertEquals(25.0, after["currentReturn"].num())
        assertEquals("2029-03-30", after["priceDate"])
    }

    @Test
    fun `ID-07 자금 용도 합계가 공모 총액과 크게 다르면 자금 용도를 내리지 않고 숨김을 알린다`() {
        val offering = detail("?corpCode=${IpoSeed.MISMATCH_CORP}")["offering"] as Map<*, *>

        assertEquals(emptyList<Any>(), offering["fundUses"])
        assertEquals(true, offering["fundUsesWithheld"])
        assertEquals(12300000000.0, offering["amount"].num())
    }

    @Test
    fun `ID-05 corpCode와 ticker는 정확히 하나여야 하고 20자를 넘으면 400`() {
        listOf(
            "",
            "?corpCode=&ticker=",
            "?corpCode=${IpoSeed.FILED_CORP}&ticker=${IpoSeed.LINKED_TICKER}",
            "?corpCode=${"9".repeat(21)}",
            "?ticker=${"9".repeat(21)}",
        ).forEach { query ->
            val response = rest.getForEntity("/api/v1/ipos/detail$query", Map::class.java)
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, query)
            assertEquals(ErrorCode.INVALID_PARAMETER, response.errorCode(), query)
        }
    }

    @Test
    fun `ID-06 없는 공모와 철회된 신고서는 404 IPO_NOT_FOUND`() {
        listOf("?corpCode=99999999", "?ticker=999999", "?corpCode=${IpoSeed.WITHDRAWN_CORP}").forEach { query ->
            val response = rest.getForEntity("/api/v1/ipos/detail$query", Map::class.java)
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode, query)
            assertEquals(ErrorCode.IPO_NOT_FOUND, response.errorCode(), query)
        }
    }

    private fun board(): Map<String, Map<*, *>> {
        val response = rest.getForEntity("/api/v1/ipos", Map::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        return ((response.body!!["data"] as Map<*, *>)["offerings"] as List<*>)
            .map { it as Map<*, *> }
            .filter { IpoSeed.owns(it["ticker"] as String?, it["corpCode"] as String?) }
            .associateBy { (it["ticker"] ?: it["corpCode"]) as String }
    }

    private fun detail(query: String): Map<*, *> {
        val response = rest.getForEntity("/api/v1/ipos/detail$query", Map::class.java)
        assertEquals(HttpStatus.OK, response.statusCode, query)
        return response.body!!["data"] as Map<*, *>
    }

    private fun Map<*, *>.list(key: String): List<Map<*, *>> = (this[key] as List<*>).map { it as Map<*, *> }

    private fun ResponseEntity<Map<*, *>>.errorCode(): String = (body!!["error"] as Map<*, *>)["code"] as String

    private fun d(offset: Long): String = today.plusDays(offset).toString()

    private fun Any?.num(): Double = (this as Number).toDouble()

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
