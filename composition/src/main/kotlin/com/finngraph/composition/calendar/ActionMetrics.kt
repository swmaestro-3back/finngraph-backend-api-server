package com.finngraph.composition.calendar

import com.finngraph.calendar.model.CorporateAction
import com.finngraph.calendar.model.EventKind
import com.finngraph.stock.model.Candle
import com.finngraph.stock.model.DividendRecord
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

enum class DpsBasis { CURRENT, PREVIOUS }

enum class PriceBasis { CURRENT_PRICE, PREVIOUS_CLOSE }

data class PricePoint(val price: BigDecimal?, val date: LocalDate?)

data class DividendMetrics(val dps: BigDecimal?, val dpsBasis: DpsBasis?, val expectedYield: BigDecimal?)

data class ExPrice(val theoretical: BigDecimal?, val basis: PriceBasis, val actualOpen: BigDecimal?)

data class RightsMetrics(
    val dilution: BigDecimal?,
    val issuePrice: BigDecimal?,
    val priceVsIssue: BigDecimal?,
    val exPrice: ExPrice,
)

data class BonusMetrics(val exPrice: ExPrice, val returnAfter5: BigDecimal?, val returnAfter20: BigDecimal?)

object ActionMetrics {

    private const val PERCENT_SCALE = 4
    private const val RATE_SCALE = 10
    private const val FIRST_WEEK = 5
    private const val FIRST_MONTH = 20
    private val HUNDRED = BigDecimal("100")

    fun dividend(action: CorporateAction, price: BigDecimal?, history: List<DividendRecord>): DividendMetrics {
        val previous = history
            .filter { it.kind == action.label && it.recordDate < action.basisDate && (it.dps?.signum() ?: 0) > 0 }
            .maxByOrNull { it.recordDate }
            ?.dps
        val dps = action.amount ?: previous
        val basis = when {
            action.amount != null -> DpsBasis.CURRENT
            previous != null -> DpsBasis.PREVIOUS
            else -> null
        }
        return DividendMetrics(dps, basis, percent(dps, price))
    }

    fun rights(action: CorporateAction, current: PricePoint, daily: List<Candle>): RightsMetrics {
        val rate = rate(action.ratio)
        val issue = action.amount
        val base = base(action.dateOf(EventKind.RIGHTS_EX), current, daily)
        val basePrice = base.price
        val theoretical = when {
            base.basis == PriceBasis.PREVIOUS_CLOSE -> basePrice
            rate == null || issue == null || basePrice == null -> null
            else -> won(basePrice + issue * rate, BigDecimal.ONE + rate)
        }
        val price = current.price
        return RightsMetrics(
            dilution = rate?.let { percent(it, BigDecimal.ONE + it) },
            issuePrice = issue,
            priceVsIssue = if (price == null || issue == null) null else percent(price - issue, issue),
            exPrice = ExPrice(theoretical, base.basis, base.actualOpen),
        )
    }

    fun bonus(action: CorporateAction, current: PricePoint, daily: List<Candle>): BonusMetrics {
        val rate = rate(action.ratio)
        val exDate = action.dateOf(EventKind.BONUS_EX)
        val base = base(exDate, current, daily)
        val basePrice = base.price
        val theoretical = when {
            base.basis == PriceBasis.PREVIOUS_CLOSE -> basePrice
            rate == null || basePrice == null -> null
            else -> won(basePrice, BigDecimal.ONE + rate)
        }
        val exIndex = if (base.basis == PriceBasis.PREVIOUS_CLOSE) daily.indexOfFirst { it.date == exDate } else -1
        return BonusMetrics(
            exPrice = ExPrice(theoretical, base.basis, base.actualOpen),
            returnAfter5 = returnAfter(FIRST_WEEK, exIndex, theoretical, daily),
            returnAfter20 = returnAfter(FIRST_MONTH, exIndex, theoretical, daily),
        )
    }

    private data class Base(val price: BigDecimal?, val basis: PriceBasis, val actualOpen: BigDecimal?)

    private fun base(exDate: LocalDate?, current: PricePoint, daily: List<Candle>): Base {
        val priceDate = current.date
        if (exDate != null && priceDate != null && exDate <= priceDate) {
            val exIndex = daily.indexOfFirst { it.date == exDate }
            if (exIndex >= 1) {
                val ex = daily[exIndex]
                val official = ex.changeRate
                    ?.let { rate -> won(ex.close.multiply(HUNDRED), HUNDRED + rate) }
                    ?: daily[exIndex - 1].close.setScale(0, RoundingMode.HALF_UP)
                return Base(official, PriceBasis.PREVIOUS_CLOSE, ex.open)
            }
        }
        return Base(current.price, PriceBasis.CURRENT_PRICE, null)
    }

    private fun returnAfter(days: Int, exIndex: Int, theoretical: BigDecimal?, daily: List<Candle>): BigDecimal? {
        val target = exIndex + days - 1
        if (theoretical == null || exIndex < 0 || target >= daily.size) return null
        return percent(daily[target].close - theoretical, theoretical)
    }

    private fun rate(ratio: BigDecimal?): BigDecimal? = ratio?.divide(HUNDRED, RATE_SCALE, RoundingMode.HALF_UP)

    private fun percent(numerator: BigDecimal?, denominator: BigDecimal?): BigDecimal? {
        if (numerator == null || denominator == null || denominator.signum() == 0) return null
        return numerator.multiply(HUNDRED).divide(denominator, PERCENT_SCALE, RoundingMode.HALF_UP)
    }

    private fun won(numerator: BigDecimal, denominator: BigDecimal): BigDecimal =
        numerator.divide(denominator, 0, RoundingMode.HALF_UP)
}
