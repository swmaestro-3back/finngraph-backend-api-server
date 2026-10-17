package com.finngraph.web.stock

import com.finngraph.support.ContractSeed
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
class StockContractApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `정정 체인은 최신 1건만 나오고 접수일 내림차순이다`() {
        val rows = fetch("/api/v1/stocks/${ContractSeed.FILER_TICKER}/contracts")

        assertEquals(
            listOf(ContractSeed.STANDALONE, ContractSeed.CHAIN_LATEST, ContractSeed.REVERSE),
            rows.map { it["rceptNo"] },
        )
    }

    @Test
    fun `제출사면 FILER 이고 계약상대 정보를 내려준다`() {
        val row = fetch("/api/v1/stocks/${ContractSeed.FILER_TICKER}/contracts")
            .single { it["rceptNo"] == ContractSeed.CHAIN_LATEST }

        assertEquals("FILER", row["role"])
        assertEquals("시드파트너", row["counterpartyName"])
        assertEquals(ContractSeed.PARTNER_TICKER, row["counterpartyTicker"])
        assertEquals(1500, row["contractAmount"])
        assertEquals(15.0, row["salesRatio"])
        assertEquals("2026-08-09", row["rceptDate"])
        assertEquals("2026-08-01", row["startDate"])
        assertEquals("2027-12-31", row["endDate"])
        assertEquals("[기재정정]단일판매ㆍ공급계약체결", row["reportName"])
        assertEquals("https://seed.test/dart/${ContractSeed.CHAIN_LATEST}", row["link"])
        assertEquals(true, row["isCorrection"])
        assertTrue(row.containsKey("isCorrection"))
    }

    @Test
    fun `역매칭 법인명이 없으면 계약상대 원문을 쓰고 없는 값은 null 이다`() {
        val row = fetch("/api/v1/stocks/${ContractSeed.FILER_TICKER}/contracts")
            .single { it["rceptNo"] == ContractSeed.STANDALONE }

        assertEquals("FILER", row["role"])
        assertEquals("비상장상대", row["counterpartyName"])
        assertNull(row["counterpartyTicker"])
        assertEquals(500, row["contractAmount"])
        assertNull(row["salesRatio"])
        assertNull(row["startDate"])
        assertEquals(false, row["isCorrection"])
    }

    @Test
    fun `계약상대면 COUNTERPARTY이고 제출사 정보를 상대로 내려준다`() {
        val row = fetch("/api/v1/stocks/${ContractSeed.FILER_TICKER}/contracts")
            .single { it["rceptNo"] == ContractSeed.REVERSE }

        assertEquals("COUNTERPARTY", row["role"])
        assertEquals("시드타사", row["counterpartyName"])
        assertEquals(ContractSeed.OTHER_TICKER, row["counterpartyTicker"])
        assertNull(row["contractAmount"])
        assertEquals(3.3, row["salesRatio"])
    }

    @Test
    fun `계약상대로만 등장하는 종목도 조회된다`() {
        val rows = fetch("/api/v1/stocks/${ContractSeed.PARTNER_TICKER}/contracts")

        assertEquals(listOf(ContractSeed.CHAIN_LATEST, ContractSeed.PARTNER_DEAL), rows.map { it["rceptNo"] })
        assertEquals(listOf("COUNTERPARTY", "COUNTERPARTY"), rows.map { it["role"] })
        assertEquals("시드공급사", rows[0]["counterpartyName"])
        assertEquals(ContractSeed.FILER_TICKER, rows[0]["counterpartyTicker"])
    }

    @Test
    fun `limit 은 접수일 순으로 자른다`() {
        val rows = fetch("/api/v1/stocks/${ContractSeed.FILER_TICKER}/contracts?limit=1")

        assertEquals(listOf(ContractSeed.STANDALONE), rows.map { it["rceptNo"] })
    }

    @Test
    fun `존재하지 않는 종목은 빈 배열이다`() {
        val response = rest.getForEntity("/api/v1/stocks/${ContractSeed.MISSING_TICKER}/contracts", String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList<Any>(), rows(response.body!!))
    }

    @Test
    fun `limit 범위 밖은 400`() {
        listOf(0, 201, -1).forEach { limit ->
            val response = rest.getForEntity(
                "/api/v1/stocks/${ContractSeed.FILER_TICKER}/contracts?limit=$limit",
                String::class.java,
            )
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, "limit=$limit")
            assertTrue(response.body!!.contains("INVALID_PARAMETER"), "limit=$limit")
            assertTrue(response.body!!.contains("\"limit\""), "limit=$limit")
        }
        assertEquals(
            HttpStatus.OK,
            rest.getForEntity("/api/v1/stocks/${ContractSeed.FILER_TICKER}/contracts?limit=200", String::class.java).statusCode,
        )
    }

    private fun fetch(path: String): List<Map<*, *>> {
        val response = rest.getForEntity(path, String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode, path)
        return rows(response.body!!)
    }

    private fun rows(body: String): List<Map<*, *>> {
        val payload = mapper.readValue(body, Map::class.java)
        return (payload["data"] as List<*>).map { it as Map<*, *> }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun seed() = ContractSeed.seed()

        @JvmStatic
        @AfterAll
        fun cleanup() = ContractSeed.cleanup()

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
