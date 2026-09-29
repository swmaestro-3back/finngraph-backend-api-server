package com.finngraph.theme.model

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarketStatsTest {

    private val base = LocalDate.parse("2026-09-26")

    @Test
    fun `부호별 수와 중앙값 비율 적재율을 4자리로 낸다`() {
        val stats = MarketStats.of(base, listOf("1.5", "-0.3", "0", "2.1", "0.58").map(::BigDecimal), 2348, 2400)

        assertEquals(5, stats.pricedCount)
        assertEquals(3, stats.upCount)
        assertEquals(1, stats.downCount)
        assertEquals(1, stats.flatCount)
        assertEquals(BigDecimal("0.5800"), stats.medianChange)
        assertEquals(BigDecimal("0.6000"), stats.upRatio)
        assertEquals(BigDecimal("0.2000"), stats.downRatio)
        assertEquals(BigDecimal("0.9783"), stats.coverage)
    }

    @Test
    fun `짝수 개면 가운데 둘의 평균이 중앙값이다`() {
        val stats = MarketStats.of(base, listOf("1", "2", "3", "10").map(::BigDecimal), 4, 4)

        assertEquals(BigDecimal("2.5000"), stats.medianChange)
    }

    @Test
    fun `등락률이 하나도 없으면 중앙값과 비율은 null이다`() {
        val stats = MarketStats.of(base, emptyList(), 0, 10)

        assertEquals(0, stats.pricedCount)
        assertNull(stats.medianChange)
        assertNull(stats.upRatio)
        assertNull(stats.downRatio)
        assertEquals(BigDecimal("0.0000"), stats.coverage)
    }

    @Test
    fun `활성 종목이 없으면 적재율은 null이다`() {
        assertNull(MarketStats.of(null, emptyList(), 0, 0).coverage)
    }
}
