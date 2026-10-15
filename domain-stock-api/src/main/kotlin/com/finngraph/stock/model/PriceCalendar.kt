package com.finngraph.stock.model

import java.time.LocalDate

data class DailyCandleCount(
    val date: LocalDate,
    val candles: Int,
)

object PriceCalendar {

    const val RECENT_DAYS = 20
    const val READY_RATIO = 0.9

    private const val MIN_RATIO_TO_MEDIAN = 0.5

    fun priceDate(recent: List<DailyCandleCount>): LocalDate? {
        val ordered = recent.sortedByDescending { it.date }
        val window = ordered.take(RECENT_DAYS)
        if (window.isEmpty()) return null

        val threshold = median(window.map { it.candles }) * READY_RATIO
        return ordered.firstOrNull { it.candles >= threshold }?.date
    }

    fun tradingDatesBefore(baseDate: LocalDate, recent: List<DailyCandleCount>, count: Int = RECENT_DAYS): List<LocalDate> {
        val window = recent
            .filter { it.date <= baseDate }
            .sortedByDescending { it.date }
            .take(RECENT_DAYS)
        if (window.isEmpty()) return emptyList()

        val threshold = median(window.map { it.candles }) * MIN_RATIO_TO_MEDIAN
        return recent
            .filter { it.date < baseDate && it.candles >= threshold }
            .map { it.date }
            .distinct()
            .sortedDescending()
            .take(count)
    }

    private fun median(counts: List<Int>): Double {
        val sorted = counts.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle].toDouble()
        else (sorted[middle - 1] + sorted[middle]) / 2.0
    }
}
