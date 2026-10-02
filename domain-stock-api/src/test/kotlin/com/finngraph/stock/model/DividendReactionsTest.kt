package com.finngraph.stock.model

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DividendReactionsTest {

    private fun series(from: String, count: Int, overrides: Map<String, Pair<String, String>> = emptyMap()): List<Candle> =
        generateSequence(LocalDate.parse(from)) { it.plusDays(1) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .take(count)
            .map { date ->
                val (open, close) = overrides[date.toString()] ?: ("10000" to "10000")
                Candle(date, BigDecimal(open), BigDecimal(open), BigDecimal(close), BigDecimal(close), 1000, null)
            }
            .toList()

    private fun record(date: String, kind: String, dps: String?) =
        DividendRecord(LocalDate.parse(date), kind, dps?.let(::BigDecimal), null)

    @Test
    fun `배당락일 시초 갭과 이론 낙폭과 회복 거래일을 계산한다`() {
        val candles = series(
            "2027-01-04",
            30,
            mapOf("2027-01-28" to ("9600" to "9700"), "2027-01-29" to ("9800" to "9800")),
        )

        val reaction = DividendReactions.of(listOf(record("2027-01-29", "결산", "500")), candles).single()

        assertEquals(LocalDate.parse("2027-01-28"), reaction.exDate)
        assertEquals(BigDecimal("10000"), reaction.prevClose)
        assertEquals(BigDecimal("9600"), reaction.exOpen)
        assertEquals(BigDecimal("5.0000"), reaction.theoreticalDrop)
        assertEquals(BigDecimal("-4.0000"), reaction.openGap)
        assertEquals(3, reaction.recoveryDays)
        assertEquals(false, reaction.pending)
    }

    @Test
    fun `아직 회복 못 했고 60거래일이 안 지났으면 진행 중이다`() {
        val candles = series("2027-03-01", 23).map {
            if (it.date >= LocalDate.parse("2027-03-25")) it.copy(open = BigDecimal("9900"), close = BigDecimal("9900")) else it
        }

        val reaction = DividendReactions.of(listOf(record("2027-03-26", "분기", "100")), candles).single()

        assertNull(reaction.recoveryDays)
        assertTrue(reaction.pending)
    }

    @Test
    fun `60거래일 안에 회복 못 하면 미회복으로 확정한다`() {
        val exDate = LocalDate.parse("2027-01-28")
        val candles = series("2027-01-04", 90).map {
            if (it.date >= exDate) it.copy(open = BigDecimal("9000"), close = BigDecimal("9000")) else it
        }

        val reaction = DividendReactions.of(listOf(record("2027-01-29", "결산", "500")), candles).single()

        assertNull(reaction.recoveryDays)
        assertEquals(false, reaction.pending)
    }

    @Test
    fun `금액 없음·일봉 시작 전·마지막 일봉 뒤·거래정지 구간 배당은 뺀다`() {
        val candles = series("2027-01-04", 60).filterNot {
            it.date >= LocalDate.parse("2027-02-10") && it.date <= LocalDate.parse("2027-02-26")
        }

        val reactions = DividendReactions.of(
            listOf(
                record("2027-01-29", "분기", null),
                record("2026-06-30", "결산", "300"),
                record("2027-12-30", "결산", "300"),
                record("2027-02-24", "분기", "100"),
            ),
            candles,
        )

        assertEquals(emptyList(), reactions)
    }

    @Test
    fun `주당배당금이 0원인 회차는 미확정·무배당이라 뺀다`() {
        val candles = series("2027-01-04", 60)

        val reactions = DividendReactions.of(
            listOf(record("2027-03-12", "분기", "0"), record("2027-01-29", "결산", "500")),
            candles,
        )

        assertEquals(listOf(LocalDate.parse("2027-01-29")), reactions.map { it.recordDate })
    }

    @Test
    fun `최신 기준일부터 준다`() {
        val candles = series("2027-01-04", 60)

        val reactions = DividendReactions.of(
            listOf(record("2027-01-29", "결산", "500"), record("2027-03-12", "분기", "100")),
            candles,
        )

        assertEquals(listOf(LocalDate.parse("2027-03-12"), LocalDate.parse("2027-01-29")), reactions.map { it.recordDate })
    }
}
