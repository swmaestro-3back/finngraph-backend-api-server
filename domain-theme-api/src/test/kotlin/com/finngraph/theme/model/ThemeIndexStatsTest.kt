package com.finngraph.theme.model

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ThemeIndexStatsTest {

    private val base = LocalDate.parse("2026-07-31")

    private val series = listOf(
        close("2025-07-30", "800"),
        close("2025-09-15", "1300"),
        close("2025-12-30", "900"),
        close("2026-04-30", "950"),
        close("2026-06-30", "1000"),
        close("2026-07-24", "1100"),
        close("2026-07-28", "1150"),
        close("2026-07-29", "1140"),
        close("2026-07-30", "1200"),
        close("2026-07-31", "1260"),
    )

    @Test
    fun `기간 수익률은 기준일에서 달력으로 물러난 날 이전의 마지막 종가 대비다`() {
        val summary = assertNotNull(ThemeIndexStats.summarize(base, series))

        assertEquals(base, summary.date)
        assertEquals(BigDecimal("1260"), summary.close)
        assertEquals(BigDecimal("5.0000"), summary.change)
        assertEquals(BigDecimal("14.5455"), summary.r1w)
        assertEquals(BigDecimal("26.0000"), summary.r1m)
        assertEquals(BigDecimal("32.6316"), summary.r3m)
        assertEquals(BigDecimal("57.5000"), summary.r1y)
        assertEquals(BigDecimal("40.0000"), summary.ytd)
    }

    @Test
    fun `52주 고저는 1년 안의 종가로 잡고 고점 대비를 낸다`() {
        val summary = assertNotNull(ThemeIndexStats.summarize(base, series))

        assertEquals(BigDecimal("1300"), summary.high52w)
        assertEquals(BigDecimal("900"), summary.low52w)
        assertEquals(BigDecimal("-3.0769"), summary.fromHigh52w)
    }

    @Test
    fun `연속 등락일은 방향이 바뀌는 곳에서 끊기고 하락은 음수다`() {
        assertEquals(2, assertNotNull(ThemeIndexStats.summarize(base, series)).streak)

        val falling = listOf(close("2026-07-28", "100"), close("2026-07-29", "90"), close("2026-07-30", "80"), close("2026-07-31", "70"))
        assertEquals(-3, assertNotNull(ThemeIndexStats.summarize(base, falling)).streak)

        val flat = listOf(close("2026-07-29", "100"), close("2026-07-30", "110"), close("2026-07-31", "110"))
        assertEquals(0, assertNotNull(ThemeIndexStats.summarize(base, flat)).streak)
    }

    @Test
    fun `기준일 행이 없으면 요약하지 않는다`() {
        assertNull(ThemeIndexStats.summarize(base, series.dropLast(1)))
        assertNull(ThemeIndexStats.summarize(base, emptyList()))
    }

    @Test
    fun `기준일 이후 행은 장중 값이라 무시한다`() {
        val summary = assertNotNull(ThemeIndexStats.summarize(base, series + close("2026-08-03", "9999")))

        assertEquals(BigDecimal("1260"), summary.close)
        assertEquals(BigDecimal("1300"), summary.high52w)
    }

    @Test
    fun `이력이 짧으면 닿지 않는 기간 수익률만 null이다`() {
        val summary = assertNotNull(ThemeIndexStats.summarize(base, series.takeLast(3)))

        assertEquals(BigDecimal("5.0000"), summary.change)
        assertNull(summary.r1w)
        assertNull(summary.r1y)
        assertNull(summary.ytd)
        assertEquals(2, summary.streak)
    }

    @Test
    fun `하루치뿐이면 등락률이 없다`() {
        val summary = assertNotNull(ThemeIndexStats.summarize(base, listOf(close("2026-07-31", "1000"))))

        assertNull(summary.change)
        assertEquals(0, summary.streak)
        assertEquals(BigDecimal("0.0000"), summary.fromHigh52w)
    }

    @Test
    fun `조회 구간은 1년 기준점을 찾을 여유를 두고 시작한다`() {
        assertEquals(LocalDate.parse("2025-06-30"), ThemeIndexStats.windowStart(base))
    }

    private fun close(date: String, value: String) = ThemeIndexClose(LocalDate.parse(date), BigDecimal(value))
}
