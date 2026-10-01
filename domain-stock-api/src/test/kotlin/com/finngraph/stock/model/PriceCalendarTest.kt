package com.finngraph.stock.model

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PriceCalendarTest {

    private val base = LocalDate.parse("2026-09-18")

    @Test
    fun `가격 기준일은 최근 중앙값의 90퍼센트 이상 적재된 가장 최근 날이다`() {
        val recent = listOf(
            DailyCandleCount(base, 1200),
            DailyCandleCount(base.minusDays(1), 2348),
            DailyCandleCount(base.minusDays(2), 2347),
            DailyCandleCount(base.minusDays(3), 2346),
        )

        assertEquals(base.minusDays(1), PriceCalendar.priceDate(recent))
    }

    @Test
    fun `장중 수집이 끝나 문턱을 넘으면 오늘이 가격 기준일이다`() {
        val recent = listOf(
            DailyCandleCount(base, 2340),
            DailyCandleCount(base.minusDays(1), 2348),
            DailyCandleCount(base.minusDays(2), 2347),
        )

        assertEquals(base, PriceCalendar.priceDate(recent))
    }

    @Test
    fun `가격 기준일 문턱은 중앙값의 90퍼센트이고 경계값은 통과한다`() {
        val atThreshold = listOf(DailyCandleCount(base, 90), DailyCandleCount(base.minusDays(1), 100), DailyCandleCount(base.minusDays(2), 100))
        val belowThreshold = listOf(DailyCandleCount(base, 89), DailyCandleCount(base.minusDays(1), 100), DailyCandleCount(base.minusDays(2), 100))

        assertEquals(base, PriceCalendar.priceDate(atThreshold))
        assertEquals(base.minusDays(1), PriceCalendar.priceDate(belowThreshold))
    }

    @Test
    fun `캔들이 없으면 가격 기준일도 없다`() {
        assertNull(PriceCalendar.priceDate(emptyList()))
    }
}
