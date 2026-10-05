package com.finngraph.web

import com.finngraph.composition.briefing.BriefingAssembly
import com.finngraph.composition.briefing.BriefingComposer
import com.finngraph.composition.hottheme.HotThemeComposer
import com.finngraph.support.HotThemeSeed
import com.finngraph.support.IntradaySeed
import com.finngraph.support.TestContainers
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PriceBasisApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var hotThemes: HotThemeComposer

    @Autowired
    lateinit var briefing: BriefingComposer

    @BeforeEach
    fun reset() = IntradaySeed.clear()

    @Test
    fun `장중 캔들이 전 종목에 들어오면 가격 기준일은 오늘이고 밸류에이션은 전일 값을 쓴다`() {
        IntradaySeed.load()

        val market = data("/api/v1/themes/market")
        assertEquals(IntradaySeed.TODAY, market["baseDate"])
        assertEquals(HotThemeSeed.BASE_DATE, market["valuationDate"])
        assertNotNull(market["updatedAt"])

        val theme = data("/api/v1/themes/${HotThemeSeed.SURGE_THEME}")
        assertEquals(IntradaySeed.TODAY, theme["baseDate"])
        assertEquals(HotThemeSeed.BASE_DATE, theme["valuationDate"])
        assertEquals(10.0, theme.number("change"))
        assertEquals(10.0, theme.number("weightedChange"))
        assertNotNull(theme["marketCap"])
        assertNotNull(theme["avgTradingValue"])
        assertNull(theme["tradingValueRatio"])
    }

    @Test
    fun `종목 상세와 테마 구성 종목은 같은 기준일의 같은 가격을 낸다`() {
        IntradaySeed.load()

        val stock = data("/api/v1/stocks/909101")
        assertEquals(IntradaySeed.TODAY, stock["baseDate"])
        assertEquals(HotThemeSeed.BASE_DATE, stock["valuationDate"])
        assertEquals(113.3, stock.number("price"))
        assertEquals(10.0, stock.number("change"))
        assertEquals(2_000_000.0, stock.number("marketCap"))

        val member = list("/api/v1/themes/${HotThemeSeed.SURGE_THEME}/stocks").single { it["ticker"] == "909101" }
        assertEquals(stock.number("price"), member.number("price"))
        assertEquals(stock.number("change"), member.number("change"))
    }

    @Test
    fun `장중 봉은 가격 기준일이 넘어간 뒤에야 종목 캔들에 나온다`() {
        IntradaySeed.load()

        val last = list("/api/v1/stocks/909101/candles?period=D").last()
        assertEquals(IntradaySeed.TODAY, last["date"])
        assertEquals(113.3, last.number("close"))
    }

    @Test
    fun `아침 첫 수집이 덜 끝나면 가격 기준일은 전일에 머물고 캔들도 전일까지만 준다`() {
        IntradaySeed.load(listOf(9101L, 9102L))

        val market = data("/api/v1/themes/market")
        assertEquals(HotThemeSeed.BASE_DATE, market["baseDate"])
        assertEquals(HotThemeSeed.BASE_DATE, market["valuationDate"])

        val stock = data("/api/v1/stocks/909101")
        assertEquals(HotThemeSeed.BASE_DATE, stock["baseDate"])
        assertEquals(103.0, stock.number("price"))
        assertEquals(HotThemeSeed.BASE_DATE, list("/api/v1/stocks/909101/candles?period=D").last()["date"])
    }

    @Test
    fun `통합 장전 수집처럼 30퍼센트 종목만 당일 봉이 있으면 종목과 테마 가격 기준일은 전일에 머문다`() {
        val candled = HotThemeSeed.STOCKS.filter { it.kind != HotThemeSeed.Kind.NO_CANDLE }
        val early = candled.take((candled.size * EARLY_SHARE).roundToInt()).map { it.id }
        IntradaySeed.load(early)

        assertEquals(HotThemeSeed.BASE_DATE, data("/api/v1/themes/market")["baseDate"])
        assertEquals(HotThemeSeed.BASE_DATE, data("/api/v1/themes/${HotThemeSeed.SURGE_THEME}")["baseDate"])
        val stock = data("/api/v1/stocks/909101")
        assertEquals(HotThemeSeed.BASE_DATE, stock["baseDate"])
        assertEquals(103.0, stock.number("price"))
        assertEquals(HotThemeSeed.BASE_DATE, list("/api/v1/stocks/909101/candles?period=D").last()["date"])
    }

    @Test
    fun `ETL 핫테마는 장중 가격 기준일로 화면 핫테마와 같은 테마를 발행한다`() {
        IntradaySeed.load(risers = HotThemeSeed.STOCKS.filter { it.theme == HotThemeSeed.FALL_THEME }.map { it.id })

        val snapshot = hotThemes.hotForEtl(HOT_COUNT)
        assertEquals(LocalDate.parse(IntradaySeed.TODAY), snapshot.tradeDate)
        assertEquals(listOf(HotThemeSeed.FALL_THEME), snapshot.themes.map { it.id })
        assertEquals(hotThemes.hot(HOT_COUNT).map { it.id }, snapshot.themes.map { it.id })
    }

    @Test
    fun `아침 첫 수집이 덜 끝나면 ETL 핫테마도 전일 기준으로 발행한다`() {
        IntradaySeed.load(listOf(9101L, 9102L))

        val snapshot = hotThemes.hotForEtl(HOT_COUNT)
        assertEquals(LocalDate.parse(HotThemeSeed.BASE_DATE), snapshot.tradeDate)
        assertEquals(
            listOf(HotThemeSeed.SURGE_THEME, HotThemeSeed.BOUNDARY_THEME, HotThemeSeed.FALL_THEME),
            snapshot.themes.map { it.id },
        )
    }

    @Test
    fun `브리핑은 장중에도 마감 기준일을 쓴다`() {
        IntradaySeed.load()

        assertEquals(
            BriefingAssembly.DateMismatch(LocalDate.parse(IntradaySeed.TODAY), LocalDate.parse(HotThemeSeed.BASE_DATE)),
            briefing.assemble(LocalDate.parse(IntradaySeed.TODAY)),
        )
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
        private const val HOT_COUNT = 30
        private const val EARLY_SHARE = 0.3

        @JvmStatic
        @BeforeAll
        fun seed() = HotThemeSeed.seed()

        @JvmStatic
        @AfterAll
        fun cleanup() {
            IntradaySeed.clear()
            HotThemeSeed.cleanup()
        }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
