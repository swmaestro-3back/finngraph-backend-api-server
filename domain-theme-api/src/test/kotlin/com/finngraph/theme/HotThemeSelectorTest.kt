package com.finngraph.theme

import com.finngraph.theme.model.HotSide
import com.finngraph.theme.model.MarketStats
import com.finngraph.theme.model.ThemeSummary
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HotThemeSelectorTest {

    private val market = market(priced = 100, up = 60, down = 35, median = "0.5", coverage = "0.95")

    @Test
    fun `상승은 등락률 내림차순 하락은 등락률 오름차순으로 뽑는다`() {
        val themes = listOf(
            summary("완만상승", "1.5", lower = "1.1", upper = "1.9"),
            summary("급등", "5.0", lower = "3.0", upper = "7.0"),
            summary("급락", "-4.0", lower = "-6.0", upper = "-3.0"),
            summary("완만하락", "-0.5", lower = "-1.0", upper = "-0.1"),
            summary("상승", "3.0", lower = "2.0", upper = "4.0"),
            summary("하락", "-2.0", lower = "-3.0", upper = "-1.0"),
        )

        val selected = HotThemeSelector.select(themes, market, 4)

        assertEquals(listOf("급등", "상승", "급락", "하락"), selected.map { it.name })
        assertEquals(listOf(HotSide.UP, HotSide.UP, HotSide.DOWN, HotSide.DOWN), selected.map { it.hotSide })
    }

    @Test
    fun `등락률을 낸 종목이 5개 미만이면 제외한다`() {
        val themes = listOf(
            summary("네종목", "9.0", lower = "8.0", upper = "10.0", priced = 4, stockCount = 4, up = 4),
            summary("다섯종목", "3.0", lower = "2.0", upper = "4.0"),
        )

        val selected = HotThemeSelector.select(themes, market, 4)

        assertEquals(listOf("다섯종목"), selected.map { it.name })
        assertNull(HotThemeSelector.hotSide(themes.first(), market))
    }

    @Test
    fun `결손이 30퍼센트를 넘으면 제외하고 30퍼센트 경계는 통과한다`() {
        val over = summary("결손초과", "5.0", lower = "4.0", upper = "6.0", priced = 5, stockCount = 8, up = 5)
        val boundary = summary("결손경계", "5.0", lower = "4.0", upper = "6.0", priced = 7, stockCount = 10, up = 7)

        assertNull(HotThemeSelector.hotSide(over, market))
        assertEquals(HotSide.UP, HotThemeSelector.hotSide(boundary, market))
    }

    @Test
    fun `같은 방향 종목이 절반 미만이면 등락률이 커도 제외한다`() {
        val skewed = summary("쏠림", "6.0", lower = "5.0", upper = "7.0", priced = 5, up = 2, down = 3)
        val reverse = summary("역쏠림", "-6.0", lower = "-7.0", upper = "-5.0", priced = 5, up = 3, down = 2)

        assertNull(HotThemeSelector.hotSide(skewed, market))
        assertNull(HotThemeSelector.hotSide(reverse, market))
    }

    @Test
    fun `시장보다 좁은 방향성이면 제외한다`() {
        val narrow = summary("좁은상승", "3.0", lower = "2.0", upper = "4.0", priced = 10, stockCount = 10, up = 5, down = 5)
        val narrowDown = summary("좁은하락", "-3.0", lower = "-4.0", upper = "-2.0", priced = 10, stockCount = 10, up = 5, down = 5)
        val upMarket = market(priced = 100, up = 51, down = 49, median = "0.5", coverage = "0.95")
        val downMarket = market(priced = 100, up = 45, down = 55, median = "-0.5", coverage = "0.95")

        assertNull(HotThemeSelector.hotSide(narrow, upMarket))
        assertNull(HotThemeSelector.hotSide(narrowDown, downMarket))
    }

    @Test
    fun `하한이 시장 중앙값을 0점5 이상 넘지 못하면 제외한다`() {
        val weak = summary("약한상승", "1.5", lower = "0.9", upper = "2.1")
        val edge = summary("경계상승", "1.5", lower = "1.0", upper = "2.1")
        val weakDown = summary("약한하락", "-1.0", lower = "-2.0", upper = "0.1")
        val edgeDown = summary("경계하락", "-1.0", lower = "-2.0", upper = "0.0")

        assertNull(HotThemeSelector.hotSide(weak, market))
        assertEquals(HotSide.UP, HotThemeSelector.hotSide(edge, market))
        assertNull(HotThemeSelector.hotSide(weakDown, market))
        assertEquals(HotSide.DOWN, HotThemeSelector.hotSide(edgeDown, market))
    }

    @Test
    fun `하한이 없으면 후보가 아니다`() {
        val noBounds = summary("구간없음", "5.0", lower = null, upper = null)

        assertNull(HotThemeSelector.hotSide(noBounds, market))
    }

    @Test
    fun `적재율이 0점8 미만이면 빈 목록이고 hotSide도 채우지 않는다`() {
        val themes = listOf(summary("급등", "5.0", lower = "3.0", upper = "7.0"))
        val partial = market(priced = 100, up = 60, down = 35, median = "0.5", coverage = "0.7999")

        assertEquals(emptyList(), HotThemeSelector.select(themes, partial, 4))
        assertEquals(listOf(null), HotThemeSelector.annotate(themes, partial).map { it.hotSide })
        assertEquals(emptyList(), HotThemeSelector.select(themes, MarketStats.EMPTY, 4))
    }

    @Test
    fun `홀수 count는 상승 쪽이 한 개 더 많다`() {
        val themes = listOf(
            summary("상1", "3.0", lower = "3.0", upper = "3.5"),
            summary("상2", "2.0", lower = "2.0", upper = "2.5"),
            summary("상3", "1.5", lower = "1.0", upper = "2.0"),
            summary("하1", "-3.0", lower = "-3.5", upper = "-3.0"),
            summary("하2", "-2.0", lower = "-2.5", upper = "-2.0"),
            summary("하3", "-1.0", lower = "-1.5", upper = "-1.0"),
        )

        val selected = HotThemeSelector.select(themes, market, 5)

        assertEquals(listOf("상1", "상2", "상3", "하1", "하2"), selected.map { it.name })
    }

    @Test
    fun `한쪽이 부족하면 채우지 않고 있는 만큼만 반환한다`() {
        val themes = listOf(
            summary("상1", "3.0", lower = "3.0", upper = "3.5"),
            summary("상2", "2.0", lower = "2.0", upper = "2.5"),
            summary("상3", "1.5", lower = "1.0", upper = "2.0"),
            summary("하1", "-1.0", lower = "-1.5", upper = "-1.0"),
        )

        val selected = HotThemeSelector.select(themes, market, 6)

        assertEquals(listOf("상1", "상2", "상3", "하1"), selected.map { it.name })
    }

    @Test
    fun `동률이면 입력 순서를 유지한다`() {
        val themes = listOf(
            summary("먼저", "2.0", lower = "1.0", upper = "3.0"),
            summary("나중", "2.0", lower = "1.5", upper = "2.5"),
            summary("하락", "-2.0", lower = "-3.0", upper = "-1.0"),
        )

        val selected = HotThemeSelector.select(themes, market, 4)

        assertEquals(listOf("먼저", "나중", "하락"), selected.map { it.name })
    }

    @Test
    fun `count보다 후보가 많으면 상하 각각 잘라낸다`() {
        val themes = (1..30).map { summary("상$it", "$it.0", lower = "$it.0", upper = "${it + 1}.0") } +
            (1..30).map { summary("하$it", "-$it.0", lower = "-${it + 1}.0", upper = "-$it.0") }

        val selected = HotThemeSelector.select(themes, market, 40)

        assertEquals(40, selected.size)
        assertEquals("상30", selected.first().name)
        assertEquals("하30", selected[20].name)
    }

    @Test
    fun `다른 조건으로 상승이어도 가중 등락률이 0 이하면 제외한다`() {
        val zero = summary("가중보합", "3.0", lower = "2.0", upper = "4.0", weighted = "0.0000")
        val negative = summary("가중하락", "3.0", lower = "2.0", upper = "4.0", weighted = "-0.0001")

        assertNull(HotThemeSelector.hotSide(zero, market))
        assertNull(HotThemeSelector.hotSide(negative, market))
        assertEquals(emptyList(), HotThemeSelector.select(listOf(zero, negative), market, 4))
    }

    @Test
    fun `다른 조건으로 하락이어도 가중 등락률이 0 이상이면 제외한다`() {
        val zero = summary("가중보합", "-3.0", lower = "-4.0", upper = "-2.0", weighted = "0.0000")
        val positive = summary("가중상승", "-3.0", lower = "-4.0", upper = "-2.0", weighted = "0.0001")

        assertNull(HotThemeSelector.hotSide(zero, market))
        assertNull(HotThemeSelector.hotSide(positive, market))
        assertEquals(emptyList(), HotThemeSelector.select(listOf(zero, positive), market, 4))
    }

    @Test
    fun `가중 등락률이 null이면 제외한다`() {
        val up = summary("가중없음상승", "3.0", lower = "2.0", upper = "4.0", weighted = null)
        val down = summary("가중없음하락", "-3.0", lower = "-4.0", upper = "-2.0", weighted = null)

        assertNull(HotThemeSelector.hotSide(up, market))
        assertNull(HotThemeSelector.hotSide(down, market))
    }

    private fun market(priced: Int, up: Int, down: Int, median: String, coverage: String) = MarketStats(
        baseDate = null,
        pricedCount = priced,
        upCount = up,
        downCount = down,
        flatCount = priced - up - down,
        medianChange = BigDecimal(median),
        upRatio = null,
        downRatio = null,
        coverage = BigDecimal(coverage),
    )

    private fun summary(
        name: String,
        change: String?,
        lower: String?,
        upper: String?,
        priced: Int = HotThemeSelector.MIN_PRICED_STOCKS,
        stockCount: Int = priced,
        up: Int = if ((change?.let(::BigDecimal)?.signum() ?: 0) > 0) priced else 0,
        down: Int = if ((change?.let(::BigDecimal)?.signum() ?: 0) < 0) priced else 0,
        weighted: String? = change,
    ) = ThemeSummary(
        id = name.hashCode().toLong(),
        name = name,
        description = null,
        baseDate = null,
        change = change?.let(::BigDecimal),
        tradingValue = null,
        avgTradingValue = null,
        tradingValueRatio = null,
        marketCap = null,
        w1 = null,
        m1 = null,
        m3 = null,
        stockCount = stockCount,
        pricedCount = priced,
        upCount = up,
        downCount = down,
        flatCount = priced - up - down,
        suspendedCount = 0,
        trimCount = if (change == null) 0 else 1,
        meanChange = change?.let(::BigDecimal),
        changeLower = lower?.let(::BigDecimal),
        changeUpper = upper?.let(::BigDecimal),
        sensitivity = null,
        w1Count = 0,
        m1Count = 0,
        m3Count = 0,
        leaders = emptyList(),
        sources = emptyList(),
        hotSide = null,
        topStocks = emptyList(),
        weightedChange = weighted?.let(::BigDecimal),
    )
}
