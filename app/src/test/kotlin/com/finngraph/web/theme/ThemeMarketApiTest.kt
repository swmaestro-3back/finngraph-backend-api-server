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
class ThemeMarketApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `시드 종목 전체를 시장으로 보고 부호별 수·중앙값·비율·적재율을 낸다`() {
        HotThemeSeed.seed()
        try {
            val data = fetch()

            assertEquals(HotThemeSeed.BASE_DATE, data["baseDate"])
            assertEquals(41, data["pricedCount"])
            assertEquals(26, data["upCount"])
            assertEquals(13, data["downCount"])
            assertEquals(2, data["flatCount"])
            assertEquals(2.0, (data["medianChange"] as Number).toDouble())
            assertEquals(0.6341, (data["upRatio"] as Number).toDouble())
            assertEquals(0.3171, (data["downRatio"] as Number).toDouble())
            assertEquals(0.9592, (data["coverage"] as Number).toDouble())
        } finally {
            HotThemeSeed.cleanup()
        }
    }

    @Test
    fun `데이터가 없으면 기준일과 통계가 null이고 200이다`() {
        val data = fetch()

        assertNull(data["baseDate"])
        assertEquals(0, data["pricedCount"])
        assertNull(data["medianChange"])
        assertNull(data["coverage"])
    }

    @Test
    fun `market 경로는 테마 id 경로보다 먼저 매칭된다`() {
        val response = rest.getForEntity("/api/v1/themes/market", String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.contains("\"pricedCount\""))
    }

    private fun fetch(): Map<*, *> {
        val response = rest.getForEntity("/api/v1/themes/market", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        val payload = mapper.readValue(response.body!!, Map::class.java)
        return payload["data"] as Map<*, *>
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
