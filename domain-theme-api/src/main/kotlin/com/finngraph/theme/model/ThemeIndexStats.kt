package com.finngraph.theme.model

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

object ThemeIndexStats {

    const val RETURN_SCALE = 4

    private const val WEEK_DAYS = 7L
    private const val ANCHOR_BUFFER_DAYS = 31L

    private val HUNDRED = BigDecimal(100)
    private val PRECISION = MathContext.DECIMAL128

    fun windowStart(baseDate: LocalDate): LocalDate = baseDate.minusYears(1).minusDays(ANCHOR_BUFFER_DAYS)

    fun summarize(baseDate: LocalDate, closes: List<ThemeIndexClose>): ThemeIndexSummary? {
        val ordered = closes.filter { it.date <= baseDate }.sortedBy { it.date }
        val last = ordered.lastOrNull()?.takeIf { it.date == baseDate } ?: return null
        val yearAgo = baseDate.minusYears(1)
        val lastYear = ordered.filter { it.date > yearAgo }
        val high = lastYear.maxOf { it.close }

        return ThemeIndexSummary(
            date = baseDate,
            close = last.close,
            change = ordered.getOrNull(ordered.size - 2)?.let { percent(last.close, it.close) },
            r1w = since(ordered, baseDate.minusDays(WEEK_DAYS), last.close),
            r1m = since(ordered, baseDate.minusMonths(1), last.close),
            r3m = since(ordered, baseDate.minusMonths(3), last.close),
            r1y = since(ordered, yearAgo, last.close),
            ytd = since(ordered, baseDate.withDayOfYear(1).minusDays(1), last.close),
            high52w = high,
            low52w = lastYear.minOf { it.close },
            fromHigh52w = percent(last.close, high),
            streak = streak(ordered),
        )
    }

    private fun since(ordered: List<ThemeIndexClose>, target: LocalDate, current: BigDecimal): BigDecimal? =
        ordered.lastOrNull { it.date <= target }?.let { percent(current, it.close) }

    private fun percent(current: BigDecimal, base: BigDecimal): BigDecimal? {
        if (base.signum() == 0) return null
        return current.divide(base, PRECISION)
            .subtract(BigDecimal.ONE)
            .multiply(HUNDRED)
            .setScale(RETURN_SCALE, RoundingMode.HALF_UP)
    }

    private fun streak(ordered: List<ThemeIndexClose>): Int {
        if (ordered.size < 2) return 0
        val direction = ordered.last().close.compareTo(ordered[ordered.size - 2].close)
        if (direction == 0) return 0

        var days = 0
        for (i in ordered.size - 1 downTo 1) {
            if (ordered[i].close.compareTo(ordered[i - 1].close) != direction) break
            days += 1
        }
        return direction * days
    }
}
