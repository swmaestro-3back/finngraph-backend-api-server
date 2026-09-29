package com.finngraph.theme.model

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TradingCalendarTest {

    private val base = LocalDate.parse("2026-09-18")

    @Test
    fun `부분 적재일은 건너뛰고 그 전 정상일을 전 거래일로 삼는다`() {
        val recent = listOf(
            CandleDayCount(base, 2345),
            CandleDayCount(base.minusDays(1), 449),
            CandleDayCount(base.minusDays(2), 449),
            CandleDayCount(base.minusDays(5), 2348),
            CandleDayCount(base.minusDays(6), 2346),
            CandleDayCount(base.minusDays(7), 2347),
        )

        assertEquals(base.minusDays(5), TradingCalendar.previousTradingDate(base, recent))
    }

    @Test
    fun `직전 날짜가 정상이면 그날이 전 거래일이다`() {
        val recent = (0L..5L).map { CandleDayCount(base.minusDays(it), 2300 + it.toInt()) }

        assertEquals(base.minusDays(1), TradingCalendar.previousTradingDate(base, recent))
    }

    @Test
    fun `중앙값의 절반 이상이면 통과한다`() {
        val recent = listOf(
            CandleDayCount(base, 100),
            CandleDayCount(base.minusDays(1), 50),
            CandleDayCount(base.minusDays(2), 100),
        )

        assertEquals(base.minusDays(1), TradingCalendar.previousTradingDate(base, recent))
    }

    @Test
    fun `평균 창은 부분 적재일을 빼고 기준일 앞 유효 거래일을 최근순으로 최대 20개 고른다`() {
        val recent = (0L..30L).map { CandleDayCount(base.minusDays(it), if (it in 1L..2L) 449 else 2345) }

        val dates = TradingCalendar.tradingDatesBefore(base, recent)

        assertEquals(20, dates.size)
        assertEquals(base.minusDays(3), dates.first())
        assertEquals(base.minusDays(22), dates.last())
        assertEquals(false, dates.any { it == base.minusDays(1) || it == base.minusDays(2) || it >= base })
        assertEquals(listOf(base.minusDays(3), base.minusDays(4)), TradingCalendar.tradingDatesBefore(base, recent, 2))
    }

    @Test
    fun `평균 창의 문턱은 최근 20일 중앙값으로 정하고 후보가 없으면 빈 목록이다`() {
        val recent = listOf(CandleDayCount(base, 2345), CandleDayCount(base.minusDays(1), 10))

        assertEquals(emptyList(), TradingCalendar.tradingDatesBefore(base, recent))
        assertEquals(emptyList(), TradingCalendar.tradingDatesBefore(base, emptyList()))
    }

    @Test
    fun `기준일보다 앞선 후보가 없으면 null이다`() {
        assertNull(TradingCalendar.previousTradingDate(base, listOf(CandleDayCount(base, 2345))))
        assertNull(TradingCalendar.previousTradingDate(base, emptyList()))
    }

    @Test
    fun `이전 날짜가 중앙값의 절반에 못 미치면 null이다`() {
        val recent = listOf(
            CandleDayCount(base, 2345),
            CandleDayCount(base.minusDays(1), 10),
        )

        assertNull(TradingCalendar.previousTradingDate(base, recent))
    }

    @Test
    fun `기준일 이후와 20일 밖의 날짜는 무시한다`() {
        val recent = (0L..25L).map { CandleDayCount(base.minusDays(it), if (it == 25L) 1 else 2345) } +
            CandleDayCount(base.plusDays(1), 5000)

        assertEquals(base.minusDays(1), TradingCalendar.previousTradingDate(base, recent))
    }
}
