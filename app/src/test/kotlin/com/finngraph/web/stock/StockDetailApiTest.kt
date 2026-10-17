package com.finngraph.web.stock

import com.finngraph.support.CompanySeed
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class StockDetailApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `기업 설명이 있으면 본문과 출처, 원천 접수번호를 내려준다`() {
        val data = fetch(CompanySeed.DESCRIBED_TICKER)

        assertEquals("시드전자", data["name"])
        assertEquals(CompanySeed.DESCRIPTION, data["description"])
        assertEquals("DART_LLM", data["descriptionSource"])
        assertEquals(CompanySeed.RCEPT_NO, data["descriptionRceptNo"])
    }

    @Test
    fun `기업 설명이 없으면 세 필드가 모두 null 로 존재한다`() {
        val data = fetch(CompanySeed.BARE_TICKER)

        listOf("description", "descriptionSource", "descriptionRceptNo").forEach { field ->
            assertTrue(data.containsKey(field), field)
            assertNull(data[field], field)
        }
    }

    @Test
    fun `기업 정보가 있으면 profile 에 원값 그대로 내려준다`() {
        val profile = fetch(CompanySeed.DESCRIBED_TICKER)["profile"] as Map<*, *>

        assertEquals(CompanySeed.CEO_NAME, profile["ceoName"])
        assertEquals(CompanySeed.ESTABLISHED_ON, profile["establishedOn"])
        assertEquals(CompanySeed.LISTED_ON, profile["listedOn"])
        assertEquals(CompanySeed.FISCAL_MONTH, profile["fiscalMonth"])
        assertEquals(CompanySeed.LISTED_SHARES, (profile["listedShares"] as Number).toLong())
        assertEquals(0, BigDecimal(CompanySeed.PAR_VALUE).compareTo(BigDecimal(profile["parValue"].toString())))
        assertEquals(CompanySeed.HOMEPAGE, profile["homepage"])
        assertEquals(CompanySeed.ADDRESS, profile["address"])
    }

    @Test
    fun `기업 정보가 없으면 profile 객체는 있고 필드가 모두 null 이다`() {
        val profile = fetch(CompanySeed.BARE_TICKER)["profile"] as Map<*, *>

        PROFILE_FIELDS.forEach { field ->
            assertTrue(profile.containsKey(field), field)
            assertNull(profile[field], field)
        }
    }

    private fun fetch(ticker: String): Map<*, *> {
        val response = rest.getForEntity("/api/v1/stocks/$ticker", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode, ticker)
        val payload = mapper.readValue(response.body!!, Map::class.java)
        return payload["data"] as Map<*, *>
    }

    companion object {
        private val PROFILE_FIELDS = listOf(
            "ceoName", "establishedOn", "listedOn", "fiscalMonth", "listedShares", "parValue", "homepage", "address",
        )

        @JvmStatic
        @BeforeAll
        fun seed() = CompanySeed.seed()

        @JvmStatic
        @AfterAll
        fun cleanup() = CompanySeed.cleanup()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
