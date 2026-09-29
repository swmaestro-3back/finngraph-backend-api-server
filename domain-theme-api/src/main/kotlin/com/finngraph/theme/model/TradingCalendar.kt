package com.finngraph.theme.model

import java.time.LocalDate

data class CandleDayCount(
    val date: LocalDate,
    val candles: Int,
)

data class PricingBasis(
    val baseDate: LocalDate?,
    val prevTradingDate: LocalDate?,
    val candleCounts: List<CandleDayCount> = emptyList(),
)

object TradingCalendar {

    const val RECENT_DAYS = 20

    private const val MIN_RATIO_TO_MEDIAN = 0.5

    fun previousTradingDate(baseDate: LocalDate, recent: List<CandleDayCount>): LocalDate? {
        val window = recentWindow(baseDate, recent)
        if (window.isEmpty()) return null

        val threshold = threshold(window)
        return window
            .filter { it.date < baseDate && it.candles >= threshold }
            .maxOfOrNull { it.date }
    }

    fun tradingDatesBefore(baseDate: LocalDate, recent: List<CandleDayCount>, count: Int = RECENT_DAYS): List<LocalDate> {
        val window = recentWindow(baseDate, recent)
        if (window.isEmpty()) return emptyList()

        val threshold = threshold(window)
        return recent
            .filter { it.date < baseDate && it.candles >= threshold }
            .map { it.date }
            .distinct()
            .sortedDescending()
            .take(count)
    }

    private fun recentWindow(baseDate: LocalDate, recent: List<CandleDayCount>): List<CandleDayCount> =
        recent
            .filter { it.date <= baseDate }
            .sortedByDescending { it.date }
            .take(RECENT_DAYS)

    private fun threshold(window: List<CandleDayCount>): Double =
        median(window.map { it.candles }) * MIN_RATIO_TO_MEDIAN

    private fun median(counts: List<Int>): Double {
        val sorted = counts.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle].toDouble()
        else (sorted[middle - 1] + sorted[middle]) / 2.0
    }
}
