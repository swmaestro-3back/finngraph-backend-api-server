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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ThemeTradingValueRatioApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `배율은 유효 전 거래일 평균 대비이고 부분 적재일과 정지 종목은 분자·분모 모두에서 빠진다`() {
        HotThemeSeed.seed()
        try {
            val themes = themes()

            val surge = themes.getValue("시드급등테마")
            assertEquals(631_000L, (surge["tradingValue"] as Number).toLong())
            assertEquals(600_000L, (surge["avgTradingValue"] as Number).toLong())
            assertEquals(1.0517, (surge["tradingValueRatio"] as Number).toDouble())

            val boundary = themes.getValue("시드경계테마")
            assertEquals(727_000L, (boundary["tradingValue"] as Number).toLong())
            assertEquals(700_000L, (boundary["avgTradingValue"] as Number).toLong())
            assertEquals(1.0386, (boundary["tradingValueRatio"] as Number).toDouble())

            val fall = themes.getValue("시드하락테마")
            assertEquals(476_000L, (fall["tradingValue"] as Number).toLong())
            assertEquals(500_000L, (fall["avgTradingValue"] as Number).toLong())
            assertEquals(0.952, (fall["tradingValueRatio"] as Number).toDouble())

            val sparse = themes.getValue("시드결손테마")
            assertEquals(525_000L, (sparse["tradingValue"] as Number).toLong())
            assertEquals(500_000L, (sparse["avgTradingValue"] as Number).toLong())
            assertEquals(1.05, (sparse["tradingValueRatio"] as Number).toDouble())
        } finally {
            HotThemeSeed.cleanup()
        }
    }

    @Test
    fun `유효 거래일이 둘이면 종목별 평균이고 volume 0인 날은 그 종목의 평균에서 빠진다`() {
        HotThemeSeed.seed()
        HotThemeSeed.seedAverageWindow()
        try {
            val surge = themes().getValue("시드급등테마")

            assertEquals(631_000L, (surge["tradingValue"] as Number).toLong())
            assertEquals(1_100_000L, (surge["avgTradingValue"] as Number).toLong())
            assertEquals(0.5736, (surge["tradingValueRatio"] as Number).toDouble())
        } finally {
            HotThemeSeed.cleanup()
        }
    }

    @Test
    fun `데이터가 없으면 배율과 평균 거래대금은 null이다`() {
        HotThemeSeed.seedNullChange()
        try {
            val theme = themes().getValue("시드무등락테마")

            assertNull(theme["tradingValue"])
            assertNull(theme["avgTradingValue"])
            assertNull(theme["tradingValueRatio"])
        } finally {
            HotThemeSeed.cleanup()
        }
    }

    private fun themes(): Map<String, Map<*, *>> {
        val response = rest.getForEntity("/api/v1/themes", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        val payload = mapper.readValue(response.body!!, Map::class.java)
        return (payload["data"] as List<*>).map { it as Map<*, *> }.associateBy { it["name"] as String }
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
