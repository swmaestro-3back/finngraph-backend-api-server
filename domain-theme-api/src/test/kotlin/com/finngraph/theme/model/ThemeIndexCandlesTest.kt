package com.finngraph.theme.model

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ThemeIndexCandlesTest {

    private val base = LocalDate.parse("2026-07-31")

    @Test
    fun `주봉은 그 주 월요일, 월봉은 그 달 1일이 시작일이다`() {
        assertEquals(LocalDate.parse("2026-07-27"), ThemeIndexCandles.periodStart(base, IndexPeriod.W))
        assertEquals(LocalDate.parse("2026-07-27"), ThemeIndexCandles.periodStart(LocalDate.parse("2026-08-02"), IndexPeriod.W))
        assertEquals(LocalDate.parse("2026-07-27"), ThemeIndexCandles.periodStart(LocalDate.parse("2026-07-27"), IndexPeriod.W))
        assertEquals(LocalDate.parse("2026-07-01"), ThemeIndexCandles.periodStart(base, IndexPeriod.M))
    }

    @Test
    fun `조회 시작일은 기준일이 속한 구간에서 개수만큼 거슬러 올라간다`() {
        assertEquals(LocalDate.parse("2026-07-20"), ThemeIndexCandles.windowStart(base, IndexPeriod.W, 2))
        assertEquals(LocalDate.parse("2026-07-27"), ThemeIndexCandles.windowStart(base, IndexPeriod.W, 1))
        assertEquals(LocalDate.parse("2026-06-01"), ThemeIndexCandles.windowStart(base, IndexPeriod.M, 2))
        assertFailsWith<IllegalArgumentException> { ThemeIndexCandles.windowStart(base, IndexPeriod.D, 5) }
        assertFailsWith<IllegalArgumentException> { ThemeIndexCandles.windowStart(base, IndexPeriod.W, 0) }
    }

    @Test
    fun `주봉은 첫 시가, 최고 고가, 최저 저가, 마지막 종가, 거래량·거래대금 합이다`() {
        val weeks = ThemeIndexCandles.aggregate(dailies, IndexPeriod.W)

        assertEquals(listOf(LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-27")), weeks.map { it.date })
        val week = weeks.last()
        assertEquals(BigDecimal("1105"), week.open)
        assertEquals(BigDecimal("1270"), week.high)
        assertEquals(BigDecimal("1100"), week.low)
        assertEquals(BigDecimal("1260"), week.close)
        assertEquals(500L, week.volume)
        assertEquals(5000L, week.tradeValue)
    }

    @Test
    fun `입력 순서와 무관하게 날짜순으로 묶는다`() {
        assertEquals(
            ThemeIndexCandles.aggregate(dailies, IndexPeriod.M),
            ThemeIndexCandles.aggregate(dailies.reversed(), IndexPeriod.M),
        )
    }

    @Test
    fun `거래대금은 있는 값만 더하고 전부 없으면 null이다`() {
        val partial = listOf(candle("2026-07-30", "1", "1", "1", "1", 1, null), candle("2026-07-31", "1", "1", "1", "1", 1, 7))
        assertEquals(7L, ThemeIndexCandles.aggregate(partial, IndexPeriod.W).single().tradeValue)

        val none = partial.map { it.copy(tradeValue = null) }
        assertNull(ThemeIndexCandles.aggregate(none, IndexPeriod.W).single().tradeValue)
    }

    private val dailies = listOf(
        candle("2026-07-24", "1090", "1105", "1085", "1100", 100, 1000),
        candle("2026-07-28", "1105", "1160", "1100", "1150", 110, 1100),
        candle("2026-07-29", "1150", "1155", "1130", "1140", 120, 1200),
        candle("2026-07-30", "1145", "1210", "1140", "1200", 130, 1300),
        candle("2026-07-31", "1205", "1270", "1195", "1260", 140, 1400),
    )

    private fun candle(date: String, open: String, high: String, low: String, close: String, volume: Long, tradeValue: Long?) =
        ThemeIndexCandle(
            date = LocalDate.parse(date),
            open = BigDecimal(open),
            high = BigDecimal(high),
            low = BigDecimal(low),
            close = BigDecimal(close),
            volume = volume,
            tradeValue = tradeValue,
        )
}
