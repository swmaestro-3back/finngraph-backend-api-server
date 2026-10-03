package com.finngraph.composition.calendar

import com.finngraph.calendar.model.ActionStep
import com.finngraph.calendar.model.CorporateAction
import com.finngraph.calendar.model.EventFamily
import com.finngraph.calendar.model.EventKind
import com.finngraph.stock.model.Candle
import com.finngraph.stock.model.DividendRecord
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActionMetricsTest {

    private val priceDate = LocalDate.parse("2027-03-31")

    private fun action(
        family: EventFamily,
        basis: String,
        label: String? = null,
        amount: String? = null,
        ratio: String? = null,
        steps: List<Pair<EventKind, String>> = emptyList(),
    ) = CorporateAction(
        ticker = "961101",
        family = family,
        label = label,
        basisDate = LocalDate.parse(basis),
        steps = steps.map { (kind, date) -> ActionStep(kind, LocalDate.parse(date), null, false) },
        amount = amount?.let(::BigDecimal),
        ratio = ratio?.let(::BigDecimal),
        agenda = emptyList(),
        agendaTruncated = false,
    )

    private fun daily(
        until: String = "2027-03-31",
        overrides: Map<String, Pair<String?, String?>> = emptyMap(),
        rates: Map<String, String> = emptyMap(),
    ): List<Candle> =
        generateSequence(LocalDate.parse("2027-01-04")) { it.plusDays(1) }
            .takeWhile { it <= LocalDate.parse(until) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .map { date ->
                val (open, close) = overrides[date.toString()] ?: (null to null)
                Candle(
                    date,
                    BigDecimal(open ?: "10000"),
                    BigDecimal("10000"),
                    BigDecimal("10000"),
                    BigDecimal(close ?: "10000"),
                    1000,
                    null,
                    rates[date.toString()]?.let(::BigDecimal),
                )
            }
            .toList()

    private fun record(date: String, kind: String, dps: String?) =
        DividendRecord(LocalDate.parse(date), kind, dps?.let(::BigDecimal), null)

    @Test
    fun `이번 금액이 있으면 그 금액으로 이번 배당 수익률을 계산한다`() {
        val metrics = ActionMetrics.dividend(action(EventFamily.DIV, "2027-03-26", "분기", amount = "100"), BigDecimal("9900"), emptyList())

        assertEquals(DividendMetrics(BigDecimal("100"), DpsBasis.CURRENT, BigDecimal("1.0101")), metrics)
    }

    @Test
    fun `금액이 없으면 같은 종류의 직전 배당금으로 대신한다`() {
        val history = listOf(
            record("2027-05-01", "결산", "900"),
            record("2027-03-26", "분기", "100"),
            record("2027-01-29", "결산", "500"),
            record("2026-06-30", "결산", "300"),
            record("2027-02-26", "결산", null),
        )

        val metrics = ActionMetrics.dividend(action(EventFamily.DIV, "2027-04-30", "결산"), BigDecimal("9900"), history)

        assertEquals(DividendMetrics(BigDecimal("500"), DpsBasis.PREVIOUS, BigDecimal("5.0505")), metrics)
    }

    @Test
    fun `직전 배당금이 0원이면 대신하지 않고 그 앞 회차를 쓴다`() {
        val history = listOf(
            record("2027-03-31", "결산", "0"),
            record("2027-01-29", "결산", "500"),
        )

        assertEquals(
            DividendMetrics(BigDecimal("500"), DpsBasis.PREVIOUS, BigDecimal("5.0505")),
            ActionMetrics.dividend(action(EventFamily.DIV, "2027-04-30", "결산"), BigDecimal("9900"), history),
        )
        assertEquals(
            DividendMetrics(null, null, null),
            ActionMetrics.dividend(action(EventFamily.DIV, "2027-04-30", "결산"), BigDecimal("9900"), history.take(1)),
        )
    }

    @Test
    fun `대신할 배당이 없으면 비우고 현재가가 없으면 수익률만 비운다`() {
        assertEquals(
            DividendMetrics(null, null, null),
            ActionMetrics.dividend(action(EventFamily.DIV, "2027-04-30", "결산"), BigDecimal("9900"), emptyList()),
        )
        assertEquals(
            DividendMetrics(BigDecimal("100"), DpsBasis.CURRENT, null),
            ActionMetrics.dividend(action(EventFamily.DIV, "2027-03-26", "분기", amount = "100"), null, emptyList()),
        )
    }

    @Test
    fun `권리락 전 유상증자는 현재가로 이론가를 낸다`() {
        val rights = action(EventFamily.RIGHTS, "2027-05-12", amount = "8000", ratio = "25", steps = listOf(EventKind.RIGHTS_EX to "2027-05-11"))

        val metrics = ActionMetrics.rights(rights, PricePoint(BigDecimal("10000"), priceDate), daily())

        assertEquals(
            RightsMetrics(BigDecimal("20.0000"), BigDecimal("8000"), BigDecimal("25.0000"), ExPrice(BigDecimal("9600"), PriceBasis.CURRENT_PRICE, null)),
            metrics,
        )
    }

    @Test
    fun `권리락이 지난 유상증자는 권리락일 등락률로 거래소 기준가를 되돌려 확정하고 실제 시초가를 붙인다`() {
        val rights = action(EventFamily.RIGHTS, "2027-02-17", amount = "8000", ratio = "25", steps = listOf(EventKind.RIGHTS_EX to "2027-02-16"))
        val candles = daily(
            overrides = mapOf("2027-02-15" to (null to "13000"), "2027-02-16" to ("10300" to "10000")),
            rates = mapOf("2027-02-16" to "-3.8462"),
        )

        val metrics = ActionMetrics.rights(rights, PricePoint(BigDecimal("10000"), priceDate), candles)

        assertEquals(ExPrice(BigDecimal("10400"), PriceBasis.PREVIOUS_CLOSE, BigDecimal("10300")), metrics.exPrice)
    }

    @Test
    fun `권리락일 등락률이 없으면 수정주가인 전날 종가를 기준가로 쓴다`() {
        val rights = action(EventFamily.RIGHTS, "2027-02-17", amount = "8000", ratio = "25", steps = listOf(EventKind.RIGHTS_EX to "2027-02-16"))
        val candles = daily(overrides = mapOf("2027-02-15" to (null to "10400"), "2027-02-16" to ("10300" to null)))

        val metrics = ActionMetrics.rights(rights, PricePoint(BigDecimal("10000"), priceDate), candles)

        assertEquals(ExPrice(BigDecimal("10400"), PriceBasis.PREVIOUS_CLOSE, BigDecimal("10300")), metrics.exPrice)
    }

    @Test
    fun `권리락이 지났어도 그날 일봉이 없으면 현재가 참고값으로 돌아간다`() {
        val rights = action(EventFamily.RIGHTS, "2027-02-17", amount = "8000", ratio = "25", steps = listOf(EventKind.RIGHTS_EX to "2027-02-16"))
        val candles = daily().filterNot { it.date == LocalDate.parse("2027-02-16") }

        val metrics = ActionMetrics.rights(rights, PricePoint(BigDecimal("10000"), priceDate), candles)

        assertEquals(ExPrice(BigDecimal("9600"), PriceBasis.CURRENT_PRICE, null), metrics.exPrice)
    }

    @Test
    fun `발행가가 없으면 이론가와 발행가 대비 현재가를 비우고 희석률은 낸다`() {
        val rights = action(EventFamily.RIGHTS, "2027-05-12", ratio = "25", steps = listOf(EventKind.RIGHTS_EX to "2027-05-11"))

        val metrics = ActionMetrics.rights(rights, PricePoint(BigDecimal("10000"), priceDate), daily())

        assertEquals(BigDecimal("20.0000"), metrics.dilution)
        assertNull(metrics.priceVsIssue)
        assertNull(metrics.exPrice.theoretical)
    }

    @Test
    fun `권리락이 지난 무상증자는 거래소 기준가와 5·20거래일 수익률을 낸다`() {
        val bonus = action(EventFamily.BONUS, "2027-02-03", ratio = "100", steps = listOf(EventKind.BONUS_EX to "2027-02-02"))
        val candles = daily(
            overrides = mapOf("2027-02-01" to (null to "10000"), "2027-02-08" to (null to "11000")),
            rates = mapOf("2027-02-02" to "0"),
        )

        val metrics = ActionMetrics.bonus(bonus, PricePoint(BigDecimal("10000"), priceDate), candles)

        assertEquals(
            BonusMetrics(ExPrice(BigDecimal("10000"), PriceBasis.PREVIOUS_CLOSE, BigDecimal("10000")), BigDecimal("10.0000"), BigDecimal("0.0000")),
            metrics,
        )
    }

    @Test
    fun `일봉이 모자라거나 권리락 전이면 무상증자 수익률을 비운다`() {
        val bonus = action(EventFamily.BONUS, "2027-02-03", ratio = "100", steps = listOf(EventKind.BONUS_EX to "2027-02-02"))
        val short = daily(until = "2027-02-12")

        val shortMetrics = ActionMetrics.bonus(bonus, PricePoint(BigDecimal("10000"), LocalDate.parse("2027-02-12")), short)
        val beforeEx = ActionMetrics.bonus(bonus, PricePoint(BigDecimal("10000"), LocalDate.parse("2027-02-01")), short)

        assertEquals(BigDecimal("0.0000"), shortMetrics.returnAfter5)
        assertNull(shortMetrics.returnAfter20)
        assertEquals(ExPrice(BigDecimal("5000"), PriceBasis.CURRENT_PRICE, null), beforeEx.exPrice)
        assertNull(beforeEx.returnAfter5)
    }
}
