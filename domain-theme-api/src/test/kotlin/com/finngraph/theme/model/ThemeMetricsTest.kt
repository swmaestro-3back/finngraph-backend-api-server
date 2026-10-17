package com.finngraph.theme.model

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeMetricsTest {

    @Test
    fun `두 종목이면 모든 값이 null이고 절사 수는 0이다`() {
        val metrics = ThemeMetrics.of(decimals("1.0", "2.0"))

        assertEquals(2, metrics.count)
        assertNull(metrics.value)
        assertNull(metrics.mean)
        assertNull(metrics.lower)
        assertNull(metrics.upper)
        assertNull(metrics.sensitivity)
        assertEquals(0, metrics.trimCount)
        assertTrue(metrics.trimmedIndexes.isEmpty())
    }

    @Test
    fun `세 종목이면 중앙값이고 구간과 민감도는 없다`() {
        val metrics = ThemeMetrics.of(decimals("1", "2", "30"))

        assertEquals(BigDecimal("2.0000"), metrics.value)
        assertEquals(BigDecimal("11.0000"), metrics.mean)
        assertNull(metrics.lower)
        assertNull(metrics.upper)
        assertNull(metrics.sensitivity)
        assertEquals(1, metrics.trimCount)
        assertEquals(setOf(0, 2), metrics.trimmedIndexes)
    }

    @Test
    fun `네 종목이면 가운데 둘의 평균이다`() {
        val metrics = ThemeMetrics.of(decimals("-5", "1", "3", "40"))

        assertEquals(BigDecimal("2.0000"), metrics.value)
        assertEquals(1, metrics.trimCount)
        assertNotNull(metrics.lower)
        assertNotNull(metrics.upper)
    }

    @Test
    fun `다섯 종목에서 극단값 하나는 절사된다`() {
        val metrics = ThemeMetrics.of(decimals("0.1", "0.2", "0.3", "0.4", "30"))

        assertEquals(BigDecimal("0.3000"), metrics.value)
        assertEquals(BigDecimal("6.2000"), metrics.mean)
        assertEquals(setOf(0, 4), metrics.trimmedIndexes)
    }

    @Test
    fun `캔서문샷 여덟 종목은 절사평균 7점15 근처에 민감도 2점81이고 하한이 1 미만이다`() {
        val metrics = ThemeMetrics.of(decimals("-0.62", "0.81", "1.02", "2.20", "3.92", "13.76", "21.20", "29.95"))

        assertNear(7.15, metrics.value, 0.01)
        assertNear(9.03, metrics.mean, 0.01)
        assertNear(2.81, metrics.sensitivity, 0.01)
        assertTrue(assertNotNull(metrics.lower) < BigDecimal.ONE, "lower=${metrics.lower}")
        assertTrue(assertNotNull(metrics.upper) > assertNotNull(metrics.value))
        assertEquals(setOf(0, 7), metrics.trimmedIndexes)
    }

    @Test
    fun `모두 같은 값이면 표준편차 하한이 적용돼 하한이 값에 가깝다`() {
        val metrics = ThemeMetrics.of(List(8) { BigDecimal("29.95") })

        assertEquals(BigDecimal("29.9500"), metrics.value)
        assertEquals(BigDecimal("29.9500"), metrics.mean)
        assertTrue(assertNotNull(metrics.lower) >= BigDecimal("29.5"), "lower=${metrics.lower}")
        assertTrue(assertNotNull(metrics.lower) < BigDecimal("29.95"))
        assertEquals(BigDecimal("0.00"), metrics.sensitivity)
    }

    @Test
    fun `절사 수는 10퍼센트 올림이다`() {
        mapOf(3 to 1, 4 to 1, 5 to 1, 10 to 1, 11 to 2, 19 to 2, 20 to 2, 21 to 3, 30 to 3, 31 to 4).forEach { (n, k) ->
            assertEquals(k, ThemeMetrics.trimCount(n), "n=$n")
            assertEquals(k, ThemeMetrics.of(List(n) { BigDecimal(it) }).trimCount, "n=$n")
        }
    }

    @Test
    fun `null은 제외하고 원래 위치의 인덱스로 절사 대상을 표시한다`() {
        val metrics = ThemeMetrics.of(listOf(null, BigDecimal("50"), null, BigDecimal("1"), BigDecimal("2"), BigDecimal("-9"), null))

        assertEquals(4, metrics.count)
        assertEquals(BigDecimal("1.5000"), metrics.value)
        assertEquals(setOf(5, 1), metrics.trimmedIndexes)
    }

    @Test
    fun `동률은 입력 순서대로 절사한다`() {
        val metrics = ThemeMetrics.of(decimals("1", "1", "1", "1", "1"))

        assertEquals(setOf(0, 4), metrics.trimmedIndexes)
    }

    private fun decimals(vararg values: String): List<BigDecimal?> = values.map(::BigDecimal)

    private fun assertNear(expected: Double, actual: BigDecimal?, tolerance: Double) {
        val value = assertNotNull(actual).toDouble()
        assertTrue(kotlin.math.abs(value - expected) <= tolerance, "expected $expected±$tolerance but was $value")
    }
}
