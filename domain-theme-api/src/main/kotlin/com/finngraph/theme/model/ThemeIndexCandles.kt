package com.finngraph.theme.model

import java.time.DayOfWeek
import java.time.LocalDate

object ThemeIndexCandles {

    fun periodStart(date: LocalDate, period: IndexPeriod): LocalDate = when (period) {
        IndexPeriod.D -> date
        IndexPeriod.W -> date.with(DayOfWeek.MONDAY)
        IndexPeriod.M -> date.withDayOfMonth(1)
    }

    fun windowStart(baseDate: LocalDate, period: IndexPeriod, limit: Int): LocalDate {
        require(limit > 0) { "limit은 양수여야 합니다" }
        val offset = (limit - 1).toLong()
        return when (period) {
            IndexPeriod.D -> throw IllegalArgumentException("일봉은 달력 구간이 아니라 거래일 개수로 자른다")
            IndexPeriod.W -> periodStart(baseDate, period).minusWeeks(offset)
            IndexPeriod.M -> periodStart(baseDate, period).minusMonths(offset)
        }
    }

    fun aggregate(daily: List<ThemeIndexCandle>, period: IndexPeriod): List<ThemeIndexCandle> =
        daily.sortedBy { it.date }
            .groupBy { periodStart(it.date, period) }
            .map { (start, rows) ->
                ThemeIndexCandle(
                    date = start,
                    open = rows.first().open,
                    high = rows.maxOf { it.high },
                    low = rows.minOf { it.low },
                    close = rows.last().close,
                    volume = rows.sumOf { it.volume },
                    tradeValue = rows.mapNotNull { it.tradeValue }.takeIf { it.isNotEmpty() }?.sum(),
                )
            }
}
