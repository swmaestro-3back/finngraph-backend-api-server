package com.finngraph.theme.model

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.max
import kotlin.math.sqrt

data class ThemeMetrics(
    val count: Int,
    val value: BigDecimal?,
    val mean: BigDecimal?,
    val lower: BigDecimal?,
    val upper: BigDecimal?,
    val sensitivity: BigDecimal?,
    val trimCount: Int,
    val trimmedIndexes: Set<Int>,
) {
    companion object {
        const val MIN_COUNT = 3
        const val VALUE_SCALE = 4
        const val SENSITIVITY_SCALE = 2

        private const val TRIM_RATIO_DENOMINATOR = 10
        private const val MIN_DEVIATION = 0.5
        private const val BASE_MULTIPLIER = 1.28
        private const val SMALL_SAMPLE_MULTIPLIER = 1.2
        private val PRECISION = MathContext.DECIMAL128

        fun empty(count: Int) = ThemeMetrics(count, null, null, null, null, null, 0, emptySet())

        fun of(values: List<BigDecimal?>): ThemeMetrics {
            val present = values.withIndex().mapNotNull { (index, value) -> value?.let { IndexedValue(index, it) } }
            val n = present.size
            if (n < MIN_COUNT) return empty(n)

            val sorted = present.sortedBy { it.value }
            val xs = sorted.map { it.value }
            val k = trimCount(n)
            val h = n - 2 * k
            val value = trimmedMean(xs, k)
            val (lower, upper) = if (h >= 2) interval(xs, k, h, value) else null to null
            val trimmed = (sorted.take(k) + sorted.takeLast(k)).map { it.index }.toSet()

            return ThemeMetrics(
                count = n,
                value = value.scaled(VALUE_SCALE),
                mean = mean(xs).scaled(VALUE_SCALE),
                lower = lower,
                upper = upper,
                sensitivity = sensitivity(xs, value),
                trimCount = k,
                trimmedIndexes = trimmed,
            )
        }

        fun trimCount(n: Int): Int = (n + TRIM_RATIO_DENOMINATOR - 1) / TRIM_RATIO_DENOMINATOR

        private fun trimmedMean(sorted: List<BigDecimal>, k: Int): BigDecimal =
            mean(sorted.subList(k, sorted.size - k))

        private fun mean(xs: List<BigDecimal>): BigDecimal =
            xs.fold(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal(xs.size), PRECISION)

        private fun interval(sorted: List<BigDecimal>, k: Int, h: Int, value: BigDecimal): Pair<BigDecimal, BigDecimal> {
            val n = sorted.size
            val floor = sorted[k].toDouble()
            val ceiling = sorted[n - k - 1].toDouble()
            val winsorized = sorted.map { it.toDouble().coerceIn(floor, ceiling) }
            val center = winsorized.average()
            val variance = winsorized.sumOf { (it - center) * (it - center) } / (n - 1)
            val deviation = max(sqrt(variance), MIN_DEVIATION)
            val standardError = sqrt((n - 1) * deviation * deviation / (h.toDouble() * (h - 1)))
            val multiplier = BASE_MULTIPLIER + SMALL_SAMPLE_MULTIPLIER / (h - 1)
            val half = BigDecimal(multiplier * standardError)
            return value.subtract(half).scaled(VALUE_SCALE) to value.add(half).scaled(VALUE_SCALE)
        }

        private fun sensitivity(sorted: List<BigDecimal>, value: BigDecimal): BigDecimal? {
            val m = sorted.size - 1
            if (m < MIN_COUNT) return null
            val k = trimCount(m)
            return sorted.indices
                .maxOf { excluded ->
                    val rest = sorted.filterIndexed { index, _ -> index != excluded }
                    trimmedMean(rest, k).subtract(value).abs()
                }
                .scaled(SENSITIVITY_SCALE)
        }

        private fun BigDecimal.scaled(scale: Int): BigDecimal = setScale(scale, RoundingMode.HALF_UP)
    }
}
