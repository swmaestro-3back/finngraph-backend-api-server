package com.finngraph.theme.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class MarketStats(
    val baseDate: LocalDate?,
    val pricedCount: Int,
    val upCount: Int,
    val downCount: Int,
    val flatCount: Int,
    val medianChange: BigDecimal?,
    val upRatio: BigDecimal?,
    val downRatio: BigDecimal?,
    val coverage: BigDecimal?,
) {
    companion object {
        const val SCALE = 4

        val EMPTY = MarketStats(null, 0, 0, 0, 0, null, null, null, null)

        fun of(baseDate: LocalDate?, changes: List<BigDecimal>, candleCount: Int, activeCount: Int): MarketStats {
            val priced = changes.size
            val up = changes.count { it.signum() > 0 }
            val down = changes.count { it.signum() < 0 }
            return MarketStats(
                baseDate = baseDate,
                pricedCount = priced,
                upCount = up,
                downCount = down,
                flatCount = priced - up - down,
                medianChange = median(changes),
                upRatio = ratio(up, priced),
                downRatio = ratio(down, priced),
                coverage = ratio(candleCount, activeCount),
            )
        }

        private fun median(changes: List<BigDecimal>): BigDecimal? {
            if (changes.isEmpty()) return null
            val sorted = changes.sorted()
            val middle = sorted.size / 2
            val raw = if (sorted.size % 2 == 1) sorted[middle]
            else sorted[middle - 1].add(sorted[middle]).divide(BigDecimal(2))
            return raw.setScale(SCALE, RoundingMode.HALF_UP)
        }

        private fun ratio(numerator: Int, denominator: Int): BigDecimal? {
            if (denominator <= 0) return null
            return BigDecimal(numerator).divide(BigDecimal(denominator), SCALE, RoundingMode.HALF_UP)
        }
    }
}
