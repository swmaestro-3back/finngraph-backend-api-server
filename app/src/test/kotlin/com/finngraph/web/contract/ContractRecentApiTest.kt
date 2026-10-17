package com.finngraph.web.contract

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
class ContractRecentApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `기본값은 최신 접수일 기준 7일 salesRatio 내림차순이며 null 은 뒤로 간다`() {
        val rows = fetch("/api/v1/contracts/recent")

        assertEquals(
            listOf(ContractSeed.PARTNER_DEAL, ContractSeed.CHAIN_LATEST, ContractSeed.REVERSE, ContractSeed.STANDALONE),
            rows.map { it["rceptNo"] },
        )
        assertNull(rows.last()["salesRatio"])
    }

    @Test
    fun `정정 체인의 이전 회차는 창 안에 있어도 제외된다`() {
        val rceptNos = fetch("/api/v1/contracts/recent?days=30").map { it["rceptNo"] }

        assertTrue(ContractSeed.CHAIN_LATEST in rceptNos)
        assertTrue(ContractSeed.CHAIN_ROOT !in rceptNos)
        assertTrue(ContractSeed.CHAIN_MID !in rceptNos)
    }

    @Test
    fun `contractAmount 정렬은 파싱 실패 행을 뒤로 보낸다`() {
        val rows = fetch("/api/v1/contracts/recent?sort=contractAmount")

        assertEquals(
            listOf(ContractSeed.CHAIN_LATEST, ContractSeed.PARTNER_DEAL, ContractSeed.STANDALONE, ContractSeed.REVERSE),
            rows.map { it["rceptNo"] },
        )
        assertEquals(1500, rows.first()["contractAmount"])
        assertNull(rows.last()["contractAmount"])
    }

    @Test
    fun `days 를 늘리면 오래된 공시가 들어온다`() {
        val narrow = fetch("/api/v1/contracts/recent?days=1").map { it["rceptNo"] }
        val wide = fetch("/api/v1/contracts/recent?days=90").map { it["rceptNo"] }

        assertEquals(listOf(ContractSeed.CHAIN_LATEST, ContractSeed.STANDALONE), narrow)
        assertEquals(
            listOf(
                ContractSeed.PARTNER_DEAL,
                ContractSeed.CHAIN_LATEST,
                ContractSeed.REVERSE,
                ContractSeed.STALE,
                ContractSeed.STANDALONE,
            ),
            wide,
        )
    }

    @Test
    fun `제출사 정보는 role 없이 그대로 내려준다`() {
        val row = fetch("/api/v1/contracts/recent").single { it["rceptNo"] == ContractSeed.REVERSE }

        assertEquals(ContractSeed.OTHER_TICKER, row["filerTicker"])
        assertEquals("시드타사", row["filerName"])
        assertEquals("KOSDAQ", row["filerMarket"])
        assertEquals("시드공급사", row["counterpartyName"])
        assertEquals(ContractSeed.FILER_TICKER, row["counterpartyTicker"])
        assertEquals(3.3, row["salesRatio"])
        assertNull(row["contractAmount"])
        assertEquals(false, row["isCorrection"])
        assertTrue(row.containsKey("isCorrection"))
    }

    @Test
    fun `limit 은 정렬 후에 자른다`() {
        val rows = fetch("/api/v1/contracts/recent?limit=2")

        assertEquals(listOf(ContractSeed.PARTNER_DEAL, ContractSeed.CHAIN_LATEST), rows.map { it["rceptNo"] })
    }

    @Test
    fun `days limit sort 가 범위 밖이면 400`() {
        mapOf(
            "days=0" to "days",
            "days=91" to "days",
            "limit=0" to "limit",
            "limit=101" to "limit",
            "sort=rceptDate" to "sort",
        ).forEach { (query, field) ->
            val response = rest.getForEntity("/api/v1/contracts/recent?$query", String::class.java)
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, query)
            assertTrue(response.body!!.contains("INVALID_PARAMETER"), query)
            assertTrue(response.body!!.contains("\"$field\""), query)
        }
        listOf("days=90", "limit=100", "sort=contractAmount").forEach { query ->
            val response = rest.getForEntity("/api/v1/contracts/recent?$query", String::class.java)
            assertEquals(HttpStatus.OK, response.statusCode, query)
        }
    }

    private fun fetch(path: String): List<Map<*, *>> {
        val response = rest.getForEntity(path, String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode, path)
        val payload = mapper.readValue(response.body, Map::class.java)
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
