package com.finngraph.web.theme

import com.finngraph.support.HotThemeSeed
import com.finngraph.support.TestContainers
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
class ThemeHotApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `기본 count는 20이며 무토큰으로 200`() {
        val response = rest.getForEntity("/api/v1/themes/hot", String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.contains("\"data\""))
    }

    @Test
    fun `허용된 count 3종은 200`() {
        listOf(10, 20, 30).forEach { count ->
            val response = rest.getForEntity("/api/v1/themes/hot?count=$count", String::class.java)
            assertEquals(HttpStatus.OK, response.statusCode, "count=$count")
        }
    }

    @Test
    fun `응답 순서는 하한 초과분 순이며 결손·쏠림·소수 테마는 빠진다`() {
        HotThemeSeed.seed()
        try {
            val response = rest.getForEntity("/api/v1/themes/hot?count=20", String::class.java)

            assertEquals(HttpStatus.OK, response.statusCode)
            val payload = mapper.readValue(response.body, Map::class.java)
            val themes = (payload["data"] as List<*>).map { it as Map<*, *> }
            assertEquals(listOf("시드급등테마", "시드경계테마", "시드하락테마"), themes.map { it["name"] })
            assertEquals(listOf("UP", "UP", "DOWN"), themes.map { it["hotSide"] })

            val surge = themes.first()
            assertEquals(HotThemeSeed.BASE_DATE, surge["baseDate"])
            assertEquals(5.0, (surge["change"] as Number).toDouble())
            assertEquals(5.1667, (surge["meanChange"] as Number).toDouble())
            assertEquals(7, surge["stockCount"])
            assertEquals(6, surge["pricedCount"])
            assertEquals(6, surge["upCount"])
            assertEquals(0, surge["downCount"])
            assertEquals(0, surge["flatCount"])
            assertEquals(1, surge["suspendedCount"])
            assertEquals(1, surge["trimCount"])
            assertEquals(4.0301, (surge["changeLower"] as Number).toDouble())
            assertEquals(5.9699, (surge["changeUpper"] as Number).toDouble())
            assertEquals(0.33, (surge["sensitivity"] as Number).toDouble())
            assertEquals(0, surge["w1Count"])
            assertNull(surge["w1"])
            assertEquals(listOf("naver", "judal"), surge["sources"])
            val leaders = (surge["leaders"] as List<*>).map { it as Map<*, *> }
            assertEquals(listOf("909107", "909105"), leaders.map { it["ticker"] })
            assertEquals(listOf(8.0, 6.0), leaders.map { (it["change"] as Number).toDouble() })
            val top = (surge["topStocks"] as List<*>).map { (it as Map<*, *>)["ticker"] }
            assertEquals(listOf("909101", "909102", "909103"), top)
            assertEquals(631_000L, (surge["tradingValue"] as Number).toLong())
            assertEquals(6_050_000L, (surge["marketCap"] as Number).toLong())

            val boundary = themes[1]
            assertEquals(10, boundary["stockCount"])
            assertEquals(7, boundary["pricedCount"])
            assertEquals(3.8, (boundary["change"] as Number).toDouble())

            val fall = themes.last()
            assertEquals(-4.6667, (fall["change"] as Number).toDouble())
            assertEquals(-4.8, (fall["meanChange"] as Number).toDouble())
            assertEquals(0, fall["upCount"])
            assertEquals(5, fall["downCount"])
            val fallLeaders = (fall["leaders"] as List<*>).map { (it as Map<*, *>)["ticker"] }
            assertEquals(listOf("909115", "909113"), fallLeaders)
        } finally {
            HotThemeSeed.cleanup()
        }
    }

    @Test
    fun `허용되지 않은 count는 400`() {
        listOf(0, 15, 40, 100).forEach { count ->
            val response = rest.getForEntity("/api/v1/themes/hot?count=$count", String::class.java)
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, "count=$count")
            assertTrue(response.body!!.contains("count"), "count=$count")
        }
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
