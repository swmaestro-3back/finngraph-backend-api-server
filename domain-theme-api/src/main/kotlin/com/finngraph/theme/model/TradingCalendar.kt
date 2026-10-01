package com.finngraph.theme.model

import java.time.LocalDate
import java.time.OffsetDateTime

data class CandleDayCount(
    val date: LocalDate,
    val candles: Int,
    val lastUpdatedAt: OffsetDateTime? = null,
)

data class PricingBasis(
    val baseDate: LocalDate?,
    val prevTradingDate: LocalDate?,
    val candleCounts: List<CandleDayCount> = emptyList(),
    val valuationDate: LocalDate? = baseDate,
    val priceUpdatedAt: OffsetDateTime? = null,
) {
    val isSettled: Boolean
        get() = baseDate != null && baseDate == valuationDate

    fun settled(): PricingBasis {
        if (isSettled) return this
        val date = valuationDate ?: return PricingBasis(null, null, candleCounts, null)
        return PricingBasis(
            baseDate = date,
            prevTradingDate = TradingCalendar.previousTradingDate(date, candleCounts),
            candleCounts = candleCounts,
            valuationDate = date,
            priceUpdatedAt = candleCounts.firstOrNull { it.date == date }?.lastUpdatedAt,
        )
    }
}

object TradingCalendar {

    const val RECENT_DAYS = 20
    const val PRICE_READY_RATIO = 0.9

    private const val MIN_RATIO_TO_MEDIAN = 0.5

    fun priceDate(recent: List<CandleDayCount>): LocalDate? {
        val ordered = recent.sortedByDescending { it.date }
        val window = ordered.take(RECENT_DAYS)
        if (window.isEmpty()) return null

        val threshold = median(window.map { it.candles }) * PRICE_READY_RATIO
        return ordered.firstOrNull { it.candles >= threshold }?.date
    }

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
