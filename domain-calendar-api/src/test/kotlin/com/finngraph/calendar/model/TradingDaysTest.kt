package com.finngraph.calendar.model

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TradingDaysTest {

    private fun weekdays(from: String, to: String): Map<LocalDate, Boolean> =
        generateSequence(LocalDate.parse(from)) { it.plusDays(1) }
            .takeWhile { it <= LocalDate.parse(to) }
            .associateWith { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }

    @Test
    fun `기준일이 개장일이면 개장일 두 번 앞이 매수 마감일`() {
        val result = TradingDays.lastBuy(LocalDate.parse("2026-09-30"), weekdays("2026-09-01", "2026-10-31"))

        assertEquals(LastBuy(LocalDate.parse("2026-09-28"), false), result)
    }

    @Test
    fun `기준일이 주말이고 그 앞 평일이 휴장이면 휴장일을 건너뛴다`() {
        val days = weekdays("2026-09-01", "2026-10-31") + mapOf(LocalDate.parse("2026-10-01") to false)

        val result = TradingDays.lastBuy(LocalDate.parse("2026-10-03"), days)

        assertEquals(LastBuy(LocalDate.parse("2026-09-29"), false), result)
    }

    @Test
    fun `휴장일 정보가 없으면 주말만 빼고 계산하고 추정으로 표시한다`() {
        val result = TradingDays.lastBuy(LocalDate.parse("2027-03-31"), emptyMap())

        assertEquals(LastBuy(LocalDate.parse("2027-03-29"), true), result)
    }

    @Test
    fun `조회한 날짜 중 하나라도 휴장일 정보 밖이면 추정이다`() {
        val result = TradingDays.lastBuy(LocalDate.parse("2026-09-30"), weekdays("2026-09-30", "2026-09-30"))

        assertEquals(LastBuy(LocalDate.parse("2026-09-28"), true), result)
    }
}
