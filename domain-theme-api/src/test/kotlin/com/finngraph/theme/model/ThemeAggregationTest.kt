package com.finngraph.theme.model

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThemeAggregationTest {

    private val base = LocalDate.parse("2026-07-31")
    private val prev = LocalDate.parse("2026-07-29")
    private val partial = LocalDate.parse("2026-07-30")
    private val theme = ThemeIdentity(1, "테마", null, listOf("naver"))

    @Test
    fun `거래정지 종목은 등락률 없이 정지 수로만 집계된다`() {
        val members = listOf(
            member(1, "103"),
            member(2, "104"),
            member(3, "105"),
            member(4, "100", suspended = true, volume = 0),
        )

        val summary = ThemeAggregation.summary(theme, members, base, prev)

        assertEquals(4, summary.stockCount)
        assertEquals(3, summary.pricedCount)
        assertEquals(1, summary.suspendedCount)
        assertEquals(BigDecimal("4.0000"), summary.change)
        assertEquals(BigDecimal("4.0000"), summary.meanChange)
    }

    @Test
    fun `정리매매 종목은 종목 수에서 빠지고 표에는 맨 뒤에 DELISTING으로 나온다`() {
        val members = listOf(
            member(1, "80", delisting = true, cap = 9_000_000),
            member(2, "103", cap = 100),
            member(3, "104", cap = 200),
            member(4, "105", cap = 300),
        )

        val summary = ThemeAggregation.summary(theme, members, base, prev)
        val views = ThemeAggregation.stockViews(members, prev)

        assertEquals(3, summary.stockCount)
        assertEquals(listOf("t4", "t3", "t2", "t1"), views.map { it.ticker })
        val last = views.last()
        assertEquals(ChangeStatus.DELISTING, last.changeStatus)
        assertNull(last.change)
        assertEquals(true, last.delistingTrade)
    }

    @Test
    fun `직전 캔들이 전 거래일이 아니면 NO_PREV이고 기준일 캔들이 없으면 NO_CANDLE이다`() {
        val members = listOf(
            member(1, "103"),
            member(2, "104"),
            member(3, "105"),
            member(4, "100", prevDate = partial),
            member(5, "100", prevDate = null, prevClose = null),
            member(6, null),
        )

        val summary = ThemeAggregation.summary(theme, members, base, prev)
        val statuses = ThemeAggregation.stockViews(members, prev).associate { it.ticker to it.changeStatus }

        assertEquals(6, summary.stockCount)
        assertEquals(3, summary.pricedCount)
        assertEquals(ChangeStatus.NO_PREV, statuses["t4"])
        assertEquals(ChangeStatus.NO_PREV, statuses["t5"])
        assertEquals(ChangeStatus.NO_CANDLE, statuses["t6"])
    }

    @Test
    fun `전 거래일이 없으면 모든 등락률이 null이다`() {
        val members = (1L..5L).map { member(it, "105") }

        val summary = ThemeAggregation.summary(theme, members, base, null)

        assertEquals(0, summary.pricedCount)
        assertNull(summary.change)
        assertEquals(5, summary.stockCount - summary.pricedCount - summary.suspendedCount)
    }

    @Test
    fun `절사된 종목은 TRIMMED이고 등락률은 유지된다`() {
        val members = listOf(
            member(1, "103"),
            member(2, "104"),
            member(3, "105"),
            member(4, "105"),
            member(5, "106"),
            member(6, "108"),
        )

        val views = ThemeAggregation.stockViews(members, prev).associateBy { it.ticker }

        assertEquals(ChangeStatus.TRIMMED, views.getValue("t1").changeStatus)
        assertEquals(BigDecimal("3.0000"), views.getValue("t1").change)
        assertEquals(ChangeStatus.TRIMMED, views.getValue("t6").changeStatus)
        assertEquals(ChangeStatus.PRICED, views.getValue("t2").changeStatus)
    }

    @Test
    fun `주도주는 테마 방향으로 두 개이고 대표 종목은 시총 상위 세 개다`() {
        val members = listOf(
            member(1, "103", cap = 500),
            member(2, "104", cap = 400),
            member(3, "105", cap = 300),
            member(4, "105", cap = 200),
            member(5, "106", cap = 100),
            member(6, "108", cap = null),
        )

        val summary = ThemeAggregation.summary(theme, members, base, prev)

        assertEquals(listOf("t6", "t5"), summary.leaders.map { it.ticker })
        assertEquals(BigDecimal("8.0000"), summary.leaders.first().change)
        assertEquals(listOf("t1", "t2", "t3"), summary.topStocks.map { it.ticker })
        assertEquals(1500L, summary.marketCap)
        assertEquals(1, summary.trimCount)
        assertEquals(listOf("naver"), summary.sources)
    }

    @Test
    fun `기간 수익률은 등락률을 낸 종목 중 값이 있는 것만 센다`() {
        val members = listOf(
            member(1, "103", r1w = "1"),
            member(2, "104", r1w = "2"),
            member(3, "105", r1w = "3"),
            member(4, "105", r1w = null),
            member(5, "100", suspended = true, volume = 0, r1w = "99"),
        )

        val summary = ThemeAggregation.summary(theme, members, base, prev)

        assertEquals(3, summary.w1Count)
        assertEquals(BigDecimal("2.0000"), summary.w1)
        assertEquals(0, summary.m1Count)
        assertNull(summary.m1)
    }

    @Test
    fun `거래대금 배율은 평균이 있는 산출 종목만으로 분자·분모를 맞춘다`() {
        val members = listOf(
            member(1, "103", tradeValue = 300),
            member(2, "104", tradeValue = 500),
            member(3, "105", tradeValue = 700),
            member(4, "105", tradeValue = 900),
        )
        val averages = averages(1 to "100", 2 to "200", 3 to "300")

        val summary = ThemeAggregation.summary(theme, members, base, prev, averages)

        assertEquals(2400L, summary.tradingValue)
        assertEquals(600L, summary.avgTradingValue)
        assertEquals(BigDecimal("2.5000"), summary.tradingValueRatio)
    }

    @Test
    fun `정지 종목은 평균이 있어도 배율의 분자·분모에서 빠진다`() {
        val members = listOf(
            member(1, "103", tradeValue = 150),
            member(2, "104", tradeValue = 150),
            member(3, "105", tradeValue = 150),
            member(4, "100", suspended = true, volume = 0, tradeValue = 0),
        )
        val averages = averages(1 to "100", 2 to "100", 3 to "100", 4 to "1000")

        val summary = ThemeAggregation.summary(theme, members, base, prev, averages)

        assertEquals(300L, summary.avgTradingValue)
        assertEquals(BigDecimal("1.5000"), summary.tradingValueRatio)
    }

    @Test
    fun `평균이 하나도 없거나 분모가 0이면 배율과 평균 거래대금은 null이다`() {
        val members = listOf(member(1, "103"), member(2, "104"), member(3, "105"))

        val none = ThemeAggregation.summary(theme, members, base, prev)
        val zero = ThemeAggregation.summary(theme, members, base, prev, averages(1 to "0", 2 to "0"))

        assertNull(none.tradingValueRatio)
        assertNull(none.avgTradingValue)
        assertEquals(3000L, none.tradingValue)
        assertNull(zero.tradingValueRatio)
        assertNull(zero.avgTradingValue)
    }

    @Test
    fun `시장 통계는 유니버스 종목의 등락률과 활성 종목 대비 캔들 수로 만든다`() {
        val stocks = listOf(
            member(1, "103").stock,
            member(2, "97").stock,
            member(3, "100").stock,
            member(4, "150", delisting = true).stock,
            member(5, null).stock,
            member(6, "110", active = false).stock,
        )

        val stats = ThemeAggregation.marketStats(base, stocks, prev)

        assertEquals(3, stats.pricedCount)
        assertEquals(1, stats.upCount)
        assertEquals(1, stats.downCount)
        assertEquals(1, stats.flatCount)
        assertEquals(BigDecimal("0.0000"), stats.medianChange)
        assertEquals(BigDecimal("0.8000"), stats.coverage)
    }

    private fun member(
        id: Long,
        close: String?,
        suspended: Boolean = false,
        delisting: Boolean = false,
        active: Boolean = true,
        volume: Long = 1000,
        prevDate: LocalDate? = prev,
        prevClose: String? = "100",
        cap: Long? = 1000 - id,
        r1w: String? = null,
        tradeValue: Long = 1000L,
    ) = ThemeMember(
        stock = StockObservation(
            id = id,
            ticker = "t$id",
            name = "종목$id",
            market = "KOSPI",
            isActive = active,
            tradingSuspended = suspended,
            underAdministration = false,
            delistingTrade = delisting,
            preferredStock = false,
            etp = false,
            spac = false,
            close = close?.let(::BigDecimal),
            volume = if (close == null) null else volume,
            tradeValue = if (close == null) null else tradeValue,
            prevDate = prevDate,
            prevClose = prevClose?.let(::BigDecimal),
            marketCap = cap,
            r1w = r1w?.let(::BigDecimal),
            r1m = null,
            r3m = null,
        ),
        reason = null,
    )

    private fun averages(vararg entries: Pair<Int, String>): Map<Long, TradeValueAverage> =
        entries.associate { (id, average) -> id.toLong() to TradeValueAverage(id.toLong(), BigDecimal(average), 20) }
}
